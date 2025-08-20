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
     * 注意：当启用分片存储时，此字段可能为空，数据存储在分片中
     */
    @Field("database_files")
    private Map<String, String> databaseFiles;

    /**
     * 下载文件目录的文件列表
     * 存储已下载的媒体文件信息
     * 注意：当启用分片存储时，此字段可能为空，数据存储在分片中
     */
    @Field("downloaded_files")
    private Map<String, String> downloadedFiles;

    /**
     * 存储格式版本
     * v1: 传统单文档存储（兼容现有数据）
     * v2: 分片存储格式
     */
    @Field("storage_version")
    private String storageVersion = "v1";

    /**
     * 是否启用GridFS存储
     * true: 使用GridFS存储，大文件数据存储在GridFS中
     * false: 使用传统存储，数据存储在当前文档的databaseFiles和downloadedFiles字段中
     */
    @Field("shard_enabled")
    private Boolean shardEnabled = false;

    /**
     * GridFS文件引用列表（保留字段以兼容旧数据）
     * 当启用GridFS存储时，可能存储相关GridFS文件的ID列表
     * 格式：["gridfs_id_1", "gridfs_id_2", ...]
     */
    @Field("shard_refs")
    private java.util.List<String> shardRefs;

    /**
     * 数据完整性校验码
     * 用于验证分片数据的完整性，防止数据丢失或损坏
     * 使用SHA-256算法计算所有分片数据的哈希值
     */
    @Field("integrity_hash")
    private String integrityHash;

    /**
     * 压缩算法类型
     * none: 不压缩
     * gzip: GZIP压缩
     * 未来可扩展其他压缩算法
     */
    @Field("compression_type")
    private String compressionType = "none";

    /**
     * 原始数据大小（字节）
     * 压缩前的数据总大小，用于统计和监控
     */
    @Field("original_size")
    private Long originalSize;

    /**
     * 压缩后数据大小（字节）
     * 压缩后的数据总大小，用于计算压缩率
     */
    @Field("compressed_size")
    private Long compressedSize;

    /**
     * GridFS存储的数据库文件ID
     * 当使用GridFS存储时，此字段存储数据库文件在GridFS中的ObjectId
     */
    @Field("database_files_gridfs_id")
    private String databaseFilesGridfsId;

    /**
     * GridFS存储的下载文件ID
     * 当使用GridFS存储时，此字段存储下载文件在GridFS中的ObjectId
     */
    @Field("downloaded_files_gridfs_id")
    private String downloadedFilesGridfsId;

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

    public String getStorageVersion() {
        return storageVersion;
    }

    public void setStorageVersion(String storageVersion) {
        this.storageVersion = storageVersion;
    }

    public Boolean getShardEnabled() {
        return shardEnabled;
    }

    public void setShardEnabled(Boolean shardEnabled) {
        this.shardEnabled = shardEnabled;
    }

    public java.util.List<String> getShardRefs() {
        return shardRefs;
    }

    public void setShardRefs(java.util.List<String> shardRefs) {
        this.shardRefs = shardRefs;
    }

    public String getIntegrityHash() {
        return integrityHash;
    }

    public void setIntegrityHash(String integrityHash) {
        this.integrityHash = integrityHash;
    }

    public String getCompressionType() {
        return compressionType;
    }

    public void setCompressionType(String compressionType) {
        this.compressionType = compressionType;
    }

    public Long getOriginalSize() {
        return originalSize;
    }

    public void setOriginalSize(Long originalSize) {
        this.originalSize = originalSize;
    }

    public Long getCompressedSize() {
        return compressedSize;
    }

    public void setCompressedSize(Long compressedSize) {
        this.compressedSize = compressedSize;
    }

    public String getDatabaseFilesGridfsId() {
        return databaseFilesGridfsId;
    }

    public void setDatabaseFilesGridfsId(String databaseFilesGridfsId) {
        this.databaseFilesGridfsId = databaseFilesGridfsId;
    }

    public String getDownloadedFilesGridfsId() {
        return downloadedFilesGridfsId;
    }

    public void setDownloadedFilesGridfsId(String downloadedFilesGridfsId) {
        this.downloadedFilesGridfsId = downloadedFilesGridfsId;
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
        this.lastActiveTime = LocalDateTime.now();
    }

    /**
     * 获取session数据（别名方法）
     * 
     * @return 数据库文件数据
     */
    public Map<String, String> getSessionData() {
        return getDatabaseFiles();
    }

    /**
     * 获取TDLib数据（别名方法）
     * 
     * @return 数据库文件数据
     */
    public Map<String, String> getTdlibData() {
        return getDatabaseFiles();
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