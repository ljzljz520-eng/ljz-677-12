package com.excel.dto;

import lombok.Builder;
import lombok.Data;

import java.util.List;

/**
 * 导入前字段校验结果
 */
@Data
@Builder
public class ValidationResultDTO {

    /**
     * 是否校验通过
     */
    private Boolean passed;

    /**
     * 总记录数
     */
    private Integer totalCount;

    /**
     * 校验通过条数
     */
    private Integer validCount;

    /**
     * 校验失败条数
     */
    private Integer errorCount;

    /**
     * 错误数据列表（仅返回前若干条用于页面展示，完整列表可下载）
     */
    private List<ExcelDataDTO> errorList;

    /**
     * 错误列表是否被截断
     */
    private Boolean errorListTruncated;

    /**
     * 校验缓存ID，用于下载完整错误数据
     */
    private String validationId;

    /**
     * 提示消息
     */
    private String message;
}
