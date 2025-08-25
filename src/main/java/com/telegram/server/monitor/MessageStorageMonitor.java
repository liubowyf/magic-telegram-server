package com.telegram.server.monitor;

import com.telegram.server.config.MessageStorageConfig;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.concurrent.atomic.AtomicLong;

/**
 * 消息存储监控组件
 * 用于监控和统计Telegram消息存储的性能指标
 * 
 * @author liubo
 * @date 2025-08-19
 */
@Component
public class MessageStorageMonitor {

    private static final Logger logger = LoggerFactory.getLogger(MessageStorageMonitor.class);
    private static final DateTimeFormatter TIME_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    @Autowired
    private MessageStorageConfig config;

    // ==================== 性能统计指标 ====================

    /**
     * 总接收消息数
     */
    private final AtomicLong totalReceivedMessages = new AtomicLong(0);

    /**
     * 总保存成功消息数
     */
    private final AtomicLong totalSavedMessages = new AtomicLong(0);

    /**
     * 总重复消息数
     */
    private final AtomicLong totalDuplicateMessages = new AtomicLong(0);

    /**
     * 总保存失败消息数
     */
    private final AtomicLong totalFailedMessages = new AtomicLong(0);

    /**
     * 当前批处理队列大小
     */
    private final AtomicLong currentQueueSize = new AtomicLong(0);

    /**
     * 平均处理时间（毫秒）
     */
    private final AtomicLong totalProcessingTime = new AtomicLong(0);

    /**
     * 处理次数计数器
     */
    private final AtomicLong processingCount = new AtomicLong(0);

    /**
     * 最后统计时间
     */
    private volatile LocalDateTime lastStatisticsTime = LocalDateTime.now();

    /**
     * 上次统计时的消息数量
     */
    private volatile long lastReceivedCount = 0;
    private volatile long lastSavedCount = 0;
    private volatile long lastDuplicateCount = 0;
    private volatile long lastFailedCount = 0;

    // ==================== 监控方法 ====================

    /**
     * 记录接收到的消息
     */
    public void recordReceivedMessage() {
        totalReceivedMessages.incrementAndGet();
    }

    /**
     * 记录保存成功的消息
     */
    public void recordSavedMessage() {
        totalSavedMessages.incrementAndGet();
    }

    /**
     * 记录重复的消息
     */
    public void recordDuplicateMessage() {
        totalDuplicateMessages.incrementAndGet();
    }

    /**
     * 记录保存失败的消息
     */
    public void recordFailedMessage() {
        totalFailedMessages.incrementAndGet();
    }

    /**
     * 记录处理时间
     * 
     * @param processingTimeMs 处理时间（毫秒）
     */
    public void recordProcessingTime(long processingTimeMs) {
        totalProcessingTime.addAndGet(processingTimeMs);
        processingCount.incrementAndGet();
    }

    /**
     * 更新队列大小
     * 
     * @param queueSize 当前队列大小
     */
    public void updateQueueSize(long queueSize) {
        currentQueueSize.set(queueSize);
    }

    /**
     * 获取平均处理时间
     * 
     * @return 平均处理时间（毫秒）
     */
    public double getAverageProcessingTime() {
        long count = processingCount.get();
        if (count == 0) {
            return 0.0;
        }
        return (double) totalProcessingTime.get() / count;
    }

    /**
     * 获取成功率
     * 
     * @return 成功率（百分比）
     */
    public double getSuccessRate() {
        long total = totalReceivedMessages.get();
        if (total == 0) {
            return 100.0;
        }
        long successful = totalSavedMessages.get() + totalDuplicateMessages.get();
        return (double) successful / total * 100.0;
    }

    /**
     * 获取重复率
     * 
     * @return 重复率（百分比）
     */
    public double getDuplicateRate() {
        long total = totalReceivedMessages.get();
        if (total == 0) {
            return 0.0;
        }
        return (double) totalDuplicateMessages.get() / total * 100.0;
    }

    // ==================== 定时统计输出 ====================

