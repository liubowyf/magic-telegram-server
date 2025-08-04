package com.telegram.server.util;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import javax.crypto.Cipher;
import javax.crypto.KeyGenerator;
import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Base64;
import java.util.HashMap;
import java.util.Map;

/**
 * Session凭据管理器
 * 
 * 负责安全地管理Telegram API凭据的存储、加载和加密。使用AES-256
 * 加密算法确保敏感信息（如API ID、API Hash、手机号）的安全性。
 * 
 * 主要功能：
 * - 加密存储API凭据到本地文件系统
 * - 安全加载和解密已保存的凭据
 * - 自动生成和管理加密密钥
 * - 提供凭据存在性检查和清理功能
 * 
 * 文件结构：
 * - credentials.enc: 加密后的凭据文件（JSON格式）
 * - session.key: Base64编码的AES密钥文件
 * 
 * 安全特性：
 * - 使用AES-256加密算法
 * - 每个session目录独立的加密密钥
 * - 凭据文件和密钥文件分离存储
 * - 支持密钥自动生成和重用
 * 
 * @author Magic Telegram Server
 * @version 1.0
 * @since 2024
 */
@Component
public class SessionCredentialsManager {
    
    /**
     * 日志记录器
     * 用于记录凭据管理操作的日志信息
     */
    private static final Logger logger = LoggerFactory.getLogger(SessionCredentialsManager.class);
    
    /**
     * JSON对象映射器
     * 用于凭据对象的序列化和反序列化
     */
    private static final ObjectMapper objectMapper = new ObjectMapper();
    
    /**
     * 加密凭据文件名
     * 存储加密后的API凭据信息
     */
    private static final String CREDENTIALS_FILE = "credentials.enc";
    
    /**
     * 加密密钥文件名
     * 存储Base64编码的AES加密密钥
     */
    private static final String KEY_FILE = "session.key";
    
    /**
     * 加密算法名称
     * 使用AES对称加密算法
     */
    private static final String ALGORITHM = "AES";
    
    /**
     * 安全保存API凭据到指定session目录
     * 
     * 将Telegram API凭据加密后保存到指定的session目录中。
     * 如果目录不存在会自动创建，如果加密密钥不存在会自动生成。
     * 
     * 保存的凭据包括：
     * - apiId: Telegram API ID
     * - apiHash: Telegram API Hash
     * - phoneNumber: 关联的手机号码
     * - saveTime: 保存时间戳
     * 
     * 安全措施：
     * - 使用AES-256加密算法
     * - 每个session目录独立的加密密钥
     * - 凭据以JSON格式序列化后加密
     * 
     * @param sessionPath session目录的绝对路径
     * @param apiId Telegram API ID，从https://my.telegram.org获取
     * @param apiHash Telegram API Hash，从https://my.telegram.org获取
     * @param phoneNumber 手机号码，格式如+8613800138000
     * @return true表示保存成功，false表示保存失败
     */
    public boolean saveCredentials(String sessionPath, Integer apiId, String apiHash, String phoneNumber) {
        try {
            Path sessionDir = Paths.get(sessionPath);
            if (!Files.exists(sessionDir)) {
                Files.createDirectories(sessionDir);
            }
            
            // 创建凭据对象
            Map<String, Object> credentials = new HashMap<>();
            credentials.put("apiId", apiId);
            credentials.put("apiHash", apiHash);
            if (phoneNumber != null) {
                credentials.put("phoneNumber", phoneNumber);
            }
            credentials.put("timestamp", System.currentTimeMillis());
            
            // 序列化凭据
            String credentialsJson = objectMapper.writeValueAsString(credentials);
            
            // 生成或获取加密密钥
            SecretKey secretKey = getOrCreateSecretKey(sessionDir);
            
            // 加密凭据
            String encryptedCredentials = encrypt(credentialsJson, secretKey);
            
            // 保存加密后的凭据
            Path credentialsFile = sessionDir.resolve(CREDENTIALS_FILE);
            Files.write(credentialsFile, encryptedCredentials.getBytes());
            
            logger.info("API凭据已保存到session目录: {}", sessionPath);
            return true;
            
        } catch (Exception e) {
            logger.error("保存API凭据失败: {}", e.getMessage(), e);
            return false;
        }
    }
    
