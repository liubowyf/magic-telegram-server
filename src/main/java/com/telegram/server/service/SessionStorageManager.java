package com.telegram.server.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.telegram.server.entity.TelegramSession;
import com.telegram.server.repository.TelegramSessionRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.IOException;
import java.util.Map;

/**
 * Session存储管理器
 * 统一管理session数据的存储，自动处理分片和压缩
 * 
 * @author liubo
 * @date 2025-01-19
 */
@Service
public class SessionStorageManager {

    private static final Logger logger = LoggerFactory.getLogger(SessionStorageManager.class);

    /**
     * 启用分片存储的阈值（8MB）
     */
    @Value("${session.storage.shard.threshold:8388608}")
    private long shardThreshold;

    /**
     * 存储版本
     */
    private static final String STORAGE_VERSION_V2 = "v2";
    private static final String STORAGE_VERSION_V1 = "v1";

    @Autowired
    private TelegramSessionRepository sessionRepository;

    @Autowired
    private SessionShardManager shardManager;

    @Autowired
    private ObjectMapper objectMapper;

    /**
     * 存储session数据
     * 自动判断是否需要分片存储
     * 
     * @param session session对象
     * @param databaseFiles 数据库文件数据
     * @param downloadedFiles 下载文件数据
     * @return 更新后的session对象
     * @throws IOException 存储过程中的IO异常
     */
    @Transactional
    public TelegramSession storeSession(TelegramSession session, 
                                       Map<String, String> databaseFiles, 
                                       Map<String, String> downloadedFiles) throws IOException {
        logger.info("开始存储session数据: sessionId={}", session.getId());

        // 计算数据大小
        String databaseFilesJson = serializeToJson(databaseFiles);
        String downloadedFilesJson = serializeToJson(downloadedFiles);
        
        long databaseFilesSize = databaseFilesJson != null ? databaseFilesJson.length() : 0;
        long downloadedFilesSize = downloadedFilesJson != null ? downloadedFilesJson.length() : 0;
        long totalSize = databaseFilesSize + downloadedFilesSize;

        logger.info("数据大小统计: databaseFiles={} bytes, downloadedFiles={} bytes, total={} bytes", 
                databaseFilesSize, downloadedFilesSize, totalSize);

        if (totalSize > shardThreshold) {
            // 使用分片存储
            return storeWithSharding(session, databaseFilesJson, downloadedFilesJson);
        } else {
            // 使用传统存储
            return storeWithoutSharding(session, databaseFiles, downloadedFiles);
        }
    }

    /**
     * 读取session数据
     * 自动判断存储方式并读取数据
     * 
     * @param sessionId session ID
     * @return session对象，包含完整的文件数据
     * @throws IOException 读取过程中的IO异常
     */
    public TelegramSession loadSession(String sessionId) throws IOException {
        logger.debug("开始加载session数据: sessionId={}", sessionId);

        TelegramSession session = sessionRepository.findById(sessionId).orElse(null);
        if (session == null) {
            logger.debug("Session不存在: sessionId={}", sessionId);
            return null;
        }

        if (STORAGE_VERSION_V2.equals(session.getStorageVersion()) && 
            Boolean.TRUE.equals(session.getShardEnabled())) {
            // 从分片存储读取
            return loadFromSharding(session);
        } else {
            // 从传统存储读取
            logger.debug("使用传统存储方式加载session: sessionId={}", sessionId);
            return session;
        }
    }

    /**
     * 删除session数据
     * 同时删除主文档和分片数据
     * 
     * @param sessionId session ID
     * @return 是否删除成功
     */
    @Transactional
    public boolean deleteSession(String sessionId) {
        logger.info("开始删除session数据: sessionId={}", sessionId);

        try {
            // 删除分片数据
            long deletedShards = shardManager.deleteShards(sessionId, null);
            if (deletedShards > 0) {
                logger.info("删除分片数据: sessionId={}, 分片数量={}", sessionId, deletedShards);
            }

            // 删除主文档
            sessionRepository.deleteById(sessionId);
            
            logger.info("Session删除完成: sessionId={}", sessionId);
            return true;
        } catch (Exception e) {
            logger.error("删除session失败: sessionId={}", sessionId, e);
            return false;
        }
    }

