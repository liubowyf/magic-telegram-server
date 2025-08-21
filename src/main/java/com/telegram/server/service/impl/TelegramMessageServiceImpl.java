package com.telegram.server.service.impl;

import com.telegram.server.entity.TelegramMessage;
import com.telegram.server.service.ITelegramMessageService;
import com.telegram.server.repository.TelegramMessageRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import com.telegram.server.lifecycle.ApplicationLifecycleManager;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import com.telegram.server.monitor.MessageStorageMonitor;
import com.telegram.server.config.MessageStorageConfig;
import com.telegram.server.dto.MessageDTO;
import com.telegram.server.dto.PageRequestDTO;
import com.telegram.server.dto.PageResponseDTO;
import java.util.stream.Collectors;
import java.util.ArrayList;
import java.util.Base64;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Telegram消息服务类
 * 
 * 提供Telegram消息的业务逻辑处理，包括消息存储、查询、统计等功能。
 * 针对大量群组消息的高效处理进行了优化，支持异步处理和批量操作。
 * 
 * 主要功能：
 * - 异步消息存储：确保消息入库的及时性
 * - 消息去重：避免重复存储相同消息
 * - 分组查询：按账号和群组分组查询消息
 * - 统计分析：提供消息统计和分析功能
 * - 批量处理：支持批量消息处理
 * - 性能监控：记录处理性能指标
 * 
 * 性能优化：
 * - 异步处理提高响应速度
 * - 批量插入减少数据库压力
 * - 智能去重避免重复处理
 * - 分页查询控制内存使用
 * 
 * @author liubo
 * @date 2025-01-19
 */
@Service
@Transactional
public class TelegramMessageServiceImpl implements ITelegramMessageService {

    private static final Logger logger = LoggerFactory.getLogger(TelegramMessageServiceImpl.class);

    @Autowired
    private TelegramMessageRepository messageRepository;

    @Autowired
    private MessageStorageMonitor monitor;

    @Autowired
    private MessageStorageConfig config;

    @Autowired
    private ApplicationLifecycleManager lifecycleManager;

    /**
     * 服务实例ID，用于集群环境下的消息追踪
     */
    @Value("${spring.application.name:telegram-server}")
    private String instanceId;

    /**
     * 批量处理大小
     */
    @Value("${telegram.message.batch-size:100}")
    private int batchSize;

    /**
     * 异步处理线程池名称
     */
    private static final String ASYNC_EXECUTOR = "messageProcessorExecutor";

    // ==================== 性能监控指标 ====================
    
    /**
     * 消息处理计数器
     */
    private final AtomicLong processedMessageCount = new AtomicLong(0);
    
    /**
     * 消息存储计数器
     */
    private final AtomicLong savedMessageCount = new AtomicLong(0);
    
    /**
     * 重复消息计数器
     */
    private final AtomicLong duplicateMessageCount = new AtomicLong(0);

    // ==================== 核心消息处理方法 ====================
    
