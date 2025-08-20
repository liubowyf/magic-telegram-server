package com.telegram.server.entity;

import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;
import org.springframework.data.mongodb.core.mapping.Field;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.index.CompoundIndex;
import org.springframework.data.mongodb.core.index.CompoundIndexes;

import java.time.LocalDateTime;
import com.telegram.server.util.TimeZoneUtil;

/**
 * Telegram消息实体类
 * 
 * 用于在MongoDB中存储Telegram群消息，支持按账号和群组分组存储。
 * 实现消息的及时入库和高效查询，支持大量群组的消息处理。
 * 
 * 主要功能：
 * - 存储完整的消息信息（文本、媒体、发送者等）
 * - 支持按账号和群组分组查询
 * - 提供消息去重机制
 * - 支持多种消息类型（文本、图片、视频、音频等）
 * - 记录消息状态和统计信息
 * 
 * 分组策略：
 * - 按账号分组：accountPhone字段
 * - 按群组分组：chatId字段
 * - 复合分组：accountPhone + chatId组合
 * 
 * 性能优化：
 * - 使用复合索引提高查询效率
 * - 支持时间范围查询
 * - 优化存储结构减少空间占用
 * 
 * @author liubo
 * @date 2025-01-19
 */
@Document(collection = "telegram_messages")
@CompoundIndexes({
    @CompoundIndex(name = "account_chat_message_idx", def = "{'accountPhone': 1, 'chatId': 1, 'messageId': 1}", unique = true),
    @CompoundIndex(name = "account_chat_time_idx", def = "{'accountPhone': 1, 'chatId': 1, 'messageDate': -1}"),
    @CompoundIndex(name = "message_time_idx", def = "{'messageDate': -1}")
})
public class TelegramMessage {

    /**
     * 主键ID，使用复合ID格式：accountPhone_chatId_messageId
     * 确保不同账号间的消息唯一性
     */
    @Id
    private String id;

    // ==================== 分组字段 ====================
    
    /**
     * 账号手机号（带国家代码）
     * 用于按账号分组，与TelegramSession中的phoneNumber对应
     */
    @Indexed
    @Field("account_phone")
    private String accountPhone;

    /**
     * 聊天ID
     * Telegram中每个聊天/群组的唯一标识
     */
    @Indexed
    @Field("chat_id")
    private Long chatId;

    /**
     * 群组/聊天名称
     * 便于识别消息来源
     */
    @Field("chat_title")
    private String chatTitle;

    /**
     * 聊天类型
     * private: 私聊
     * basicGroup: 基础群组
     * supergroup: 超级群组
     * channel: 频道
     */
    @Field("chat_type")
    private String chatType;

    // ==================== 消息基础信息 ====================
    
    /**
     * Telegram消息ID
     * 在特定聊天中的唯一标识
     */
    @Field("message_id")
    private Long messageId;

    /**
     * 消息文本内容
     * 对于非文本消息，存储caption或描述信息
     */
    @Field("message_text")
    private String messageText;

    /**
     * 消息类型
     * text: 文本消息
     * photo: 图片消息
     * video: 视频消息
     * audio: 音频消息
     * document: 文档消息
     * sticker: 贴纸消息
     * animation: 动画消息
     * voice: 语音消息
     * videoNote: 视频笔记
     * contact: 联系人消息
     * location: 位置消息
     * poll: 投票消息
     * other: 其他类型
     */
    @Field("message_type")
    private String messageType;

    /**
     * 消息发送时间
     * 使用Telegram服务器时间
     */
    @Indexed
    @Field("message_date")
    private LocalDateTime messageDate;

    // ==================== 发送者信息 ====================
    
    /**
     * 发送者ID
     * 用户ID或聊天ID
     */
    @Field("sender_id")
    private Long senderId;

    /**
     * 发送者名称
     * 用户名或聊天名称
     */
    @Field("sender_name")
    private String senderName;

    /**
     * 发送者类型
     * user: 用户
     * chat: 聊天/群组
     */
    @Field("sender_type")
    private String senderType;

    // ==================== 消息状态 ====================
    
    /**
     * 消息是否可编辑
     */
    @Field("can_be_edited")
    private Boolean canBeEdited;

