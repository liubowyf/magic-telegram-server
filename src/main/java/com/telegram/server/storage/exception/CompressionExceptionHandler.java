package com.telegram.server.storage.exception;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * 压缩异常处理器
 * 处理数据压缩失败的情况，提供降级和重试策略
 * 
 * @author liubo
 * @date 2025-08-20
 */
public class CompressionExceptionHandler {
    
    private static final Logger logger = LoggerFactory.getLogger(CompressionExceptionHandler.class);
    
    private final String sessionId;
    private final long dataSize;
    private final String compressionType;
    private final Throwable cause;
    
    /**
     * 构造函数
     * 
     * @param sessionId Session ID
     * @param dataSize 数据大小
     * @param compressionType 压缩类型
     * @param cause 异常原因
     */
    public CompressionExceptionHandler(String sessionId, long dataSize, String compressionType, Throwable cause) {
        this.sessionId = sessionId;
        this.dataSize = dataSize;
        this.compressionType = compressionType;
        this.cause = cause;
    }
    
    /**
     * 获取处理建议
     * 
     * @return 处理建议
     */
    public HandlingStrategy getHandlingStrategy() {
        // 根据异常类型和数据大小决定策略
        if (isMemoryRelatedError()) {
            return new HandlingStrategy(
                StrategyType.REDUCE_COMPRESSION_LEVEL,
                "内存不足导致压缩失败",
                "降低压缩级别或使用流式压缩"
            );
        } else if (isCorruptedDataError()) {
            return new HandlingStrategy(
                StrategyType.SKIP_COMPRESSION,
                "数据损坏导致压缩失败",
                "跳过压缩，直接存储原始数据"
            );
        } else if (isTimeoutError()) {
            return new HandlingStrategy(
                StrategyType.USE_FASTER_ALGORITHM,
                "压缩超时",
                "使用更快的压缩算法如LZ4"
            );
        } else {
            return new HandlingStrategy(
                StrategyType.FALLBACK_TO_UNCOMPRESSED,
                "未知压缩错误",
                "回退到无压缩存储"
            );
        }
    }
    
    /**
     * 获取建议的替代压缩算法
     * 
     * @return 替代压缩算法列表
     */
    public String[] getAlternativeCompressionTypes() {
        switch (compressionType.toLowerCase()) {
            case "gzip":
                return new String[]{"lz4", "snappy", "none"};
            case "lz4":
                return new String[]{"snappy", "gzip", "none"};
            case "snappy":
                return new String[]{"lz4", "gzip", "none"};
            default:
                return new String[]{"lz4", "snappy", "gzip", "none"};
        }
    }
    
    /**
     * 判断是否应该重试压缩
     * 
     * @return 是否应该重试
     */
    public boolean shouldRetryCompression() {
        // 对于临时性错误，建议重试
        return isTemporaryError() && dataSize < 50 * 1024 * 1024; // 小于50MB才重试
    }
    
    /**
     * 获取建议的压缩级别
     * 
     * @return 压缩级别（1-9，1最快，9最小）
     */
    public int getRecommendedCompressionLevel() {
        if (dataSize > 100 * 1024 * 1024) { // 大于100MB
            return 1; // 最快压缩
        } else if (dataSize > 10 * 1024 * 1024) { // 大于10MB
            return 3; // 平衡压缩
        } else {
            return 6; // 标准压缩
        }
    }
    
    /**
     * 检查是否为内存相关错误
     */
    private boolean isMemoryRelatedError() {
        if (cause == null) return false;
        String message = cause.getMessage();
        return message != null && (
            message.contains("OutOfMemoryError") ||
            message.contains("memory") ||
            message.contains("heap")
        );
    }
    
    /**
     * 检查是否为数据损坏错误
     */
    private boolean isCorruptedDataError() {
        if (cause == null) return false;
        String message = cause.getMessage();
        return message != null && (
            message.contains("corrupt") ||
            message.contains("invalid") ||
            message.contains("malformed")
        );
    }
    
    /**
     * 检查是否为超时错误
     */
    private boolean isTimeoutError() {
        if (cause == null) return false;
        String message = cause.getMessage();
        return message != null && (
            message.contains("timeout") ||
            message.contains("time out") ||
            message.contains("interrupted")
        );
    }
    
    /**
     * 检查是否为临时性错误
     */
    private boolean isTemporaryError() {
        return isMemoryRelatedError() || isTimeoutError();
    }
    
    /**
     * 处理策略类型
     */
    public enum StrategyType {
        REDUCE_COMPRESSION_LEVEL,    // 降低压缩级别
        USE_FASTER_ALGORITHM,        // 使用更快算法
        SKIP_COMPRESSION,            // 跳过压缩
        FALLBACK_TO_UNCOMPRESSED     // 回退到无压缩
    }
    
    /**
     * 处理策略
     */
    public static class HandlingStrategy {
        private final StrategyType type;
        private final String description;
        private final String recommendation;
        
        public HandlingStrategy(StrategyType type, String description, String recommendation) {
            this.type = type;
            this.description = description;
            this.recommendation = recommendation;
        }
        
        public StrategyType getType() {
            return type;
        }
        
        public String getDescription() {
            return description;
        }
        
        public String getRecommendation() {
            return recommendation;
        }
        
        @Override
        public String toString() {
            return String.format("HandlingStrategy{type=%s, description='%s', recommendation='%s'}",
                               type, description, recommendation);
        }
    }
    
    // Getters
    public String getSessionId() {
        return sessionId;
    }
    
    public long getDataSize() {
        return dataSize;
    }
    
    public String getCompressionType() {
        return compressionType;
    }
    
    public Throwable getCause() {
        return cause;
    }
    
    @Override
    public String toString() {
        return String.format("CompressionExceptionHandler{sessionId='%s', dataSize=%d, compressionType='%s', cause=%s}",
                           sessionId, dataSize, compressionType, cause != null ? cause.getClass().getSimpleName() : "null");
    }
}