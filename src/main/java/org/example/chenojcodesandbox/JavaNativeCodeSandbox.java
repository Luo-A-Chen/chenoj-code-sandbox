package org.example.chenojcodesandbox;

import org.example.chenojcodesandbox.model.ExecuteCodeRequest;
import org.example.chenojcodesandbox.model.ExecuteCodeResponse;
import org.example.chenojcodesandbox.model.ExecuteMessage;
import org.springframework.stereotype.Component;
import org.springframework.stereotype.Service;

import java.io.File;
import java.util.List;


/**
 * Java原生代码沙箱实现（直接复用模板方法）
 */
@Component
public class JavaNativeCodeSandbox extends JavaCodeSandboxTemplate {
    @Override
    public ExecuteCodeResponse executeCode(ExecuteCodeRequest request) {
        return super.executeCode(request);
    }

    @Override
    public File CodeSavetoFile(String code) {
        return super.CodeSavetoFile(code);
    }

    @Override
    public ExecuteMessage compileFile(File userCodefile) {
        return super.compileFile(userCodefile);
    }

    @Override
    public List<ExecuteMessage> executeFile(File userCodeFile, List<String> inputList) {
        return super.executeFile(userCodeFile, inputList);
    }

    @Override
    public boolean deleteFile(File userCodeFile) {
        return super.deleteFile(userCodeFile);
    }
}
