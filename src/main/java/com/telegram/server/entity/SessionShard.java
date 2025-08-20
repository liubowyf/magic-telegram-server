package com.telegram.server.entity;

import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;
import org.springframework.data.mongodb.core.mapping.Field;
import org.springframework.data.mongodb.core.index.Indexed;

import java.time.LocalDateTime;

/**
 * Session分片实体类
 * 用于存储大型session数据的分片，解决MongoDB 16MB文档大小限制
 * 
 * @author liubo
 * @date 2025-01-19
 */
@Document(collection = "session_shards")
public class SessionShard {

    /**
     * 分片唯一标识
     */
    @Id
    private String id;

    /**
     * 关联的主session ID
     */
    @Indexed
    @Field("session_id")
    private String sessionId;

    /**
     * 分片序号（从0开始）
     */
    @Field("shard_index")
    private Integer shardIndex;

    /**
     * 分片类型：database_files, downloaded_files
     */
    @Field("shard_type")
    private String shardType;

    /**
     * 分片数据（Base64编码的压缩数据）
     */
    @Field("data")
    private String data;

    /**
     * 压缩类型：gzip, lz4, none
     */
    @Field("compression_type")
    private String compressionType = "gzip";

    /**
     * 原始数据大小（字节）
     */
    @Field("original_size")
    private Long originalSize;

    /**
     * 压缩后数据大小（字节）
     */
    @Field("compressed_size")
    private Long compressedSize;

    /**
     * 分片数据的MD5哈希值，用于完整性校验
     */
    @Field("data_hash")
    private String dataHash;

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
     * 默认构造函数
     */
    public SessionShard() {
        this.createdTime = LocalDateTime.now();
        this.updatedTime = LocalDateTime.now();
    }

    /**
     * 构造函数
     * 
     * @param sessionId 主session ID
     * @param shardIndex 分片序号
     * @param shardType 分片类型
     */
    public SessionShard(String sessionId, Integer shardIndex, String shardType) {
        this();
        this.sessionId = sessionId;
        this.shardIndex = shardIndex;
        this.shardType = shardType;
        this.id = generateShardId(sessionId, shardType, shardIndex);
    }

    /**
     * 生成分片ID
     * 
     * @param sessionId 主session ID
     * @param shardType 分片类型
     * @param shardIndex 分片序号
     * @return 分片ID
     */
    private String generateShardId(String sessionId, String shardType, Integer shardIndex) {
        return String.format("%s_%s_%d", sessionId, shardType, shardIndex);
    }

    // Getter and Setter methods

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getSessionId() {
        return sessionId;
    }

    public void setSessionId(String sessionId) {
        this.sessionId = sessionId;
    }

    public Integer getShardIndex() {
        return shardIndex;
    }

    public void setShardIndex(Integer shardIndex) {
        this.shardIndex = shardIndex;
    }

    public String getShardType() {
        return shardType;
    }

    public void setShardType(String shardType) {
        this.shardType = shardType;
    }

    public String getData() {
        return data;
    }

    public void setData(String data) {
        this.data = data;
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

    public String getDataHash() {
        return dataHash;
    }

    public void setDataHash(String dataHash) {
        this.dataHash = dataHash;
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

    /**
     * 更新时间戳
     */
    public void updateTimestamp() {
        this.updatedTime = LocalDateTime.now();
    }

    @Override
    public String toString() {
        return "SessionShard{" +
                "id='" + id + '\'' +
                ", sessionId='" + sessionId + '\'' +
                ", shardIndex=" + shardIndex +
                ", shardType='" + shardType + '\'' +
                ", compressionType='" + compressionType + '\'' +
                ", originalSize=" + originalSize +
                ", compressedSize=" + compressedSize +
                ", dataHash='" + dataHash + '\'' +
                ", createdTime=" + createdTime +
                ", updatedTime=" + updatedTime +
                '}';
    }
}