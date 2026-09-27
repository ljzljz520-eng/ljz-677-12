package com.excel.service;

import cn.hutool.core.util.IdUtil;
import com.excel.dto.ExcelDataDTO;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * 导入前校验错误数据缓存
 * 校验结果在内存中短期保存，供用户下载完整错误行；过期自动清理
 */
@Component
public class ValidationErrorCache {

    /**
     * 缓存有效期：30分钟
     */
    private static final long TTL_MS = 30 * 60 * 1000L;

    /**
     * 最多缓存的校验批次数量
     */
    private static final int MAX_ENTRIES = 100;

    private final Map<String, CachedErrors> cache = new ConcurrentHashMap<>();

    private ScheduledExecutorService scheduler;

    @PostConstruct
    public void init() {
        scheduler = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "validation-error-cache-cleanup");
            t.setDaemon(true);
            return t;
        });
        // 每5分钟清理一次过期数据
        scheduler.scheduleAtFixedRate(this::cleanup, 5, 5, TimeUnit.MINUTES);
    }

    @PreDestroy
    public void destroy() {
        if (scheduler != null) {
            scheduler.shutdownNow();
        }
    }

    /**
     * 保存错误数据，返回validationId
     */
    public String store(List<ExcelDataDTO> errors) {
        cleanup();
        String validationId = IdUtil.fastSimpleUUID();
        cache.put(validationId, new CachedErrors(errors, System.currentTimeMillis() + TTL_MS));
        // 超出容量时淘汰最早过期的一条
        while (cache.size() > MAX_ENTRIES) {
            cache.entrySet().stream()
                    .min((a, b) -> Long.compare(a.getValue().getExpireAt(), b.getValue().getExpireAt()))
                    .map(Map.Entry::getKey)
                    .ifPresent(cache::remove);
        }
        return validationId;
    }

    /**
     * 获取错误数据，不存在或已过期返回null
     */
    public List<ExcelDataDTO> get(String validationId) {
        CachedErrors cached = cache.get(validationId);
        if (cached == null || cached.getExpireAt() < System.currentTimeMillis()) {
            cache.remove(validationId);
            return null;
        }
        return cached.getErrors();
    }

    /**
     * 清理过期数据
     */
    private void cleanup() {
        long now = System.currentTimeMillis();
        cache.entrySet().removeIf(e -> e.getValue().getExpireAt() < now);
    }

    /**
     * 缓存条目
     */
    public static class CachedErrors {

        private final List<ExcelDataDTO> errors;
        private final long expireAt;

        public CachedErrors(List<ExcelDataDTO> errors, long expireAt) {
            this.errors = errors;
            this.expireAt = expireAt;
        }

        public List<ExcelDataDTO> getErrors() {
            return errors;
        }

        public long getExpireAt() {
            return expireAt;
        }
    }
}
