package org.example.chenojcodesandbox.sandbox.template;

import cn.hutool.core.io.FileUtil;
import cn.hutool.core.util.StrUtil;
import lombok.extern.slf4j.Slf4j;
import org.example.chenojcodesandbox.model.ExecuteCodeRequest;
import org.example.chenojcodesandbox.model.ExecuteCodeResponse;
import org.example.chenojcodesandbox.model.ExecuteMessage;
import org.example.chenojcodesandbox.model.JudgeConfig;
import org.example.chenojcodesandbox.model.JudgeInfo;
import org.example.chenojcodesandbox.sandbox.CodeSandbox;
import org.example.chenojcodesandbox.sandbox.SandboxLimits;
import org.example.chenojcodesandbox.utils.JavaFunctionCodeAssembler;
import org.example.chenojcodesandbox.utils.ProcessUtils;
import org.example.chenojcodesandbox.utils.SandboxErrorMessages;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * java模版方法的实现
 */
@Slf4j
public abstract class JavaCodeSandboxTemplate implements CodeSandbox {

    private static final String GLOBAL_CODE_DIR_NAME = "tempCode";
    private static final String GLOBAL_JAVA_CLASS_NAME = "Main.java";

    @Override
    public ExecuteCodeResponse executeCode(ExecuteCodeRequest request) {
        List<String> inputList = request.getInputList();
        String code = request.getCode();
        JudgeConfig judgeConfig = request.getJudgeConfig();

        File userCodeFile = null;
        try {
            String codeToCompile = code;
            String language = StrUtil.blankToDefault(request.getLanguage(), "java");
            if ("java".equalsIgnoreCase(language)) {
                codeToCompile = JavaFunctionCodeAssembler.assemble(code, judgeConfig);
            } else {
                throw new IllegalArgumentException("当前仅支持 Java 方法题");
            }
            userCodeFile = CodeSavetoFile(codeToCompile);
            ExecuteMessage compileFileExecuteMessage = compileFile(userCodeFile);
            System.out.println(compileFileExecuteMessage);
            List<ExecuteMessage> executeMessageList = executeFile(userCodeFile, inputList, judgeConfig);
            return getOutputResponse(executeMessageList);
        } catch (Exception e) {
            log.error("代码沙箱执行异常", e);
            return getErrorResponse(e);
        } finally {
            if (userCodeFile != null) {
                boolean delete = deleteFile(userCodeFile);
                if (!delete) {
                    log.error("删除文件失败,userCodeFilePath={}", userCodeFile.getAbsolutePath());
                }
            }
        }
    }

    public File CodeSavetoFile(String code) {
        String userDir = System.getProperty("user.dir");
        String globalCodePathName = userDir + File.separator + GLOBAL_CODE_DIR_NAME;
        if (!FileUtil.exist(globalCodePathName)) {
            FileUtil.mkdir(globalCodePathName);
        }
        String userCodeParentPath = globalCodePathName + File.separator + UUID.randomUUID();
        String userCodePath = userCodeParentPath + File.separator + GLOBAL_JAVA_CLASS_NAME;
        return FileUtil.writeString(code, userCodePath, StandardCharsets.UTF_8);
    }

    public ExecuteMessage compileFile(File userCodefile) {
        String compileCmd = String.format(
                "javac -encoding utf-8 -source 1.8 -target 1.8 -Xlint:-options %s",
                userCodefile.getAbsolutePath());
        try {
            Process compileProcess = Runtime.getRuntime().exec(compileCmd);
            ExecuteMessage executeMessage = ProcessUtils.runProcessAndGetMessage(compileProcess, "编译");
            if (executeMessage.getExitValue() != 0) {
                String detail = StrUtil.blankToDefault(executeMessage.getErrorMessage(),
                        StrUtil.nullToEmpty(executeMessage.getMessage()));
                detail = SandboxErrorMessages.simplifyCompileOutput(StrUtil.trim(detail));
                throw new RuntimeException(StrUtil.isBlank(detail) ? "编译错误" : "编译错误: " + detail);
            }
            return executeMessage;
        } catch (RuntimeException e) {
            throw e;
        } catch (Exception e) {
            throw new RuntimeException("编译错误: " + e.getMessage(), e);
        }
    }

    /**
     * 执行用户代码；子类按题目 judgeConfig 限制执行，且不得超过 {@link SandboxLimits} 平台硬上限。
     */
    public abstract List<ExecuteMessage> executeFile(File userCodeFile, List<String> inputList, JudgeConfig judgeConfig);

    protected ExecuteCodeResponse getOutputResponse(List<ExecuteMessage> executeMessageList) {
        ExecuteCodeResponse executeCodeResponse = new ExecuteCodeResponse();
        List<String> outputList = new ArrayList<>();
        long maxTime = 0;
        long maxMemoryKb = 0;

        for (ExecuteMessage executeMessage : executeMessageList) {
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
            Long memory = executeMessage.getMemory();
            if (memory != null) {
                maxMemoryKb = Math.max(maxMemoryKb, memory);
            }
        }

        if (outputList.size() == executeMessageList.size()
                && executeCodeResponse.getStatus() == null) {
            executeCodeResponse.setStatus(1);
        }
        executeCodeResponse.setOutputList(outputList);
        JudgeInfo judgeInfo = new JudgeInfo();
        judgeInfo.setTime(maxTime);
        judgeInfo.setMemory(maxMemoryKb);
        if (executeCodeResponse.getStatus() != null && executeCodeResponse.getStatus() != 1) {
            judgeInfo.setErrorMessage(executeCodeResponse.getMessage());
        }
        executeCodeResponse.setJudgeInfo(judgeInfo);
        return executeCodeResponse;
    }

    public boolean deleteFile(File userCodeFile) {
        if (userCodeFile.getParentFile() != null) {
            boolean del = FileUtil.del(userCodeFile.getParentFile());
            System.out.println("删除" + (del ? "成功" : "失败"));
            return del;
        }
        return true;
    }

    protected ExecuteCodeResponse getErrorResponse(Throwable e) {
        ExecuteCodeResponse executeCodeResponse = new ExecuteCodeResponse();
        executeCodeResponse.setOutputList(new ArrayList<>());
        String msg = SandboxErrorMessages.toUserMessage(e);
        executeCodeResponse.setMessage(msg);
        executeCodeResponse.setStatus(2);
        JudgeInfo judgeInfo = new JudgeInfo();
        judgeInfo.setErrorMessage(msg);
        executeCodeResponse.setJudgeInfo(judgeInfo);
        return executeCodeResponse;
    }
}
