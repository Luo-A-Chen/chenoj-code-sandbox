package org.example.chenojcodesandbox.utils;

import org.example.chenojcodesandbox.model.ExecuteMessage;

import java.io.BufferedReader;
import java.io.InputStreamReader;

public class ProcessUtils {

    /**
     * 进程工具类
     * 获取进程执行信息
     *
     * @param runProcess
     * @return
     */
    public static ExecuteMessage runProcessAndGetMessage(Process runProcess,String opName) {
        ExecuteMessage executeMessage = new ExecuteMessage();
        try {
            // 1.等待程序执行，获取错误码
            int exitValue = runProcess.waitFor();
            if (exitValue == 0) {
                System.out.println("编译成功");
                //分批获取进程的正常输出
                BufferedReader bufferReader = new BufferedReader(new InputStreamReader(runProcess.getInputStream()));
                StringBuilder compileOutputStringBuilder = new StringBuilder();
                //逐行读取输出
                String compilOutputLine;
                while ((compilOutputLine = bufferReader.readLine()) != null) {
                    compileOutputStringBuilder.append(compilOutputLine);
                }
                executeMessage.setMessage(compileOutputStringBuilder.toString());
                System.out.println("编译信息: " + compileOutputStringBuilder);
            } else {
                // 异常输出
                System.out.println("编译失败.错误码: " + exitValue);

                //错误信息进程输出
                BufferedReader errorBufferReader = new BufferedReader(new InputStreamReader(runProcess.getErrorStream()));
                StringBuilder errorCompileOutputStringBuilder = new StringBuilder();

                //循环读取
                String errorCompileOutputline;
                while ((errorCompileOutputline = errorBufferReader.readLine()) != null) {
                    errorCompileOutputStringBuilder.append(errorCompileOutputline);
                }
                executeMessage.setErrorMessage(errorCompileOutputStringBuilder.toString());
                System.out.println("编译错误信息: " + errorCompileOutputStringBuilder);
            }
            executeMessage.setExitValue(exitValue);
        } catch (Exception e) {
            e.printStackTrace();
        }
        return executeMessage;
    }
}
