package org.example.chenojcodesandbox;

import cn.hutool.core.io.FileUtil;
import cn.hutool.core.io.resource.ResourceUtil;
import org.example.chenojcodesandbox.model.ExecuteCodeRequest;
import org.example.chenojcodesandbox.model.ExecuteCodeResponse;
import org.example.chenojcodesandbox.model.ExecuteMessage;
import org.example.chenojcodesandbox.utils.ProcessUtils;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;

/**
 * Java原生代码沙箱
 */
public class JavaNativeCodeSandbox implements CodeSandbox {
    // 全局代码存放的目录，写死了但是设置全局变量，防止魔法值
    private static final String GLOBAL_CODE_DIR_NAME = "tempCode";
    // 全局代码文件名,执行的代码只能运行在该目录下，减少读取用户输入的类名
    private static final String GLOBAL_JAVA_CLASS_NAME = "Main.java";

    public static void main(String[] args) {
        JavaNativeCodeSandbox javaNativeCodeSandbox = new JavaNativeCodeSandbox();
        //1.构造一个代码沙县请求类
        ExecuteCodeRequest executeCodeRequest = new ExecuteCodeRequest();
        //2.读取题目输入用例（这里先测试写死）
        executeCodeRequest.setInputList(Arrays.asList("1 2","3 4"));
        //3.读取用户写入代码
        String code = ResourceUtil.readStr("testCode/simpleComputeArgs/Main.java", StandardCharsets.UTF_8);
        executeCodeRequest.setCode(code);
        executeCodeRequest.setLanguage("java");
        //4.调用沙箱处理，将用户请求编译
        ExecuteCodeResponse executeCodeResponse = javaNativeCodeSandbox.executeCode(executeCodeRequest);
        System.out.println(executeCodeResponse);
    }


    @Override
    public ExecuteCodeResponse executeCode(ExecuteCodeRequest request) {
        // 1.题目输入用例列表+提交的代码+编程语言
        List<String> inputList =request.getInputList();
        String code=request.getCode();
        String language=request.getLanguage();

        //用户的代码保存成文件
        String userDir = System.getProperty("user.dir");
        String globalCodePathName=userDir+ File.separator+GLOBAL_CODE_DIR_NAME;
        //判断全局代码目录是否存在，没有则新建
        if(!FileUtil.exist(globalCodePathName)){
            FileUtil.mkdir(globalCodePathName);
        }
        //把用户的代码隔离存放,保存为文件
        String userCodeParentPath=globalCodePathName+File.separator+ UUID.randomUUID();
        String userCodePath=userCodeParentPath+File.separator+GLOBAL_JAVA_CLASS_NAME;
        File userCodeFile=FileUtil.writeString(code,userCodePath,StandardCharsets.UTF_8);

        //2.编译代码，得到class文件
        //调用cmd命令去运行代码文件
        String compileCmd =String.format("javac -encoding utf-8 -d %s %s", userCodeParentPath, userCodeFile.getAbsolutePath());
        try {
            Process compileProcess =Runtime.getRuntime().exec(compileCmd);
            int exitState=compileProcess.waitFor();
            if (exitState== 0) {
                //分批获取正常进程的信息
                System.out.println("编译成功");
                BufferedReader bufferReader = new BufferedReader(new InputStreamReader(compileProcess.getInputStream()));
                StringBuilder stringBuilder = new StringBuilder();
                String compileOutputLine;
                while((compileOutputLine=bufferReader.readLine())!=null){
                    stringBuilder.append(compileOutputLine);
                }
                System.out.println("编译信息: " + stringBuilder);
            }else{
                System.out.println("编译失败");
                BufferedReader errorBufferReader = new BufferedReader(new InputStreamReader(compileProcess.getErrorStream()));
                StringBuilder errorStringBuilder = new StringBuilder();
                String errorCompileOutputLine;
                while ((errorCompileOutputLine = errorBufferReader.readLine()) != null) {
                    errorStringBuilder.append(errorCompileOutputLine);
                }
                System.out.println("编译错误信息: " + errorStringBuilder);
            }
        } catch (Exception e) {
            throw new RuntimeException("编译过程异常",e);
        }

        //3.执行代码，得到输出结果
        for (String inputArgs : inputList) {
            String runCmd = String.format("java -Dfile.encoding=UTF-8 -cp %s Main %s", userCodeParentPath, inputArgs);
            try {
                Process runProcess = Runtime.getRuntime().exec(runCmd);
                ExecuteMessage executeMessage = ProcessUtils.runProcessAndGetMessage(runProcess, "运行");
                System.out.println("运行结果: " + executeMessage);
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        }
        //4. 收集整理输出结果

        //5.文件清理，释放空间

        //6. 错误处理，提升程序健壮性

        return null;
    }
}
