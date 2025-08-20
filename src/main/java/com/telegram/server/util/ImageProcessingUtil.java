package com.telegram.server.util;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.io.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDateTime;
import java.util.Base64;

/**
 * 图片处理工具类
 * 
 * 提供图片文件的处理功能，包括：
 * - 图片文件的Base64编码和解码
 * - 图片文件大小检查
 * - MIME类型检测
 * - 文件名处理
 * - 图片存储策略判断
 * 
 * 存储策略：
 * - 小于1MB的图片：转换为Base64编码存储在MongoDB中
 * - 大于1MB的图片：只存储文件路径，文件保存在本地
 * 
 * @author liubo
 * @date 2025-01-19
 */
@Component
public class ImageProcessingUtil {

    private static final Logger logger = LoggerFactory.getLogger(ImageProcessingUtil.class);
    
    /**
     * 图片大小阈值（1MB）
     * 小于此大小的图片将转换为Base64存储
     */
    public static final long IMAGE_SIZE_THRESHOLD = 1024 * 1024; // 1MB
    
    /**
     * 支持的图片MIME类型
     */
    private static final String[] SUPPORTED_IMAGE_TYPES = {
        "image/jpeg", "image/jpg", "image/png", "image/gif", 
        "image/webp", "image/bmp", "image/tiff"
    };

    /**
     * 将图片文件转换为Base64编码
     * 
     * @param filePath 图片文件路径
     * @return Base64编码字符串，失败返回null
     */
    public String convertImageToBase64(String filePath) {
        if (filePath == null || filePath.trim().isEmpty()) {
            logger.warn("图片文件路径为空");
            return null;
        }
        
        try {
            Path path = Paths.get(filePath);
            if (!Files.exists(path)) {
                logger.warn("图片文件不存在: {}", filePath);
                return null;
            }
            
            byte[] imageBytes = Files.readAllBytes(path);
            if (imageBytes.length == 0) {
                logger.warn("图片文件为空: {}", filePath);
                return null;
            }
            
            String base64 = Base64.getEncoder().encodeToString(imageBytes);
            logger.debug("图片转换为Base64成功: {} -> {} bytes", filePath, base64.length());
            return base64;
            
        } catch (IOException e) {
            logger.error("图片转换为Base64失败: {}", filePath, e);
            return null;
        }
    }

    /**
     * 将Base64编码转换为图片文件
     * 
     * @param base64Data Base64编码数据
     * @param outputPath 输出文件路径
     * @return 是否转换成功
     */
    public boolean convertBase64ToImage(String base64Data, String outputPath) {
        if (base64Data == null || base64Data.trim().isEmpty()) {
            logger.warn("Base64数据为空");
            return false;
        }
        
        if (outputPath == null || outputPath.trim().isEmpty()) {
            logger.warn("输出文件路径为空");
            return false;
        }
        
        try {
            byte[] imageBytes = Base64.getDecoder().decode(base64Data);
            Path path = Paths.get(outputPath);
            
            // 确保父目录存在
            Files.createDirectories(path.getParent());
            
            Files.write(path, imageBytes);
            logger.debug("Base64转换为图片文件成功: {} -> {}", base64Data.length(), outputPath);
            return true;
            
        } catch (Exception e) {
            logger.error("Base64转换为图片文件失败: {}", outputPath, e);
            return false;
        }
    }



    /**
     * 检测图片文件的MIME类型
     * 
     * @param filePath 图片文件路径
     * @return MIME类型，失败返回null
     */
    public String detectMimeType(String filePath) {
        if (filePath == null || filePath.trim().isEmpty()) {
            return null;
        }
        
        try {
            Path path = Paths.get(filePath);
            if (!Files.exists(path)) {
                return null;
            }
            
            String mimeType = Files.probeContentType(path);
            if (mimeType == null) {
                // 根据文件扩展名推断MIME类型
                String fileName = path.getFileName().toString().toLowerCase();
                if (fileName.endsWith(".jpg") || fileName.endsWith(".jpeg")) {
                    mimeType = "image/jpeg";
                } else if (fileName.endsWith(".png")) {
                    mimeType = "image/png";
                } else if (fileName.endsWith(".gif")) {
                    mimeType = "image/gif";
                } else if (fileName.endsWith(".webp")) {
                    mimeType = "image/webp";
                } else if (fileName.endsWith(".bmp")) {
                    mimeType = "image/bmp";
                } else if (fileName.endsWith(".tiff") || fileName.endsWith(".tif")) {
                    mimeType = "image/tiff";
                }
            }
            
            return mimeType;
        } catch (IOException e) {
            logger.error("检测MIME类型失败: {}", filePath, e);
            return null;
        }
    }

    /**
     * 判断是否为支持的图片类型
     * 
     * @param mimeType MIME类型
     * @return 是否支持
     */
    public boolean isSupportedImageType(String mimeType) {
        if (mimeType == null || mimeType.trim().isEmpty()) {
            return false;
        }
        
        for (String supportedType : SUPPORTED_IMAGE_TYPES) {
            if (supportedType.equalsIgnoreCase(mimeType.trim())) {
                return true;
            }
        }
        return false;
    }

    /**
     * 判断图片是否应该存储为Base64
     * 
     * @param fileSize 文件大小（字节）
     * @return 是否应该存储为Base64
     */
    public boolean shouldStoreAsBase64(long fileSize) {
        return fileSize > 0 && fileSize <= IMAGE_SIZE_THRESHOLD;
    }

    /**
     * 从文件路径提取文件名
     * 
     * @param filePath 文件路径
     * @return 文件名，失败返回null
     */
    public String extractFileName(String filePath) {
        if (filePath == null || filePath.trim().isEmpty()) {
            return null;
        }
        
        try {
            Path path = Paths.get(filePath);
            return path.getFileName().toString();
        } catch (Exception e) {
            logger.error("提取文件名失败: {}", filePath, e);
            return null;
        }
    }



    /**
     * 检查文件是否存在
     * 
     * @param filePath 文件路径
     * @return 文件是否存在
     */
    public boolean fileExists(String filePath) {
        if (filePath == null || filePath.trim().isEmpty()) {
            return false;
        }
        
        try {
            return Files.exists(Paths.get(filePath));
        } catch (Exception e) {
            logger.error("检查文件是否存在失败: {}", filePath, e);
            return false;
        }
    }

    /**
     * 删除文件
     * 
     * @param filePath 文件路径
     * @return 是否删除成功
     */
    public boolean deleteFile(String filePath) {
        if (filePath == null || filePath.trim().isEmpty()) {
            return false;
        }
        
        try {
            Path path = Paths.get(filePath);
            if (Files.exists(path)) {
                Files.delete(path);
                logger.debug("文件删除成功: {}", filePath);
                return true;
            }
            return true; // 文件不存在也算删除成功
        } catch (IOException e) {
            logger.error("文件删除失败: {}", filePath, e);
            return false;
        }
    }


}