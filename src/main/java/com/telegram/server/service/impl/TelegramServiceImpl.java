package com.telegram.server.service.impl;

import it.tdlight.Init;
import it.tdlight.Log;
import it.tdlight.Slf4JLogMessageHandler;
import it.tdlight.client.APIToken;
import it.tdlight.client.AuthenticationSupplier;

import it.tdlight.client.SimpleTelegramClient;
import it.tdlight.client.SimpleTelegramClientBuilder;
import it.tdlight.client.SimpleTelegramClientFactory;
import it.tdlight.client.TDLibSettings;
import it.tdlight.jni.TdApi;
import it.tdlight.util.UnsupportedNativeLibraryException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.Map;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.telegram.server.config.TelegramConfigManager;
import com.telegram.server.entity.TelegramSession;
import com.telegram.server.entity.TelegramMessage;
import com.telegram.server.service.ITelegramService;
import com.telegram.server.service.ITelegramMessageService;
import com.telegram.server.service.ITelegramSessionService;
import org.springframework.beans.factory.annotation.Autowired;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.util.Base64;
import java.util.concurrent.CompletableFuture;
import java.util.Optional;
import java.util.List;
import com.telegram.server.util.ImageProcessingUtil;
import com.telegram.server.util.TimeZoneUtil;
import com.telegram.server.util.RetryHandler;
import com.telegram.server.util.PathValidator;
import com.telegram.server.config.TelegramConfig;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.Instant;

/**
 * 单账号Telegram服务类
 * 
 * 提供单个Telegram账号的完整管理功能，包括客户端初始化、认证流程、
 * 消息监听和状态管理。这是系统的核心服务类，负责与Telegram服务器
 * 的所有通信和交互。
 * 
 * 主要功能：
 * - TDLight客户端初始化和配置
 * - Telegram账号认证流程（手机号、验证码、密码）
 * - 实时消息接收和处理
 * - 代理服务器配置（SOCKS5）
 * - 连接状态监控和管理
 * - Session数据持久化
 * 
 * 认证流程：
 * 1. 配置API ID和API Hash
 * 2. 提交手机号码
 * 3. 提交短信验证码
 * 4. 如需要，提交两步验证密码
 * 5. 完成认证，开始消息监听
 * 
 * @author liubo
 * @version 1.0
 * @since 2025.08.01
 */
@Service
public class TelegramServiceImpl implements ITelegramService {

    /**
     * 日志记录器
     * 用于记录服务运行日志，便于调试和监控
     */
    private static final Logger logger = LoggerFactory.getLogger(TelegramServiceImpl.class);
    
    /**
     * JSON对象映射器
     * 用于处理消息的JSON序列化和反序列化
     */
    private static final ObjectMapper objectMapper = new ObjectMapper();
    
    /**
     * 日期时间格式化器
     * 统一的时间格式，用于消息时间戳格式化
     */
    private static final DateTimeFormatter dateTimeFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    /**
     * Telegram配置管理器
     * 负责API配置信息的持久化存储和读取
     */
    @Autowired
    private TelegramConfigManager configManager;
    
    /**
     * Telegram Session管理服务
     * 负责MongoDB中session数据的管理
     */
    @Autowired
    private ITelegramSessionService sessionService;
    
    /**
     * Telegram消息存储服务
     * 用于将接收到的群消息存储到MongoDB
     */
    @Autowired
    private ITelegramMessageService messageService;
    
    /**
     * 图片处理工具类
     */
    @Autowired
    private ImageProcessingUtil imageProcessingUtil;
    
    /**
     * 时区处理工具类
     */
    @Autowired
    private TimeZoneUtil timeZoneUtil;
    
    /**
     * TDLight重试处理器
     */
    @Autowired
    private RetryHandler tdlightRetryHandler;
    
    /**
     * 网络操作重试处理器
     */
    @Autowired
    private RetryHandler networkRetryHandler;
    
    /**
     * 路径验证工具类
     */
    @Autowired
    private PathValidator pathValidator;
    
    /**
     * Telegram配置类
     */
    @Autowired
    private TelegramConfig telegramConfig;
    
    /**
     * 当前使用的API ID
     * 从配置文件中读取，不再从application.yml获取
     */
    private Integer apiId;

    /**
     * 当前使用的API Hash
     * 从配置文件中读取，不再从application.yml获取
     */
    private String apiHash;

    /**
     * 当前使用的手机号码
     * 从配置文件中读取，不再从application.yml获取
     */
    private String phoneNumber;
    
    /**
     * 运行时动态配置的API ID
     * 通过REST API接口动态设置，优先级高于配置文件
     */
    private Integer runtimeApiId;
    
    /**
     * 运行时动态配置的API Hash
     * 通过REST API接口动态设置，优先级高于配置文件
     */
    private String runtimeApiHash;
    
    /**
     * 运行时动态配置的手机号码
     * 通过REST API接口动态设置，优先级高于配置文件
     */
    private String runtimePhoneNumber;

    /**
     * Telegram会话数据存储路径
     * 用于保存数据库文件和下载文件
     */
    @Value("${telegram.session.path:./telegram-session}")
    private String sessionPath;
    
    /**
     * 下载文件目录路径
     * 从配置文件读取，用于存储TDLib下载的文件
     */
    @Value("${telegram.session.downloads.path:${java.io.tmpdir}/telegram-downloads}")
    private String downloadsPath;
    
    /**
     * 下载临时目录路径
     * 从配置文件读取，用于存储TDLib下载过程中的临时文件
     */
    @Value("${telegram.session.downloads.temp-path:${java.io.tmpdir}/telegram-downloads/temp}")
    private String downloadsTempPath;

    /**
     * SOCKS5代理服务器主机地址
     * 用于网络代理连接
     */
    @Value("${proxy.socks5.host:127.0.0.1}")
    private String proxyHost;

    /**
     * SOCKS5代理服务器端口
     * 用于网络代理连接
     */
    @Value("${proxy.socks5.port:7890}")
    private int proxyPort;

    /**
     * Telegram客户端工厂
     * 用于创建和管理Telegram客户端实例
     */
    private SimpleTelegramClientFactory clientFactory;
    
    /**
     * Telegram客户端实例
     * 核心的Telegram通信客户端
     */
    private SimpleTelegramClient client;
    
    /**
     * 当前授权状态
     * 跟踪Telegram账号的认证状态
     */
    private TdApi.AuthorizationState currentAuthState;

    /**
     * 初始化Telegram服务
     */
    @PostConstruct
    public void init() {
        try {
            logger.info("正在初始化Telegram服务基础组件...");
            
            // 初始化Session管理服务
            sessionService.init();
            
            // 从MongoDB加载配置（优先）或从配置管理器加载配置
            loadConfigFromMongoDB();
            
            // 检查API配置，只有配置完整时才初始化TDLight库
            if (validateApiConfiguration()) {
                logger.info("检测到有效的API配置，正在初始化TDLight库...");
                
                // 初始化TDLight原生库
                Init.init();
                
                // 设置日志级别
                Log.setLogMessageHandler(1, new Slf4JLogMessageHandler());
                
                // 创建客户端工厂
                clientFactory = new SimpleTelegramClientFactory();
                
                logger.info("TDLight库初始化完成");
                
                // 自动尝试使用默认配置初始化客户端
                // 如果存在有效的session，将自动恢复登录状态
                autoInitializeClient();
            } else {
                logger.info("API配置不完整，跳过TDLight库初始化。应用将以配置模式启动。");
            }
            
            logger.info("Telegram服务基础组件初始化完成");
            
        } catch (Exception e) {
            logger.error("初始化Telegram服务失败", e);
            // 不抛出异常，允许应用继续启动
            logger.warn("Telegram服务初始化失败，但应用将继续启动以支持配置管理");
        }
    }
    
    /**
     * 从MongoDB加载配置信息
     * 
     * 优先从MongoDB中查找可用的session配置，如果没有找到则回退到配置文件。
     * 支持集群环境下的配置共享和负载均衡。
     * 
     * @author liubo
     * @since 2025.08.11
     */
    /**
     * 从MongoDB加载配置信息
     * 优先从MongoDB获取可用session，如果没有则回退到配置文件
     * 
     * @author liubo
     * @date 2025-08-25
     */
    private void loadConfigFromMongoDB() {
        try {
            if (loadFromAvailableSession()) {
                return;
            }
            
            // 如果MongoDB中没有可用session，回退到配置文件
            logger.info("MongoDB中没有可用session，尝试从配置文件加载");
            loadConfigFromManager();
            
            // 如果从配置文件加载成功，则迁移到MongoDB
            migrateConfigIfLoaded();
            
        } catch (Exception e) {
            logger.error("从MongoDB加载配置失败，回退到配置文件", e);
            loadConfigFromManager();
        }
    }
    
    /**
     * 从可用的session中加载配置
     * 
     * @return 是否成功加载
     * @author liubo
     * @date 2025-08-25
     */
    private boolean loadFromAvailableSession() {
        List<TelegramSession> availableSessions = sessionService.getAvailableSessions();
        
        if (availableSessions.isEmpty()) {
            return false;
        }
        
        // 选择第一个可用的session
        TelegramSession session = availableSessions.get(0);
        
        setConfigurationFromSession(session);
        activateAndRestoreSession(session);
        
        logger.info("成功从MongoDB加载session配置: {}", this.phoneNumber);
        return true;
    }
    
    /**
     * 从session设置配置信息
     * 
     * @param session Telegram会话
     * @author liubo
     * @date 2025-08-25
     */
    private void setConfigurationFromSession(TelegramSession session) {
        this.apiId = session.getApiId();
        this.apiHash = session.getApiHash();
        this.phoneNumber = session.getPhoneNumber();
        
        // 同时设置运行时配置
        this.runtimeApiId = this.apiId;
        this.runtimeApiHash = this.apiHash;
        this.runtimePhoneNumber = this.phoneNumber;
    }
    
    /**
     * 激活session并恢复session文件
     * 
     * @param session Telegram会话
     * @author liubo
     * @date 2025-08-25
     */
    private void activateAndRestoreSession(TelegramSession session) {
        // 激活此session
        sessionService.activateSession(session.getPhoneNumber());
        
        // 从MongoDB恢复session文件到本地
        sessionService.restoreSessionFiles(session.getPhoneNumber(), sessionPath);
    }
    
    /**
     * 如果配置已加载，则迁移到MongoDB
     * 
     * @author liubo
     * @date 2025-08-25
     */
    private void migrateConfigIfLoaded() {
        if (this.apiId != null && this.apiHash != null && this.phoneNumber != null) {
            migrateConfigToMongoDB();
        }
    }
    
    /**
     * 从配置管理器加载配置信息（回退方法）
     * 
     * 在服务启动时从持久化存储中读取API配置信息，
     * 如果配置文件存在且有效，则加载到内存中使用。
     * 
     * @author liubo
     * @since 2025.01.05
     */
    /**
     * 从配置管理器加载配置信息（已废弃）
     * 
     * 此方法已废弃，所有配置现在都从MongoDB加载。
     * 保留此方法仅为兼容性考虑。
     */
    @Deprecated
    private void loadConfigFromManager() {
        logger.debug("loadConfigFromManager方法已废弃，所有配置现在都从MongoDB加载");
        // 不再从本地文件加载配置，所有配置都从MongoDB获取
    }
    
    /**
     * 将配置迁移到MongoDB
     * 
     * 将从配置文件加载的配置信息迁移到MongoDB中，
     * 同时保存本地session文件数据。
     * 
     * @author liubo
     * @since 2025.08.11
     */
    private void migrateConfigToMongoDB() {
        try {
            if (this.apiId != null && this.apiHash != null && this.phoneNumber != null) {
                // 创建或更新MongoDB中的session
                TelegramSession session = sessionService.createOrUpdateSession(
                    this.phoneNumber, this.apiId, this.apiHash);
                
                // 保存本地session文件到MongoDB
                sessionService.saveSessionFiles(this.phoneNumber, sessionPath);
                
                // 激活session
                sessionService.activateSession(this.phoneNumber);
                
                logger.info("成功将配置迁移到MongoDB: {}", this.phoneNumber);
            }
        } catch (Exception e) {
            logger.error("迁移配置到MongoDB失败", e);
        }
    }
    
    /**
     * 保存session到MongoDB
     * 
     * 在认证成功后将当前session数据保存到MongoDB中，
     * 确保集群环境下的session数据同步。
     * 
     * @author liubo
     * @since 2025.08.11
     */
    private void saveSessionToMongoDB() {
        try {
            String currentPhoneNumber = runtimePhoneNumber != null ? runtimePhoneNumber : phoneNumber;
            Integer currentApiId = runtimeApiId != null ? runtimeApiId : apiId;
            String currentApiHash = runtimeApiHash != null ? runtimeApiHash : apiHash;
            
            if (currentPhoneNumber != null && currentApiId != null && currentApiHash != null) {
                // 创建或更新MongoDB中的session
                sessionService.createOrUpdateSession(currentPhoneNumber, currentApiId, currentApiHash);
                
                // 保存临时session文件到MongoDB
                sessionService.saveSessionFiles(currentPhoneNumber, sessionPath);
                
                // 更新session状态为已认证
                sessionService.updateAuthenticationStatus(currentPhoneNumber, true);
                sessionService.updateAuthState(currentPhoneNumber, "READY");
                
                logger.info("成功保存session到MongoDB: {}", currentPhoneNumber);
                
                // 异步清理临时文件（延迟执行，确保TDLight完成所有操作）
                CompletableFuture.runAsync(() -> {
                    try {
                        Thread.sleep(5000); // 等待5秒确保所有操作完成
                        cleanupTempSessionFiles();
                    } catch (Exception e) {
                        logger.warn("清理临时session文件时发生错误: {}", e.getMessage());
                    }
                });
            }
        } catch (Exception e) {
            logger.error("保存session到MongoDB失败", e);
        }
    }

    /**
     * 配置SOCKS5代理服务器
     * 
     * 为Telegram客户端配置SOCKS5代理，用于网络连接。
     * 代理配置将应用于所有的Telegram网络请求。
     * 
     * 配置参数从application.properties中读取：
     * - proxy.socks5.host: 代理服务器地址
     * - proxy.socks5.port: 代理服务器端口
     */
    private void configureProxy() {
        try {
            logger.info("正在配置SOCKS5代理: {}:{}", proxyHost, proxyPort);
            
            TdApi.AddProxy addProxy = new TdApi.AddProxy();
            addProxy.server = proxyHost;
            addProxy.port = proxyPort;
            addProxy.enable = true;
            addProxy.type = new TdApi.ProxyTypeSocks5(null, null);
            
            client.send(addProxy).whenComplete((result, throwable) -> {
                if (throwable != null) {
                    logger.error("配置代理失败", throwable);
                } else {
                    logger.info("代理配置成功: {}", result);
                }
            });
            
        } catch (Exception e) {
            logger.error("配置代理时发生错误", e);
        }
    }

    /**
     * 处理新消息更新事件
     * 
     * 当接收到新的Telegram消息时，此方法会被自动调用。
     * 只处理文本消息和图片消息，其他类型的消息将被丢弃。
     * 
     * 处理流程：
     * 1. 检查消息类型，只处理文本和图片消息
     * 2. 提取消息基本信息（ID、聊天ID、发送时间等）
     * 3. 异步获取聊天详细信息（群组名称、类型等）
     * 4. 解析消息内容和类型
     * 5. 生成完整的JSON格式消息对象
     * 6. 存储到MongoDB数据库
     * 
     * @param update 新消息更新事件，包含完整的消息信息
     * @author liubo
     * @date 2025-01-20
     */
    private void handleNewMessage(TdApi.UpdateNewMessage update) {
        try {
            TdApi.Message message = update.message;
            
            // 消息类型过滤：只处理文本消息和图片消息
            if (!isMessageTypeSupported(message)) {
                return;
            }
            
            // 异步获取聊天信息并处理消息
            fetchChatAndProcessMessage(message);
            
        } catch (Exception e) {
            logger.error("处理新消息时发生错误", e);
        }
    }
    
    /**
     * 检查消息类型是否支持处理
     * 
     * @param message 消息对象
     * @return 如果消息类型支持则返回true，否则返回false
     * @author liubo
     * @date 2025-01-20
     */
    private boolean isMessageTypeSupported(TdApi.Message message) {
        boolean isTextMessage = message.content instanceof TdApi.MessageText;
        boolean isPhotoMessage = message.content instanceof TdApi.MessagePhoto;
        return isTextMessage || isPhotoMessage;
    }
    
