package com.telegram.server.util;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ThreadLocalRandom;
import java.util.function.Supplier;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * TDLight异常重试处理工具类
 * 提供智能重试机制，支持指数退避、熔断器模式和异常分类处理
 * 
 * @author liubo
 * @date 2025-08-19
 */
@Component
public class RetryHandler {
    
    private static final Logger logger = LoggerFactory.getLogger(RetryHandler.class);
    
    // 默认重试配置
    private static final int DEFAULT_MAX_ATTEMPTS = 3;
    private static final long DEFAULT_BASE_DELAY_MS = 1000L;
    private static final long DEFAULT_MAX_DELAY_MS = 30000L;
    private static final double DEFAULT_BACKOFF_MULTIPLIER = 2.0;
    private static final double DEFAULT_JITTER_FACTOR = 0.1;
    
    // 熔断器配置
    private static final int CIRCUIT_BREAKER_FAILURE_THRESHOLD = 5;
    private static final long CIRCUIT_BREAKER_TIMEOUT_MS = 60000L; // 1分钟
    
    // 熔断器状态跟踪
    private final Map<String, CircuitBreakerState> circuitBreakers = new ConcurrentHashMap<>();
    
    /**
     * 重试配置类
     */
    public static class RetryConfig {
        private int maxAttempts = DEFAULT_MAX_ATTEMPTS;
        private long baseDelayMs = DEFAULT_BASE_DELAY_MS;
        private long maxDelayMs = DEFAULT_MAX_DELAY_MS;
        private double backoffMultiplier = DEFAULT_BACKOFF_MULTIPLIER;
        private double jitterFactor = DEFAULT_JITTER_FACTOR;
        private boolean enableCircuitBreaker = true;
        private String circuitBreakerKey = "default";
        
        public RetryConfig maxAttempts(int maxAttempts) {
            if (maxAttempts <= 0) {
                throw new IllegalArgumentException("Max attempts must be positive");
            }
            this.maxAttempts = maxAttempts;
            return this;
        }
        
        public RetryConfig baseDelayMs(long baseDelayMs) {
            if (baseDelayMs < 0) {
                throw new IllegalArgumentException("Base delay cannot be negative");
            }
            this.baseDelayMs = baseDelayMs;
            return this;
        }
        
        public RetryConfig maxDelayMs(long maxDelayMs) {
            if (maxDelayMs < 0) {
                throw new IllegalArgumentException("Max delay cannot be negative");
            }
            this.maxDelayMs = maxDelayMs;
            return this;
        }
        
        public RetryConfig backoffMultiplier(double backoffMultiplier) {
            if (backoffMultiplier < 1.0) {
                throw new IllegalArgumentException("Backoff multiplier must be >= 1.0");
            }
            this.backoffMultiplier = backoffMultiplier;
            return this;
        }
        
        public RetryConfig jitterFactor(double jitterFactor) {
            if (jitterFactor < 0.0 || jitterFactor > 1.0) {
                throw new IllegalArgumentException("Jitter factor must be between 0.0 and 1.0");
            }
            this.jitterFactor = jitterFactor;
            return this;
        }
        
        public RetryConfig enableCircuitBreaker(boolean enable) {
            this.enableCircuitBreaker = enable;
            return this;
        }
        
        public RetryConfig circuitBreakerKey(String key) {
            if (key == null || key.trim().isEmpty()) {
                throw new IllegalArgumentException("Circuit breaker key cannot be null or empty");
            }
            this.circuitBreakerKey = key.trim();
            return this;
        }
        
        // Getters
        public int getMaxAttempts() { return maxAttempts; }
        public long getBaseDelayMs() { return baseDelayMs; }
        public long getMaxDelayMs() { return maxDelayMs; }
        public double getBackoffMultiplier() { return backoffMultiplier; }
        public double getJitterFactor() { return jitterFactor; }
        public boolean isEnableCircuitBreaker() { return enableCircuitBreaker; }
        public String getCircuitBreakerKey() { return circuitBreakerKey; }
    }
    
    /**
     * 熔断器状态类
     */
    private static class CircuitBreakerState {
        private final AtomicInteger failureCount = new AtomicInteger(0);
        private volatile LocalDateTime lastFailureTime;
        private volatile boolean isOpen = false;
        
