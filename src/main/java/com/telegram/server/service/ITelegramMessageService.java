package com.telegram.server.service;

import com.telegram.server.entity.TelegramMessage;
import com.telegram.server.repository.TelegramMessageRepository;
import org.springframework.data.domain.Page;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;

/**
 * Telegram消息服务接口
 * 
 * 定义Telegram消息的业务逻辑处理接口，包括消息存储、查询、统计等功能。
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
 * @author liubo
 * @date 2025-01-19
 */
public interface ITelegramMessageService {

    // ==================== 消息存储方法 ====================
    
    /**
     * 异步更新图片数据
     * 
     * @param accountPhone 账号手机号
     * @param chatId 聊天ID
     * @param messageId 消息ID
     * @param imageData 图片数据
     * @param imageFilename 图片文件名
     * @param imageMimeType 图片MIME类型
     * @param imageStatus 图片状态
     * @return 异步处理结果
     */
    CompletableFuture<Boolean> updateImageDataAsync(String accountPhone, Long chatId, Long messageId,
                                                   String imageData, String imageFilename, 
                                                   String imageMimeType, String imageStatus);

    /**
     * 异步保存单个消息
     * 这是核心方法，用于处理从Telegram接收到的消息
     * 
     * @param message 消息对象
     * @return 异步处理结果
     */
    CompletableFuture<Boolean> saveMessageAsync(TelegramMessage message);

    /**
     * 同步保存单个消息
     * 用于需要立即确认保存结果的场景
     * 
     * @param message 消息对象
     * @return 是否保存成功
     */
    boolean saveMessage(TelegramMessage message);

    /**
     * 批量异步保存消息
     * 用于处理大量消息的场景，提高处理效率
     * 
     * @param messages 消息列表
     * @return 异步处理结果
     */
    CompletableFuture<Integer> saveMessagesAsync(List<TelegramMessage> messages);

    // ==================== 消息查询方法 ====================
    
    /**
     * 检查消息是否存在
     * 
     * @param accountPhone 账号手机号
     * @param chatId 聊天ID
     * @param messageId 消息ID
     * @return 是否存在
     */
    boolean isMessageExists(String accountPhone, Long chatId, Long messageId);

    /**
     * 根据ID查找消息
     * 
     * @param messageId 消息复合ID
     * @return 消息对象（可能为空）
     */
    Optional<TelegramMessage> findById(String messageId);

    /**
     * 根据账号、聊天ID和消息ID查找消息
     * 
     * @param accountPhone 账号手机号
     * @param chatId 聊天ID
     * @param messageId 消息ID
     * @return 消息对象（可能为空）
     */
    Optional<TelegramMessage> findMessage(String accountPhone, Long chatId, Long messageId);

    /**
     * 查询指定账号的消息
     * 
     * @param accountPhone 账号手机号
     * @param page 页码（从0开始）
     * @param size 每页大小
     * @return 消息分页结果
     */
    Page<TelegramMessage> findMessagesByAccount(String accountPhone, int page, int size);

    /**
     * 查询指定账号在指定群组的消息
     * 
     * @param accountPhone 账号手机号
     * @param chatId 聊天ID
     * @param page 页码（从0开始）
     * @param size 每页大小
     * @return 消息分页结果
     */
    Page<TelegramMessage> findMessagesByAccountAndChat(String accountPhone, Long chatId, int page, int size);

    /**
     * 查询指定账号的最新消息
     * 
     * @param accountPhone 账号手机号
     * @param limit 限制数量
     * @return 最新消息列表
     */
    Page<TelegramMessage> findLatestMessages(String accountPhone, int limit);

    /**
     * 查询指定账号在指定群组的最新消息
     * 
     * @param accountPhone 账号手机号
     * @param chatId 聊天ID
     * @param limit 限制数量
     * @return 最新消息列表
     */
    Page<TelegramMessage> findLatestMessagesInChat(String accountPhone, Long chatId, int limit);

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
    Page<TelegramMessage> findMessagesByTimeRange(String accountPhone, LocalDateTime startTime, 
            LocalDateTime endTime, int page, int size);

    /**
     * 按消息类型查询消息
     * 
     * @param accountPhone 账号手机号
     * @param messageType 消息类型
     * @param page 页码
     * @param size 每页大小
     * @return 消息分页结果
     */
    Page<TelegramMessage> findMessagesByType(String accountPhone, String messageType, int page, int size);

    /**
     * 搜索包含指定文本的消息
     * 
     * @param accountPhone 账号手机号
     * @param searchText 搜索文本
     * @param page 页码
     * @param size 每页大小
     * @return 消息分页结果
     */
    Page<TelegramMessage> searchMessages(String accountPhone, String searchText, int page, int size);

