package org.example.chenojcodesandbox.utils;

import cn.hutool.core.date.StopWatch;
import org.example.chenojcodesandbox.model.ExecuteMessage;

import java.io.BufferedReader;
import java.io.InputStreamReader;

/**
 * 进程工具类
 */
public class ProcessUtils {

    /**
     * 获取进程执行信息
     *
     * @param runProcess opName运行类型
     * @return ExecuteMessage
     */
    public static ExecuteMessage runProcessAndGetMessage(Process runProcess,String opName) {
        ExecuteMessage executeMessage = new ExecuteMessage();
        try {
            //检测时间
            StopWatch stopWatch = new StopWatch();
            stopWatch.start();

            int exitState = runProcess.waitFor();
            if (exitState == 0) {
                System.out.println(opName + "成功");
                //获取命令行输入（拿到进程对应的一个输入流，输入流里是写好对应的程序内容）
                BufferedReader bufferReader = new BufferedReader(new InputStreamReader(runProcess.getInputStream()));
                StringBuilder stringBuilder = new StringBuilder();
                String compileOutputLine;
                //逐行读取输出
                while ((compileOutputLine = bufferReader.readLine()) != null) {
                    stringBuilder.append(compileOutputLine);
                }
                System.out.println("编译信息 " + stringBuilder);
            } else {
                System.out.println(opName + "失败，错误码：" + exitState);
                BufferedReader bufferReader = new BufferedReader(new InputStreamReader(runProcess.getInputStream()));
                //再把结果拼起来
                StringBuilder stringBuilder = new StringBuilder();
                String compileOutputLine;
                while ((compileOutputLine = bufferReader.readLine()) != null) {
                    stringBuilder.append(compileOutputLine);
                }

                BufferedReader errorBufferReader = new BufferedReader(new InputStreamReader(runProcess.getErrorStream()));
                StringBuilder errorOutputStringBuilder = new StringBuilder();
                String errorCompileOutputLine;
                while ((errorCompileOutputLine = errorBufferReader.readLine()) != null) {
                    errorOutputStringBuilder.append(errorCompileOutputLine);
                }
                executeMessage.setErrorMessage(errorOutputStringBuilder.toString());
            }
            //结束时间
            stopWatch.stop();
            executeMessage.setTime(stopWatch.getLastTaskTimeMillis());
        } catch (Exception e) {
            e.printStackTrace();
        }
        return executeMessage;
    }
}
