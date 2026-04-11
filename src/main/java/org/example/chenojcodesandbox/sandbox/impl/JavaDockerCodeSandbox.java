package org.example.chenojcodesandbox;
import cn.hutool.core.date.StopWatch;
import cn.hutool.core.io.FileUtil;
import cn.hutool.core.io.resource.ResourceUtil;
import cn.hutool.core.util.ArrayUtil;
import cn.hutool.core.util.StrUtil;
import cn.hutool.dfa.FoundWord;
import cn.hutool.dfa.WordTree;
import com.github.dockerjava.api.DockerClient;
import com.github.dockerjava.api.async.ResultCallback;
import com.github.dockerjava.api.command.*;
import com.github.dockerjava.api.model.*;
import com.github.dockerjava.core.DockerClientBuilder;
import com.github.dockerjava.core.command.ExecStartResultCallback;
import org.example.chenojcodesandbox.model.ExecuteCodeRequest;
import org.example.chenojcodesandbox.model.ExecuteCodeResponse;
import org.example.chenojcodesandbox.model.ExecuteMessage;
import org.example.chenojcodesandbox.model.JudgeInfo;
import org.example.chenojcodesandbox.security.DefaultSecurityManager;
import org.example.chenojcodesandbox.utils.ProcessUtils;
import org.springframework.stereotype.Component;

import java.io.Closeable;
import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

import org.springframework.context.annotation.Primary;

@Component
@Primary
public class JavaDockerCodeSandbox extends JavaCodeSandboxTemplate implements CodeSandbox{
    private static final long TIME_OUT = 10000L;
    private static final String IMAGE = "openjdk:8-alpine";

    // 静态初始化块，类加载时只执行一次，确保镜像只拉取一次
    static {
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
            throw new RuntimeException("镜像拉取失败", e);
        }
    }

    /**
     * 创建docker容器执行用户代码文件
     * @param userCodeFile
     * @param inputList
     * @return
     */
    @Override
    public List<ExecuteMessage> executeFile(File userCodeFile, List<String> inputList) {
        String userCodeParentPath = userCodeFile.getParentFile().getAbsolutePath();
        DockerClient dockerClient = DockerClientBuilder.getInstance().build();

        // 2)创建容器
        CreateContainerCmd containerCmd = dockerClient.createContainerCmd(IMAGE);
        HostConfig hostConfig=new HostConfig();
        hostConfig.withMemory(100*1000*1000L); //内存上限100mb
        hostConfig.withMemorySwap(0L);         //禁止使用交换内存
        hostConfig.withCpuCount(1L);           //只使用一个cpu核心
        hostConfig.setBinds(new Bind(userCodeParentPath,new Volume("/app")));
        //todo hostConfig.withSecurityOpts(Array.asList("seccomp=网上搜docker安全管理配置字符串"));

        CreateContainerResponse createConfigResponse= containerCmd
                .withHostConfig(hostConfig)
                .withNetworkDisabled(true) //禁止网络资源调用，用户代码不能联网
                .withReadonlyRootfs(true) //禁止向根目录里面写文件，根目录只读
                .withAttachStdin(true)    // 附加标准输入
                .withAttachStderr(true)   // 附加错误输出
                .withAttachStdout(true)   // 附加标准输出
                .withTty(true)            // 分配伪终端
                .exec();                  // 真正创建容器
        System.out.println(createConfigResponse);
        String containerId =createConfigResponse.getId();

        //3)启动容器
        dockerClient.startContainerCmd(containerId).exec();
        List<ExecuteMessage> executeMessageList = new ArrayList<>();

        for(String inputArgs :inputList){
            StopWatch  stopWatch = new StopWatch();
            String[] inputArgsArray=inputArgs.split(" ");
            String[] cmdArray = ArrayUtil.append(new String[]{"java","-cp","/app","Main"},inputArgsArray);
            ExecCreateCmdResponse execCreateCmdResponse=dockerClient.execCreateCmd(containerId)
                    .withCmd(cmdArray)
                    .withAttachStderr(true)
                    .withAttachStdin(true)
                    .withAttachStdout(true)
                    .exec();
            System.out.println("创建执行命令: "+execCreateCmdResponse);

            System.out.println("创建执行命令:"+ execCreateCmdResponse);
            //定义回调，创建消息值，用于返回执行情况
            ExecuteMessage executeMessage = new ExecuteMessage();
            final String[] message = {null};
            final String[] errorMessage = {null};
            long time =0L;
            final boolean[] timeout ={true};//默认程序执行是超时的
            String execId = execCreateCmdResponse.getId();

            ExecStartResultCallback execStartResultCallback =new ExecStartResultCallback(){
                @Override
                public void onComplete(){
                    timeout[0]=false;//执行成功了以后又设置成不超时的
                    super.onComplete();
                }

                @Override
                public void onNext(Frame frame){
                    StreamType streamType=frame.getStreamType();
                    if(StreamType.STDERR.equals(streamType)){
                        errorMessage[0] =new String(frame.getPayload());
                        System.out.println("输出错误结果: "+ errorMessage[0]);
                    } else{
                        message[0]=new String(frame.getPayload());
                        System.out.println("输出结果: "+message[0]);
                    }
                    super.onNext(frame);
                }
            };

            //定义一个最大内存
            final long[] maxMemory={0L};

            //获取占用的内存
            StatsCmd statsCmd = dockerClient.statsCmd(containerId);
            ResultCallback<Statistics> statisticsResultCallback = statsCmd.exec(new ResultCallback<Statistics>() {
                @Override
                public void onStart(Closeable closeable) {
                }

                @Override
                public void onNext(Statistics statistics) {
                    System.out.println("内存占用: " + statistics.getMemoryStats().getUsage());
                    maxMemory[0] =Math.max(statistics.getMemoryStats().getUsage(),maxMemory[0]);
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

            try{
                stopWatch.start();
                dockerClient.execStartCmd(execId)
                        .exec(execStartResultCallback)
                        .awaitCompletion(TIME_OUT, TimeUnit.MILLISECONDS);//容器运行超过5秒往下走
                stopWatch.stop();
                time = stopWatch.getLastTaskTimeMillis();
                statsCmd.close();
            }catch(InterruptedException e){
                System.out.println("错误输出结果: "+e.getMessage());
                throw new  RuntimeException(e);
            }
            executeMessage.setMessage(message[0]);
            executeMessage.setErrorMessage(errorMessage[0]);
            executeMessage.setTimeout(timeout[0]);
            executeMessageList.add(executeMessage);
            executeMessage.setTime(time);
            executeMessage.setMemory(maxMemory[0]);
        }
        // 所有测试用例执行完，停止并删除容器
        dockerClient.stopContainerCmd(containerId).exec();
        dockerClient.removeContainerCmd(containerId).exec();
        return executeMessageList;
    }

}
