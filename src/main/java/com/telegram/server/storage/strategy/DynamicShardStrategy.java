package com.telegram.server.storage.strategy;

import com.telegram.server.storage.util.ShardSizeCalculator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.zip.Deflater;
import java.util.zip.GZIPOutputStream;

/**
 * 动态分片策略
 * 根据数据特征动态调整分片大小，优化存储效率
 * 
 * @author liubo
 * @date 2025-08-20
 */
@Component
public class DynamicShardStrategy {
    
    private static final Logger logger = LoggerFactory.getLogger(DynamicShardStrategy.class);
    
    /** 压缩类型枚举 */
    public enum CompressionType {
        NONE(1.0),
        GZIP(0.3),
        DEFLATE(0.35),
        LZ4(0.5);
        
        private final double estimatedRatio;
        
        CompressionType(double estimatedRatio) {
            this.estimatedRatio = estimatedRatio;
        }
        
        public double getEstimatedRatio() {
            return estimatedRatio;
        }
    }
    
    /** 数据类型枚举 */
    public enum DataType {
        TEXT(0.4),          // 文本数据压缩率较高
        BINARY(0.8),        // 二进制数据压缩率较低
        JSON(0.3),          // JSON数据压缩率很高
        SESSION(0.6),       // Session数据压缩率中等
        UNKNOWN(0.7);       // 未知类型使用保守估计
        
        private final double estimatedCompressionRatio;
        
        DataType(double estimatedCompressionRatio) {
            this.estimatedCompressionRatio = estimatedCompressionRatio;
        }
        
        public double getEstimatedCompressionRatio() {
            return estimatedCompressionRatio;
        }
    }
    
    /**
     * 计算最优分片大小
     * 
     * @param data 原始数据
     * @param compressionType 压缩类型
     * @return 最优分片大小（字节）
     */
    public long calculateOptimalShardSize(byte[] data, CompressionType compressionType) {
        if (data == null || data.length == 0) {
            return ShardSizeCalculator.calculateSafeShardSize();
        }
        
        try {
            // 1. 检测数据类型
            DataType dataType = detectDataType(data);
            logger.debug("检测到数据类型: {}", dataType);
            
            // 2. 估算压缩率
            double estimatedCompressionRatio = estimateCompressionRatio(data, compressionType, dataType);
            logger.debug("估算压缩率: {}", estimatedCompressionRatio);
            
            // 3. 计算最优分片大小
            long optimalSize = ShardSizeCalculator.calculateSafeShardSize(estimatedCompressionRatio);
            
            // 4. 应用数据特征调整
            optimalSize = applyDataCharacteristicsAdjustment(optimalSize, data, dataType);
            
            logger.debug("计算得到最优分片大小: {} ({})", 
                        optimalSize, ShardSizeCalculator.formatBytes(optimalSize));
            
            return optimalSize;
            
        } catch (Exception e) {
            logger.warn("计算最优分片大小时发生异常，使用默认安全大小", e);
            return ShardSizeCalculator.calculateSafeShardSize();
        }
    }
    
    /**
     * 检测数据类型
     * 
     * @param data 数据
     * @return 数据类型
     */
    private DataType detectDataType(byte[] data) {
        if (data.length < 10) {
            return DataType.UNKNOWN;
        }
        
        // 检查是否为文本数据（ASCII范围内的字符较多）
        int textCharCount = 0;
        int sampleSize = Math.min(1000, data.length);
        
        for (int i = 0; i < sampleSize; i++) {
            byte b = data[i];
            if ((b >= 32 && b <= 126) || b == 9 || b == 10 || b == 13) {
                textCharCount++;
            }
        }
        
        double textRatio = (double) textCharCount / sampleSize;
        
        if (textRatio > 0.8) {
            // 进一步检查是否为JSON
            String sample = new String(data, 0, Math.min(100, data.length));
            if (sample.trim().startsWith("{") || sample.trim().startsWith("[")) {
                return DataType.JSON;
            }
            return DataType.TEXT;
        } else if (textRatio > 0.3) {
            // 可能是session数据（包含文本和二进制）
            return DataType.SESSION;
        } else {
            return DataType.BINARY;
        }
    }
    
    /**
     * 估算压缩率
     * 
     * @param data 数据
     * @param compressionType 压缩类型
     * @param dataType 数据类型
     * @return 估算的压缩率
     */
    public double estimateCompressionRatio(byte[] data, CompressionType compressionType, DataType dataType) {
        if (compressionType == CompressionType.NONE) {
            return 1.0;
        }
        
        // 基础压缩率估算
        double baseRatio = dataType.getEstimatedCompressionRatio();
        double compressionEfficiency = compressionType.getEstimatedRatio();
        
        // 综合计算
        double estimatedRatio = baseRatio * compressionEfficiency + (1 - compressionEfficiency);
        
        // 对于小数据样本，进行实际压缩测试以提高准确性
        if (data.length <= 10 * 1024) { // 10KB以下进行实际测试
            double actualRatio = performActualCompressionTest(data, compressionType);
            if (actualRatio > 0) {
                // 使用实际测试结果和估算结果的加权平均
                estimatedRatio = actualRatio * 0.7 + estimatedRatio * 0.3;
            }
        }
        
        // 确保压缩率在合理范围内
        return Math.max(0.1, Math.min(1.0, estimatedRatio));
    }
    
