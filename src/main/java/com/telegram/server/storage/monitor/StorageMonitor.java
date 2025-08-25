package com.telegram.server.storage.monitor;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.Map;
import java.util.List;
import java.util.ArrayList;

/**
 * 存储监控组件
 * 监控分片存储状态、性能指标和异常情况
 * 
 * @author liubo
 * @date 2025-08-20
 */
@Component
public class StorageMonitor {
    
    private static final Logger logger = LoggerFactory.getLogger(StorageMonitor.class);
    
    // 性能指标
    private final AtomicLong totalStorageOperations = new AtomicLong(0);
    private final AtomicLong totalStorageTime = new AtomicLong(0);
    private final AtomicLong totalStorageSize = new AtomicLong(0);
    private final AtomicInteger activeShardCount = new AtomicInteger(0);
    private final AtomicLong compressionSavings = new AtomicLong(0);
    
    // 异常统计
    private final AtomicLong storageFailures = new AtomicLong(0);
    private final AtomicLong oversizeShardCount = new AtomicLong(0);
    private final AtomicLong compressionFailures = new AtomicLong(0);
    
    // Session级别监控
    private final Map<String, SessionStorageMetrics> sessionMetrics = new ConcurrentHashMap<>();
    
    // 最近的异常记录
    private final List<StorageException> recentExceptions = new ArrayList<>();
    private static final int MAX_EXCEPTION_HISTORY = 100;
    
    /**
     * Session存储指标
     */
    public static class SessionStorageMetrics {
        private final String sessionId;
        private final AtomicLong totalSize = new AtomicLong(0);
        private final AtomicInteger shardCount = new AtomicInteger(0);
        private final AtomicLong lastUpdateTime = new AtomicLong(System.currentTimeMillis());
        private final AtomicLong operationCount = new AtomicLong(0);
        private final AtomicLong compressionRatio = new AtomicLong(1000); // 以千分比存储
        
        public SessionStorageMetrics(String sessionId) {
            this.sessionId = sessionId;
        }
        
        // Getters
        public String getSessionId() { return sessionId; }
        public long getTotalSize() { return totalSize.get(); }
        public int getShardCount() { return shardCount.get(); }
        public long getLastUpdateTime() { return lastUpdateTime.get(); }
        public long getOperationCount() { return operationCount.get(); }
        public double getCompressionRatio() { return compressionRatio.get() / 1000.0; }
        
        // 内部更新方法
        void updateSize(long sizeChange) { totalSize.addAndGet(sizeChange); }
        void updateShardCount(int countChange) { shardCount.addAndGet(countChange); }
        void updateLastUpdateTime() { lastUpdateTime.set(System.currentTimeMillis()); }
        void incrementOperationCount() { operationCount.incrementAndGet(); }
        void updateCompressionRatio(double ratio) { compressionRatio.set((long)(ratio * 1000)); }
    }
    
    /**
     * 存储异常记录
     */
    public static class StorageException {
        private final LocalDateTime timestamp;
        private final String sessionId;
        private final String operation;
        private final String errorMessage;
        private final long dataSize;
        
        public StorageException(String sessionId, String operation, String errorMessage, long dataSize) {
            this.timestamp = LocalDateTime.now();
            this.sessionId = sessionId;
            this.operation = operation;
            this.errorMessage = errorMessage;
            this.dataSize = dataSize;
        }
        
        // Getters
        public LocalDateTime getTimestamp() { return timestamp; }
        public String getSessionId() { return sessionId; }
        public String getOperation() { return operation; }
        public String getErrorMessage() { return errorMessage; }
        public long getDataSize() { return dataSize; }
    }
    
    /**
     * 记录存储操作开始
     * 
     * @param sessionId Session ID
     * @param operation 操作类型
     * @param dataSize 数据大小
     * @return 操作ID，用于后续记录操作结束
     */
    public String recordStorageStart(String sessionId, String operation, long dataSize) {
        String operationId = sessionId + "_" + System.currentTimeMillis();
        
        totalStorageOperations.incrementAndGet();
        totalStorageSize.addAndGet(dataSize);
        
        SessionStorageMetrics metrics = sessionMetrics.computeIfAbsent(sessionId, SessionStorageMetrics::new);
        metrics.incrementOperationCount();
        metrics.updateLastUpdateTime();
        
        logger.debug("存储操作开始: sessionId={}, operation={}, dataSize={}, operationId={}", 
                    sessionId, operation, dataSize, operationId);
        
        return operationId;
    }
    
