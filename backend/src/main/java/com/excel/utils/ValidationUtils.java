package com.excel.utils;

import cn.hutool.core.util.IdcardUtil;
import cn.hutool.core.util.PhoneUtil;
import cn.hutool.core.util.StrUtil;
import com.excel.dto.ExcelDataDTO;
import com.excel.entity.ExcelData;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.time.format.ResolverStyle;
import java.util.ArrayList;
import java.util.List;

/**
 * 导入数据字段校验工具
 * 必填：医保编号、姓名、项目编码、金额、就诊日期、机构编码
 * 金额需为合法数值且不能为负；就诊日期需为合法日期
 */
public class ValidationUtils {

    /**
     * 支持的日期格式
     */
    private static final DateTimeFormatter[] DATE_FORMATTERS = new DateTimeFormatter[]{
            DateTimeFormatter.ofPattern("uuuu-M-d").withResolverStyle(ResolverStyle.STRICT),
            DateTimeFormatter.ofPattern("uuuu/M/d").withResolverStyle(ResolverStyle.STRICT),
            DateTimeFormatter.ofPattern("uuuu.M.d").withResolverStyle(ResolverStyle.STRICT),
            DateTimeFormatter.ofPattern("uuuuMMdd").withResolverStyle(ResolverStyle.STRICT),
            DateTimeFormatter.ofPattern("uuuu年M月d日").withResolverStyle(ResolverStyle.STRICT),
            DateTimeFormatter.ISO_LOCAL_DATE
    };

    /**
     * Excel 1900日期系统的纪元（序列号1对应1900-01-01，这里以1899-12-31为基准做近似换算）
     */
    private static final LocalDate EXCEL_EPOCH = LocalDate.of(1899, 12, 31);

    /**
     * Excel日期序列号合法范围
     */
    private static final long EXCEL_SERIAL_MIN = 2L;
    private static final long EXCEL_SERIAL_MAX = 2958465L;

    /**
     * 校验导入DTO，校验通过时会将金额、就诊日期回填为标准化类型
     */
    public static ValidationResult validate(ExcelDataDTO dto) {
        ValidationResult result = new ValidationResult();

        // 医保编号
        if (StrUtil.isBlank(dto.getMedicalNo())) {
            result.addError("医保编号不能为空");
        }

        // 姓名
        if (StrUtil.isBlank(dto.getName())) {
            result.addError("姓名不能为空");
        } else if (dto.getName().trim().length() > 50) {
            result.addError("姓名长度不能超过50个字符");
        }

        // 项目编码
        if (StrUtil.isBlank(dto.getItemCode())) {
            result.addError("项目编码不能为空");
        }

        // 机构编码
        if (StrUtil.isBlank(dto.getOrgCode())) {
            result.addError("机构编码不能为空");
        }

        // 金额
        if (StrUtil.isBlank(dto.getAmount())) {
            result.addError("金额不能为空");
        } else {
            try {
                BigDecimal amount = parseAmount(dto.getAmount());
                if (amount.compareTo(BigDecimal.ZERO) < 0) {
                    result.addError("金额不能为负数");
                } else {
                    result.setAmount(amount);
                }
            } catch (NumberFormatException e) {
                result.addError("金额格式不正确: " + dto.getAmount().trim());
            }
        }

        // 就诊日期
        if (StrUtil.isBlank(dto.getVisitDate())) {
            result.addError("就诊日期不能为空");
        } else {
            try {
                result.setVisitDate(parseVisitDate(dto.getVisitDate()));
            } catch (DateTimeParseException | IllegalArgumentException e) {
                result.addError("就诊日期格式不正确: " + dto.getVisitDate().trim() + "（支持yyyy-MM-dd等格式）");
            }
        }

        // 身份证号（非必填，但填写了必须合法）
        if (StrUtil.isNotBlank(dto.getIdCard())) {
            if (!IdcardUtil.isValidCard(dto.getIdCard().trim())) {
                result.addError("身份证号格式不正确");
            }
        }

        // 手机号（非必填，但填写了必须合法）
        if (StrUtil.isNotBlank(dto.getPhone())) {
            if (!PhoneUtil.isMobile(dto.getPhone().trim())) {
                result.addError("手机号格式不正确");
            }
        }

        return result;
    }