    /**
     * 获取session存储统计信息
     * 
     * @param sessionId session ID
     * @return 存储统计信息
     */
    public Map<String, Object> getStorageStatistics(String sessionId) {
        TelegramSession session = sessionRepository.findById(sessionId).orElse(null);
        if (session == null) {
            return Map.of("error", "Session not found");
        }

        Map<String, Object> stats = Map.of(
                "sessionId", sessionId,
                "storageVersion", session.getStorageVersion(),
                "shardEnabled", Boolean.TRUE.equals(session.getShardEnabled()),
                "compressionType", session.getCompressionType(),
                "originalSize", session.getOriginalSize(),
                "compressedSize", session.getCompressedSize()
        );

        if (Boolean.TRUE.equals(session.getShardEnabled())) {
            // 添加分片统计信息
            Map<String, Object> databaseStats = shardManager.getShardStatistics(
                    sessionId, SessionShardManager.SHARD_TYPE_DATABASE_FILES);
            Map<String, Object> downloadStats = shardManager.getShardStatistics(
                    sessionId, SessionShardManager.SHARD_TYPE_DOWNLOADED_FILES);
            
            stats.put("databaseFilesShards", databaseStats);
            stats.put("downloadedFilesShards", downloadStats);
        }

        return stats;
    }

    /**
     * 使用分片存储方式存储session
     * 
     * @param session session对象
     * @param databaseFilesJson 数据库文件JSON
     * @param downloadedFilesJson 下载文件JSON
     * @return 更新后的session对象
     * @throws IOException 存储过程中的IO异常
     */
    private TelegramSession storeWithSharding(TelegramSession session, 
                                             String databaseFilesJson, 
                                             String downloadedFilesJson) throws IOException {
        logger.info("使用分片存储方式: sessionId={}", session.getId());

        // 存储数据库文件分片
        SessionShardManager.ShardStorageResult databaseResult = null;
        if (databaseFilesJson != null && !databaseFilesJson.isEmpty()) {
            databaseResult = shardManager.storeShards(
                    session.getId(), SessionShardManager.SHARD_TYPE_DATABASE_FILES, databaseFilesJson);
        }

        // 存储下载文件分片
        SessionShardManager.ShardStorageResult downloadResult = null;
        if (downloadedFilesJson != null && !downloadedFilesJson.isEmpty()) {
            downloadResult = shardManager.storeShards(
                    session.getId(), SessionShardManager.SHARD_TYPE_DOWNLOADED_FILES, downloadedFilesJson);
        }

        // 更新session元数据
        session.setStorageVersion(STORAGE_VERSION_V2);
        session.setShardEnabled(true);
        session.setDatabaseFiles(null); // 清空原始数据
        session.setDownloadedFiles(null); // 清空原始数据

        // 设置分片引用
        java.util.List<String> allShardRefs = new java.util.ArrayList<>();
        if (databaseResult != null) {
            allShardRefs.addAll(databaseResult.getShardRefs());
        }
        if (downloadResult != null) {
            allShardRefs.addAll(downloadResult.getShardRefs());
        }
        session.setShardRefs(allShardRefs);

        // 设置压缩和大小信息
        long totalOriginalSize = 0;
        long totalCompressedSize = 0;
        String compressionType = "none";
        
        if (databaseResult != null) {
            totalOriginalSize += databaseResult.getTotalOriginalSize();
            totalCompressedSize += databaseResult.getTotalCompressedSize();
            compressionType = databaseResult.getCompressionType();
        }
        if (downloadResult != null) {
            totalOriginalSize += downloadResult.getTotalOriginalSize();
            totalCompressedSize += downloadResult.getTotalCompressedSize();
            if ("none".equals(compressionType)) {
                compressionType = downloadResult.getCompressionType();
            }
        }

        session.setOriginalSize(totalOriginalSize);
        session.setCompressedSize(totalCompressedSize);
        session.setCompressionType(compressionType);

        // 计算完整性哈希
        String integrityData = (databaseFilesJson != null ? databaseFilesJson : "") + 
                              (downloadedFilesJson != null ? downloadedFilesJson : "");
        session.setIntegrityHash(calculateSimpleHash(integrityData));

        // 保存session
        session = sessionRepository.save(session);
        
        logger.info("分片存储完成: sessionId={}, 原始大小={} bytes, 压缩后大小={} bytes, 分片数={}", 
                session.getId(), totalOriginalSize, totalCompressedSize, allShardRefs.size());

        return session;
    }

