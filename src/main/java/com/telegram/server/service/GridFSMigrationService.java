package com.telegram.server.service;

import com.telegram.server.entity.TelegramSession;
import com.telegram.server.repository.TelegramSessionRepository;
import com.telegram.server.service.gridfs.GridFSService;
import com.telegram.server.service.gridfs.GridFSCompressionService;
import com.telegram.server.service.gridfs.GridFSIntegrityService;
import org.bson.Document;
import org.bson.types.ObjectId;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.IOException;
import java.util.List;
import java.util.Map;

/**
 * GridFS迁移服务
 * 负责将现有的分片存储数据迁移到GridFS
 * 
 * @author liubo
 * @date 2025-01-20
 */
@Service
@ConditionalOnProperty(name = "session.storage.gridfs.migration.enabled", havingValue = "true")
public class GridFSMigrationService {

    private static final Logger logger = LoggerFactory.getLogger(GridFSMigrationService.class);

    /**
     * 迁移策略：读时自动迁移
     */
    @Value("${session.storage.gridfs.migration.strategy:on-read}")
    private String migrationStrategy;

    /**
     * 批处理大小
     */
    @Value("${session.storage.gridfs.migration.batchSize:100}")
    private int batchSize;

    @Autowired
    private TelegramSessionRepository sessionRepository;

    @Autowired
    private SessionShardManager shardManager;

    @Autowired
    private GridFSService gridfsService;

    @Autowired
    private GridFSCompressionService compressionService;

    @Autowired
    private GridFSIntegrityService integrityService;

    /**
     * 存储版本常量
     */
    private static final String STORAGE_VERSION_V1 = "v1";
    private static final String STORAGE_VERSION_V2 = "v2";
    private static final String STORAGE_VERSION_GRIDFS = "gridfs";

    /**
     * 判断session是否需要迁移
     * 
     * @param session session对象
     * @return 是否需要迁移
     */
    public boolean shouldMigrate(TelegramSession session) {
        if (!"on-read".equals(migrationStrategy)) {
            return false;
        }
        
        String storageVersion = session.getStorageVersion();
        return STORAGE_VERSION_V1.equals(storageVersion) || 
               (STORAGE_VERSION_V2.equals(storageVersion) && Boolean.TRUE.equals(session.getShardEnabled()));
    }

    /**
     * 迁移单个session到GridFS
     * 
     * @param session 需要迁移的session
     * @return 迁移后的session
     * @throws IOException 迁移过程中的IO异常
     */
    @Transactional
    public TelegramSession migrateSession(TelegramSession session) 
            throws IOException, 
                   GridFSCompressionService.CompressionException, 
                   GridFSIntegrityService.IntegrityException, 
                   GridFSService.GridFSException {
        String sessionId = session.getId();
        logger.info("开始迁移session到GridFS: sessionId={}, 当前版本={}", sessionId, session.getStorageVersion());

        try {
            if (STORAGE_VERSION_V1.equals(session.getStorageVersion())) {
                // 从传统存储迁移
                return migrateFromTraditionalStorage(session);
            } else if (STORAGE_VERSION_V2.equals(session.getStorageVersion()) && 
                      Boolean.TRUE.equals(session.getShardEnabled())) {
                // 从分片存储迁移
                return migrateFromShardStorage(session);
            } else {
                logger.warn("不支持的存储版本迁移: sessionId={}, 版本={}", sessionId, session.getStorageVersion());
                return session;
            }
        } catch (Exception e) {
            logger.error("迁移session失败: sessionId={}", sessionId, e);
            throw new IOException("迁移session失败: " + e.getMessage(), e);
        }
    }

    /**
     * 批量迁移sessions到GridFS
     * 
     * @return 迁移结果统计
     */
    @Transactional
    public MigrationResult batchMigrate() {
        logger.info("开始批量迁移sessions到GridFS, 批处理大小={}", batchSize);
        
        MigrationResult result = new MigrationResult();
        int offset = 0;
        
        while (true) {
            // 查找需要迁移的sessions
            List<TelegramSession> sessions = findSessionsToMigrate(offset, batchSize);
            if (sessions.isEmpty()) {
                break;
            }
            
            for (TelegramSession session : sessions) {
                try {
                    migrateSession(session);
                    result.incrementSuccess();
                    logger.debug("Session迁移成功: sessionId={}", session.getId());
                } catch (Exception e) {
                    result.incrementFailure();
                    result.addError(session.getId(), e.getMessage());
                    logger.error("Session迁移失败: sessionId={}", session.getId(), e);
                }
            }
            
            offset += batchSize;
            
            // 避免无限循环
            if (sessions.size() < batchSize) {
                break;
            }
        }
        
        logger.info("批量迁移完成: 成功={}, 失败={}", result.getSuccessCount(), result.getFailureCount());
        return result;
    }