    /**
     * 执行实际压缩测试
     * 
     * @param data 数据
     * @param compressionType 压缩类型
     * @return 实际压缩率，失败时返回-1
     */
    private double performActualCompressionTest(byte[] data, CompressionType compressionType) {
        try {
            byte[] compressed = compressData(data, compressionType);
            if (compressed != null && compressed.length > 0) {
                return (double) compressed.length / data.length;
            }
        } catch (Exception e) {
            logger.debug("实际压缩测试失败: {}", e.getMessage());
        }
        return -1;
    }
    
    /**
     * 压缩数据（用于测试）
     * 
     * @param data 原始数据
     * @param compressionType 压缩类型
     * @return 压缩后的数据
     */
    private byte[] compressData(byte[] data, CompressionType compressionType) throws IOException {
        switch (compressionType) {
            case GZIP:
                return compressWithGzip(data);
            case DEFLATE:
                return compressWithDeflate(data);
            case LZ4:
                // LZ4压缩需要额外的库，这里使用DEFLATE作为替代
                return compressWithDeflate(data);
            default:
                return data;
        }
    }
    
    /**
     * GZIP压缩
     */
    private byte[] compressWithGzip(byte[] data) throws IOException {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        try (GZIPOutputStream gzipOut = new GZIPOutputStream(baos)) {
            gzipOut.write(data);
        }
        return baos.toByteArray();
    }
    
    /**
     * DEFLATE压缩
     */
    private byte[] compressWithDeflate(byte[] data) {
        Deflater deflater = new Deflater();
        deflater.setInput(data);
        deflater.finish();
        
        byte[] buffer = new byte[data.length];
        int compressedLength = deflater.deflate(buffer);
        deflater.end();
        
        byte[] result = new byte[compressedLength];
        System.arraycopy(buffer, 0, result, 0, compressedLength);
        return result;
    }
    
    /**
     * 应用数据特征调整
     * 
     * @param baseShardSize 基础分片大小
     * @param data 数据
     * @param dataType 数据类型
     * @return 调整后的分片大小
     */
    private long applyDataCharacteristicsAdjustment(long baseShardSize, byte[] data, DataType dataType) {
        long adjustedSize = baseShardSize;
        
        // 根据数据大小调整
        if (data.length < 1024 * 1024) { // 小于1MB的数据
            // 小数据可以使用更大的分片
            adjustedSize = (long) (adjustedSize * 1.2);
        } else if (data.length > 50 * 1024 * 1024) { // 大于50MB的数据
            // 大数据使用更保守的分片大小
            adjustedSize = (long) (adjustedSize * 0.9);
        }
        
        // 根据数据类型调整
        switch (dataType) {
            case JSON:
            case TEXT:
                // 文本数据压缩效果好，可以使用更大的分片
                adjustedSize = (long) (adjustedSize * 1.1);
                break;
            case BINARY:
                // 二进制数据压缩效果差，使用更小的分片
                adjustedSize = (long) (adjustedSize * 0.9);
                break;
            default:
                // 其他类型保持不变
                break;
        }
        
        // 确保调整后的大小仍然安全
        long maxSafeSize = ShardSizeCalculator.calculateSafeShardSize();
        return Math.min(adjustedSize, maxSafeSize);
    }
    
    /**
     * 计算推荐的分片数量
     * 
     * @param totalDataSize 总数据大小
     * @param data 数据样本（用于分析）
     * @param compressionType 压缩类型
     * @return 推荐的分片数量
     */
    public int calculateRecommendedShardCount(long totalDataSize, byte[] data, CompressionType compressionType) {
        if (totalDataSize <= 0) {
            return 0;
        }
        
        long optimalShardSize = calculateOptimalShardSize(data, compressionType);
        int shardCount = (int) Math.ceil((double) totalDataSize / optimalShardSize);
        
        // 确保分片数量合理（不要太多小分片）
        int maxShards = 100; // 最大分片数限制
        if (shardCount > maxShards) {
            logger.warn("计算得到的分片数量({})超过最大限制({}), 将使用最大限制", shardCount, maxShards);
            return maxShards;
        }
        
        return shardCount;
    }
    
    /**
     * 获取数据类型的描述
     * 
     * @param data 数据
     * @return 数据类型描述
     */
    public String getDataTypeDescription(byte[] data) {
        DataType dataType = detectDataType(data);
        return String.format("%s (估算压缩率: %.1f%%)", 
                           dataType.name(), 
                           dataType.getEstimatedCompressionRatio() * 100);
    }
}