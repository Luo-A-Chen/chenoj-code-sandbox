package org.example.chenojcodesandbox.sandbox.impl;

import org.example.chenojcodesandbox.model.ExecuteMessage;
import org.example.chenojcodesandbox.model.JudgeConfig;
import org.example.chenojcodesandbox.sandbox.SandboxLimits;
import org.example.chenojcodesandbox.sandbox.template.JavaCodeSandboxTemplate;
import org.example.chenojcodesandbox.utils.ProcessUtils;
import org.example.chenojcodesandbox.utils.SandboxStdinSupport;
import org.springframework.stereotype.Component;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

/**
 * Java 原生沙箱：标准输入喂用例，与 Docker 判题方式一致。
 */
@Component
public class JavaNativeCodeSandbox extends JavaCodeSandboxTemplate {

    @Override
    public List<ExecuteMessage> executeFile(File userCodeFile, List<String> inputList, JudgeConfig judgeConfig) {
        long execTimeoutMs = SandboxLimits.effectiveTimeMs(judgeConfig);
        int heapMb = SandboxLimits.effectiveNativeHeapMb(judgeConfig);
        File workDir = userCodeFile.getParentFile();

        List<ExecuteMessage> executeMessageList = new ArrayList<>();
        for (String inputContent : inputList) {
            try {
                ProcessBuilder processBuilder = new ProcessBuilder(
                        "java",
                        "-Xmx" + heapMb + "m",
                        "-Dfile.encoding=UTF-8",
                        "-cp",
                        workDir.getAbsolutePath(),
                        "Main");
                processBuilder.directory(workDir);
                processBuilder.redirectErrorStream(false);
                Process runProcess = processBuilder.start();

                Thread watchdog = new Thread(() -> {
                    try {
                        Thread.sleep(execTimeoutMs);
                        runProcess.destroy();
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                    }
                });
                watchdog.setDaemon(true);
                watchdog.start();

                SandboxStdinSupport.feedStdin(runProcess, inputContent);
                ExecuteMessage executeMessage = ProcessUtils.runProcessAndGetMessage(runProcess, "运行");
                watchdog.interrupt();

                if (executeMessage.getTime() != null && executeMessage.getTime() > execTimeoutMs) {
                    executeMessage.setTimeout(true);
                }
                executeMessageList.add(executeMessage);
            } catch (IOException e) {
                throw new RuntimeException("执行错误: " + e.getMessage(), e);
            }
        }
        return executeMessageList;
    }
}
