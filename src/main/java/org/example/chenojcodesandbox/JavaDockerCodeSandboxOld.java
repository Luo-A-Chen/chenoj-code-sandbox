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

import java.io.Closeable;
import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

public class JavaDockerCodeSandboxOld extends JavaCodeSandboxTemplate implements CodeSandbox{

    /**
     * Java原生代码沙箱
     */
        // 全局代码存放的目录，写死了但是设置全局变量，防止魔法值
        private static final String GLOBAL_CODE_DIR_NAME = "tempCode";
        // 全局代码文件名,执行的代码只能运行在该目录下，减少读取用户输入的类名
        private static final String GLOBAL_JAVA_CLASS_NAME = "Main.java";
        // 定义一个守护线程的超时时间
        private static final long TIME_OUT = 10000L;
        // 定义一个黑名单，用来限制用户输入的代码
        private static final List<String> BLACK_LIST = Arrays.asList("java.io.File",
                "java.io.FileOutputStream", "java.io.FileInputStream", "java.io.FileReader",
                "java.io.FileWriter", "java.io.BufferedReader", "java.io.BufferedWriter",
                "java.io.PrintWriter", "java.io.InputStream");
        // 创建一个单词树，用来限制用户输入的代码(这里是一个hutool的工具类)
        private static final WordTree wordTree = new WordTree();
        static {
            wordTree.addWords(BLACK_LIST);
        }
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
         * 核心思路就是用程序代替人工进行命令行编译
         * Process类是java程序运行时JVM创建的进程对象
         *
         * @param request
         * @return
         */
        @Override
        public ExecuteCodeResponse executeCode(ExecuteCodeRequest request) {
            //引入java安全管理器
            SecurityManager securityManager = new DefaultSecurityManager();
            System.setSecurityManager(securityManager);

            // 1.题目输入用例列表+提交的代码+编程语言
            List<String> inputList =request.getInputList();
            String code=request.getCode();
            String language=request.getLanguage();

            //进行代码黑名单检验，看是否违规
            FoundWord foundWord = wordTree.matchWord(code);
            if(foundWord!=null){
                System.out.println("包含敏感词: "+foundWord.getFoundWord());
                return null;
            }

            //2.用户的代码保存成文件
            //2.1获取到当前用户工作的一个根目录
            String userDir = System.getProperty("user.dir");
            //2.2根目录+下划线+代码保存文件
            String globalCodePathName=userDir+ File.separator+GLOBAL_CODE_DIR_NAME;
            //2.3判断文件是否存在，没有则新建
            if(!FileUtil.exist(globalCodePathName)){
                FileUtil.mkdir(globalCodePathName);
            }
            //2.4把用户的代码隔离存放,保存为文件
            //2.5保存父文件=代码文件夹+下划线+随机文件名
            String userCodeParentPath=globalCodePathName+File.separator+ UUID.randomUUID();
            //2.6父文件下的文件路径
            String userCodePath=userCodeParentPath+File.separator+GLOBAL_JAVA_CLASS_NAME;
            //2.7真正处理的文件
            File userCodeFile=FileUtil.writeString(code,userCodePath,StandardCharsets.UTF_8);

            //3.编译代码，得到class文件
            String compileCmd = String.format("javac -encoding utf-8 %s", userCodeFile.getAbsolutePath());
            try {
                Process compileProcess =Runtime.getRuntime().exec(compileCmd);
                ExecuteMessage executeMessage = ProcessUtils.runProcessAndGetMessage(compileProcess, "编译");
                System.out.println(executeMessage);
            } catch (Exception e) {
                return getErrorResponse(e);
            }

            // 4.创建容器
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
//                executeMessage.setMemory(maxMemory[0]);
            }
//5. 收集整理输出结果
            ExecuteCodeResponse executeCodeResponse = new ExecuteCodeResponse();
            List<String> outputList = new ArrayList<>();
            // 取用时最大值，便于判断是否超时
            long maxTime = 0;
            for (ExecuteMessage executeMessage : executeMessageList) {
                String errorMessage = executeMessage.getErrorMessage();
                if (StrUtil.isNotBlank(errorMessage)) {
                    executeCodeResponse.setMessage(errorMessage);
                    executeCodeResponse.setStatus(3);
                    break;
                }
                outputList.add(executeMessage.getMessage());
                Long time = executeMessage.getTime();
                if (time != null) {
                    maxTime = Math.max(maxTime, time);
                }
            }
            //正常运行输出设置状态
            if (outputList.size() == executeMessageList.size()) {
                executeCodeResponse.setStatus(1);
            }
            executeCodeResponse.setOutputList(outputList);
            JudgeInfo judgeInfo = new JudgeInfo();
            //拿到最大执行时间
            judgeInfo.setTime(maxTime);
            //拿到程序执行内存todo
            executeCodeResponse.setJudgeInfo(judgeInfo);

            //6.文件清理，释放空间
            if (userCodeFile.getParentFile() != null) {
                boolean del = FileUtil.del(userCodeFile.getParentFile());
                System.out.println("删除" + (del ? "成功" : "失败"));
            }

            //7. 错误处理，提升程序健壮性

            return executeCodeResponse;
        }

        /**
         * 错误处理,获取错误响应
         *
         * @param e
         * @return
         */
        private ExecuteCodeResponse getErrorResponse(Throwable e) {
            ExecuteCodeResponse executeCodeResponse = new ExecuteCodeResponse();
            executeCodeResponse.setOutputList(new ArrayList<>());
            executeCodeResponse.setMessage(e.getMessage());
            //这里执行的是代码沙箱的错误处理
            executeCodeResponse.setStatus(2);
            executeCodeResponse.setJudgeInfo(new JudgeInfo());
            return executeCodeResponse;
        }

}
