package com.excel.dto;

import com.alibaba.excel.annotation.ExcelProperty;
import com.alibaba.excel.annotation.write.style.ColumnWidth;
import lombok.Data;

/**
 * 上报失败数据导出DTO
 */
@Data
public class ErrorExportDTO {

    @ExcelProperty(value = "医保编号", index = 0)
    @ColumnWidth(20)
    private String medicalNo;

    @ExcelProperty(value = "姓名", index = 1)
    @ColumnWidth(12)
    private String name;

    @ExcelProperty(value = "项目编码", index = 2)
    @ColumnWidth(15)
    private String itemCode;

    @ExcelProperty(value = "金额", index = 3)
    @ColumnWidth(12)
    private String amount;

    @ExcelProperty(value = "就诊日期", index = 4)
    @ColumnWidth(15)
    private String visitDate;

    @ExcelProperty(value = "机构编码", index = 5)
    @ColumnWidth(15)
    private String orgCode;

    @ExcelProperty(value = "身份证号", index = 6)
    @ColumnWidth(22)
    private String idCard;

    @ExcelProperty(value = "手机号", index = 7)
    @ColumnWidth(15)
    private String phone;

    @ExcelProperty(value = "备注", index = 8)
    @ColumnWidth(25)
    private String remark;

    @ExcelProperty(value = "错误原因", index = 9)
    @ColumnWidth(40)
    private String errorMsg;
}
