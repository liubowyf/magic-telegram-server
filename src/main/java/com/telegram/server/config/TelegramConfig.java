package com.telegram.server.config;

import com.telegram.server.util.PathValidator;
import com.telegram.server.util.RetryHandler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.validation.annotation.Validated;

import jakarta.annotation.PostConstruct;
import java.time.ZoneId;
import java.util.concurrent.TimeUnit;

/**
 * Telegram相关配置类
 * 包含TDLight客户端配置、时区配置、重试配置和路径配置
 * 
 * @author liubo
 * @date 2025-08-19
 */
@Configuration
@ConfigurationProperties(prefix = "telegram")
@Validated
public class TelegramConfig {
    
    private static final Logger logger = LoggerFactory.getLogger(TelegramConfig.class);
    
    /**
     * TDLight客户端配置
     */
    private TdlightConfig tdlight = new TdlightConfig();
    
    /**
     * 时区配置
     */
    private TimezoneConfig timezone = new TimezoneConfig();
    
    /**
     * 重试配置
     */
    private RetryConfig retry = new RetryConfig();
    
    /**
     * 路径配置
     */
    private PathConfig path = new PathConfig();
    
    @PostConstruct
    public void init() {
        logger.info("Initializing Telegram configuration...");
        
        // 验证时区配置
        try {
            ZoneId.of(timezone.getDefaultZone());
            logger.info("Default timezone validated: {}", timezone.getDefaultZone());
        } catch (Exception e) {
            logger.warn("Invalid default timezone: {}, falling back to UTC", timezone.getDefaultZone());
            timezone.setDefaultZone("UTC");
        }
        
        // 验证路径配置
        if (path.getDataDirectory() != null && !path.getDataDirectory().trim().isEmpty()) {
            logger.info("Data directory configured: {}", path.getDataDirectory());
        } else {
            logger.info("No data directory configured, will use system temp directory");
        }
        
        logger.info("Telegram configuration initialized successfully");
    }
    
    /**
     * 创建TDLight重试处理器Bean
     */
    @Bean("tdlightRetryHandler")
    public RetryHandler createTdlightRetryHandler() {
        RetryHandler.RetryConfig config = new RetryHandler.RetryConfig()
            .maxAttempts(retry.getTdlight().getMaxAttempts())
            .baseDelayMs(retry.getTdlight().getInitialDelayMs())
            .maxDelayMs(retry.getTdlight().getMaxDelayMs())
            .backoffMultiplier(retry.getTdlight().getMultiplier())
            .circuitBreakerKey("tdlight");
        
        RetryHandler retryHandler = new RetryHandler();
        logger.info("Created TDLight retry handler with config: {}", config);
        return retryHandler;
    }
    
    /**
     * 创建网络操作重试处理器Bean
     */
    @Bean("networkRetryHandler")
    public RetryHandler createNetworkRetryHandler() {
        RetryHandler.RetryConfig config = new RetryHandler.RetryConfig()
            .maxAttempts(retry.getNetwork().getMaxAttempts())
            .baseDelayMs(retry.getNetwork().getInitialDelayMs())
            .maxDelayMs(retry.getNetwork().getMaxDelayMs())
            .backoffMultiplier(retry.getNetwork().getMultiplier())
            .circuitBreakerKey("network");
        
        RetryHandler retryHandler = new RetryHandler();
        logger.info("Created network retry handler with config: {}", config);
        return retryHandler;
    }
    
    // Getters and Setters
    public TdlightConfig getTdlight() {
        return tdlight;
    }
    
    public void setTdlight(TdlightConfig tdlight) {
        this.tdlight = tdlight;
    }
    
    public TimezoneConfig getTimezone() {
        return timezone;
    }
    
    public void setTimezone(TimezoneConfig timezone) {
        this.timezone = timezone;
    }
    
    public RetryConfig getRetry() {
        return retry;
    }
    
    public void setRetry(RetryConfig retry) {
        this.retry = retry;
    }
    
    public PathConfig getPath() {
        return path;
    }
    
    public void setPath(PathConfig path) {
        this.path = path;
    }
    
    /**
     * TDLight客户端配置
     */
    public static class TdlightConfig {
        
        private String apiId;
        
        private String apiHash;
        
        private String phoneNumber;
        
        private long sessionTimeoutMs = TimeUnit.MINUTES.toMillis(30);
        
        private long connectionTimeoutMs = TimeUnit.SECONDS.toMillis(30);
        
