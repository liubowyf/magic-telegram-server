package com.telegram.server.storage.exception;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * MongoDB异常处理器
 * 处理MongoDB操作失败的情况，提供重试和恢复策略
 * 
 * @author liubo
 * @date 2025-08-20
 */
public class MongoExceptionHandler {
    
    private static final Logger logger = LoggerFactory.getLogger(MongoExceptionHandler.class);
    
    private final String sessionId;
    private final String operation;
    private final Throwable cause;
    
    /**
     * 处理策略枚举
     */
    public enum Strategy {
        /** 立即重试 */
        IMMEDIATE_RETRY,
        /** 延迟重试 */
        DELAYED_RETRY,
        /** 切换到备用连接 */
        SWITCH_CONNECTION,
        /** 降级到本地存储 */
        FALLBACK_TO_LOCAL,
        /** 拒绝操作 */
        REJECT_OPERATION
    }
    
    /**
     * 处理结果
     */
    public static class HandlingResult {
        private final Strategy strategy;
        private final boolean canProceed;
        private final String reason;
        private final Object suggestion;
        private final long retryDelayMs;
        
        public HandlingResult(Strategy strategy, boolean canProceed, String reason, 
                            Object suggestion, long retryDelayMs) {
            this.strategy = strategy;
            this.canProceed = canProceed;
            this.reason = reason;
            this.suggestion = suggestion;
            this.retryDelayMs = retryDelayMs;
        }
        
        // Getters
        public Strategy getStrategy() { return strategy; }
        public boolean canProceed() { return canProceed; }
        public String getReason() { return reason; }
        public Object getSuggestion() { return suggestion; }
        public long getRetryDelayMs() { return retryDelayMs; }
        
        @Override
        public String toString() {
            return String.format("HandlingResult{strategy=%s, canProceed=%s, reason='%s', retryDelay=%dms}", 
                               strategy, canProceed, reason, retryDelayMs);
        }
    }
    
    public MongoExceptionHandler(String sessionId, String operation, Throwable cause) {
        this.sessionId = sessionId;
        this.operation = operation;
        this.cause = cause;
    }
    
    /**
     * 分析并提供处理策略
     * 
     * @return 处理结果
     */
    public HandlingResult analyze() {
        logger.info("分析MongoDB操作失败情况: sessionId={}, operation={}, cause={}", 
                   sessionId, operation, cause.getClass().getSimpleName());
        
        String causeMessage = cause.getMessage() != null ? cause.getMessage().toLowerCase() : "";
        String causeType = cause.getClass().getSimpleName().toLowerCase();
        
        // 网络连接问题
        if (isNetworkError(cause, causeMessage, causeType)) {
            return new HandlingResult(
                Strategy.DELAYED_RETRY,
                true,
                "网络连接问题，建议延迟重试",
                "检查网络连接状态",
                5000 // 5秒延迟
            );
        }
        
        // 超时问题
        if (isTimeoutError(cause, causeMessage, causeType)) {
            return new HandlingResult(
                Strategy.IMMEDIATE_RETRY,
                true,
                "操作超时，建议立即重试",
                "增加超时时间配置",
                0
            );
        }
        
        // 认证问题
        if (isAuthenticationError(cause, causeMessage, causeType)) {
            return new HandlingResult(
                Strategy.REJECT_OPERATION,
                false,
                "认证失败，需要检查数据库凭据",
                "验证MongoDB用户名和密码",
                0
            );
        }
        
        // 文档大小超限
        if (isDocumentSizeError(cause, causeMessage, causeType)) {
            return new HandlingResult(
                Strategy.REJECT_OPERATION,
                false,
                "文档大小超出MongoDB限制，需要优化数据结构",
                "使用GridFS或减少文档大小",
                0
            );
        }
        
        // 磁盘空间不足
        if (isDiskSpaceError(cause, causeMessage, causeType)) {
            return new HandlingResult(
                Strategy.FALLBACK_TO_LOCAL,
                true,
                "MongoDB磁盘空间不足，建议降级到本地存储",
                "清理MongoDB磁盘空间",
                0
            );
        }
        
        // 数据库锁定
        if (isDatabaseLockError(cause, causeMessage, causeType)) {
            return new HandlingResult(
                Strategy.DELAYED_RETRY,
                true,
                "数据库被锁定，建议延迟重试",
                "等待锁定释放",
                10000 // 10秒延迟
            );
        }
        
        // 连接池耗尽
        if (isConnectionPoolError(cause, causeMessage, causeType)) {
            return new HandlingResult(
                Strategy.DELAYED_RETRY,
                true,
                "连接池耗尽，建议延迟重试",
                "增加连接池大小或减少并发操作",
                3000 // 3秒延迟
            );
        }
        
        // 索引问题
        if (isIndexError(cause, causeMessage, causeType)) {
            return new HandlingResult(
                Strategy.IMMEDIATE_RETRY,
                true,
                "索引相关错误，建议立即重试",
                "检查索引状态",
                0
            );
        }
        
        // 副本集问题
        if (isReplicaSetError(cause, causeMessage, causeType)) {
            return new HandlingResult(
                Strategy.SWITCH_CONNECTION,
                true,
                "副本集问题，建议切换到其他节点",
                "检查副本集状态",
                2000 // 2秒延迟
            );
        }
        
        // 默认策略：延迟重试
        return new HandlingResult(
            Strategy.DELAYED_RETRY,
            true,
            "未知MongoDB错误，建议延迟重试",
            "检查MongoDB日志获取详细信息",
            5000 // 5秒延迟
        );
    }
    