    /**
     * 获取聊天信息并处理消息
     * 
     * @param message 消息对象
     * @author liubo
     * @date 2025-01-20
     */
    private void fetchChatAndProcessMessage(TdApi.Message message) {
        client.send(new TdApi.GetChat(message.chatId)).whenComplete((chat, throwable) -> {
            if (throwable == null) {
                processMessageWithChat(message, chat);
            } else {
                logger.error("获取聊天信息失败", throwable);
            }
        });
    }
    
    /**
     * 处理消息和聊天信息
     * 
     * @param message 消息对象
     * @param chat 聊天对象
     * @author liubo
     * @date 2025-01-20
     */
    private void processMessageWithChat(TdApi.Message message, TdApi.Chat chat) {
        try {
            String chatTitle = chat.title;
            String messageText = getMessageText(message.content);
            
            // 创建完整的JSON格式消息对象
            ObjectNode messageJson = createMessageJsonObject(message, chat, chatTitle, messageText);
            
            // 获取消息类型
            String contentType = getMessageContentType(message);
            
            // 处理图片消息的特殊逻辑
            if (message.content instanceof TdApi.MessagePhoto) {
                handlePhotoMessage(messageJson, (TdApi.MessagePhoto) message.content, message, chat);
            }
            
            // 异步存储消息到MongoDB
            saveMessageToMongoDB(message, chat, messageText, contentType, messageJson);
            
        } catch (Exception jsonException) {
            logger.error("生成JSON格式消息失败", jsonException);
        }
    }
    
    /**
     * 创建消息JSON对象
     * 
     * @param message 消息对象
     * @param chat 聊天对象
     * @param chatTitle 聊天标题
     * @param messageText 消息文本
     * @return 消息JSON对象
     * @author liubo
     * @date 2025-01-20
     */
    private ObjectNode createMessageJsonObject(TdApi.Message message, TdApi.Chat chat, String chatTitle, String messageText) {
        ObjectNode messageJson = objectMapper.createObjectNode();
        
        // 设置基础信息
        setBasicMessageInfo(messageJson, message, chatTitle, messageText);
        
        // 设置聊天类型信息
        setChatTypeInfo(messageJson, chat);
        
        // 设置时间信息
        setTimeInfo(messageJson, message);
        
        // 设置发送者信息
        setSenderInfo(messageJson, message);
        
        // 设置消息类型
        setMessageTypeInfo(messageJson, message);
        
        // 设置回复信息
        setReplyInfo(messageJson, message);
        
        // 设置转发信息
        setForwardInfo(messageJson, message);
        
        // 设置消息状态
        setMessageStatus(messageJson, message);
        
        // 设置线程和专辑信息
        setThreadAndAlbumInfo(messageJson, message);
        
        // 设置交互信息
        setInteractionInfo(messageJson, message);
        
        return messageJson;
    }
    
    /**
     * 设置消息基础信息
     * 
     * @param messageJson 消息JSON对象
     * @param message 消息对象
     * @param chatTitle 聊天标题
     * @param messageText 消息文本
     * @author liubo
     * @date 2025-01-20
     */
    private void setBasicMessageInfo(ObjectNode messageJson, TdApi.Message message, String chatTitle, String messageText) {
        // 基础信息 - 使用配置的时区显示接收时间
        LocalDateTime receiveTimeUtc = TimeZoneUtil.convertUnixToUtc(Instant.now().getEpochSecond());
        LocalDateTime receiveTime = TimeZoneUtil.convertUtcToChina(receiveTimeUtc);
        messageJson.put("接收时间", String.format("【%s】", receiveTime.format(dateTimeFormatter)));
        messageJson.put("消息ID", String.format("【%d】", message.id));
        messageJson.put("聊天ID", String.format("【%d】", message.chatId));
        messageJson.put("群组名称", String.format("【%s】", chatTitle));
        messageJson.put("消息内容", String.format("【%s】", messageText));
    }
    
    /**
     * 设置聊天类型信息
     * 
     * @param messageJson 消息JSON对象
     * @param chat 聊天对象
     * @author liubo
     * @date 2025-01-20
     */
    private void setChatTypeInfo(ObjectNode messageJson, TdApi.Chat chat) {
        String chatType = "【未知】";
        if (chat.type instanceof TdApi.ChatTypePrivate) {
            chatType = "【私聊】";
        } else if (chat.type instanceof TdApi.ChatTypeBasicGroup) {
            chatType = "【基础群组】";
        } else if (chat.type instanceof TdApi.ChatTypeSupergroup) {
            TdApi.ChatTypeSupergroup supergroup = (TdApi.ChatTypeSupergroup) chat.type;
            chatType = supergroup.isChannel ? "【频道】" : "【超级群组】";
        } else if (chat.type instanceof TdApi.ChatTypeSecret) {
            chatType = "【私密聊天】";
        }
        messageJson.put("聊天类型", chatType);
    }
    
    /**
     * 设置时间信息
     * 
     * @param messageJson 消息JSON对象
     * @param message 消息对象
     * @author liubo
     * @date 2025-01-20
     */
    private void setTimeInfo(ObjectNode messageJson, TdApi.Message message) {
        // 消息时间信息 - 使用配置的时区进行转换
        LocalDateTime sendTimeUtc = TimeZoneUtil.convertUnixToUtc(message.date);
        LocalDateTime sendTime = TimeZoneUtil.convertUtcToChina(sendTimeUtc);
        messageJson.put("消息发送时间", String.format("【%s】", sendTime.format(dateTimeFormatter)));
        
        if (message.editDate > 0) {
            LocalDateTime editTimeUtc = TimeZoneUtil.convertUnixToUtc(message.editDate);
            LocalDateTime editTime = TimeZoneUtil.convertUtcToChina(editTimeUtc);
            messageJson.put("消息编辑时间", String.format("【%s】", editTime.format(dateTimeFormatter)));
        } else {
            messageJson.put("消息编辑时间", "【未编辑】");
        }
    }
    
    /**
     * 设置发送者信息
     * 
     * @param messageJson 消息JSON对象
     * @param message 消息对象
     * @author liubo
     * @date 2025-01-20
     */
    /**
     * 安全地获取用户信息并设置到JSON对象
     * 使用try-catch机制避免"Have no access to the user"错误
     * 优化处理：当获取用户信息异常时，静默处理，不影响消息内容获取
     * 
     * @param messageJson 消息JSON对象
     * @param userId 用户ID
     * @author liubo
     * @date 2025-01-20
     */
    private void setSafeUserInfo(ObjectNode messageJson, long userId) {
        try {
            if (client != null) {
                // 尝试获取用户信息
                client.send(new TdApi.GetUser(userId), result -> {
                    if (result.isError()) {
                        // 如果获取用户信息失败，静默处理，不记录警告日志
                        // 优化：用户信息获取失败时，设置为空或默认值，不影响消息处理
                        messageJson.put("发送者名称", "");
                    } else {
                        TdApi.User user = (TdApi.User) result.get();
                        String userName = buildUserDisplayName(user);
                        messageJson.put("发送者名称", String.format("【%s】", userName));
                    }
                });
            } else {
                messageJson.put("发送者名称", "");
            }
        } catch (Exception e) {
            // 捕获所有异常，确保不会中断消息处理
            // 优化：静默处理异常，不记录日志，用户信息为空
            messageJson.put("发送者名称", "");
        }
    }

    /**
     * 安全地获取用户名称
     * 使用try-catch机制避免"Have no access to the user"错误
     * 优化处理：当获取用户信息异常时，静默处理，返回null
     * 
     * @param userId 用户ID
     * @return 用户名称，如果获取失败返回null
     * @author liubo
     * @date 2025-01-20
     */
    private String getSafeUserName(long userId) {
        try {
            if (client != null) {
                // 同步获取用户信息（注意：这里使用异步方式避免阻塞）
                CompletableFuture<String> future = new CompletableFuture<>();
                
                client.send(new TdApi.GetUser(userId), result -> {
                    if (result.isError()) {
                        // 优化：静默处理用户信息获取失败，不记录警告日志
                        future.complete(null);
                    } else {
                        TdApi.User user = (TdApi.User) result.get();
                        String userName = buildUserDisplayName(user);
                        future.complete(userName);
                    }
                });
                
                // 设置超时时间，避免长时间等待
                return future.get(3, java.util.concurrent.TimeUnit.SECONDS);
            }
        } catch (Exception e) {
            // 捕获所有异常，包括超时异常
            // 优化：静默处理异常，不记录日志
        }
        return null;
    }

    /**
     * 构建用户显示名称
     * 优先使用用户名，其次使用姓名组合
     * 
     * @param user TDLib用户对象
     * @return 用户显示名称
     * @author liubo
     * @date 2025-01-20
     */
    private String buildUserDisplayName(TdApi.User user) {
        // 尝试获取用户名
        if (user.usernames != null && user.usernames.editableUsername != null && !user.usernames.editableUsername.trim().isEmpty()) {
            return "@" + user.usernames.editableUsername;
        }
        
        StringBuilder nameBuilder = new StringBuilder();
        if (user.firstName != null && !user.firstName.trim().isEmpty()) {
            nameBuilder.append(user.firstName);
        }
        if (user.lastName != null && !user.lastName.trim().isEmpty()) {
            if (nameBuilder.length() > 0) {
                nameBuilder.append(" ");
            }
            nameBuilder.append(user.lastName);
        }
        
        return nameBuilder.length() > 0 ? nameBuilder.toString() : "用户" + user.id;
    }

    /**
     * 安全地设置发送者信息到JSON对象
     * 避免"Have no access to the user"错误导致消息处理中断
     * 
     * @param messageJson 消息JSON对象
     * @param message TDLib消息对象
     * @author liubo
     * @date 2025-01-20
     */
    private void setSenderInfo(ObjectNode messageJson, TdApi.Message message) {
        if (message.senderId instanceof TdApi.MessageSenderUser) {
            TdApi.MessageSenderUser userSender = (TdApi.MessageSenderUser) message.senderId;
            messageJson.put("发送者类型", "【用户】");
            messageJson.put("发送者ID", String.format("【%d】", userSender.userId));
            
            // 安全地获取用户详细信息，避免"Have no access to the user"错误
            setSafeUserInfo(messageJson, userSender.userId);
        } else if (message.senderId instanceof TdApi.MessageSenderChat) {
            TdApi.MessageSenderChat chatSender = (TdApi.MessageSenderChat) message.senderId;
            messageJson.put("发送者类型", "【聊天】");
            messageJson.put("发送者ID", String.format("【%d】", chatSender.chatId));
            
            // 对于聊天类型，不需要获取用户信息
            messageJson.put("发送者名称", "【群组/频道】");
        } else {
            messageJson.put("发送者类型", "【未知】");
            messageJson.put("发送者ID", "【未知】");
            messageJson.put("发送者名称", "【未知】");
        }
    }
    
    /**
     * 设置消息类型信息
     * 
     * @param messageJson 消息JSON对象
     * @param message 消息对象
     * @author liubo
     * @date 2025-01-20
     */
    private void setMessageTypeInfo(ObjectNode messageJson, TdApi.Message message) {
        String contentType = getMessageContentType(message);
        messageJson.put("消息类型", contentType);
    }
    
    /**
     * 获取消息内容类型
     * 
     * @param message 消息对象
     * @return 消息内容类型字符串
     * @author liubo
     * @date 2025-01-20
     */
    private String getMessageContentType(TdApi.Message message) {
        if (message.content instanceof TdApi.MessageText) {
            return "【文本消息】";
        } else if (message.content instanceof TdApi.MessagePhoto) {
            return "【图片消息】";
        } else if (message.content instanceof TdApi.MessageVideo) {
            return "【视频消息】";
        } else if (message.content instanceof TdApi.MessageAudio) {
            return "【音频消息】";
        } else if (message.content instanceof TdApi.MessageDocument) {
            return "【文档消息】";
        } else if (message.content instanceof TdApi.MessageSticker) {
            return "【贴纸消息】";
        } else if (message.content instanceof TdApi.MessageAnimation) {
            return "【动画消息】";
        } else if (message.content instanceof TdApi.MessageVoiceNote) {
            return "【语音消息】";
        } else if (message.content instanceof TdApi.MessageVideoNote) {
            return "【视频笔记】";
        } else if (message.content instanceof TdApi.MessageLocation) {
            return "【位置消息】";
        } else if (message.content instanceof TdApi.MessageContact) {
            return "【联系人消息】";
        } else if (message.content instanceof TdApi.MessagePoll) {
            return "【投票消息】";
        }
        return "【未知类型】";
    }
    
    /**
     * 设置回复信息
     * 
     * @param messageJson 消息JSON对象
     * @param message 消息对象
     * @author liubo
     * @date 2025-01-20
     */
    private void setReplyInfo(ObjectNode messageJson, TdApi.Message message) {
        if (message.replyTo != null && message.replyTo instanceof TdApi.MessageReplyToMessage) {
            TdApi.MessageReplyToMessage replyTo = (TdApi.MessageReplyToMessage) message.replyTo;
            messageJson.put("回复消息ID", String.format("【%d】", replyTo.messageId));
            messageJson.put("回复聊天ID", String.format("【%d】", replyTo.chatId));
        } else {
            messageJson.put("回复消息ID", "【无回复】");
            messageJson.put("回复聊天ID", "【无回复】");
        }
    }
    
    /**
     * 设置转发信息
     * 
     * @param messageJson 消息JSON对象
     * @param message 消息对象
     * @author liubo
     * @date 2025-01-20
     */
    private void setForwardInfo(ObjectNode messageJson, TdApi.Message message) {
        if (message.forwardInfo != null) {
            messageJson.put("转发来源", String.format("【%s】", message.forwardInfo.origin.getClass().getSimpleName()));
            messageJson.put("转发时间", String.format("【%s】", 
                java.time.Instant.ofEpochSecond(message.forwardInfo.date).atZone(java.time.ZoneId.systemDefault()).format(dateTimeFormatter)));
        } else {
            messageJson.put("转发来源", "【非转发消息】");
            messageJson.put("转发时间", "【非转发消息】");
        }
    }
    
    /**
     * 设置消息状态
     * 
     * @param messageJson 消息JSON对象
     * @param message 消息对象
     * @author liubo
     * @date 2025-01-20
     */
    private void setMessageStatus(ObjectNode messageJson, TdApi.Message message) {
        messageJson.put("是否置顶", message.isPinned ? "【是】" : "【否】");
        messageJson.put("是否可编辑", message.canBeEdited ? "【是】" : "【否】");
        messageJson.put("是否可删除", message.canBeDeletedOnlyForSelf || message.canBeDeletedForAllUsers ? "【是】" : "【否】");
        messageJson.put("是否可转发", message.canBeForwarded ? "【是】" : "【否】");
        messageJson.put("是否可保存", message.canBeSaved ? "【是】" : "【否】");
    }
    
    /**
     * 设置线程和专辑信息
     * 
     * @param messageJson 消息JSON对象
     * @param message 消息对象
     * @author liubo
     * @date 2025-01-20
     */
    private void setThreadAndAlbumInfo(ObjectNode messageJson, TdApi.Message message) {
        // 消息线程信息
        if (message.messageThreadId > 0) {
            messageJson.put("消息线程ID", String.format("【%d】", message.messageThreadId));
        } else {
            messageJson.put("消息线程ID", "【无线程】");
        }
        
        // 媒体专辑信息
        if (message.mediaAlbumId > 0) {
            messageJson.put("媒体专辑ID", String.format("【%d】", message.mediaAlbumId));
        } else {
            messageJson.put("媒体专辑ID", "【无专辑】");
        }
    }
    
    /**
     * 设置交互信息
     * 
     * @param messageJson 消息JSON对象
     * @param message 消息对象
     * @author liubo
     * @date 2025-01-20
     */
    private void setInteractionInfo(ObjectNode messageJson, TdApi.Message message) {
        // 查看次数
        if (message.interactionInfo != null && message.interactionInfo.viewCount > 0) {
            messageJson.put("查看次数", String.format("【%d】", message.interactionInfo.viewCount));
        } else {
            messageJson.put("查看次数", "【无统计】");
        }
        
        // 转发次数
        if (message.interactionInfo != null && message.interactionInfo.forwardCount > 0) {
            messageJson.put("转发次数", String.format("【%d】", message.interactionInfo.forwardCount));
        } else {
            messageJson.put("转发次数", "【无统计】");
        }
    }

