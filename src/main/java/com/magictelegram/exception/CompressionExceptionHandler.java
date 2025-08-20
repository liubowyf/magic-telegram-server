package com.magictelegram.exception;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * 压缩异常处理器
 * 处理数据压缩失败的情况，提供降级和恢复策略
 * 
 * @author liubo
 * @date 2024-12-19
 */
public class CompressionExceptionHandler {
    
    private static final Logger logger = LoggerFactory.getLogger(CompressionExceptionHandler.class);
    
    private final String sessionId;
    private final long dataSize;
    private final String compressionType;
    private final Throwable cause;
    
    /**
     * 处理策略枚举
     */
    public enum Strategy {
        /** 重试相同压缩算法 */
        RETRY_SAME_COMPRESSION,
        /** 切换到其他压缩算法 */
        SWITCH_COMPRESSION_TYPE,
        /** 不使用压缩 */
        NO_COMPRESSION,
        /** 分割数据后重试 */
        SPLIT_AND_RETRY,
        /** 拒绝处理 */
        REJECT_PROCESSING
    }
    
    /**
     * 处理结果
     */
    public static class HandlingResult {
        private final Strategy strategy;
        private final boolean canProceed;
        private final String reason;
        private final Object suggestion;
        
        public HandlingResult(Strategy strategy, boolean canProceed, String reason, Object suggestion) {
            this.strategy = strategy;
            this.canProceed = canProceed;
            this.reason = reason;
            this.suggestion = suggestion;
        }
        
        // Getters
        public Strategy getStrategy() { return strategy; }
        public boolean canProceed() { return canProceed; }
        public String getReason() { return reason; }
        public Object getSuggestion() { return suggestion; }
        
        @Override
        public String toString() {
            return String.format("HandlingResult{strategy=%s, canProceed=%s, reason='%s'}", 
                               strategy, canProceed, reason);
        }
    }
    
    public CompressionExceptionHandler(String sessionId, long dataSize, String compressionType, Throwable cause) {
        this.sessionId = sessionId;
        this.dataSize = dataSize;
        this.compressionType = compressionType;
        this.cause = cause;
    }
    
    /**
     * 分析并提供处理策略
     * 
     * @return 处理结果
     */
    public HandlingResult analyze() {
        logger.info("分析压缩失败情况: sessionId={}, dataSize={}, compressionType={}, cause={}", 
                   sessionId, dataSize, compressionType, cause.getClass().getSimpleName());
        
        // 分析失败原因
        String causeMessage = cause.getMessage() != null ? cause.getMessage().toLowerCase() : "";
        
        // 内存不足
        if (isMemoryRelated(cause, causeMessage)) {
            if (dataSize > 50 * 1024 * 1024) { // 大于50MB
                return new HandlingResult(
                    Strategy.SPLIT_AND_RETRY,
                    true,
                    "数据过大导致内存不足，建议分割数据后重试",
                    calculateSplitSize()
                );
            } else {
                return new HandlingResult(
                    Strategy.NO_COMPRESSION,
                    true,
                    "内存不足，建议不使用压缩直接存储",
                    null
                );
            }
        }
        
        // 数据格式问题
        if (isDataFormatRelated(cause, causeMessage)) {
            if ("GZIP".equalsIgnoreCase(compressionType)) {
                return new HandlingResult(
                    Strategy.NO_COMPRESSION,
                    true,
                    "数据格式不适合GZIP压缩，建议不使用压缩",
                    null
                );
            } else {
                return new HandlingResult(
                    Strategy.SWITCH_COMPRESSION_TYPE,
                    true,
                    "当前压缩算法不适合此数据格式，建议切换到GZIP",
                    "GZIP"
                );
            }
        }
        
        // 临时性错误
        if (isTemporaryError(cause, causeMessage)) {
            return new HandlingResult(
                Strategy.RETRY_SAME_COMPRESSION,
                true,
                "临时性错误，建议重试相同压缩算法",
                3 // 建议重试3次
            );
        }
        
        // 压缩算法本身问题
        if (isCompressionAlgorithmError(cause, causeMessage)) {
            if ("GZIP".equalsIgnoreCase(compressionType)) {
                return new HandlingResult(
                    Strategy.NO_COMPRESSION,
                    true,
                    "GZIP压缩算法出错，建议不使用压缩",
                    null
                );
            } else {
                return new HandlingResult(
                    Strategy.SWITCH_COMPRESSION_TYPE,
                    true,
                    "当前压缩算法出错，建议切换到GZIP",
                    "GZIP"
                );
            }
        }
        
        // 数据过大
        if (dataSize > 100 * 1024 * 1024) { // 大于100MB
            return new HandlingResult(
                Strategy.REJECT_PROCESSING,
                false,
                "数据过大且压缩失败，拒绝处理以保护系统稳定性",
                null
            );
        }
        
        // 默认策略：切换到无压缩
        return new HandlingResult(
            Strategy.NO_COMPRESSION,
            true,
            "未知压缩错误，建议不使用压缩直接存储",
            null
        );
    }
    