        private boolean enableLogging = false;
        
        private String logLevel = "INFO";
        
        // Getters and Setters
        public String getApiId() {
            return apiId;
        }
        
        public void setApiId(String apiId) {
            this.apiId = apiId;
        }
        
        public String getApiHash() {
            return apiHash;
        }
        
        public void setApiHash(String apiHash) {
            this.apiHash = apiHash;
        }
        
        public String getPhoneNumber() {
            return phoneNumber;
        }
        
        public void setPhoneNumber(String phoneNumber) {
            this.phoneNumber = phoneNumber;
        }
        
        public long getSessionTimeoutMs() {
            return sessionTimeoutMs;
        }
        
        public void setSessionTimeoutMs(long sessionTimeoutMs) {
            this.sessionTimeoutMs = sessionTimeoutMs;
        }
        
        public long getConnectionTimeoutMs() {
            return connectionTimeoutMs;
        }
        
        public void setConnectionTimeoutMs(long connectionTimeoutMs) {
            this.connectionTimeoutMs = connectionTimeoutMs;
        }
        
        public boolean isEnableLogging() {
            return enableLogging;
        }
        
        public void setEnableLogging(boolean enableLogging) {
            this.enableLogging = enableLogging;
        }
        
        public String getLogLevel() {
            return logLevel;
        }
        
        public void setLogLevel(String logLevel) {
            this.logLevel = logLevel;
        }
    }
    
    /**
     * 时区配置
     */
    public static class TimezoneConfig {
        
        private String defaultZone = "UTC";
        
        private boolean storeInUtc = true;
        
        private boolean autoDetectUserTimezone = false;
        
        private String displayZone = "Asia/Shanghai";
        
        // Getters and Setters
        public String getDefaultZone() {
            return defaultZone;
        }
        
        public void setDefaultZone(String defaultZone) {
            this.defaultZone = defaultZone;
        }
        
        public boolean isStoreInUtc() {
            return storeInUtc;
        }
        
        public void setStoreInUtc(boolean storeInUtc) {
            this.storeInUtc = storeInUtc;
        }
        
        public boolean isAutoDetectUserTimezone() {
            return autoDetectUserTimezone;
        }
        
        public void setAutoDetectUserTimezone(boolean autoDetectUserTimezone) {
            this.autoDetectUserTimezone = autoDetectUserTimezone;
        }
        
        public String getDisplayZone() {
            return displayZone;
        }
        
        public void setDisplayZone(String displayZone) {
            this.displayZone = displayZone;
        }
    }
    
    /**
     * 重试配置
     */
    public static class RetryConfig {
        
        private TdlightRetryConfig tdlight = new TdlightRetryConfig();
        private NetworkRetryConfig network = new NetworkRetryConfig();
        
        // Getters and Setters
        public TdlightRetryConfig getTdlight() {
            return tdlight;
        }
        
        public void setTdlight(TdlightRetryConfig tdlight) {
            this.tdlight = tdlight;
        }
        
        public NetworkRetryConfig getNetwork() {
            return network;
        }
        
        public void setNetwork(NetworkRetryConfig network) {
            this.network = network;
        }
    }
    
    /**
     * TDLight重试配置
     */
    public static class TdlightRetryConfig {
        
        private int maxAttempts = 3;
        
        private long initialDelayMs = 1000;
        
        private long maxDelayMs = 30000;
        
        private double multiplier = 2.0;
        
        private long timeoutMs = 60000;
        
        private CircuitBreakerConfig circuitBreaker = new CircuitBreakerConfig();
        
        // Getters and Setters
        public int getMaxAttempts() {
            return maxAttempts;
        }
        
        public void setMaxAttempts(int maxAttempts) {
            this.maxAttempts = maxAttempts;
        }
        
        public long getInitialDelayMs() {
            return initialDelayMs;
        }
        
        public void setInitialDelayMs(long initialDelayMs) {
            this.initialDelayMs = initialDelayMs;
        }
        
        public long getMaxDelayMs() {
            return maxDelayMs;
        }
        
        public void setMaxDelayMs(long maxDelayMs) {
            this.maxDelayMs = maxDelayMs;
        }
        
        public double getMultiplier() {
            return multiplier;
        }
        
        public void setMultiplier(double multiplier) {
            this.multiplier = multiplier;
        }
        
        public long getTimeoutMs() {
            return timeoutMs;
        }
        
