package com.telegram.server.dto;

import java.time.LocalDateTime;
import java.util.Map;

/**
 * 消息数据传输对象
 * 
 * 用于Web管理系统中消息信息的数据传输，包含消息的基本信息和内容。
 * 
 * @author liubo
 * @date 2025-01-21
 */
public class MessageDTO {
    
    /**
     * 消息ID
     */
    private String id;
    
    /**
     * 消息ID（Telegram原始ID）
     */
    private Long messageId;
    
    /**
     * 群组ID
     */
    private Long chatId;
    
    /**
     * 群组名称
     */
    private String chatTitle;
    
    /**
     * 发送者ID
     */
    private Long senderId;
    
    /**
     * 发送者姓名
     */
    private String senderName;
    
    /**
     * 消息类型
     */
    private String messageType;
    
    /**
     * 文本内容
     */
    private String textContent;
    
    /**
     * 是否包含图片
     */
    private boolean hasImage;
    
    /**
     * 图片信息
     */
    private Map<String, Object> imageInfo;
    
    /**
     * 消息时间
     */
    private LocalDateTime messageTime;
    
    /**
     * 创建时间
     */
    private LocalDateTime createdAt;
    
    /**
     * 原始消息数据
     */
    private Map<String, Object> rawData;
    
    /**
     * 原始消息数据（JSON格式）
     */
    private String rawMessageData;
    
    /**
     * 图片数据（Base64编码）
     */
    private String imageData;
    
    /**
     * 图片MIME类型
     */
    private String imageMimeType;
    
    /**
     * 图片文件名
     */
    private String imageFilename;
    
    /**
     * 图片状态
     */
    private String imageStatus;
    
    // 构造函数
    public MessageDTO() {}
    
    public MessageDTO(String id, Long messageId, Long chatId, String messageType) {
        this.id = id;
        this.messageId = messageId;
        this.chatId = chatId;
        this.messageType = messageType;
    }
    
    // Getter和Setter方法
    public String getId() {
        return id;
    }
    
    public void setId(String id) {
        this.id = id;
    }
    
    public Long getMessageId() {
        return messageId;
    }
    
    public void setMessageId(Long messageId) {
        this.messageId = messageId;
    }
    
    public Long getChatId() {
        return chatId;
    }
    
    public void setChatId(Long chatId) {
        this.chatId = chatId;
    }
    
    public String getChatTitle() {
        return chatTitle;
    }
    
    public void setChatTitle(String chatTitle) {
        this.chatTitle = chatTitle;
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
    
    public String getMessageType() {
        return messageType;
    }
    
    public void setMessageType(String messageType) {
        this.messageType = messageType;
    }
    
    public String getTextContent() {
        return textContent;
    }
    
    public void setTextContent(String textContent) {
        this.textContent = textContent;
    }
    
    public boolean isHasImage() {
        return hasImage;
    }
    
    public void setHasImage(boolean hasImage) {
        this.hasImage = hasImage;
    }
    
    public Map<String, Object> getImageInfo() {
        return imageInfo;
    }
    
    public void setImageInfo(Map<String, Object> imageInfo) {
        this.imageInfo = imageInfo;
    }
    
    public LocalDateTime getMessageTime() {
        return messageTime;
    }
    
    public void setMessageTime(LocalDateTime messageTime) {
        this.messageTime = messageTime;
    }
    
    public LocalDateTime getCreatedAt() {
        return createdAt;
    }
    
    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }
    
    public Map<String, Object> getRawData() {
        return rawData;
    }
    
    public void setRawData(Map<String, Object> rawData) {
        this.rawData = rawData;
    }
    
    public String getRawMessageData() {
        return rawMessageData;
    }
    
    public void setRawMessageData(String rawMessageData) {
        this.rawMessageData = rawMessageData;
    }
    
    public String getImageData() {
        return imageData;
    }
    
    public void setImageData(String imageData) {
        this.imageData = imageData;
    }
    
    public String getImageMimeType() {
        return imageMimeType;
    }
    
    public void setImageMimeType(String imageMimeType) {
        this.imageMimeType = imageMimeType;
    }
    
    public String getImageFilename() {
        return imageFilename;
    }
    
    public void setImageFilename(String imageFilename) {
        this.imageFilename = imageFilename;
    }
    
    public String getImageStatus() {
        return imageStatus;
    }
    
    public void setImageStatus(String imageStatus) {
        this.imageStatus = imageStatus;
    }
    
    @Override
    public String toString() {
        return "MessageDTO{" +
                "id='" + id + '\'' +
                ", messageId=" + messageId +
                ", chatId=" + chatId +
                ", chatTitle='" + chatTitle + '\'' +
                ", senderId=" + senderId +
                ", senderName='" + senderName + '\'' +
                ", messageType='" + messageType + '\'' +
                ", textContent='" + textContent + '\'' +
                ", hasImage=" + hasImage +
                ", messageTime=" + messageTime +
                ", createdAt=" + createdAt +
                '}';
    }
}