package com.telegram.server.service.gridfs;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.telegram.server.entity.TelegramSession;
import com.telegram.server.repository.TelegramSessionRepository;
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
@ConditionalOnProperty(name = "telegram.session.storage.strategy", havingValue = "gridfs")
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

        GridFSStorageContext context = new GridFSStorageContext();
        
        // 存储数据库文件到GridFS
        if (databaseFilesJson != null && !databaseFilesJson.isEmpty()) {
            storeDatabaseFilesToGridFS(session, databaseFilesJson, context);
        }

        // 存储下载文件到GridFS
        if (downloadedFilesJson != null && !downloadedFilesJson.isEmpty()) {
            storeDownloadedFilesToGridFS(session, downloadedFilesJson, context);
        }

        // 更新session元数据并保存
        return finalizeGridFSStorage(session, context);
    }

    /**
     * 存储数据库文件到GridFS
     * 
     * @param session session对象
     * @param databaseFilesJson 数据库文件JSON数据
     * @param context 存储上下文
     * @throws IOException 存储过程中的IO异常
     */
    private void storeDatabaseFilesToGridFS(TelegramSession session, String databaseFilesJson, 
                                           GridFSStorageContext context) throws IOException {
        String filename = "session_" + session.getId() + "_database_files.json";
        
        // 压缩数据
        CompressionResult compressionResult = compressData(databaseFilesJson, session.getId(), "数据库文件");
        byte[] compressedData = compressionResult.getData();
        context.compressionType = compressionResult.isCompressed() ? "gzip" : "none";
        
        // 计算完整性哈希
        String dbIntegrityHash = calculateIntegrityHash(databaseFilesJson, session.getId(), "数据库文件");
        
        // 存储到GridFS
        String fileId = storeFileToGridFS(session.getId(), filename, compressedData, 
                "database_files", databaseFilesJson.length(), compressedData.length, 
                context.compressionType, dbIntegrityHash);
        session.setDatabaseFilesGridfsId(fileId);
        
        // 更新统计信息
        context.totalOriginalSize += databaseFilesJson.getBytes("UTF-8").length;
        context.totalCompressedSize += compressedData.length;
        context.integrityHash += dbIntegrityHash;
        
        logger.debug("数据库文件存储到GridFS: sessionId={}, fileId={}, 原始大小={}, 压缩后大小={}", 
                session.getId(), fileId, databaseFilesJson.getBytes("UTF-8").length, compressedData.length);
    }

    /**
     * 存储下载文件到GridFS
     * 
     * @param session session对象
     * @param downloadedFilesJson 下载文件JSON数据
     * @param context 存储上下文
     * @throws IOException 存储过程中的IO异常
     */
    private void storeDownloadedFilesToGridFS(TelegramSession session, String downloadedFilesJson, 
                                             GridFSStorageContext context) throws IOException {
        String filename = "session_" + session.getId() + "_downloaded_files.json";
        
        // 压缩数据
        CompressionResult compressionResult = compressData(downloadedFilesJson, session.getId(), "下载文件");
        byte[] compressedData = compressionResult.getData();
        if ("none".equals(context.compressionType)) {
            context.compressionType = compressionResult.isCompressed() ? "gzip" : "none";
        }
        
        // 计算完整性哈希
        String dlIntegrityHash = calculateIntegrityHash(downloadedFilesJson, session.getId(), "下载文件");
        
        // 存储到GridFS
        String fileId = storeFileToGridFS(session.getId(), filename, compressedData, 
                "downloaded_files", downloadedFilesJson.length(), compressedData.length, 
                context.compressionType, dlIntegrityHash);
        session.setDownloadedFilesGridfsId(fileId);
        
        // 更新统计信息
        context.totalOriginalSize += downloadedFilesJson.getBytes("UTF-8").length;
        context.totalCompressedSize += compressedData.length;
        context.integrityHash += dlIntegrityHash;
        
        logger.debug("下载文件存储到GridFS: sessionId={}, fileId={}, 原始大小={}, 压缩后大小={}", 
                session.getId(), fileId, downloadedFilesJson.getBytes("UTF-8").length, compressedData.length);
    }

    /**
     * 压缩数据
     * 
     * @param data 原始数据
     * @param sessionId session ID
     * @param dataType 数据类型描述
     * @return 压缩结果
     * @throws IOException 压缩失败时抛出
     */
    private CompressionResult compressData(String data, String sessionId, String dataType) throws IOException {
        try {
            return compressionService.compress(data.getBytes("UTF-8"));
        } catch (CompressionException e) {
            logger.error("压缩{}失败: sessionId={}", dataType, sessionId, e);
            throw new IOException("压缩" + dataType + "失败", e);
        }
    }

    /**
     * 计算完整性哈希
     * 
     * @param data 原始数据
     * @param sessionId session ID
     * @param dataType 数据类型描述
     * @return 完整性哈希值
     * @throws IOException 计算失败时抛出
     */
    private String calculateIntegrityHash(String data, String sessionId, String dataType) throws IOException {
        try {
            return integrityService.calculateChecksum(data.getBytes("UTF-8"));
        } catch (IntegrityException e) {
            logger.error("计算{}哈希失败: sessionId={}", dataType, sessionId, e);
            throw new IOException("计算" + dataType + "哈希失败", e);
        }
    }

    /**
     * 存储文件到GridFS
     * 
     * @param sessionId session ID
     * @param filename 文件名
     * @param data 文件数据
     * @param dataType 数据类型
     * @param originalSize 原始大小
     * @param compressedSize 压缩后大小
     * @param compressionType 压缩类型
     * @param integrityHash 完整性哈希
     * @return GridFS文件ID
     * @throws IOException 存储失败时抛出
     */
    private String storeFileToGridFS(String sessionId, String filename, byte[] data, 
                                    String dataType, int originalSize, int compressedSize, 
                                    String compressionType, String integrityHash) throws IOException {
        Map<String, Object> metadata = Map.of(
                "sessionId", sessionId,
                "dataType", dataType,
                "originalSize", originalSize,
                "compressedSize", compressedSize,
                "compressionType", compressionType,
                "integrityHash", integrityHash
        );
        
        Document metadataDoc = new Document();
        for (Map.Entry<String, Object> entry : metadata.entrySet()) {
            metadataDoc.append(entry.getKey(), entry.getValue());
        }
        
        try {
            ObjectId gridfsId = gridfsService.storeFile(BucketType.SESSION, filename, data, metadataDoc);
            return gridfsId.toString();
        } catch (GridFSException e) {
            logger.error("存储{}到GridFS失败: sessionId={}", dataType, sessionId, e);
            throw new IOException("存储" + dataType + "到GridFS失败", e);
        }
    }

    /**
     * 完成GridFS存储并更新session
     * 
     * @param session session对象
     * @param context 存储上下文
     * @return 更新后的session对象
     * @throws IOException 存储失败时抛出
     */
    private TelegramSession finalizeGridFSStorage(TelegramSession session, GridFSStorageContext context) throws IOException {
        // 更新session元数据
        session.setStorageVersion(STORAGE_VERSION_GRIDFS);
        session.setDatabaseFiles(null); // 清空原始数据
        session.setDownloadedFiles(null); // 清空原始数据
        session.setShardEnabled(null); // 清空分片相关字段
        session.setShardRefs(null);
        
        // 设置压缩和大小信息
        session.setOriginalSize(context.totalOriginalSize);
        session.setCompressedSize(context.totalCompressedSize);
        session.setCompressionType(context.compressionType);
        
        // 计算整体完整性哈希
        try {
            session.setIntegrityHash(integrityService.calculateChecksum(context.integrityHash.getBytes("UTF-8")));
        } catch (IntegrityException e) {
            logger.error("计算整体完整性哈希失败: sessionId={}", session.getId(), e);
            throw new IOException("计算整体完整性哈希失败", e);
        }

        // 保存session
        session = sessionRepository.save(session);
        
        logger.info("GridFS存储完成: sessionId={}, 原始大小={} bytes, 压缩后大小={} bytes, 压缩率={:.2f}%", 
                session.getId(), context.totalOriginalSize, context.totalCompressedSize, 
                context.totalOriginalSize > 0 ? (1.0 - (double)context.totalCompressedSize / context.totalOriginalSize) * 100 : 0);

        return session;
    }

    /**
     * GridFS存储上下文
     * 用于在存储过程中传递状态信息
     */
    private static class GridFSStorageContext {
        long totalOriginalSize = 0;
        long totalCompressedSize = 0;
        String compressionType = "none";
        String integrityHash = "";
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
    /**
     * 从GridFS加载session数据
     * 
     * @param session session对象
     * @return 加载完成的session对象
     * @throws IOException 加载过程中的IO异常
     */
    private TelegramSession loadFromGridFS(TelegramSession session) throws IOException {
        logger.debug("从GridFS加载session: sessionId={}", session.getId());

        // 加载数据库文件
        loadDatabaseFilesFromGridFS(session);

        // 加载下载文件
        loadDownloadedFilesFromGridFS(session);

        logger.debug("GridFS数据加载完成: sessionId={}", session.getId());
        return session;
    }

    /**
     * 从GridFS加载数据库文件
     * 
     * @param session session对象
     * @throws IOException 加载过程中的IO异常
     */
    private void loadDatabaseFilesFromGridFS(TelegramSession session) throws IOException {
        Map<String, String> databaseFiles = loadFilesFromGridFS(
            session.getId(), 
            session.getDatabaseFilesGridfsId(), 
            "数据库文件"
        );
        if (databaseFiles != null) {
            session.setDatabaseFiles(databaseFiles);
        }
    }

    /**
     * 从GridFS加载下载文件
     * 
     * @param session session对象
     * @throws IOException 加载过程中的IO异常
     */
    private void loadDownloadedFilesFromGridFS(TelegramSession session) throws IOException {
        Map<String, String> downloadedFiles = loadFilesFromGridFS(
            session.getId(), 
            session.getDownloadedFilesGridfsId(), 
            "下载文件"
        );
        if (downloadedFiles != null) {
            session.setDownloadedFiles(downloadedFiles);
        }
    }

    /**
     * 从GridFS加载文件的通用方法
     * 
     * @param sessionId 会话ID
     * @param fileId GridFS文件ID
     * @param fileType 文件类型（用于日志）
     * @return 反序列化后的文件映射，如果文件不存在返回null
     * @throws IOException 加载过程中的IO异常
     */
    private Map<String, String> loadFilesFromGridFS(String sessionId, String fileId, String fileType) throws IOException {
        if (fileId == null) {
            return null;
        }

        try {
            // 读取压缩数据
            byte[] compressedData = readGridFSFile(sessionId, fileId, fileType);
            if (compressedData == null) {
                return null;
            }

            // 获取元数据并解压
            Document metadata = getGridFSMetadata(fileId);
            byte[] originalData = decompressGridFSData(compressedData, metadata);
            String filesJson = new String(originalData, "UTF-8");

            // 验证完整性
            verifyDataIntegrity(originalData, metadata, sessionId, fileType);

            // 反序列化
            Map<String, String> files = deserializeFromJson(filesJson, Map.class);

            logger.debug("{}从GridFS加载完成: sessionId={}, 数据大小={} bytes", 
                    fileType, sessionId, originalData.length);
            
            return files;
        } catch (Exception e) {
            logger.error("从GridFS读取{}失败: sessionId={}, fileId={}", 
                    fileType, sessionId, fileId, e);
            throw new IOException("读取" + fileType + "失败", e);
        }
    }

    /**
     * 从GridFS读取文件数据
     * 
     * @param sessionId 会话ID
     * @param fileId 文件ID
     * @param fileType 文件类型（用于日志）
     * @return 文件数据，如果文件不存在返回null
     * @throws IOException 读取失败时抛出异常
     */
    private byte[] readGridFSFile(String sessionId, String fileId, String fileType) throws IOException {
        try {
            Optional<byte[]> compressedDataOpt = gridfsService.readFile(BucketType.SESSION, new ObjectId(fileId));
            if (!compressedDataOpt.isPresent()) {
                logger.warn("无法读取{}GridFS: sessionId={}, fileId={}", fileType, sessionId, fileId);
                return null;
            }
            return compressedDataOpt.get();
        } catch (GridFSException e) {
            throw new IOException("读取GridFS文件失败: " + fileType, e);
        }
    }

    /**
     * 获取GridFS文件元数据
     * 
     * @param fileId 文件ID
     * @return 文件元数据
     * @throws IOException 获取失败时抛出异常
     */
    private Document getGridFSMetadata(String fileId) throws IOException {
        try {
            Optional<GridFSFile> metadataOpt = gridfsService.getFileMetadata(
                    BucketType.SESSION, new ObjectId(fileId));
            return metadataOpt.map(GridFSFile::getMetadata).orElse(new Document());
        } catch (GridFSException e) {
            throw new IOException("获取GridFS文件元数据失败", e);
        }
    }

    /**
     * 解压GridFS数据
     * 
     * @param compressedData 压缩数据
     * @param metadata 文件元数据
     * @return 解压后的原始数据
     * @throws IOException 解压失败时抛出异常
     */
    private byte[] decompressGridFSData(byte[] compressedData, Document metadata) throws IOException {
        try {
            String compressionType = metadata.getString("compressionType");
            boolean isCompressed = "gzip".equals(compressionType);
            
            // 如果标记为压缩但解压失败，尝试作为未压缩数据处理
            if (isCompressed) {
                try {
                    return compressionService.decompress(compressedData, true);
                } catch (CompressionException e) {
                    logger.warn("GZIP解压失败，可能是数据格式不匹配，尝试作为未压缩数据处理: {}", e.getMessage());
                    
                    // 检查数据是否看起来像有效的JSON（大多数session数据都是JSON）
                    if (isLikelyJsonData(compressedData)) {
                        logger.info("数据似乎是未压缩的JSON，直接返回原始数据");
                        return compressedData;
                    }
                    
                    // 如果不像JSON，重新抛出原始异常
                    throw new IOException("解压数据失败，数据格式不匹配: " + e.getMessage(), e);
                }
            } else {
                return compressionService.decompress(compressedData, false);
            }
        } catch (CompressionException e) {
            throw new IOException("解压数据失败", e);
        }
    }
    
    /**
     * 检查数据是否可能是JSON格式
     * 
     * @param data 待检查的数据
     * @return 如果数据看起来像JSON则返回true
     */
    private boolean isLikelyJsonData(byte[] data) {
        if (data == null || data.length < 2) {
            return false;
        }
        
        // 检查是否以JSON对象或数组开始
        char firstChar = (char) data[0];
        char lastChar = (char) data[data.length - 1];
        
        return (firstChar == '{' && lastChar == '}') || (firstChar == '[' && lastChar == ']');
    }

    /**
     * 验证数据完整性
     * 
     * @param originalData 原始数据
     * @param metadata 文件元数据
     * @param sessionId 会话ID
     * @param fileType 文件类型（用于日志）
     * @throws IOException 验证失败时抛出异常
     */
    private void verifyDataIntegrity(byte[] originalData, Document metadata, String sessionId, String fileType) throws IOException {
        String expectedHash = metadata.getString("integrityHash");
        if (expectedHash != null && integrityService.isEnabled()) {
            IntegrityResult result;
            try {
                result = integrityService.verifyIntegrity(originalData, expectedHash);
            } catch (IntegrityException e) {
                logger.error("验证{}完整性失败: sessionId={}", fileType, sessionId, e);
                throw new IOException("验证" + fileType + "完整性失败", e);
            }
            if (!result.isValid()) {
                throw new IOException(fileType + "完整性校验失败: " + result.getMessage());
            }
        }
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