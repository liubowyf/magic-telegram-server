package com.telegram.server.storage.exception;

import com.telegram.server.storage.monitor.StorageMonitor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

/**
 * 存储异常处理器
 * 处理分片存储过程中的各种异常情况，提供重试、降级和恢复机制
 * 
 * @author liubo
 * @date 2025-08-20
 */
@Component
public class StorageExceptionHandler {
    
    private static final Logger logger = LoggerFactory.getLogger(StorageExceptionHandler.class);
    
    @Autowired
    private StorageMonitor storageMonitor;
    
    // 重试配置
    private static final int MAX_RETRY_ATTEMPTS = 3;
    private static final long INITIAL_RETRY_DELAY_MS = 1000; // 1秒
    private static final double RETRY_DELAY_MULTIPLIER = 2.0;
    
    // 熔断器配置
    private static final int CIRCUIT_BREAKER_FAILURE_THRESHOLD = 10;
    private static final long CIRCUIT_BREAKER_TIMEOUT_MS = 60000; // 1分钟
    
    // 熔断器状态
    private final AtomicInteger consecutiveFailures = new AtomicInteger(0);
    private final AtomicLong lastFailureTime = new AtomicLong(0);
    private volatile boolean circuitBreakerOpen = false;
    
    /**
     * 存储操作结果
     */
    public static class StorageOperationResult<T> {
        private final boolean success;
        private final T result;
        private final StorageException exception;
        private final int attemptCount;
        
        private StorageOperationResult(boolean success, T result, StorageException exception, int attemptCount) {
            this.success = success;
            this.result = result;
            this.exception = exception;
            this.attemptCount = attemptCount;
        }
        
        public static <T> StorageOperationResult<T> success(T result, int attemptCount) {
            return new StorageOperationResult<>(true, result, null, attemptCount);
        }
        
        public static <T> StorageOperationResult<T> failure(StorageException exception, int attemptCount) {
            return new StorageOperationResult<>(false, null, exception, attemptCount);
        }
        
        // Getters
        public boolean isSuccess() { return success; }
        public T getResult() { return result; }
        public StorageException getException() { return exception; }
        public int getAttemptCount() { return attemptCount; }
    }
    
    /**
     * 存储操作接口
     */
    @FunctionalInterface
    public interface StorageOperation<T> {
        T execute() throws StorageException;
    }
    
    /**
     * 执行带重试的存储操作
     * 
     * @param sessionId Session ID
     * @param operationName 操作名称
     * @param operation 存储操作
     * @param <T> 返回类型
     * @return 操作结果
     */
    public <T> StorageOperationResult<T> executeWithRetry(String sessionId, String operationName, 
                                                         StorageOperation<T> operation) {
        // 检查熔断器状态
        if (isCircuitBreakerOpen()) {
            StorageException exception = new StorageException(
                StorageException.ErrorType.CIRCUIT_BREAKER_OPEN,
                "熔断器开启，拒绝执行存储操作",
                sessionId
            );
            storageMonitor.recordStorageFailure("circuit-breaker", sessionId, operationName, 
                                               exception.getMessage(), 0);
            return StorageOperationResult.failure(exception, 0);
        }
        
        int attemptCount = 0;
        long retryDelay = INITIAL_RETRY_DELAY_MS;
        
        while (attemptCount < MAX_RETRY_ATTEMPTS) {
            attemptCount++;
            
            try {
                logger.debug("执行存储操作: sessionId={}, operation={}, attempt={}/{}", 
                           sessionId, operationName, attemptCount, MAX_RETRY_ATTEMPTS);
                
                T result = operation.execute();
                
                // 操作成功，重置熔断器
                resetCircuitBreaker();
                
                logger.debug("存储操作成功: sessionId={}, operation={}, attempt={}", 
                           sessionId, operationName, attemptCount);
                
                return StorageOperationResult.success(result, attemptCount);
                
            } catch (StorageException e) {
                logger.warn("存储操作失败: sessionId={}, operation={}, attempt={}/{}, error={}", 
                          sessionId, operationName, attemptCount, MAX_RETRY_ATTEMPTS, e.getMessage());
                
                // 记录失败
                storageMonitor.recordStorageFailure(
                    "retry-" + attemptCount, sessionId, operationName, e.getMessage(), 0
                );
                
                // 判断是否应该重试
                if (!shouldRetry(e, attemptCount)) {
                    updateCircuitBreaker();
                    return StorageOperationResult.failure(e, attemptCount);
                }
                
                // 等待重试
                if (attemptCount < MAX_RETRY_ATTEMPTS) {
                    try {
                        Thread.sleep(retryDelay);
                        retryDelay = (long) (retryDelay * RETRY_DELAY_MULTIPLIER);
                    } catch (InterruptedException ie) {
                        Thread.currentThread().interrupt();
                        StorageException interruptedException = new StorageException(
                            StorageException.ErrorType.OPERATION_INTERRUPTED,
                            "存储操作被中断",
                            sessionId,
                            ie
                        );
                        return StorageOperationResult.failure(interruptedException, attemptCount);
                    }
                }
            }
        }
        
        // 所有重试都失败了
        updateCircuitBreaker();
        StorageException finalException = new StorageException(
            StorageException.ErrorType.MAX_RETRIES_EXCEEDED,
            String.format("存储操作在%d次重试后仍然失败", MAX_RETRY_ATTEMPTS),
            sessionId
        );
        
        return StorageOperationResult.failure(finalException, attemptCount);
    }
    