    /**
     * 记录存储操作成功
     * 
     * @param operationId 操作ID
     * @param sessionId Session ID
     * @param shardCount 分片数量
     * @param compressedSize 压缩后大小
     * @param originalSize 原始大小
     * @param duration 操作耗时（毫秒）
     */
    public void recordStorageSuccess(String operationId, String sessionId, int shardCount, 
                                   long compressedSize, long originalSize, long duration) {
        totalStorageTime.addAndGet(duration);
        activeShardCount.addAndGet(shardCount);
        
        if (originalSize > compressedSize) {
            compressionSavings.addAndGet(originalSize - compressedSize);
        }
        
        SessionStorageMetrics metrics = sessionMetrics.get(sessionId);
        if (metrics != null) {
            metrics.updateSize(compressedSize);
            metrics.updateShardCount(shardCount);
            if (originalSize > 0) {
                metrics.updateCompressionRatio((double) compressedSize / originalSize);
            }
        }
        
        logger.debug("存储操作成功: operationId={}, sessionId={}, shardCount={}, compressedSize={}, originalSize={}, duration={}ms", 
                    operationId, sessionId, shardCount, compressedSize, originalSize, duration);
    }
    
    /**
     * 记录存储操作失败
     * 
     * @param operationId 操作ID
     * @param sessionId Session ID
     * @param operation 操作类型
     * @param errorMessage 错误信息
     * @param dataSize 数据大小
     */
    public void recordStorageFailure(String operationId, String sessionId, String operation, 
                                    String errorMessage, long dataSize) {
        storageFailures.incrementAndGet();
        
        StorageException exception = new StorageException(sessionId, operation, errorMessage, dataSize);
        synchronized (recentExceptions) {
            recentExceptions.add(exception);
            if (recentExceptions.size() > MAX_EXCEPTION_HISTORY) {
                recentExceptions.remove(0);
            }
        }
        
        logger.error("存储操作失败: operationId={}, sessionId={}, operation={}, error={}, dataSize={}", 
                    operationId, sessionId, operation, errorMessage, dataSize);
    }
    
    /**
     * 记录超大分片警告
     * 
     * @param sessionId Session ID
     * @param shardSize 分片大小
     * @param maxAllowedSize 最大允许大小
     */
    public void recordOversizeShard(String sessionId, long shardSize, long maxAllowedSize) {
        oversizeShardCount.incrementAndGet();
        
        String errorMessage = String.format("分片大小超限: %d bytes > %d bytes", shardSize, maxAllowedSize);
        StorageException exception = new StorageException(sessionId, "SHARD_OVERSIZE", errorMessage, shardSize);
        
        synchronized (recentExceptions) {
            recentExceptions.add(exception);
            if (recentExceptions.size() > MAX_EXCEPTION_HISTORY) {
                recentExceptions.remove(0);
            }
        }
        
        logger.warn("检测到超大分片: sessionId={}, shardSize={}, maxAllowed={}", 
                   sessionId, shardSize, maxAllowedSize);
    }
    
    /**
     * 记录压缩失败
     * 
     * @param sessionId Session ID
     * @param dataSize 数据大小
     * @param errorMessage 错误信息
     */
    public void recordCompressionFailure(String sessionId, long dataSize, String errorMessage) {
        compressionFailures.incrementAndGet();
        
        StorageException exception = new StorageException(sessionId, "COMPRESSION_FAILURE", errorMessage, dataSize);
        synchronized (recentExceptions) {
            recentExceptions.add(exception);
            if (recentExceptions.size() > MAX_EXCEPTION_HISTORY) {
                recentExceptions.remove(0);
            }
        }
        
        logger.error("压缩失败: sessionId={}, dataSize={}, error={}", sessionId, dataSize, errorMessage);
    }
    
    /**
     * 获取全局存储统计信息
     * 
     * @return 存储统计信息
     */
    public StorageStatistics getGlobalStatistics() {
        return new StorageStatistics(
            totalStorageOperations.get(),
            totalStorageTime.get(),
            totalStorageSize.get(),
            activeShardCount.get(),
            compressionSavings.get(),
            storageFailures.get(),
            oversizeShardCount.get(),
            compressionFailures.get(),
            sessionMetrics.size()
        );
    }
    