    /**
     * 异步保存消息到MongoDB
     * 将接收到的Telegram消息转换为TelegramMessage实体并存储
     * 
     * @param message Telegram原始消息对象
     * @param chat 聊天信息
     * @param messageText 消息文本内容
     * @param contentType 消息内容类型
     * @param messageJson 完整的消息JSON对象
     */
    /**
     * 保存消息到MongoDB
     * @param message Telegram消息对象
     * @param chat 聊天对象
     * @param messageText 消息文本
     * @param contentType 内容类型
     * @param messageJson 消息JSON对象
     * @author liubo
     * @date 2025-08-25
     */
    private void saveMessageToMongoDB(TdApi.Message message, TdApi.Chat chat, String messageText, String contentType, ObjectNode messageJson) {
        try {
            TelegramMessage telegramMessage = createTelegramMessageEntity();
            setBasicMessageInfo(telegramMessage, message, chat);
            setChatTypeInfo(telegramMessage, chat);
            setSenderInfo(telegramMessage, message);
            setMessageContentInfo(telegramMessage, messageText, contentType);
            setTimeInfo(telegramMessage);
            setReplyInfo(telegramMessage, message);
            setForwardInfo(telegramMessage, message);
            setMessageStatusInfo(telegramMessage, message);
            setThreadAndAlbumInfo(telegramMessage, message);
            setInteractionInfo(telegramMessage, message);
            setRawJsonData(telegramMessage, messageJson);
            saveMessageAsync(telegramMessage, message);
        } catch (Exception e) {
            logger.error("创建TelegramMessage实体失败: chatId={}, messageId={}", message.chatId, message.id, e);
        }
    }

    /**
     * 创建TelegramMessage实体
     * @return TelegramMessage实体
     * @author liubo
     * @date 2025-08-25
     */
    private TelegramMessage createTelegramMessageEntity() {
        return new TelegramMessage();
    }

    /**
     * 设置基础消息信息
     * @param telegramMessage TelegramMessage实体
     * @param message Telegram消息对象
     * @param chat 聊天对象
     * @author liubo
     * @date 2025-08-25
     */
    private void setBasicMessageInfo(TelegramMessage telegramMessage, TdApi.Message message, TdApi.Chat chat) {
        telegramMessage.setAccountPhone(this.runtimePhoneNumber != null ? this.runtimePhoneNumber : this.phoneNumber);
        telegramMessage.setChatId(message.chatId);
        telegramMessage.setMessageId(message.id);
        telegramMessage.setChatTitle(chat.title);
    }

    /**
     * 设置聊天类型信息
     * @param telegramMessage TelegramMessage实体
     * @param chat 聊天对象
     * @author liubo
     * @date 2025-08-25
     */
    private void setChatTypeInfo(TelegramMessage telegramMessage, TdApi.Chat chat) {
        String chatType = "unknown";
        if (chat.type instanceof TdApi.ChatTypePrivate) {
            chatType = "private";
        } else if (chat.type instanceof TdApi.ChatTypeBasicGroup) {
            chatType = "basic_group";
        } else if (chat.type instanceof TdApi.ChatTypeSupergroup) {
            TdApi.ChatTypeSupergroup supergroup = (TdApi.ChatTypeSupergroup) chat.type;
            chatType = supergroup.isChannel ? "channel" : "supergroup";
        } else if (chat.type instanceof TdApi.ChatTypeSecret) {
            chatType = "secret";
        }
        telegramMessage.setChatType(chatType);
    }

    /**
     * 设置发送者信息
     * @param telegramMessage TelegramMessage实体
     * @param message Telegram消息对象
     * @author liubo
     * @date 2025-08-25
     */
    /**
     * 安全地设置发送者信息到TelegramMessage实体
     * 避免"Have no access to the user"错误导致消息处理中断
     * 
     * @param telegramMessage Telegram消息实体
     * @param message TDLib消息对象
     * @author liubo
     * @date 2025-01-20
     */
    private void setSenderInfo(TelegramMessage telegramMessage, TdApi.Message message) {
        if (message.senderId instanceof TdApi.MessageSenderUser) {
            TdApi.MessageSenderUser userSender = (TdApi.MessageSenderUser) message.senderId;
            telegramMessage.setSenderType("user");
            telegramMessage.setSenderId(userSender.userId);
            
            // 安全地获取用户详细信息，避免"Have no access to the user"错误
            String userName = getSafeUserName(userSender.userId);
            if (userName != null) {
                telegramMessage.setSenderName(userName);
            }
        } else if (message.senderId instanceof TdApi.MessageSenderChat) {
            TdApi.MessageSenderChat chatSender = (TdApi.MessageSenderChat) message.senderId;
            telegramMessage.setSenderType("chat");
            telegramMessage.setSenderId(chatSender.chatId);
            telegramMessage.setSenderName("群组/频道");
        } else {
            telegramMessage.setSenderType("unknown");
            telegramMessage.setSenderId(0L);
            telegramMessage.setSenderName("未知");
        }
    }

    /**
     * 设置消息内容信息
     * @param telegramMessage TelegramMessage实体
     * @param messageText 消息文本
     * @param contentType 内容类型
     * @author liubo
     * @date 2025-08-25
     */
    private void setMessageContentInfo(TelegramMessage telegramMessage, String messageText, String contentType) {
        telegramMessage.setMessageText(messageText);
        telegramMessage.setMessageType(contentType.replaceAll("【|】", "")); // 移除格式化字符
    }

    /**
     * 设置时间信息
     * @param telegramMessage TelegramMessage实体
     * @author liubo
     * @date 2025-08-25
     */
    private void setTimeInfo(TelegramMessage telegramMessage) {
        // created_time: 当前真实北京时间（数据写入时间）
        telegramMessage.setCreatedTime(LocalDateTime.now(ZoneId.of("Asia/Shanghai")));
        // message_date: 消息接收时间（北京时间）
        telegramMessage.setMessageDate(LocalDateTime.now(ZoneId.of("Asia/Shanghai")));
    }

    /**
     * 设置回复信息
     * @param telegramMessage TelegramMessage实体
     * @param message Telegram消息对象
     * @author liubo
     * @date 2025-08-25
     */
    private void setReplyInfo(TelegramMessage telegramMessage, TdApi.Message message) {
        if (message.replyTo != null && message.replyTo instanceof TdApi.MessageReplyToMessage) {
            TdApi.MessageReplyToMessage replyTo = (TdApi.MessageReplyToMessage) message.replyTo;
            telegramMessage.setReplyToMessageId(replyTo.messageId);
        }
    }

    /**
     * 设置转发信息
     * @param telegramMessage TelegramMessage实体
     * @param message Telegram消息对象
     * @author liubo
     * @date 2025-08-25
     */
    private void setForwardInfo(TelegramMessage telegramMessage, TdApi.Message message) {
        if (message.forwardInfo != null) {
            telegramMessage.setForwardFromChatId(message.chatId);
            telegramMessage.setForwardFromMessageId(message.id);
        }
    }

    /**
     * 设置消息状态信息
     * @param telegramMessage TelegramMessage实体
     * @param message Telegram消息对象
     * @author liubo
     * @date 2025-08-25
     */
    private void setMessageStatusInfo(TelegramMessage telegramMessage, TdApi.Message message) {
        telegramMessage.setIsPinned(message.isPinned);
        telegramMessage.setCanBeEdited(message.canBeEdited);
        telegramMessage.setCanBeDeleted(message.canBeDeletedOnlyForSelf || message.canBeDeletedForAllUsers);
        telegramMessage.setCanBeForwarded(message.canBeForwarded);
        telegramMessage.setCanBeSaved(message.canBeSaved);
    }

    /**
     * 设置线程和专辑信息
     * @param telegramMessage TelegramMessage实体
     * @param message Telegram消息对象
     * @author liubo
     * @date 2025-08-25
     */
    private void setThreadAndAlbumInfo(TelegramMessage telegramMessage, TdApi.Message message) {
        if (message.messageThreadId > 0) {
            telegramMessage.setMessageThreadId(message.messageThreadId);
        }
        if (message.mediaAlbumId > 0) {
            telegramMessage.setMediaAlbumId(message.mediaAlbumId);
        }
    }

    /**
     * 设置交互信息
     * @param telegramMessage TelegramMessage实体
     * @param message Telegram消息对象
     * @author liubo
     * @date 2025-08-25
     */
    private void setInteractionInfo(TelegramMessage telegramMessage, TdApi.Message message) {
        if (message.interactionInfo != null) {
            telegramMessage.setViewCount(message.interactionInfo.viewCount);
            telegramMessage.setForwardCount(message.interactionInfo.forwardCount);
        }
    }

    /**
     * 设置原始JSON数据
     * @param telegramMessage TelegramMessage实体
     * @param messageJson 消息JSON对象
     * @author liubo
     * @date 2025-08-25
     */
    private void setRawJsonData(TelegramMessage telegramMessage, ObjectNode messageJson) {
        telegramMessage.setRawMessageJson(messageJson.toString());
    }

    /**
     * 异步保存消息
     * @param telegramMessage TelegramMessage实体
     * @param message Telegram消息对象
     * @author liubo
     * @date 2025-08-25
     */
    private void saveMessageAsync(TelegramMessage telegramMessage, TdApi.Message message) {
        messageService.saveMessageAsync(telegramMessage).whenComplete((result, throwable) -> {
            if (throwable != null) {
                logger.error("保存消息到MongoDB失败: chatId={}, messageId={}", message.chatId, message.id, throwable);
            } else if (result) {
                logger.info("消息已保存到MongoDB: chatId={}, messageId={}", message.chatId, message.id);
            } else {
                logger.debug("消息已存在，跳过保存: chatId={}, messageId={}", message.chatId, message.id);
            }
        });
    }

    /**
     * 处理授权状态更新事件
     * 
     * 监听Telegram客户端的授权状态变化，根据不同状态执行相应操作。
     * 这是认证流程的核心处理方法，负责引导用户完成整个登录过程。
     * 
     * 支持的授权状态：
     * - AuthorizationStateReady: 授权完成，开始消息监听
     * - AuthorizationStateWaitPhoneNumber: 等待手机号输入
     * - AuthorizationStateWaitCode: 等待验证码输入
     * - AuthorizationStateWaitPassword: 等待两步验证密码
     * - AuthorizationStateClosed/Closing: 客户端关闭状态
     * 
     * @param update 授权状态更新事件，包含新的授权状态信息
     */
    private void handleAuthorizationState(TdApi.UpdateAuthorizationState update) {
        TdApi.AuthorizationState authState = update.authorizationState;
        this.currentAuthState = authState;
        
        if (authState instanceof TdApi.AuthorizationStateReady) {
            logger.info("✅ Telegram授权成功，session已恢复，开始监听消息");
            // 授权成功后立即获取聊天列表以启用实时消息接收
            initializeMessageReceiving();
            
            // 保存session到MongoDB
            saveSessionToMongoDB();
            
            // 激活session状态
            String currentPhoneNumber = runtimePhoneNumber != null ? runtimePhoneNumber : phoneNumber;
            if (currentPhoneNumber != null) {
                sessionService.activateSession(currentPhoneNumber);
                logger.info("✅ Session已激活: {}", currentPhoneNumber);
            }
        } else if (authState instanceof TdApi.AuthorizationStateWaitPhoneNumber) {
            logger.info("⏳ 等待输入手机号码 - 请调用 /api/telegram/phone 接口提交手机号");
        } else if (authState instanceof TdApi.AuthorizationStateWaitCode) {
            logger.info("⏳ 等待输入验证码 - 请调用 /api/telegram/code 接口提交验证码");
        } else if (authState instanceof TdApi.AuthorizationStateWaitPassword) {
            logger.info("⏳ 等待输入二次验证密码 - 请调用 /api/telegram/password 接口提交密码");
        } else if (authState instanceof TdApi.AuthorizationStateClosed) {
            logger.info("❌ Telegram客户端已关闭");
        } else if (authState instanceof TdApi.AuthorizationStateClosing) {
            logger.info("⏳ Telegram客户端正在关闭");
        } else {
            logger.info("📱 授权状态: {}", authState.getClass().getSimpleName());
        }
    }

    /**
     * 处理新聊天
     * @param update 新聊天更新
     */
    private void handleNewChat(TdApi.UpdateNewChat update) {
        // logger.info("发现新聊天: {} (ID: {})", update.chat.title, update.chat.id);
    }

    /**
     * 处理聊天最后一条消息更新
     * @param update 聊天最后消息更新
     */
    private void handleChatLastMessage(TdApi.UpdateChatLastMessage update) {
        logger.debug("聊天 {} 的最后一条消息已更新", update.chatId);
    }

    /**
     * 处理连接状态更新
     * @param update 连接状态更新
     */
    private void handleConnectionState(TdApi.UpdateConnectionState update) {
        logger.info("连接状态更新: {}", update.state.getClass().getSimpleName());
        if (update.state instanceof TdApi.ConnectionStateReady) {
            logger.info("Telegram连接已就绪，可以接收实时消息");
        }
    }

    /**
     * 初始化消息接收功能
     * 
     * 在客户端授权成功后调用，用于激活实时消息接收功能。
     * 通过获取聊天列表和设置相关选项来确保能够接收到所有新消息。
     * 
     * 执行的操作：
     * 1. 获取聊天列表以激活消息接收
     * 2. 设置在线状态为true
     * 3. 启用消息数据库同步
     * 4. 配置其他必要的接收选项
     * 
     * 注意：此方法必须在授权完成后调用，否则可能无法正常接收消息。
     */
    private void initializeMessageReceiving() {
        try {
            // 获取聊天列表以激活消息接收
            TdApi.GetChats getChats = new TdApi.GetChats(new TdApi.ChatListMain(), 100);
            client.send(getChats, result -> {
                if (result.isError()) {
                    logger.error("获取聊天列表失败: {}", result.getError().message);
                } else {
                    logger.info("聊天列表获取成功，消息监听已激活");
                }
            });
            
            // 设置在线状态
            client.send(new TdApi.SetOption("online", new TdApi.OptionValueBoolean(true)));
            
            // 启用消息数据库同步
            client.send(new TdApi.SetOption("use_message_database", new TdApi.OptionValueBoolean(true)));
            
            logger.info("消息接收初始化完成");
        } catch (Exception e) {
            logger.error("初始化消息接收失败", e);
        }
    }

    /**
     * 处理退出命令
     * @param chat 聊天对象
     * @param sender 发送者
     * @param command 命令
     */
    private void handleQuitCommand(TdApi.Chat chat, TdApi.MessageSender sender, String command) {
        logger.info("收到退出命令，正在关闭客户端");
    }

    /**
     * 动态配置Telegram API信息
     * 
     * 允许在运行时动态设置Telegram API ID和API Hash。
     * 如果客户端已经授权成功，则不会重新初始化；
     * 如果配置未变更，也不会重新初始化客户端。
     * 只有在必要时才会重新创建客户端实例。
     * 
     * 使用场景：
     * - 首次配置API信息
     * - 更换API凭据
     * - 修复配置错误
     * 
     * @param appId Telegram API ID，从https://my.telegram.org获取
     * @param appHash Telegram API Hash，从https://my.telegram.org获取
     * @return true表示配置成功，false表示配置失败
     */
    public boolean configApi(int appId, String appHash) {
        try {
            // 检查是否已经有活跃的授权状态
            if (currentAuthState instanceof TdApi.AuthorizationStateReady) {
                logger.info("客户端已经授权成功，无需重新配置API");
                return true;
            }
            
            // 检查API配置是否已经相同
            if (this.runtimeApiId != null && this.runtimeApiId.equals(appId) && 
                this.runtimeApiHash != null && this.runtimeApiHash.equals(appHash)) {
                logger.info("API配置未变更，无需重新初始化客户端");
                return true;
            }
            
            // 更新运行时配置
            this.runtimeApiId = appId;
            this.runtimeApiHash = appHash;
            
            // 同时更新基础配置
            this.apiId = appId;
            this.apiHash = appHash;
            
            logger.info("API配置更新: appId={}, appHash={}", appId, appHash.substring(0, 8) + "...");
            
            // 保存配置到MongoDB（如果有手机号）
            if (this.runtimePhoneNumber != null && !this.runtimePhoneNumber.isEmpty()) {
                try {
                    // 创建或更新MongoDB中的session配置
                    sessionService.createOrUpdateSession(this.runtimePhoneNumber, appId, appHash);
                    logger.info("API配置已保存到MongoDB: {}", this.runtimePhoneNumber);
                } catch (Exception e) {
                    logger.warn("保存API配置到MongoDB失败: {}", e.getMessage());
                }
            } else {
                logger.info("暂无手机号，API配置将在认证时保存到MongoDB");
            }
            
            // 只有在配置变更时才重新初始化客户端
            initializeClient();
            
            return true;
        } catch (Exception e) {
            logger.error("配置API失败", e);
            return false;
        }
    }
    
