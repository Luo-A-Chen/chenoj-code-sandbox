package org.example.chenojcodesandbox.model;

import lombok.Data;

/**
 * 判题信息
 */
@Data
public class JudgeInfo {
    /**
     * 程序执行信息
     */
    private String message;

    /**
     * 消耗内存
     */
    private Long memory;

    /**
     * 消耗时间（毫秒）
     */
    private Long time;

    /**
     * 编译/运行错误详情（stderr、异常信息等）
     */
    private String errorMessage;
}
