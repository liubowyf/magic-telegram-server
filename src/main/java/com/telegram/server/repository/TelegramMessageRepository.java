package com.telegram.server.repository;

import com.telegram.server.entity.TelegramMessage;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.data.mongodb.repository.Query;
import org.springframework.data.mongodb.repository.Aggregation;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

/**
 * Telegram消息数据访问层接口
 * 
 * 提供Telegram消息的CRUD操作和复杂查询功能，支持按账号和群组分组查询。
 * 针对大量群组消息的高效存储和查询进行了优化。
 * 
 * 主要功能：
 * - 基础CRUD操作
 * - 按账号分组查询
 * - 按群组分组查询
 * - 复合条件查询
 * - 时间范围查询
 * - 消息类型过滤
 * - 分页查询支持
 * - 统计查询功能
 * 
 * 性能优化：
 * - 利用复合索引提高查询效率
 * - 支持批量操作
 * - 提供聚合查询功能
 * - 优化分页查询性能
 * 
 * @author liubo
 * @date 2025-01-19
 */
@Repository
public interface TelegramMessageRepository extends MongoRepository<TelegramMessage, String> {

    // ==================== 基础查询方法 ====================
    
    /**
     * 根据账号、聊天ID和消息ID查找消息
     * 用于消息去重和精确查找
     * 
     * @param accountPhone 账号手机号
     * @param chatId 聊天ID
     * @param messageId 消息ID
     * @return 消息对象（可能为空）
     */
    Optional<TelegramMessage> findByAccountPhoneAndChatIdAndMessageId(
            String accountPhone, Long chatId, Long messageId);

    /**
     * 检查消息是否已存在
     * 用于快速去重检查
     * 
     * @param accountPhone 账号手机号
     * @param chatId 聊天ID
     * @param messageId 消息ID
     * @return 是否存在
     */
    boolean existsByAccountPhoneAndChatIdAndMessageId(
            String accountPhone, Long chatId, Long messageId);

    // ==================== 按账号分组查询 ====================
    
    /**
     * 查询指定账号的所有消息
     * 支持分页查询
     * 
     * @param accountPhone 账号手机号
     * @param pageable 分页参数
     * @return 消息分页结果
     */
    Page<TelegramMessage> findByAccountPhone(String accountPhone, Pageable pageable);

    /**
     * 查询指定账号在指定时间范围内的消息
     * 
     * @param accountPhone 账号手机号
     * @param startTime 开始时间
     * @param endTime 结束时间
     * @param pageable 分页参数
     * @return 消息分页结果
     */
    Page<TelegramMessage> findByAccountPhoneAndMessageDateBetween(
            String accountPhone, LocalDateTime startTime, LocalDateTime endTime, Pageable pageable);

    /**
     * 统计指定账号的消息总数
     * 
     * @param accountPhone 账号手机号
     * @return 消息总数
     */
    long countByAccountPhone(String accountPhone);

    /**
     * 查询指定账号的最新消息
     * 
     * @param accountPhone 账号手机号
     * @param pageable 分页参数（通常设置为按时间倒序）
     * @return 最新消息列表
     */
    @Query("{'accountPhone': ?0}")
    Page<TelegramMessage> findLatestMessagesByAccount(String accountPhone, Pageable pageable);

    // ==================== 按群组分组查询 ====================
    
    /**
     * 查询指定账号在指定群组的所有消息
     * 
     * @param accountPhone 账号手机号
     * @param chatId 聊天ID
     * @param pageable 分页参数
     * @return 消息分页结果
     */
    Page<TelegramMessage> findByAccountPhoneAndChatId(
            String accountPhone, Long chatId, Pageable pageable);

    /**
     * 查询指定账号在指定群组的指定时间范围内的消息
     * 
     * @param accountPhone 账号手机号
     * @param chatId 聊天ID
     * @param startTime 开始时间
     * @param endTime 结束时间
     * @param pageable 分页参数
     * @return 消息分页结果
     */
    Page<TelegramMessage> findByAccountPhoneAndChatIdAndMessageDateBetween(
            String accountPhone, Long chatId, LocalDateTime startTime, 
            LocalDateTime endTime, Pageable pageable);

    /**
     * 统计指定账号在指定群组的消息总数
     * 
     * @param accountPhone 账号手机号
     * @param chatId 聊天ID
     * @return 消息总数
     */
    long countByAccountPhoneAndChatId(String accountPhone, Long chatId);

