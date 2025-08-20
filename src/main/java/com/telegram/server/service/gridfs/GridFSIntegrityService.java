package com.telegram.server.service.gridfs;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;

/**
 * GridFS完整性校验服务类
 * 提供数据完整性校验功能
 * 
 * @author liubo
 * @date 2025-01-19
 */
@Service
@ConfigurationProperties(prefix = "session.storage.gridfs.integrity")
public class GridFSIntegrityService {

    private static final Logger logger = LoggerFactory.getLogger(GridFSIntegrityService.class);

    /** 是否启用完整性校验 */
    private boolean enabled = true;
    
    /** 校验算法 */
    private String algorithm = "sha256";

    /**
     * 计算数据的校验和
     * 
     * @param data 数据
     * @return 校验和
     * @throws IntegrityException 校验异常
     */
    public String calculateChecksum(byte[] data) throws IntegrityException {
        if (!enabled) {
            return null;
        }

        if (data == null || data.length == 0) {
            return calculateEmptyChecksum();
        }

        try {
            MessageDigest digest = MessageDigest.getInstance(algorithm.toUpperCase());
            byte[] hashBytes = digest.digest(data);
            String checksum = HexFormat.of().formatHex(hashBytes);
            
            logger.debug("校验和计算成功: algorithm={}, dataSize={}, checksum={}", 
                algorithm, data.length, checksum.substring(0, Math.min(16, checksum.length())) + "...");
            
            return checksum;
        } catch (NoSuchAlgorithmException e) {
            logger.error("不支持的校验算法: algorithm={}", algorithm, e);
            throw new IntegrityException("不支持的校验算法: " + algorithm, e);
        } catch (Exception e) {
            logger.error("校验和计算失败: algorithm={}, dataSize={}, error={}", 
                algorithm, data != null ? data.length : 0, e.getMessage(), e);
            throw new IntegrityException("校验和计算失败: " + e.getMessage(), e);
        }
    }

    /**
     * 验证数据完整性
     * 
     * @param data 数据
     * @param expectedChecksum 期望的校验和
     * @return 验证结果
     * @throws IntegrityException 校验异常
     */
    public IntegrityResult verifyIntegrity(byte[] data, String expectedChecksum) throws IntegrityException {
        if (!enabled) {
            return new IntegrityResult(true, null, expectedChecksum, "完整性校验已禁用");
        }

        if (expectedChecksum == null || expectedChecksum.trim().isEmpty()) {
            logger.debug("期望校验和为空，跳过验证");
            return new IntegrityResult(true, null, null, "期望校验和为空");
        }

        try {
            String actualChecksum = calculateChecksum(data);
            boolean isValid = expectedChecksum.equalsIgnoreCase(actualChecksum);
            
            if (isValid) {
                logger.debug("数据完整性验证通过: dataSize={}, checksum={}", 
                    data != null ? data.length : 0, 
                    actualChecksum != null ? actualChecksum.substring(0, Math.min(16, actualChecksum.length())) + "..." : "null");
                return new IntegrityResult(true, actualChecksum, expectedChecksum, "验证通过");
            } else {
                logger.warn("数据完整性验证失败: dataSize={}, expected={}, actual={}", 
                    data != null ? data.length : 0, 
                    expectedChecksum.substring(0, Math.min(16, expectedChecksum.length())) + "...",
                    actualChecksum != null ? actualChecksum.substring(0, Math.min(16, actualChecksum.length())) + "..." : "null");
                return new IntegrityResult(false, actualChecksum, expectedChecksum, "校验和不匹配");
            }
        } catch (Exception e) {
            logger.error("数据完整性验证异常: dataSize={}, expectedChecksum={}, error={}", 
                data != null ? data.length : 0, expectedChecksum, e.getMessage(), e);
            throw new IntegrityException("数据完整性验证异常: " + e.getMessage(), e);
        }
    }

