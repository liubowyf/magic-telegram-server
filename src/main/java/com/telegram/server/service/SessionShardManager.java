package com.telegram.server.service;

import com.magictelegram.strategy.DynamicShardStrategy;
import com.magictelegram.util.ShardSizeCalculator;
import com.magictelegram.monitor.StorageMonitor;
import com.magictelegram.exception.StorageExceptionHandler;
import com.magictelegram.exception.StorageException;
import com.telegram.server.entity.SessionShard;
import com.telegram.server.repository.SessionShardRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.IOException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Session分片管理器
 * 负责session数据的分片存储、读取和管理
 * 
 * @author liubo
 * @date 2025-01-19
 */
@Service
public class SessionShardManager {

    private static final Logger logger = LoggerFactory.getLogger(SessionShardManager.class);

    /**
     * 单个分片的最大大小（动态计算，确保安全性）
     * 使用ShardSizeCalculator计算安全的分片大小
     */
    private final long maxShardSize = ShardSizeCalculator.calculateSafeShardSize();

    /**
     * 分片类型常量
     */
    public static final String SHARD_TYPE_DATABASE_FILES = "database_files";
    public static final String SHARD_TYPE_DOWNLOADED_FILES = "downloaded_files";

    @Autowired
    private SessionShardRepository sessionShardRepository;

    @Autowired
    private CompressionService compressionService;
    
    @Autowired
    private DynamicShardStrategy dynamicShardStrategy;
    
    @Autowired
    private StorageMonitor storageMonitor;
    
    @Autowired
    private StorageExceptionHandler exceptionHandler;

    /**
     * 分片存储结果
     */
    public static class ShardStorageResult {
        private final List<String> shardRefs;
        private final long totalOriginalSize;
        private final long totalCompressedSize;
        private final String integrityHash;
        private final String compressionType;

        public ShardStorageResult(List<String> shardRefs, long totalOriginalSize, 
                                long totalCompressedSize, String integrityHash, String compressionType) {
            this.shardRefs = shardRefs;
            this.totalOriginalSize = totalOriginalSize;
            this.totalCompressedSize = totalCompressedSize;
            this.integrityHash = integrityHash;
            this.compressionType = compressionType;
        }

        public List<String> getShardRefs() {
            return shardRefs;
        }

        public long getTotalOriginalSize() {
            return totalOriginalSize;
        }

        public long getTotalCompressedSize() {
            return totalCompressedSize;
        }

        public String getIntegrityHash() {
            return integrityHash;
        }

        public String getCompressionType() {
            return compressionType;
        }
    }

    /**
     * 存储分片数据
     * 集成异常处理和监控，增强存储操作的健壮性
     * 
     * @param sessionId 主session ID
     * @param shardType 分片类型
     * @param data 原始数据（Map格式的JSON字符串）
     * @return 分片存储结果
     * @throws IOException 存储过程中的IO异常
     */
    @Transactional
    public ShardStorageResult storeShards(String sessionId, String shardType, String data) throws IOException {
        logger.info("开始存储分片数据: sessionId={}, shardType={}, dataSize={} bytes", 
                sessionId, shardType, data != null ? data.length() : 0);

        // 记录存储操作开始
         String operationId = storageMonitor.recordStorageStart(sessionId, "storeShards", data != null ? data.length() : 0);

         // 使用异常处理器执行存储操作
         try {
             StorageExceptionHandler.StorageOperationResult<ShardStorageResult> result = 
                 exceptionHandler.executeWithRetry(sessionId, "storeShards", () -> {
                     return performStoreShards(sessionId, shardType, data);
                 });

             if (result.isSuccess()) {
                  ShardStorageResult storageResult = result.getResult();
                  storageMonitor.recordStorageSuccess(operationId, sessionId, storageResult.getShardRefs().size(), 
                        storageResult.getTotalCompressedSize(), storageResult.getTotalOriginalSize(), 0);
                  logger.info("存储分片数据成功，sessionId: {}, shardType: {}, 尝试次数: {}", 
                      sessionId, shardType, result.getAttemptCount());
                  return result.getResult();
              } else {
                 logger.error("存储分片数据失败，sessionId: {}, shardType: {}, 尝试次数: {}, 错误: {}", 
                     sessionId, shardType, result.getAttemptCount(), result.getException().getMessage());
                 throw new IOException("存储分片数据失败", result.getException());
             }
         } catch (Exception e) {
             storageMonitor.recordStorageFailure(operationId, sessionId, "storeShards", 
                 e.getMessage(), data != null ? data.length() : 0);
             throw e;
         }
    }