    /**
     * 异步更新消息的图片数据
     * 
     * 用于在图片下载和处理完成后更新消息实体的图片相关字段。
     * 支持Base64编码数据存储和文件路径存储两种模式。
     * 
     * @param accountPhone 账号手机号
     * @param chatId 聊天ID
     * @param messageId 消息ID
     * @param imageData Base64编码的图片数据（可为null）
     * @param imageFilename 图片文件名
     * @param imageMimeType 图片MIME类型
     * @param imageStatus 图片处理状态
     * @return 更新结果的CompletableFuture
     * @author liubo
     * @since 2025.01.19
     */
    @Async("messageProcessingExecutor")
    public CompletableFuture<Boolean> updateImageDataAsync(String accountPhone, Long chatId, Long messageId,
                                                           String imageData, String imageFilename, 
                                                           String imageMimeType, String imageStatus) {
        try {
            // 检查Spring容器状态
            if (!isApplicationContextActive()) {
                logger.warn("Spring容器未就绪或正在关闭，跳过异步图片数据更新");
                return CompletableFuture.completedFuture(false);
            }
        } catch (Exception e) {
            logger.error("检查图片数据更新前置条件时发生异常: {}", e.getMessage());
            return CompletableFuture.completedFuture(false);
        }

        try {
            // 查找消息
            Optional<TelegramMessage> messageOpt = findMessage(accountPhone, chatId, messageId);
            if (!messageOpt.isPresent()) {
                logger.warn("未找到消息，无法更新图片数据: accountPhone={}, chatId={}, messageId={}", 
                           accountPhone, chatId, messageId);
                return CompletableFuture.completedFuture(false);
            }
            
            TelegramMessage message = messageOpt.get();
            
            // 更新图片相关字段
            message.setImageData(imageData);
            message.setImageFilename(imageFilename);
            message.setImageMimeType(imageMimeType);
            message.setImageStatus(imageStatus);
            message.setImageProcessedTime(LocalDateTime.now());
            
            // 保存更新
            messageRepository.save(message);
            
            logger.info("图片数据更新成功: accountPhone={}, chatId={}, messageId={}, filename={}, mimeType={}, status={}",
                       accountPhone, chatId, messageId, imageFilename, imageMimeType, imageStatus);
            
            return CompletableFuture.completedFuture(true);
            
        } catch (Exception e) {
            logger.error("更新图片数据失败: accountPhone={}, chatId={}, messageId={}, error={}", 
                        accountPhone, chatId, messageId, e.getMessage(), e);
            return CompletableFuture.completedFuture(false);
        }
    }
    
    /**
     * 检查Spring容器是否处于活跃状态
     * 防止在容器销毁阶段执行异步操作导致BeanCreationNotAllowedException
     * 
     * @return 容器是否活跃
     */
    private boolean isApplicationContextActive() {
        try {
            return lifecycleManager != null && lifecycleManager.isApplicationContextActive();
        } catch (Exception e) {
            logger.warn("检查Spring容器状态时发生异常: {}", e.getMessage());
            return false;
        }
    }

    /**
     * 异步保存单个消息
     * 这是核心方法，用于处理从Telegram接收到的消息
     * 
     * @param message 消息对象
     * @return 异步处理结果
     */
    @Async("messageProcessingExecutor")
    public CompletableFuture<Boolean> saveMessageAsync(TelegramMessage message) {
        try {
            // 检查Spring容器状态
            if (!isApplicationContextActive()) {
                logger.warn("Spring容器未就绪或正在关闭，跳过异步消息保存");
                return CompletableFuture.completedFuture(false);
            }

            if (message == null) {
                logger.warn("尝试保存空消息，跳过处理");
                return CompletableFuture.completedFuture(false);
            }

            // 检查是否启用消息存储
            if (!config.isEnabled()) {
                logger.debug("消息存储功能已禁用，跳过保存");
                return CompletableFuture.completedFuture(false);
            }
        } catch (Exception e) {
            logger.error("检查消息保存前置条件时发生异常: {}", e.getMessage());
            return CompletableFuture.completedFuture(false);
        }

        long startTime = System.currentTimeMillis();
        monitor.recordReceivedMessage();

        try {
            // 检查消息是否已存在（去重）
            if (isDuplicateMessage(message)) {
                return CompletableFuture.completedFuture(false);
            }
            
            // 准备并保存消息
            TelegramMessage savedMessage = prepareSaveMessage(message);
            
            // 更新统计和记录日志
            updateSaveStatistics();
            logSaveSuccess(savedMessage, startTime);
            
            return CompletableFuture.completedFuture(true);
            
        } catch (Exception e) {
            logSaveFailure(message, e);
            monitor.recordFailedMessage();
            return CompletableFuture.completedFuture(false);
        } finally {
            recordProcessingTime(startTime);
        }
    }
    
