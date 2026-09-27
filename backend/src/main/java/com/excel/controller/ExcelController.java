package com.excel.controller;

import cn.hutool.core.bean.BeanUtil;
import com.alibaba.excel.EasyExcel;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.excel.dto.*;
import com.excel.entity.ExcelData;
import com.excel.entity.ImportRecord;
import com.excel.service.ExcelImportService;
import com.excel.service.ReportService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

@RestController
@RequestMapping("/api/excel")
@RequiredArgsConstructor
@Tag(name = "Excel导入管理", description = "Excel数据导入与上报接口")
public class ExcelController {

    private static final Logger logger = LoggerFactory.getLogger(ExcelController.class);

    private final ExcelImportService excelImportService;
    private final ReportService reportService;

    /**
     * 基础文件校验，返回错误提示；校验通过返回null
     */
    private String checkFile(MultipartFile file) {
        if (file.isEmpty()) {
            return "请选择要上传的文件";
        }
        String fileName = file.getOriginalFilename();
        if (fileName == null || (!fileName.endsWith(".xlsx") && !fileName.endsWith(".xls"))) {
            return "仅支持Excel文件（.xlsx或.xls）";
        }
        return null;
    }

    @PostMapping("/validate")
    @Operation(summary = "导入前校验", description = "上传Excel文件进行字段校验，不写入数据库。校验不通过的数据行可下载修正")
    public ApiResponse<ValidationResultDTO> validateExcel(@RequestParam("file") MultipartFile file) {
        try {
            String fileError = checkFile(file);
            if (fileError != null) {
                return ApiResponse.error(fileError);
            }
            ValidationResultDTO result = excelImportService.validateExcel(file);
            return ApiResponse.success(result.getMessage(), result);
        } catch (Exception e) {
            logger.error("Excel校验失败", e);
            return ApiResponse.error("校验失败: " + e.getMessage());
        }
    }

    @GetMapping("/validate/errors/{validationId}")
    @Operation(summary = "下载校验错误数据", description = "将导入前校验未通过的数据行导出为Excel，修正后可直接重新上传")
    public void downloadValidationErrors(@PathVariable String validationId, HttpServletResponse response)
            throws IOException {
        List<ExcelDataDTO> errors = excelImportService.getValidationErrors(validationId);
        if (errors == null) {
            response.setStatus(HttpServletResponse.SC_NOT_FOUND);
            response.setContentType("application/json");
            response.setCharacterEncoding(StandardCharsets.UTF_8.name());
            response.getWriter().write("{\"code\":404,\"message\":\"错误数据不存在或已过期，请重新校验文件\"}");
            return;
        }

        setExcelResponseHeader(response, "校验错误数据_" + LocalDateTime.now()
                .format(DateTimeFormatter.ofPattern("yyyyMMddHHmmss")));

        List<ValidationErrorExportDTO> exportList = new ArrayList<>();
        for (ExcelDataDTO dto : errors) {
            ValidationErrorExportDTO export = new ValidationErrorExportDTO();
            BeanUtil.copyProperties(dto, export);
            exportList.add(export);
        }

        EasyExcel.write(response.getOutputStream(), ValidationErrorExportDTO.class)
                .sheet("校验错误数据")
                .doWrite(exportList);
    }

    @PostMapping("/import")
    @Operation(summary = "导入Excel", description = "上传Excel文件进行数据导入，导入前自动校验，校验不通过则整批取消导入")
    public ApiResponse<ImportResultDTO> importExcel(
            @RequestParam("file") MultipartFile file,
            Authentication authentication) {
        try {
            String fileError = checkFile(file);
            if (fileError != null) {
                return ApiResponse.error(fileError);
            }

            Long userId = (Long) authentication.getPrincipal();
            ImportResultDTO result = excelImportService.importExcel(file, userId);
            return ApiResponse.success("导入完成", result);
        } catch (Exception e) {
            logger.error("Excel导入失败", e);
            return ApiResponse.error("导入失败: " + e.getMessage());
        }
    }

    @GetMapping("/records")
    @Operation(summary = "获取导入记录", description = "分页获取导入记录列表")
    public ApiResponse<Page<ImportRecord>> getImportRecords(
            @RequestParam(defaultValue = "1") Integer pageNum,
            @RequestParam(defaultValue = "10") Integer pageSize) {
        Page<ImportRecord> page = excelImportService.getImportRecords(pageNum, pageSize);
        return ApiResponse.success(page);
    }