    /**
     * 处理分片大小超限异常
     * 
     * @param sessionId Session ID
     * @param shardSize 分片大小
     * @param maxSize 最大允许大小
     * @return 处理建议
     */
    public ShardSizeExceptionHandler handleShardSizeException(String sessionId, long shardSize, long maxSize) {
        storageMonitor.recordOversizeShard(sessionId, shardSize, maxSize);
        
        logger.error("分片大小超限: sessionId={}, shardSize={}, maxSize={}", sessionId, shardSize, maxSize);
        
        return new ShardSizeExceptionHandler(sessionId, shardSize, maxSize);
    }
    
    /**
     * 处理压缩失败异常
     * 
     * @param sessionId Session ID
     * @param dataSize 数据大小
     * @param compressionType 压缩类型
     * @param cause 原因
     * @return 处理建议
     */
    public CompressionExceptionHandler handleCompressionException(String sessionId, long dataSize, 
                                                                 String compressionType, Throwable cause) {
        String errorMessage = String.format("压缩失败: type=%s, cause=%s", compressionType, cause.getMessage());
        storageMonitor.recordCompressionFailure(sessionId, dataSize, errorMessage);
        
        logger.error("压缩失败: sessionId={}, dataSize={}, compressionType={}", 
                    sessionId, dataSize, compressionType, cause);
        
        return new CompressionExceptionHandler(sessionId, dataSize, compressionType, cause);
    }
    
    /**
     * 处理MongoDB存储异常
     * 
     * @param sessionId Session ID
     * @param operation 操作类型
     * @param cause 原因
     * @return 处理建议
     */
    public MongoExceptionHandler handleMongoException(String sessionId, String operation, Throwable cause) {
        String errorMessage = String.format("MongoDB操作失败: operation=%s, cause=%s", operation, cause.getMessage());
        storageMonitor.recordStorageFailure("mongo-error", sessionId, operation, errorMessage, 0);
        
        logger.error("MongoDB操作失败: sessionId={}, operation={}", sessionId, operation, cause);
        
        return new MongoExceptionHandler(sessionId, operation, cause);
    }
    