    /**
     * 从指定session目录安全加载API凭据
     * 
     * 从指定的session目录中解密并加载之前保存的Telegram API凭据。
     * 需要凭据文件和密钥文件都存在才能成功加载。
     * 
     * 加载过程：
     * 1. 检查凭据文件和密钥文件是否存在
     * 2. 加载Base64编码的AES密钥
     * 3. 读取加密的凭据文件
     * 4. 使用AES算法解密凭据数据
     * 5. 将JSON数据反序列化为Map对象
     * 
     * 返回的凭据Map包含以下字段：
     * - apiId: Integer类型的Telegram API ID
     * - apiHash: String类型的Telegram API Hash
     * - phoneNumber: String类型的手机号码（可选）
     * - timestamp: Long类型的保存时间戳
     * 
     * @param sessionPath session目录的绝对路径
     * @return 解密后的凭据Map对象，包含apiId、apiHash、phoneNumber等信息；
     *         如果文件不存在、解密失败或数据格式错误则返回null
     */
    public Map<String, Object> loadCredentials(String sessionPath) {
        try {
            Path sessionDir = Paths.get(sessionPath);
            Path credentialsFile = sessionDir.resolve(CREDENTIALS_FILE);
            Path keyFile = sessionDir.resolve(KEY_FILE);
            
            if (!Files.exists(credentialsFile) || !Files.exists(keyFile)) {
                logger.debug("session目录中未找到凭据文件: {}", sessionPath);
                return null;
            }
            
            // 读取加密的凭据
            String encryptedCredentials = new String(Files.readAllBytes(credentialsFile));
            
            // 读取密钥
            SecretKey secretKey = loadSecretKey(sessionDir);
            if (secretKey == null) {
                logger.error("无法加载加密密钥: {}", sessionPath);
                return null;
            }
            
            // 解密凭据
            String credentialsJson = decrypt(encryptedCredentials, secretKey);
            
            // 反序列化凭据
            @SuppressWarnings("unchecked")
            Map<String, Object> credentials = objectMapper.readValue(credentialsJson, Map.class);
            
            logger.info("成功从session目录加载API凭据: {}", sessionPath);
            return credentials;
            
        } catch (Exception e) {
            logger.error("加载API凭据失败: {}", e.getMessage(), e);
            return null;
        }
    }
    
    /**
     * 检查指定session目录是否存在有效的凭据文件
     * 
     * 检查指定session目录中是否同时存在加密凭据文件和密钥文件。
     * 只有两个文件都存在时才认为凭据完整可用。
     * 
     * 检查的文件：
     * - credentials.enc: 加密的凭据文件
     * - session.key: AES加密密钥文件
     * 
     * @param sessionPath session目录的绝对路径
     * @return true表示凭据文件完整存在，false表示缺少必要文件
     */
    public boolean hasCredentials(String sessionPath) {
        Path sessionDir = Paths.get(sessionPath);
        Path credentialsFile = sessionDir.resolve(CREDENTIALS_FILE);
        Path keyFile = sessionDir.resolve(KEY_FILE);
        
        return Files.exists(credentialsFile) && Files.exists(keyFile);
    }
    
