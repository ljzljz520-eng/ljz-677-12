package com.excel.service;

import cn.hutool.core.util.IdUtil;
import cn.hutool.core.util.StrUtil;
import com.alibaba.excel.EasyExcel;
import com.alibaba.excel.support.ExcelTypeEnum;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.excel.dto.ExcelDataDTO;
import com.excel.dto.ImportResultDTO;
import com.excel.entity.ExcelData;
import com.excel.entity.ImportRecord;
import com.excel.entity.User;
import com.excel.listener.ExcelDataListener;
import com.excel.mapper.ExcelDataMapper;
import com.excel.mapper.ImportRecordMapper;
import com.excel.mapper.UserMapper;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.List;

@Service
@RequiredArgsConstructor
public class ExcelImportService {

    private static final Logger logger = LoggerFactory.getLogger(ExcelImportService.class);

    private final ExcelDataMapper excelDataMapper;
    private final ImportRecordMapper importRecordMapper;
    private final UserMapper userMapper;
    private final ObjectMapper objectMapper;

    /**
     * 导入Excel文件
     * 流程：第一遍解析只做导入前字段校验（不写库），校验全部通过后才进行第二遍解析入库。
     * 校验不过时整批拒绝导入，该批次不能进入上送步骤，错误行可下载修正后重新上传。
     */
    @Transactional(rollbackFor = Exception.class)
    public ImportResultDTO importExcel(MultipartFile file, Long operatorId) throws IOException {
        String batchNo = IdUtil.fastSimpleUUID();
        String fileName = file.getOriginalFilename();
        long fileSize = file.getSize();

        logger.info("开始导入Excel文件: {}, 大小: {} bytes, 批次号: {}", fileName, fileSize, batchNo);

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

        // 根据文件后缀判断Excel类型
        ExcelTypeEnum excelType = fileName != null && fileName.endsWith(".xlsx")
                ? ExcelTypeEnum.XLSX : ExcelTypeEnum.XLS;

        // ========== 第一遍：导入前字段校验，不写入数据库 ==========
        ExcelDataListener validateListener = new ExcelDataListener(excelDataMapper, batchNo, true);
        try {
            EasyExcel.read(file.getInputStream(), ExcelDataDTO.class, validateListener)
                    .excelType(excelType)
                    .charset(StandardCharsets.UTF_8)
                    .sheet()
                    .headRowNumber(1)
                    .doRead();
        } catch (Exception e) {
            logger.error("Excel文件解析失败", e);
            record.setStatus(2);
            record.setErrorDetails("文件解析失败: " + e.getMessage());
            importRecordMapper.updateById(record);
            throw new RuntimeException("Excel文件解析失败: " + e.getMessage(), e);
        }

        // 校验不过：整批拒绝导入，不能进入上送步骤
        if (validateListener.getFailCount() > 0) {
            record.setTotalCount(validateListener.getTotalCount());
            record.setSuccessCount(0);
            record.setFailCount(validateListener.getFailCount());
            record.setStatus(2);
            // 错误行以JSON结构存储，供"下载错误行"使用
            record.setErrorDetails(objectMapper.writeValueAsString(validateListener.getErrorList()));
            importRecordMapper.updateById(record);

            logger.info("字段校验未通过: 总计{}条，错误{}条，数据未导入",
                    validateListener.getTotalCount(), validateListener.getFailCount());

            String message = String.format("校验未通过：共%d条数据，%d条存在错误，数据未导入，不能进入上送步骤。请下载错误行修正后重新上传",
                    validateListener.getTotalCount(), validateListener.getFailCount());
            if (validateListener.getFailCount() > validateListener.getErrorList().size()) {
                message += String.format("（错误明细仅保留前%d条）", validateListener.getErrorList().size());
            }

            return ImportResultDTO.builder()
                    .batchNo(batchNo)
                    .totalCount(validateListener.getTotalCount())
                    .successCount(0)
                    .failCount(validateListener.getFailCount())
                    .errorList(validateListener.getErrorList())
                    .status("validation_failed")
                    .message(message)
                    .build();
        }

        // ========== 第二遍：校验通过，批量入库 ==========
        ExcelDataListener listener = new ExcelDataListener(excelDataMapper, batchNo, false);
        try {
            EasyExcel.read(file.getInputStream(), ExcelDataDTO.class, listener)
                    .excelType(excelType)
                    .charset(StandardCharsets.UTF_8)
                    .sheet()
                    .headRowNumber(1)
                    .doRead();

            // 更新导入记录
            record.setTotalCount(listener.getTotalCount());
            record.setSuccessCount(listener.getSuccessCount());
            record.setFailCount(listener.getFailCount());
            record.setStatus(listener.getFailCount() > 0 ? 2 : 1);

            if (!listener.getErrorList().isEmpty()) {
                record.setErrorDetails(objectMapper.writeValueAsString(listener.getErrorList()));
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
     * 获取导入记录列表
     */
    public Page<ImportRecord> getImportRecords(Integer pageNum, Integer pageSize) {
        Page<ImportRecord> page = new Page<>(pageNum, pageSize);
        return importRecordMapper.selectPage(page,
                new LambdaQueryWrapper<ImportRecord>()
                        .orderByDesc(ImportRecord::getCreateTime));
    }

    /**
     * 获取批次的校验错误行（用于下载修正后重新导入）
     */
    public List<ExcelDataDTO> getValidationErrorRows(String batchNo) {
        ImportRecord record = importRecordMapper.selectOne(
                new LambdaQueryWrapper<ImportRecord>()
                        .eq(ImportRecord::getBatchNo, batchNo));
        if (record == null || StrUtil.isBlank(record.getErrorDetails())) {
            return Collections.emptyList();
        }
        try {
            return objectMapper.readValue(record.getErrorDetails(),
                    new TypeReference<List<ExcelDataDTO>>() {
                    });
        } catch (Exception e) {
            logger.warn("批次{}的错误详情非结构化数据，无法导出错误行: {}", batchNo, e.getMessage());
            return Collections.emptyList();
        }
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
