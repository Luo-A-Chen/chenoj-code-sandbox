package org.example.chenojcodesandbox.utils;

import cn.hutool.core.util.StrUtil;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

/**
 * 将沙箱异常、javac 输出整理为面向用户的简短提示。
 */
public final class SandboxErrorMessages {

    private static final Pattern RUNTIME_PREFIX =
            Pattern.compile("^java\\.lang\\.RuntimeException:\\s*");

    private SandboxErrorMessages() {
    }

    public static String toUserMessage(Throwable e) {
        if (e == null) {
            return "系统错误";
        }
        String msg = deepestMessage(e);
        if (StrUtil.isBlank(msg)) {
            return "系统错误";
        }
        msg = RUNTIME_PREFIX.matcher(msg).replaceFirst("");
        if (msg.startsWith("编译错误")) {
            return msg;
        }
        if (msg.startsWith("执行错误")) {
            return msg;
        }
        return msg;
    }

    /**
     * 从 javac 输出中提取 error 行，去掉 warning 与冗长路径提示。
     */
    public static String simplifyCompileOutput(String raw) {
        if (StrUtil.isBlank(raw)) {
            return "";
        }
        List<String> errors = new ArrayList<>();
        for (String line : raw.split("\\R")) {
            String trimmed = line.trim();
            if (trimmed.isEmpty()) {
                continue;
            }
            if (trimmed.contains("warning:") && !trimmed.contains("error:")) {
                continue;
            }
            if (trimmed.contains("error:")) {
                errors.add(trimmed);
            }
        }
        if (errors.isEmpty()) {
            return StrUtil.trim(raw);
        }
        int limit = Math.min(2, errors.size());
        return String.join("; ", errors.subList(0, limit));
    }

    private static String deepestMessage(Throwable e) {
        Throwable cur = e;
        String last = null;
        while (cur != null) {
            if (StrUtil.isNotBlank(cur.getMessage())) {
                last = cur.getMessage();
            }
            Throwable next = cur.getCause();
            if (next == null || next == cur) {
                break;
            }
            cur = next;
        }
        return last;
    }
}
