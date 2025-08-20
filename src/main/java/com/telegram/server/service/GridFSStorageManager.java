package com.telegram.server.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.telegram.server.entity.TelegramSession;
import com.telegram.server.repository.TelegramSessionRepository;
import com.telegram.server.service.gridfs.GridFSService;
import com.telegram.server.service.gridfs.GridFSCompressionService;
import com.telegram.server.service.gridfs.GridFSIntegrityService;
// import com.telegram.server.service.gridfs.GridFSMigrationService; // TODO: 待实现
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.bson.types.ObjectId;
import org.bson.Document;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

import com.telegram.server.service.gridfs.GridFSService.BucketType;
import com.telegram.server.service.gridfs.GridFSService.GridFSException;
import com.telegram.server.service.gridfs.GridFSCompressionService.CompressionResult;
import com.telegram.server.service.gridfs.GridFSCompressionService.CompressionException;
import com.telegram.server.service.gridfs.GridFSIntegrityService.IntegrityResult;
import com.telegram.server.service.gridfs.GridFSIntegrityService.IntegrityException;
import com.mongodb.client.gridfs.model.GridFSFile;

/**
 * GridFS存储管理器
 * 使用GridFS替代自定义分片机制，提供统一的session数据存储
 * 
 * @author liubo
 * @date 2025-01-20
 */
@Service
@ConditionalOnProperty(name = "session.storage.strategy", havingValue = "gridfs")
public class GridFSStorageManager {

    private static final Logger logger = LoggerFactory.getLogger(GridFSStorageManager.class);

    /**
     * 启用GridFS存储的阈值（8MB）
     */
    @Value("${session.storage.shard.threshold:8388608}")
    private long gridfsThreshold;

    /**
     * 存储版本
     */
    private static final String STORAGE_VERSION_GRIDFS = "gridfs";
    private static final String STORAGE_VERSION_V1 = "v1";

    @Autowired
    private TelegramSessionRepository sessionRepository;

    @Autowired
    private GridFSService gridfsService;

    @Autowired
    private GridFSCompressionService compressionService;

    @Autowired
    private GridFSIntegrityService integrityService;

    // @Autowired
    // private GridFSMigrationService migrationService; // TODO: 待实现

    @Autowired
    private ObjectMapper objectMapper;

