package com.telegram.server.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.util.concurrent.Executor;
import java.util.concurrent.ThreadPoolExecutor;

/**
 * 消息存储配置类
 * 用于配置Telegram消息存储的相关参数
 * 
 * @author liubo
 * @date 2025-08-19
 */
@Configuration
@ConfigurationProperties(prefix = "message.storage")
public class MessageStorageConfig {

    /**
     * 是否启用消息存储功能
     * 默认启用
     */
    private boolean enabled = true;

    /**
     * 批量保存的批次大小
     * 默认100条消息为一批
     */
    private int batchSize = 100;

    /**
     * 批量保存的超时时间（毫秒）
     * 默认5秒超时
     */
    private long batchTimeout = 5000;

    /**
     * 异步处理队列的最大容量
     * 默认10000条消息
     */
    private int queueCapacity = 10000;

    /**
     * 消息处理线程池核心线程数
     * 默认2个线程
     */
    private int corePoolSize = 2;

    /**
     * 消息处理线程池最大线程数
     * 默认8个线程
     */
    private int maxPoolSize = 8;

    /**
     * 线程空闲时间（秒）
     * 默认60秒
     */
    private int keepAliveTime = 60;

    /**
     * 是否启用性能监控
     * 默认启用
     */
    private boolean monitoringEnabled = true;

    /**
     * 性能统计输出间隔（秒）
     * 默认60秒输出一次统计信息
     */
    private int statisticsInterval = 60;

    /**
     * 是否启用消息去重
     * 默认启用
     */
    private boolean deduplicationEnabled = true;

    /**
     * 消息重试次数
     * 默认重试3次
     */
    private int retryAttempts = 3;

    /**
     * 重试间隔（毫秒）
     * 默认1秒
     */
    private long retryInterval = 1000;

    /**
     * 是否启用详细日志
     * 默认关闭，避免日志过多
     */
    private boolean verboseLogging = false;

    // ==================== Getter和Setter方法 ====================

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public int getBatchSize() {
        return batchSize;
    }

    public void setBatchSize(int batchSize) {
        this.batchSize = batchSize;
    }

    public long getBatchTimeout() {
        return batchTimeout;
    }

    public void setBatchTimeout(long batchTimeout) {
        this.batchTimeout = batchTimeout;
    }

    public int getQueueCapacity() {
        return queueCapacity;
    }

    public void setQueueCapacity(int queueCapacity) {
        this.queueCapacity = queueCapacity;
    }

    public int getCorePoolSize() {
        return corePoolSize;
    }

    public void setCorePoolSize(int corePoolSize) {
        this.corePoolSize = corePoolSize;
    }

    public int getMaxPoolSize() {
        return maxPoolSize;
    }

    public void setMaxPoolSize(int maxPoolSize) {
        this.maxPoolSize = maxPoolSize;
    }

    public int getKeepAliveTime() {
        return keepAliveTime;
    }

    public void setKeepAliveTime(int keepAliveTime) {
        this.keepAliveTime = keepAliveTime;
    }

    public boolean isMonitoringEnabled() {
        return monitoringEnabled;
    }

    public void setMonitoringEnabled(boolean monitoringEnabled) {
        this.monitoringEnabled = monitoringEnabled;
    }

    public int getStatisticsInterval() {
        return statisticsInterval;
    }

    public void setStatisticsInterval(int statisticsInterval) {
        this.statisticsInterval = statisticsInterval;
    }

    public boolean isDeduplicationEnabled() {
        return deduplicationEnabled;
    }

    public void setDeduplicationEnabled(boolean deduplicationEnabled) {
        this.deduplicationEnabled = deduplicationEnabled;
    }

    public int getRetryAttempts() {
        return retryAttempts;
    }

    public void setRetryAttempts(int retryAttempts) {
        this.retryAttempts = retryAttempts;
    }

    public long getRetryInterval() {
        return retryInterval;
    }

    public void setRetryInterval(long retryInterval) {
        this.retryInterval = retryInterval;
    }

    public boolean isVerboseLogging() {
        return verboseLogging;
    }

    public void setVerboseLogging(boolean verboseLogging) {
        this.verboseLogging = verboseLogging;
    }

    @Override
    public String toString() {
        return "MessageStorageConfig{" +
                "enabled=" + enabled +
                ", batchSize=" + batchSize +
                ", batchTimeout=" + batchTimeout +
                ", queueCapacity=" + queueCapacity +
                ", corePoolSize=" + corePoolSize +
                ", maxPoolSize=" + maxPoolSize +
                ", keepAliveTime=" + keepAliveTime +
                ", monitoringEnabled=" + monitoringEnabled +
                ", statisticsInterval=" + statisticsInterval +
                ", deduplicationEnabled=" + deduplicationEnabled +
                ", retryAttempts=" + retryAttempts +
                ", retryInterval=" + retryInterval +
                ", verboseLogging=" + verboseLogging +
                '}';
    }

    // messageProcessingExecutor Bean已移至AsyncConfig.java中统一管理
    // 避免重复定义导致的Bean创建异常
}