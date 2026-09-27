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
 * 导入前字段校验工具
 * 必填字段：医保编号、姓名、项目编码、金额、就诊日期、机构编码
 * 格式校验：金额须为合法数字，就诊日期须为合法日期
 */
public class ValidationUtils {

    /**
     * 支持的就诊日期格式（严格模式，拒绝 2023-02-30 之类的非法日期）
     */
    private static final List<DateTimeFormatter> DATE_FORMATTERS = List.of(
            DateTimeFormatter.ofPattern("uuuu-M-d").withResolverStyle(ResolverStyle.STRICT),
            DateTimeFormatter.ofPattern("uuuu/M/d").withResolverStyle(ResolverStyle.STRICT),
            DateTimeFormatter.ofPattern("uuuu.M.d").withResolverStyle(ResolverStyle.STRICT),
            DateTimeFormatter.ofPattern("uuuu年M月d日").withResolverStyle(ResolverStyle.STRICT),
            DateTimeFormatter.ofPattern("uuuuMMdd").withResolverStyle(ResolverStyle.STRICT)
    );

    /**
     * 金额上限，与数据库 DECIMAL(15,2) 对应
     */
    private static final BigDecimal MAX_AMOUNT = new BigDecimal("9999999999999.99");

    /**
     * Excel日期序列值的基准日（1899-12-30）
     */
    private static final LocalDate EXCEL_EPOCH = LocalDate.of(1899, 12, 30);

    /**
     * 导入前字段校验
     *
     * @return 错误信息，null表示校验通过
     */
    public static String validate(ExcelDataDTO dto) {
        List<String> errors = new ArrayList<>();

        // ===== 必填字段存在性校验 =====
        if (StrUtil.isBlank(dto.getInsuranceNo())) {
            errors.add("医保编号不能为空");
        } else if (dto.getInsuranceNo().length() > 50) {
            errors.add("医保编号长度不能超过50个字符");
        }

        if (StrUtil.isBlank(dto.getName())) {
            errors.add("姓名不能为空");
        } else if (dto.getName().length() > 50) {
            errors.add("姓名长度不能超过50个字符");
        }

        if (StrUtil.isBlank(dto.getItemCode())) {
            errors.add("项目编码不能为空");
        } else if (dto.getItemCode().length() > 50) {
            errors.add("项目编码长度不能超过50个字符");
        }

        if (StrUtil.isBlank(dto.getOrgCode())) {
            errors.add("机构编码不能为空");
        } else if (dto.getOrgCode().length() > 50) {
            errors.add("机构编码长度不能超过50个字符");
        }

        // ===== 金额：必填 + 格式 =====
        if (StrUtil.isBlank(dto.getAmount())) {
            errors.add("金额不能为空");
        } else {
            BigDecimal amount = parseAmount(dto.getAmount());
            if (amount == null) {
                errors.add("金额格式不正确（应为数字，如 1000.00）");
            } else if (amount.compareTo(BigDecimal.ZERO) < 0) {
                errors.add("金额不能为负数");
            } else if (amount.compareTo(MAX_AMOUNT) > 0) {
                errors.add("金额超出允许范围");
            }
        }

        // ===== 就诊日期：必填 + 格式 =====
        if (StrUtil.isBlank(dto.getVisitDate())) {
            errors.add("就诊日期不能为空");
        } else if (parseVisitDate(dto.getVisitDate()) == null) {
            errors.add("就诊日期格式不正确（支持yyyy-MM-dd、yyyy/MM/dd、yyyyMMdd等格式）");
        }

        // ===== 选填字段格式校验 =====
        if (StrUtil.isNotBlank(dto.getIdCard()) && !IdcardUtil.isValidCard(dto.getIdCard())) {
            errors.add("身份证号格式不正确");
        }
        if (StrUtil.isNotBlank(dto.getPhone()) && !PhoneUtil.isMobile(dto.getPhone())) {
            errors.add("手机号格式不正确");
        }
        if (StrUtil.isNotBlank(dto.getAddress()) && dto.getAddress().length() > 200) {
            errors.add("地址长度不能超过200个字符");
        }

        return errors.isEmpty() ? null : String.join("; ", errors);
    }

    /**
     * 上送前对已入库数据复检（防御性校验，防止历史脏数据进入上送步骤）
     *
     * @return 错误信息，null表示校验通过
     */
    public static String validateEntity(ExcelData data) {
        List<String> errors = new ArrayList<>();

        if (StrUtil.isBlank(data.getInsuranceNo())) {
            errors.add("医保编号不能为空");
        }
        if (StrUtil.isBlank(data.getName())) {
            errors.add("姓名不能为空");
        }
        if (StrUtil.isBlank(data.getItemCode())) {
            errors.add("项目编码不能为空");
        }
        if (StrUtil.isBlank(data.getOrgCode())) {
            errors.add("机构编码不能为空");
        }
        if (data.getAmount() == null) {
            errors.add("金额不能为空");
        } else if (data.getAmount().compareTo(BigDecimal.ZERO) < 0) {
            errors.add("金额不能为负数");
        }
        if (data.getVisitDate() == null) {
            errors.add("就诊日期不能为空");
        }

        return errors.isEmpty() ? null : String.join("; ", errors);
    }

    /**
     * 解析金额字符串，兼容千分位逗号与常见货币符号
     *
     * @return 解析失败返回null
     */
    public static BigDecimal parseAmount(String raw) {
        if (StrUtil.isBlank(raw)) {
            return null;
        }
        String cleaned = raw.trim()
                .replace(",", "")
                .replace("￥", "")
                .replace("¥", "")
                .replace("$", "")
                .trim();
        if (cleaned.isEmpty()) {
            return null;
        }
        try {
            return new BigDecimal(cleaned);
        } catch (NumberFormatException e) {
            return null;
        }
    }

    /**
     * 解析就诊日期，支持 yyyy-MM-dd、yyyy/M/d、yyyy.MM.dd、yyyyMMdd、yyyy年M月d日
     * 以及Excel日期序列值（单元格为日期格式时按字符串读取到的是数字）
     *
     * @return 解析失败返回null
     */
    public static LocalDate parseVisitDate(String raw) {
        if (StrUtil.isBlank(raw)) {
            return null;
        }
        String value = raw.trim();

        // Excel序列日期（5位数字，覆盖1927年~2173年）
        if (value.matches("^\\d{5}(\\.\\d+)?$")) {
            try {
                long serial = (long) Double.parseDouble(value);
                return EXCEL_EPOCH.plusDays(serial);
            } catch (NumberFormatException | ArithmeticException e) {
                return null;
            }
        }

        for (DateTimeFormatter formatter : DATE_FORMATTERS) {
            try {
                return LocalDate.parse(value, formatter);
            } catch (DateTimeParseException ignored) {
                // 尝试下一种格式
            }
        }
        return null;
    }
}