    /**
     * 执行实际的分片存储逻辑
     * 
     * @param sessionId 主session ID
     * @param shardType 分片类型
     * @param data 原始数据
     * @return 分片存储结果
     * @throws StorageException 存储异常
     */
    private ShardStorageResult performStoreShards(String sessionId, String shardType, String data) throws StorageException {
        try {
            // 清理旧的分片数据
            cleanupOldShards(sessionId, shardType);

            if (data == null || data.isEmpty()) {
                logger.info("数据为空，跳过分片存储");
                return new ShardStorageResult(Collections.emptyList(), 0, 0, "", CompressionService.CompressionType.NONE.getValue());
            }

            // 选择压缩类型
            CompressionService.CompressionType compressionType = CompressionService.CompressionType.NONE;
            CompressionService.CompressionResult compressionResult;
            
            try {
                compressionType = compressionService.evaluateBestCompression(data.getBytes());
                // 压缩数据
                compressionResult = compressionService.compress(data, compressionType);
            } catch (Exception e) {
                // 处理压缩异常
                exceptionHandler.handleCompressionException(
                    sessionId, data.length(), compressionType != null ? compressionType.name() : "UNKNOWN", e
                );
                throw new StorageException(
                    StorageException.ErrorType.COMPRESSION_FAILED, 
                    "数据压缩失败: " + e.getMessage(), 
                    sessionId, e
                );
            }

        // 计算完整性哈希
        String integrityHash = calculateHash(data);

        // 分片压缩后的数据
        byte[] compressedData = compressionResult.getCompressedData();
        
        // 使用动态分片策略计算最优分片大小
        DynamicShardStrategy.CompressionType dynamicCompressionType = 
                convertCompressionType(compressionType);
        long optimalShardSize = dynamicShardStrategy.calculateOptimalShardSize(
                data.getBytes(), dynamicCompressionType);
        
        // 确保分片大小不超过安全限制
        long actualShardSize = Math.min(optimalShardSize, maxShardSize);
        
        logger.info("动态分片策略: 原始数据={}bytes, 压缩后={}bytes, 最优分片大小={}bytes, 实际分片大小={}bytes",
                data.length(), compressedData.length, optimalShardSize, actualShardSize);
        
            // 分片存储
            List<String> shardRefs = new ArrayList<>();
            
            if (compressedData.length <= actualShardSize) {
                // 数据足够小，存储为单个分片
                SessionShard shard = createShard(sessionId, shardType, 0, compressedData, compressionResult);
                
                try {
                    sessionShardRepository.save(shard);
                } catch (Exception e) {
                    // 处理MongoDB存储异常
                    exceptionHandler.handleMongoException(sessionId, "save", e);
                    throw new StorageException(
                        StorageException.ErrorType.DATABASE_ERROR, 
                        "保存分片到数据库失败: " + e.getMessage(), 
                        sessionId, e
                    );
                }
                
                shardRefs.add(shard.getId());
                
                logger.info("数据存储为单个分片: shardId={}, 分片大小={}bytes", 
                        shard.getId(), compressedData.length);
            } else {
                // 数据过大，需要分片存储
                List<byte[]> chunks = splitData(compressedData, actualShardSize);
                
                // 验证分片安全性
                for (int i = 0; i < chunks.size(); i++) {
                    byte[] chunk = chunks.get(i);
                    if (!ShardSizeCalculator.isEncodedShardSizeSafe(chunk.length)) {
                         long encodedSize = (long) Math.ceil(chunk.length * 4.0 / 3.0);
                         logger.warn("分片{}大小可能不安全: {}bytes (编码后约{}bytes)", 
                                 i, chunk.length, encodedSize);
                         
                         // 处理分片大小超限异常
                         exceptionHandler.handleShardSizeException(
                             sessionId, chunk.length, actualShardSize
                         );
                     }
                    
                    SessionShard shard = createShard(sessionId, shardType, i, chunk, compressionResult);
                    
                    try {
                        sessionShardRepository.save(shard);
                    } catch (Exception e) {
                        // 处理MongoDB存储异常
                        exceptionHandler.handleMongoException(sessionId, "save", e);
                        throw new StorageException(
                            StorageException.ErrorType.DATABASE_ERROR, 
                            "保存分片到数据库失败: " + e.getMessage(), 
                            sessionId, e
                        );
                    }
                    
                    shardRefs.add(shard.getId());
                }
                
                logger.info("数据分片存储完成: 总分片数={}, 原始大小={}bytes, 压缩后大小={}bytes, 平均分片大小={}bytes", 
                        chunks.size(), compressionResult.getOriginalSize(), 
                        compressionResult.getCompressedSize(), compressedData.length / chunks.size());
            }

            return new ShardStorageResult(shardRefs, compressionResult.getOriginalSize(), 
                    compressionResult.getCompressedSize(), integrityHash, compressionType.getValue());
                    
        } catch (StorageException e) {
            throw e; // 重新抛出StorageException
        } catch (Exception e) {
            logger.error("存储分片数据时发生未知错误，sessionId: {}, shardType: {}", sessionId, shardType, e);
            throw new StorageException(
                StorageException.ErrorType.UNKNOWN_ERROR, 
                "存储分片时发生未知错误: " + e.getMessage(), 
                sessionId, e
            );
        }
    }