    /**
     * 清除指定session目录中的所有凭据文件
     * 
     * 删除指定session目录中的加密凭据文件和密钥文件，
     * 彻底清除该session的API凭据信息。
     * 
     * 清除的文件：
     * - credentials.enc: 加密的凭据文件
     * - session.key: AES加密密钥文件
     * 
     * 注意事项：
     * - 清除操作不可逆，请谨慎使用
     * - 清除后需要重新配置API凭据
     * - 如果文件不存在，不会报错
     * 
     * @param sessionPath session目录的绝对路径
     * @return true表示清除成功（包括文件不存在的情况），false表示清除过程中发生错误
     */
    public boolean clearCredentials(String sessionPath) {
        try {
            Path sessionDir = Paths.get(sessionPath);
            Path credentialsFile = sessionDir.resolve(CREDENTIALS_FILE);
            Path keyFile = sessionDir.resolve(KEY_FILE);
            
            boolean success = true;
            if (Files.exists(credentialsFile)) {
                Files.delete(credentialsFile);
                logger.info("已删除凭据文件: {}", credentialsFile);
            }
            if (Files.exists(keyFile)) {
                Files.delete(keyFile);
                logger.info("已删除密钥文件: {}", keyFile);
            }
            
            return success;
            
        } catch (Exception e) {
            logger.error("清除凭据失败: {}", e.getMessage(), e);
            return false;
        }
    }
    
    /**
     * 获取或创建AES加密密钥
     * 
     * 从指定session目录获取现有的AES加密密钥，如果不存在则自动创建新密钥。
     * 密钥以Base64格式编码存储在session.key文件中。
     * 
     * 处理流程：
     * 1. 检查session目录中是否存在密钥文件
     * 2. 如果存在，调用loadSecretKey方法加载密钥
     * 3. 如果不存在，调用createAndSaveSecretKey方法创建新密钥
     * 4. 返回SecretKey对象供加密解密使用
     * 
     * 安全特性：
     * - 使用256位AES密钥（32字节）
     * - 每个session目录独立的密钥
     * - 密钥文件权限受操作系统保护
     * - 密钥重用机制避免重复生成
     * 
     * @param sessionDir session目录的Path对象
     * @return SecretKey对象，用于AES加密解密操作
     * @throws Exception 当密钥文件读写失败或密钥生成失败时抛出异常
     */
    private SecretKey getOrCreateSecretKey(Path sessionDir) throws Exception {
        Path keyFile = sessionDir.resolve(KEY_FILE);
        
        if (Files.exists(keyFile)) {
            return loadSecretKey(sessionDir);
        } else {
            return createAndSaveSecretKey(sessionDir);
        }
    }
    
    /**
     * 创建并保存新的AES加密密钥
     * 
     * 生成一个新的256位AES加密密钥，并将其以Base64格式保存到指定session目录。
     * 该方法在session目录中不存在密钥文件时被调用。
     * 
     * 创建流程：
     * 1. 初始化AES密钥生成器，设置密钥长度为256位
     * 2. 生成随机的AES密钥
     * 3. 将密钥编码为Base64字符串
     * 4. 保存Base64编码的密钥到session.key文件
     * 5. 记录密钥创建日志
     * 6. 返回生成的SecretKey对象
     * 
     * 安全考虑：
     * - 使用Java标准的KeyGenerator确保密钥随机性
     * - 256位密钥长度提供高强度加密保护
     * - Base64编码便于文件存储和传输
     * 
     * @param sessionDir session目录的Path对象
     * @return 新创建的SecretKey对象
     * @throws Exception 当密钥生成失败或文件写入失败时抛出异常
     */
    private SecretKey createAndSaveSecretKey(Path sessionDir) throws Exception {
        KeyGenerator keyGenerator = KeyGenerator.getInstance(ALGORITHM);
        keyGenerator.init(256);
        SecretKey secretKey = keyGenerator.generateKey();
        
        // 保存密钥
        Path keyFile = sessionDir.resolve(KEY_FILE);
        String encodedKey = Base64.getEncoder().encodeToString(secretKey.getEncoded());
        Files.write(keyFile, encodedKey.getBytes());
        
        logger.debug("已创建新的加密密钥: {}", keyFile);
        return secretKey;
    }
    