    /**
     * 提交手机号码进行认证
     * 
     * 在Telegram认证流程中提交手机号码。这是认证的第一步，
     * 提交后Telegram会向该手机号发送短信验证码。
     * 
     * 前置条件：
     * - 客户端必须已经初始化
     * - 当前授权状态应为等待手机号
     * 
     * 后续步骤：
     * - 等待接收短信验证码
     * - 调用submitAuthCode()提交验证码
     * 
     * @param phoneNumber 手机号码，格式如：+8613800138000
     * @return true表示提交成功，false表示提交失败
     */
    public boolean submitPhoneNumber(String phoneNumber) {
        try {
            this.runtimePhoneNumber = phoneNumber;
            this.phoneNumber = phoneNumber;
            logger.info("保存手机号: {}", phoneNumber);
            
            // 检查客户端是否已初始化
            if (client == null) {
                logger.error("客户端未初始化，请先配置API");
                return false;
            }
            
            // 保存配置到MongoDB
            if (this.apiId != null && this.apiHash != null) {
                try {
                    // 创建或更新MongoDB中的session配置
                    sessionService.createOrUpdateSession(phoneNumber, this.apiId, this.apiHash);
                    logger.info("配置已保存到MongoDB: {}", phoneNumber);
                } catch (Exception e) {
                    logger.warn("保存配置到MongoDB失败: {}", e.getMessage());
                }
            }
            
            // 使用重试机制发送手机号进行认证
            RetryHandler.RetryResult<Void> result = tdlightRetryHandler.executeWithRetry(() -> {
                client.send(new TdApi.SetAuthenticationPhoneNumber(phoneNumber, null));
                return null;
            }, RetryHandler.createTdLightConfig(), "submitPhoneNumber");
            
            if (result.isSuccess()) {
                logger.info("手机号已提交: {}", phoneNumber);
                return true;
            } else {
                logger.error("提交手机号失败，已达到最大重试次数: {}", result.getLastException().getMessage());
                return false;
            }
        } catch (Exception e) {
            logger.error("提交手机号失败", e);
            return false;
        }
    }
    
    /**
     * 自动初始化客户端（使用默认配置，支持session恢复）
     */
    /**
     * 自动初始化客户端
     * 
     * 在应用启动时自动检查MongoDB中的配置和session数据，如果存在有效的配置和session，
     * 则自动初始化客户端并恢复登录状态。这样可以实现应用重启后的自动登录。
     * 
     * 检查逻辑：
     * 1. 检查API配置是否完整（API ID、API Hash、手机号）
     * 2. 检查MongoDB中是否存在session数据
     * 3. 如果都满足，则自动初始化客户端
     * 4. TDLight会自动从临时恢复的session文件恢复登录状态
     * 
     * @author liubo
     * @since 2025.08.05
     */
    /**
     * 自动初始化Telegram客户端
     * 
     * 检查API配置和MongoDB中的session数据，如果存在已认证的session则自动恢复，
     * 否则等待首次认证。包括路径验证、目录创建、客户端配置等步骤。
     * 
     * @author liubo
     * @date 2025-01-21
     */
    private void autoInitializeClient() {
        try {
            // 检查API配置
            if (!validateApiConfiguration()) {
                return;
            }
            
            // 检查并获取MongoDB中的session数据
            SessionInfo sessionInfo = checkMongoSessionData();
            
            // 创建session目录
            createSessionDirectory();
            
            // 如果有MongoDB session数据，尝试恢复
            if (sessionInfo.hasValidSession) {
                sessionInfo = restoreSessionFromMongoDB(sessionInfo);
            }
            
            // 初始化TDLib客户端
            initializeTDLibClient(sessionInfo);
            
        } catch (Exception e) {
            logger.error("自动初始化客户端失败", e);
        }
    }
    
    /**
     * 验证API配置
     * @return 配置是否有效
     * @author liubo
     * @date 2025-01-21
     */
    private boolean validateApiConfiguration() {
        if (apiId == null || apiHash == null || apiHash.isEmpty()) {
            logger.info("未配置API信息，跳过自动初始化。请通过 /api/telegram/config 接口配置API信息。");
            return false;
        }
        return true;
    }
    
    /**
     * 检查MongoDB中的session数据
     * @return session信息
     * @author liubo
     * @date 2025-01-21
     */
    private SessionInfo checkMongoSessionData() {
        SessionInfo sessionInfo = new SessionInfo();
        
        if (phoneNumber != null && !phoneNumber.isEmpty()) {
            sessionInfo = checkSpecificPhoneSession(phoneNumber);
        } else {
            sessionInfo = findAvailableSession();
        }
        
        return sessionInfo;
    }
    
    /**
     * 检查指定手机号的session
     * @param phone 手机号
     * @return session信息
     * @author liubo
     * @date 2025-01-21
     */
    private SessionInfo checkSpecificPhoneSession(String phone) {
        SessionInfo sessionInfo = new SessionInfo();
        
        Optional<TelegramSession> sessionOpt = sessionService.getSessionByPhoneNumber(phone);
        if (sessionOpt.isPresent()) {
            TelegramSession session = sessionOpt.get();
            if ("READY".equals(session.getAuthState())) {
                sessionInfo.hasValidSession = true;
                sessionInfo.phoneNumber = phone;
                sessionInfo.activeSession = session;
                int dbFileCount = (session.getDatabaseFiles() != null) ? session.getDatabaseFiles().size() : 0;
                logger.info("检测到MongoDB中存在已认证的session数据: {}, 数据库文件数量: {}", phone, dbFileCount);
            } else {
                logger.info("MongoDB中找到手机号 {} 的session数据，但状态为: {}", phone, session.getAuthState());
            }
        } else {
            logger.info("MongoDB中未找到手机号 {} 的session数据", phone);
        }
        
        return sessionInfo;
    }
    
    /**
     * 查找可用的已认证session
     * @return session信息
     * @author liubo
     * @date 2025-01-21
     */
    private SessionInfo findAvailableSession() {
        SessionInfo sessionInfo = new SessionInfo();
        
        List<TelegramSession> availableSessions = sessionService.getAvailableSessions();
        for (TelegramSession session : availableSessions) {
            if ("READY".equals(session.getAuthState())) {
                sessionInfo.hasValidSession = true;
                sessionInfo.phoneNumber = session.getPhoneNumber();
                phoneNumber = sessionInfo.phoneNumber; // 更新当前手机号
                sessionInfo.activeSession = session;
                int dbFileCount = (session.getDatabaseFiles() != null) ? session.getDatabaseFiles().size() : 0;
                logger.info("检测到MongoDB中存在已认证的可用session数据: {}, 数据库文件数量: {}", sessionInfo.phoneNumber, dbFileCount);
                break;
            }
        }
        
        if (!sessionInfo.hasValidSession) {
            logger.info("MongoDB中未找到任何已认证的可用session数据");
        }
        
        return sessionInfo;
    }
    
    /**
     * 创建session目录
     * @author liubo
     * @date 2025-01-21
     */
    private void createSessionDirectory() {
        try {
            Path configuredSessionDir = Paths.get(sessionPath);
            if (!Files.exists(configuredSessionDir)) {
                Files.createDirectories(configuredSessionDir);
                logger.info("创建session目录: {}", sessionPath);
            } else {
                logger.info("使用现有session目录: {}", sessionPath);
            }
        } catch (IOException e) {
            logger.error("创建session目录失败: {}", e.getMessage(), e);
            throw new RuntimeException("无法创建session目录", e);
        }
    }
    
    /**
     * 从MongoDB恢复session数据
     * @param sessionInfo session信息
     * @return 更新后的session信息
     * @author liubo
     * @date 2025-01-21
     */
    private SessionInfo restoreSessionFromMongoDB(SessionInfo sessionInfo) {
        if (!sessionInfo.hasValidSession || sessionInfo.phoneNumber == null || sessionInfo.activeSession == null) {
            return sessionInfo;
        }
        
        logger.info("正在从MongoDB恢复session数据到临时目录: {}", sessionPath);
        try {
            boolean restored = sessionService.restoreSessionFiles(sessionInfo.phoneNumber, sessionPath);
            if (restored) {
                logger.info("成功从MongoDB恢复session数据");
                sessionInfo = validateRestoredSession(sessionInfo);
                
                if (sessionInfo.hasValidSession) {
                    // 更新运行时配置
                    runtimeApiId = sessionInfo.activeSession.getApiId();
                    runtimeApiHash = sessionInfo.activeSession.getApiHash();
                    runtimePhoneNumber = sessionInfo.activeSession.getPhoneNumber();
                }
            } else {
                logger.warn("从MongoDB恢复session数据失败，将进行首次认证");
                sessionInfo.hasValidSession = false;
            }
        } catch (Exception e) {
            logger.error("从MongoDB恢复session数据时发生错误: {}", e.getMessage(), e);
            sessionInfo.hasValidSession = false;
        }
        
        return sessionInfo;
    }
    
    /**
     * 验证恢复的session文件
     * @param sessionInfo session信息
     * @return 更新后的session信息
     * @author liubo
     * @date 2025-01-21
     */
    private SessionInfo validateRestoredSession(SessionInfo sessionInfo) {
        File sessionDirFile = new File(sessionPath);
        boolean hasValidSession = false;
        
        if (sessionDirFile.exists() && sessionDirFile.isDirectory()) {
            File[] files = sessionDirFile.listFiles();
            if (files != null) {
                logger.info("恢复后的session目录包含 {} 个文件", files.length);
                
                // 检查是否有TDLib数据库文件
                for (File file : files) {
                    logger.info("恢复的文件: {} (大小: {} bytes)", file.getName(), file.length());
                    if (file.getName().equals("td.binlog") || 
                        file.getName().startsWith("db.sqlite") ||
                        file.getName().endsWith(".db")) {
                        hasValidSession = true;
                        logger.info("检测到有效的TDLib数据库文件: {}", file.getName());
                    }
                }
                
                if (!hasValidSession) {
                    logger.warn("MongoDB中的session数据不完整，缺少TDLib数据库文件，将回退到正常认证流程");
                    sessionInfo.hasValidSession = false;
                }
            } else {
                logger.warn("session目录为空，将回退到正常认证流程");
                sessionInfo.hasValidSession = false;
            }
        } else {
            logger.warn("session目录不存在或不是目录: {}，将回退到正常认证流程", sessionPath);
            sessionInfo.hasValidSession = false;
        }
        
        return sessionInfo;
    }
    
    /**
     * 初始化TDLib客户端
     * @param sessionInfo session信息
     * @author liubo
     * @date 2025-01-21
     */
    private void initializeTDLibClient(SessionInfo sessionInfo) {
        if (sessionInfo.hasValidSession) {
            logger.info("检测到已存在的session数据，正在尝试自动恢复登录状态...");
        } else {
            logger.info("未检测到已认证的session数据，需要首次认证。请通过API接口完成认证流程。");
        }
        
        logger.info("正在自动初始化Telegram客户端...");
        
        // 创建TDLib设置和目录
        TDLibSettings settings = createTDLibSettings();
        createTDLibDirectories(settings);
        
        // 构建客户端
        SimpleTelegramClientBuilder clientBuilder = buildTelegramClient(settings);
        
        // 创建客户端实例
        String usePhoneNumber = determinePhoneNumber(sessionInfo);
        client = clientBuilder.build(AuthenticationSupplier.user(usePhoneNumber));
        
        configureProxy();
        
        if (sessionInfo.hasValidSession) {
            logger.info("Telegram客户端自动初始化完成，正在从MongoDB session数据恢复登录状态...");
        } else {
            logger.info("Telegram客户端自动初始化完成，等待首次认证...");
        }
    }
    
    /**
     * 创建TDLib设置
     * @return TDLib设置
     * @author liubo
     * @date 2025-01-21
     */
    private TDLibSettings createTDLibSettings() {
        APIToken apiToken = new APIToken(apiId, apiHash);
        return TDLibSettings.create(apiToken);
    }
    
    /**
     * 创建TDLib目录
     * @param settings TDLib设置
     * @author liubo
     * @date 2025-01-21
     */
    private void createTDLibDirectories(TDLibSettings settings) {
        Path sessionDir = Paths.get(sessionPath);
        Path databaseDir = sessionDir.resolve("database");
        Path downloadsDir = Paths.get(downloadsPath);
        Path downloadsTempDir = Paths.get(downloadsTempPath);
        
        // 验证路径配置
        validatePaths();
        
        // 使用重试机制确保目录存在
        createDirectoriesWithRetry(sessionDir, databaseDir, downloadsDir, downloadsTempDir);
        
        settings.setDatabaseDirectoryPath(databaseDir);
        settings.setDownloadedFilesDirectoryPath(downloadsDir);
    }
    
    /**
     * 验证路径配置
     * @author liubo
     * @date 2025-01-21
     */
    private void validatePaths() {
        PathValidator.ValidationResult sessionValidation = pathValidator.validatePath(sessionPath, true);
        if (!sessionValidation.isValid()) {
            throw new RuntimeException("会话路径验证失败: " + sessionValidation.getErrorMessage());
        }
        
        PathValidator.ValidationResult downloadsValidation = pathValidator.validatePath(downloadsPath, true);
        if (!downloadsValidation.isValid()) {
            throw new RuntimeException("下载路径验证失败: " + downloadsValidation.getErrorMessage());
        }
        
        PathValidator.ValidationResult tempValidation = pathValidator.validatePath(downloadsTempPath, true);
        if (!tempValidation.isValid()) {
            throw new RuntimeException("临时下载路径验证失败: " + tempValidation.getErrorMessage());
        }
        
        logger.info("路径验证通过: session={}, downloads={}, temp={}", sessionPath, downloadsPath, downloadsTempPath);
    }
    
    /**
     * 使用重试机制创建目录
     * @param sessionDir session目录
     * @param databaseDir 数据库目录
     * @param downloadsDir 下载目录
     * @param downloadsTempDir 临时下载目录
     * @author liubo
     * @date 2025-01-21
     */
    private void createDirectoriesWithRetry(Path sessionDir, Path databaseDir, Path downloadsDir, Path downloadsTempDir) {
        RetryHandler.RetryResult<Void> dirResult = networkRetryHandler.executeWithRetry(() -> {
            try {
                Files.createDirectories(sessionDir);
                Files.createDirectories(databaseDir);
                Files.createDirectories(downloadsDir);
                Files.createDirectories(downloadsTempDir);
                return null;
            } catch (IOException e) {
                throw new RuntimeException("创建目录失败: " + e.getMessage(), e);
            }
        }, RetryHandler.createFastConfig(), "createTDLibDirectories");
        
        if (dirResult.isSuccess()) {
            logger.info("创建TDLib目录: session={}, database={}, downloads={}, temp={}", 
                       sessionDir, databaseDir, downloadsDir, downloadsTempDir);
        } else {
            logger.error("创建TDLib目录失败，已达到最大重试次数: {}", dirResult.getLastException().getMessage());
            throw new RuntimeException("无法创建TDLib必需的目录", dirResult.getLastException());
        }
    }
    
    /**
     * 构建Telegram客户端
     * @param settings TDLib设置
     * @return 客户端构建器
     * @author liubo
     * @date 2025-01-21
     */
    private SimpleTelegramClientBuilder buildTelegramClient(TDLibSettings settings) {
        SimpleTelegramClientBuilder clientBuilder = clientFactory.builder(settings);
        clientBuilder.addUpdateHandler(TdApi.UpdateNewMessage.class, this::handleNewMessage);
        clientBuilder.addUpdateHandler(TdApi.UpdateAuthorizationState.class, this::handleAuthorizationState);
        clientBuilder.addUpdateHandler(TdApi.UpdateNewChat.class, this::handleNewChat);
        clientBuilder.addUpdateHandler(TdApi.UpdateChatLastMessage.class, this::handleChatLastMessage);
        clientBuilder.addUpdateHandler(TdApi.UpdateConnectionState.class, this::handleConnectionState);
        clientBuilder.addCommandHandler("quit", this::handleQuitCommand);
        return clientBuilder;
    }
    
    /**
     * 确定使用的手机号
     * @param sessionInfo session信息
     * @return 手机号
     * @author liubo
     * @date 2025-01-21
     */
    private String determinePhoneNumber(SessionInfo sessionInfo) {
        if (sessionInfo.hasValidSession) {
            // 如果有已认证的session，使用空字符串让TDLight自动恢复
            logger.info("检测到已认证session，使用空字符串让TDLight自动从session文件恢复登录状态: {}", sessionInfo.phoneNumber);
            return "";
        } else {
            // 如果没有session，使用配置的手机号进行首次认证
            String usePhoneNumber = (sessionInfo.phoneNumber != null && !sessionInfo.phoneNumber.isEmpty()) ? sessionInfo.phoneNumber : "";
            logger.info("未检测到已认证session，等待首次认证...");
            return usePhoneNumber;
        }
    }
    