    /**
     * 定时输出性能统计信息
     * 根据配置的间隔时间执行
     */
    @Scheduled(fixedDelayString = "#{messageStorageConfig.statisticsInterval * 1000}")
    public void outputStatistics() {
        if (!config.isMonitoringEnabled()) {
            return;
        }

        LocalDateTime now = LocalDateTime.now();
        long currentReceived = totalReceivedMessages.get();
        long currentSaved = totalSavedMessages.get();
        long currentDuplicate = totalDuplicateMessages.get();
        long currentFailed = totalFailedMessages.get();

        // 计算增量
        long receivedDelta = currentReceived - lastReceivedCount;
        long savedDelta = currentSaved - lastSavedCount;
        long duplicateDelta = currentDuplicate - lastDuplicateCount;
        long failedDelta = currentFailed - lastFailedCount;

        // 计算速率（每分钟）
        long intervalSeconds = java.time.Duration.between(lastStatisticsTime, now).getSeconds();
        if (intervalSeconds == 0) intervalSeconds = 1; // 避免除零

        double receivedRate = (double) receivedDelta / intervalSeconds * 60;
        double savedRate = (double) savedDelta / intervalSeconds * 60;

        // 输出统计信息
        logger.info("=== Telegram消息存储性能统计 [{}] ===", now.format(TIME_FORMATTER));
        logger.info("总计: 接收={}, 保存={}, 重复={}, 失败={}", 
                currentReceived, currentSaved, currentDuplicate, currentFailed);
        logger.info("增量: 接收={}, 保存={}, 重复={}, 失败={}", 
                receivedDelta, savedDelta, duplicateDelta, failedDelta);
        logger.info("速率: 接收={:.1f}/分钟, 保存={:.1f}/分钟", receivedRate, savedRate);
        logger.info("指标: 成功率={:.2f}%, 重复率={:.2f}%, 平均处理时间={:.2f}ms", 
                getSuccessRate(), getDuplicateRate(), getAverageProcessingTime());
        logger.info("队列: 当前大小={}, 最大容量={}", currentQueueSize.get(), config.getQueueCapacity());
        
        // 队列容量警告
        if (currentQueueSize.get() > config.getQueueCapacity() * 0.8) {
            logger.warn("警告: 消息队列使用率超过80%，当前={}/{}", 
                    currentQueueSize.get(), config.getQueueCapacity());
        }

        // 失败率警告
        if (currentFailed > 0 && getSuccessRate() < 95.0) {
            logger.warn("警告: 消息保存成功率低于95%，当前成功率={:.2f}%", getSuccessRate());
        }

        logger.info("================================================");

        // 更新上次统计数据
        lastStatisticsTime = now;
        lastReceivedCount = currentReceived;
        lastSavedCount = currentSaved;
        lastDuplicateCount = currentDuplicate;
        lastFailedCount = currentFailed;
    }

    /**
     * 获取当前统计摘要
     * 
     * @return 统计摘要字符串
     */
    public String getStatisticsSummary() {
        return String.format(
                "消息统计: 接收=%d, 保存=%d, 重复=%d, 失败=%d, 成功率=%.2f%%, 队列=%d/%d",
                totalReceivedMessages.get(),
                totalSavedMessages.get(),
                totalDuplicateMessages.get(),
                totalFailedMessages.get(),
                getSuccessRate(),
                currentQueueSize.get(),
                config.getQueueCapacity()
        );
    }

    /**
     * 重置所有统计数据
     */
    public void resetStatistics() {
        totalReceivedMessages.set(0);
        totalSavedMessages.set(0);
        totalDuplicateMessages.set(0);
        totalFailedMessages.set(0);
        currentQueueSize.set(0);
        totalProcessingTime.set(0);
        processingCount.set(0);
        lastStatisticsTime = LocalDateTime.now();
        lastReceivedCount = 0;
        lastSavedCount = 0;
        lastDuplicateCount = 0;
        lastFailedCount = 0;
        
        logger.info("消息存储统计数据已重置");
    }

    // ==================== Getter方法 ====================

    public long getTotalReceivedMessages() {
        return totalReceivedMessages.get();
    }

    public long getTotalSavedMessages() {
        return totalSavedMessages.get();
    }

    public long getTotalDuplicateMessages() {
        return totalDuplicateMessages.get();
    }

    public long getTotalFailedMessages() {
        return totalFailedMessages.get();
    }

    public long getCurrentQueueSize() {
        return currentQueueSize.get();
    }
}