        public void recordSuccess() {
            failureCount.set(0);
            isOpen = false;
            lastFailureTime = null;
        }
        
        public void recordFailure() {
            int failures = failureCount.incrementAndGet();
            lastFailureTime = LocalDateTime.now();
            
            if (failures >= CIRCUIT_BREAKER_FAILURE_THRESHOLD) {
                isOpen = true;
            }
        }
        
        public boolean isOpen() {
            if (!isOpen) {
                return false;
            }
            
            // 检查是否应该尝试半开状态
            if (lastFailureTime != null) {
                long timeSinceLastFailure = ChronoUnit.MILLIS.between(lastFailureTime, LocalDateTime.now());
                if (timeSinceLastFailure >= CIRCUIT_BREAKER_TIMEOUT_MS) {
                    isOpen = false; // 进入半开状态
                    return false;
                }
            }
            
            return true;
        }
        
        public int getFailureCount() {
            return failureCount.get();
        }
    }
    
    /**
     * 重试结果类
     */
    public static class RetryResult<T> {
        private final T result;
        private final boolean success;
        private final int attemptCount;
        private final Exception lastException;
        private final long totalDurationMs;
        
        private RetryResult(T result, boolean success, int attemptCount, Exception lastException, long totalDurationMs) {
            this.result = result;
            this.success = success;
            this.attemptCount = attemptCount;
            this.lastException = lastException;
            this.totalDurationMs = totalDurationMs;
        }
        
        public static <T> RetryResult<T> success(T result, int attemptCount, long totalDurationMs) {
            return new RetryResult<>(result, true, attemptCount, null, totalDurationMs);
        }
        
        public static <T> RetryResult<T> failure(Exception lastException, int attemptCount, long totalDurationMs) {
            return new RetryResult<>(null, false, attemptCount, lastException, totalDurationMs);
        }
        
        // Getters
        public T getResult() { return result; }
        public boolean isSuccess() { return success; }
        public int getAttemptCount() { return attemptCount; }
        public Exception getLastException() { return lastException; }
        public long getTotalDurationMs() { return totalDurationMs; }
    }
    
    /**
     * 使用默认配置执行重试
     * 
     * @param operation 要执行的操作
     * @param operationName 操作名称，用于日志记录
     * @return 重试结果
     */
    public <T> RetryResult<T> executeWithRetry(Supplier<T> operation, String operationName) {
        return executeWithRetry(operation, new RetryConfig(), operationName);
    }
    
