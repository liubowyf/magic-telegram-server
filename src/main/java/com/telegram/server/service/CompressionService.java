package com.telegram.server.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.zip.GZIPInputStream;
import java.util.zip.GZIPOutputStream;

/**
 * 数据压缩服务
 * 提供数据压缩和解压缩功能，支持多种压缩算法
 * 
 * @author liubo
 * @date 2025-01-19
 */
@Service
public class CompressionService {

    private static final Logger logger = LoggerFactory.getLogger(CompressionService.class);

    /**
     * 压缩类型枚举
     */
    public enum CompressionType {
        NONE("none"),
        GZIP("gzip");

        private final String value;

        CompressionType(String value) {
            this.value = value;
        }

        public String getValue() {
            return value;
        }

        public static CompressionType fromValue(String value) {
            for (CompressionType type : values()) {
                if (type.value.equals(value)) {
                    return type;
                }
            }
            return NONE;
        }
    }

    /**
     * 压缩结果类
     */
    public static class CompressionResult {
        private final byte[] compressedData;
        private final long originalSize;
        private final long compressedSize;
        private final CompressionType compressionType;
        private final double compressionRatio;

        public CompressionResult(byte[] compressedData, long originalSize, long compressedSize, CompressionType compressionType) {
            this.compressedData = compressedData;
            this.originalSize = originalSize;
            this.compressedSize = compressedSize;
            this.compressionType = compressionType;
            this.compressionRatio = originalSize > 0 ? (double) compressedSize / originalSize : 1.0;
        }

        public byte[] getCompressedData() {
            return compressedData;
        }

        public long getOriginalSize() {
            return originalSize;
        }

        public long getCompressedSize() {
            return compressedSize;
        }

        public CompressionType getCompressionType() {
            return compressionType;
        }

        public double getCompressionRatio() {
            return compressionRatio;
        }

        public String getCompressedDataAsBase64() {
            return Base64.getEncoder().encodeToString(compressedData);
        }
    }

    /**
     * 压缩字符串数据
     * 
     * @param data 原始字符串数据
     * @param compressionType 压缩类型
     * @return 压缩结果
     * @throws IOException 压缩过程中的IO异常
     */
    public CompressionResult compress(String data, CompressionType compressionType) throws IOException {
        if (data == null || data.isEmpty()) {
            return new CompressionResult(new byte[0], 0, 0, compressionType);
        }

        byte[] originalBytes = data.getBytes(StandardCharsets.UTF_8);
        return compress(originalBytes, compressionType);
    }

    /**
     * 压缩字节数组数据
     * 
     * @param data 原始字节数组数据
     * @param compressionType 压缩类型
     * @return 压缩结果
     * @throws IOException 压缩过程中的IO异常
     */
    public CompressionResult compress(byte[] data, CompressionType compressionType) throws IOException {
        if (data == null || data.length == 0) {
            return new CompressionResult(new byte[0], 0, 0, compressionType);
        }

        long originalSize = data.length;
        byte[] compressedData;

        switch (compressionType) {
            case GZIP:
                compressedData = compressWithGzip(data);
                break;
            case NONE:
            default:
                compressedData = data;
                break;
        }

        long compressedSize = compressedData.length;
        
        logger.debug("压缩完成: 原始大小={} bytes, 压缩后大小={} bytes, 压缩率={:.2f}%, 压缩类型={}",
                originalSize, compressedSize, 
                (1.0 - (double) compressedSize / originalSize) * 100,
                compressionType.getValue());

        return new CompressionResult(compressedData, originalSize, compressedSize, compressionType);
    }

    /**
     * 解压缩数据为字符串
     * 
     * @param compressedData 压缩后的数据
     * @param compressionType 压缩类型
     * @return 解压缩后的字符串
     * @throws IOException 解压缩过程中的IO异常
     */
    public String decompressToString(byte[] compressedData, CompressionType compressionType) throws IOException {
        byte[] decompressedData = decompress(compressedData, compressionType);
        return new String(decompressedData, StandardCharsets.UTF_8);
    }

    /**
     * 解压缩Base64编码的数据为字符串
     * 
     * @param base64CompressedData Base64编码的压缩数据
     * @param compressionType 压缩类型
     * @return 解压缩后的字符串
     * @throws IOException 解压缩过程中的IO异常
     */
    public String decompressFromBase64ToString(String base64CompressedData, CompressionType compressionType) throws IOException {
        if (base64CompressedData == null || base64CompressedData.isEmpty()) {
            return "";
        }

        byte[] compressedData = Base64.getDecoder().decode(base64CompressedData);
        return decompressToString(compressedData, compressionType);
    }

    /**
     * 解压缩数据为字节数组
     * 
     * @param compressedData 压缩后的数据
     * @param compressionType 压缩类型
     * @return 解压缩后的字节数组
     * @throws IOException 解压缩过程中的IO异常
     */
    public byte[] decompress(byte[] compressedData, CompressionType compressionType) throws IOException {
        if (compressedData == null || compressedData.length == 0) {
            return new byte[0];
        }

        switch (compressionType) {
            case GZIP:
                return decompressWithGzip(compressedData);
            case NONE:
            default:
                return compressedData;
        }
    }

    /**
     * 使用GZIP压缩数据
     * 
     * @param data 原始数据
     * @return 压缩后的数据
     * @throws IOException 压缩过程中的IO异常
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
     * @param compressedData 压缩后的数据
     * @return 解压缩后的数据
     * @throws IOException 解压缩过程中的IO异常
     */
    private byte[] decompressWithGzip(byte[] compressedData) throws IOException {
        try (ByteArrayInputStream bais = new ByteArrayInputStream(compressedData);
             GZIPInputStream gzipIn = new GZIPInputStream(bais);
             ByteArrayOutputStream baos = new ByteArrayOutputStream()) {
            
            byte[] buffer = new byte[8192];
            int len;
            while ((len = gzipIn.read(buffer)) != -1) {
                baos.write(buffer, 0, len);
            }
            return baos.toByteArray();
        }
    }

    /**
     * 选择最佳压缩类型
     * 根据数据大小和内容特征自动选择最适合的压缩算法
     * 
     * @param data 原始数据
     * @return 推荐的压缩类型
     */
    public CompressionType selectBestCompressionType(byte[] data) {
        if (data == null || data.length == 0) {
            return CompressionType.NONE;
        }

        // 小于1KB的数据不压缩
        if (data.length < 1024) {
            return CompressionType.NONE;
        }

        // 默认使用GZIP压缩
        return CompressionType.GZIP;
    }

    /**
     * 计算压缩效果
     * 测试不同压缩算法的效果，返回最佳选择
     * 
     * @param data 原始数据
     * @return 最佳压缩类型
     */
    public CompressionType evaluateBestCompression(byte[] data) {
        if (data == null || data.length < 1024) {
            return CompressionType.NONE;
        }

        try {
            // 测试GZIP压缩效果
            CompressionResult gzipResult = compress(data, CompressionType.GZIP);
            
            // 如果压缩率低于10%，则不压缩
            if (gzipResult.getCompressionRatio() > 0.9) {
                logger.debug("压缩效果不佳，压缩率仅为{:.2f}%，选择不压缩", 
                        (1.0 - gzipResult.getCompressionRatio()) * 100);
                return CompressionType.NONE;
            }

            return CompressionType.GZIP;
        } catch (IOException e) {
            logger.warn("压缩测试失败，选择不压缩: {}", e.getMessage());
            return CompressionType.NONE;
        }
    }
}