        public void setTimeoutMs(long timeoutMs) {
            this.timeoutMs = timeoutMs;
        }
        
        public CircuitBreakerConfig getCircuitBreaker() {
            return circuitBreaker;
        }
        
        public void setCircuitBreaker(CircuitBreakerConfig circuitBreaker) {
            this.circuitBreaker = circuitBreaker;
        }
    }
    
    /**
     * 网络重试配置
     */
    public static class NetworkRetryConfig {
        
        private int maxAttempts = 5;
        
        private long initialDelayMs = 500;
        
        private long maxDelayMs = 10000;
        
        private double multiplier = 1.5;
        
        private long timeoutMs = 30000;
        
        private CircuitBreakerConfig circuitBreaker = new CircuitBreakerConfig();
        
        // Getters and Setters
        public int getMaxAttempts() {
            return maxAttempts;
        }
        
        public void setMaxAttempts(int maxAttempts) {
            this.maxAttempts = maxAttempts;
        }
        
        public long getInitialDelayMs() {
            return initialDelayMs;
        }
        
        public void setInitialDelayMs(long initialDelayMs) {
            this.initialDelayMs = initialDelayMs;
        }
        
        public long getMaxDelayMs() {
            return maxDelayMs;
        }
        
        public void setMaxDelayMs(long maxDelayMs) {
            this.maxDelayMs = maxDelayMs;
        }
        
        public double getMultiplier() {
            return multiplier;
        }
        
        public void setMultiplier(double multiplier) {
            this.multiplier = multiplier;
        }
        
        public long getTimeoutMs() {
            return timeoutMs;
        }
        
        public void setTimeoutMs(long timeoutMs) {
            this.timeoutMs = timeoutMs;
        }
        
        public CircuitBreakerConfig getCircuitBreaker() {
            return circuitBreaker;
        }
        
        public void setCircuitBreaker(CircuitBreakerConfig circuitBreaker) {
            this.circuitBreaker = circuitBreaker;
        }
    }
    
    /**
     * 熔断器配置
     */
    public static class CircuitBreakerConfig {
        
        private int failureThreshold = 5;
        
        private long recoveryTimeoutMs = 60000;
        
        private int halfOpenMaxCalls = 3;
        
        // Getters and Setters
        public int getFailureThreshold() {
            return failureThreshold;
        }
        
        public void setFailureThreshold(int failureThreshold) {
            this.failureThreshold = failureThreshold;
        }
        
        public long getRecoveryTimeoutMs() {
            return recoveryTimeoutMs;
        }
        
        public void setRecoveryTimeoutMs(long recoveryTimeoutMs) {
            this.recoveryTimeoutMs = recoveryTimeoutMs;
        }
        
        public int getHalfOpenMaxCalls() {
            return halfOpenMaxCalls;
        }
        
        public void setHalfOpenMaxCalls(int halfOpenMaxCalls) {
            this.halfOpenMaxCalls = halfOpenMaxCalls;
        }
    }
    
    /**
     * 路径配置
     */
    public static class PathConfig {
        
        private String dataDirectory;
        
        private String sessionDirectory;
        
        private String logDirectory;
        
        private String tempDirectory;
        
        private boolean autoCreateDirectories = true;
        
        private boolean validatePaths = true;
        
        // Getters and Setters
        public String getDataDirectory() {
            return dataDirectory;
        }
        
        public void setDataDirectory(String dataDirectory) {
            this.dataDirectory = dataDirectory;
        }
        
        public String getSessionDirectory() {
            return sessionDirectory;
        }
        
        public void setSessionDirectory(String sessionDirectory) {
            this.sessionDirectory = sessionDirectory;
        }
        
        public String getLogDirectory() {
            return logDirectory;
        }
        
        public void setLogDirectory(String logDirectory) {
            this.logDirectory = logDirectory;
        }
        
        public String getTempDirectory() {
            return tempDirectory;
        }
        
        public void setTempDirectory(String tempDirectory) {
            this.tempDirectory = tempDirectory;
        }
        
        public boolean isAutoCreateDirectories() {
            return autoCreateDirectories;
        }
        
        public void setAutoCreateDirectories(boolean autoCreateDirectories) {
            this.autoCreateDirectories = autoCreateDirectories;
        }
        
        public boolean isValidatePaths() {
            return validatePaths;
        }
        
        public void setValidatePaths(boolean validatePaths) {
            this.validatePaths = validatePaths;
        }
    }
}