package org.example.chenojcodesandbox;

import cn.hutool.core.io.FileUtil;
import cn.hutool.core.io.resource.ResourceUtil;
import cn.hutool.core.util.StrUtil;
import cn.hutool.dfa.FoundWord;
import cn.hutool.dfa.WordTree;
import org.example.chenojcodesandbox.model.ExecuteCodeRequest;
import org.example.chenojcodesandbox.model.ExecuteCodeResponse;
import org.example.chenojcodesandbox.model.ExecuteMessage;
import org.example.chenojcodesandbox.model.JudgeInfo;
import org.example.chenojcodesandbox.security.DefaultSecurityManager;
import org.example.chenojcodesandbox.utils.ProcessUtils;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;

/**
 * Java原生代码沙箱
 */
public class JavaNativeCodeSandboxOld implements CodeSandbox {
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



    public static void main(String[] args) {
        JavaNativeCodeSandboxOld javaNativeCodeSandboxOld = new JavaNativeCodeSandboxOld();
        //1.构造一个代码沙县请求类
        ExecuteCodeRequest executeCodeRequest = new ExecuteCodeRequest();
        //2.读取题目输入用例（这里先测试写死）
        executeCodeRequest.setInputList(Arrays.asList("1 2", "1 3"));
        //3.读取用户写入代码
        String code = ResourceUtil.readStr("testCode/simpleComputeArgs/Main.java", StandardCharsets.UTF_8);
        executeCodeRequest.setCode(code);
        executeCodeRequest.setLanguage("java");
        //4.调用沙箱处理，将用户请求编译
        ExecuteCodeResponse executeCodeResponse = javaNativeCodeSandboxOld.executeCode(executeCodeRequest);
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

        //4.执行代码，得到输出结果
        List<ExecuteMessage> executeMessageList = new ArrayList<>();
        for (String inputArgs : inputList) {
            String runCmd = String.format("java -Xmx256m -Dfile.encoding=UTF-8 -cp %s Main %s", userCodeParentPath, inputArgs);
            try {
                Process runProcess = Runtime.getRuntime().exec(runCmd);
                //使用守护线程对用户线程运行时间进行一个监控
                new Thread(() -> {
                    try {
                        //等待状态结束即为超时
                        Thread.sleep(TIME_OUT);
                        runProcess.destroy();
                    } catch (InterruptedException e) {
                        throw new RuntimeException(e);
                    }
                }).start();
                ExecuteMessage executeMessage = ProcessUtils.runProcessAndGetMessage(runProcess, "运行");
                System.out.println(executeMessage);
                executeMessageList.add(executeMessage);
            } catch (Exception e) {
                return getErrorResponse(e);
            }
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
