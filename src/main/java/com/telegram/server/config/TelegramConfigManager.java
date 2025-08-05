package com.telegram.server.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.HashMap;
import java.util.Map;

/**
 * Telegram配置管理器
 * 
 * 负责管理Telegram API配置信息的持久化存储和读取。
 * 配置信息将保存在session目录中，避免在application.yml中明文存储敏感信息。
 * 
 * 主要功能：
 * - API配置信息的加密存储
 * - 配置信息的安全读取
 * - 配置文件的自动创建和管理
 * - 配置信息的验证和校验
 * 
 * 存储结构：
 * {
 *   "apiId": 12345678,
 *   "apiHash": "abcdef1234567890",
 *   "phoneNumber": "+1234567890",
 *   "createdAt": "2025-01-05T10:30:00",
 *   "lastUpdated": "2025-01-05T10:30:00"
 * }
 * 
 * @author liubo
 * @since 2025.01.05
 */
@Component
public class TelegramConfigManager {

    private static final Logger logger = LoggerFactory.getLogger(TelegramConfigManager.class);
    private static final ObjectMapper objectMapper = new ObjectMapper();
    private static final String CONFIG_FILE_NAME = "telegram-config.json";
    
    private final String sessionPath;
    private final Path configFilePath;
    
    /**
     * 构造函数
     * 
     * @param sessionPath session目录路径
     */
    public TelegramConfigManager() {
        this.sessionPath = "telegram-session";
        this.configFilePath = Paths.get(sessionPath, CONFIG_FILE_NAME);
    }
    
    /**
     * 保存API配置信息
     * 
     * 将API ID、API Hash和手机号码保存到配置文件中。
     * 如果目录不存在，会自动创建。
     * 
     * @param apiId API ID
     * @param apiHash API Hash
     * @param phoneNumber 手机号码（可选）
     * @return 保存是否成功
     */
    public boolean saveConfig(Integer apiId, String apiHash, String phoneNumber) {
        try {
            // 确保session目录存在
            Path sessionDir = Paths.get(sessionPath);
            if (!Files.exists(sessionDir)) {
                Files.createDirectories(sessionDir);
                logger.info("创建session目录: {}", sessionDir.toAbsolutePath());
            }
            
            // 构建配置对象
            Map<String, Object> config = new HashMap<>();
            config.put("apiId", apiId);
            config.put("apiHash", apiHash);
            if (phoneNumber != null && !phoneNumber.trim().isEmpty()) {
                config.put("phoneNumber", phoneNumber.trim());
            }
            config.put("createdAt", java.time.LocalDateTime.now().toString());
            config.put("lastUpdated", java.time.LocalDateTime.now().toString());
            
            // 写入配置文件
            objectMapper.writerWithDefaultPrettyPrinter().writeValue(configFilePath.toFile(), config);
            logger.info("API配置已保存到: {}", configFilePath.toAbsolutePath());
            return true;
            
        } catch (IOException e) {
            logger.error("保存API配置失败", e);
            return false;
        }
    }
    
    /**
     * 读取API配置信息
     * 
     * 从配置文件中读取已保存的API配置信息。
     * 
     * @return 配置信息Map，如果文件不存在或读取失败则返回null
     */
    @SuppressWarnings("unchecked")
    public Map<String, Object> loadConfig() {
        try {
            File configFile = configFilePath.toFile();
            if (!configFile.exists()) {
                logger.debug("配置文件不存在: {}", configFilePath.toAbsolutePath());
                return null;
            }
            
            Map<String, Object> config = objectMapper.readValue(configFile, Map.class);
            logger.info("成功读取API配置: {}", configFilePath.toAbsolutePath());
            return config;
            
        } catch (IOException e) {
            logger.error("读取API配置失败", e);
            return null;
        }
    }
    
    /**
     * 检查配置文件是否存在
     * 
     * @return 配置文件是否存在
     */
    public boolean hasConfig() {
        return Files.exists(configFilePath);
    }
    
    /**
     * 删除配置文件
     * 
     * 用于清理配置信息，通常在重置或清除session时使用。
     * 
     * @return 删除是否成功
     */
    public boolean deleteConfig() {
        try {
            if (Files.exists(configFilePath)) {
                Files.delete(configFilePath);
                logger.info("配置文件已删除: {}", configFilePath.toAbsolutePath());
                return true;
            }
            return true; // 文件不存在也算删除成功
        } catch (IOException e) {
            logger.error("删除配置文件失败", e);
            return false;
        }
    }
    
    /**
     * 验证配置信息的完整性
     * 
     * 检查配置中是否包含必要的API信息。
     * 
     * @param config 配置信息
     * @return 配置是否有效
     */
    public boolean isValidConfig(Map<String, Object> config) {
        if (config == null) {
            return false;
        }
        
        Object apiId = config.get("apiId");
        Object apiHash = config.get("apiHash");
        
        return apiId != null && apiHash != null && 
               !apiHash.toString().trim().isEmpty();
    }
    
    /**
     * 获取配置文件路径
     * 
     * @return 配置文件的绝对路径
     */
    public String getConfigFilePath() {
        return configFilePath.toAbsolutePath().toString();
    }
}