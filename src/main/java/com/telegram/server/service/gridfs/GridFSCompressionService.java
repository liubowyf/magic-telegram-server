package com.telegram.server.service.gridfs;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Service;

import java.io.*;
import java.util.zip.GZIPInputStream;
import java.util.zip.GZIPOutputStream;

/**
 * GridFS压缩服务类
 * 提供数据压缩和解压缩功能
 * 
 * @author liubo
 * @date 2025-01-19
 */
@Service
@ConfigurationProperties(prefix = "session.storage.gridfs.compression")
public class GridFSCompressionService {

    private static final Logger logger = LoggerFactory.getLogger(GridFSCompressionService.class);

    /** 是否启用压缩 */
    private boolean enabled = true;
    
    /** 压缩算法 */
    private String algorithm = "gzip";
    
    /** 压缩阈值（字节），小于此值不压缩 */
    private long threshold = 1024 * 1024; // 1MB

    /**
     * 压缩数据
     * 
     * @param data 原始数据
     * @return 压缩结果
     * @throws CompressionException 压缩异常
     */
    public CompressionResult compress(byte[] data) throws CompressionException {
        if (data == null || data.length == 0) {
            return new CompressionResult(data, false, data != null ? data.length : 0, 0);
        }

        // 检查是否需要压缩
        if (!enabled || data.length < threshold) {
            logger.debug("数据无需压缩: enabled={}, size={}, threshold={}", enabled, data.length, threshold);
            return new CompressionResult(data, false, data.length, data.length);
        }

        try {
            byte[] compressedData = compressWithGzip(data);
            
            // 检查压缩效果，如果压缩后反而更大，则不压缩
            if (compressedData.length >= data.length) {
                logger.debug("压缩效果不佳，使用原始数据: originalSize={}, compressedSize={}", 
                    data.length, compressedData.length);
                return new CompressionResult(data, false, data.length, data.length);
            }
            
            logger.debug("数据压缩成功: originalSize={}, compressedSize={}, ratio={:.2f}%", 
                data.length, compressedData.length, 
                (1.0 - (double) compressedData.length / data.length) * 100);
            
            return new CompressionResult(compressedData, true, data.length, compressedData.length);
        } catch (Exception e) {
            logger.error("数据压缩失败: size={}, error={}", data.length, e.getMessage(), e);
            throw new CompressionException("数据压缩失败: " + e.getMessage(), e);
        }
    }

    /**
     * 解压缩数据
     * 
     * @param data 压缩数据
     * @param isCompressed 是否已压缩
     * @return 解压缩后的数据
     * @throws CompressionException 解压缩异常
     */
    public byte[] decompress(byte[] data, boolean isCompressed) throws CompressionException {
        if (data == null || data.length == 0) {
            return data;
        }

        if (!isCompressed) {
            logger.debug("数据未压缩，直接返回: size={}", data.length);
            return data;
        }

        try {
            byte[] decompressedData = decompressWithGzip(data);
            logger.debug("数据解压缩成功: compressedSize={}, decompressedSize={}", 
                data.length, decompressedData.length);
            return decompressedData;
        } catch (Exception e) {
            logger.error("数据解压缩失败: size={}, error={}", data.length, e.getMessage(), e);
            throw new CompressionException("数据解压缩失败: " + e.getMessage(), e);
        }
    }

    /**
     * 使用GZIP压缩数据
     * 
     * @param data 原始数据
     * @return 压缩后的数据
     * @throws IOException 压缩异常
     */
    private byte[] compressWithGzip(byte[] data) throws IOException {
        try (ByteArrayOutputStream baos = new ByteArrayOutputStream();
             GZIPOutputStream gzipOut = new GZIPOutputStream(baos)) {
            
            gzipOut.write(data);
            gzipOut.finish();
            return baos.toByteArray();
        }
    }

    /**
     * 使用GZIP解压缩数据
     * 
     * @param compressedData 压缩数据
     * @return 解压缩后的数据
     * @throws IOException 解压缩异常
     */
    private byte[] decompressWithGzip(byte[] compressedData) throws IOException {
        try (ByteArrayInputStream bais = new ByteArrayInputStream(compressedData);
             GZIPInputStream gzipIn = new GZIPInputStream(bais);
             ByteArrayOutputStream baos = new ByteArrayOutputStream()) {
            
            byte[] buffer = new byte[8192];
            int bytesRead;
            while ((bytesRead = gzipIn.read(buffer)) != -1) {
                baos.write(buffer, 0, bytesRead);
            }
            return baos.toByteArray();
        }
    }

    /**
     * 获取压缩比率
     * 
     * @param originalSize 原始大小
     * @param compressedSize 压缩后大小
     * @return 压缩比率（0-1之间）
     */
    public double getCompressionRatio(long originalSize, long compressedSize) {
        if (originalSize == 0) {
            return 0.0;
        }
        return 1.0 - (double) compressedSize / originalSize;
    }

    /**
     * 检查数据是否应该压缩
     * 
     * @param dataSize 数据大小
     * @return 是否应该压缩
     */
    public boolean shouldCompress(long dataSize) {
        return enabled && dataSize >= threshold;
    }

    // Getters and Setters
    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public String getAlgorithm() {
        return algorithm;
    }

    public void setAlgorithm(String algorithm) {
        this.algorithm = algorithm;
    }

    public long getThreshold() {
        return threshold;
    }

    public void setThreshold(long threshold) {
        this.threshold = threshold;
    }

    /**
     * 压缩结果类
     */
    public static class CompressionResult {
        /** 压缩后的数据 */
        private final byte[] data;
        /** 是否已压缩 */
        private final boolean compressed;
        /** 原始大小 */
        private final long originalSize;
        /** 压缩后大小 */
        private final long compressedSize;

        public CompressionResult(byte[] data, boolean compressed, long originalSize, long compressedSize) {
            this.data = data;
            this.compressed = compressed;
            this.originalSize = originalSize;
            this.compressedSize = compressedSize;
        }

        public byte[] getData() {
            return data;
        }

        public boolean isCompressed() {
            return compressed;
        }

        public long getOriginalSize() {
            return originalSize;
        }

        public long getCompressedSize() {
            return compressedSize;
        }

        public double getCompressionRatio() {
            if (originalSize == 0) {
                return 0.0;
            }
            return 1.0 - (double) compressedSize / originalSize;
        }
    }

    /**
     * 压缩异常
     */
    public static class CompressionException extends Exception {
        public CompressionException(String message) {
            super(message);
        }

        public CompressionException(String message, Throwable cause) {
            super(message, cause);
        }
    }
}