    /**
     * 判断是否为网络错误
     */
    private boolean isNetworkError(Throwable cause, String message, String type) {
        return message.contains("connection") ||
               message.contains("network") ||
               message.contains("unreachable") ||
               message.contains("refused") ||
               type.contains("connection") ||
               type.contains("network");
    }
    
    /**
     * 判断是否为超时错误
     */
    private boolean isTimeoutError(Throwable cause, String message, String type) {
        return message.contains("timeout") ||
               message.contains("timed out") ||
               type.contains("timeout");
    }
    
    /**
     * 判断是否为认证错误
     */
    private boolean isAuthenticationError(Throwable cause, String message, String type) {
        return message.contains("authentication") ||
               message.contains("unauthorized") ||
               message.contains("access denied") ||
               message.contains("credential") ||
               type.contains("authentication");
    }
    
    /**
     * 判断是否为文档大小错误
     */
    private boolean isDocumentSizeError(Throwable cause, String message, String type) {
        return message.contains("document size") ||
               message.contains("bson size") ||
               message.contains("16mb") ||
               message.contains("too large");
    }
    
    /**
     * 判断是否为磁盘空间错误
     */
    private boolean isDiskSpaceError(Throwable cause, String message, String type) {
        return message.contains("disk") ||
               message.contains("space") ||
               message.contains("storage") ||
               message.contains("full");
    }
    
    /**
     * 判断是否为数据库锁定错误
     */
    private boolean isDatabaseLockError(Throwable cause, String message, String type) {
        return message.contains("lock") ||
               message.contains("locked") ||
               message.contains("blocking");
    }
    
    /**
     * 判断是否为连接池错误
     */
    private boolean isConnectionPoolError(Throwable cause, String message, String type) {
        return message.contains("pool") ||
               message.contains("exhausted") ||
               message.contains("max connections");
    }
    
    /**
     * 判断是否为索引错误
     */
    private boolean isIndexError(Throwable cause, String message, String type) {
        return message.contains("index") ||
               message.contains("key too large");
    }
    
    /**
     * 判断是否为副本集错误
     */
    private boolean isReplicaSetError(Throwable cause, String message, String type) {
        return message.contains("replica") ||
               message.contains("primary") ||
               message.contains("secondary") ||
               message.contains("election");
    }
    
    /**
     * 获取重试建议
     * 
     * @return 重试建议
     */
    public String getRetryAdvice() {
        HandlingResult result = analyze();
        
        switch (result.getStrategy()) {
            case IMMEDIATE_RETRY:
                return "建议立即重试，通常是临时性问题";
            case DELAYED_RETRY:
                return String.format("建议延迟%dms后重试，等待问题自动恢复", result.getRetryDelayMs());
            case SWITCH_CONNECTION:
                return "建议切换到备用MongoDB连接或节点";
            case FALLBACK_TO_LOCAL:
                return "建议降级到本地文件存储，避免数据丢失";
            case REJECT_OPERATION:
                return "建议拒绝操作，需要人工干预解决问题";
            default:
                return "未知策略，请检查系统状态";
        }
    }
    
    /**
     * 生成异常报告
     * 
     * @return 异常报告
     */
    public String generateReport() {
        HandlingResult result = analyze();
        
        StringBuilder report = new StringBuilder();
        report.append("=== MongoDB异常报告 ===\n");
        report.append(String.format("Session ID: %s\n", sessionId));
        report.append(String.format("操作类型: %s\n", operation));
        report.append(String.format("异常类型: %s\n", cause.getClass().getSimpleName()));
        report.append(String.format("错误消息: %s\n", cause.getMessage()));
        report.append(String.format("建议策略: %s\n", result.getStrategy()));
        report.append(String.format("处理原因: %s\n", result.getReason()));
        
        if (result.getSuggestion() != null) {
            report.append(String.format("具体建议: %s\n", result.getSuggestion()));
        }
        
        if (result.getRetryDelayMs() > 0) {
            report.append(String.format("重试延迟: %dms\n", result.getRetryDelayMs()));
        }
        
        report.append(String.format("重试建议: %s\n", getRetryAdvice()));
        report.append(String.format("可以继续: %s\n", result.canProceed() ? "是" : "否"));
        report.append("=====================\n");
        
        return report.toString();
    }
    
    /**
     * 创建对应的StorageException
     * 
     * @return StorageException
     */
    public StorageException createException() {
        return StorageException.mongoOperationFailed(sessionId, operation, cause);
    }
}