    /**
     * 快速验证数据完整性（仅比较校验和）
     * 
     * @param actualChecksum 实际校验和
     * @param expectedChecksum 期望校验和
     * @return 是否匹配
     */
    public boolean quickVerify(String actualChecksum, String expectedChecksum) {
        if (!enabled) {
            return true;
        }

        if (actualChecksum == null && expectedChecksum == null) {
            return true;
        }

        if (actualChecksum == null || expectedChecksum == null) {
            return false;
        }

        return actualChecksum.equalsIgnoreCase(expectedChecksum);
    }

    /**
     * 计算空数据的校验和
     * 
     * @return 空数据校验和
     * @throws IntegrityException 校验异常
     */
    private String calculateEmptyChecksum() throws IntegrityException {
        try {
            MessageDigest digest = MessageDigest.getInstance(algorithm.toUpperCase());
            byte[] hashBytes = digest.digest(new byte[0]);
            return HexFormat.of().formatHex(hashBytes);
        } catch (NoSuchAlgorithmException e) {
            throw new IntegrityException("不支持的校验算法: " + algorithm, e);
        }
    }

    /**
     * 获取支持的算法列表
     * 
     * @return 支持的算法
     */
    public String[] getSupportedAlgorithms() {
        return new String[]{"MD5", "SHA-1", "SHA-256", "SHA-512"};
    }

    /**
     * 检查算法是否支持
     * 
     * @param algorithm 算法名称
     * @return 是否支持
     */
    public boolean isAlgorithmSupported(String algorithm) {
        if (algorithm == null || algorithm.trim().isEmpty()) {
            return false;
        }
        
        try {
            MessageDigest.getInstance(algorithm.toUpperCase());
            return true;
        } catch (NoSuchAlgorithmException e) {
            return false;
        }
    }

    /**
     * 生成数据指纹（用于快速比较）
     * 
     * @param data 数据
     * @return 数据指纹
     */
    public String generateFingerprint(byte[] data) {
        if (data == null || data.length == 0) {
            return "empty";
        }
        
        // 使用数据长度和前后几个字节生成简单指纹
        StringBuilder fingerprint = new StringBuilder();
        fingerprint.append("len:").append(data.length);
        
        if (data.length > 0) {
            fingerprint.append(",head:");
            int headLen = Math.min(4, data.length);
            for (int i = 0; i < headLen; i++) {
                fingerprint.append(String.format("%02x", data[i] & 0xFF));
            }
        }
        
        if (data.length > 8) {
            fingerprint.append(",tail:");
            int tailStart = data.length - 4;
            for (int i = tailStart; i < data.length; i++) {
                fingerprint.append(String.format("%02x", data[i] & 0xFF));
            }
        }
        
        return fingerprint.toString();
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

    /**
     * 完整性验证结果类
     */
    public static class IntegrityResult {
        /** 是否验证通过 */
        private final boolean valid;
        /** 实际校验和 */
        private final String actualChecksum;
        /** 期望校验和 */
        private final String expectedChecksum;
        /** 验证消息 */
        private final String message;

        public IntegrityResult(boolean valid, String actualChecksum, String expectedChecksum, String message) {
            this.valid = valid;
            this.actualChecksum = actualChecksum;
            this.expectedChecksum = expectedChecksum;
            this.message = message;
        }

        public boolean isValid() {
            return valid;
        }

        public String getActualChecksum() {
            return actualChecksum;
        }

        public String getExpectedChecksum() {
            return expectedChecksum;
        }

        public String getMessage() {
            return message;
        }

        @Override
        public String toString() {
            return String.format("IntegrityResult{valid=%s, message='%s'}", valid, message);
        }
    }

    /**
     * 完整性校验异常
     */
    public static class IntegrityException extends Exception {
        public IntegrityException(String message) {
            super(message);
        }

        public IntegrityException(String message, Throwable cause) {
            super(message, cause);
        }
    }
}