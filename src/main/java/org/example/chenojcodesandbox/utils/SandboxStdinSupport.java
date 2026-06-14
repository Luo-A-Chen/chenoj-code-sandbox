package org.example.chenojcodesandbox.utils;

import cn.hutool.core.io.FileUtil;

import java.io.File;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;

/**
 * 判题用例通过标准输入喂给用户程序（与 Solution.run / cin 等入口一致）。
 */
public final class SandboxStdinSupport {

    public static final String STDIN_FILE_NAME = ".judge.stdin.in";

    private SandboxStdinSupport() {
    }

    /**
     * 将用例输入写入工作目录，供 {@code java Main < stdin.in} 使用。
     */
    public static void writeStdinFile(File workDir, String input) {
        File stdinFile = new File(workDir, STDIN_FILE_NAME);
        String content = input == null ? "" : input;
        FileUtil.writeString(content, stdinFile, StandardCharsets.UTF_8);
    }

    public static void deleteStdinFile(File workDir) {
        File stdinFile = new File(workDir, STDIN_FILE_NAME);
        if (stdinFile.exists()) {
            FileUtil.del(stdinFile);
        }
    }

    /**
     * 原生进程：启动后写入 stdin 并关闭，再收集输出。
     */
    public static void feedStdin(Process process, String input) throws IOException {
        OutputStream stdin = process.getOutputStream();
        if (input != null && !input.isEmpty()) {
            stdin.write(input.getBytes(StandardCharsets.UTF_8));
        }
        stdin.flush();
        stdin.close();
    }
}
