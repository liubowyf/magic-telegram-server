package com.telegram.server.entity;

import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;
import org.springframework.data.mongodb.core.mapping.Field;
import org.springframework.data.mongodb.core.index.Indexed;

import java.time.LocalDateTime;
import java.util.Map;

/**
 * Telegram Session实体类
 * 
 * 用于在MongoDB中存储Telegram账号的session信息，支持集群部署。
 * 包含账号认证信息、session文件数据、配置信息等。
 * 
 * 主要功能：
 * - 存储API ID和API Hash
 * - 存储手机号码和认证状态
 * - 存储TDLib session文件的二进制数据
 * - 支持多账号管理
 * - 记录创建和更新时间
 * - 支持session状态管理
 * 
 * 集群支持：
 * - 通过MongoDB实现session数据共享
 * - 支持多个服务实例同时访问
 * - 提供session锁定机制防止冲突
 * 
 * @author liubo
 * @date 2025-08-11
 */
@Document(collection = "telegram_sessions")
public class TelegramSession {

    /**
     * 主键ID，使用手机号作为唯一标识
     */
    @Id
    private String id;

    /**
     * 手机号码（带国家代码）
     * 作为账号的唯一标识
     */
    @Indexed(unique = true)
    @Field("phone_number")
    private String phoneNumber;

    /**
     * Telegram API ID
     */
    @Field("api_id")
    private Integer apiId;

    /**
     * Telegram API Hash
     */
    @Field("api_hash")
    private String apiHash;

    /**
     * 认证状态
     * UNAUTHORIZED - 未认证
     * WAIT_PHONE_NUMBER - 等待手机号
     * WAIT_CODE - 等待验证码
     * WAIT_PASSWORD - 等待密码
     * READY - 已认证
     */
    @Field("auth_state")
    private String authState;

    /**
     * TDLib数据库文件数据（Base64编码）
     * 存储td.binlog等关键文件
     */
    @Field("database_files")
    private Map<String, String> databaseFiles;

    /**
     * 下载文件目录的文件列表
     * 存储已下载的媒体文件信息
     */
    @Field("downloaded_files")
    private Map<String, String> downloadedFiles;

    /**
     * Session是否激活
     * 用于标识当前session是否正在使用
     */
    @Field("is_active")
    private Boolean isActive;

    /**
     * 最后活跃时间
     * 用于清理长时间未使用的session
     */
    @Field("last_active_time")
    private LocalDateTime lastActiveTime;

    /**
     * 创建时间
     */
    @Field("created_time")
    private LocalDateTime createdTime;

    /**
     * 更新时间
     */
    @Field("updated_time")
    private LocalDateTime updatedTime;

    /**
     * 服务实例ID
     * 标识当前使用此session的服务实例
     */
    @Field("instance_id")
    private String instanceId;

    /**
     * 扩展配置信息
     * 存储其他自定义配置
     */
    @Field("extra_config")
    private Map<String, Object> extraConfig;

    /**
     * 默认构造函数
     */
    public TelegramSession() {
        this.createdTime = LocalDateTime.now();
        this.updatedTime = LocalDateTime.now();
        this.isActive = false;
    }

    /**
     * 构造函数
     * 
     * @param phoneNumber 手机号码
     * @param apiId API ID
     * @param apiHash API Hash
     */
    public TelegramSession(String phoneNumber, Integer apiId, String apiHash) {
        this();
        this.phoneNumber = phoneNumber;
        this.apiId = apiId;
        this.apiHash = apiHash;
        this.id = phoneNumber; // 使用手机号作为ID
    }

    // Getter和Setter方法

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getPhoneNumber() {
        return phoneNumber;
    }

    public void setPhoneNumber(String phoneNumber) {
        this.phoneNumber = phoneNumber;
        this.id = phoneNumber; // 同步更新ID
    }

    public Integer getApiId() {
        return apiId;
    }

    public void setApiId(Integer apiId) {
        this.apiId = apiId;
    }

    public String getApiHash() {
        return apiHash;
    }

    public void setApiHash(String apiHash) {
        this.apiHash = apiHash;
    }

    public String getAuthState() {
        return authState;
    }

    public void setAuthState(String authState) {
        this.authState = authState;
    }

    public Map<String, String> getDatabaseFiles() {
        return databaseFiles;
    }

    public void setDatabaseFiles(Map<String, String> databaseFiles) {
        this.databaseFiles = databaseFiles;
    }

    public Map<String, String> getDownloadedFiles() {
        return downloadedFiles;
    }

    public void setDownloadedFiles(Map<String, String> downloadedFiles) {
        this.downloadedFiles = downloadedFiles;
    }

    public Boolean getIsActive() {
        return isActive;
    }

    public void setIsActive(Boolean isActive) {
        this.isActive = isActive;
    }

    public LocalDateTime getLastActiveTime() {
        return lastActiveTime;
    }

    public void setLastActiveTime(LocalDateTime lastActiveTime) {
        this.lastActiveTime = lastActiveTime;
    }

    public LocalDateTime getCreatedTime() {
        return createdTime;
    }

    public void setCreatedTime(LocalDateTime createdTime) {
        this.createdTime = createdTime;
    }

    public LocalDateTime getUpdatedTime() {
        return updatedTime;
    }

    public void setUpdatedTime(LocalDateTime updatedTime) {
        this.updatedTime = updatedTime;
    }

    public String getInstanceId() {
        return instanceId;
    }

    public void setInstanceId(String instanceId) {
        this.instanceId = instanceId;
    }

    public Map<String, Object> getExtraConfig() {
        return extraConfig;
    }

    public void setExtraConfig(Map<String, Object> extraConfig) {
        this.extraConfig = extraConfig;
    }

    /**
     * 更新最后活跃时间
     */
    public void updateLastActiveTime() {
        this.lastActiveTime = LocalDateTime.now();
        this.updatedTime = LocalDateTime.now();
    }

    /**
     * 激活session
     * 
     * @param instanceId 服务实例ID
     */
    public void activate(String instanceId) {
        this.isActive = true;
        this.instanceId = instanceId;
        this.updateLastActiveTime();
    }

    /**
     * 停用session
     */
    public void deactivate() {
        this.isActive = false;
        this.instanceId = null;
        this.updatedTime = LocalDateTime.now();
    }

    @Override
    public String toString() {
        return "TelegramSession{" +
                "id='" + id + '\'' +
                ", phoneNumber='" + phoneNumber + '\'' +
                ", apiId=" + apiId +
                ", authState='" + authState + '\'' +
                ", isActive=" + isActive +
                ", lastActiveTime=" + lastActiveTime +
                ", instanceId='" + instanceId + '\'' +
                '}';
    }
}