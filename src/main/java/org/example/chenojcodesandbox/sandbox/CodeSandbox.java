package org.example.chenojcodesandbox;


import org.example.chenojcodesandbox.model.ExecuteCodeRequest;
import org.example.chenojcodesandbox.model.ExecuteCodeResponse;

/**
 * 代码沙箱接口
 * 也方便了后续代码沙箱的切换了
 * 
 */
public interface CodeSandbox {
    /**
     * 执行代码
     * 后续的代码沙箱实现都得继承这个接口，然后只接受ExecuteCodeRequest参数
     * TODO 这里需要考虑，如果代码沙箱服务挂了，应该如何查看并处理
     * @param request
     * @return
     */
    ExecuteCodeResponse executeCode(ExecuteCodeRequest request);
}
