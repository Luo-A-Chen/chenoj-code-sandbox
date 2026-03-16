package org.example.chenojcodesandbox;

import cn.hutool.core.io.FileUtil;
import cn.hutool.core.io.resource.ResourceUtil;
import org.example.chenojcodesandbox.model.ExecuteCodeRequest;
import org.example.chenojcodesandbox.model.ExecuteCodeResponse;

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
        ExecuteCodeRequest executeCodeRequest = new ExecuteCodeRequest();
        executeCodeRequest.setInputList(Arrays.asList("1 2","3 4"));
        String code = ResourceUtil.readStr("testCode/simpleComputeArgs", StandardCharsets.UTF_8);
        executeCodeRequest.setCode(code);
        executeCodeRequest.setLanguage("java");
        ExecuteCodeResponse executeCodeResponse = javaNativeCodeSandbox.executeCode(executeCodeRequest);
        System.out.println(executeCodeResponse);
    }


    @Override
    public ExecuteCodeResponse executeCode(ExecuteCodeRequest request) {
        List<String> inputList =request.getInputList();
        String code=request.getCode();
        String language=request.getLanguage();

        String userDir = System.getProperty("user.dir");
        String globalCodePathName=userDir+ File.separator+GLOBAL_CODE_DIR_NAME;
        //判断全局代码目录是否存在，没有则新建
        if(!FileUtil.exist(globalCodePathName)){
            FileUtil.mkdir(globalCodePathName);
        }

        //把用户的代码隔离存放
        String userCodeParentPath=globalCodePathName+File.separator+ UUID.randomUUID();
        String userCodePath=userCodeParentPath+File.separator+GLOBAL_JAVA_CLASS_NAME;
        File userCodeFile=FileUtil.writeString(code,userCodePath,StandardCharsets.UTF_8);

        //2.编译代码，得到class文件,调用cmd命令去运行代码文件
        //也同样是从cmd中获得执行的结果
        String compileCmd =String.format("javac -encoding utf-8 %s",userCodeFile.getAbsolutePath());
        try {
            Process compileProcess =Runtime.getRuntime().exec(compileCmd);
            int exitValue = compileProcess.waitFor();
            if(exitValue==0){
                System.out.println("编译成功");
                //分批获取进程的正常输出
                BufferedReader bufferReader=new BufferedReader(new InputStreamReader(compileProcess.getInputStream()));
                //循环读取
                String compilOutputLine;
                while((compilOutputLine=bufferReader.readLine())!=null){
                    System.out.println(compilOutputLine);
                }
            }else{
                System.out.println("编译失败.错误码: "+exitValue);
                BufferedReader bufferReader=new BufferedReader(new InputStreamReader(compileProcess.getInputStream()));
                //循环读取
                String compilOutputLine;
                while((compilOutputLine=bufferReader.readLine())!=null){
                    System.out.println(compilOutputLine);
                }

                //错误信息进程输出
                BufferedReader errorBufferReader=new BufferedReader(new InputStreamReader(compileProcess.getErrorStream()));
                //循环读取
                String errorBufferOutputline;
                while((compilOutputLine=bufferReader.readLine())!=null){
                    System.out.println(compilOutputLine);
                }
            }
        } catch (IOException | InterruptedException e) {
            throw new RuntimeException(e);
        }
        return null;
    }
}