    /**
     * 判断是否为内存相关错误
     */
    private boolean isMemoryRelated(Throwable cause, String message) {
        return cause instanceof OutOfMemoryError ||
               message.contains("out of memory") ||
               message.contains("heap space") ||
               message.contains("memory");
    }
    
    /**
     * 判断是否为数据格式相关错误
     */
    private boolean isDataFormatRelated(Throwable cause, String message) {
        return message.contains("invalid") ||
               message.contains("corrupt") ||
               message.contains("format") ||
               message.contains("header") ||
               cause instanceof IllegalArgumentException;
    }
    
    /**
     * 判断是否为临时性错误
     */
    private boolean isTemporaryError(Throwable cause, String message) {
        return message.contains("timeout") ||
               message.contains("interrupted") ||
               message.contains("temporary") ||
               cause instanceof InterruptedException;
    }
    
    /**
     * 判断是否为压缩算法错误
     */
    private boolean isCompressionAlgorithmError(Throwable cause, String message) {
        return message.contains("compression") ||
               message.contains("deflate") ||
               message.contains("gzip") ||
               message.contains("algorithm");
    }
    
    /**
     * 计算分割大小
     */
    private long calculateSplitSize() {
        // 建议分割为原大小的1/4，但不小于10MB
        long splitSize = Math.max(dataSize / 4, 10 * 1024 * 1024);
        return Math.min(splitSize, 50 * 1024 * 1024); // 不超过50MB
    }
    
    /**
     * 获取建议的替代压缩类型
     * 
     * @return 替代压缩类型
     */
    public String getSuggestedAlternativeCompression() {
        if ("GZIP".equalsIgnoreCase(compressionType)) {
            return "NONE"; // GZIP失败则不压缩
        } else {
            return "GZIP"; // 其他压缩失败则尝试GZIP
        }
    }
    
    /**
     * 估算无压缩情况下的存储大小
     * 
     * @return 估算的存储大小
     */
    public long estimateUncompressedStorageSize() {
        // Base64编码会增加约33%的大小
        return (long) (dataSize * 1.33);
    }
    
    /**
     * 生成异常报告
     * 
     * @return 异常报告
     */
    public String generateReport() {
        HandlingResult result = analyze();
        
        StringBuilder report = new StringBuilder();
        report.append("=== 压缩异常报告 ===\n");
        report.append(String.format("Session ID: %s\n", sessionId));
        report.append(String.format("数据大小: %,d bytes (%.2f MB)\n", dataSize, dataSize / 1024.0 / 1024.0));
        report.append(String.format("压缩类型: %s\n", compressionType));
        report.append(String.format("失败原因: %s\n", cause.getClass().getSimpleName()));
        report.append(String.format("错误消息: %s\n", cause.getMessage()));
        report.append(String.format("建议策略: %s\n", result.getStrategy()));
        report.append(String.format("处理原因: %s\n", result.getReason()));
        
        if (result.getSuggestion() != null) {
            report.append(String.format("具体建议: %s\n", result.getSuggestion()));
        }
        
        report.append(String.format("替代压缩: %s\n", getSuggestedAlternativeCompression()));
        report.append(String.format("无压缩大小: %,d bytes (%.2f MB)\n", 
                     estimateUncompressedStorageSize(), 
                     estimateUncompressedStorageSize() / 1024.0 / 1024.0));
        report.append(String.format("可以继续: %s\n", result.canProceed() ? "是" : "否"));
        report.append("===================\n");
        
        return report.toString();
    }
    
    /**
     * 创建对应的StorageException
     * 
     * @return StorageException
     */
    public StorageException createException() {
        return StorageException.compressionFailed(sessionId, compressionType, cause);
    }
}