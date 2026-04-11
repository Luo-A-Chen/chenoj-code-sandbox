package org.example.chenojcodesandbox.controller;

import org.example.chenojcodesandbox.CodeSandbox;
import org.example.chenojcodesandbox.model.ExecuteCodeRequest;
import org.example.chenojcodesandbox.model.ExecuteCodeResponse;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import javax.annotation.Resource;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

@RestController("/")
public class MainController {
    //定义鉴权请求头和密钥
    private static final String AUTH_REQUEST_HEADER = "auth";
    private static final String AUTH_REQUEST_SECRET = "secretKey";

    @Resource
    private CodeSandbox codeSandbox;

// 安全检查接口
    @GetMapping("/index")
    public String index() {
        return "Hello World!";
    }

    /**
     * 执行代码开放api接口
     * 将代码沙箱通过api接口暴露给外界调用
     */
    @PostMapping("/executeCode")
    ExecuteCodeResponse executeCode(@RequestBody ExecuteCodeRequest executeCodeRequest,
                                    HttpServletRequest request,
                                    HttpServletResponse response) {
        String authHeader = request.getHeader(AUTH_REQUEST_HEADER);
        if (!authHeader.equals(AUTH_REQUEST_SECRET)) {
            response.setStatus(403);
            return null;
        }
        if (executeCodeRequest == null) {
            throw new RuntimeException("参数为空");
        }
        return codeSandbox.executeCode(executeCodeRequest);
    }
}