    /**
     * 判断是否应该重试
     * 
     * @param exception 异常
     * @param attemptCount 当前尝试次数
     * @return 是否应该重试
     */
    private boolean shouldRetry(StorageException exception, int attemptCount) {
        if (attemptCount >= MAX_RETRY_ATTEMPTS) {
            return false;
        }
        
        switch (exception.getErrorType()) {
            case NETWORK_ERROR:
            case TEMPORARY_FAILURE:
            case TIMEOUT:
                return true;
            case DOCUMENT_SIZE_EXCEEDED:
            case INVALID_DATA:
            case AUTHENTICATION_FAILED:
            case CIRCUIT_BREAKER_OPEN:
            case OPERATION_INTERRUPTED:
                return false;
            default:
                return attemptCount < 2; // 对于未知错误，最多重试1次
        }
    }
    
    /**
     * 检查熔断器是否开启
     * 
     * @return 熔断器是否开启
     */
    private boolean isCircuitBreakerOpen() {
        if (!circuitBreakerOpen) {
            return false;
        }
        
        // 检查是否应该尝试恢复
        long timeSinceLastFailure = System.currentTimeMillis() - lastFailureTime.get();
        if (timeSinceLastFailure > CIRCUIT_BREAKER_TIMEOUT_MS) {
            logger.info("熔断器超时，尝试恢复");
            circuitBreakerOpen = false;
            return false;
        }
        
        return true;
    }
    
    /**
     * 更新熔断器状态（失败时调用）
     */
    private void updateCircuitBreaker() {
        int failures = consecutiveFailures.incrementAndGet();
        lastFailureTime.set(System.currentTimeMillis());
        
        if (failures >= CIRCUIT_BREAKER_FAILURE_THRESHOLD && !circuitBreakerOpen) {
            circuitBreakerOpen = true;
            logger.error("熔断器开启: 连续失败{}次，将在{}ms后尝试恢复", 
                        failures, CIRCUIT_BREAKER_TIMEOUT_MS);
        }
    }
    
    /**
     * 重置熔断器状态（成功时调用）
     */
    private void resetCircuitBreaker() {
        if (consecutiveFailures.get() > 0) {
            consecutiveFailures.set(0);
            if (circuitBreakerOpen) {
                circuitBreakerOpen = false;
                logger.info("熔断器恢复: 操作成功，重置失败计数");
            }
        }
    }
    
    /**
     * 获取熔断器状态
     * 
     * @return 熔断器状态信息
     */
    public CircuitBreakerStatus getCircuitBreakerStatus() {
        return new CircuitBreakerStatus(
            circuitBreakerOpen,
            consecutiveFailures.get(),
            lastFailureTime.get(),
            CIRCUIT_BREAKER_FAILURE_THRESHOLD,
            CIRCUIT_BREAKER_TIMEOUT_MS
        );
    }
    
    /**
     * 熔断器状态信息
     */
    public static class CircuitBreakerStatus {
        private final boolean open;
        private final int consecutiveFailures;
        private final long lastFailureTime;
        private final int failureThreshold;
        private final long timeoutMs;
        
        public CircuitBreakerStatus(boolean open, int consecutiveFailures, long lastFailureTime, 
                                  int failureThreshold, long timeoutMs) {
            this.open = open;
            this.consecutiveFailures = consecutiveFailures;
            this.lastFailureTime = lastFailureTime;
            this.failureThreshold = failureThreshold;
            this.timeoutMs = timeoutMs;
        }
        
        // Getters
        public boolean isOpen() { return open; }
        public int getConsecutiveFailures() { return consecutiveFailures; }
        public long getLastFailureTime() { return lastFailureTime; }
        public int getFailureThreshold() { return failureThreshold; }
        public long getTimeoutMs() { return timeoutMs; }
        
        /**
         * 获取剩余恢复时间
         * @return 剩余恢复时间（毫秒），如果熔断器未开启返回0
         */
        public long getRemainingRecoveryTime() {
            if (!open) {
                return 0;
            }
            long elapsed = System.currentTimeMillis() - lastFailureTime;
            return Math.max(0, timeoutMs - elapsed);
        }
        
        @Override
        public String toString() {
            return String.format(
                "CircuitBreakerStatus{open=%s, failures=%d/%d, remainingRecovery=%dms}",
                open, consecutiveFailures, failureThreshold, getRemainingRecoveryTime()
            );
        }
    }
}