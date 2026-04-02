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

@Component
public class JavaDockerCodeSandbox extends JavaCodeSandboxTemplate implements CodeSandbox{
    // 定义一个守护线程的超时时间
    private static final long TIME_OUT = 10000L;
    // 初始化容器
    private static final Boolean FIRST_INIT=true;


    public static void main(String[] args) {
        JavaDockerCodeSandbox javaNativeCodeSandbox = new JavaDockerCodeSandbox();
        //1.构造一个代码沙县请求类
        ExecuteCodeRequest executeCodeRequest = new ExecuteCodeRequest();
        //2.读取题目输入用例（这里先测试写死）
        executeCodeRequest.setInputList(Arrays.asList("1 2", "1 3"));
        //3.读取用户写入代码
        String code = ResourceUtil.readStr("testCode/simpleComputeArgs/Main.java", StandardCharsets.UTF_8);
        executeCodeRequest.setCode(code);
        executeCodeRequest.setLanguage("java");
        //4.调用沙箱处理，将用户请求编译
        ExecuteCodeResponse executeCodeResponse = javaNativeCodeSandbox.executeCode(executeCodeRequest);
        System.out.println(executeCodeResponse);
    }


    /**
     * 3.创建容器执行用户代码文件
     * @param userCodeFile
     * @param inputList
     * @return
     */
    @Override
    public List<ExecuteMessage> executeFile(File userCodeFile, List<String> inputList) {
        String userCodeParentPath = userCodeFile.getParentFile().getAbsolutePath();
        // 获取一个所有参数都是默认值的docker client
        DockerClient dockerClient = DockerClientBuilder.getInstance().build();

        // 1)拉取镜像
        String image="openjdk:8-alpine";
        if(FIRST_INIT){
            PullImageCmd pullImageCmd =dockerClient.pullImageCmd(image);
            PullImageResultCallback pullImageResultCallback =new PullImageResultCallback(){
                @Override
                public void onNext(PullResponseItem item){
                    System.out.println("下载的镜像： "+ item.getStatus());
                    super.onNext(item);
                }

            };
            try {
                pullImageCmd
                        .exec(pullImageResultCallback)
                        .awaitCompletion();
            } catch (InterruptedException e) {
                System.out.println("拉取镜像异常： "+ e.getMessage());
                throw new RuntimeException(e);
            }
        }
        System.out.println("下载完成");


        // 2)创建容器时指定hostconfig，定义容器配置，来限制内存
        CreateContainerCmd containerCmd =dockerClient.createContainerCmd(image);
        HostConfig hostConfig=new HostConfig();
        hostConfig.withMemory(100*1000*1000L);
        hostConfig.withMemorySwap(0L);//内存交换的值
        hostConfig.withCpuCount(1L);
        hostConfig.setBinds(new Bind(userCodeParentPath,new Volume("/app")));
        //todo hostConfig.withSecurityOpts(Array.asList("seccomp=网上搜docker安全管理配置字符串"));

        CreateContainerResponse createConfigResponse= containerCmd
                .withHostConfig(hostConfig)
                .withNetworkDisabled(true)//禁止网络资源调用
                .withReadonlyRootfs(true)//禁止向根目录里面写文件
                .withAttachStdin(true)
                .withAttachStderr(true)
                .withAttachStdout(true)
                .withTty(true)
                .exec();
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
            //创建消息值，用于返回执行情况
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
            executeMessageList.add(executeMessage);
            executeMessage.setTime(time);
            executeMessage.setMemory(maxMemory[0]);
        }
        return executeMessageList;
    }

}
