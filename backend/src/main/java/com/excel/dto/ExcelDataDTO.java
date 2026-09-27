package com.excel.dto;

import com.alibaba.excel.annotation.ExcelProperty;
import com.alibaba.excel.annotation.write.style.ColumnWidth;
import lombok.Data;

/**
 * Excel导入数据DTO
 * 金额、就诊日期使用String接收原始输入，由ValidationUtils逐行校验格式，
 * 避免类型转换异常中断解析，保证格式错误的行能被逐行列出
 */
@Data
public class ExcelDataDTO {

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

    /**
     * 行号，用于错误定位
     */
    private Integer rowIndex;

    /**
     * 错误信息
     */
    private String errorMsg;
}
