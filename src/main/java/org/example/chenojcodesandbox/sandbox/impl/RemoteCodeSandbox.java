package org.example.chenojcodesandbox;

import cn.hutool.http.HttpUtil;
import cn.hutool.json.JSONUtil;
import org.example.chenojcodesandbox.model.ExecuteCodeRequest;
import org.example.chenojcodesandbox.model.ExecuteCodeResponse;
import org.springframework.stereotype.Component;

/**
 * 远程代码沙箱（通过 HTTP 调用远端沙箱服务）
 */
@Component
public class RemoteCodeSandbox implements CodeSandbox {

    private static final String REMOTE_URL = "http://localhost:8090/executeCode";
    private static final String AUTH_REQUEST_HEADER = "auth";
    private static final String AUTH_REQUEST_SECRET = "secretKey";

    @Override
    public ExecuteCodeResponse executeCode(ExecuteCodeRequest request) {
        String requestJson = JSONUtil.toJsonStr(request);
        String responseJson = HttpUtil.createPost(REMOTE_URL)
                .header(AUTH_REQUEST_HEADER, AUTH_REQUEST_SECRET)
                .body(requestJson)
                .execute()
                .body();
        return JSONUtil.toBean(responseJson, ExecuteCodeResponse.class);
    }
}
