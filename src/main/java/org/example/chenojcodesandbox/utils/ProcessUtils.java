package org.example.chenojcodesandbox.utils;

import cn.hutool.core.date.StopWatch;
import org.apache.commons.lang3.StringUtils;
import org.example.chenojcodesandbox.model.ExecuteMessage;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.List;

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
            executeMessage.setExitValue(exitState);
            //正常退出
            if (exitState == 0) {
                System.out.println(opName + "成功");
                //获取命令行输入（拿到进程对应的一个输入流，输入流里是写好对应的程序内容）
                BufferedReader bufferReader = new BufferedReader(new InputStreamReader(runProcess.getInputStream()));
                List<String> outputStrList =new ArrayList<>();
                //逐行读取输出
                String compileOutputLine;
                while ((compileOutputLine = bufferReader.readLine()) != null) {
                    outputStrList.add(compileOutputLine);
                }
                executeMessage.setMessage(StringUtils.join(outputStrList,"\n"));
                System.out.println("编译信息 " + outputStrList);
            } else {
                System.out.println(opName + "失败，错误码：" + exitState);
                BufferedReader bufferReader = new BufferedReader(new InputStreamReader(runProcess.getInputStream()));
                List<String> outputStrList = new ArrayList<>();
                String compileOutputLine;
                while ((compileOutputLine = bufferReader.readLine()) != null) {
                    outputStrList.add(compileOutputLine);
                }
                executeMessage.setMessage(StringUtils.join(outputStrList, "\n"));

                BufferedReader errorBufferReader = new BufferedReader(new InputStreamReader(runProcess.getErrorStream()));
                List<String> errorOutputStrList = new ArrayList<>();
                String errorCompileOutputLine;
                while ((errorCompileOutputLine = errorBufferReader.readLine()) != null) {
                    errorOutputStrList.add(errorCompileOutputLine);
                }
                String errText = StringUtils.join(errorOutputStrList, "\n");
                // javac 等工具把报错写在 stderr，原先未写入 errorMessage，日志里永远是 []
                executeMessage.setErrorMessage(errText);
                System.out.println(opName + " stdout: " + outputStrList);
                System.out.println(opName + " stderr: " + errorOutputStrList);
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