    /**
     * 存储session数据
     * 自动判断是否需要GridFS存储
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
        logger.info("开始存储session数据到GridFS: sessionId={}", session.getId());

        // 计算数据大小
        String databaseFilesJson = serializeToJson(databaseFiles);
        String downloadedFilesJson = serializeToJson(downloadedFiles);
        
        long databaseFilesSize = databaseFilesJson != null ? databaseFilesJson.length() : 0;
        long downloadedFilesSize = downloadedFilesJson != null ? downloadedFilesJson.length() : 0;
        long totalSize = databaseFilesSize + downloadedFilesSize;

        logger.info("数据大小统计: databaseFiles={} bytes, downloadedFiles={} bytes, total={} bytes", 
                databaseFilesSize, downloadedFilesSize, totalSize);

        if (totalSize > gridfsThreshold) {
            // 使用GridFS存储
            return storeWithGridFS(session, databaseFilesJson, downloadedFilesJson);
        } else {
            // 使用传统存储
            return storeWithoutGridFS(session, databaseFiles, downloadedFiles);
        }
    }

    /**
     * 读取session数据
     * 自动判断存储方式并读取数据，支持自动迁移
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

        // TODO: 检查是否需要迁移
        // if (migrationService.shouldMigrate(session)) {
        //     logger.info("检测到需要迁移的session: sessionId={}, 当前版本={}", 
        //             sessionId, session.getStorageVersion());
        //     session = migrationService.migrateSession(session);
        // }

        if (STORAGE_VERSION_GRIDFS.equals(session.getStorageVersion())) {
            // 从GridFS读取
            return loadFromGridFS(session);
        } else {
            // 从传统存储读取
            logger.debug("使用传统存储方式加载session: sessionId={}", sessionId);
            return session;
        }
    }

    /**
     * 删除session数据
     * 同时删除主文档和GridFS文件
     * 
     * @param sessionId session ID
     * @return 是否删除成功
     */
    @Transactional
    public boolean deleteSession(String sessionId) {
        logger.info("开始删除session数据: sessionId={}", sessionId);

        try {
            TelegramSession session = sessionRepository.findById(sessionId).orElse(null);
            if (session != null && STORAGE_VERSION_GRIDFS.equals(session.getStorageVersion())) {
                // 删除GridFS文件
                if (session.getDatabaseFilesGridfsId() != null) {
                    gridfsService.deleteFile(BucketType.SESSION, new ObjectId(session.getDatabaseFilesGridfsId()));
                    logger.debug("删除数据库文件GridFS: sessionId={}, fileId={}", 
                            sessionId, session.getDatabaseFilesGridfsId());
                }
                if (session.getDownloadedFilesGridfsId() != null) {
                    gridfsService.deleteFile(BucketType.SESSION, new ObjectId(session.getDownloadedFilesGridfsId()));
                    logger.debug("删除下载文件GridFS: sessionId={}, fileId={}", 
                            sessionId, session.getDownloadedFilesGridfsId());
                }
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

        Map<String, Object> stats = new HashMap<>();
        stats.put("sessionId", sessionId);
        stats.put("storageVersion", session.getStorageVersion());
        stats.put("compressionType", session.getCompressionType());
        stats.put("originalSize", session.getOriginalSize());
        stats.put("compressedSize", session.getCompressedSize());
        stats.put("integrityHash", session.getIntegrityHash());

        if (STORAGE_VERSION_GRIDFS.equals(session.getStorageVersion())) {
            stats.put("gridfsEnabled", true);
            stats.put("databaseFilesGridfsId", session.getDatabaseFilesGridfsId());
            stats.put("downloadedFilesGridfsId", session.getDownloadedFilesGridfsId());
            
            // 添加GridFS文件统计信息
            try {
                if (session.getDatabaseFilesGridfsId() != null) {
                    Optional<GridFSFile> dbFileOpt = gridfsService.getFileMetadata(
                            BucketType.SESSION, new ObjectId(session.getDatabaseFilesGridfsId()));
                    Document dbFileInfo = dbFileOpt.map(GridFSFile::getMetadata).orElse(new Document());
                    stats.put("databaseFilesInfo", dbFileInfo);
                }
                if (session.getDownloadedFilesGridfsId() != null) {
                    Optional<GridFSFile> dlFileOpt = gridfsService.getFileMetadata(
                            BucketType.SESSION, new ObjectId(session.getDownloadedFilesGridfsId()));
                    Document dlFileInfo = dlFileOpt.map(GridFSFile::getMetadata).orElse(new Document());
                    stats.put("downloadedFilesInfo", dlFileInfo);
                }
            } catch (Exception e) {
                logger.warn("获取GridFS文件信息失败: sessionId={}", sessionId, e);
                stats.put("gridfsInfoError", e.getMessage());
            }
        } else {
            stats.put("gridfsEnabled", false);
        }

        return stats;
    }

    /**
     * 使用GridFS存储方式存储session
     * 
     * @param session session对象
     * @param databaseFilesJson 数据库文件JSON
     * @param downloadedFilesJson 下载文件JSON
     * @return 更新后的session对象
     * @throws IOException 存储过程中的IO异常
     */
    private TelegramSession storeWithGridFS(TelegramSession session, 
                                           String databaseFilesJson, 
                                           String downloadedFilesJson) throws IOException {
        logger.info("使用GridFS存储方式: sessionId={}", session.getId());

        long totalOriginalSize = 0;
        long totalCompressedSize = 0;
        String compressionType = "none";
        String integrityHash = "";

        // 存储数据库文件到GridFS
        if (databaseFilesJson != null && !databaseFilesJson.isEmpty()) {
            String filename = "session_" + session.getId() + "_database_files.json";
            
            // 压缩数据
            CompressionResult compressionResult;
            try {
                compressionResult = compressionService.compress(databaseFilesJson.getBytes("UTF-8"));
            } catch (CompressionException e) {
                logger.error("压缩数据库文件失败: sessionId={}", session.getId(), e);
                throw new IOException("压缩数据库文件失败", e);
            }
            byte[] compressedData = compressionResult.getData();
            compressionType = compressionResult.isCompressed() ? "gzip" : "none";
            
            // 计算完整性哈希
            String dbIntegrityHash;
            try {
                dbIntegrityHash = integrityService.calculateChecksum(databaseFilesJson.getBytes("UTF-8"));
            } catch (IntegrityException e) {
                logger.error("计算数据库文件哈希失败: sessionId={}", session.getId(), e);
                throw new IOException("计算数据库文件哈希失败", e);
            }
            
            // 存储到GridFS
            Map<String, Object> metadata = Map.of(
                    "sessionId", session.getId(),
                    "dataType", "database_files",
                    "originalSize", databaseFilesJson.getBytes("UTF-8").length,
                    "compressedSize", compressedData.length,
                    "compressionType", compressionType,
                    "integrityHash", dbIntegrityHash
            );
            
            Document metadataDoc = new Document();
            for (Map.Entry<String, Object> entry : metadata.entrySet()) {
                metadataDoc.append(entry.getKey(), entry.getValue());
            }
            ObjectId gridfsId;
            try {
                gridfsId = gridfsService.storeFile(BucketType.SESSION, filename, compressedData, metadataDoc);
            } catch (GridFSException e) {
                logger.error("存储数据库文件到GridFS失败: sessionId={}", session.getId(), e);
                throw new IOException("存储数据库文件到GridFS失败", e);
            }
            String fileId = gridfsId.toString();
            session.setDatabaseFilesGridfsId(fileId);
            
            totalOriginalSize += databaseFilesJson.getBytes("UTF-8").length;
            totalCompressedSize += compressedData.length;
            integrityHash += dbIntegrityHash;
            
            logger.debug("数据库文件存储到GridFS: sessionId={}, fileId={}, 原始大小={}, 压缩后大小={}", 
                    session.getId(), fileId, databaseFilesJson.getBytes("UTF-8").length, compressedData.length);
        }

        // 存储下载文件到GridFS
        if (downloadedFilesJson != null && !downloadedFilesJson.isEmpty()) {
            String filename = "session_" + session.getId() + "_downloaded_files.json";
            
            // 压缩数据
            CompressionResult compressionResult;
            try {
                compressionResult = compressionService.compress(downloadedFilesJson.getBytes("UTF-8"));
            } catch (CompressionException e) {
                logger.error("压缩下载文件失败: sessionId={}", session.getId(), e);
                throw new IOException("压缩下载文件失败", e);
            }
            byte[] compressedData = compressionResult.getData();
            if ("none".equals(compressionType)) {
                compressionType = compressionResult.isCompressed() ? "gzip" : "none";
            }
            
            // 计算完整性哈希
            String dlIntegrityHash;
            try {
                dlIntegrityHash = integrityService.calculateChecksum(downloadedFilesJson.getBytes("UTF-8"));
            } catch (IntegrityException e) {
                logger.error("计算下载文件哈希失败: sessionId={}", session.getId(), e);
                throw new IOException("计算下载文件哈希失败", e);
            }
            
            // 存储到GridFS
            Map<String, Object> metadata = Map.of(
                    "sessionId", session.getId(),
                    "dataType", "downloaded_files",
                    "originalSize", downloadedFilesJson.getBytes("UTF-8").length,
                    "compressedSize", compressedData.length,
                    "compressionType", compressionType,
                    "integrityHash", dlIntegrityHash
            );
            
            Document metadataDoc = new Document();
            for (Map.Entry<String, Object> entry : metadata.entrySet()) {
                metadataDoc.append(entry.getKey(), entry.getValue());
            }
            ObjectId gridfsId;
            try {
                gridfsId = gridfsService.storeFile(BucketType.SESSION, filename, compressedData, metadataDoc);
            } catch (GridFSException e) {
                logger.error("存储下载文件到GridFS失败: sessionId={}", session.getId(), e);
                throw new IOException("存储下载文件到GridFS失败", e);
            }
            String fileId = gridfsId.toString();
            session.setDownloadedFilesGridfsId(fileId);
            
            totalOriginalSize += downloadedFilesJson.getBytes("UTF-8").length;
            totalCompressedSize += compressedData.length;
            integrityHash += dlIntegrityHash;
            
            logger.debug("下载文件存储到GridFS: sessionId={}, fileId={}, 原始大小={}, 压缩后大小={}", 
                    session.getId(), fileId, downloadedFilesJson.getBytes("UTF-8").length, compressedData.length);
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
        try {
            session.setIntegrityHash(integrityService.calculateChecksum(integrityHash.getBytes("UTF-8")));
        } catch (IntegrityException e) {
            logger.error("计算整体完整性哈希失败: sessionId={}", session.getId(), e);
            throw new IOException("计算整体完整性哈希失败", e);
        }

        // 保存session
        session = sessionRepository.save(session);
        
        logger.info("GridFS存储完成: sessionId={}, 原始大小={} bytes, 压缩后大小={} bytes, 压缩率={:.2f}%", 
                session.getId(), totalOriginalSize, totalCompressedSize, 
                totalOriginalSize > 0 ? (1.0 - (double)totalCompressedSize / totalOriginalSize) * 100 : 0);

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
    private TelegramSession storeWithoutGridFS(TelegramSession session, 
                                              Map<String, String> databaseFiles, 
                                              Map<String, String> downloadedFiles) {
        logger.info("使用传统存储方式: sessionId={}", session.getId());

        session.setStorageVersion(STORAGE_VERSION_V1);
        session.setDatabaseFiles(databaseFiles);
        session.setDownloadedFiles(downloadedFiles);
        session.setDatabaseFilesGridfsId(null);
        session.setDownloadedFilesGridfsId(null);
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
     * 从GridFS加载session
     * 
     * @param session session对象
     * @return 包含完整数据的session对象
     * @throws IOException 读取过程中的IO异常
     */
    private TelegramSession loadFromGridFS(TelegramSession session) throws IOException {
        logger.debug("从GridFS加载session: sessionId={}", session.getId());

        // 读取数据库文件
        if (session.getDatabaseFilesGridfsId() != null) {
            try {
                Optional<byte[]> compressedDataOpt = gridfsService.readFile(BucketType.SESSION, new ObjectId(session.getDatabaseFilesGridfsId()));
                if (!compressedDataOpt.isPresent()) {
                    logger.warn("无法读取数据库文件GridFS: sessionId={}, fileId={}", session.getId(), session.getDatabaseFilesGridfsId());
                    return session;
                }
                byte[] compressedData = compressedDataOpt.get();
                
                // 检查是否压缩
                Optional<GridFSFile> metadataOpt = gridfsService.getFileMetadata(
                        BucketType.SESSION, new ObjectId(session.getDatabaseFilesGridfsId()));
                Document metadata = metadataOpt.map(GridFSFile::getMetadata).orElse(new Document());
                String compressionType = metadata.getString("compressionType");
                boolean isCompressed = "gzip".equals(compressionType);
                
                byte[] originalData = compressionService.decompress(compressedData, isCompressed);
                String databaseFilesJson = new String(originalData, "UTF-8");
                
                // 验证完整性
                String expectedHash = metadata.getString("integrityHash");
                if (expectedHash != null && integrityService.isEnabled()) {
                    IntegrityResult result;
                    try {
                        result = integrityService.verifyIntegrity(originalData, expectedHash);
                    } catch (IntegrityException e) {
                        logger.error("验证数据库文件完整性失败: sessionId={}", session.getId(), e);
                        throw new IOException("验证数据库文件完整性失败", e);
                    }
                    if (!result.isValid()) {
                        throw new IOException("数据库文件完整性校验失败: " + result.getMessage());
                    }
                }
                
                Map<String, String> databaseFiles = deserializeFromJson(databaseFilesJson, Map.class);
                session.setDatabaseFiles(databaseFiles);
                
                logger.debug("数据库文件从GridFS加载完成: sessionId={}, 数据大小={} bytes", 
                        session.getId(), originalData.length);
            } catch (Exception e) {
                logger.error("从GridFS读取数据库文件失败: sessionId={}, fileId={}", 
                        session.getId(), session.getDatabaseFilesGridfsId(), e);
                throw new IOException("读取数据库文件失败", e);
            }
        }

        // 读取下载文件
        if (session.getDownloadedFilesGridfsId() != null) {
            try {
                Optional<byte[]> compressedDataOpt = gridfsService.readFile(BucketType.SESSION, new ObjectId(session.getDownloadedFilesGridfsId()));
                if (!compressedDataOpt.isPresent()) {
                    logger.warn("无法读取下载文件GridFS: sessionId={}, fileId={}", session.getId(), session.getDownloadedFilesGridfsId());
                    return session;
                }
                byte[] compressedData = compressedDataOpt.get();
                
                // 检查是否压缩
                Optional<GridFSFile> metadataOpt = gridfsService.getFileMetadata(
                        BucketType.SESSION, new ObjectId(session.getDownloadedFilesGridfsId()));
                Document metadata = metadataOpt.map(GridFSFile::getMetadata).orElse(new Document());
                String compressionType = metadata.getString("compressionType");
                boolean isCompressed = "gzip".equals(compressionType);
                
                byte[] originalData = compressionService.decompress(compressedData, isCompressed);
                String downloadedFilesJson = new String(originalData, "UTF-8");
                
                // 验证完整性
                String expectedHash = metadata.getString("integrityHash");
                if (expectedHash != null && integrityService.isEnabled()) {
                    IntegrityResult result;
                    try {
                        result = integrityService.verifyIntegrity(originalData, expectedHash);
                    } catch (IntegrityException e) {
                        logger.error("验证下载文件完整性失败: sessionId={}", session.getId(), e);
                        throw new IOException("验证下载文件完整性失败", e);
                    }
                    if (!result.isValid()) {
                        throw new IOException("下载文件完整性校验失败: " + result.getMessage());
                    }
                }
                
                Map<String, String> downloadedFiles = deserializeFromJson(downloadedFilesJson, Map.class);
                session.setDownloadedFiles(downloadedFiles);
                
                logger.debug("下载文件从GridFS加载完成: sessionId={}, 数据大小={} bytes", 
                        session.getId(), originalData.length);
            } catch (Exception e) {
                logger.error("从GridFS读取下载文件失败: sessionId={}, fileId={}", 
                        session.getId(), session.getDownloadedFilesGridfsId(), e);
                throw new IOException("读取下载文件失败", e);
            }
        }

        logger.debug("GridFS数据加载完成: sessionId={}", session.getId());
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
}