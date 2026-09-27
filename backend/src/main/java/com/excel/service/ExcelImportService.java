package com.excel.service;

import cn.hutool.core.util.IdUtil;
import com.alibaba.excel.EasyExcel;
import com.alibaba.excel.support.ExcelTypeEnum;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.excel.dto.ExcelDataDTO;
import com.excel.dto.ImportResultDTO;
import com.excel.dto.ValidationResultDTO;
import com.excel.entity.ExcelData;
import com.excel.entity.ImportRecord;
import com.excel.entity.User;
import com.excel.listener.ExcelDataListener;
import com.excel.listener.ExcelValidateListener;
import com.excel.mapper.ExcelDataMapper;
import com.excel.mapper.ImportRecordMapper;
import com.excel.mapper.UserMapper;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.List;

@Service
@RequiredArgsConstructor
public class ExcelImportService {

    private static final Logger logger = LoggerFactory.getLogger(ExcelImportService.class);

    /**
     * 校验结果页面最多返回的错误行数量（完整列表通过下载获取）
     */
    private static final int MAX_ERROR_RETURN = 100;

    /**
     * 导入记录error_details最多记录的错误条数
     */
    private static final int MAX_ERROR_DETAILS = 100;

    private final ExcelDataMapper excelDataMapper;
    private final ImportRecordMapper importRecordMapper;
    private final UserMapper userMapper;
    private final ValidationErrorCache validationErrorCache;

    /**
     * 导入前字段校验：只解析校验，不写入数据库
     */
    public ValidationResultDTO validateExcel(MultipartFile file) throws IOException {
        String fileName = file.getOriginalFilename();
        long fileSize = file.getSize();
        logger.info("开始导入前校验: {}, 大小: {} bytes", fileName, fileSize);

        ExcelValidateListener listener = new ExcelValidateListener();
        readExcel(file, listener);

        int totalCount = listener.getTotalCount();
        int errorCount = listener.getErrorCount();
        List<ExcelDataDTO> errorList = listener.getErrorList();

        // 缓存完整错误列表，供下载
        String validationId = null;
        if (!errorList.isEmpty()) {
            validationId = validationErrorCache.store(errorList);
        }

        boolean passed = totalCount > 0 && errorCount == 0;
        String message;
        if (totalCount == 0) {
            message = "文件中没有数据，请检查文件内容";
        } else if (passed) {
            message = String.format("校验通过，共%d条数据", totalCount);
        } else {
            message = String.format("校验未通过，共%d条数据，%d条校验失败", totalCount, errorCount);
        }

        logger.info("导入前校验完成: {}", message);

        return ValidationResultDTO.builder()
                .passed(passed)
                .totalCount(totalCount)
                .validCount(totalCount - errorCount)
                .errorCount(errorCount)
                .errorList(capErrorList(errorList))
                .errorListTruncated(errorList.size() > MAX_ERROR_RETURN)
                .validationId(validationId)
                .message(message)
                .build();
    }

    /**
     * 获取缓存的校验错误行
     */
    public List<ExcelDataDTO> getValidationErrors(String validationId) {
        return validationErrorCache.get(validationId);
    }

