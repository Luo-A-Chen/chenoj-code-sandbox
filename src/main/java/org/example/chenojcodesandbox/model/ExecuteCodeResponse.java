package org.example.chenojcodesandbox.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * 执行代码响应类
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ExecuteCodeResponse {
    /**
     * 沙箱输出用例
     */
    private List<String> outputList;

    /**
     * 沙箱执行信息，沙箱接口信息
     */
    private String message;

    /**
     * 沙箱判断状态
     */
    private Integer status;


    /**
     * 沙箱判断信息
     */
    private JudgeInfo judgeInfo;


}