    /**
     * 消息是否可删除
     */
    @Field("can_be_deleted")
    private Boolean canBeDeleted;

    /**
     * 消息是否可转发
     */
    @Field("can_be_forwarded")
    private Boolean canBeForwarded;

    /**
     * 消息是否可保存
     */
    @Field("can_be_saved")
    private Boolean canBeSaved;

    /**
     * 消息是否置顶
     */
    @Field("is_pinned")
    private Boolean isPinned;

    // ==================== 回复和转发信息 ====================
    
    /**
     * 回复的消息ID
     * 如果是回复消息，记录被回复消息的ID
     */
    @Field("reply_to_message_id")
    private Long replyToMessageId;

    /**
     * 转发来源聊天ID
     * 如果是转发消息，记录原始聊天ID
     */
    @Field("forward_from_chat_id")
    private Long forwardFromChatId;

    /**
     * 转发来源消息ID
     * 如果是转发消息，记录原始消息ID
     */
    @Field("forward_from_message_id")
    private Long forwardFromMessageId;

    // ==================== 媒体信息 ====================
    
    /**
     * 媒体类型
     * 对于包含媒体的消息，记录具体的媒体类型
     */
    @Field("media_type")
    private String mediaType;

    /**
     * 媒体文件路径
     * 本地存储的媒体文件路径
     */
    @Field("media_path")
    private String mediaPath;

    /**
     * 媒体文件大小（字节）
     */
    @Field("media_size")
    private Long mediaSize;

    /**
     * 媒体文件宽度（像素）
     * 适用于图片和视频
     */
    @Field("media_width")
    private Integer mediaWidth;

    /**
     * 媒体文件高度（像素）
     * 适用于图片和视频
     */
    @Field("media_height")
    private Integer mediaHeight;

    /**
     * 媒体文件时长（秒）
     * 适用于音频和视频
     */
    @Field("media_duration")
    private Integer mediaDuration;

    // ==================== 图片存储信息 ====================
    
    /**
     * 图片数据（Base64编码）
     * 小于1MB的图片直接存储Base64编码数据
     * 大于1MB的图片此字段为null，只存储文件路径
     */
    @Field("image_data")
    private String imageData;
    
    /**
     * 图片文件名
     * 原始图片文件的名称
     */
    @Field("image_filename")
    private String imageFilename;
    
    /**
     * 图片MIME类型
     * 如：image/jpeg, image/png, image/webp等
     */
    @Field("image_mime_type")
    private String imageMimeType;
    
    /**
     * 图片处理状态
     * pending: 待处理
     * downloading: 下载中
     * processed: 已处理
     * failed: 处理失败
     */
    @Field("image_status")
    private String imageStatus;
    
    /**
     * 图片处理完成时间
     * 记录图片下载和处理完成的时间
     */
    @Field("image_processed_time")
    private LocalDateTime imageProcessedTime;

    // ==================== 统计信息 ====================
    
    /**
     * 消息查看次数
     * 在频道中的查看统计
     */
    @Field("view_count")
    private Integer viewCount;

    /**
     * 消息转发次数
     * 被转发的次数统计
     */
    @Field("forward_count")
    private Integer forwardCount;

    /**
     * 消息线程ID
     * 用于消息线程功能
     */
    @Field("message_thread_id")
    private Long messageThreadId;

    /**
     * 媒体专辑ID
     * 用于分组媒体消息
     */
    @Field("media_album_id")
    private Long mediaAlbumId;

    // ==================== 系统字段 ====================
    
    /**
     * 消息入库时间
     * 记录消息存储到数据库的时间
     */
    @Field("created_time")
    private LocalDateTime createdTime;

    /**
     * 处理实例ID
     * 记录处理该消息的服务实例，用于集群环境下的问题追踪
     */
    @Field("instance_id")
    private String instanceId;

    /**
     * 原始消息JSON
     * 存储完整的原始消息数据，便于后续扩展和调试
     */
    @Field("raw_message_json")
    private String rawMessageJson;

    // ==================== 构造函数 ====================
    
    /**
     * 默认构造函数
     */
    public TelegramMessage() {
        this.createdTime = TimeZoneUtil.getCurrentUtc();
        this.canBeEdited = false;
        this.canBeDeleted = false;
        this.canBeForwarded = false;
        this.canBeSaved = false;
        this.isPinned = false;
        this.viewCount = 0;
        this.forwardCount = 0;
    }

