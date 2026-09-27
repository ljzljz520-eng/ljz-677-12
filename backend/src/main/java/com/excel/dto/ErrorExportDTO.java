package com.excel.dto;

import com.alibaba.excel.annotation.ExcelProperty;
import com.alibaba.excel.annotation.write.style.ColumnWidth;
import lombok.Data;

/**
 * 错误行导出DTO
 * 列顺序与导入模板完全一致（0-10列），行号与错误原因附在最后，
 * 用户修正后可直接按模板格式重新上传
 */
@Data
public class ErrorExportDTO {

    @ExcelProperty(value = "医保编号", index = 0)
    @ColumnWidth(18)
    private String insuranceNo;

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
    @ColumnWidth(14)
    private String visitDate;

    @ExcelProperty(value = "机构编码", index = 5)
    @ColumnWidth(16)
    private String orgCode;

    @ExcelProperty(value = "数据编号", index = 6)
    @ColumnWidth(15)
    private String dataCode;

    @ExcelProperty(value = "身份证号", index = 7)
    @ColumnWidth(22)
    private String idCard;

    @ExcelProperty(value = "手机号", index = 8)
    @ColumnWidth(15)
    private String phone;

    @ExcelProperty(value = "地址", index = 9)
    @ColumnWidth(30)
    private String address;

    @ExcelProperty(value = "备注", index = 10)
    @ColumnWidth(25)
    private String remark;

    @ExcelProperty(value = "行号", index = 11)
    @ColumnWidth(8)
    private Integer rowIndex;

    @ExcelProperty(value = "错误原因", index = 12)
    @ColumnWidth(45)
    private String errorMsg;
}
