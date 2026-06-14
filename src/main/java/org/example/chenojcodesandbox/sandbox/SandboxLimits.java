package org.example.chenojcodesandbox.sandbox;

import org.example.chenojcodesandbox.model.JudgeConfig;

/**
 * 沙箱平台级硬上限；题目限制不得超过此值。
 */
public final class SandboxLimits {

    /** 单题/单次执行最大等待时间（毫秒） */
    public static final long MAX_TIME_MS = 10000L;

    /** 题目内存上限（KB），64MB */
    public static final long MAX_MEMORY_KB = 64L * 1024;

    /** Docker 容器内存硬上限（字节），64MB */
    public static final long MAX_MEMORY_BYTES = MAX_MEMORY_KB * 1024L;

    /** 原生沙箱 JVM 堆上限（MB） */
    public static final int MAX_NATIVE_HEAP_MB = 64;

    private SandboxLimits() {
    }

    public static long effectiveTimeMs(JudgeConfig config) {
        if (config == null || config.getTimeLimit() == null || config.getTimeLimit() <= 0) {
            return MAX_TIME_MS;
        }
        return Math.min(config.getTimeLimit(), MAX_TIME_MS);
    }

    public static long effectiveMemoryBytes(JudgeConfig config) {
        if (config == null || config.getMemoryLimit() == null || config.getMemoryLimit() <= 0) {
            return MAX_MEMORY_BYTES;
        }
        long questionBytes = config.getMemoryLimit() * 1024L;
        return Math.min(questionBytes, MAX_MEMORY_BYTES);
    }

    public static int effectiveNativeHeapMb(JudgeConfig config) {
        long bytes = effectiveMemoryBytes(config);
        int mb = (int) Math.max(1, bytes / (1024L * 1024L));
        return Math.min(mb, MAX_NATIVE_HEAP_MB);
    }

    /** Docker stats 字节 → 判题统一使用的 KB */
    public static long bytesToKb(long bytes) {
        if (bytes <= 0) {
            return 0L;
        }
        return (bytes + 1023) / 1024;
    }
}
