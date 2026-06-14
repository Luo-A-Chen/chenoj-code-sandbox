package org.example.chenojcodesandbox.model;

import lombok.Data;

/**
 * 题目判题配置（与 OJ 后端 JudgeConfig 字段一致，单位：时间 ms、内存 KB）
 */
@Data
public class JudgeConfig {

    private String methodName;

    private java.util.List<String> paramTypes;

    private String returnType;

    private Long timeLimit;

    private Long memoryLimit;
}