    /**
     * 使用指定配置执行重试
     * 
     * @param operation 要执行的操作
     * @param config 重试配置
     * @param operationName 操作名称，用于日志记录
     * @return 重试结果
     */
    public <T> RetryResult<T> executeWithRetry(Supplier<T> operation, RetryConfig config, String operationName) {
        if (operation == null) {
            throw new IllegalArgumentException("Operation cannot be null");
        }
        if (config == null) {
            throw new IllegalArgumentException("Config cannot be null");
        }
        if (operationName == null || operationName.trim().isEmpty()) {
            operationName = "unknown-operation";
        }
        
        long startTime = System.currentTimeMillis();
        Exception lastException = null;
        
        // 检查熔断器状态
        if (config.isEnableCircuitBreaker()) {
            CircuitBreakerState circuitBreaker = getCircuitBreaker(config.getCircuitBreakerKey());
            if (circuitBreaker.isOpen()) {
                logger.warn("Circuit breaker is open for key: {}, operation: {}, failure count: {}", 
                    config.getCircuitBreakerKey(), operationName, circuitBreaker.getFailureCount());
                return RetryResult.failure(
                    new RuntimeException("Circuit breaker is open"), 
                    0, 
                    System.currentTimeMillis() - startTime
                );
            }
        }
        
        for (int attempt = 1; attempt <= config.getMaxAttempts(); attempt++) {
            try {
                logger.debug("Executing operation: {}, attempt: {}/{}", operationName, attempt, config.getMaxAttempts());
                
                T result = operation.get();
                
                // 记录成功
                if (config.isEnableCircuitBreaker()) {
                    getCircuitBreaker(config.getCircuitBreakerKey()).recordSuccess();
                }
                
                long totalDuration = System.currentTimeMillis() - startTime;
                logger.info("Operation succeeded: {}, attempt: {}/{}, duration: {}ms", 
                    operationName, attempt, config.getMaxAttempts(), totalDuration);
                
                return RetryResult.success(result, attempt, totalDuration);
                
            } catch (Exception e) {
                lastException = e;
                
                // 检查是否应该重试
                if (!shouldRetry(e, attempt, config.getMaxAttempts())) {
                    logger.error("Operation failed and will not retry: {}, attempt: {}/{}, error: {}", 
                        operationName, attempt, config.getMaxAttempts(), e.getMessage());
                    break;
                }
                
                logger.warn("Operation failed: {}, attempt: {}/{}, error: {}, will retry", 
                    operationName, attempt, config.getMaxAttempts(), e.getMessage());
                
                // 如果不是最后一次尝试，则等待
                if (attempt < config.getMaxAttempts()) {
                    long delay = calculateDelay(attempt, config);
                    try {
                        Thread.sleep(delay);
                    } catch (InterruptedException ie) {
                        Thread.currentThread().interrupt();
                        logger.error("Retry interrupted for operation: {}", operationName);
                        break;
                    }
                }
            }
        }
        
        // 记录失败
        if (config.isEnableCircuitBreaker()) {
            getCircuitBreaker(config.getCircuitBreakerKey()).recordFailure();
        }
        
        long totalDuration = System.currentTimeMillis() - startTime;
        logger.error("Operation failed after all retries: {}, attempts: {}, duration: {}ms, last error: {}", 
            operationName, config.getMaxAttempts(), totalDuration, 
            lastException != null ? lastException.getMessage() : "unknown");
        
        return RetryResult.failure(lastException, config.getMaxAttempts(), totalDuration);
    }
    
    /**
     * 异步执行重试
     * 
     * @param operation 要执行的操作
     * @param config 重试配置
     * @param operationName 操作名称
     * @return CompletableFuture包装的重试结果
     */
    public <T> CompletableFuture<RetryResult<T>> executeWithRetryAsync(Supplier<T> operation, RetryConfig config, String operationName) {
        return CompletableFuture.supplyAsync(() -> executeWithRetry(operation, config, operationName));
    }
    
    /**
     * 判断是否应该重试
     * 
     * @param exception 异常
     * @param currentAttempt 当前尝试次数
     * @param maxAttempts 最大尝试次数
     * @return 是否应该重试
     */
    private boolean shouldRetry(Exception exception, int currentAttempt, int maxAttempts) {
        // 如果已达到最大尝试次数，不重试
        if (currentAttempt >= maxAttempts) {
            return false;
        }
        
        // 如果是中断异常，不重试
        if (exception instanceof InterruptedException) {
            return false;
        }
        
        // 检查异常类型和消息，判断是否应该重试
        String exceptionMessage = exception.getMessage();
        if (exceptionMessage != null) {
            String lowerMessage = exceptionMessage.toLowerCase();
            
            // TDLight相关的可重试异常
            if (lowerMessage.contains("connection") || 
                lowerMessage.contains("timeout") ||
                lowerMessage.contains("network") ||
                lowerMessage.contains("receive wrong user") ||
                lowerMessage.contains("tdlib") ||
                lowerMessage.contains("temporary")) {
                return true;
            }
            
            // 不可重试的异常
            if (lowerMessage.contains("authentication") ||
                lowerMessage.contains("authorization") ||
                lowerMessage.contains("invalid credentials") ||
                lowerMessage.contains("access denied") ||
                lowerMessage.contains("forbidden")) {
                return false;
            }
        }
        
        // 默认情况下，网络相关异常可以重试
        return exception instanceof RuntimeException;
    }
    
