package com.telegram.server.controller;

import com.telegram.server.config.MessageStorageConfig;
import com.telegram.server.entity.TelegramMessage;
import com.telegram.server.monitor.MessageStorageMonitor;
import com.telegram.server.service.ITelegramMessageService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;

/**
 * 消息存储测试控制器
 * 用于验证消息存储功能是否正常工作
 * 
 * @author liubo
 * @date 2025-08-19
 */
@RestController
@RequestMapping("/api/message-storage")
public class MessageStorageTestController {

    @Autowired
    private MessageStorageConfig config;

    @Autowired
    private ITelegramMessageService telegramMessageService;

    @Autowired
    private MessageStorageMonitor monitor;

    /**
     * 获取消息存储配置信息
     */
    @GetMapping("/config")
    public ResponseEntity<Map<String, Object>> getConfig() {
        Map<String, Object> configInfo = new HashMap<>();
        configInfo.put("enabled", config.isEnabled());
        configInfo.put("batchSize", config.getBatchSize());
        configInfo.put("batchTimeout", config.getBatchTimeout());
        configInfo.put("queueCapacity", config.getQueueCapacity());
        configInfo.put("monitoringEnabled", config.isMonitoringEnabled());
        configInfo.put("deduplicationEnabled", config.isDeduplicationEnabled());
        return ResponseEntity.ok(configInfo);
    }

    /**
     * 获取监控统计信息
     */
    @GetMapping("/stats")
    public ResponseEntity<String> getStats() {
        return ResponseEntity.ok(monitor.getStatisticsSummary());
    }

    /**
     * 测试保存单条消息
     */
    @PostMapping("/test-single")
    public ResponseEntity<Map<String, Object>> testSingleMessage() {
        try {
            TelegramMessage message = createTestMessage(
                System.currentTimeMillis(),
                -1001234567890L,
                123456789L,
                "测试消息 - " + LocalDateTime.now()
            );

            telegramMessageService.saveMessageAsync(message);

            Map<String, Object> result = new HashMap<>();
            result.put("success", true);
            result.put("message", "单条消息保存请求已提交");
            result.put("messageId", message.getMessageId());
            result.put("timestamp", LocalDateTime.now());
            
            return ResponseEntity.ok(result);
        } catch (Exception e) {
            Map<String, Object> result = new HashMap<>();
            result.put("success", false);
            result.put("error", e.getMessage());
            return ResponseEntity.ok(result);
        }
    }

    /**
     * 获取最新消息（用于验证时区修复）
     */
    @GetMapping("/latest-messages")
    public ResponseEntity<Map<String, Object>> getLatestMessages(
            @RequestParam(defaultValue = "3") int limit,
            @RequestParam(required = false) String accountPhone) {
        try {
            // 如果没有指定账号，使用默认账号
            if (accountPhone == null || accountPhone.trim().isEmpty()) {
                accountPhone = "default"; // 可以根据实际情况调整
            }
            
            var messages = telegramMessageService.findLatestMessages(accountPhone, limit);
            
            Map<String, Object> result = new HashMap<>();
            result.put("success", true);
            result.put("accountPhone", accountPhone);
            result.put("messageCount", messages.getContent().size());
            result.put("messages", messages.getContent());
            result.put("timestamp", LocalDateTime.now());
            
            return ResponseEntity.ok(result);
        } catch (Exception e) {
            Map<String, Object> result = new HashMap<>();
            result.put("success", false);
            result.put("error", e.getMessage());
            return ResponseEntity.ok(result);
        }
    }

    /**
     * 测试保存批量消息
     */
    @PostMapping("/test-batch")
    public ResponseEntity<Map<String, Object>> testBatchMessages() {
        try {
            java.util.List<TelegramMessage> messages = new java.util.ArrayList<>();
            long baseTime = System.currentTimeMillis();
            
            for (int i = 0; i < 5; i++) {
                TelegramMessage message = createTestMessage(
                    baseTime + i,
                    -1001234567890L,
                    123456789L + i,
                    "批量测试消息 " + (i + 1) + " - " + LocalDateTime.now()
                );
                messages.add(message);
            }

            telegramMessageService.saveMessagesAsync(messages);

            Map<String, Object> result = new HashMap<>();
            result.put("success", true);
            result.put("message", "批量消息保存请求已提交");
            result.put("messageCount", messages.size());
            result.put("timestamp", LocalDateTime.now());
            
            return ResponseEntity.ok(result);
        } catch (Exception e) {
            Map<String, Object> result = new HashMap<>();
            result.put("success", false);
            result.put("error", e.getMessage());
            return ResponseEntity.ok(result);
        }
    }

    /**
     * 健康检查
     */
    @GetMapping("/health")
    public ResponseEntity<Map<String, Object>> health() {
        Map<String, Object> health = new HashMap<>();
        health.put("status", "UP");
        health.put("messageStorage", config.isEnabled() ? "ENABLED" : "DISABLED");
        health.put("monitoring", config.isMonitoringEnabled() ? "ENABLED" : "DISABLED");
        health.put("timestamp", LocalDateTime.now());
        return ResponseEntity.ok(health);
    }

    /**
     * 创建测试消息
     */
    private TelegramMessage createTestMessage(Long messageId, Long chatId, Long userId, String content) {
        TelegramMessage message = new TelegramMessage();
        message.setMessageId(messageId);
        message.setChatId(chatId);
        message.setSenderId(userId);
        message.setMessageText(content);
        message.setMessageType("text");
        message.setMessageDate(LocalDateTime.now());
        return message;
    }
}