    /**
     * 检查消息是否为重复消息
     * 
     * @param message 待检查的消息
     * @return 如果是重复消息返回true
     */
    private boolean isDuplicateMessage(TelegramMessage message) {
        if (config.isDeduplicationEnabled() && isMessageExists(message.getAccountPhone(), message.getChatId(), message.getMessageId())) {
            if (config.isVerboseLogging()) {
                logger.debug("消息已存在，跳过保存: accountPhone={}, chatId={}, messageId={}", 
                    message.getAccountPhone(), message.getChatId(), message.getMessageId());
            }
            duplicateMessageCount.incrementAndGet();
            monitor.recordDuplicateMessage();
            return true;
        }
        return false;
    }
    
    /**
     * 准备并保存消息
     * 
     * @param message 待保存的消息
     * @return 保存后的消息
     */
    private TelegramMessage prepareSaveMessage(TelegramMessage message) {
        // 设置实例ID和创建时间
        message.setInstanceId(instanceId);
        message.updateCreatedTime();
        
        // 保存消息
        return messageRepository.save(message);
    }
    
    /**
     * 更新保存统计信息
     */
    private void updateSaveStatistics() {
        processedMessageCount.incrementAndGet();
        savedMessageCount.incrementAndGet();
        monitor.recordSavedMessage();
    }
    
    /**
     * 记录保存成功日志
     * 
     * @param savedMessage 保存成功的消息
     * @param startTime 开始时间
     */
    private void logSaveSuccess(TelegramMessage savedMessage, long startTime) {
        if (config.isVerboseLogging()) {
            long endTime = System.currentTimeMillis();
            logger.info("消息保存成功: accountPhone={}, chatId={}, messageId={}, chatTitle={}, messageType={}, 耗时={}ms", 
                savedMessage.getAccountPhone(), savedMessage.getChatId(), savedMessage.getMessageId(),
                savedMessage.getChatTitle(), savedMessage.getMessageType(), (endTime - startTime));
        }
    }
    
    /**
     * 记录保存失败日志
     * 
     * @param message 保存失败的消息
     * @param e 异常信息
     */
    private void logSaveFailure(TelegramMessage message, Exception e) {
        logger.error("消息保存失败: accountPhone={}, chatId={}, messageId={}, error={}", 
            message.getAccountPhone(), message.getChatId(), message.getMessageId(), e.getMessage(), e);
    }

    /**
     * 同步保存单个消息
     * 用于需要立即确认保存结果的场景
     * 
     * @param message 消息对象
     * @return 是否保存成功
     */
    public boolean saveMessage(TelegramMessage message) {
        try {
            // 设置实例ID和创建时间
            message.setInstanceId(instanceId);
            message.updateCreatedTime();
            
            // 检查消息是否已存在（去重）
            if (isMessageExists(message.getAccountPhone(), message.getChatId(), message.getMessageId())) {
                duplicateMessageCount.incrementAndGet();
                logger.debug("消息已存在，跳过保存: accountPhone={}, chatId={}, messageId={}", 
                    message.getAccountPhone(), message.getChatId(), message.getMessageId());
                return false;
            }
            
            // 保存消息
            messageRepository.save(message);
            
            // 更新统计计数器
            processedMessageCount.incrementAndGet();
            savedMessageCount.incrementAndGet();
            
            logger.info("消息保存成功: accountPhone={}, chatId={}, messageId={}, chatTitle={}", 
                message.getAccountPhone(), message.getChatId(), message.getMessageId(), message.getChatTitle());
            
            return true;
            
        } catch (Exception e) {
            logger.error("消息保存失败: accountPhone={}, chatId={}, messageId={}, error={}", 
                message.getAccountPhone(), message.getChatId(), message.getMessageId(), e.getMessage(), e);
            return false;
        }
    }