    /**
     * 计算延迟时间（指数退避 + 抖动）
     * 
     * @param attempt 当前尝试次数
     * @param config 重试配置
     * @return 延迟时间（毫秒）
     */
    private long calculateDelay(int attempt, RetryConfig config) {
        // 指数退避
        double delay = config.getBaseDelayMs() * Math.pow(config.getBackoffMultiplier(), attempt - 1);
        
        // 限制最大延迟
        delay = Math.min(delay, config.getMaxDelayMs());
        
        // 添加抖动
        if (config.getJitterFactor() > 0) {
            double jitter = delay * config.getJitterFactor();
            double randomFactor = ThreadLocalRandom.current().nextDouble(-jitter, jitter);
            delay += randomFactor;
        }
        
        // 确保延迟不为负数
        return Math.max(0, (long) delay);
    }
    
    /**
     * 获取熔断器状态
     * 
     * @param key 熔断器键
     * @return 熔断器状态
     */
    private CircuitBreakerState getCircuitBreaker(String key) {
        return circuitBreakers.computeIfAbsent(key, k -> new CircuitBreakerState());
    }
    
    /**
     * 重置熔断器状态
     * 
     * @param key 熔断器键
     */
    public void resetCircuitBreaker(String key) {
        if (key == null || key.trim().isEmpty()) {
            throw new IllegalArgumentException("Circuit breaker key cannot be null or empty");
        }
        
        CircuitBreakerState state = circuitBreakers.get(key);
        if (state != null) {
            state.recordSuccess();
            logger.info("Circuit breaker reset for key: {}", key);
        }
    }
    
    /**
     * 获取熔断器状态信息
     * 
     * @param key 熔断器键
     * @return 状态信息字符串
     */
    public String getCircuitBreakerStatus(String key) {
        if (key == null || key.trim().isEmpty()) {
            return "Invalid key";
        }
        
        CircuitBreakerState state = circuitBreakers.get(key);
        if (state == null) {
            return "Circuit breaker not found for key: " + key;
        }
        
        return String.format("Circuit Breaker [%s]: Open=%s, FailureCount=%d, LastFailure=%s", 
            key, state.isOpen(), state.getFailureCount(), 
            state.lastFailureTime != null ? state.lastFailureTime.toString() : "None");
    }
    
    /**
     * 获取所有熔断器状态信息
     * 
     * @return 所有熔断器状态信息
     */
    public String getAllCircuitBreakerStatus() {
        if (circuitBreakers.isEmpty()) {
            return "No circuit breakers registered";
        }
        
        StringBuilder sb = new StringBuilder("Circuit Breaker Status:\n");
        circuitBreakers.forEach((key, state) -> {
            sb.append(String.format("  [%s]: Open=%s, FailureCount=%d, LastFailure=%s\n", 
                key, state.isOpen(), state.getFailureCount(), 
                state.lastFailureTime != null ? state.lastFailureTime.toString() : "None"));
        });
        
        return sb.toString();
    }
    
    /**
     * 清理所有熔断器状态
     */
    public void clearAllCircuitBreakers() {
        int count = circuitBreakers.size();
        circuitBreakers.clear();
        logger.info("Cleared {} circuit breakers", count);
    }
    
    /**
     * 创建TDLight专用的重试配置
     * 
     * @return TDLight重试配置
     */
    public static RetryConfig createTdLightConfig() {
        return new RetryConfig()
            .maxAttempts(3)
            .baseDelayMs(2000L)
            .maxDelayMs(10000L)
            .backoffMultiplier(2.0)
            .jitterFactor(0.2)
            .enableCircuitBreaker(true)
            .circuitBreakerKey("tdlight");
    }
    
    /**
     * 创建网络操作专用的重试配置
     * 
     * @return 网络重试配置
     */
    public static RetryConfig createNetworkConfig() {
        return new RetryConfig()
            .maxAttempts(5)
            .baseDelayMs(1000L)
            .maxDelayMs(30000L)
            .backoffMultiplier(1.5)
            .jitterFactor(0.1)
            .enableCircuitBreaker(true)
            .circuitBreakerKey("network");
    }
    
    /**
     * 创建快速重试配置（用于轻量级操作）
     * 
     * @return 快速重试配置
     */
    public static RetryConfig createFastConfig() {
        return new RetryConfig()
            .maxAttempts(2)
            .baseDelayMs(500L)
            .maxDelayMs(2000L)
            .backoffMultiplier(2.0)
            .jitterFactor(0.05)
            .enableCircuitBreaker(false);
    }
}