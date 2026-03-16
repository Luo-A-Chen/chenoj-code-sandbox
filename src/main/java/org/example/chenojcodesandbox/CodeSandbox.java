package org.example.chenojcodesandbox;


import org.example.chenojcodesandbox.model.ExecuteCodeRequest;
import org.example.chenojcodesandbox.model.ExecuteCodeResponse;

/**
 * 代码沙箱接口
 */
public interface CodeSandbox {
    /**
     * 执行代码
     * 后续的代码沙箱实现都得继承这个接口，然后只接受ExecuteCodeRequest参数
     * TODO 这里需要考虑，如果代码沙箱服务挂了，应该如何查看并处理
     * todo 这里想考虑将代码沙箱设置成一个微服务项目，单独被调用，然后通过http的这种形式调用代码沙箱进行一个开发
     *
     * @param request
     * @return
     */
    ExecuteCodeResponse executeCode(ExecuteCodeRequest request);
}