    /**
     * 构造函数
     * 
     * @param accountPhone 账号手机号
     * @param chatId 聊天ID
     * @param messageId 消息ID
     */
    public TelegramMessage(String accountPhone, Long chatId, Long messageId) {
        this();
        this.accountPhone = accountPhone;
        this.chatId = chatId;
        this.messageId = messageId;
        this.id = generateId(accountPhone, chatId, messageId);
    }

    // ==================== 工具方法 ====================
    
    /**
     * 生成复合ID
     * 格式：accountPhone_chatId_messageId
     * 
     * @param accountPhone 账号手机号
     * @param chatId 聊天ID
     * @param messageId 消息ID
     * @return 复合ID字符串
     */
    public static String generateId(String accountPhone, Long chatId, Long messageId) {
        return accountPhone + "_" + chatId + "_" + messageId;
    }

    /**
     * 更新入库时间
     */
    public void updateCreatedTime() {
        this.createdTime = LocalDateTime.now();
    }

    /**
     * 设置实例ID
     * 
     * @param instanceId 实例ID
     */
    public void setInstanceId(String instanceId) {
        this.instanceId = instanceId;
    }

    // ==================== Getter和Setter方法 ====================
    
    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getAccountPhone() {
        return accountPhone;
    }

    public void setAccountPhone(String accountPhone) {
        this.accountPhone = accountPhone;
        // 更新复合ID
        if (this.chatId != null && this.messageId != null) {
            this.id = generateId(accountPhone, this.chatId, this.messageId);
        }
    }

    public Long getChatId() {
        return chatId;
    }

    public void setChatId(Long chatId) {
        this.chatId = chatId;
        // 更新复合ID
        if (this.accountPhone != null && this.messageId != null) {
            this.id = generateId(this.accountPhone, chatId, this.messageId);
        }
    }

    public String getChatTitle() {
        return chatTitle;
    }

    public void setChatTitle(String chatTitle) {
        this.chatTitle = chatTitle;
    }

    public String getChatType() {
        return chatType;
    }

    public void setChatType(String chatType) {
        this.chatType = chatType;
    }

    public Long getMessageId() {
        return messageId;
    }

    public void setMessageId(Long messageId) {
        this.messageId = messageId;
        // 更新复合ID
        if (this.accountPhone != null && this.chatId != null) {
            this.id = generateId(this.accountPhone, this.chatId, messageId);
        }
    }

    public String getMessageText() {
        return messageText;
    }

    public void setMessageText(String messageText) {
        this.messageText = messageText;
    }

    public String getMessageType() {
        return messageType;
    }

    public void setMessageType(String messageType) {
        this.messageType = messageType;
    }

    public LocalDateTime getMessageDate() {
        return messageDate;
    }

    public void setMessageDate(LocalDateTime messageDate) {
        this.messageDate = messageDate;
    }

    public Long getSenderId() {
        return senderId;
    }

    public void setSenderId(Long senderId) {
        this.senderId = senderId;
    }

    public String getSenderName() {
        return senderName;
    }

    public void setSenderName(String senderName) {
        this.senderName = senderName;
    }

    public String getSenderType() {
        return senderType;
    }

    public void setSenderType(String senderType) {
        this.senderType = senderType;
    }

    public Boolean getCanBeEdited() {
        return canBeEdited;
    }

    public void setCanBeEdited(Boolean canBeEdited) {
        this.canBeEdited = canBeEdited;
    }

    public Boolean getCanBeDeleted() {
        return canBeDeleted;
    }

    public void setCanBeDeleted(Boolean canBeDeleted) {
        this.canBeDeleted = canBeDeleted;
    }

    public Boolean getCanBeForwarded() {
        return canBeForwarded;
    }

    public void setCanBeForwarded(Boolean canBeForwarded) {
        this.canBeForwarded = canBeForwarded;
    }

    public Boolean getCanBeSaved() {
        return canBeSaved;
    }

    public void setCanBeSaved(Boolean canBeSaved) {
        this.canBeSaved = canBeSaved;
    }