    /**
     * Session信息内部类
     * @author liubo
     * @date 2025-01-21
     */
    private static class SessionInfo {
        boolean hasValidSession = false;
        String phoneNumber = null;
        TelegramSession activeSession = null;
    }
    
    /**
     * 重新初始化客户端（使用运行时配置）
     */
    /**
     * 初始化客户端
     * @author liubo
     * @date 2025-01-21
     */
    private void initializeClient() {
        try {
            // 初始化TDLib工厂
            initializeTDLibFactory();
            
            // 获取API配置
            ApiConfig apiConfig = getApiConfiguration();
            if (!apiConfig.isValid()) {
                logger.warn("API配置不完整，跳过客户端初始化");
                return;
            }
            
            // 恢复会话数据
            restoreSessionData(apiConfig.phoneNumber);
            
            // 创建并配置TDLib设置
            TDLibSettings settings = createAndConfigureTDLibSettings(apiConfig);
            
            // 构建并配置客户端
            buildAndConfigureClient(settings, apiConfig.phoneNumber);
            
            logger.info("Telegram客户端重新初始化完成");
        } catch (Exception e) {
            logger.error("重新初始化客户端失败", e);
        }
    }
    
    /**
     * 初始化TDLib工厂
     * @author liubo
     * @date 2025-01-21
     */
    private void initializeTDLibFactory() throws UnsupportedNativeLibraryException {
        if (clientFactory == null) {
            Init.init();
            Log.setLogMessageHandler(1, new Slf4JLogMessageHandler());
            clientFactory = new SimpleTelegramClientFactory();
        }
    }
    
    /**
     * 获取API配置
     * @return API配置信息
     * @author liubo
     * @date 2025-01-21
     */
    private ApiConfig getApiConfiguration() {
        int useApiId = runtimeApiId != null ? runtimeApiId : apiId;
        String useApiHash = runtimeApiHash != null ? runtimeApiHash : apiHash;
        String usePhoneNumber = runtimePhoneNumber != null ? runtimePhoneNumber : 
                               (phoneNumber != null ? phoneNumber : "");
        
        return new ApiConfig(useApiId, useApiHash, usePhoneNumber);
    }
    
    /**
     * 恢复会话数据
     * @param phoneNumber 手机号
     * @author liubo
     * @date 2025-01-21
     */
    private void restoreSessionData(String phoneNumber) {
        if (phoneNumber != null && !phoneNumber.isEmpty()) {
            Optional<TelegramSession> sessionOpt = sessionService.getSessionByPhoneNumber(phoneNumber);
            if (sessionOpt.isPresent()) {
                TelegramSession session = sessionOpt.get();
                if (session.getDatabaseFiles() != null && !session.getDatabaseFiles().isEmpty()) {
                    logger.info("正在从MongoDB恢复session数据: {}", phoneNumber);
                    boolean restored = sessionService.restoreSessionFiles(phoneNumber, sessionPath);
                    if (restored) {
                        logger.info("成功从MongoDB恢复session数据");
                    } else {
                        logger.warn("从MongoDB恢复session数据失败");
                    }
                }
            }
        }
    }
    
    /**
     * 创建并配置TDLib设置
     * @param apiConfig API配置
     * @return TDLib设置
     * @author liubo
     * @date 2025-01-21
     */
    private TDLibSettings createAndConfigureTDLibSettings(ApiConfig apiConfig) {
        APIToken apiToken = new APIToken(apiConfig.apiId, apiConfig.apiHash);
        TDLibSettings settings = TDLibSettings.create(apiToken);
        
        // 创建并设置目录
        createTDLibDirectoriesForClient(settings);
        
        return settings;
    }
    
    /**
     * 为客户端创建TDLib目录
     * @param settings TDLib设置
     * @author liubo
     * @date 2025-01-21
     */
    private void createTDLibDirectoriesForClient(TDLibSettings settings) {
        Path sessionDir = Paths.get(sessionPath);
        Path databaseDir = sessionDir.resolve("database");
        Path downloadsDir = Paths.get(downloadsPath);
        Path downloadsTempDir = Paths.get(downloadsTempPath);
        
        try {
            Files.createDirectories(sessionDir);
            Files.createDirectories(databaseDir);
            Files.createDirectories(downloadsDir);
            Files.createDirectories(downloadsTempDir);
            logger.info("创建TDLib目录: session={}, database={}, downloads={}, temp={}", 
                       sessionDir, databaseDir, downloadsDir, downloadsTempDir);
        } catch (IOException e) {
            logger.error("创建TDLib目录失败", e);
            throw new RuntimeException("无法创建TDLib必需的目录", e);
        }
        
        settings.setDatabaseDirectoryPath(databaseDir);
        settings.setDownloadedFilesDirectoryPath(downloadsDir);
    }
    
    /**
     * 构建并配置客户端
     * @param settings TDLib设置
     * @param phoneNumber 手机号
     * @author liubo
     * @date 2025-01-21
     */
    private void buildAndConfigureClient(TDLibSettings settings, String phoneNumber) {
        SimpleTelegramClientBuilder clientBuilder = clientFactory.builder(settings);
        
        // 添加更新处理器
        addUpdateHandlers(clientBuilder);
        
        // 构建客户端
        client = clientBuilder.build(AuthenticationSupplier.user(phoneNumber));
        
        // 配置代理
        configureProxy();
    }
    
    /**
     * 添加更新处理器
     * @param clientBuilder 客户端构建器
     * @author liubo
     * @date 2025-01-21
     */
    private void addUpdateHandlers(SimpleTelegramClientBuilder clientBuilder) {
        clientBuilder.addUpdateHandler(TdApi.UpdateNewMessage.class, this::handleNewMessage);
        clientBuilder.addUpdateHandler(TdApi.UpdateAuthorizationState.class, this::handleAuthorizationState);
        clientBuilder.addUpdateHandler(TdApi.UpdateNewChat.class, this::handleNewChat);
        clientBuilder.addUpdateHandler(TdApi.UpdateChatLastMessage.class, this::handleChatLastMessage);
        clientBuilder.addUpdateHandler(TdApi.UpdateConnectionState.class, this::handleConnectionState);
        clientBuilder.addCommandHandler("quit", this::handleQuitCommand);
    }
    
    /**
     * API配置信息
     * @author liubo
     * @date 2025-01-21
     */
    private static class ApiConfig {
        final int apiId;
        final String apiHash;
        final String phoneNumber;
        
        ApiConfig(int apiId, String apiHash, String phoneNumber) {
            this.apiId = apiId;
            this.apiHash = apiHash;
            this.phoneNumber = phoneNumber;
        }
        
        boolean isValid() {
            return apiId != 0 && apiHash != null && !apiHash.isEmpty();
        }
    }
    
    /**
     * 启动监听
     */
    public void startListening() {
        logger.info("Telegram服务已启动，开始监听消息");
    }
    
    /**
     * 提交短信验证码
     * 
     * 提交从Telegram收到的短信验证码以完成认证。
     * 这是认证流程的第二步，验证码通常为5-6位数字。
     * 
     * 可能的结果：
     * 1. 验证成功，直接完成授权
     * 2. 验证成功，但需要输入两步验证密码
     * 3. 验证码错误或其他错误
     * 
     * 返回的Map包含以下字段：
     * - success: 是否成功
     * - message: 结果消息
     * - needPassword: 是否需要输入密码
     * 
     * @param code 短信验证码，通常为5-6位数字
     * @return 包含提交结果的Map对象
     */
    public Map<String, Object> submitAuthCode(String code) {
        Map<String, Object> result = new HashMap<>();
        
        try {
            if (currentAuthState instanceof TdApi.AuthorizationStateWaitCode) {
                return processAuthCodeSubmission(code, result);
            } else {
                return createInvalidStateResult(result);
            }
        } catch (Exception e) {
            logger.error("提交验证码失败", e);
            result.put("success", false);
            result.put("message", "提交验证码失败: " + e.getMessage());
            return result;
        }
    }
    
    /**
     * 处理验证码提交过程
     * 
     * @param code 验证码
     * @param result 结果Map
     * @return 处理结果
     * @throws Exception 处理异常
     */
    private Map<String, Object> processAuthCodeSubmission(String code, Map<String, Object> result) throws Exception {
        // 使用重试机制提交验证码
        RetryHandler.RetryResult<Void> retryResult = executeAuthCodeSubmission(code);
        
        if (!retryResult.isSuccess()) {
            return createSubmissionFailureResult(result, retryResult.getLastException());
        }
        
        logger.info("验证码已提交: {}", code);
        
        // 等待一段时间以获取新的授权状态
        Thread.sleep(2000);
        
        return createAuthCodeSubmissionResult(result);
    }
    
    /**
     * 执行验证码提交
     * 
     * @param code 验证码
     * @return 重试结果
     */
    private RetryHandler.RetryResult<Void> executeAuthCodeSubmission(String code) {
        return tdlightRetryHandler.executeWithRetry(() -> {
            TdApi.CheckAuthenticationCode checkCode = new TdApi.CheckAuthenticationCode(code);
            client.send(checkCode);
            return null;
        }, RetryHandler.createTdLightConfig(), "submitAuthCode");
    }
    
    /**
     * 创建提交失败结果
     * 
     * @param result 结果Map
     * @param exception 异常信息
     * @return 失败结果
     */
    private Map<String, Object> createSubmissionFailureResult(Map<String, Object> result, Exception exception) {
        logger.error("提交验证码失败，已达到最大重试次数: {}", exception.getMessage());
        result.put("success", false);
        result.put("message", "提交验证码失败: " + exception.getMessage());
        return result;
    }
    
    /**
     * 创建验证码提交结果
     * 
     * @param result 结果Map
     * @return 提交结果
     */
    private Map<String, Object> createAuthCodeSubmissionResult(Map<String, Object> result) {
        if (currentAuthState instanceof TdApi.AuthorizationStateWaitPassword) {
            result.put("success", true);
            result.put("message", "验证码正确，需要输入二级密码");
            result.put("needPassword", true);
        } else if (currentAuthState instanceof TdApi.AuthorizationStateReady) {
            result.put("success", true);
            result.put("message", "验证成功，授权完成");
            result.put("needPassword", false);
        } else {
            result.put("success", true);
            result.put("message", "验证码已提交，等待处理");
            result.put("needPassword", false);
        }
        return result;
    }
    
    /**
     * 创建无效状态结果
     * 
     * @param result 结果Map
     * @return 无效状态结果
     */
    private Map<String, Object> createInvalidStateResult(Map<String, Object> result) {
        logger.warn("当前状态不需要验证码，当前状态: {}", 
            currentAuthState != null ? currentAuthState.getClass().getSimpleName() : "null");
        result.put("success", false);
        result.put("message", "当前状态不需要验证码");
        return result;
    }
    
    /**
     * 提交两步验证密码
     * 
     * 如果Telegram账号启用了两步验证（2FA），在验证码验证成功后
     * 还需要提交两步验证密码才能完成最终的授权。
     * 
     * 前置条件：
     * - 短信验证码已验证成功
     * - 当前授权状态为等待密码
     * - 账号必须已启用两步验证
     * 
     * 注意事项：
     * - 密码错误可能导致账号被临时锁定
     * - 建议在UI中提供密码可见性切换
     * 
     * @param password 两步验证密码，用户设置的安全密码
     * @return true表示提交成功，false表示提交失败或当前状态不需要密码
     */
    public boolean submitPassword(String password) {
        try {
            if (currentAuthState instanceof TdApi.AuthorizationStateWaitPassword) {
                // 使用重试机制提交密码
                RetryHandler.RetryResult<Void> result = tdlightRetryHandler.executeWithRetry(() -> {
                    TdApi.CheckAuthenticationPassword checkPassword = new TdApi.CheckAuthenticationPassword(password);
                    client.send(checkPassword);
                    return null;
                }, RetryHandler.createTdLightConfig(), "submitPassword");
                
                if (result.isSuccess()) {
                    logger.info("密码已提交");
                    return true;
                } else {
                    logger.error("提交密码失败，已达到最大重试次数: {}", result.getLastException().getMessage());
                    return false;
                }
            } else {
                logger.warn("当前状态不需要密码，当前状态: {}", 
                    currentAuthState != null ? currentAuthState.getClass().getSimpleName() : "null");
                return false;
            }
        } catch (Exception e) {
            logger.error("提交密码失败", e);
            return false;
        }
    }

    /**
     * 获取消息文本内容
     * @param content 消息内容
     * @return 文本内容
     */
    /**
     * 获取消息文本内容
     * 
     * 根据不同的消息类型提取相应的文本内容，
     * 对于图片消息会提供详细的描述信息。
     * 
     * @param content 消息内容对象
     * @return 消息的文本描述
     * @author liubo
     * @since 2025.01.05
     */
    /**
     * 获取消息文本内容
     * 
     * @param content 消息内容对象
     * @return 消息文本
     * @author liubo
     * @date 2025-01-21
     */
    private String getMessageText(TdApi.MessageContent content) {
        if (content instanceof TdApi.MessageText) {
            return getTextMessageContent((TdApi.MessageText) content);
        } else if (content instanceof TdApi.MessagePhoto) {
            return getPhotoMessageContent((TdApi.MessagePhoto) content);
        } else if (content instanceof TdApi.MessageVideo) {
            return getVideoMessageContent((TdApi.MessageVideo) content);
        } else if (content instanceof TdApi.MessageDocument) {
            return getDocumentMessageContent((TdApi.MessageDocument) content);
        } else if (content instanceof TdApi.MessageSticker) {
            return getStickerMessageContent();
        } else if (content instanceof TdApi.MessageAnimation) {
            return getAnimationMessageContent((TdApi.MessageAnimation) content);
        } else {
            return getUnknownMessageContent(content);
        }
    }
    
    /**
     * 获取文本消息内容
     * 
     * @param messageText 文本消息对象
     * @return 文本内容
     * @author liubo
     * @date 2025-01-21
     */
    private String getTextMessageContent(TdApi.MessageText messageText) {
        return messageText.text.text;
    }
    
    /**
     * 获取图片消息内容
     * 
     * @param photo 图片消息对象
     * @return 图片消息描述
     * @author liubo
     * @date 2025-01-21
     */
    private String getPhotoMessageContent(TdApi.MessagePhoto photo) {
        StringBuilder photoInfo = new StringBuilder("[图片消息]");
        
        addPhotoSizeInfo(photoInfo, photo);
        addPhotoCaptionInfo(photoInfo, photo);
        
        return photoInfo.toString();
    }
    
    /**
     * 添加图片尺寸信息
     * 
     * @param photoInfo 图片信息构建器
     * @param photo 图片消息对象
     * @author liubo
     * @date 2025-01-21
     */
    private void addPhotoSizeInfo(StringBuilder photoInfo, TdApi.MessagePhoto photo) {
        if (photo.photo.sizes.length > 0) {
            TdApi.PhotoSize largestPhoto = photo.photo.sizes[photo.photo.sizes.length - 1];
            photoInfo.append(String.format(" 尺寸:%dx%d", largestPhoto.width, largestPhoto.height));
            photoInfo.append(String.format(" 大小:%d字节", largestPhoto.photo.size));
        }
    }
    
    /**
     * 添加图片描述信息
     * 
     * @param photoInfo 图片信息构建器
     * @param photo 图片消息对象
     * @author liubo
     * @date 2025-01-21
     */
    private void addPhotoCaptionInfo(StringBuilder photoInfo, TdApi.MessagePhoto photo) {
        if (photo.caption != null && !photo.caption.text.isEmpty()) {
            photoInfo.append(" 描述:").append(photo.caption.text);
        }
    }
    
    /**
     * 获取视频消息内容
     * 
     * @param video 视频消息对象
     * @return 视频消息描述
     * @author liubo
     * @date 2025-01-21
     */
    private String getVideoMessageContent(TdApi.MessageVideo video) {
        return "[视频]" + (video.caption != null ? video.caption.text : "");
    }
    
    /**
     * 获取文档消息内容
     * 
     * @param document 文档消息对象
     * @return 文档消息描述
     * @author liubo
     * @date 2025-01-21
     */
    private String getDocumentMessageContent(TdApi.MessageDocument document) {
        return "[文档]" + (document.caption != null ? document.caption.text : "");
    }
    
    /**
     * 获取贴纸消息内容
     * 
     * @return 贴纸消息描述
     * @author liubo
     * @date 2025-01-21
     */
    private String getStickerMessageContent() {
        return "[贴纸]";
    }
    