    /**
     * 读取分片数据
     * 集成异常处理和监控
     * 
     * @param sessionId 主session ID
     * @param shardType 分片类型
     * @return 重组后的原始数据
     * @throws IOException 读取过程中的IO异常
     */
    public String readShards(String sessionId, String shardType) throws IOException {
        logger.debug("开始读取分片数据: sessionId={}, shardType={}", sessionId, shardType);

        // 记录读取操作开始
         String operationId = storageMonitor.recordStorageStart(sessionId, "readShards", 0);

         try {
             StorageExceptionHandler.StorageOperationResult<String> result = 
                 exceptionHandler.executeWithRetry(sessionId, "readShards", () -> {
                     return performReadShards(sessionId, shardType);
                 });

             if (result.isSuccess()) {
                 String resultData = result.getResult();
                 storageMonitor.recordStorageSuccess(operationId, sessionId, 1, 
                     resultData != null ? resultData.length() : 0, 
                     resultData != null ? resultData.length() : 0, 0);
                 logger.debug("读取分片数据成功，sessionId: {}, shardType: {}, 尝试次数: {}", 
                     sessionId, shardType, result.getAttemptCount());
                 return result.getResult();
             } else {
                 logger.error("读取分片数据失败，sessionId: {}, shardType: {}, 尝试次数: {}, 错误: {}", 
                     sessionId, shardType, result.getAttemptCount(), result.getException().getMessage());
                 throw new IOException("读取分片数据失败", result.getException());
             }
         } catch (Exception e) {
             storageMonitor.recordStorageFailure(operationId, sessionId, "readShards", 
                 e.getMessage(), 0);
             throw e;
         }
    }