    /**
     * 批量异步保存消息
     * 用于处理大量消息的场景，提高处理效率
     * 
     * @param messages 消息列表
     * @return 异步处理结果
     */
    @Async(ASYNC_EXECUTOR)
    public CompletableFuture<Integer> saveMessagesAsync(List<TelegramMessage> messages) {
        try {
            // 检查Spring容器状态
            if (!isApplicationContextActive()) {
                logger.warn("Spring容器未就绪或正在关闭，跳过批量异步消息保存");
                return CompletableFuture.completedFuture(0);
            }

            if (messages == null || messages.isEmpty()) {
                return CompletableFuture.completedFuture(0);
            }
            
            // 检查是否启用消息存储
            if (!config.isEnabled()) {
                logger.debug("消息存储功能已禁用，跳过批量保存");
                return CompletableFuture.completedFuture(0);
            }
        } catch (Exception e) {
            logger.error("检查批量消息保存前置条件时发生异常: {}", e.getMessage());
            return CompletableFuture.completedFuture(0);
        }
        
        long startTime = System.currentTimeMillis();
        recordReceivedMessages(messages.size());
        
        try {
            BatchSaveResult result = processBatchSave(messages);
            updateStatistics(messages.size(), result.savedCount, result.duplicateCount);
            logBatchSaveResult(messages.size(), result.savedCount, result.duplicateCount, startTime);
            
            return CompletableFuture.completedFuture(result.savedCount);
            
        } catch (Exception e) {
            logger.error("批量消息保存失败: 消息数量={}, error={}", messages.size(), e.getMessage(), e);
            recordFailedMessages(messages.size());
            return CompletableFuture.completedFuture(0);
        } finally {
            recordProcessingTime(startTime);
        }
    }
    
    /**
     * 记录接收到的消息数量
     * 
     * @param messageCount 消息数量
     */
    private void recordReceivedMessages(int messageCount) {
        for (int i = 0; i < messageCount; i++) {
            monitor.recordReceivedMessage();
        }
    }
    
    /**
     * 处理批量保存逻辑
     * 
     * @param messages 待保存的消息列表
     * @return 批量保存结果
     */
    private BatchSaveResult processBatchSave(List<TelegramMessage> messages) {
        final AtomicLong duplicateCountLocal = new AtomicLong(0);
        
        // 设置实例ID和创建时间
        prepareMessagesForSave(messages);
        
        // 过滤重复消息（如果启用去重）
        List<TelegramMessage> uniqueMessages = filterDuplicateMessages(messages, duplicateCountLocal);
        
        // 批量保存
        int savedCount = saveUniqueMessages(uniqueMessages);
        
        // 记录重复消息数量
        long duplicateCount = duplicateCountLocal.get();
        recordDuplicateMessages((int) duplicateCount);
        
        return new BatchSaveResult(savedCount, (int) duplicateCount);
    }
    
    /**
     * 为消息设置实例ID和创建时间
     * 
     * @param messages 消息列表
     */
    private void prepareMessagesForSave(List<TelegramMessage> messages) {
        for (TelegramMessage message : messages) {
            message.setInstanceId(instanceId);
            message.updateCreatedTime();
        }
    }
    
    /**
     * 过滤重复消息
     * 
     * @param messages 原始消息列表
     * @param duplicateCountLocal 重复消息计数器
     * @return 去重后的消息列表
     */
    private List<TelegramMessage> filterDuplicateMessages(List<TelegramMessage> messages, AtomicLong duplicateCountLocal) {
        if (config.isDeduplicationEnabled()) {
            return messages.stream()
                .filter(message -> {
                    if (isMessageExists(message.getAccountPhone(), message.getChatId(), message.getMessageId())) {
                        duplicateCountLocal.incrementAndGet();
                        return false;
                    }
                    return true;
                })
                .toList();
        } else {
            return messages;
        }
    }
    
    /**
     * 保存唯一消息
     * 
     * @param uniqueMessages 去重后的消息列表
     * @return 保存成功的消息数量
     */
    private int saveUniqueMessages(List<TelegramMessage> uniqueMessages) {
        if (!uniqueMessages.isEmpty()) {
            List<TelegramMessage> savedMessages = messageRepository.saveAll(uniqueMessages);
            int savedCount = savedMessages.size();
            
            // 记录保存成功的消息数量
            for (int i = 0; i < savedCount; i++) {
                monitor.recordSavedMessage();
            }
            
            return savedCount;
        }
        return 0;
    }
    