    /**
     * 获取动图消息内容
     * 
     * @param animation 动图消息对象
     * @return 动图消息描述
     * @author liubo
     * @date 2025-01-21
     */
    private String getAnimationMessageContent(TdApi.MessageAnimation animation) {
        return "[动图]" + (animation.caption != null ? animation.caption.text : "");
    }
    
    /**
     * 获取未知类型消息内容
     * 
     * @param content 消息内容对象
     * @return 未知消息类型描述
     * @author liubo
     * @date 2025-01-21
     */
    private String getUnknownMessageContent(TdApi.MessageContent content) {
        return "[" + content.getClass().getSimpleName() + "]";
    }

    /**
     * 处理图片消息的详细信息
     * 
     * 解析图片消息的详细信息，包括图片尺寸、文件大小等，
     * 并尝试下载图片文件，判断图片是链接地址还是base64格式。
     * 
     * @param messageJson 消息JSON对象，用于添加图片相关信息
     * @param photoMessage 图片消息对象
     * @param message 消息对象
     * @param chat 聊天对象
     * @author liubo
     * @since 2025.01.05
     */
    private void handlePhotoMessage(ObjectNode messageJson, TdApi.MessagePhoto photoMessage, TdApi.Message message, TdApi.Chat chat) {
        try {
            // 添加图片基本信息
            addPhotoCaption(messageJson, photoMessage);
            
            // 获取图片的不同尺寸信息
            TdApi.PhotoSize[] photoSizes = photoMessage.photo.sizes;
            messageJson.put("图片尺寸数量", String.format("【%d】", photoSizes.length));
            
            // 处理最大尺寸的图片
            if (photoSizes.length > 0) {
                TdApi.PhotoSize largestPhoto = photoSizes[photoSizes.length - 1]; // 通常最后一个是最大尺寸
                processLargestPhoto(messageJson, largestPhoto, message, chat);
            } else {
                messageJson.put("图片信息", "【无可用尺寸】");
            }
            
        } catch (Exception e) {
            logger.error("处理图片消息时发生错误", e);
            messageJson.put("图片处理错误", String.format("【%s】", e.getMessage()));
        }
    }
    
    /**
     * 添加图片描述信息
     * 
     * @param messageJson 消息JSON对象
     * @param photoMessage 图片消息对象
     * @author liubo
     * @since 2025.01.05
     */
    private void addPhotoCaption(ObjectNode messageJson, TdApi.MessagePhoto photoMessage) {
        if (photoMessage.caption != null && !photoMessage.caption.text.isEmpty()) {
            messageJson.put("图片描述", String.format("【%s】", photoMessage.caption.text));
        } else {
            messageJson.put("图片描述", "【无描述】");
        }
    }
    
    /**
     * 处理最大尺寸的图片
     * 
     * @param messageJson 消息JSON对象
     * @param largestPhoto 最大尺寸的图片
     * @param message 消息对象
     * @param chat 聊天对象
     * @author liubo
     * @since 2025.01.05
     */
    private void processLargestPhoto(ObjectNode messageJson, TdApi.PhotoSize largestPhoto, TdApi.Message message, TdApi.Chat chat) {
        // 添加图片尺寸和文件信息
        addPhotoSizeInfo(messageJson, largestPhoto);
        
        // 检查图片是否已下载
        if (largestPhoto.photo.local.isDownloadingCompleted) {
            handleDownloadedPhoto(messageJson, largestPhoto, message);
        } else {
            handleUndownloadedPhoto(messageJson, largestPhoto, message, chat);
        }
    }
    
    /**
     * 添加图片尺寸和文件信息
     * 
     * @param messageJson 消息JSON对象
     * @param largestPhoto 最大尺寸的图片
     * @author liubo
     * @since 2025.01.05
     */
    private void addPhotoSizeInfo(ObjectNode messageJson, TdApi.PhotoSize largestPhoto) {
        messageJson.put("图片宽度", String.format("【%d像素】", largestPhoto.width));
        messageJson.put("图片高度", String.format("【%d像素】", largestPhoto.height));
        messageJson.put("图片文件大小", String.format("【%d字节】", largestPhoto.photo.size));
        messageJson.put("图片文件ID", String.format("【%d】", largestPhoto.photo.id));
        messageJson.put("图片唯一ID", String.format("【%s】", largestPhoto.photo.remote.uniqueId));
    }
    
    /**
     * 处理已下载的图片
     * 
     * @param messageJson 消息JSON对象
     * @param largestPhoto 最大尺寸的图片
     * @param message 消息对象
     * @author liubo
     * @since 2025.01.05
     */
    private void handleDownloadedPhoto(ObjectNode messageJson, TdApi.PhotoSize largestPhoto, TdApi.Message message) {
        messageJson.put("图片下载状态", "【已下载】");
        messageJson.put("图片本地路径", String.format("【%s】", largestPhoto.photo.local.path));
        
        // 尝试读取图片文件并判断格式，同时更新MongoDB
        String accountPhone = this.runtimePhoneNumber != null ? this.runtimePhoneNumber : this.phoneNumber;
        processDownloadedPhoto(messageJson, largestPhoto.photo.local.path, accountPhone, message.chatId, message.id);
    }
    
    /**
     * 处理未下载的图片
     * 
     * @param messageJson 消息JSON对象
     * @param largestPhoto 最大尺寸的图片
     * @param message 消息对象
     * @param chat 聊天对象
     * @author liubo
     * @since 2025.01.05
     */
    private void handleUndownloadedPhoto(ObjectNode messageJson, TdApi.PhotoSize largestPhoto, TdApi.Message message, TdApi.Chat chat) {
        messageJson.put("图片下载状态", "【未下载】");
        
        // 异步下载图片
        downloadPhoto(messageJson, largestPhoto.photo, message, chat);
    }
    
    /**
     * 处理已下载的图片文件
     * 
     * 读取本地图片文件，判断是否为base64格式或文件路径，
     * 并提取图片的基本信息，同时更新MongoDB中的消息记录。
     * 
     * @param messageJson 消息JSON对象
     * @param localPath 图片本地路径
     * @author liubo
     * @since 2025.01.05
     */
    private void processDownloadedPhoto(ObjectNode messageJson, String localPath) {
        processDownloadedPhoto(messageJson, localPath, null, null, null);
    }
    
    /**
     * 处理已下载的图片文件（增强版本）
     * 
     * 读取本地图片文件，进行图片处理和存储，并更新MongoDB中的消息记录。
     * 支持Base64编码存储（小文件）和路径存储（大文件）两种模式。
     * 
     * @param messageJson 消息JSON对象
     * @param localPath 图片本地路径
     * @param accountPhone 账号手机号（可为null，用于消息更新）
     * @param chatId 聊天ID（可为null，用于消息更新）
     * @param messageId 消息ID（可为null，用于消息更新）
     * @author liubo
     * @since 2025.01.19
     */
    /**
     * 处理已下载的图片文件
     * @param messageJson 消息JSON对象
     * @param localPath 本地文件路径
     * @param accountPhone 账号手机号
     * @param chatId 聊天ID
     * @param messageId 消息ID
     * @author liubo
     * @date 2025-01-21
     */
    private void processDownloadedPhoto(ObjectNode messageJson, String localPath, 
                                       String accountPhone, Long chatId, Long messageId) {
        try {
            File photoFile = new File(localPath);
            if (photoFile.exists() && photoFile.isFile()) {
                processValidPhotoFile(messageJson, photoFile, localPath, accountPhone, chatId, messageId);
            } else {
                messageJson.put("图片文件状态", "【文件不存在或不可读】");
            }
        } catch (Exception e) {
            logger.error("处理已下载图片时发生错误", e);
            messageJson.put("图片处理错误", String.format("【%s】", e.getMessage()));
        }
    }

    /**
     * 处理有效的图片文件
     * @param messageJson 消息JSON对象
     * @param photoFile 图片文件对象
     * @param localPath 本地文件路径
     * @param accountPhone 账号手机号
     * @param chatId 聊天ID
     * @param messageId 消息ID
     * @author liubo
     * @date 2025-01-21
     */
    private void processValidPhotoFile(ObjectNode messageJson, File photoFile, String localPath,
                                      String accountPhone, Long chatId, Long messageId) {
        // 提取文件基本信息
        extractFileBasicInfo(messageJson, photoFile, localPath);
        
        // 检测文件类型
        String mimeType = imageProcessingUtil.detectMimeType(localPath);
        boolean isImageFile = imageProcessingUtil.isSupportedImageType(mimeType);
        
        // 设置文件类型信息
        setFileTypeInfo(messageJson, photoFile, mimeType, isImageFile);
        
        // 处理图片存储
        if (isImageFile) {
            processImageStorage(messageJson, localPath, photoFile.length(), accountPhone, chatId, messageId);
        } else {
            messageJson.put("图片存储方式", "【非支持的图片格式，跳过处理】");
        }
    }

    /**
     * 提取文件基本信息
     * @param messageJson 消息JSON对象
     * @param photoFile 图片文件对象
     * @param localPath 本地文件路径
     * @author liubo
     * @date 2025-01-21
     */
    private void extractFileBasicInfo(ObjectNode messageJson, File photoFile, String localPath) {
        // 读取文件大小
        long fileSize = photoFile.length();
        messageJson.put("图片实际文件大小", String.format("【%d字节】", fileSize));
        
        // 提取文件名
        String filename = imageProcessingUtil.extractFileName(localPath);
        messageJson.put("图片文件名", String.format("【%s】", filename));
    }

    /**
     * 设置文件类型信息
     * @param messageJson 消息JSON对象
     * @param photoFile 图片文件对象
     * @param mimeType MIME类型
     * @param isImageFile 是否为图片文件
     * @author liubo
     * @date 2025-01-21
     */
    private void setFileTypeInfo(ObjectNode messageJson, File photoFile, String mimeType, boolean isImageFile) {
        messageJson.put("图片MIME类型", String.format("【%s】", mimeType));
        
        // 提取文件扩展名
        String fileName = photoFile.getName().toLowerCase();
        String fileExtension = "";
        int lastDotIndex = fileName.lastIndexOf('.');
        if (lastDotIndex > 0) {
            fileExtension = fileName.substring(lastDotIndex + 1);
        }
        messageJson.put("图片文件扩展名", String.format("【%s】", fileExtension));
        messageJson.put("是否为图片文件", isImageFile ? "【是】" : "【否】");
    }

    /**
     * 处理图片存储
     * @param messageJson 消息JSON对象
     * @param localPath 本地文件路径
     * @param fileSize 文件大小
     * @param accountPhone 账号手机号
     * @param chatId 聊天ID
     * @param messageId 消息ID
     * @author liubo
     * @date 2025-01-21
     */
    private void processImageStorage(ObjectNode messageJson, String localPath, long fileSize,
                                   String accountPhone, Long chatId, Long messageId) {
        try {
            ImageStorageResult storageResult = determineStorageStrategy(messageJson, localPath, fileSize);
            updateImageDataInMongoDB(messageJson, accountPhone, chatId, messageId, 
                                   storageResult.imageData, localPath, storageResult.imageStatus);
        } catch (Exception e) {
            handleImageStorageError(messageJson, localPath, e);
        }
    }
    
    /**
     * 确定存储策略并处理
     * @param messageJson 消息JSON对象
     * @param localPath 本地文件路径
     * @param fileSize 文件大小
     * @return 存储结果
     * @author liubo
     * @date 2025-01-21
     */
    private ImageStorageResult determineStorageStrategy(ObjectNode messageJson, String localPath, long fileSize) {
        ImageStorageResult result = new ImageStorageResult();
        
        if (imageProcessingUtil.shouldStoreAsBase64(fileSize)) {
            result = processBase64StorageStrategy(messageJson, localPath);
        } else {
            result = processPathStorageStrategy(messageJson, localPath);
        }
        
        return result;
    }
    
    /**
     * 处理Base64存储策略
     * @param messageJson 消息JSON对象
     * @param localPath 本地文件路径
     * @return 存储结果
     * @author liubo
     * @date 2025-01-21
     */
    private ImageStorageResult processBase64StorageStrategy(ObjectNode messageJson, String localPath) {
        ImageStorageResult result = new ImageStorageResult();
        result.imageData = processBase64Storage(messageJson, localPath);
        
        if (result.imageData == null) {
            // Base64编码失败，降级为路径存储
            result.imageStatus = "base64_failed";
            result.imagePath = localPath;
            messageJson.put("图片存储方式", "【Base64编码失败，降级为路径存储】");
        } else {
            result.imageStatus = "processed";
        }
        
        return result;
    }
    
    /**
     * 处理路径存储策略
     * @param messageJson 消息JSON对象
     * @param localPath 本地文件路径
     * @return 存储结果
     * @author liubo
     * @date 2025-01-21
     */
    private ImageStorageResult processPathStorageStrategy(ObjectNode messageJson, String localPath) {
        ImageStorageResult result = new ImageStorageResult();
        result.imagePath = localPath;
        result.imageStatus = "processed";
        messageJson.put("图片存储方式", "【文件路径存储】");
        return result;
    }
    
    /**
     * 处理图片存储错误
     * @param messageJson 消息JSON对象
     * @param localPath 本地文件路径
     * @param e 异常对象
     * @author liubo
     * @date 2025-01-21
     */
    private void handleImageStorageError(ObjectNode messageJson, String localPath, Exception e) {
        logger.error("处理图片存储失败: {}", localPath, e);
        messageJson.put("图片存储方式", "【处理失败】");
        messageJson.put("错误信息", String.format("【%s】", e.getMessage()));
    }
    
    /**
     * 图片存储结果内部类
     * @author liubo
     * @date 2025-01-21
     */
    private static class ImageStorageResult {
        String imageData = null;
        String imagePath = null;
        String imageStatus = "processed";
    }

    /**
     * 处理Base64存储
     * @param messageJson 消息JSON对象
     * @param localPath 本地文件路径
     * @return Base64编码的图片数据，失败时返回null
     * @author liubo
     * @date 2025-01-21
     */
    private String processBase64Storage(ObjectNode messageJson, String localPath) {
        String imageData = imageProcessingUtil.convertImageToBase64(localPath);
        if (imageData != null) {
            messageJson.put("图片存储方式", String.format("【Base64编码，长度：%d字符】", imageData.length()));
            // 只显示前100个字符的base64内容，避免日志过长
            String base64Preview = imageData.length() > 100 ? 
                imageData.substring(0, 100) + "..." : imageData;
            messageJson.put("Base64预览", String.format("【%s】", base64Preview));
        }
        return imageData;
    }

    /**
     * 更新MongoDB中的图片数据
     * @param messageJson 消息JSON对象
     * @param accountPhone 账号手机号
     * @param chatId 聊天ID
     * @param messageId 消息ID
     * @param imageData 图片数据
     * @param localPath 本地文件路径
     * @param imageStatus 图片状态
     * @author liubo
     * @date 2025-01-21
     */
    private void updateImageDataInMongoDB(ObjectNode messageJson, String accountPhone, Long chatId, Long messageId,
                                        String imageData, String localPath, String imageStatus) {
        if (accountPhone != null && chatId != null && messageId != null) {
            String filename = imageProcessingUtil.extractFileName(localPath);
            String mimeType = imageProcessingUtil.detectMimeType(localPath);
            
            messageService.updateImageDataAsync(
                accountPhone, chatId, messageId,
                imageData, filename, mimeType, imageStatus
            ).exceptionally(throwable -> {
                logger.error("更新图片数据到MongoDB失败: accountPhone={}, chatId={}, messageId={}", 
                    accountPhone, chatId, messageId, throwable);
                return null;
            });
            messageJson.put("MongoDB更新", "【已提交异步更新】");
        } else {
             messageJson.put("MongoDB更新", "【跳过更新，缺少必要参数】");
         }
     }
    
    /**
     * 异步下载图片文件（带重试机制）
     * 
     * 使用TDLib的downloadFile API异步下载图片文件，
     * 下载完成后更新消息信息。包含重试机制以处理网络异常。
     * 
     * @param messageJson 消息JSON对象
     * @param photo 图片文件对象
     * @param message 消息对象
     * @param chat 聊天对象
     * @author liubo
     * @since 2025.01.05
     */
    private void downloadPhoto(ObjectNode messageJson, TdApi.File photo, TdApi.Message message, TdApi.Chat chat) {
        downloadPhotoWithRetry(messageJson, photo, message, chat, 0);
    }
    