    /**
     * 执行实际的分片读取逻辑
     * 
     * @param sessionId 主session ID
     * @param shardType 分片类型
     * @return 重组后的原始数据
     * @throws StorageException 存储异常
     */
    private String performReadShards(String sessionId, String shardType) throws StorageException {
        try {
            List<SessionShard> shards;
            
            try {
                shards = sessionShardRepository
                        .findBySessionIdAndShardTypeOrderByShardIndex(sessionId, shardType);
            } catch (Exception e) {
                // 处理MongoDB查询异常
                exceptionHandler.handleMongoException(sessionId, "findBySessionIdAndShardType", e);
                throw new StorageException(
                    StorageException.ErrorType.DATABASE_ERROR, 
                    "查询分片数据失败: " + e.getMessage(), 
                    sessionId, e
                );
            }

            if (shards.isEmpty()) {
                logger.debug("未找到分片数据: sessionId={}, shardType={}", sessionId, shardType);
                return null;
            }

            // 验证分片完整性
            validateShardIntegrity(shards);

            // 重组压缩数据
            byte[] compressedData = reassembleShards(shards);

            // 解压缩数据
            CompressionService.CompressionType compressionType = 
                    CompressionService.CompressionType.fromValue(shards.get(0).getCompressionType());
            
            String decompressedData;
            try {
                decompressedData = compressionService.decompressToString(compressedData, compressionType);
            } catch (Exception e) {
                // 处理解压缩异常
                exceptionHandler.handleCompressionException(
                    sessionId, compressedData.length, compressionType.name(), e
                );
                throw new StorageException(
                    StorageException.ErrorType.COMPRESSION_FAILED, 
                    "数据解压缩失败: " + e.getMessage(), 
                    sessionId, e
                );
            }

            logger.debug("分片数据读取完成: sessionId={}, shardType={}, 分片数={}, 解压后大小={} bytes", 
                    sessionId, shardType, shards.size(), decompressedData.length());

            return decompressedData;
            
        } catch (StorageException e) {
            throw e; // 重新抛出StorageException
        } catch (Exception e) {
            logger.error("读取分片数据时发生未知错误，sessionId: {}, shardType: {}", sessionId, shardType, e);
            throw new StorageException(
                StorageException.ErrorType.UNKNOWN_ERROR, 
                "读取分片时发生未知错误: " + e.getMessage(), 
                sessionId, e
            );
        }
    }

    /**
     * 删除分片数据
     * 
     * @param sessionId 主session ID
     * @param shardType 分片类型（可选，为null时删除所有类型）
     * @return 删除的分片数量
     */
    @Transactional
    public long deleteShards(String sessionId, String shardType) {
        logger.info("开始删除分片数据: sessionId={}, shardType={}", sessionId, shardType);

        long deletedCount;
        if (shardType != null) {
            deletedCount = sessionShardRepository.deleteBySessionIdAndShardType(sessionId, shardType);
        } else {
            deletedCount = sessionShardRepository.deleteBySessionId(sessionId);
        }

        logger.info("分片数据删除完成: sessionId={}, shardType={}, 删除数量={}", 
                sessionId, shardType, deletedCount);

        return deletedCount;
    }

    /**
     * 检查分片是否存在
     * 
     * @param sessionId 主session ID
     * @param shardType 分片类型
     * @return 是否存在分片
     */
    public boolean hasShards(String sessionId, String shardType) {
        return sessionShardRepository.existsBySessionIdAndShardType(sessionId, shardType);
    }

    /**
     * 获取分片统计信息
     * 
     * @param sessionId 主session ID
     * @param shardType 分片类型
     * @return 分片统计信息
     */
    public Map<String, Object> getShardStatistics(String sessionId, String shardType) {
        List<SessionShard> shards = sessionShardRepository
                .findBySessionIdAndShardTypeOrderByShardIndex(sessionId, shardType);

        Map<String, Object> stats = new HashMap<>();
        stats.put("shardCount", shards.size());
        stats.put("totalOriginalSize", shards.stream().mapToLong(SessionShard::getOriginalSize).sum());
        stats.put("totalCompressedSize", shards.stream().mapToLong(SessionShard::getCompressedSize).sum());
        stats.put("compressionType", shards.isEmpty() ? "none" : shards.get(0).getCompressionType());
        
        if (!shards.isEmpty()) {
            long totalOriginal = shards.stream().mapToLong(SessionShard::getOriginalSize).sum();
            long totalCompressed = shards.stream().mapToLong(SessionShard::getCompressedSize).sum();
            double compressionRatio = totalOriginal > 0 ? (double) totalCompressed / totalOriginal : 1.0;
            stats.put("compressionRatio", compressionRatio);
        }

        return stats;
    }

    /**
     * 清理旧的分片数据
     * 
     * @param sessionId 主session ID
     * @param shardType 分片类型
     */
    private void cleanupOldShards(String sessionId, String shardType) {
        long deletedCount = sessionShardRepository.deleteBySessionIdAndShardType(sessionId, shardType);
        if (deletedCount > 0) {
            logger.debug("清理旧分片数据: sessionId={}, shardType={}, 删除数量={}", 
                    sessionId, shardType, deletedCount);
        }
    }