    /**
     * 获取Session级别的存储指标
     * 
     * @param sessionId Session ID
     * @return Session存储指标，如果不存在返回null
     */
    public SessionStorageMetrics getSessionMetrics(String sessionId) {
        return sessionMetrics.get(sessionId);
    }
    
    /**
     * 获取最近的异常记录
     * 
     * @param limit 返回记录数量限制
     * @return 最近的异常记录列表
     */
    public List<StorageException> getRecentExceptions(int limit) {
        synchronized (recentExceptions) {
            int size = recentExceptions.size();
            int fromIndex = Math.max(0, size - limit);
            return new ArrayList<>(recentExceptions.subList(fromIndex, size));
        }
    }
    
    /**
     * 清理Session监控数据
     * 
     * @param sessionId Session ID
     */
    public void cleanupSessionMetrics(String sessionId) {
        SessionStorageMetrics removed = sessionMetrics.remove(sessionId);
        if (removed != null) {
            activeShardCount.addAndGet(-removed.getShardCount());
            logger.debug("清理Session监控数据: sessionId={}, shardCount={}", sessionId, removed.getShardCount());
        }
    }
    
    /**
     * 重置所有统计信息
     */
    public void resetStatistics() {
        totalStorageOperations.set(0);
        totalStorageTime.set(0);
        totalStorageSize.set(0);
        activeShardCount.set(0);
        compressionSavings.set(0);
        storageFailures.set(0);
        oversizeShardCount.set(0);
        compressionFailures.set(0);
        sessionMetrics.clear();
        
        synchronized (recentExceptions) {
            recentExceptions.clear();
        }
        
        logger.info("存储监控统计信息已重置");
    }
    
    /**
     * 存储统计信息
     */
    public static class StorageStatistics {
        private final long totalOperations;
        private final long totalTime;
        private final long totalSize;
        private final int activeShards;
        private final long compressionSavings;
        private final long failures;
        private final long oversizeShards;
        private final long compressionFailures;
        private final int activeSessions;
        
        public StorageStatistics(long totalOperations, long totalTime, long totalSize, 
                               int activeShards, long compressionSavings, long failures, 
                               long oversizeShards, long compressionFailures, int activeSessions) {
            this.totalOperations = totalOperations;
            this.totalTime = totalTime;
            this.totalSize = totalSize;
            this.activeShards = activeShards;
            this.compressionSavings = compressionSavings;
            this.failures = failures;
            this.oversizeShards = oversizeShards;
            this.compressionFailures = compressionFailures;
            this.activeSessions = activeSessions;
        }
        
        // Getters
        public long getTotalOperations() { return totalOperations; }
        public long getTotalTime() { return totalTime; }
        public long getTotalSize() { return totalSize; }
        public int getActiveShards() { return activeShards; }
        public long getCompressionSavings() { return compressionSavings; }
        public long getFailures() { return failures; }
        public long getOversizeShards() { return oversizeShards; }
        public long getCompressionFailures() { return compressionFailures; }
        public int getActiveSessions() { return activeSessions; }
        
        /**
         * 计算平均操作时间
         * @return 平均操作时间（毫秒）
         */
        public double getAverageOperationTime() {
            return totalOperations > 0 ? (double) totalTime / totalOperations : 0.0;
        }
        
        /**
         * 计算成功率
         * @return 成功率（0.0-1.0）
         */
        public double getSuccessRate() {
            return totalOperations > 0 ? (double) (totalOperations - failures) / totalOperations : 1.0;
        }
        
        /**
         * 计算压缩节省率
         * @return 压缩节省率（0.0-1.0）
         */
        public double getCompressionSavingsRate() {
            return totalSize > 0 ? (double) compressionSavings / totalSize : 0.0;
        }
        
        @Override
        public String toString() {
            return String.format(
                "StorageStatistics{operations=%d, avgTime=%.2fms, totalSize=%d, " +
                "activeShards=%d, successRate=%.2f%%, compressionSavings=%d(%.2f%%)}",
                totalOperations, getAverageOperationTime(), totalSize, activeShards,
                getSuccessRate() * 100, compressionSavings, getCompressionSavingsRate() * 100
            );
        }
    }
}