    /**
     * 查询指定账号在指定群组的最新消息
     * 
     * @param accountPhone 账号手机号
     * @param chatId 聊天ID
     * @param pageable 分页参数
     * @return 最新消息列表
     */
    @Query("{'accountPhone': ?0, 'chatId': ?1}")
    Page<TelegramMessage> findLatestMessagesByAccountAndChat(
            String accountPhone, Long chatId, Pageable pageable);

    // ==================== 消息类型查询 ====================
    
    /**
     * 查询指定账号的指定类型消息
     * 
     * @param accountPhone 账号手机号
     * @param messageType 消息类型
     * @param pageable 分页参数
     * @return 消息分页结果
     */
    Page<TelegramMessage> findByAccountPhoneAndMessageType(
            String accountPhone, String messageType, Pageable pageable);

    /**
     * 查询指定账号在指定群组的指定类型消息
     * 
     * @param accountPhone 账号手机号
     * @param chatId 聊天ID
     * @param messageType 消息类型
     * @param pageable 分页参数
     * @return 消息分页结果
     */
    Page<TelegramMessage> findByAccountPhoneAndChatIdAndMessageType(
            String accountPhone, Long chatId, String messageType, Pageable pageable);

    /**
     * 查询指定账号的多种类型消息
     * 
     * @param accountPhone 账号手机号
     * @param messageTypes 消息类型列表
     * @param pageable 分页参数
     * @return 消息分页结果
     */
    Page<TelegramMessage> findByAccountPhoneAndMessageTypeIn(
            String accountPhone, List<String> messageTypes, Pageable pageable);

    // ==================== 文本搜索查询 ====================
    
    /**
     * 在指定账号的消息中搜索包含指定文本的消息
     * 
     * @param accountPhone 账号手机号
     * @param searchText 搜索文本
     * @param pageable 分页参数
     * @return 消息分页结果
     */
    @Query("{'accountPhone': ?0, 'messageText': {$regex: ?1, $options: 'i'}}")
    Page<TelegramMessage> searchMessagesByText(
            String accountPhone, String searchText, Pageable pageable);

    /**
     * 在指定账号的指定群组中搜索包含指定文本的消息
     * 
     * @param accountPhone 账号手机号
     * @param chatId 聊天ID
     * @param searchText 搜索文本
     * @param pageable 分页参数
     * @return 消息分页结果
     */
    @Query("{'accountPhone': ?0, 'chatId': ?1, 'messageText': {$regex: ?2, $options: 'i'}}")
    Page<TelegramMessage> searchMessagesByTextInChat(
            String accountPhone, Long chatId, String searchText, Pageable pageable);

    // ==================== 发送者查询 ====================
    
    /**
     * 查询指定账号中指定发送者的消息
     * 
     * @param accountPhone 账号手机号
     * @param senderId 发送者ID
     * @param pageable 分页参数
     * @return 消息分页结果
     */
    Page<TelegramMessage> findByAccountPhoneAndSenderId(
            String accountPhone, Long senderId, Pageable pageable);

    /**
     * 查询指定账号在指定群组中指定发送者的消息
     * 
     * @param accountPhone 账号手机号
     * @param chatId 聊天ID
     * @param senderId 发送者ID
     * @param pageable 分页参数
     * @return 消息分页结果
     */
    Page<TelegramMessage> findByAccountPhoneAndChatIdAndSenderId(
            String accountPhone, Long chatId, Long senderId, Pageable pageable);

    // ==================== 时间范围查询 ====================
    
    /**
     * 查询指定时间之后的消息
     * 
     * @param accountPhone 账号手机号
     * @param afterTime 时间点
     * @param pageable 分页参数
     * @return 消息分页结果
     */
    Page<TelegramMessage> findByAccountPhoneAndMessageDateAfter(
            String accountPhone, LocalDateTime afterTime, Pageable pageable);

    /**
     * 查询指定时间之前的消息
     * 
     * @param accountPhone 账号手机号
     * @param beforeTime 时间点
     * @param pageable 分页参数
     * @return 消息分页结果
     */
    Page<TelegramMessage> findByAccountPhoneAndMessageDateBefore(
            String accountPhone, LocalDateTime beforeTime, Pageable pageable);