    @GetMapping("/data/{batchNo}")
    @Operation(summary = "获取批次数据", description = "根据批次号分页获取数据")
    public ApiResponse<Page<ExcelData>> getDataByBatch(
            @PathVariable String batchNo,
            @RequestParam(defaultValue = "1") Integer pageNum,
            @RequestParam(defaultValue = "10") Integer pageSize) {
        Page<ExcelData> page = excelImportService.getDataByBatch(batchNo, pageNum, pageSize);
        return ApiResponse.success(page);
    }

    @PostMapping("/report/{batchNo}")
    @Operation(summary = "上报数据", description = "将指定批次数据上报到国家平台，上送前再次校验，校验不通过禁止上送")
    public ApiResponse<ReportResultDTO> reportData(@PathVariable String batchNo) {
        try {
            ReportResultDTO result = reportService.reportToNationalPlatform(batchNo);
            return ApiResponse.success("上报完成", result);
        } catch (Exception e) {
            logger.error("数据上报失败", e);
            return ApiResponse.error("上报失败: " + e.getMessage());
        }
    }

    @GetMapping("/report/failed/{batchNo}")
    @Operation(summary = "获取上报失败数据", description = "获取指定批次上报失败的数据")
    public ApiResponse<List<ExcelData>> getFailedReportData(@PathVariable String batchNo) {
        List<ExcelData> failedList = reportService.getFailedReportData(batchNo);
        return ApiResponse.success(failedList);
    }

    @PostMapping("/report/retry/{batchNo}")
    @Operation(summary = "重试上报", description = "重新上报失败的数据")
    public ApiResponse<ReportResultDTO> retryReport(@PathVariable String batchNo) {
        try {
            // 先重置失败数据状态
            reportService.resetFailedData(batchNo);
            // 再次上报
            ReportResultDTO result = reportService.reportToNationalPlatform(batchNo);
            return ApiResponse.success("重新上报完成", result);
        } catch (Exception e) {
            logger.error("重新上报失败", e);
            return ApiResponse.error("重新上报失败: " + e.getMessage());
        }
    }

    @GetMapping("/template")
    @Operation(summary = "下载导入模板", description = "下载Excel导入模板")
    public void downloadTemplate(HttpServletResponse response) throws IOException {
        setExcelResponseHeader(response, "数据导入模板");

        List<ExcelDataDTO> templateData = new ArrayList<>();
        ExcelDataDTO example = new ExcelDataDTO();
        example.setMedicalNo("YB2024001");
        example.setName("张三");
        example.setItemCode("XM001");
        example.setAmount("1000.00");
        example.setVisitDate("2024-01-15");
        example.setOrgCode("ORG001");
        example.setIdCard("110101199001011234");
        example.setPhone("13800138000");
        example.setRemark("示例数据");
        templateData.add(example);

        EasyExcel.write(response.getOutputStream(), ExcelDataDTO.class)
                .sheet("数据导入模板")
                .doWrite(templateData);
    }

    @GetMapping("/export/errors/{batchNo}")
    @Operation(summary = "导出错误数据", description = "导出上报失败的数据为Excel")
    public void exportErrors(@PathVariable String batchNo, HttpServletResponse response) throws IOException {
        List<ExcelData> failedList = reportService.getFailedReportData(batchNo);

        setExcelResponseHeader(response, "上报失败数据_" + batchNo);

        List<ErrorExportDTO> exportList = new ArrayList<>();
        for (ExcelData data : failedList) {
            ErrorExportDTO dto = new ErrorExportDTO();
            dto.setMedicalNo(data.getMedicalNo());
            dto.setName(data.getName());
            dto.setItemCode(data.getItemCode());
            dto.setAmount(data.getAmount() != null ? data.getAmount().toPlainString() : null);
            dto.setVisitDate(data.getVisitDate() != null
                    ? data.getVisitDate().format(DateTimeFormatter.ISO_LOCAL_DATE) : null);
            dto.setOrgCode(data.getOrgCode());
            dto.setIdCard(data.getIdCard());
            dto.setPhone(data.getPhone());
            dto.setRemark(data.getRemark());
            dto.setErrorMsg(data.getReportMessage());
            exportList.add(dto);
        }

        EasyExcel.write(response.getOutputStream(), ErrorExportDTO.class)
                .sheet("上报失败数据")
                .doWrite(exportList);
    }

    /**
     * 设置Excel下载响应头
     */
    private void setExcelResponseHeader(HttpServletResponse response, String fileBaseName) throws IOException {
        response.setContentType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet");
        response.setCharacterEncoding("utf-8");
        String fileName = URLEncoder.encode(fileBaseName, StandardCharsets.UTF_8)
                .replaceAll("\\+", "%20");
        response.setHeader("Content-disposition", "attachment;filename*=utf-8''" + fileName + ".xlsx");
    }
}