    public Boolean getIsPinned() {
        return isPinned;
    }

    public void setIsPinned(Boolean isPinned) {
        this.isPinned = isPinned;
    }

    public Long getReplyToMessageId() {
        return replyToMessageId;
    }

    public void setReplyToMessageId(Long replyToMessageId) {
        this.replyToMessageId = replyToMessageId;
    }

    public Long getForwardFromChatId() {
        return forwardFromChatId;
    }

    public void setForwardFromChatId(Long forwardFromChatId) {
        this.forwardFromChatId = forwardFromChatId;
    }

    public Long getForwardFromMessageId() {
        return forwardFromMessageId;
    }

    public void setForwardFromMessageId(Long forwardFromMessageId) {
        this.forwardFromMessageId = forwardFromMessageId;
    }

    public String getMediaType() {
        return mediaType;
    }

    public void setMediaType(String mediaType) {
        this.mediaType = mediaType;
    }

    public String getMediaPath() {
        return mediaPath;
    }

    public void setMediaPath(String mediaPath) {
        this.mediaPath = mediaPath;
    }

    public Long getMediaSize() {
        return mediaSize;
    }

    public void setMediaSize(Long mediaSize) {
        this.mediaSize = mediaSize;
    }

    public Integer getMediaWidth() {
        return mediaWidth;
    }

    public void setMediaWidth(Integer mediaWidth) {
        this.mediaWidth = mediaWidth;
    }

    public Integer getMediaHeight() {
        return mediaHeight;
    }

    public void setMediaHeight(Integer mediaHeight) {
        this.mediaHeight = mediaHeight;
    }

    public Integer getMediaDuration() {
        return mediaDuration;
    }

    public void setMediaDuration(Integer mediaDuration) {
        this.mediaDuration = mediaDuration;
    }

    public Integer getViewCount() {
        return viewCount;
    }

    public void setViewCount(Integer viewCount) {
        this.viewCount = viewCount;
    }

    public Integer getForwardCount() {
        return forwardCount;
    }

    public void setForwardCount(Integer forwardCount) {
        this.forwardCount = forwardCount;
    }

    public Long getMessageThreadId() {
        return messageThreadId;
    }

    public void setMessageThreadId(Long messageThreadId) {
        this.messageThreadId = messageThreadId;
    }

    public Long getMediaAlbumId() {
        return mediaAlbumId;
    }

    public void setMediaAlbumId(Long mediaAlbumId) {
        this.mediaAlbumId = mediaAlbumId;
    }

    public LocalDateTime getCreatedTime() {
        return createdTime;
    }

    public void setCreatedTime(LocalDateTime createdTime) {
        this.createdTime = createdTime;
    }

    public String getInstanceId() {
        return instanceId;
    }

    public String getImageData() {
        return imageData;
    }

    public void setImageData(String imageData) {
        this.imageData = imageData;
    }

    public String getImageFilename() {
        return imageFilename;
    }

    public void setImageFilename(String imageFilename) {
        this.imageFilename = imageFilename;
    }

    public String getImageMimeType() {
        return imageMimeType;
    }

    public void setImageMimeType(String imageMimeType) {
        this.imageMimeType = imageMimeType;
    }

    public String getImageStatus() {
        return imageStatus;
    }

    public void setImageStatus(String imageStatus) {
        this.imageStatus = imageStatus;
    }

    public LocalDateTime getImageProcessedTime() {
        return imageProcessedTime;
    }

    public void setImageProcessedTime(LocalDateTime imageProcessedTime) {
        this.imageProcessedTime = imageProcessedTime;
    }

    public String getRawMessageJson() {
        return rawMessageJson;
    }

    public void setRawMessageJson(String rawMessageJson) {
        this.rawMessageJson = rawMessageJson;
    }

    @Override
    public String toString() {
        return "TelegramMessage{" +
                "id='" + id + '\'' +
                ", accountPhone='" + accountPhone + '\'' +
                ", chatId=" + chatId +
                ", chatTitle='" + chatTitle + '\'' +
                ", messageId=" + messageId +
                ", messageType='" + messageType + '\'' +
                ", messageDate=" + messageDate +
                ", senderName='" + senderName + '\'' +
                ", createdTime=" + createdTime +
                '}';
    }
}