package com.telegram.server.storage.util;

/**
 * 分片大小计算器
 * 用于计算安全的分片大小，确保不超过MongoDB文档大小限制
 * 
 * @author liubo
 * @date 2025-08-20
 */
public class ShardSizeCalculator {
    
    /** MongoDB文档大小限制 (16MB) */
    private static final long MONGODB_DOC_LIMIT = 16 * 1024 * 1024;
    
    /** Base64编码膨胀系数 (约33%增长) */
    private static final double BASE64_EXPANSION_FACTOR = 1.33;
    
    /** 元数据预留空间 (1MB) - 用于存储分片元信息 */
    private static final long METADATA_RESERVED_SIZE = 1024 * 1024;
    
    /** 安全边界系数 - 额外的安全边界 */
    private static final double SAFETY_MARGIN_FACTOR = 0.95;
    
    /**
     * 计算安全的分片大小
     * 考虑MongoDB限制、Base64编码膨胀和元数据空间
     * 
     * @return 安全的分片大小（字节）
     */
    public static long calculateSafeShardSize() {
        // 可用空间 = MongoDB限制 - 元数据预留空间
        long availableSize = MONGODB_DOC_LIMIT - METADATA_RESERVED_SIZE;
        
        // 考虑Base64编码膨胀，计算原始数据的最大大小
        long maxRawDataSize = (long) (availableSize / BASE64_EXPANSION_FACTOR);
        
        // 应用安全边界
        long safeShardSize = (long) (maxRawDataSize * SAFETY_MARGIN_FACTOR);
        
        return safeShardSize;
    }
    
    /**
     * 计算指定压缩率下的安全分片大小
     * 
     * @param compressionRatio 压缩率 (0.0-1.0)
     * @return 考虑压缩率的安全分片大小（字节）
     */
    public static long calculateSafeShardSize(double compressionRatio) {
        if (compressionRatio <= 0 || compressionRatio > 1.0) {
            throw new IllegalArgumentException("压缩率必须在0和1之间: " + compressionRatio);
        }
        
        long baseSafeSize = calculateSafeShardSize();
        
        // 如果数据能够压缩，可以允许更大的原始分片大小
        return (long) (baseSafeSize / compressionRatio);
    }
    
    /**
     * 验证分片大小是否安全
     * 
     * @param shardSize 分片大小（字节）
     * @return 是否安全
     */
    public static boolean isShardSizeSafe(long shardSize) {
        return shardSize <= calculateSafeShardSize();
    }
    
    /**
     * 验证编码后的分片大小是否安全
     * 
     * @param encodedShardSize Base64编码后的分片大小（字节）
     * @return 是否安全
     */
    public static boolean isEncodedShardSizeSafe(long encodedShardSize) {
        long maxAllowedSize = MONGODB_DOC_LIMIT - METADATA_RESERVED_SIZE;
        return encodedShardSize <= maxAllowedSize;
    }
    
    /**
     * 计算需要的分片数量
     * 
     * @param totalDataSize 总数据大小（字节）
     * @return 需要的分片数量
     */
    public static int calculateShardCount(long totalDataSize) {
        if (totalDataSize <= 0) {
            return 0;
        }
        
        long safeShardSize = calculateSafeShardSize();
        return (int) Math.ceil((double) totalDataSize / safeShardSize);
    }
    
    /**
     * 计算考虑压缩率的分片数量
     * 
     * @param totalDataSize 总数据大小（字节）
     * @param compressionRatio 压缩率 (0.0-1.0)
     * @return 需要的分片数量
     */
    public static int calculateShardCount(long totalDataSize, double compressionRatio) {
        if (totalDataSize <= 0) {
            return 0;
        }
        
        long safeShardSize = calculateSafeShardSize(compressionRatio);
        return (int) Math.ceil((double) totalDataSize / safeShardSize);
    }
    
    /**
     * 获取MongoDB文档大小限制
     * 
     * @return MongoDB文档大小限制（字节）
     */
    public static long getMongoDbDocumentLimit() {
        return MONGODB_DOC_LIMIT;
    }
    
    /**
     * 获取Base64编码膨胀系数
     * 
     * @return Base64编码膨胀系数
     */
    public static double getBase64ExpansionFactor() {
        return BASE64_EXPANSION_FACTOR;
    }
    
    /**
     * 获取元数据预留空间大小
     * 
     * @return 元数据预留空间大小（字节）
     */
    public static long getMetadataReservedSize() {
        return METADATA_RESERVED_SIZE;
    }
    
    /**
     * 计算Base64编码后的大小
     * 
     * @param originalSize 原始数据大小（字节）
     * @return Base64编码后的大小（字节）
     */
    public static long calculateBase64EncodedSize(long originalSize) {
        return (long) (originalSize * BASE64_EXPANSION_FACTOR);
    }
    
    /**
     * 格式化字节大小为可读字符串
     * 
     * @param bytes 字节数
     * @return 格式化的字符串
     */
    public static String formatBytes(long bytes) {
        if (bytes < 1024) {
            return bytes + " B";
        }
        
        int exp = (int) (Math.log(bytes) / Math.log(1024));
        String pre = "KMGTPE".charAt(exp - 1) + "";
        return String.format("%.1f %sB", bytes / Math.pow(1024, exp), pre);
    }
}