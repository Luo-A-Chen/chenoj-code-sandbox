package org.example.chenojcodesandbox.utils;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SandboxErrorMessagesTest {

    @Test
    void unwrapRuntimePrefix() {
        RuntimeException ex = new RuntimeException("编译错误: cannot find symbol");
        assertEquals("编译错误: cannot find symbol", SandboxErrorMessages.toUserMessage(ex));
    }

    @Test
    void simplifyCompileOutputKeepsErrorsOnly() {
        String raw = "warning: [options] source value 8 is obsolete\n"
                + "/tmp/Main.java:10: error: cannot find symbol\n"
                + "  symbol: class ListNode";
        String simplified = SandboxErrorMessages.simplifyCompileOutput(raw);
        assertTrue(simplified.contains("error: cannot find symbol"));
        assertTrue(!simplified.contains("warning:"));
    }
}
