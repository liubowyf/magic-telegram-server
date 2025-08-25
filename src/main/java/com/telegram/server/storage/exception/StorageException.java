package com.telegram.server.storage.exception;

/**
 * 存储异常类
 * 用于表示分片存储过程中发生的各种异常情况
 * 
 * @author liubo
 * @date 2025-08-20
 */
public class StorageException extends Exception {
    
    private final ErrorType errorType;
    private final String sessionId;
    private final long timestamp;
    
    /**
     * 错误类型枚举
     */
    public enum ErrorType {
        /** 网络错误 */
        NETWORK_ERROR,
        /** 临时失败 */
        TEMPORARY_FAILURE,
        /** 超时 */
        TIMEOUT,
        /** 文档大小超限 */
        DOCUMENT_SIZE_EXCEEDED,
        /** 无效数据 */
        INVALID_DATA,
        /** 认证失败 */
        AUTHENTICATION_FAILED,
        /** 熔断器开启 */
        CIRCUIT_BREAKER_OPEN,
        /** 操作被中断 */
        OPERATION_INTERRUPTED,
        /** 超过最大重试次数 */
        MAX_RETRIES_EXCEEDED,
        /** 压缩失败 */
        COMPRESSION_FAILED,
        /** MongoDB操作失败 */
        MONGO_OPERATION_FAILED,
        /** 分片大小超限 */
        SHARD_SIZE_EXCEEDED,
        /** 数据库错误 */
        DATABASE_ERROR,
        /** 未知错误 */
        UNKNOWN_ERROR
    }
    
    /**
     * 构造函数
     * 
     * @param errorType 错误类型
     * @param message 错误消息
     * @param sessionId Session ID
     */
    public StorageException(ErrorType errorType, String message, String sessionId) {
        super(message);
        this.errorType = errorType;
        this.sessionId = sessionId;
        this.timestamp = System.currentTimeMillis();
    }
    
    /**
     * 构造函数（带原因）
     * 
     * @param errorType 错误类型
     * @param message 错误消息
     * @param sessionId Session ID
     * @param cause 原因
     */
    public StorageException(ErrorType errorType, String message, String sessionId, Throwable cause) {
        super(message, cause);
        this.errorType = errorType;
        this.sessionId = sessionId;
        this.timestamp = System.currentTimeMillis();
    }
    
    /**
     * 获取错误类型
     * 
     * @return 错误类型
     */
    public ErrorType getErrorType() {
        return errorType;
    }
    
    /**
     * 获取Session ID
     * 
     * @return Session ID
     */
    public String getSessionId() {
        return sessionId;
    }
    
    /**
     * 获取时间戳
     * 
     * @return 时间戳
     */
    public long getTimestamp() {
        return timestamp;
    }
    
    /**
     * 判断是否为可重试的错误
     * 
     * @return 是否可重试
     */
    public boolean isRetryable() {
        switch (errorType) {
            case NETWORK_ERROR:
            case TEMPORARY_FAILURE:
            case TIMEOUT:
                return true;
            default:
                return false;
        }
    }
    
    /**
     * 判断是否为严重错误
     * 
     * @return 是否为严重错误
     */
    public boolean isCritical() {
        switch (errorType) {
            case DOCUMENT_SIZE_EXCEEDED:
            case SHARD_SIZE_EXCEEDED:
            case AUTHENTICATION_FAILED:
            case MAX_RETRIES_EXCEEDED:
                return true;
            default:
                return false;
        }
    }
    
    @Override
    public String toString() {
        return String.format(
            "StorageException{errorType=%s, sessionId='%s', timestamp=%d, message='%s'}",
            errorType, sessionId, timestamp, getMessage()
        );
    }
    
    /**
     * 创建网络错误异常
     * 
     * @param sessionId Session ID
     * @param message 错误消息
     * @param cause 原因
     * @return StorageException
     */
    public static StorageException networkError(String sessionId, String message, Throwable cause) {
        return new StorageException(ErrorType.NETWORK_ERROR, message, sessionId, cause);
    }
    
    /**
     * 创建超时异常
     * 
     * @param sessionId Session ID
     * @param message 错误消息
     * @return StorageException
     */
    public static StorageException timeout(String sessionId, String message) {
        return new StorageException(ErrorType.TIMEOUT, message, sessionId);
    }
    
    /**
     * 创建文档大小超限异常
     * 
     * @param sessionId Session ID
     * @param actualSize 实际大小
     * @param maxSize 最大大小
     * @return StorageException
     */
    public static StorageException documentSizeExceeded(String sessionId, long actualSize, long maxSize) {
        String message = String.format("文档大小超限: actual=%d, max=%d", actualSize, maxSize);
        return new StorageException(ErrorType.DOCUMENT_SIZE_EXCEEDED, message, sessionId);
    }
    
    /**
     * 创建分片大小超限异常
     * 
     * @param sessionId Session ID
     * @param shardSize 分片大小
     * @param maxSize 最大大小
     * @return StorageException
     */
    public static StorageException shardSizeExceeded(String sessionId, long shardSize, long maxSize) {
        String message = String.format("分片大小超限: shard=%d, max=%d", shardSize, maxSize);
        return new StorageException(ErrorType.SHARD_SIZE_EXCEEDED, message, sessionId);
    }
    
    /**
     * 创建压缩失败异常
     * 
     * @param sessionId Session ID
     * @param compressionType 压缩类型
     * @param cause 原因
     * @return StorageException
     */
    public static StorageException compressionFailed(String sessionId, String compressionType, Throwable cause) {
        String message = String.format("压缩失败: type=%s", compressionType);
        return new StorageException(ErrorType.COMPRESSION_FAILED, message, sessionId, cause);
    }
    
    /**
     * 创建MongoDB操作失败异常
     * 
     * @param sessionId Session ID
     * @param operation 操作类型
     * @param cause 原因
     * @return StorageException
     */
    public static StorageException mongoOperationFailed(String sessionId, String operation, Throwable cause) {
        String message = String.format("MongoDB操作失败: operation=%s", operation);
        return new StorageException(ErrorType.MONGO_OPERATION_FAILED, message, sessionId, cause);
    }
}