    /**
     * 使用传统存储方式存储session
     * 
     * @param session session对象
     * @param databaseFiles 数据库文件数据
     * @param downloadedFiles 下载文件数据
     * @return 更新后的session对象
     */
    private TelegramSession storeWithoutSharding(TelegramSession session, 
                                                Map<String, String> databaseFiles, 
                                                Map<String, String> downloadedFiles) {
        logger.info("使用传统存储方式: sessionId={}", session.getId());

        session.setStorageVersion(STORAGE_VERSION_V1);
        session.setShardEnabled(false);
        session.setDatabaseFiles(databaseFiles);
        session.setDownloadedFiles(downloadedFiles);
        session.setShardRefs(null);
        session.setCompressionType("none");
        
        // 计算大小
        long totalSize = 0;
        if (databaseFiles != null) {
            totalSize += serializeToJson(databaseFiles).length();
        }
        if (downloadedFiles != null) {
            totalSize += serializeToJson(downloadedFiles).length();
        }
        session.setOriginalSize(totalSize);
        session.setCompressedSize(totalSize);

        return sessionRepository.save(session);
    }

    /**
     * 从分片存储加载session
     * 
     * @param session session对象
     * @return 包含完整数据的session对象
     * @throws IOException 读取过程中的IO异常
     */
    private TelegramSession loadFromSharding(TelegramSession session) throws IOException {
        logger.debug("从分片存储加载session: sessionId={}", session.getId());

        // 读取数据库文件分片
        String databaseFilesJson = shardManager.readShards(
                session.getId(), SessionShardManager.SHARD_TYPE_DATABASE_FILES);
        if (databaseFilesJson != null) {
            Map<String, String> databaseFiles = deserializeFromJson(databaseFilesJson, Map.class);
            session.setDatabaseFiles(databaseFiles);
        }

        // 读取下载文件分片
        String downloadedFilesJson = shardManager.readShards(
                session.getId(), SessionShardManager.SHARD_TYPE_DOWNLOADED_FILES);
        if (downloadedFilesJson != null) {
            Map<String, String> downloadedFiles = deserializeFromJson(downloadedFilesJson, Map.class);
            session.setDownloadedFiles(downloadedFiles);
        }

        logger.debug("分片数据加载完成: sessionId={}", session.getId());
        return session;
    }

    /**
     * 序列化对象为JSON字符串
     * 
     * @param object 对象
     * @return JSON字符串
     */
    private String serializeToJson(Object object) {
        if (object == null) {
            return null;
        }
        try {
            return objectMapper.writeValueAsString(object);
        } catch (JsonProcessingException e) {
            logger.error("JSON序列化失败", e);
            return null;
        }
    }

    /**
     * 从JSON字符串反序列化对象
     * 
     * @param json JSON字符串
     * @param clazz 目标类型
     * @return 反序列化后的对象
     */
    private <T> T deserializeFromJson(String json, Class<T> clazz) {
        if (json == null || json.isEmpty()) {
            return null;
        }
        try {
            return objectMapper.readValue(json, clazz);
        } catch (JsonProcessingException e) {
            logger.error("JSON反序列化失败", e);
            return null;
        }
    }

    /**
     * 计算简单哈希值
     * 
     * @param data 数据
     * @return 哈希值
     */
    private String calculateSimpleHash(String data) {
        if (data == null || data.isEmpty()) {
            return "";
        }
        return String.valueOf(data.hashCode());
    }
}