package com.excel.listener;

import com.alibaba.excel.context.AnalysisContext;
import com.alibaba.excel.read.listener.ReadListener;
import com.excel.dto.ExcelDataDTO;
import com.excel.utils.ValidationUtils;
import lombok.Getter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;

/**
 * 导入前字段校验监听器
 * 仅解析并校验，不写入数据库，内存中收集所有错误行
 */
public class ExcelValidateListener implements ReadListener<ExcelDataDTO> {

    private static final Logger logger = LoggerFactory.getLogger(ExcelValidateListener.class);

    /**
     * 错误数据列表
     */
    @Getter
    private final List<ExcelDataDTO> errorList = new ArrayList<>();

    /**
     * 总条数
     */
    @Getter
    private int totalCount = 0;

    /**
     * 错误条数
     */
    @Getter
    private int errorCount = 0;

    @Override
    public void invoke(ExcelDataDTO data, AnalysisContext context) {
        totalCount++;
        Integer rowIndex = context.readRowHolder().getRowIndex() + 1;
        data.setRowIndex(rowIndex);

        ValidationUtils.ValidationResult validationResult = ValidationUtils.validate(data);
        if (validationResult.hasErrors()) {
            data.setErrorMsg(validationResult.getErrorMsg());
            errorList.add(data);
            errorCount++;
            logger.warn("第{}行字段校验失败: {}", rowIndex, validationResult.getErrorMsg());
        }
    }

    @Override
    public void doAfterAllAnalysed(AnalysisContext context) {
        logger.info("Excel字段校验解析完成！总计：{}条，失败：{}条", totalCount, errorCount);
    }
}