    // ==================== 统计查询方法 ====================
    
    /**
     * 统计指定账号的消息总数
     * 
     * @param accountPhone 账号手机号
     * @return 消息总数
     */
    long countMessagesByAccount(String accountPhone);

    /**
     * 统计指定账号在指定群组的消息总数
     * 
     * @param accountPhone 账号手机号
     * @param chatId 聊天ID
     * @return 消息总数
     */
    long countMessagesByAccountAndChat(String accountPhone, Long chatId);

    /**
     * 统计指定账号在指定时间范围内的消息数量
     * 
     * @param accountPhone 账号手机号
     * @param startTime 开始时间
     * @param endTime 结束时间
     * @return 消息数量
     */
    long countMessagesByTimeRange(String accountPhone, LocalDateTime startTime, LocalDateTime endTime);

    /**
     * 获取指定账号的群组消息统计
     * 
     * @param accountPhone 账号手机号
     * @return 群组消息统计列表
     */
    List<TelegramMessageRepository.ChatMessageStats> getChatMessageStats(String accountPhone);

    /**
     * 获取指定账号的消息类型统计
     * 
     * @param accountPhone 账号手机号
     * @return 消息类型统计列表
     */
    List<TelegramMessageRepository.MessageTypeStats> getMessageTypeStats(String accountPhone);

    /**
     * 获取指定账号的活跃群组列表
     * 
     * @param accountPhone 账号手机号
     * @return 活跃群组列表
     */
    List<TelegramMessageRepository.ActiveChatInfo> getActiveChats(String accountPhone);

    // ==================== 数据管理方法 ====================
    
    /**
     * 删除指定账号的所有消息
     * 谨慎使用，建议先备份
     * 
     * @param accountPhone 账号手机号
     * @return 删除的消息数量
     */
    long deleteMessagesByAccount(String accountPhone);

    /**
     * 删除指定账号在指定群组的所有消息
     * 
     * @param accountPhone 账号手机号
     * @param chatId 聊天ID
     * @return 删除的消息数量
     */
    long deleteMessagesByAccountAndChat(String accountPhone, Long chatId);

    /**
     * 清理指定时间之前的消息
     * 用于定期数据清理
     * 
     * @param beforeTime 时间点
     * @return 删除的消息数量
     */
    long cleanupOldMessages(LocalDateTime beforeTime);

    // ==================== 性能监控方法 ====================
    
    /**
     * 获取处理性能统计
     * 
     * @return 性能统计信息
     */
    ProcessingStats getProcessingStats();

    /**
     * 消息处理统计信息
     */
    class ProcessingStats {
        private long totalProcessed;
        private long successCount;
        private long errorCount;
        private double averageProcessingTime;
        
        // 构造函数、getter和setter方法
        public ProcessingStats() {}
        
        public ProcessingStats(long totalProcessed, long successCount, long errorCount, double averageProcessingTime) {
            this.totalProcessed = totalProcessed;
            this.successCount = successCount;
            this.errorCount = errorCount;
            this.averageProcessingTime = averageProcessingTime;
        }
        
        public long getTotalProcessed() { return totalProcessed; }
        public void setTotalProcessed(long totalProcessed) { this.totalProcessed = totalProcessed; }
        
        public long getSuccessCount() { return successCount; }
        public void setSuccessCount(long successCount) { this.successCount = successCount; }
        
        public long getErrorCount() { return errorCount; }
        public void setErrorCount(long errorCount) { this.errorCount = errorCount; }
        
        public double getAverageProcessingTime() { return averageProcessingTime; }
        public void setAverageProcessingTime(double averageProcessingTime) { this.averageProcessingTime = averageProcessingTime; }
    }

    /**
     * 重置统计信息
     */
    void resetStats();

    // ==================== Web管理系统API方法 ====================
    
    /**
     * 分页获取消息列表
     * 
     * @param pageRequest 分页请求参数
     * @return 分页响应结果
     */
    com.telegram.server.dto.PageResponseDTO<com.telegram.server.dto.MessageDTO> getMessagesPage(com.telegram.server.dto.PageRequestDTO pageRequest);
    
    /**
     * 根据ID获取消息详情
     * 
     * @param messageId 消息ID
     * @return 消息详情
     */
    Optional<com.telegram.server.dto.MessageDTO> getMessageById(String messageId);
    
    /**
     * 获取消息图片数据
     * 
     * @param messageId 消息ID
     * @return 图片数据（Base64编码）
     */
    Optional<String> getMessageImage(String messageId);
    
    /**
     * 获取消息总数
     * 
     * @return 消息总数
     */
    long getMessageCount();
}