    /**
     * 从传统存储迁移
     * 
     * @param session session对象
     * @return 迁移后的session
     * @throws IOException 迁移过程中的IO异常
     */
    private TelegramSession migrateFromTraditionalStorage(TelegramSession session) 
            throws IOException, 
                   GridFSCompressionService.CompressionException, 
                   GridFSIntegrityService.IntegrityException, 
                   GridFSService.GridFSException {
        logger.debug("从传统存储迁移: sessionId={}", session.getId());
        
        Map<String, String> databaseFiles = session.getDatabaseFiles();
        Map<String, String> downloadedFiles = session.getDownloadedFiles();
        
        return migrateToGridFS(session, databaseFiles, downloadedFiles);
    }

    /**
     * 从分片存储迁移
     * 
     * @param session session对象
     * @return 迁移后的session
     * @throws IOException 迁移过程中的IO异常
     */
    private TelegramSession migrateFromShardStorage(TelegramSession session) throws IOException {
        logger.debug("从分片存储迁移: sessionId={}", session.getId());
        
        try {
            // 读取分片数据
            String databaseFilesJson = shardManager.readShards(
                    session.getId(), SessionShardManager.SHARD_TYPE_DATABASE_FILES);
            String downloadedFilesJson = shardManager.readShards(
                    session.getId(), SessionShardManager.SHARD_TYPE_DOWNLOADED_FILES);
            
            Map<String, String> databaseFiles = null;
            Map<String, String> downloadedFiles = null;
            
            if (databaseFilesJson != null && !databaseFilesJson.isEmpty()) {
                databaseFiles = deserializeFromJson(databaseFilesJson);
            }
            if (downloadedFilesJson != null && !downloadedFilesJson.isEmpty()) {
                downloadedFiles = deserializeFromJson(downloadedFilesJson);
            }
            
            // 迁移到GridFS
            TelegramSession migratedSession = migrateToGridFS(session, databaseFiles, downloadedFiles);
            
            // 清理旧的分片数据
            cleanupShardData(session.getId());
            
            return migratedSession;
        } catch (Exception e) {
            logger.error("从分片存储迁移失败: sessionId={}", session.getId(), e);
            throw new IOException("从分片存储迁移失败", e);
        }
    }

    /**
     * 迁移数据到GridFS
     * 
     * @param session session对象
     * @param databaseFiles 数据库文件数据
     * @param downloadedFiles 下载文件数据
     * @return 迁移后的session
     * @throws IOException 迁移过程中的IO异常
     */
    private TelegramSession migrateToGridFS(TelegramSession session, 
                                           Map<String, String> databaseFiles, 
                                           Map<String, String> downloadedFiles) 
            throws IOException, 
                   GridFSCompressionService.CompressionException, 
                   GridFSIntegrityService.IntegrityException, 
                   GridFSService.GridFSException {
        
        long totalOriginalSize = 0;
        long totalCompressedSize = 0;
        String compressionType = "none";
        String integrityHash = "";

        // 迁移数据库文件到GridFS
        if (databaseFiles != null && !databaseFiles.isEmpty()) {
            String databaseFilesJson = serializeToJson(databaseFiles);
            String filename = "session_" + session.getId() + "_database_files.json";
            
            // 压缩数据
            GridFSCompressionService.CompressionResult compressionResult = compressionService.compress(databaseFilesJson.getBytes("UTF-8"));
            byte[] compressedData = compressionResult.getData();
            compressionType = compressionResult.isCompressed() ? "gzip" : "none";
            
            // 计算完整性哈希
            String dbIntegrityHash = integrityService.calculateChecksum(databaseFilesJson.getBytes("UTF-8"));
            
            // 存储到GridFS
            Map<String, Object> metadata = Map.of(
                    "sessionId", session.getId(),
                    "dataType", "database_files",
                    "originalSize", databaseFilesJson.getBytes("UTF-8").length,
                    "compressedSize", compressedData.length,
                    "compressionType", compressionType,
                    "integrityHash", dbIntegrityHash,
                    "migrated", true
            );
            
            Document metadataDoc = new Document(metadata);
            ObjectId fileId = gridfsService.storeFile(GridFSService.BucketType.SESSION, filename, compressedData, metadataDoc);
            session.setDatabaseFilesGridfsId(fileId.toString());
            
            totalOriginalSize += databaseFilesJson.getBytes("UTF-8").length;
            totalCompressedSize += compressedData.length;
            integrityHash += dbIntegrityHash;
        }

        // 迁移下载文件到GridFS
        if (downloadedFiles != null && !downloadedFiles.isEmpty()) {
            String downloadedFilesJson = serializeToJson(downloadedFiles);
            String filename = "session_" + session.getId() + "_downloaded_files.json";
            
            // 压缩数据
            GridFSCompressionService.CompressionResult dlCompressionResult = compressionService.compress(downloadedFilesJson.getBytes("UTF-8"));
            byte[] compressedData = dlCompressionResult.getData();
            if ("none".equals(compressionType)) {
                compressionType = dlCompressionResult.isCompressed() ? "gzip" : "none";
            }
            
            // 计算完整性哈希
            String dlIntegrityHash = integrityService.calculateChecksum(downloadedFilesJson.getBytes("UTF-8"));
            
            // 存储到GridFS
            Map<String, Object> metadata = Map.of(
                    "sessionId", session.getId(),
                    "dataType", "downloaded_files",
                    "originalSize", downloadedFilesJson.getBytes("UTF-8").length,
                    "compressedSize", compressedData.length,
                    "compressionType", compressionType,
                    "integrityHash", dlIntegrityHash,
                    "migrated", true
            );
            
            Document metadataDoc = new Document(metadata);
            ObjectId fileId = gridfsService.storeFile(GridFSService.BucketType.SESSION, filename, compressedData, metadataDoc);
            session.setDownloadedFilesGridfsId(fileId.toString());
            
            totalOriginalSize += downloadedFilesJson.getBytes("UTF-8").length;
            totalCompressedSize += compressedData.length;
            integrityHash += dlIntegrityHash;
        }

        // 更新session元数据
        session.setStorageVersion(STORAGE_VERSION_GRIDFS);
        session.setDatabaseFiles(null); // 清空原始数据
        session.setDownloadedFiles(null); // 清空原始数据
        session.setShardEnabled(null); // 清空分片相关字段
        session.setShardRefs(null);
        
        // 设置压缩和大小信息
        session.setOriginalSize(totalOriginalSize);
        session.setCompressedSize(totalCompressedSize);
        session.setCompressionType(compressionType);
        
        // 计算整体完整性哈希
        session.setIntegrityHash(integrityService.calculateChecksum(integrityHash.getBytes("UTF-8")));

        // 保存session
        session = sessionRepository.save(session);
        
        logger.info("迁移到GridFS完成: sessionId={}, 原始大小={} bytes, 压缩后大小={} bytes", 
                session.getId(), totalOriginalSize, totalCompressedSize);

        return session;
    }

