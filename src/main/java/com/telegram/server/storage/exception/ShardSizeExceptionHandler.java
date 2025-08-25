package com.telegram.server.storage.exception;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * 分片大小异常处理器
 * 处理分片大小超限的情况，提供降级和优化建议
 * 
 * @author liubo
 * @date 2025-08-20
 */
public class ShardSizeExceptionHandler {
    
    private static final Logger logger = LoggerFactory.getLogger(ShardSizeExceptionHandler.class);
    
    private final String sessionId;
    private final long shardSize;
    private final long maxSize;
    
    /**
     * 构造函数
     * 
     * @param sessionId Session ID
     * @param shardSize 实际分片大小
     * @param maxSize 最大允许大小
     */
    public ShardSizeExceptionHandler(String sessionId, long shardSize, long maxSize) {
        this.sessionId = sessionId;
        this.shardSize = shardSize;
        this.maxSize = maxSize;
    }
    
    /**
     * 获取处理建议
     * 
     * @return 处理建议
     */
    public HandlingStrategy getHandlingStrategy() {
        double oversizeRatio = (double) shardSize / maxSize;
        
        if (oversizeRatio <= 1.2) {
            // 轻微超限，尝试压缩
            return new HandlingStrategy(
                StrategyType.COMPRESS_AND_RETRY,
                "分片轻微超限，建议使用压缩后重试",
                "尝试GZIP或LZ4压缩算法"
            );
        } else if (oversizeRatio <= 2.0) {
            // 中度超限，拆分分片
            return new HandlingStrategy(
                StrategyType.SPLIT_SHARD,
                "分片中度超限，建议拆分为更小的分片",
                String.format("建议拆分为%d个分片", (int) Math.ceil(oversizeRatio))
            );
        } else {
            // 严重超限，使用GridFS
            return new HandlingStrategy(
                StrategyType.USE_GRIDFS,
                "分片严重超限，建议使用GridFS存储",
                "将大文件存储到GridFS，只在文档中保存引用"
            );
        }
    }
    
    /**
     * 计算建议的分片大小
     * 
     * @return 建议的分片大小
     */
    public long getRecommendedShardSize() {
        // 保留20%的安全边距
        return (long) (maxSize * 0.8);
    }
    
    /**
     * 计算需要的分片数量
     * 
     * @return 分片数量
     */
    public int getRequiredShardCount() {
        long recommendedSize = getRecommendedShardSize();
        return (int) Math.ceil((double) shardSize / recommendedSize);
    }
    
    /**
     * 处理策略类型
     */
    public enum StrategyType {
        COMPRESS_AND_RETRY,  // 压缩后重试
        SPLIT_SHARD,         // 拆分分片
        USE_GRIDFS           // 使用GridFS
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
    
    public long getShardSize() {
        return shardSize;
    }
    
    public long getMaxSize() {
        return maxSize;
    }
    
    @Override
    public String toString() {
        return String.format("ShardSizeExceptionHandler{sessionId='%s', shardSize=%d, maxSize=%d}",
                           sessionId, shardSize, maxSize);
    }
}