    /**
     * 查询今天的消息
     * 
     * @param accountPhone 账号手机号
     * @param startOfDay 今天开始时间
     * @param endOfDay 今天结束时间
     * @param pageable 分页参数
     * @return 消息分页结果
     */
    @Query("{'accountPhone': ?0, 'messageDate': {$gte: ?1, $lt: ?2}}")
    Page<TelegramMessage> findTodayMessages(
            String accountPhone, LocalDateTime startOfDay, LocalDateTime endOfDay, Pageable pageable);

    // ==================== 统计查询 ====================
    
    /**
     * 统计指定账号在指定时间范围内的消息数量
     * 
     * @param accountPhone 账号手机号
     * @param startTime 开始时间
     * @param endTime 结束时间
     * @return 消息数量
     */
    long countByAccountPhoneAndMessageDateBetween(
            String accountPhone, LocalDateTime startTime, LocalDateTime endTime);

    /**
     * 统计指定账号各群组的消息数量
     * 使用聚合查询
     * 
     * @param accountPhone 账号手机号
     * @return 群组消息统计结果
     */
    @Aggregation(pipeline = {
        "{ $match: { 'accountPhone': ?0 } }",
        "{ $group: { '_id': { 'chatId': '$chatId', 'chatTitle': '$chatTitle' }, 'messageCount': { $sum: 1 }, 'lastMessageDate': { $max: '$messageDate' } } }",
        "{ $sort: { 'messageCount': -1 } }"
    })
    List<ChatMessageStats> getMessageStatsByChat(String accountPhone);

    /**
     * 统计指定账号各消息类型的数量
     * 
     * @param accountPhone 账号手机号
     * @return 消息类型统计结果
     */
    @Aggregation(pipeline = {
        "{ $match: { 'accountPhone': ?0 } }",
        "{ $group: { '_id': '$messageType', 'count': { $sum: 1 } } }",
        "{ $sort: { 'count': -1 } }"
    })
    List<MessageTypeStats> getMessageStatsByType(String accountPhone);

    /**
     * 获取指定账号的活跃群组列表
     * 按最后消息时间排序
     * 
     * @param accountPhone 账号手机号
     * @return 活跃群组列表
     */
    @Aggregation(pipeline = {
        "{ $match: { 'accountPhone': ?0 } }",
        "{ $group: { '_id': { 'chatId': '$chatId', 'chatTitle': '$chatTitle', 'chatType': '$chatType' }, 'lastMessageDate': { $max: '$messageDate' }, 'messageCount': { $sum: 1 } } }",
        "{ $sort: { 'lastMessageDate': -1 } }",
        "{ $limit: 50 }"
    })
    List<ActiveChatInfo> getActiveChats(String accountPhone);

    // ==================== 批量操作 ====================
    
    /**
     * 查询指定账号在多个群组中的消息
     * 
     * @param accountPhone 账号手机号
     * @param chatIds 群组ID列表
     * @param pageable 分页参数
     * @return 消息分页结果
     */
    Page<TelegramMessage> findByAccountPhoneAndChatIdIn(
            String accountPhone, List<Long> chatIds, Pageable pageable);

    /**
     * 删除指定账号的所有消息
     * 谨慎使用，建议先备份
     * 
     * @param accountPhone 账号手机号
     * @return 删除的消息数量
     */
    long deleteByAccountPhone(String accountPhone);

    /**
     * 删除指定账号在指定群组的所有消息
     * 
     * @param accountPhone 账号手机号
     * @param chatId 聊天ID
     * @return 删除的消息数量
     */
    long deleteByAccountPhoneAndChatId(String accountPhone, Long chatId);

    /**
     * 删除指定时间之前的消息
     * 用于数据清理
     * 
     * @param beforeTime 时间点
     * @return 删除的消息数量
     */
    long deleteByMessageDateBefore(LocalDateTime beforeTime);

    // ==================== 内部统计类 ====================
    
    /**
     * 群组消息统计信息
     */
    interface ChatMessageStats {
        ChatInfo get_id();
        Long getMessageCount();
        LocalDateTime getLastMessageDate();
        
        interface ChatInfo {
            Long getChatId();
            String getChatTitle();
        }
    }

    /**
     * 消息类型统计信息
     */
    interface MessageTypeStats {
        String get_id(); // 消息类型
        Long getCount();
    }

    /**
     * 活跃群组信息
     */
    interface ActiveChatInfo {
        ChatDetail get_id();
        LocalDateTime getLastMessageDate();
        Long getMessageCount();
        
        interface ChatDetail {
            Long getChatId();
            String getChatTitle();
            String getChatType();
        }
    }
}