    /**
     * 记录重复消息数量
     * 
     * @param duplicateCount 重复消息数量
     */
    private void recordDuplicateMessages(int duplicateCount) {
        for (int i = 0; i < duplicateCount; i++) {
            monitor.recordDuplicateMessage();
        }
    }
    
    /**
     * 记录失败消息数量
     * 
     * @param messageCount 失败消息数量
     */
    private void recordFailedMessages(int messageCount) {
        for (int i = 0; i < messageCount; i++) {
            monitor.recordFailedMessage();
        }
    }
    
    /**
     * 更新统计计数器
     * 
     * @param totalCount 总消息数量
     * @param savedCount 保存成功数量
     * @param duplicateCount 重复消息数量
     */
    private void updateStatistics(int totalCount, int savedCount, int duplicateCount) {
        processedMessageCount.addAndGet(totalCount);
        savedMessageCount.addAndGet(savedCount);
        duplicateMessageCount.addAndGet(duplicateCount);
    }
    
    /**
     * 记录批量保存结果日志
     * 
     * @param totalCount 总消息数量
     * @param savedCount 保存成功数量
     * @param duplicateCount 重复消息数量
     * @param startTime 开始时间
     */
    private void logBatchSaveResult(int totalCount, int savedCount, int duplicateCount, long startTime) {
        if (config.isVerboseLogging()) {
            long endTime = System.currentTimeMillis();
            logger.info("批量消息保存完成: 总数={}, 保存={}, 重复={}, 耗时={}ms", 
                totalCount, savedCount, duplicateCount, (endTime - startTime));
        }
    }
    
    /**
     * 记录处理时间
     * 
     * @param startTime 开始时间
     */
    private void recordProcessingTime(long startTime) {
        long processingTime = System.currentTimeMillis() - startTime;
        monitor.recordProcessingTime(processingTime);
    }
    
    /**
     * 批量保存结果内部类
     */
    private static class BatchSaveResult {
        final int savedCount;
        final int duplicateCount;
        
        BatchSaveResult(int savedCount, int duplicateCount) {
            this.savedCount = savedCount;
            this.duplicateCount = duplicateCount;
        }
    }

    // ==================== 消息查询方法 ====================
    
    /**
     * 检查消息是否已存在
     * 
     * @param accountPhone 账号手机号
     * @param chatId 聊天ID
     * @param messageId 消息ID
     * @return 是否存在
     */
    public boolean isMessageExists(String accountPhone, Long chatId, Long messageId) {
        return messageRepository.existsByAccountPhoneAndChatIdAndMessageId(accountPhone, chatId, messageId);
    }

    /**
     * 根据ID查找消息
     * 
     * @param messageId 消息复合ID
     * @return 消息对象（可能为空）
     */
    public Optional<TelegramMessage> findById(String messageId) {
        return messageRepository.findById(messageId);
    }

    /**
     * 根据账号、聊天ID和消息ID查找消息
     * 
     * @param accountPhone 账号手机号
     * @param chatId 聊天ID
     * @param messageId 消息ID
     * @return 消息对象（可能为空）
     */
    public Optional<TelegramMessage> findMessage(String accountPhone, Long chatId, Long messageId) {
        return messageRepository.findByAccountPhoneAndChatIdAndMessageId(accountPhone, chatId, messageId);
    }

