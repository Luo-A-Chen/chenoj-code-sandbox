package org.example.chenojcodesandbox.sandbox.impl;

import cn.hutool.core.date.StopWatch;
import com.github.dockerjava.api.DockerClient;
import com.github.dockerjava.api.async.ResultCallback;
import com.github.dockerjava.api.command.*;
import com.github.dockerjava.api.model.*;
import com.github.dockerjava.core.DockerClientBuilder;
import com.github.dockerjava.core.command.ExecStartResultCallback;
import org.example.chenojcodesandbox.model.ExecuteMessage;
import org.example.chenojcodesandbox.model.JudgeConfig;
import org.example.chenojcodesandbox.sandbox.CodeSandbox;
import org.example.chenojcodesandbox.sandbox.SandboxLimits;
import org.example.chenojcodesandbox.sandbox.template.JavaCodeSandboxTemplate;
import org.example.chenojcodesandbox.utils.SandboxStdinSupport;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Component;

import java.io.Closeable;
import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.TimeUnit;

@Component
@Primary
public class JavaDockerCodeSandbox extends JavaCodeSandboxTemplate implements CodeSandbox {

    private static final String IMAGE = "eclipse-temurin:8-jre-alpine";

    private static String normalizeProcessOutput(String raw) {
        if (raw == null) {
            return null;
        }
        return raw.replaceFirst("\\s+$", "");
    }

    private volatile boolean imagePrepared;

    private void ensureDockerImage() {
        if (imagePrepared) {
            return;
        }
        synchronized (this) {
            if (imagePrepared) {
                return;
            }
            DockerClient dockerClient = DockerClientBuilder.getInstance().build();
            PullImageResultCallback pullImageResultCallback = new PullImageResultCallback() {
                @Override
                public void onNext(PullResponseItem item) {
                    System.out.println("下载的镜像：" + item.getStatus());
                    super.onNext(item);
                }
            };
            try {
                dockerClient.pullImageCmd(IMAGE)
                        .exec(pullImageResultCallback)
                        .awaitCompletion();
                System.out.println("镜像拉取完成");
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new RuntimeException("镜像拉取被中断", e);
            }
            imagePrepared = true;
        }
    }

    @Override
    public List<ExecuteMessage> executeFile(File userCodeFile, List<String> inputList, JudgeConfig judgeConfig) {
        ensureDockerImage();
        long execTimeoutMs = SandboxLimits.effectiveTimeMs(judgeConfig);
        long containerMemoryBytes = SandboxLimits.effectiveMemoryBytes(judgeConfig);

        File workDir = userCodeFile.getParentFile();
        String userCodeParentPath = workDir.getAbsolutePath();
        DockerClient dockerClient = DockerClientBuilder.getInstance().build();

        HostConfig hostConfig = new HostConfig()
                .withMemory(containerMemoryBytes)
                .withMemorySwap(0L)
                .withCpuCount(1L)
                .withBinds(new Bind(userCodeParentPath, new Volume("/app")))
                .withTmpFs(Collections.singletonMap("/tmp", "rw,exec"));

        CreateContainerResponse createConfigResponse = dockerClient.createContainerCmd(IMAGE)
                .withHostConfig(hostConfig)
                .withEntrypoint("tail", "-f", "/dev/null")
                .withNetworkDisabled(true)
                .withReadonlyRootfs(true)
                .withAttachStderr(true)
                .withAttachStdout(true)
                .withTty(true)
                .exec();
        String containerId = createConfigResponse.getId();

        dockerClient.startContainerCmd(containerId).exec();
        List<ExecuteMessage> executeMessageList = new ArrayList<>();

        String stdinRedirectCmd = String.format(
                "java -Dfile.encoding=UTF-8 -cp /app Main < /app/%s",
                SandboxStdinSupport.STDIN_FILE_NAME);

        try {
            for (String inputContent : inputList) {
                SandboxStdinSupport.writeStdinFile(workDir, inputContent);

                StopWatch stopWatch = new StopWatch();
                ExecCreateCmdResponse execCreateCmdResponse = dockerClient.execCreateCmd(containerId)
                        .withCmd("sh", "-c", stdinRedirectCmd)
                        .withAttachStderr(true)
                        .withAttachStdout(true)
                        .withAttachStdin(false)
                        .exec();

                ExecuteMessage executeMessage = new ExecuteMessage();
                final StringBuilder stdout = new StringBuilder();
                final StringBuilder stderr = new StringBuilder();
                long time = 0L;
                final boolean[] timeout = {true};
                String execId = execCreateCmdResponse.getId();

                ExecStartResultCallback execStartResultCallback = new ExecStartResultCallback() {
                    @Override
                    public void onComplete() {
                        timeout[0] = false;
                        super.onComplete();
                    }

                    @Override
                    public void onNext(Frame frame) {
                        StreamType streamType = frame.getStreamType();
                        String chunk = new String(frame.getPayload(), StandardCharsets.UTF_8);
                        if (StreamType.STDERR.equals(streamType)) {
                            stderr.append(chunk);
                        } else {
                            stdout.append(chunk);
                        }
                        super.onNext(frame);
                    }
                };

                final long[] maxMemoryBytes = {0L};
                StatsCmd statsCmd = dockerClient.statsCmd(containerId);
                ResultCallback<Statistics> statisticsResultCallback = statsCmd.exec(new ResultCallback<Statistics>() {
                    @Override
                    public void onStart(Closeable closeable) {
                    }

                    @Override
                    public void onNext(Statistics statistics) {
                        Long usage = statistics.getMemoryStats().getUsage();
                        if (usage != null) {
                            maxMemoryBytes[0] = Math.max(maxMemoryBytes[0], usage);
                        }
                    }

                    @Override
                    public void onError(Throwable throwable) {
                    }

                    @Override
                    public void onComplete() {
                    }

                    @Override
                    public void close() throws IOException {
                    }
                });

                try {
                    stopWatch.start();
                    dockerClient.execStartCmd(execId)
                            .exec(execStartResultCallback)
                            .awaitCompletion(execTimeoutMs, TimeUnit.MILLISECONDS);
                    stopWatch.stop();
                    time = stopWatch.getLastTaskTimeMillis();
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    throw new RuntimeException(e);
                } finally {
                    statsCmd.close();
                }

                executeMessage.setMessage(normalizeProcessOutput(stdout.toString()));
                executeMessage.setErrorMessage(normalizeProcessOutput(stderr.toString()));
                executeMessage.setTimeout(timeout[0]);
                executeMessage.setTime(time);
                executeMessage.setMemory(SandboxLimits.bytesToKb(maxMemoryBytes[0]));
                executeMessageList.add(executeMessage);
            }
        } finally {
            SandboxStdinSupport.deleteStdinFile(workDir);
            dockerClient.stopContainerCmd(containerId).exec();
            dockerClient.removeContainerCmd(containerId).exec();
        }
        return executeMessageList;
    }
}