    /**
     * 带重试机制的图片下载方法
     * 
     * @param messageJson 消息JSON对象
     * @param photo 图片文件对象
     * @param message 消息对象
     * @param chat 聊天对象
     * @param retryCount 当前重试次数（保留参数兼容性，实际使用RetryHandler）
     * @author liubo
     * @since 2025.08.19
     */
    /**
     * 带重试机制的图片下载
     * 
     * @param messageJson 消息JSON对象
     * @param photo 图片文件对象
     * @param message 消息对象
     * @param chat 聊天对象
     * @param retryCount 重试次数
     * @author liubo
     * @date 2025-01-21
     */
    private void downloadPhotoWithRetry(ObjectNode messageJson, TdApi.File photo, TdApi.Message message, TdApi.Chat chat, int retryCount) {
        RetryHandler.RetryResult<Void> result = executePhotoDownloadWithRetry(messageJson, photo, message, chat);
        
        if (!result.isSuccess()) {
            handleDownloadFailure(messageJson, message, result.getLastException());
        }
    }
    
    /**
     * 执行带重试机制的图片下载
     * 
     * @param messageJson 消息JSON对象
     * @param photo 图片文件对象
     * @param message 消息对象
     * @param chat 聊天对象
     * @return 重试结果
     * @author liubo
     * @date 2025-01-21
     */
    private RetryHandler.RetryResult<Void> executePhotoDownloadWithRetry(ObjectNode messageJson, TdApi.File photo, TdApi.Message message, TdApi.Chat chat) {
        return tdlightRetryHandler.executeWithRetry(() -> {
            try {
                downloadPhotoInternal(messageJson, photo, message, chat);
                return null;
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        }, RetryHandler.createTdLightConfig(), "downloadPhoto");
    }
    
    /**
     * 处理图片下载失败
     * 
     * @param messageJson 消息JSON对象
     * @param message 消息对象
     * @param exception 异常信息
     * @author liubo
     * @date 2025-01-21
     */
    private void handleDownloadFailure(ObjectNode messageJson, TdApi.Message message, Exception exception) {
        logger.error("图片下载失败，已达到最大重试次数: {}", exception.getMessage());
        
        updateMessageJsonForFailure(messageJson, exception);
        updateMongoDBForFailure(message);
    }
    
    /**
     * 更新消息JSON为失败状态
     * 
     * @param messageJson 消息JSON对象
     * @param exception 异常信息
     * @author liubo
     * @date 2025-01-21
     */
    private void updateMessageJsonForFailure(ObjectNode messageJson, Exception exception) {
        messageJson.put("downloadStatus", "failed");
        messageJson.put("downloadError", exception.getMessage());
    }
    
    /**
     * 更新MongoDB中的失败状态
     * 
     * @param message 消息对象
     * @author liubo
     * @date 2025-01-21
     */
    private void updateMongoDBForFailure(TdApi.Message message) {
        String accountPhone = this.runtimePhoneNumber != null ? this.runtimePhoneNumber : this.phoneNumber;
        messageService.updateImageDataAsync(accountPhone, message.chatId, message.id, 
                                          null, null, null, "failed")
            .exceptionally(updateThrowable -> {
                logger.error("更新图片失败状态到MongoDB失败: accountPhone={}, chatId={}, messageId={}", 
                           accountPhone, message.chatId, message.id, updateThrowable);
                return false;
            });
    }
    
    /**
     * 内部图片下载实现
     * 
     * @param messageJson 消息JSON对象
     * @param photo 图片文件对象
     * @param message 消息对象
     * @param chat 聊天对象
     * @throws Exception 下载异常
     * @author liubo
     * @date 2025-01-21
     */
    private void downloadPhotoInternal(ObjectNode messageJson, TdApi.File photo, TdApi.Message message, TdApi.Chat chat) throws Exception {
        messageJson.put("图片下载状态", "【开始下载】");
        
        // 检查文件下载状态
        if (checkPhotoAlreadyDownloaded(messageJson, photo, message)) {
            return;
        }
        
        // 验证文件可下载性
        validatePhotoDownloadable(messageJson, photo);
        
        // 执行文件下载
        TdApi.File downloadedFile = executePhotoDownload(photo);
        
        // 处理下载结果
        handleDownloadResult(messageJson, photo, downloadedFile, message);
    }
    
    /**
     * 检查图片是否已下载完成
     * @param messageJson 消息JSON对象
     * @param photo 图片文件对象
     * @param message 消息对象
     * @return 是否已下载完成
     * @author liubo
     * @date 2025-01-21
     */
    private boolean checkPhotoAlreadyDownloaded(ObjectNode messageJson, TdApi.File photo, TdApi.Message message) {
        if (photo.local.isDownloadingCompleted) {
            logger.info("图片已下载完成，直接处理: {}", photo.local.path);
            String accountPhone = this.runtimePhoneNumber != null ? this.runtimePhoneNumber : this.phoneNumber;
            processDownloadedPhoto(messageJson, photo.local.path, accountPhone, message.chatId, message.id);
            return true;
        }
        return false;
    }
    
    /**
     * 验证图片文件可下载性
     * @param messageJson 消息JSON对象
     * @param photo 图片文件对象
     * @throws RuntimeException 文件无法下载时抛出异常
     * @author liubo
     * @date 2025-01-21
     */
    private void validatePhotoDownloadable(ObjectNode messageJson, TdApi.File photo) throws RuntimeException {
        if (!photo.local.canBeDownloaded) {
            logger.warn("图片文件无法下载: 文件ID【{}】", photo.id);
            messageJson.put("图片下载状态", "【无法下载】");
            throw new RuntimeException("图片文件无法下载: 文件ID " + photo.id);
        }
    }
    
    /**
     * 执行图片文件下载
     * @param photo 图片文件对象
     * @return 下载完成的文件对象
     * @throws Exception 下载异常
     * @author liubo
     * @date 2025-01-21
     */
    private TdApi.File executePhotoDownload(TdApi.File photo) throws Exception {
        // 创建下载请求
        TdApi.DownloadFile downloadRequest = new TdApi.DownloadFile(
            photo.id,     // 文件ID
            16,           // 优先级（降低优先级以减少服务器压力）
            0,            // 起始偏移
            0,            // 下载大小限制（0表示下载整个文件）
            false         // 异步下载（改为false以减少服务器负载）
        );
        
        logger.info("开始下载图片: 文件ID【{}】, 大小【{}】字节", photo.id, photo.size);
        
        // 同步下载文件（用于重试机制）
        CompletableFuture<TdApi.File> downloadFuture = client.send(downloadRequest);
        return downloadFuture.get(); // 同步等待下载完成
    }
    
    /**
     * 处理下载结果
     * @param messageJson 消息JSON对象
     * @param originalPhoto 原始图片文件对象
     * @param downloadedFile 下载完成的文件对象
     * @param message 消息对象
     * @throws Exception 处理异常
     * @author liubo
     * @date 2025-01-21
     */
    private void handleDownloadResult(ObjectNode messageJson, TdApi.File originalPhoto, 
                                     TdApi.File downloadedFile, TdApi.Message message) throws Exception {
        if (downloadedFile.local.isDownloadingCompleted) {
            logger.info("图片下载完成: {}", downloadedFile.local.path);
            
            // 创建下载结果信息
            ObjectNode downloadResultJson = createDownloadResultJson(originalPhoto, downloadedFile);
            
            // 处理下载完成的图片
            String accountPhone = this.runtimePhoneNumber != null ? this.runtimePhoneNumber : this.phoneNumber;
            processDownloadedPhoto(downloadResultJson, downloadedFile.local.path, accountPhone, message.chatId, message.id);
            
            // 输出下载结果
            outputDownloadResult(downloadResultJson);
        } else {
            handleIncompleteDownload(originalPhoto, downloadedFile);
        }
    }
    
    /**
     * 创建下载结果JSON对象
     * @param originalPhoto 原始图片文件对象
     * @param downloadedFile 下载完成的文件对象
     * @return 下载结果JSON对象
     * @author liubo
     * @date 2025-01-21
     */
    private ObjectNode createDownloadResultJson(TdApi.File originalPhoto, TdApi.File downloadedFile) {
        ObjectNode downloadResultJson = objectMapper.createObjectNode();
        downloadResultJson.put("下载完成时间", String.format("【%s】", LocalDateTime.now().format(dateTimeFormatter)));
        downloadResultJson.put("图片文件ID", String.format("【%d】", originalPhoto.id));
        downloadResultJson.put("图片下载路径", String.format("【%s】", downloadedFile.local.path));
        downloadResultJson.put("图片文件大小", String.format("【%d字节】", downloadedFile.size));
        return downloadResultJson;
    }
    
    /**
     * 输出下载结果
     * @param downloadResultJson 下载结果JSON对象
     * @throws Exception JSON处理异常
     * @author liubo
     * @date 2025-01-21
     */
    private void outputDownloadResult(ObjectNode downloadResultJson) throws Exception {
        String downloadResultOutput = objectMapper.writeValueAsString(downloadResultJson);
        logger.info("图片下载结果: {}", downloadResultOutput);
        System.out.println("📸 图片下载完成: " + downloadResultOutput);
    }
    
    /**
     * 处理下载未完成的情况
     * @param originalPhoto 原始图片文件对象
     * @param downloadedFile 下载的文件对象
     * @throws RuntimeException 下载未完成异常
     * @author liubo
     * @date 2025-01-21
     */
    private void handleIncompleteDownload(TdApi.File originalPhoto, TdApi.File downloadedFile) throws RuntimeException {
        logger.warn("图片下载未完成: 文件ID【{}】, 下载进度【{}/{}】", originalPhoto.id, downloadedFile.local.downloadedSize, downloadedFile.size);
        throw new RuntimeException(String.format("图片下载未完成: 文件ID %d, 下载进度 %d/%d", 
                                                originalPhoto.id, downloadedFile.local.downloadedSize, downloadedFile.size));
    }
    


    /**
     * 获取服务状态
     * @return 服务状态
     */
    public String getStatus() {
        if (client == null) {
            return "客户端未初始化";
        }
        
        if (currentAuthState instanceof TdApi.AuthorizationStateReady) {
            return "已授权，正在监听消息";
        } else if (currentAuthState instanceof TdApi.AuthorizationStateWaitPhoneNumber) {
            return "等待输入手机号";
        } else if (currentAuthState instanceof TdApi.AuthorizationStateWaitCode) {
            return "等待输入验证码";
        } else if (currentAuthState instanceof TdApi.AuthorizationStateWaitPassword) {
            return "等待输入密码";
        } else {
            return "未知状态: " + (currentAuthState != null ? currentAuthState.getClass().getSimpleName() : "null");
        }
    }
    
    /**
     * 获取详细的授权状态信息
     * 
     * 返回当前Telegram客户端的详细授权状态，包括：
     * - 当前状态类型和描述
     * - 下一步操作指引
     * - 各种状态标志位
     * - 时间戳信息
     * 
     * 返回的Map包含以下字段：
     * - success: 操作是否成功
     * - status: 状态代码（READY、WAIT_PHONE、WAIT_CODE等）
     * - message: 状态描述信息
     * - needsConfig/needsPhone/needsCode/needsPassword: 各种需求标志
     * - isReady: 是否已就绪
     * - nextStep: 下一步操作建议
     * - timestamp: 状态获取时间戳
     * 
     * @return 包含详细授权状态信息的Map对象
     */
    /**
     * 获取详细的授权状态信息
     * 
     * 返回包含当前授权状态、需求标志、下一步操作指引等详细信息的Map对象
     * 
     * @return 包含授权状态详细信息的Map对象
     * @author liubo
     * @since 2025-08-05
     */
    public Map<String, Object> getAuthStatus() {
        Map<String, Object> status = new HashMap<>();
        
        if (client == null) {
            return createNotInitializedStatus();
        }
        
        status.put("success", true);
        populateAuthStateInfo(status);
        status.put("timestamp", System.currentTimeMillis());
        
        return status;
    }

    /**
     * 创建客户端未初始化状态信息
     * 
     * @return 未初始化状态的Map对象
     * @author liubo
     * @since 2025-08-25
     */
    private Map<String, Object> createNotInitializedStatus() {
        Map<String, Object> status = new HashMap<>();
        status.put("success", false);
        status.put("status", "NOT_INITIALIZED");
        status.put("message", "客户端未初始化");
        status.put("needsConfig", true);
        status.put("needsPhone", false);
        status.put("needsCode", false);
        status.put("needsPassword", false);
        status.put("isReady", false);
        return status;
    }

    /**
     * 根据当前授权状态填充状态信息
     * 
     * @param status 状态信息Map对象
     * @author liubo
     * @since 2025-08-25
     */
    private void populateAuthStateInfo(Map<String, Object> status) {
        if (currentAuthState instanceof TdApi.AuthorizationStateReady) {
            populateReadyState(status);
        } else if (currentAuthState instanceof TdApi.AuthorizationStateWaitPhoneNumber) {
            populateWaitPhoneState(status);
        } else if (currentAuthState instanceof TdApi.AuthorizationStateWaitCode) {
            populateWaitCodeState(status);
        } else if (currentAuthState instanceof TdApi.AuthorizationStateWaitPassword) {
            populateWaitPasswordState(status);
        } else if (currentAuthState instanceof TdApi.AuthorizationStateClosed) {
            populateClosedState(status);
        } else {
            populateUnknownState(status);
        }
    }

    /**
     * 填充已授权就绪状态信息
     * 
     * @param status 状态信息Map对象
     * @author liubo
     * @since 2025-08-25
     */
    private void populateReadyState(Map<String, Object> status) {
        status.put("status", "READY");
        status.put("message", "✅ 已授权成功，正在监听消息");
        status.put("needsConfig", false);
        status.put("needsPhone", false);
        status.put("needsCode", false);
        status.put("needsPassword", false);
        status.put("isReady", true);
    }

    /**
     * 填充等待手机号状态信息
     * 
     * @param status 状态信息Map对象
     * @author liubo
     * @since 2025-08-25
     */
    private void populateWaitPhoneState(Map<String, Object> status) {
        status.put("status", "WAIT_PHONE");
        status.put("message", "⏳ 等待输入手机号码");
        status.put("needsConfig", false);
        status.put("needsPhone", true);
        status.put("needsCode", false);
        status.put("needsPassword", false);
        status.put("isReady", false);
        status.put("nextStep", "请调用 POST /api/telegram/auth/phone 接口提交手机号");
    }

    /**
     * 填充等待验证码状态信息
     * 
     * @param status 状态信息Map对象
     * @author liubo
     * @since 2025-08-25
     */
    private void populateWaitCodeState(Map<String, Object> status) {
        status.put("status", "WAIT_CODE");
        status.put("message", "⏳ 等待输入验证码");
        status.put("needsConfig", false);
        status.put("needsPhone", false);
        status.put("needsCode", true);
        status.put("needsPassword", false);
        status.put("isReady", false);
        status.put("nextStep", "请调用 POST /api/telegram/auth/code 接口提交验证码");
    }

    /**
     * 填充等待密码状态信息
     * 
     * @param status 状态信息Map对象
     * @author liubo
     * @since 2025-08-25
     */
    private void populateWaitPasswordState(Map<String, Object> status) {
        status.put("status", "WAIT_PASSWORD");
        status.put("message", "⏳ 等待输入二次验证密码");
        status.put("needsConfig", false);
        status.put("needsPhone", false);
        status.put("needsCode", false);
        status.put("needsPassword", true);
        status.put("isReady", false);
        status.put("nextStep", "请调用 POST /api/telegram/auth/password 接口提交密码");
    }

    /**
     * 填充客户端已关闭状态信息
     * 
     * @param status 状态信息Map对象
     * @author liubo
     * @since 2025-08-25
     */
    private void populateClosedState(Map<String, Object> status) {
        status.put("status", "CLOSED");
        status.put("message", "❌ 客户端已关闭");
        status.put("needsConfig", true);
        status.put("needsPhone", false);
        status.put("needsCode", false);
        status.put("needsPassword", false);
        status.put("isReady", false);
    }

    /**
     * 填充未知状态信息
     * 
     * @param status 状态信息Map对象
     * @author liubo
     * @since 2025-08-25
     */
    private void populateUnknownState(Map<String, Object> status) {
        String stateName = currentAuthState != null ? currentAuthState.getClass().getSimpleName() : "null";
        status.put("status", "UNKNOWN");
        status.put("message", "📱 未知授权状态: " + stateName);
        status.put("needsConfig", false);
        status.put("needsPhone", false);
        status.put("needsCode", false);
        status.put("needsPassword", false);
        status.put("isReady", false);
    }

    /**
     * 初始化账号
     * 
     * 创建并初始化单个Telegram账号实例，准备进行API配置和认证流程。
     * 这是使用系统的第一步操作。
     * 
     * @author liubo
     * @since 2025-08-25
     */
    public void initializeAccount() {
        try {
            logger.info("正在初始化Telegram账号...");
            
            // 重置运行时配置
            this.runtimeApiId = null;
            this.runtimeApiHash = null;
            this.runtimePhoneNumber = null;
            this.currentAuthState = null;
            
            // 如果客户端已存在，先关闭
            if (client != null) {
                client.close();
                client = null;
            }
            
            logger.info("Telegram账号初始化完成，请配置API信息");
            
        } catch (Exception e) {
            logger.error("初始化Telegram账号时发生错误", e);
            throw new RuntimeException("初始化账号失败: " + e.getMessage(), e);
        }
    }
    
    /**
     * 停止消息监听
     * 
     * 停止Telegram消息监听功能，但保持客户端连接。
     * 
     * @author liubo
     * @since 2025-08-25
     */
    public void stopListening() {
        try {
            logger.info("正在停止消息监听...");
            
            if (client != null) {
                // 这里可以添加停止特定监听器的逻辑
                // 目前TDLight客户端没有直接的停止监听方法
                // 但可以通过标志位控制消息处理
                logger.info("消息监听已停止");
            } else {
                logger.warn("客户端未初始化，无法停止监听");
                throw new RuntimeException("客户端未初始化");
            }
            
        } catch (Exception e) {
            logger.error("停止消息监听时发生错误", e);
            throw new RuntimeException("停止监听失败: " + e.getMessage(), e);
        }
    }
    
    /**
     * 清理Session数据
     * 
     * 清除当前账号的所有Session数据，包括认证信息和缓存数据。
     * 清理后需要重新进行认证流程。
     * 
     * @author liubo
     * @since 2025-08-25
     */
    public void clearSession() {
        try {
            logger.info("正在清理Session数据...");
            
            String currentPhoneNumber = getCurrentPhoneNumber();
            
            closeCurrentClient();
            cleanupMongoDBSessionData(currentPhoneNumber);
            resetAuthenticationState();
            deleteLocalSessionFiles();
            
            logger.info("Session数据清理完成");
            
        } catch (Exception e) {
            logger.error("清理Session数据时发生错误", e);
            throw new RuntimeException("清理Session失败: " + e.getMessage(), e);
        }
    }

    /**
     * 获取当前电话号码
     * @return 当前电话号码
     */
    private String getCurrentPhoneNumber() {
        return runtimePhoneNumber != null ? runtimePhoneNumber : phoneNumber;
    }

    /**
     * 关闭当前客户端
     */
    private void closeCurrentClient() {
        if (client != null) {
            try {
                client.close();
            } catch (Exception e) {
                logger.error("关闭Telegram客户端时发生错误: {}", e.getMessage(), e);
                // 不重新抛出异常，确保清理过程能够继续
            }
            client = null;
        }
    }

    /**
     * 清理MongoDB中的session数据
     * @param phoneNumber 电话号码
     */
    private void cleanupMongoDBSessionData(String phoneNumber) {
        if (phoneNumber != null) {
            try {
                sessionService.deactivateSession(phoneNumber);
                sessionService.deleteSession(phoneNumber);
                logger.info("MongoDB中的session数据已清理: {}", phoneNumber);
            } catch (Exception e) {
                logger.warn("清理MongoDB session数据时发生错误: {}", e.getMessage());
            }
        }
    }

    /**
     * 重置认证状态
     */
    private void resetAuthenticationState() {
        this.currentAuthState = null;
        this.runtimeApiId = null;
        this.runtimeApiHash = null;
        this.runtimePhoneNumber = null;
        this.apiId = null;
        this.apiHash = null;
        this.phoneNumber = null;
        
        // 注意：不再删除本地配置文件，因为所有配置都存储在MongoDB中
        logger.debug("跳过删除本地配置文件，所有配置都存储在MongoDB中");
    }

    /**
     * 删除本地Session文件
     */
    private void deleteLocalSessionFiles() {
        try {
            Path sessionDir = Paths.get(sessionPath);
            if (sessionDir.toFile().exists()) {
                // 删除Session目录下的所有文件
                java.nio.file.Files.walk(sessionDir)
                    .sorted(java.util.Comparator.reverseOrder())
                    .map(java.nio.file.Path::toFile)
                    .forEach(java.io.File::delete);
                logger.info("Session文件已删除: {}", sessionPath);
            }
        } catch (Exception e) {
            logger.warn("删除Session文件时发生错误: {}", e.getMessage());
        }
    }

    /**
     * 关闭服务
     */
    @PreDestroy
    public void shutdown() {
        try {
            logger.info("正在关闭Telegram服务...");
            
            // 在关闭前保存session数据到MongoDB
            if (client != null && currentAuthState instanceof TdApi.AuthorizationStateReady) {
                logger.info("正在保存session数据到MongoDB...");
                saveSessionToMongoDB();
            }
            
            if (client != null) {
                client.close();
                client = null;
            }
            
            if (clientFactory != null) {
                clientFactory.close();
                clientFactory = null;
            }
            
            // 清理临时session文件
            cleanupTempSessionFiles();
            
            logger.info("Telegram服务已关闭");
            
        } catch (Exception e) {
            logger.error("关闭Telegram服务时发生错误", e);
        }
    }
    
    /**
     * 清理临时session文件
     */
    /**
     * 清理临时session文件
     * 
     * 只清理真正的临时目录，不删除正在使用的session目录
     * 临时目录的特征：路径包含"telegram-session-"且不是当前正在使用的sessionPath
     * 
     * @author liubo
     * @date 2025-01-20
     */
    private void cleanupTempSessionFiles() {
        try {
            // 获取临时目录的父目录
            Path sessionDir = Paths.get(sessionPath);
            Path parentDir = sessionDir.getParent();
            
            if (parentDir != null && Files.exists(parentDir)) {
                // 遍历父目录，查找临时session目录
                Files.list(parentDir)
                    .filter(Files::isDirectory)
                    .filter(path -> path.getFileName().toString().startsWith("telegram-session-"))
                    .filter(path -> !path.equals(sessionDir)) // 不删除当前正在使用的session目录
                    .forEach(tempDir -> {
                        try {
                            // 递归删除临时目录及其所有内容
                            Files.walk(tempDir)
                                .sorted((a, b) -> b.compareTo(a)) // 先删除文件，再删除目录
                                .forEach(path -> {
                                    try {
                                        Files.deleteIfExists(path);
                                    } catch (IOException e) {
                                        logger.warn("删除临时文件失败: {}", path, e);
                                    }
                                });
                            logger.info("已清理临时session目录: {}", tempDir);
                        } catch (Exception e) {
                            logger.warn("清理临时session目录失败: {}", tempDir, e);
                        }
                    });
            }
        } catch (Exception e) {
            logger.warn("清理临时session文件时发生错误: {}", e.getMessage());
        }
    }
    
    /**
     * 检查MongoDB中session数据的完整性
     * 
     * 用于诊断session数据问题，检查数据库中存储的session信息，
     * 包括认证状态、文件数据、活跃状态等关键信息。
     * 
     * @return Map 包含检查结果的详细信息
     *         - sessions: session列表及详细信息
     *         - summary: 数据统计摘要
     *         - issues: 发现的数据问题
     * 
     * @author liubo
     * @since 2025-01-20
     */
    /**
     * 检查Session数据完整性
     * 
     * 检查所有Telegram会话的数据完整性，包括：
     * - 会话状态统计
     * - 文件数据验证
     * - 数据一致性检查
     * - 健康度评估
     * 
     * @return 包含检查结果的Map对象
     * @author liubo
     * @date 2025-08-05
     */
    public Map<String, Object> checkSessionDataIntegrity() {
        Map<String, Object> result = new HashMap<>();
        List<String> issues = new java.util.ArrayList<>();
        
        try {
            List<TelegramSession> allSessions = sessionService.getAllSessions();
            
            SessionIntegrityStats stats = new SessionIntegrityStats();
            List<Map<String, Object>> sessionDetails = processAllSessions(allSessions, stats, issues);
            
            Map<String, Object> summary = generateIntegritySummary(stats, issues.size());
            
            result.put("sessions", sessionDetails);
            result.put("summary", summary);
            result.put("issues", issues);
            result.put("checkTime", LocalDateTime.now().format(dateTimeFormatter));
            
            logger.info("Session数据完整性检查完成: 总数={}, 活跃={}, 已认证={}, 问题数={}", 
                       stats.totalSessions, stats.activeSessions, stats.readySessions, issues.size());
            
        } catch (Exception e) {
            logger.error("检查session数据完整性时发生错误", e);
            result.put("error", "检查失败: " + e.getMessage());
            issues.add("检查过程中发生异常: " + e.getMessage());
            result.put("issues", issues);
        }
        
        return result;
    }
    
    /**
     * 处理所有Session数据
     * 
     * @param allSessions 所有Session列表
     * @param stats 统计信息对象
     * @param issues 问题列表
     * @return Session详细信息列表
     * @author liubo
     * @date 2025-08-25
     */
    private List<Map<String, Object>> processAllSessions(List<TelegramSession> allSessions, 
                                                         SessionIntegrityStats stats, 
                                                         List<String> issues) {
        List<Map<String, Object>> sessionDetails = new java.util.ArrayList<>();
        stats.totalSessions = allSessions.size();
        
        for (TelegramSession session : allSessions) {
            Map<String, Object> sessionInfo = createSessionInfo(session);
            updateSessionStats(session, stats);
            
            Map<String, Object> fileInfo = analyzeSessionFiles(session, issues, stats);
            sessionInfo.put("fileInfo", fileInfo);
            
            List<String> sessionIssues = validateSessionConsistency(session);
            sessionInfo.put("issues", sessionIssues);
            sessionDetails.add(sessionInfo);
            
            // 添加到全局问题列表
            for (String issue : sessionIssues) {
                issues.add("Session " + session.getPhoneNumber() + ": " + issue);
            }
        }
        
        return sessionDetails;
    }
    
    /**
     * 创建Session基本信息
     * 
     * @param session Session对象
     * @return Session信息Map
     * @author liubo
     * @date 2025-08-25
     */
    private Map<String, Object> createSessionInfo(TelegramSession session) {
        Map<String, Object> sessionInfo = new HashMap<>();
        sessionInfo.put("phoneNumber", session.getPhoneNumber());
        sessionInfo.put("authState", session.getAuthState());
        sessionInfo.put("isActive", session.getIsActive());
        sessionInfo.put("instanceId", session.getInstanceId());
        sessionInfo.put("lastActiveTime", session.getLastActiveTime());
        sessionInfo.put("createdTime", session.getCreatedTime());
        sessionInfo.put("updatedTime", session.getUpdatedTime());
        return sessionInfo;
    }
    
    /**
     * 更新Session统计信息
     * 
     * @param session Session对象
     * @param stats 统计信息对象
     * @author liubo
     * @date 2025-08-25
     */
    private void updateSessionStats(TelegramSession session, SessionIntegrityStats stats) {
        if (Boolean.TRUE.equals(session.getIsActive())) {
            stats.activeSessions++;
        }
        
        if ("READY".equals(session.getAuthState())) {
            stats.readySessions++;
        }
    }
    
    /**
     * 分析Session文件数据
     * 
     * @param session Session对象
     * @param issues 问题列表
     * @param stats 统计信息对象
     * @return 文件信息Map
     * @author liubo
     * @date 2025-08-25
     */
    private Map<String, Object> analyzeSessionFiles(TelegramSession session, List<String> issues, SessionIntegrityStats stats) {
        Map<String, Object> fileInfo = new HashMap<>();
        
        if (session.getDatabaseFiles() != null && !session.getDatabaseFiles().isEmpty()) {
            stats.sessionsWithFiles++;
            return analyzeExistingFiles(session, fileInfo, issues);
        } else {
            stats.sessionsWithoutFiles++;
            return analyzeMissingFiles(session, fileInfo, issues);
        }
    }
    
    /**
     * 分析存在的文件数据
     * 
     * @param session Session对象
     * @param fileInfo 文件信息Map
     * @param issues 问题列表
     * @return 文件信息Map
     * @author liubo
     * @date 2025-08-25
     */
    private Map<String, Object> analyzeExistingFiles(TelegramSession session, 
                                                     Map<String, Object> fileInfo, 
                                                     List<String> issues) {
        fileInfo.put("databaseFileCount", session.getDatabaseFiles().size());
        fileInfo.put("databaseFiles", session.getDatabaseFiles().keySet());
        
        // 检查关键文件是否存在
        boolean hasBinlog = session.getDatabaseFiles().keySet().stream()
            .anyMatch(key -> key.contains("binlog"));
        boolean hasDb = session.getDatabaseFiles().keySet().stream()
            .anyMatch(key -> key.contains(".db") || key.contains(".sqlite"));
        
        fileInfo.put("hasBinlog", hasBinlog);
        fileInfo.put("hasDatabase", hasDb);
        
        if (!hasBinlog) {
            issues.add("Session " + session.getPhoneNumber() + " 缺少binlog文件");
        }
        if (!hasDb) {
            issues.add("Session " + session.getPhoneNumber() + " 缺少数据库文件");
        }
        
        // 检查下载文件
        if (session.getDownloadedFiles() != null) {
            fileInfo.put("downloadedFileCount", session.getDownloadedFiles().size());
        } else {
            fileInfo.put("downloadedFileCount", 0);
        }
        
        return fileInfo;
    }
    
    /**
     * 分析缺失的文件数据
     * 
     * @param session Session对象
     * @param fileInfo 文件信息Map
     * @param issues 问题列表
     * @return 文件信息Map
     * @author liubo
     * @date 2025-08-25
     */
    private Map<String, Object> analyzeMissingFiles(TelegramSession session, 
                                                    Map<String, Object> fileInfo, 
                                                    List<String> issues) {
        fileInfo.put("databaseFileCount", 0);
        fileInfo.put("databaseFiles", new java.util.ArrayList<>());
        fileInfo.put("hasBinlog", false);
        fileInfo.put("hasDatabase", false);
        fileInfo.put("downloadedFileCount", 0);
        
        if ("READY".equals(session.getAuthState())) {
            issues.add("Session " + session.getPhoneNumber() + " 状态为READY但缺少数据库文件");
        }
        
        return fileInfo;
    }
    
    /**
     * 验证Session数据一致性
     * 
     * @param session Session对象
     * @return 问题列表
     * @author liubo
     * @date 2025-08-25
     */
    private List<String> validateSessionConsistency(TelegramSession session) {
        List<String> sessionIssues = new java.util.ArrayList<>();
        
        // 检查认证状态与文件数据的一致性
        if ("READY".equals(session.getAuthState()) && 
            (session.getDatabaseFiles() == null || session.getDatabaseFiles().isEmpty())) {
            sessionIssues.add("认证状态为READY但缺少session文件数据");
        }
        
        // 检查活跃状态与最后活跃时间
        if (Boolean.TRUE.equals(session.getIsActive()) && session.getLastActiveTime() == null) {
            sessionIssues.add("标记为活跃但缺少最后活跃时间");
        }
        
        // 检查API配置
        if (session.getApiId() == null || session.getApiHash() == null) {
            sessionIssues.add("缺少API配置信息");
        }
        
        return sessionIssues;
    }
    
    /**
     * 生成完整性检查摘要
     * 
     * @param stats 统计信息
     * @param totalIssues 总问题数
     * @return 摘要信息Map
     * @author liubo
     * @date 2025-08-25
     */
    private Map<String, Object> generateIntegritySummary(SessionIntegrityStats stats, int totalIssues) {
        Map<String, Object> summary = new HashMap<>();
        summary.put("totalSessions", stats.totalSessions);
        summary.put("activeSessions", stats.activeSessions);
        summary.put("readySessions", stats.readySessions);
        summary.put("sessionsWithFiles", stats.sessionsWithFiles);
        summary.put("sessionsWithoutFiles", stats.sessionsWithoutFiles);
        summary.put("totalIssues", totalIssues);
        
        // 数据健康度评估
        String healthStatus;
        if (totalIssues == 0) {
            healthStatus = "HEALTHY";
        } else if (totalIssues <= stats.totalSessions) {
            healthStatus = "WARNING";
        } else {
            healthStatus = "CRITICAL";
        }
        summary.put("healthStatus", healthStatus);
        
        return summary;
    }
    
    /**
     * Session完整性统计信息内部类
     * 
     * @author liubo
     * @date 2025-08-25
     */
    private static class SessionIntegrityStats {
        int totalSessions = 0;
        int activeSessions = 0;
        int readySessions = 0;
        int sessionsWithFiles = 0;
        int sessionsWithoutFiles = 0;
    }
}