    /**
     * 查询指定账号的消息
     * 
     * @param accountPhone 账号手机号
     * @param page 页码（从0开始）
     * @param size 每页大小
     * @return 消息分页结果
     */
    public Page<TelegramMessage> findMessagesByAccount(String accountPhone, int page, int size) {
        Pageable pageable = PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "messageDate"));
        return messageRepository.findByAccountPhone(accountPhone, pageable);
    }

    /**
     * 查询指定账号在指定群组的消息
     * 
     * @param accountPhone 账号手机号
     * @param chatId 聊天ID
     * @param page 页码（从0开始）
     * @param size 每页大小
     * @return 消息分页结果
     */
    public Page<TelegramMessage> findMessagesByAccountAndChat(String accountPhone, Long chatId, int page, int size) {
        Pageable pageable = PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "messageDate"));
        return messageRepository.findByAccountPhoneAndChatId(accountPhone, chatId, pageable);
    }

    /**
     * 查询指定账号的最新消息
     * 
     * @param accountPhone 账号手机号
     * @param limit 限制数量
     * @return 最新消息列表
     */
    public Page<TelegramMessage> findLatestMessages(String accountPhone, int limit) {
        Pageable pageable = PageRequest.of(0, limit, Sort.by(Sort.Direction.DESC, "messageDate"));
        return messageRepository.findLatestMessagesByAccount(accountPhone, pageable);
    }

    /**
     * 查询指定账号在指定群组的最新消息
     * 
     * @param accountPhone 账号手机号
     * @param chatId 聊天ID
     * @param limit 限制数量
     * @return 最新消息列表
     */
    public Page<TelegramMessage> findLatestMessagesInChat(String accountPhone, Long chatId, int limit) {
        Pageable pageable = PageRequest.of(0, limit, Sort.by(Sort.Direction.DESC, "messageDate"));
        return messageRepository.findLatestMessagesByAccountAndChat(accountPhone, chatId, pageable);
    }

    /**
     * 按时间范围查询消息
     * 
     * @param accountPhone 账号手机号
     * @param startTime 开始时间
     * @param endTime 结束时间
     * @param page 页码
     * @param size 每页大小
     * @return 消息分页结果
     */
    public Page<TelegramMessage> findMessagesByTimeRange(String accountPhone, LocalDateTime startTime, 
            LocalDateTime endTime, int page, int size) {
        Pageable pageable = PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "messageDate"));
        return messageRepository.findByAccountPhoneAndMessageDateBetween(accountPhone, startTime, endTime, pageable);
    }

    /**
     * 按消息类型查询消息
     * 
     * @param accountPhone 账号手机号
     * @param messageType 消息类型
     * @param page 页码
     * @param size 每页大小
     * @return 消息分页结果
     */
    public Page<TelegramMessage> findMessagesByType(String accountPhone, String messageType, int page, int size) {
        Pageable pageable = PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "messageDate"));
        return messageRepository.findByAccountPhoneAndMessageType(accountPhone, messageType, pageable);
    }

    /**
     * 搜索包含指定文本的消息
     * 
     * @param accountPhone 账号手机号
     * @param searchText 搜索文本
     * @param page 页码
     * @param size 每页大小
     * @return 消息分页结果
     */
    public Page<TelegramMessage> searchMessages(String accountPhone, String searchText, int page, int size) {
        Pageable pageable = PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "messageDate"));
        return messageRepository.searchMessagesByText(accountPhone, searchText, pageable);
    }

    // ==================== 统计查询方法 ====================
    
    /**
     * 统计指定账号的消息总数
     * 
     * @param accountPhone 账号手机号
     * @return 消息总数
     */
    public long countMessagesByAccount(String accountPhone) {
        return messageRepository.countByAccountPhone(accountPhone);
    }

    /**
     * 统计指定账号在指定群组的消息总数
     * 
     * @param accountPhone 账号手机号
     * @param chatId 聊天ID
     * @return 消息总数
     */
    public long countMessagesByAccountAndChat(String accountPhone, Long chatId) {
        return messageRepository.countByAccountPhoneAndChatId(accountPhone, chatId);
    }

    /**
     * 统计指定账号在指定时间范围内的消息数量
     * 
     * @param accountPhone 账号手机号
     * @param startTime 开始时间
     * @param endTime 结束时间
     * @return 消息数量
     */
    public long countMessagesByTimeRange(String accountPhone, LocalDateTime startTime, LocalDateTime endTime) {
        return messageRepository.countByAccountPhoneAndMessageDateBetween(accountPhone, startTime, endTime);
    }

    /**
     * 获取指定账号的群组消息统计
     * 
     * @param accountPhone 账号手机号
     * @return 群组消息统计列表
     */
    public List<TelegramMessageRepository.ChatMessageStats> getChatMessageStats(String accountPhone) {
        return messageRepository.getMessageStatsByChat(accountPhone);
    }

    /**
     * 获取指定账号的消息类型统计
     * 
     * @param accountPhone 账号手机号
     * @return 消息类型统计列表
     */
    public List<TelegramMessageRepository.MessageTypeStats> getMessageTypeStats(String accountPhone) {
        return messageRepository.getMessageStatsByType(accountPhone);
    }

    /**
     * 获取指定账号的活跃群组列表
     * 
     * @param accountPhone 账号手机号
     * @return 活跃群组列表
     */
    public List<TelegramMessageRepository.ActiveChatInfo> getActiveChats(String accountPhone) {
        return messageRepository.getActiveChats(accountPhone);
    }

    // ==================== 数据管理方法 ====================
    
    /**
     * 删除指定账号的所有消息
     * 谨慎使用，建议先备份
     * 
     * @param accountPhone 账号手机号
     * @return 删除的消息数量
     */
    public long deleteMessagesByAccount(String accountPhone) {
        try {
            long deletedCount = messageRepository.deleteByAccountPhone(accountPhone);
            logger.info("删除账号消息完成: accountPhone={}, 删除数量={}", accountPhone, deletedCount);
            return deletedCount;
        } catch (Exception e) {
            logger.error("删除账号消息失败: accountPhone={}, error={}", accountPhone, e.getMessage(), e);
            return 0;
        }
    }

    /**
     * 删除指定账号在指定群组的所有消息
     * 
     * @param accountPhone 账号手机号
     * @param chatId 聊天ID
     * @return 删除的消息数量
     */
    public long deleteMessagesByAccountAndChat(String accountPhone, Long chatId) {
        try {
            long deletedCount = messageRepository.deleteByAccountPhoneAndChatId(accountPhone, chatId);
            logger.info("删除群组消息完成: accountPhone={}, chatId={}, 删除数量={}", accountPhone, chatId, deletedCount);
            return deletedCount;
        } catch (Exception e) {
            logger.error("删除群组消息失败: accountPhone={}, chatId={}, error={}", accountPhone, chatId, e.getMessage(), e);
            return 0;
        }
    }

    /**
     * 清理指定时间之前的消息
     * 用于定期数据清理
     * 
     * @param beforeTime 时间点
     * @return 删除的消息数量
     */
    public long cleanupOldMessages(LocalDateTime beforeTime) {
        try {
            long deletedCount = messageRepository.deleteByMessageDateBefore(beforeTime);
            logger.info("清理历史消息完成: beforeTime={}, 删除数量={}", 
                beforeTime.format(DateTimeFormatter.ISO_LOCAL_DATE_TIME), deletedCount);
            return deletedCount;
        } catch (Exception e) {
            logger.error("清理历史消息失败: beforeTime={}, error={}", 
                beforeTime.format(DateTimeFormatter.ISO_LOCAL_DATE_TIME), e.getMessage(), e);
            return 0;
        }
    }

    // ==================== 性能监控方法 ====================
    
    /**
     * 获取处理性能统计
     * 
     * @return 性能统计信息
     */
    public ITelegramMessageService.ProcessingStats getProcessingStats() {
        return new ITelegramMessageService.ProcessingStats(
            processedMessageCount.get(),
            savedMessageCount.get(),
            duplicateMessageCount.get(),
            0.0 // averageProcessingTime placeholder
        );
    }

    /**
     * 重置性能统计计数器
     */
    public void resetStats() {
        processedMessageCount.set(0);
        savedMessageCount.set(0);
        duplicateMessageCount.set(0);
        logger.info("性能统计计数器已重置");
    }

    // ==================== Web管理系统API方法 ====================

    /**
     * 分页获取消息列表
     * 
     * @param pageRequest 分页请求参数
     * @return 分页消息响应
     */
    @Override
    public PageResponseDTO<MessageDTO> getMessagesPage(PageRequestDTO pageRequest) {
        try {
            int page = Math.max(0, pageRequest.getPage()); // 前端传递的页码已经是从0开始
            int size = Math.max(1, Math.min(pageRequest.getSize(), 100)); // 限制每页最大100条
            
            Pageable pageable = PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "messageDate"));
            Page<TelegramMessage> messagePage = messageRepository.findAll(pageable);
            
            List<MessageDTO> messageDTOs = messagePage.getContent().stream()
                    .map(this::convertToMessageDTO)
                    .collect(Collectors.toList());
            
            return new PageResponseDTO<>(
                    messageDTOs,
                    pageRequest.getPage(),
                    size,
                    messagePage.getTotalElements()
            );
        } catch (Exception e) {
            logger.error("分页获取消息列表失败: {}", e.getMessage(), e);
            return new PageResponseDTO<>(new ArrayList<>(), pageRequest.getPage(), pageRequest.getSize(), 0L);
        }
    }

    /**
     * 根据ID获取消息详情
     * 
     * @param messageId 消息ID
     * @return 消息详情
     */
    @Override
    public Optional<MessageDTO> getMessageById(String messageId) {
        try {
            Optional<TelegramMessage> messageOpt = messageRepository.findById(messageId);
            if (messageOpt.isPresent()) {
                return Optional.of(convertToMessageDTO(messageOpt.get()));
            }
            logger.warn("未找到ID为{}的消息", messageId);
            return Optional.empty();
        } catch (Exception e) {
            logger.error("根据ID获取消息详情失败: messageId={}, error={}", messageId, e.getMessage(), e);
            return Optional.empty();
        }
    }

    /**
     * 获取消息图片数据
     * 
     * @param messageId 消息ID
     * @return Base64编码的图片数据
     */
    @Override
    public Optional<String> getMessageImage(String messageId) {
        try {
            Optional<TelegramMessage> messageOpt = messageRepository.findById(messageId);
            if (messageOpt.isPresent()) {
                TelegramMessage message = messageOpt.get();
                String imageData = message.getImageData();
                if (imageData != null && !imageData.isEmpty()) {
                    return Optional.of(imageData);
                }
            }
            logger.warn("消息{}没有图片数据", messageId);
            return Optional.empty();
        } catch (Exception e) {
            logger.error("获取消息图片数据失败: messageId={}, error={}", messageId, e.getMessage(), e);
            return Optional.empty();
        }
    }

    /**
     * 获取消息总数
     * 
     * @return 消息总数
     */
    @Override
    public long getMessageCount() {
        try {
            return messageRepository.count();
        } catch (Exception e) {
            logger.error("获取消息总数失败: {}", e.getMessage(), e);
            return 0L;
        }
    }

    /**
     * 将TelegramMessage转换为MessageDTO
     * 
     * @param message Telegram消息实体
     * @return 消息DTO
     */
    private MessageDTO convertToMessageDTO(TelegramMessage message) {
        MessageDTO dto = new MessageDTO();
        dto.setId(message.getId());
        dto.setChatId(message.getChatId());
        dto.setMessageId(message.getMessageId());
        dto.setMessageType(message.getMessageType());
        dto.setTextContent(message.getMessageText());
        dto.setSenderName(message.getSenderName());
        dto.setMessageTime(message.getMessageDate());
        dto.setCreatedAt(message.getCreatedTime());
        dto.setHasImage(message.getImageData() != null && !message.getImageData().isEmpty());
        dto.setImageFilename(message.getImageFilename());
        dto.setImageMimeType(message.getImageMimeType());
        dto.setImageStatus(message.getImageStatus());
        dto.setChatTitle(message.getChatTitle());
        dto.setSenderId(message.getSenderId());
        dto.setRawMessageData(message.getRawMessageJson());
        return dto;
    }

}