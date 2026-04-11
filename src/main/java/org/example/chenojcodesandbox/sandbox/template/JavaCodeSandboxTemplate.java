package org.example.chenojcodesandbox;

import cn.hutool.core.io.FileUtil;
import cn.hutool.core.util.StrUtil;
import lombok.extern.slf4j.Slf4j;
import org.example.chenojcodesandbox.model.ExecuteCodeRequest;
import org.example.chenojcodesandbox.model.ExecuteCodeResponse;
import org.example.chenojcodesandbox.model.ExecuteMessage;
import org.example.chenojcodesandbox.model.JudgeInfo;
import org.example.chenojcodesandbox.utils.ProcessUtils;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;


/**
 * java模版方法的实现
 * Template模版，沙箱模板
 */
@Slf4j
public abstract class JavaCodeSandboxTemplate implements CodeSandbox{
    // 全局代码存放的目录，写死了但是设置全局变量，防止魔法值
    private static final String GLOBAL_CODE_DIR_NAME = "tempCode";
    // 全局代码文件名,执行的代码只能运行在该目录下，减少读取用户输入的类名
    private static final String GLOBAL_JAVA_CLASS_NAME = "Main.java";
    // 定义一个守护线程的超时时间
    private static final long TIME_OUT = 10000L;


    /**
     * 核心思路就是用程序代替人工进行命令行编译
     * Process类是java程序运行时JVM创建的进程对象
     *
     * @param request
     * @return
     */
    @Override
    public ExecuteCodeResponse executeCode(ExecuteCodeRequest request) {
        List<String> inputList = request.getInputList();
        String code = request.getCode();
        String language = request.getLanguage();

        try {
            //1.把用户代码保存成文件
            File userCodeFile = CodeSavetoFile(code);

            //2.编译代码，得到class文件
            ExecuteMessage compileFileExecuteMessage = compileFile(userCodeFile);
            System.out.println(compileFileExecuteMessage);

            //3.执行代码，得到输出结果
            List<ExecuteMessage> executeMessageList = executeFile(userCodeFile, inputList);

            //4. 收集整理输出结果
            ExecuteCodeResponse outputResponse = getOutputResponse(executeMessageList);

            //5.文件清理，释放空间
            boolean delete = deleteFile(userCodeFile);
            if (!delete) {
                log.error("删除文件失败,userCodeFilePath={}", userCodeFile.getAbsolutePath());
            }
            return outputResponse;
        } catch (Exception e) {
            log.error("代码沙箱执行异常", e);
            return getErrorResponse(e);
        }
    }

    //模板方法实施开发

    /**
     * 1.把用户代码保存成文件
     * @param code 用户代码
     * @return
     */
    public File CodeSavetoFile(String code){
        //获取到当前用户工作的一个根目录
        String userDir = System.getProperty("user.dir");
        //根目录+下划线+代码保存文件
        String globalCodePathName=userDir+ File.separator+GLOBAL_CODE_DIR_NAME;
        //判断文件是否存在，没有则新建
        if(!FileUtil.exist(globalCodePathName)){
            FileUtil.mkdir(globalCodePathName);
        }
        //把用户的代码隔离存放,保存为文件
        //保存父文件=代码文件夹+下划线+随机文件名
        String userCodeParentPath=globalCodePathName+File.separator+ UUID.randomUUID();
        //父文件下的文件路径
        String userCodePath=userCodeParentPath+File.separator+GLOBAL_JAVA_CLASS_NAME;
        //真正处理的文件
        File userCodeFile=FileUtil.writeString(code,userCodePath,StandardCharsets.UTF_8);

        return userCodeFile;
    }

    /**
     * 2.编译代码
     * @param userCodefile
     */
    public ExecuteMessage compileFile(File  userCodefile){
        String compileCmd = String.format("javac -encoding utf-8 %s", userCodefile.getAbsolutePath());
        try {
            Process compileProcess =Runtime.getRuntime().exec(compileCmd);
            ExecuteMessage executeMessage = ProcessUtils.runProcessAndGetMessage(compileProcess, "编译");
            if(executeMessage.getExitValue()!=0){
                throw new RuntimeException("编译错误");
            }
            return executeMessage;
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    /**
     * 3.执行代码,获得执行结果列表
     * @param userCodeFile
     * @param inputList
     */
    public List<ExecuteMessage> executeFile(File userCodeFile, List<String> inputList){
        String userCodeParentPath=userCodeFile.getParentFile().getAbsolutePath();

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
                throw new RuntimeException("执行错误"+e);
            }
        }
        return executeMessageList;
    }

    /**
     * 4.整理输出结果
     */
    private ExecuteCodeResponse getOutputResponse( List<ExecuteMessage> executeMessageList ) {
        ExecuteCodeResponse executeCodeResponse = new ExecuteCodeResponse();
        List<String> outputList = new ArrayList<>();
        // 取用时最大值，便于判断是否超时
        long maxTime = 0;
        for (ExecuteMessage executeMessage : executeMessageList) {
            // 超时判断
            if (Boolean.TRUE.equals(executeMessage.getTimeout())) {
                executeCodeResponse.setMessage("执行超时");
                executeCodeResponse.setStatus(4);
                break;
            }
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
        return executeCodeResponse;
    }

    /**
     * 5.删除文件/清理文件
     * @param userCodeFile
     */
    public boolean deleteFile(File userCodeFile){
        if (userCodeFile.getParentFile() != null) {
            String userCodeParentPath=userCodeFile.getParentFile().getAbsolutePath();
            boolean del = FileUtil.del(userCodeFile.getParentFile());
            System.out.println("删除" + (del ? "成功" : "失败"));
            return del;
        }
        return true;
    }

    /**
     * 6.错误处理,获取错误响应
     *
     * @param e
     * @return
     */
    protected ExecuteCodeResponse getErrorResponse(Throwable e) {
        ExecuteCodeResponse executeCodeResponse = new ExecuteCodeResponse();
        executeCodeResponse.setOutputList(new ArrayList<>());
        executeCodeResponse.setMessage(e.getMessage());
        //这里执行的是代码沙箱的错误处理
        executeCodeResponse.setStatus(2);
        executeCodeResponse.setJudgeInfo(new JudgeInfo());
        return executeCodeResponse;
    }
}