    /**
     * 校验已入库实体（上送前再次校验）
     */
    public static ValidationResult validateEntity(ExcelData data) {
        ValidationResult result = new ValidationResult();

        if (StrUtil.isBlank(data.getMedicalNo())) {
            result.addError("医保编号不能为空");
        }
        if (StrUtil.isBlank(data.getName())) {
            result.addError("姓名不能为空");
        } else if (data.getName().length() > 50) {
            result.addError("姓名长度不能超过50个字符");
        }
        if (StrUtil.isBlank(data.getItemCode())) {
            result.addError("项目编码不能为空");
        }
        if (StrUtil.isBlank(data.getOrgCode())) {
            result.addError("机构编码不能为空");
        }
        if (data.getAmount() == null) {
            result.addError("金额不能为空");
        } else if (data.getAmount().compareTo(BigDecimal.ZERO) < 0) {
            result.addError("金额不能为负数");
        } else {
            result.setAmount(data.getAmount());
        }
        if (data.getVisitDate() == null) {
            result.addError("就诊日期不能为空");
        } else {
            result.setVisitDate(data.getVisitDate());
        }
        if (StrUtil.isNotBlank(data.getIdCard()) && !IdcardUtil.isValidCard(data.getIdCard())) {
            result.addError("身份证号格式不正确");
        }
        if (StrUtil.isNotBlank(data.getPhone()) && !PhoneUtil.isMobile(data.getPhone())) {
            result.addError("手机号格式不正确");
        }

        return result;
    }

    /**
     * 解析金额：去除货币符号与千分位逗号、“元”等字符
     */
    private static BigDecimal parseAmount(String amountStr) {
        String s = amountStr.trim()
                .replace("￥", "")
                .replace("¥", "")
                .replace(",", "")
                .replace("元", "")
                .trim();
        if (s.isEmpty() || !s.matches("^-?\\d+(\\.\\d+)?$")) {
            throw new NumberFormatException("金额格式不正确");
        }
        double d = Double.parseDouble(s);
        if (Double.isInfinite(d) || Double.isNaN(d)) {
            throw new NumberFormatException("金额格式不正确");
        }
        return new BigDecimal(s);
    }

    /**
     * 解析就诊日期，支持多种文本格式及Excel日期序列号
     */
    private static LocalDate parseVisitDate(String dateStr) {
        String s = dateStr.trim();

        DateTimeParseException last = null;
        for (DateTimeFormatter formatter : DATE_FORMATTERS) {
            try {
                return LocalDate.parse(s, formatter);
            } catch (DateTimeParseException e) {
                last = e;
            }
        }

        // 文本格式均不匹配时，纯数字再按Excel日期序列号处理
        // （8位纯数字已由 yyyyMMdd 格式优先消费）
        if (s.matches("^\\d+$")) {
            long serial = Long.parseLong(s);
            if (serial < EXCEL_SERIAL_MIN || serial > EXCEL_SERIAL_MAX) {
                throw new DateTimeParseException("日期超出范围", s, 0);
            }
            return EXCEL_EPOCH.plusDays(serial);
        }

        throw last != null ? last : new DateTimeParseException("日期格式不正确", s, 0);
    }

    /**
     * 单行校验结果
     */
    public static class ValidationResult {

        private final List<String> errors = new ArrayList<>();

        /**
         * 解析成功后的标准金额
         */
        private BigDecimal amount;

        /**
         * 解析成功后的标准就诊日期
         */
        private LocalDate visitDate;

        public void addError(String error) {
            errors.add(error);
        }

        public boolean hasErrors() {
            return !errors.isEmpty();
        }

        public String getErrorMsg() {
            return String.join("；", errors);
        }

        public List<String> getErrors() {
            return errors;
        }

        public BigDecimal getAmount() {
            return amount;
        }

        public void setAmount(BigDecimal amount) {
            this.amount = amount;
        }

        public LocalDate getVisitDate() {
            return visitDate;
        }

        public void setVisitDate(LocalDate visitDate) {
            this.visitDate = visitDate;
        }
    }
}
