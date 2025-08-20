package com.magictelegram.exception;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * 分片大小异常处理器
 * 处理分片大小超限的情况，提供降级和恢复策略
 * 
 * @author liubo
 * @date 2024-12-19
 */
public class ShardSizeExceptionHandler {
    
    private static final Logger logger = LoggerFactory.getLogger(ShardSizeExceptionHandler.class);
    
    private final String sessionId;
    private final long shardSize;
    private final long maxSize;
    
    /**
     * 处理策略枚举
     */
    public enum Strategy {
        /** 增加分片数量 */
        INCREASE_SHARD_COUNT,
        /** 使用更强压缩 */
        USE_STRONGER_COMPRESSION,
        /** 清理旧数据 */
        CLEANUP_OLD_DATA,
        /** 拒绝存储 */
        REJECT_STORAGE
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
    
    public ShardSizeExceptionHandler(String sessionId, long shardSize, long maxSize) {
        this.sessionId = sessionId;
        this.shardSize = shardSize;
        this.maxSize = maxSize;
    }
    
    /**
     * 分析并提供处理策略
     * 
     * @return 处理结果
     */
    public HandlingResult analyze() {
        double exceedRatio = (double) shardSize / maxSize;
        
        logger.info("分析分片大小超限情况: sessionId={}, shardSize={}, maxSize={}, exceedRatio={:.2f}", 
                   sessionId, shardSize, maxSize, exceedRatio);
        
        if (exceedRatio <= 1.2) {
            // 超出不多，尝试增加分片数量
            int suggestedShardCount = (int) Math.ceil(exceedRatio * 1.1); // 增加10%缓冲
            return new HandlingResult(
                Strategy.INCREASE_SHARD_COUNT,
                true,
                "分片大小轻微超限，建议增加分片数量",
                suggestedShardCount
            );
        } else if (exceedRatio <= 1.5) {
            // 超出较多，尝试更强压缩
            return new HandlingResult(
                Strategy.USE_STRONGER_COMPRESSION,
                true,
                "分片大小明显超限，建议使用更强压缩算法",
                "GZIP" // 建议使用GZIP压缩
            );
        } else if (exceedRatio <= 2.0) {
            // 超出很多，建议清理旧数据
            return new HandlingResult(
                Strategy.CLEANUP_OLD_DATA,
                true,
                "分片大小严重超限，建议清理旧数据后重试",
                "清理超过7天的旧Session数据"
            );
        } else {
            // 超出过多，拒绝存储
            return new HandlingResult(
                Strategy.REJECT_STORAGE,
                false,
                "分片大小过度超限，拒绝存储以保护系统稳定性",
                null
            );
        }
    }
    
    /**
     * 计算建议的分片大小
     * 
     * @param targetShardCount 目标分片数量
     * @return 建议的分片大小
     */
    public long calculateSuggestedShardSize(int targetShardCount) {
        if (targetShardCount <= 0) {
            throw new IllegalArgumentException("目标分片数量必须大于0");
        }
        
        // 考虑Base64编码膨胀和元数据开销
        long availableSize = (long) (maxSize * 0.8); // 预留20%缓冲
        long suggestedSize = availableSize / targetShardCount;
        
        logger.debug("计算建议分片大小: targetShardCount={}, availableSize={}, suggestedSize={}", 
                    targetShardCount, availableSize, suggestedSize);
        
        return suggestedSize;
    }
    
    /**
     * 计算最小所需分片数量
     * 
     * @return 最小分片数量
     */
    public int calculateMinimumShardCount() {
        // 考虑Base64编码膨胀（约33%）和元数据开销
        long effectiveMaxSize = (long) (maxSize * 0.75); // 预留25%缓冲
        int minShardCount = (int) Math.ceil((double) shardSize / effectiveMaxSize);
        
        logger.debug("计算最小分片数量: shardSize={}, effectiveMaxSize={}, minShardCount={}", 
                    shardSize, effectiveMaxSize, minShardCount);
        
        return Math.max(1, minShardCount);
    }
    
    /**
     * 生成异常报告
     * 
     * @return 异常报告
     */
    public String generateReport() {
        HandlingResult result = analyze();
        
        StringBuilder report = new StringBuilder();
        report.append("=== 分片大小异常报告 ===\n");
        report.append(String.format("Session ID: %s\n", sessionId));
        report.append(String.format("分片大小: %,d bytes (%.2f MB)\n", shardSize, shardSize / 1024.0 / 1024.0));
        report.append(String.format("最大限制: %,d bytes (%.2f MB)\n", maxSize, maxSize / 1024.0 / 1024.0));
        report.append(String.format("超出比例: %.2f%%\n", ((double) shardSize / maxSize - 1) * 100));
        report.append(String.format("建议策略: %s\n", result.getStrategy()));
        report.append(String.format("处理原因: %s\n", result.getReason()));
        
        if (result.getSuggestion() != null) {
            report.append(String.format("具体建议: %s\n", result.getSuggestion()));
        }
        
        report.append(String.format("最小分片数: %d\n", calculateMinimumShardCount()));
        report.append(String.format("可以继续: %s\n", result.canProceed() ? "是" : "否"));
        report.append("========================\n");
        
        return report.toString();
    }
    
    /**
     * 创建对应的StorageException
     * 
     * @return StorageException
     */
    public StorageException createException() {
        HandlingResult result = analyze();
        
        String message = String.format(
            "分片大小超限: %,d bytes 超出限制 %,d bytes (%.1fx), 建议策略: %s",
            shardSize, maxSize, (double) shardSize / maxSize, result.getStrategy()
        );
        
        return StorageException.shardSizeExceeded(sessionId, shardSize, maxSize);
    }
}