    /**
     * 查找需要迁移的sessions
     * 
     * @param offset 偏移量
     * @param limit 限制数量
     * @return 需要迁移的sessions列表
     */
    private List<TelegramSession> findSessionsToMigrate(int offset, int limit) {
        // 这里需要根据实际的Repository接口实现
        // 暂时返回空列表，实际实现时需要添加相应的查询方法
        return List.of();
    }

    /**
     * 清理分片数据
     * 
     * @param sessionId session ID
     */
    private void cleanupShardData(String sessionId) {
        try {
            long deletedShards = shardManager.deleteShards(sessionId, null);
            if (deletedShards > 0) {
                logger.info("清理分片数据完成: sessionId={}, 删除分片数量={}", sessionId, deletedShards);
            }
        } catch (Exception e) {
            logger.warn("清理分片数据失败: sessionId={}", sessionId, e);
        }
    }

    /**
     * 序列化对象为JSON字符串
     * 
     * @param object 对象
     * @return JSON字符串
     */
    private String serializeToJson(Object object) {
        try {
            return new com.fasterxml.jackson.databind.ObjectMapper().writeValueAsString(object);
        } catch (Exception e) {
            logger.error("JSON序列化失败", e);
            return null;
        }
    }

    /**
     * 从JSON字符串反序列化对象
     * 
     * @param json JSON字符串
     * @return 反序列化后的对象
     */
    @SuppressWarnings("unchecked")
    private Map<String, String> deserializeFromJson(String json) {
        try {
            return new com.fasterxml.jackson.databind.ObjectMapper().readValue(json, Map.class);
        } catch (Exception e) {
            logger.error("JSON反序列化失败", e);
            return null;
        }
    }

    /**
     * 迁移结果统计
     */
    public static class MigrationResult {
        private int successCount = 0;
        private int failureCount = 0;
        private final Map<String, String> errors = new java.util.HashMap<>();

        public void incrementSuccess() {
            successCount++;
        }

        public void incrementFailure() {
            failureCount++;
        }

        public void addError(String sessionId, String error) {
            errors.put(sessionId, error);
        }

        public int getSuccessCount() {
            return successCount;
        }

        public int getFailureCount() {
            return failureCount;
        }

        public Map<String, String> getErrors() {
            return errors;
        }

        @Override
        public String toString() {
            return String.format("MigrationResult{成功=%d, 失败=%d, 错误数=%d}", 
                    successCount, failureCount, errors.size());
        }
    }
}