    /**
     * 创建分片对象
     * 
     * @param sessionId 主session ID
     * @param shardType 分片类型
     * @param shardIndex 分片序号
     * @param data 分片数据
     * @param compressionResult 压缩结果
     * @return 分片对象
     */
    private SessionShard createShard(String sessionId, String shardType, int shardIndex, 
                                   byte[] data, CompressionService.CompressionResult compressionResult) {
        SessionShard shard = new SessionShard(sessionId, shardIndex, shardType);
        shard.setData(Base64.getEncoder().encodeToString(data));
        shard.setCompressionType(compressionResult.getCompressionType().getValue());
        shard.setOriginalSize(compressionResult.getOriginalSize());
        shard.setCompressedSize((long) data.length);
        shard.setDataHash(calculateHash(data));
        
        return shard;
    }

    /**
     * 分割数据为多个块
     * 
     * @param data 原始数据
     * @param maxChunkSize 最大块大小
     * @return 数据块列表
     */
    private List<byte[]> splitData(byte[] data, long maxChunkSize) {
        List<byte[]> chunks = new ArrayList<>();
        int chunkSize = (int) maxChunkSize;
        
        for (int i = 0; i < data.length; i += chunkSize) {
            int end = Math.min(i + chunkSize, data.length);
            byte[] chunk = Arrays.copyOfRange(data, i, end);
            chunks.add(chunk);
        }
        
        return chunks;
    }

    /**
     * 验证分片完整性
     * 
     * @param shards 分片列表
     * @throws IllegalStateException 分片不完整时抛出异常
     */
    private void validateShardIntegrity(List<SessionShard> shards) {
        if (shards.isEmpty()) {
            return;
        }

        // 检查分片序号连续性
        for (int i = 0; i < shards.size(); i++) {
            if (!shards.get(i).getShardIndex().equals(i)) {
                throw new IllegalStateException(
                        String.format("分片序号不连续: 期望=%d, 实际=%d", i, shards.get(i).getShardIndex()));
            }
        }

        logger.debug("分片完整性验证通过: 分片数={}", shards.size());
    }

    /**
     * 重组分片数据
     * 
     * @param shards 分片列表
     * @return 重组后的数据
     */
    private byte[] reassembleShards(List<SessionShard> shards) {
        if (shards.size() == 1) {
            return Base64.getDecoder().decode(shards.get(0).getData());
        }

        // 计算总大小
        int totalSize = shards.stream()
                .mapToInt(shard -> Base64.getDecoder().decode(shard.getData()).length)
                .sum();

        // 重组数据
        byte[] result = new byte[totalSize];
        int offset = 0;
        
        for (SessionShard shard : shards) {
            byte[] shardData = Base64.getDecoder().decode(shard.getData());
            System.arraycopy(shardData, 0, result, offset, shardData.length);
            offset += shardData.length;
        }

        return result;
    }

    /**
     * 计算数据的MD5哈希值
     * 
     * @param data 数据
     * @return MD5哈希值
     */
    private String calculateHash(String data) {
        return calculateHash(data.getBytes());
    }

    /**
     * 转换压缩类型枚举
     * @param compressionType 压缩服务的压缩类型
     * @return 动态分片策略的压缩类型
     */
    private DynamicShardStrategy.CompressionType convertCompressionType(
            CompressionService.CompressionType compressionType) {
        switch (compressionType) {
            case GZIP:
                return DynamicShardStrategy.CompressionType.GZIP;
            case NONE:
            default:
                return DynamicShardStrategy.CompressionType.NONE;
        }
    }

    /**
     * 计算数据的MD5哈希值
     * 
     * @param data 数据
     * @return MD5哈希值
     */
    private String calculateHash(byte[] data) {
        try {
            MessageDigest md = MessageDigest.getInstance("MD5");
            byte[] hashBytes = md.digest(data);
            StringBuilder sb = new StringBuilder();
            for (byte b : hashBytes) {
                sb.append(String.format("%02x", b));
            }
            return sb.toString();
        } catch (NoSuchAlgorithmException e) {
            logger.error("MD5算法不可用", e);
            return "";
        }
    }
}