    /**
     * 导入Excel文件（导入前自动校验，校验不通过则整批取消，不进入入库/上送）
     */
    @Transactional(rollbackFor = Exception.class)
    public ImportResultDTO importExcel(MultipartFile file, Long operatorId) throws IOException {
        String fileName = file.getOriginalFilename();
        long fileSize = file.getSize();

        logger.info("开始导入Excel文件: {}, 大小: {} bytes", fileName, fileSize);

        // ========== 导入前字段校验（关卡） ==========
        ExcelValidateListener validateListener = new ExcelValidateListener();
        readExcel(file, validateListener);

        int totalCount = validateListener.getTotalCount();
        List<ExcelDataDTO> validationErrors = validateListener.getErrorList();
        int errorCount = validateListener.getErrorCount();

        // 空文件
        if (totalCount == 0) {
            logger.warn("文件中没有数据，导入已取消");
            return ImportResultDTO.builder()
                    .totalCount(0)
                    .successCount(0)
                    .failCount(0)
                    .errorList(List.of())
                    .status("validation_failed")
                    .message("文件中没有数据，导入已取消")
                    .build();
        }

        // 校验不通过：缓存错误行并整批取消，不写入数据库、不能进入上送
        if (errorCount > 0) {
            String validationId = validationErrorCache.store(validationErrors);
            String message = String.format(
                    "校验未通过：共%d条数据，%d条校验失败，已取消导入。请下载错误数据修正后重新上传",
                    totalCount, errorCount);
            logger.warn("导入前校验未通过，已取消导入: 总计{}条, 错误{}条", totalCount, errorCount);
            return ImportResultDTO.builder()
                    .totalCount(totalCount)
                    .successCount(totalCount - errorCount)
                    .failCount(errorCount)
                    .errorList(capErrorList(validationErrors))
                    .validationId(validationId)
                    .status("validation_failed")
                    .message(message)
                    .build();
        }

        // ========== 校验通过，开始正式导入 ==========
        String batchNo = IdUtil.fastSimpleUUID();

        // 获取操作人信息
        User operator = userMapper.selectById(operatorId);
        String operatorName = operator != null ? operator.getRealName() : "系统";

        // 创建导入记录
        ImportRecord record = new ImportRecord();
        record.setBatchNo(batchNo);
        record.setFileName(fileName);
        record.setFileSize(fileSize);
        record.setStatus(0);
        record.setOperatorId(operatorId);
        record.setOperatorName(operatorName);
        importRecordMapper.insert(record);

        ExcelDataListener listener = new ExcelDataListener(excelDataMapper, batchNo);

        try {
            readExcel(file, listener);

            // 更新导入记录
            record.setTotalCount(listener.getTotalCount());
            record.setSuccessCount(listener.getSuccessCount());
            record.setFailCount(listener.getFailCount());
            record.setStatus(listener.getFailCount() > 0 ? 2 : 1);

            if (!listener.getErrorList().isEmpty()) {
                record.setErrorDetails(buildErrorDetails(listener.getErrorList()));
            }

            importRecordMapper.updateById(record);

            logger.info("Excel导入完成: 总计{}条，成功{}条，失败{}条",
                    listener.getTotalCount(), listener.getSuccessCount(), listener.getFailCount());

            return ImportResultDTO.builder()
                    .batchNo(batchNo)
                    .totalCount(listener.getTotalCount())
                    .successCount(listener.getSuccessCount())
                    .failCount(listener.getFailCount())
                    .errorList(listener.getErrorList())
                    .status(listener.getFailCount() > 0 ? "completed_with_errors" : "completed")
                    .message(String.format("导入完成，总计%d条，成功%d条，失败%d条",
                            listener.getTotalCount(), listener.getSuccessCount(), listener.getFailCount()))
                    .build();

        } catch (Exception e) {
            logger.error("Excel导入失败", e);
            record.setStatus(2);
            record.setErrorDetails("导入失败: " + e.getMessage());
            importRecordMapper.updateById(record);
            throw new RuntimeException("Excel导入失败: " + e.getMessage(), e);
        }
    }

    /**
     * 使用EasyExcel SAX模式读取文件
     */
    private void readExcel(MultipartFile file, com.alibaba.excel.read.listener.ReadListener<ExcelDataDTO> listener)
            throws IOException {
        String fileName = file.getOriginalFilename();
        ExcelTypeEnum excelType = fileName != null && fileName.endsWith(".xlsx")
                ? ExcelTypeEnum.XLSX : ExcelTypeEnum.XLS;

        EasyExcel.read(file.getInputStream(), ExcelDataDTO.class, listener)
                .excelType(excelType)
                .charset(StandardCharsets.UTF_8)
                .sheet()
                .headRowNumber(1)
                .doRead();
    }

    /**
     * 限制返回给前端的错误行数量
     */
    private List<ExcelDataDTO> capErrorList(List<ExcelDataDTO> errorList) {
        if (errorList.size() <= MAX_ERROR_RETURN) {
            return errorList;
        }
        return errorList.subList(0, MAX_ERROR_RETURN);
    }

    /**
     * 构造导入记录中的错误详情文本
     */
    private String buildErrorDetails(List<ExcelDataDTO> errorList) {
        StringBuilder sb = new StringBuilder();
        int limit = Math.min(errorList.size(), MAX_ERROR_DETAILS);
        for (int i = 0; i < limit; i++) {
            ExcelDataDTO error = errorList.get(i);
            sb.append("第").append(error.getRowIndex()).append("行: ")
                    .append(error.getErrorMsg()).append("\n");
        }
        if (errorList.size() > limit) {
            sb.append("... 其余").append(errorList.size() - limit).append("条错误略");
        }
        return sb.toString();
    }

    /**
     * 获取导入记录列表
     */
    public Page<ImportRecord> getImportRecords(Integer pageNum, Integer pageSize) {
        Page<ImportRecord> page = new Page<>(pageNum, pageSize);
        return importRecordMapper.selectPage(page,
                new LambdaQueryWrapper<ImportRecord>()
                        .orderByDesc(ImportRecord::getCreateTime));
    }

    /**
     * 根据批次号获取数据
     */
    public Page<ExcelData> getDataByBatch(String batchNo, Integer pageNum, Integer pageSize) {
        Page<ExcelData> page = new Page<>(pageNum, pageSize);
        return excelDataMapper.selectPage(page,
                new LambdaQueryWrapper<ExcelData>()
                        .eq(ExcelData::getBatchNo, batchNo)
                        .orderByAsc(ExcelData::getId));
    }

    /**
     * 获取待上报数据
     */
    public List<ExcelData> getPendingReportData(String batchNo) {
        return excelDataMapper.selectByBatchAndStatus(batchNo, 0);
    }
}