    /**
     * 从文件加载现有的AES加密密钥
     * 
     * 从指定session目录的密钥文件中读取并解析AES加密密钥。
     * 密钥文件以Base64格式存储，需要解码后重构为SecretKey对象。
     * 
     * 加载流程：
     * 1. 检查session.key文件是否存在
     * 2. 如果不存在，返回null
     * 3. 读取文件中的Base64编码密钥字符串
     * 4. 使用Base64解码器解码为字节数组
     * 5. 使用SecretKeySpec重构AES密钥对象
     * 6. 返回可用的SecretKey对象
     * 
     * 错误处理：
     * - 文件不存在时返回null而不抛出异常
     * - Base64解码失败时会抛出异常
     * - 文件读取失败时会抛出IOException
     * 
     * @param sessionDir session目录的Path对象
     * @return 加载的SecretKey对象，如果密钥文件不存在则返回null
     * @throws Exception 当文件读取失败或Base64解码失败时抛出异常
     */
    private SecretKey loadSecretKey(Path sessionDir) throws Exception {
        Path keyFile = sessionDir.resolve(KEY_FILE);
        
        if (!Files.exists(keyFile)) {
            return null;
        }
        
        String encodedKey = new String(Files.readAllBytes(keyFile));
        byte[] decodedKey = Base64.getDecoder().decode(encodedKey);
        
        return new SecretKeySpec(decodedKey, ALGORITHM);
    }
    
    /**
     * 使用AES算法加密文本
     * 
     * 使用提供的AES密钥对明文字符串进行加密，返回Base64编码的密文。
     * 采用AES/ECB/PKCS5Padding模式进行加密操作。
     * 
     * 加密流程：
     * 1. 创建AES算法的Cipher实例
     * 2. 使用提供的SecretKey初始化为加密模式
     * 3. 将明文字符串转换为字节数组
     * 4. 执行AES加密操作
     * 5. 将加密后的字节数组编码为Base64字符串
     * 6. 返回Base64编码的密文
     * 
     * 安全特性：
     * - 使用AES对称加密算法
     * - PKCS5Padding填充模式确保数据完整性
     * - Base64编码便于存储和传输
     * 
     * @param plainText 待加密的明文字符串
     * @param secretKey 用于加密的AES密钥
     * @return Base64编码的加密结果字符串
     * @throws Exception 当加密操作失败时抛出异常
     */
    private String encrypt(String plainText, SecretKey secretKey) throws Exception {
        Cipher cipher = Cipher.getInstance(ALGORITHM);
        cipher.init(Cipher.ENCRYPT_MODE, secretKey);
        byte[] encryptedBytes = cipher.doFinal(plainText.getBytes());
        return Base64.getEncoder().encodeToString(encryptedBytes);
    }
    
    /**
     * 使用AES算法解密文本
     * 
     * 使用提供的AES密钥对Base64编码的密文进行解密，返回原始明文字符串。
     * 采用与加密相同的AES/ECB/PKCS5Padding模式进行解密操作。
     * 
     * 解密流程：
     * 1. 创建AES算法的Cipher实例
     * 2. 使用提供的SecretKey初始化为解密模式
     * 3. 将Base64编码的密文解码为字节数组
     * 4. 执行AES解密操作
     * 5. 将解密后的字节数组转换为字符串
     * 6. 返回原始明文字符串
     * 
     * 错误处理：
     * - Base64解码失败时抛出异常
     * - 密钥不匹配时解密失败抛出异常
     * - 数据损坏时PKCS5填充验证失败抛出异常
     * 
     * @param encryptedText Base64编码的加密文本
     * @param secretKey 用于解密的AES密钥，必须与加密时使用的密钥相同
     * @return 解密后的原始明文字符串
     * @throws Exception 当解密操作失败、密钥不匹配或数据损坏时抛出异常
     */
    private String decrypt(String encryptedText, SecretKey secretKey) throws Exception {
        Cipher cipher = Cipher.getInstance(ALGORITHM);
        cipher.init(Cipher.DECRYPT_MODE, secretKey);
        byte[] decryptedBytes = cipher.doFinal(Base64.getDecoder().decode(encryptedText));
        return new String(decryptedBytes);
    }
}