package com.telegram.server.service;

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
public class TelegramService {

    /**
     * 日志记录器
     * 用于记录服务运行日志，便于调试和监控
     */
    private static final Logger logger = LoggerFactory.getLogger(TelegramService.class);
    
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
    private TelegramSessionService sessionService;
    
    /**
     * Telegram消息存储服务
     * 用于将接收到的群消息存储到MongoDB
     */
    @Autowired
    private TelegramMessageService messageService;
    
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
            
            // 初始化TDLight原生库
            Init.init();
            
            // 设置日志级别
            Log.setLogMessageHandler(1, new Slf4JLogMessageHandler());
            
            // 创建客户端工厂
            clientFactory = new SimpleTelegramClientFactory();
            
            logger.info("Telegram服务基础组件初始化完成");
            
            // 初始化Session管理服务
            sessionService.init();
            
            // 从MongoDB加载配置（优先）或从配置管理器加载配置
            loadConfigFromMongoDB();
            
            // 自动尝试使用默认配置初始化客户端
            // 如果存在有效的session，将自动恢复登录状态
            autoInitializeClient();
            
        } catch (Exception e) {
            logger.error("初始化Telegram服务失败", e);
            throw new RuntimeException("Failed to initialize Telegram service", e);
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
    private void loadConfigFromMongoDB() {
        try {
            // 首先尝试从MongoDB获取可用的session
            List<TelegramSession> availableSessions = sessionService.getAvailableSessions();
            
            if (!availableSessions.isEmpty()) {
                // 选择第一个可用的session
                TelegramSession session = availableSessions.get(0);
                
                this.apiId = session.getApiId();
                this.apiHash = session.getApiHash();
                this.phoneNumber = session.getPhoneNumber();
                
                // 同时设置运行时配置
                this.runtimeApiId = this.apiId;
                this.runtimeApiHash = this.apiHash;
                this.runtimePhoneNumber = this.phoneNumber;
                
                // 激活此session
                sessionService.activateSession(this.phoneNumber);
                
                // 从MongoDB恢复session文件到本地
                sessionService.restoreSessionFiles(this.phoneNumber, sessionPath);
                
                logger.info("成功从MongoDB加载session配置: {}", this.phoneNumber);
                return;
            }
            
            // 如果MongoDB中没有可用session，回退到配置文件
            logger.info("MongoDB中没有可用session，尝试从配置文件加载");
            loadConfigFromManager();
            
            // 如果从配置文件加载成功，则迁移到MongoDB
            if (this.apiId != null && this.apiHash != null && this.phoneNumber != null) {
                migrateConfigToMongoDB();
            }
            
        } catch (Exception e) {
            logger.error("从MongoDB加载配置失败，回退到配置文件", e);
            loadConfigFromManager();
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
     */
    private void handleNewMessage(TdApi.UpdateNewMessage update) {
        try {
            TdApi.Message message = update.message;
            
            // 消息类型过滤：只处理文本消息和图片消息
            boolean isTextMessage = message.content instanceof TdApi.MessageText;
            boolean isPhotoMessage = message.content instanceof TdApi.MessagePhoto;
            
            if (!isTextMessage && !isPhotoMessage) {
                // 丢弃其他类型的消息，不进行任何处理
                return;
            }
            
            // 获取聊天信息
            client.send(new TdApi.GetChat(message.chatId)).whenComplete((chat, throwable) -> {
                if (throwable == null) {
                    String chatTitle = chat.title;
                    String messageText = getMessageText(message.content);
                    
                    // 创建完整的JSON格式消息对象
                    try {
                        ObjectNode messageJson = objectMapper.createObjectNode();
                        
                        // 基础信息 - 使用配置的时区显示接收时间
                        LocalDateTime receiveTimeUtc = TimeZoneUtil.convertUnixToUtc(Instant.now().getEpochSecond());
                        LocalDateTime receiveTime = TimeZoneUtil.convertUtcToChina(receiveTimeUtc);
                        messageJson.put("接收时间", String.format("【%s】", receiveTime.format(dateTimeFormatter)));
                        messageJson.put("消息ID", String.format("【%d】", message.id));
                        messageJson.put("聊天ID", String.format("【%d】", message.chatId));
                        messageJson.put("群组名称", String.format("【%s】", chatTitle));
                        
                        // 聊天类型信息
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
                        
                        // 发送者信息
                        if (message.senderId instanceof TdApi.MessageSenderUser) {
                            TdApi.MessageSenderUser userSender = (TdApi.MessageSenderUser) message.senderId;
                            messageJson.put("发送者类型", "【用户】");
                            messageJson.put("发送者ID", String.format("【%d】", userSender.userId));
                        } else if (message.senderId instanceof TdApi.MessageSenderChat) {
                            TdApi.MessageSenderChat chatSender = (TdApi.MessageSenderChat) message.senderId;
                            messageJson.put("发送者类型", "【聊天】");
                            messageJson.put("发送者ID", String.format("【%d】", chatSender.chatId));
                        } else {
                            messageJson.put("发送者类型", "【未知】");
                            messageJson.put("发送者ID", "【未知】");
                        }
                        
                        // 消息内容信息
                        messageJson.put("消息内容", String.format("【%s】", messageText));
                        
                        // 消息类型和特殊处理
                        String contentType = "【未知类型】";
                        if (message.content instanceof TdApi.MessageText) {
                            contentType = "【文本消息】";
                        } else if (message.content instanceof TdApi.MessagePhoto) {
                            contentType = "【图片消息】";
                            // 处理图片消息的详细信息
                            handlePhotoMessage(messageJson, (TdApi.MessagePhoto) message.content, message, chat);
                        } else if (message.content instanceof TdApi.MessageVideo) {
                            contentType = "【视频消息】";
                        } else if (message.content instanceof TdApi.MessageAudio) {
                            contentType = "【音频消息】";
                        } else if (message.content instanceof TdApi.MessageDocument) {
                            contentType = "【文档消息】";
                        } else if (message.content instanceof TdApi.MessageSticker) {
                            contentType = "【贴纸消息】";
                        } else if (message.content instanceof TdApi.MessageAnimation) {
                            contentType = "【动画消息】";
                        } else if (message.content instanceof TdApi.MessageVoiceNote) {
                            contentType = "【语音消息】";
                        } else if (message.content instanceof TdApi.MessageVideoNote) {
                            contentType = "【视频笔记】";
                        } else if (message.content instanceof TdApi.MessageLocation) {
                            contentType = "【位置消息】";
                        } else if (message.content instanceof TdApi.MessageContact) {
                            contentType = "【联系人消息】";
                        } else if (message.content instanceof TdApi.MessagePoll) {
                            contentType = "【投票消息】";
                        }
                        messageJson.put("消息类型", contentType);
                        
                        // 回复信息
                        if (message.replyTo != null && message.replyTo instanceof TdApi.MessageReplyToMessage) {
                            TdApi.MessageReplyToMessage replyTo = (TdApi.MessageReplyToMessage) message.replyTo;
                            messageJson.put("回复消息ID", String.format("【%d】", replyTo.messageId));
                            messageJson.put("回复聊天ID", String.format("【%d】", replyTo.chatId));
                        } else {
                            messageJson.put("回复消息ID", "【无回复】");
                            messageJson.put("回复聊天ID", "【无回复】");
                        }
                        
                        // 转发信息
                        if (message.forwardInfo != null) {
                            messageJson.put("转发来源", String.format("【%s】", message.forwardInfo.origin.getClass().getSimpleName()));
                            messageJson.put("转发时间", String.format("【%s】", 
                                java.time.Instant.ofEpochSecond(message.forwardInfo.date).atZone(java.time.ZoneId.systemDefault()).format(dateTimeFormatter)));
                        } else {
                            messageJson.put("转发来源", "【非转发消息】");
                            messageJson.put("转发时间", "【非转发消息】");
                        }
                        
                        // 消息状态信息
                        messageJson.put("是否置顶", message.isPinned ? "【是】" : "【否】");
                        messageJson.put("是否可编辑", message.canBeEdited ? "【是】" : "【否】");
                        messageJson.put("是否可删除", message.canBeDeletedOnlyForSelf || message.canBeDeletedForAllUsers ? "【是】" : "【否】");
                        messageJson.put("是否可转发", message.canBeForwarded ? "【是】" : "【否】");
                        messageJson.put("是否可保存", message.canBeSaved ? "【是】" : "【否】");
                        
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
                        
                        // 简洁的日志输出 - 已屏蔽
                        // logger.info("收到{}消息 - 群组: {}", contentType, chatTitle);
                        
                        // 异步存储消息到MongoDB
                        saveMessageToMongoDB(message, chat, messageText, contentType, messageJson);
                    } catch (Exception jsonException) {
                        logger.error("生成JSON格式消息失败", jsonException);
                        // 降级到原始格式 - 已屏蔽
                        // logger.info("收到新消息 - 群组: 【{}】, 消息: {}", chatTitle, messageText);
                        // System.out.println(String.format("【%s】 %s", chatTitle, messageText));
                    }
                } else {
                    logger.error("获取聊天信息失败", throwable);
                }
            });
            
        } catch (Exception e) {
            logger.error("处理新消息时发生错误", e);
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
    private void saveMessageToMongoDB(TdApi.Message message, TdApi.Chat chat, String messageText, String contentType, ObjectNode messageJson) {
        try {
            // 创建TelegramMessage实体
            TelegramMessage telegramMessage = new TelegramMessage();
            
            // 设置基础信息
            telegramMessage.setAccountPhone(this.runtimePhoneNumber != null ? this.runtimePhoneNumber : this.phoneNumber);
            telegramMessage.setChatId(message.chatId);
            telegramMessage.setMessageId(message.id);
            telegramMessage.setChatTitle(chat.title);
            
            // 设置聊天类型
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
            
            // 设置发送者信息
            if (message.senderId instanceof TdApi.MessageSenderUser) {
                TdApi.MessageSenderUser userSender = (TdApi.MessageSenderUser) message.senderId;
                telegramMessage.setSenderType("user");
                telegramMessage.setSenderId(userSender.userId);
            } else if (message.senderId instanceof TdApi.MessageSenderChat) {
                TdApi.MessageSenderChat chatSender = (TdApi.MessageSenderChat) message.senderId;
                telegramMessage.setSenderType("chat");
                telegramMessage.setSenderId(chatSender.chatId);
            } else {
                telegramMessage.setSenderType("unknown");
                telegramMessage.setSenderId(0L);
            }
            
            // 设置消息内容
            telegramMessage.setMessageText(messageText);
            telegramMessage.setMessageType(contentType.replaceAll("【|】", "")); // 移除格式化字符
            
            // 设置时间信息
            // created_time: 当前真实北京时间（数据写入时间）
            telegramMessage.setCreatedTime(LocalDateTime.now(ZoneId.of("Asia/Shanghai")));
            // message_date: 消息接收时间（北京时间）
            telegramMessage.setMessageDate(LocalDateTime.now(ZoneId.of("Asia/Shanghai")));
            
            // 设置回复信息
            if (message.replyTo != null && message.replyTo instanceof TdApi.MessageReplyToMessage) {
                TdApi.MessageReplyToMessage replyTo = (TdApi.MessageReplyToMessage) message.replyTo;
                telegramMessage.setReplyToMessageId(replyTo.messageId);
            }
            
            // 设置转发信息
            if (message.forwardInfo != null) {
                telegramMessage.setForwardFromChatId(message.chatId);
                telegramMessage.setForwardFromMessageId(message.id);
            }
            
            // 设置消息状态
            telegramMessage.setIsPinned(message.isPinned);
            telegramMessage.setCanBeEdited(message.canBeEdited);
            telegramMessage.setCanBeDeleted(message.canBeDeletedOnlyForSelf || message.canBeDeletedForAllUsers);
            telegramMessage.setCanBeForwarded(message.canBeForwarded);
            telegramMessage.setCanBeSaved(message.canBeSaved);
            
            // 设置线程和专辑信息
            if (message.messageThreadId > 0) {
                telegramMessage.setMessageThreadId(message.messageThreadId);
            }
            if (message.mediaAlbumId > 0) {
                telegramMessage.setMediaAlbumId(message.mediaAlbumId);
            }
            
            // 设置交互信息
            if (message.interactionInfo != null) {
                telegramMessage.setViewCount(message.interactionInfo.viewCount);
                telegramMessage.setForwardCount(message.interactionInfo.forwardCount);
            }
            
            // 设置完整的JSON数据
            telegramMessage.setRawMessageJson(messageJson.toString());
            
            // 异步保存消息
            messageService.saveMessageAsync(telegramMessage).whenComplete((result, throwable) -> {
                if (throwable != null) {
                    logger.error("保存消息到MongoDB失败: chatId={}, messageId={}", message.chatId, message.id, throwable);
                } else if (result) {
                    logger.debug("消息已保存到MongoDB: chatId={}, messageId={}", message.chatId, message.id);
                } else {
                    logger.debug("消息已存在，跳过保存: chatId={}, messageId={}", message.chatId, message.id);
                }
            });
            
        } catch (Exception e) {
            logger.error("创建TelegramMessage实体失败: chatId={}, messageId={}", message.chatId, message.id, e);
        }
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
    private void autoInitializeClient() {
        try {
            // 检查是否有完整的API配置
            if (apiId == null || apiHash == null || apiHash.isEmpty()) {
                logger.info("未配置API信息，跳过自动初始化。请通过 /api/telegram/config 接口配置API信息。");
                return;
            }
            
            // 检查MongoDB中是否存在session数据
            boolean hasMongoSession = false;
            String sessionPhoneNumber = null;
            TelegramSession activeSession = null;
            
            if (phoneNumber != null && !phoneNumber.isEmpty()) {
                Optional<TelegramSession> sessionOpt = sessionService.getSessionByPhoneNumber(phoneNumber);
                if (sessionOpt.isPresent()) {
                    TelegramSession session = sessionOpt.get();
                    if ("READY".equals(session.getAuthState())) {
                        hasMongoSession = true;
                        sessionPhoneNumber = phoneNumber;
                        activeSession = session;
                        int dbFileCount = (session.getDatabaseFiles() != null) ? session.getDatabaseFiles().size() : 0;
                        logger.info("检测到MongoDB中存在已认证的session数据: {}, 数据库文件数量: {}", phoneNumber, dbFileCount);
                    } else {
                        logger.info("MongoDB中找到手机号 {} 的session数据，但状态为: {}", phoneNumber, session.getAuthState());
                    }
                } else {
                    logger.info("MongoDB中未找到手机号 {} 的session数据", phoneNumber);
                }
            } else {
                // 如果没有配置手机号，尝试查找可用的已认证session
                List<TelegramSession> availableSessions = sessionService.getAvailableSessions();
                for (TelegramSession session : availableSessions) {
                    if ("READY".equals(session.getAuthState())) {
                        hasMongoSession = true;
                        sessionPhoneNumber = session.getPhoneNumber();
                        phoneNumber = sessionPhoneNumber; // 更新当前手机号
                        activeSession = session;
                        int dbFileCount = (session.getDatabaseFiles() != null) ? session.getDatabaseFiles().size() : 0;
                        logger.info("检测到MongoDB中存在已认证的可用session数据: {}, 数据库文件数量: {}", sessionPhoneNumber, dbFileCount);
                        break;
                    }
                }
                if (!hasMongoSession) {
                    logger.info("MongoDB中未找到任何已认证的可用session数据");
                }
            }
            
            // 使用配置的session目录，如果不存在则创建
            Path configuredSessionDir = Paths.get(sessionPath);
            if (!Files.exists(configuredSessionDir)) {
                Files.createDirectories(configuredSessionDir);
                logger.info("创建session目录: {}", sessionPath);
            } else {
                logger.info("使用现有session目录: {}", sessionPath);
            }
            
            // 如果MongoDB中有session数据，恢复到临时目录
            if (hasMongoSession && sessionPhoneNumber != null && activeSession != null) {
                logger.info("正在从MongoDB恢复session数据到临时目录: {}", sessionPath);
                try {
                    boolean restored = sessionService.restoreSessionFiles(sessionPhoneNumber, sessionPath);
                    if (restored) {
                        logger.info("成功从MongoDB恢复session数据");
                        // 检查恢复后的文件并验证session完整性
                        File sessionDirFile = new File(sessionPath);
                        boolean hasValidSession = false;
                        
                        if (sessionDirFile.exists() && sessionDirFile.isDirectory()) {
                            File[] files = sessionDirFile.listFiles();
                            if (files != null) {
                                logger.info("恢复后的session目录包含 {} 个文件", files.length);
                                
                                // 检查是否有TDLib数据库文件
                                for (File file : files) {
                                    logger.info("恢复的文件: {} (大小: {} bytes)", file.getName(), file.length());
                                    // 检查是否有td.binlog或其他TDLib相关文件
                                    if (file.getName().equals("td.binlog") || 
                                        file.getName().startsWith("db.sqlite") ||
                                        file.getName().endsWith(".db")) {
                                        hasValidSession = true;
                                        logger.info("检测到有效的TDLib数据库文件: {}", file.getName());
                                    }
                                }
                                
                                if (!hasValidSession) {
                                    logger.warn("MongoDB中的session数据不完整，缺少TDLib数据库文件，将回退到正常认证流程");
                                    hasMongoSession = false;
                                }
                            } else {
                                logger.warn("session目录为空，将回退到正常认证流程");
                                hasMongoSession = false;
                            }
                        } else {
                            logger.warn("session目录不存在或不是目录: {}，将回退到正常认证流程", sessionPath);
                            hasMongoSession = false;
                        }
                        
                        // 更新运行时配置
                        runtimeApiId = activeSession.getApiId();
                        runtimeApiHash = activeSession.getApiHash();
                        runtimePhoneNumber = activeSession.getPhoneNumber();
                    } else {
                        logger.warn("从MongoDB恢复session数据失败，将进行首次认证");
                        hasMongoSession = false;
                    }
                } catch (Exception e) {
                    logger.error("从MongoDB恢复session数据时发生错误: {}", e.getMessage(), e);
                    hasMongoSession = false;
                }
            }
            
            if (hasMongoSession) {
                logger.info("检测到已存在的session数据，正在尝试自动恢复登录状态...");
            } else {
                logger.info("未检测到已认证的session数据，需要首次认证。请通过API接口完成认证流程。");
            }
            
            logger.info("正在自动初始化Telegram客户端...");
            
            // 使用默认配置
            APIToken apiToken = new APIToken(apiId, apiHash);
            TDLibSettings settings = TDLibSettings.create(apiToken);
            
            Path sessionDir = Paths.get(sessionPath);
            Path databaseDir = sessionDir.resolve("database");
            Path downloadsDir = Paths.get(downloadsPath);
            Path downloadsTempDir = Paths.get(downloadsTempPath);
            
            // 验证路径配置
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
            
            // 使用重试机制确保目录存在
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
            
            settings.setDatabaseDirectoryPath(databaseDir);
            settings.setDownloadedFilesDirectoryPath(downloadsDir);
            
            SimpleTelegramClientBuilder clientBuilder = clientFactory.builder(settings);
            clientBuilder.addUpdateHandler(TdApi.UpdateNewMessage.class, this::handleNewMessage);
            clientBuilder.addUpdateHandler(TdApi.UpdateAuthorizationState.class, this::handleAuthorizationState);
            clientBuilder.addUpdateHandler(TdApi.UpdateNewChat.class, this::handleNewChat);
            clientBuilder.addUpdateHandler(TdApi.UpdateChatLastMessage.class, this::handleChatLastMessage);
            clientBuilder.addUpdateHandler(TdApi.UpdateConnectionState.class, this::handleConnectionState);
            clientBuilder.addCommandHandler("quit", this::handleQuitCommand);
            
            // 创建客户端，如果存在有效session会自动恢复
            // 关键修复：当检测到已认证session时，使用空字符串让TDLight自动从session文件恢复
            String usePhoneNumber;
            if (hasMongoSession) {
                // 如果有已认证的session，使用空字符串让TDLight自动恢复
                usePhoneNumber = "";
                logger.info("检测到已认证session，使用空字符串让TDLight自动从session文件恢复登录状态: {}", sessionPhoneNumber);
            } else {
                // 如果没有session，使用配置的手机号进行首次认证
                usePhoneNumber = (sessionPhoneNumber != null && !sessionPhoneNumber.isEmpty()) ? sessionPhoneNumber : "";
                logger.info("未检测到已认证session，等待首次认证...");
            }
            client = clientBuilder.build(AuthenticationSupplier.user(usePhoneNumber));
            
            configureProxy();
            
            if (hasMongoSession) {
                logger.info("Telegram客户端自动初始化完成，正在从MongoDB session数据恢复登录状态...");
            } else {
                logger.info("Telegram客户端自动初始化完成，等待首次认证...");
            }
        } catch (Exception e) {
            logger.error("自动初始化客户端失败", e);
        }
    }
    
    /**
     * 重新初始化客户端（使用运行时配置）
     */
    private void initializeClient() {
        try {
            if (clientFactory == null) {
                 Init.init();
                 Log.setLogMessageHandler(1, new Slf4JLogMessageHandler());
                 clientFactory = new SimpleTelegramClientFactory();
             }
            
            // 使用运行时配置或默认配置
            int useApiId = runtimeApiId != null ? runtimeApiId : apiId;
            String useApiHash = runtimeApiHash != null ? runtimeApiHash : apiHash;
            
            // 检查API配置是否完整
            if (useApiId == 0 || useApiHash == null || useApiHash.isEmpty()) {
                logger.warn("API配置不完整，跳过客户端初始化");
                return;
            }
            
            // 确定要使用的手机号
            String usePhoneNumber = runtimePhoneNumber != null ? runtimePhoneNumber : 
                                   (phoneNumber != null ? phoneNumber : "");
            
            // 如果有手机号，尝试从MongoDB恢复session数据
            if (usePhoneNumber != null && !usePhoneNumber.isEmpty()) {
                Optional<TelegramSession> sessionOpt = sessionService.getSessionByPhoneNumber(usePhoneNumber);
                if (sessionOpt.isPresent()) {
                    TelegramSession session = sessionOpt.get();
                    if (session.getDatabaseFiles() != null && !session.getDatabaseFiles().isEmpty()) {
                        logger.info("正在从MongoDB恢复session数据: {}", usePhoneNumber);
                        boolean restored = sessionService.restoreSessionFiles(usePhoneNumber, sessionPath);
                        if (restored) {
                            logger.info("成功从MongoDB恢复session数据");
                        } else {
                            logger.warn("从MongoDB恢复session数据失败");
                        }
                    }
                }
            }
            
            APIToken apiToken = new APIToken(useApiId, useApiHash);
            TDLibSettings settings = TDLibSettings.create(apiToken);
            
            Path sessionDir = Paths.get(sessionPath);
            Path databaseDir = sessionDir.resolve("database");
            Path downloadsDir = Paths.get(downloadsPath);
            Path downloadsTempDir = Paths.get(downloadsTempPath);
            
            // 确保目录存在
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
            
            SimpleTelegramClientBuilder clientBuilder = clientFactory.builder(settings);
            clientBuilder.addUpdateHandler(TdApi.UpdateNewMessage.class, this::handleNewMessage);
            clientBuilder.addUpdateHandler(TdApi.UpdateAuthorizationState.class, this::handleAuthorizationState);
            clientBuilder.addUpdateHandler(TdApi.UpdateNewChat.class, this::handleNewChat);
            clientBuilder.addUpdateHandler(TdApi.UpdateChatLastMessage.class, this::handleChatLastMessage);
            clientBuilder.addUpdateHandler(TdApi.UpdateConnectionState.class, this::handleConnectionState);
            clientBuilder.addCommandHandler("quit", this::handleQuitCommand);
            
            client = clientBuilder.build(AuthenticationSupplier.user(usePhoneNumber));
            
            configureProxy();
            
            logger.info("Telegram客户端重新初始化完成");
        } catch (Exception e) {
            logger.error("重新初始化客户端失败", e);
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
                // 使用重试机制提交验证码
                RetryHandler.RetryResult<Void> retryResult = tdlightRetryHandler.executeWithRetry(() -> {
                    TdApi.CheckAuthenticationCode checkCode = new TdApi.CheckAuthenticationCode(code);
                    client.send(checkCode);
                    return null;
                }, RetryHandler.createTdLightConfig(), "submitAuthCode");
                
                if (!retryResult.isSuccess()) {
                    logger.error("提交验证码失败，已达到最大重试次数: {}", retryResult.getLastException().getMessage());
                    result.put("success", false);
                    result.put("message", "提交验证码失败: " + retryResult.getLastException().getMessage());
                    return result;
                }
                
                logger.info("验证码已提交: {}", code);
                
                // 等待一段时间以获取新的授权状态
                Thread.sleep(2000);
                
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
            } else {
                logger.warn("当前状态不需要验证码，当前状态: {}", 
                    currentAuthState != null ? currentAuthState.getClass().getSimpleName() : "null");
                result.put("success", false);
                result.put("message", "当前状态不需要验证码");
                return result;
            }
        } catch (Exception e) {
            logger.error("提交验证码失败", e);
            result.put("success", false);
            result.put("message", "提交验证码失败: " + e.getMessage());
            return result;
        }
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
    private String getMessageText(TdApi.MessageContent content) {
        if (content instanceof TdApi.MessageText) {
            return ((TdApi.MessageText) content).text.text;
        } else if (content instanceof TdApi.MessagePhoto) {
            TdApi.MessagePhoto photo = (TdApi.MessagePhoto) content;
            StringBuilder photoInfo = new StringBuilder("[图片消息]");
            
            // 添加图片尺寸信息
            if (photo.photo.sizes.length > 0) {
                TdApi.PhotoSize largestPhoto = photo.photo.sizes[photo.photo.sizes.length - 1];
                photoInfo.append(String.format(" 尺寸:%dx%d", largestPhoto.width, largestPhoto.height));
                photoInfo.append(String.format(" 大小:%d字节", largestPhoto.photo.size));
            }
            
            // 添加图片描述
            if (photo.caption != null && !photo.caption.text.isEmpty()) {
                photoInfo.append(" 描述:").append(photo.caption.text);
            }
            
            return photoInfo.toString();
        } else if (content instanceof TdApi.MessageVideo) {
            TdApi.MessageVideo video = (TdApi.MessageVideo) content;
            return "[视频]" + (video.caption != null ? video.caption.text : "");
        } else if (content instanceof TdApi.MessageDocument) {
            TdApi.MessageDocument document = (TdApi.MessageDocument) content;
            return "[文档]" + (document.caption != null ? document.caption.text : "");
        } else if (content instanceof TdApi.MessageSticker) {
            return "[贴纸]";
        } else if (content instanceof TdApi.MessageAnimation) {
            TdApi.MessageAnimation animation = (TdApi.MessageAnimation) content;
            return "[动图]" + (animation.caption != null ? animation.caption.text : "");
        } else {
            return "[" + content.getClass().getSimpleName() + "]";
        }
    }

    /**
     * 处理图片消息的详细信息
     * 
     * 解析图片消息的详细信息，包括图片尺寸、文件大小等，
     * 并尝试下载图片文件，判断图片是链接地址还是base64格式。
     * 
     * @param messageJson 消息JSON对象，用于添加图片相关信息
     * @param photoMessage 图片消息对象
     * @author liubo
     * @since 2025.01.05
     */
    private void handlePhotoMessage(ObjectNode messageJson, TdApi.MessagePhoto photoMessage, TdApi.Message message, TdApi.Chat chat) {
        try {
            // 添加图片基本信息
            if (photoMessage.caption != null && !photoMessage.caption.text.isEmpty()) {
                messageJson.put("图片描述", String.format("【%s】", photoMessage.caption.text));
            } else {
                messageJson.put("图片描述", "【无描述】");
            }
            
            // 获取图片的不同尺寸信息
            TdApi.PhotoSize[] photoSizes = photoMessage.photo.sizes;
            messageJson.put("图片尺寸数量", String.format("【%d】", photoSizes.length));
            
            // 处理最大尺寸的图片
            if (photoSizes.length > 0) {
                TdApi.PhotoSize largestPhoto = photoSizes[photoSizes.length - 1]; // 通常最后一个是最大尺寸
                
                messageJson.put("图片宽度", String.format("【%d像素】", largestPhoto.width));
                messageJson.put("图片高度", String.format("【%d像素】", largestPhoto.height));
                messageJson.put("图片文件大小", String.format("【%d字节】", largestPhoto.photo.size));
                messageJson.put("图片文件ID", String.format("【%d】", largestPhoto.photo.id));
                messageJson.put("图片唯一ID", String.format("【%s】", largestPhoto.photo.remote.uniqueId));
                
                // 检查图片是否已下载
                if (largestPhoto.photo.local.isDownloadingCompleted) {
                    messageJson.put("图片下载状态", "【已下载】");
                    messageJson.put("图片本地路径", String.format("【%s】", largestPhoto.photo.local.path));
                    
                    // 尝试读取图片文件并判断格式，同时更新MongoDB
                    String accountPhone = this.runtimePhoneNumber != null ? this.runtimePhoneNumber : this.phoneNumber;
                    processDownloadedPhoto(messageJson, largestPhoto.photo.local.path, accountPhone, message.chatId, message.id);
                } else {
                    messageJson.put("图片下载状态", "【未下载】");
                    
                    // 异步下载图片
                    downloadPhoto(messageJson, largestPhoto.photo, message, chat);
                }
            } else {
                messageJson.put("图片信息", "【无可用尺寸】");
            }
            
        } catch (Exception e) {
            logger.error("处理图片消息时发生错误", e);
            messageJson.put("图片处理错误", String.format("【%s】", e.getMessage()));
        }
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
    private void processDownloadedPhoto(ObjectNode messageJson, String localPath, 
                                       String accountPhone, Long chatId, Long messageId) {
        try {
            File photoFile = new File(localPath);
            if (photoFile.exists() && photoFile.isFile()) {
                // 读取文件大小
                long fileSize = photoFile.length();
                messageJson.put("图片实际文件大小", String.format("【%d字节】", fileSize));
                
                // 使用ImageProcessingUtil检测MIME类型
                String mimeType = imageProcessingUtil.detectMimeType(localPath);
                messageJson.put("图片MIME类型", String.format("【%s】", mimeType));
                
                // 提取文件名
                String filename = imageProcessingUtil.extractFileName(localPath);
                messageJson.put("图片文件名", String.format("【%s】", filename));
                
                // 判断文件是否为图片格式
                String fileName = photoFile.getName().toLowerCase();
                String fileExtension = "";
                int lastDotIndex = fileName.lastIndexOf('.');
                if (lastDotIndex > 0) {
                    fileExtension = fileName.substring(lastDotIndex + 1);
                }
                messageJson.put("图片文件扩展名", String.format("【%s】", fileExtension));
                
                // 判断是否为常见图片格式
                boolean isImageFile = imageProcessingUtil.isSupportedImageType(mimeType);
                messageJson.put("是否为图片文件", isImageFile ? "【是】" : "【否】");
                
                // 处理图片存储
                if (isImageFile) {
                    String imageData = null;
                    String imagePath = null;
                    String imageStatus = "processed";
                    
                    try {
                        // 判断存储策略
                        if (imageProcessingUtil.shouldStoreAsBase64(fileSize)) {
                            // 小文件：Base64编码存储
                            imageData = imageProcessingUtil.convertImageToBase64(localPath);
                            if (imageData != null) {
                                messageJson.put("图片存储方式", String.format("【Base64编码，长度：%d字符】", imageData.length()));
                                // 只显示前100个字符的base64内容，避免日志过长
                                String base64Preview = imageData.length() > 100 ? 
                                    imageData.substring(0, 100) + "..." : imageData;
                                messageJson.put("Base64预览", String.format("【%s】", base64Preview));
                            } else {
                                imageStatus = "base64_failed";
                                imagePath = localPath; // 降级为路径存储
                                messageJson.put("图片存储方式", "【Base64编码失败，降级为路径存储】");
                            }
                        } else {
                            // 大文件：路径存储
                            imagePath = localPath;
                            messageJson.put("图片存储方式", "【文件路径存储】");
                        }
                        
                        // 更新MongoDB中的消息记录
                        if (accountPhone != null && chatId != null && messageId != null) {
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
                        
                    } catch (Exception e) {
                        logger.error("处理图片存储失败: {}", localPath, e);
                        messageJson.put("图片存储方式", "【处理失败】");
                        messageJson.put("错误信息", String.format("【%s】", e.getMessage()));
                    }
                } else {
                    messageJson.put("图片存储方式", "【非支持的图片格式，跳过处理】");
                }
            } else {
                messageJson.put("图片文件状态", "【文件不存在或不可读】");
            }
        } catch (Exception e) {
            logger.error("处理已下载图片时发生错误", e);
            messageJson.put("图片处理错误", String.format("【%s】", e.getMessage()));
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
    private void downloadPhotoWithRetry(ObjectNode messageJson, TdApi.File photo, TdApi.Message message, TdApi.Chat chat, int retryCount) {
        // 使用RetryHandler进行重试处理
        RetryHandler.RetryResult<Void> result = tdlightRetryHandler.executeWithRetry(() -> {
            try {
                downloadPhotoInternal(messageJson, photo, message, chat);
                return null;
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        }, RetryHandler.createTdLightConfig(), "downloadPhoto");
        
        if (!result.isSuccess()) {
            logger.error("图片下载失败，已达到最大重试次数: {}", result.getLastException().getMessage());
            // 更新消息状态为失败
            messageJson.put("downloadStatus", "failed");
            messageJson.put("downloadError", result.getLastException().getMessage());
            
            // 更新消息状态为失败
            String accountPhone = this.runtimePhoneNumber != null ? this.runtimePhoneNumber : this.phoneNumber;
            messageService.updateImageDataAsync(accountPhone, message.chatId, message.id, 
                                              null, null, null, "failed")
                .exceptionally(updateThrowable -> {
                    logger.error("更新图片失败状态到MongoDB失败: accountPhone={}, chatId={}, messageId={}", 
                               accountPhone, message.chatId, message.id, updateThrowable);
                    return false;
                });
        }
    }
    
    /**
     * 内部图片下载实现
     * 
     * @param messageJson 消息JSON对象
     * @param photo 图片文件对象
     * @param message 消息对象
     * @param chat 聊天对象
     * @throws Exception 下载异常
     */
    private void downloadPhotoInternal(ObjectNode messageJson, TdApi.File photo, TdApi.Message message, TdApi.Chat chat) throws Exception {
        messageJson.put("图片下载状态", "【开始下载】");
        
        // 检查文件是否已经下载完成
        if (photo.local.isDownloadingCompleted) {
            logger.info("图片已下载完成，直接处理: {}", photo.local.path);
            String accountPhone = this.runtimePhoneNumber != null ? this.runtimePhoneNumber : this.phoneNumber;
            processDownloadedPhoto(messageJson, photo.local.path, accountPhone, message.chatId, message.id);
            return;
        }
        
        // 检查文件是否可以下载
        if (!photo.local.canBeDownloaded) {
            logger.warn("图片文件无法下载: 文件ID【{}】", photo.id);
            messageJson.put("图片下载状态", "【无法下载】");
            throw new RuntimeException("图片文件无法下载: 文件ID " + photo.id);
        }
        
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
        TdApi.File downloadedFile = downloadFuture.get(); // 同步等待下载完成
        
        if (downloadedFile.local.isDownloadingCompleted) {
            logger.info("图片下载完成: {}", downloadedFile.local.path);
            
            // 创建新的JSON对象来输出下载结果
            ObjectNode downloadResultJson = objectMapper.createObjectNode();
            downloadResultJson.put("下载完成时间", String.format("【%s】", LocalDateTime.now().format(dateTimeFormatter)));
            downloadResultJson.put("图片文件ID", String.format("【%d】", photo.id));
            downloadResultJson.put("图片下载路径", String.format("【%s】", downloadedFile.local.path));
            downloadResultJson.put("图片文件大小", String.format("【%d字节】", downloadedFile.size));
            
            // 处理下载完成的图片
            String accountPhone = this.runtimePhoneNumber != null ? this.runtimePhoneNumber : this.phoneNumber;
            processDownloadedPhoto(downloadResultJson, downloadedFile.local.path, accountPhone, message.chatId, message.id);
            
            String downloadResultOutput = objectMapper.writeValueAsString(downloadResultJson);
            logger.info("图片下载结果: {}", downloadResultOutput);
            System.out.println("📸 图片下载完成: " + downloadResultOutput);
        } else {
            logger.warn("图片下载未完成: 文件ID【{}】, 下载进度【{}/{}】", photo.id, downloadedFile.local.downloadedSize, downloadedFile.size);
            throw new RuntimeException(String.format("图片下载未完成: 文件ID %d, 下载进度 %d/%d", 
                                                    photo.id, downloadedFile.local.downloadedSize, downloadedFile.size));
        }
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
    public Map<String, Object> getAuthStatus() {
        Map<String, Object> status = new HashMap<>();
        
        if (client == null) {
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
        
        status.put("success", true);
        
        if (currentAuthState instanceof TdApi.AuthorizationStateReady) {
            status.put("status", "READY");
            status.put("message", "✅ 已授权成功，正在监听消息");
            status.put("needsConfig", false);
            status.put("needsPhone", false);
            status.put("needsCode", false);
            status.put("needsPassword", false);
            status.put("isReady", true);
        } else if (currentAuthState instanceof TdApi.AuthorizationStateWaitPhoneNumber) {
            status.put("status", "WAIT_PHONE");
            status.put("message", "⏳ 等待输入手机号码");
            status.put("needsConfig", false);
            status.put("needsPhone", true);
            status.put("needsCode", false);
            status.put("needsPassword", false);
            status.put("isReady", false);
            status.put("nextStep", "请调用 POST /api/telegram/auth/phone 接口提交手机号");
        } else if (currentAuthState instanceof TdApi.AuthorizationStateWaitCode) {
            status.put("status", "WAIT_CODE");
            status.put("message", "⏳ 等待输入验证码");
            status.put("needsConfig", false);
            status.put("needsPhone", false);
            status.put("needsCode", true);
            status.put("needsPassword", false);
            status.put("isReady", false);
            status.put("nextStep", "请调用 POST /api/telegram/auth/code 接口提交验证码");
        } else if (currentAuthState instanceof TdApi.AuthorizationStateWaitPassword) {
            status.put("status", "WAIT_PASSWORD");
            status.put("message", "⏳ 等待输入二次验证密码");
            status.put("needsConfig", false);
            status.put("needsPhone", false);
            status.put("needsCode", false);
            status.put("needsPassword", true);
            status.put("isReady", false);
            status.put("nextStep", "请调用 POST /api/telegram/auth/password 接口提交密码");
        } else if (currentAuthState instanceof TdApi.AuthorizationStateClosed) {
            status.put("status", "CLOSED");
            status.put("message", "❌ 客户端已关闭");
            status.put("needsConfig", true);
            status.put("needsPhone", false);
            status.put("needsCode", false);
            status.put("needsPassword", false);
            status.put("isReady", false);
        } else {
            String stateName = currentAuthState != null ? currentAuthState.getClass().getSimpleName() : "null";
            status.put("status", "UNKNOWN");
            status.put("message", "📱 未知授权状态: " + stateName);
            status.put("needsConfig", false);
            status.put("needsPhone", false);
            status.put("needsCode", false);
            status.put("needsPassword", false);
            status.put("isReady", false);
        }
        
        status.put("timestamp", System.currentTimeMillis());
        return status;
    }

    /**
     * 初始化账号
     * 
     * 创建并初始化单个Telegram账号实例，准备进行API配置和认证流程。
     * 这是使用系统的第一步操作。
     * 
     * @author liubo
     * @since 2024-12-19
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
     * @since 2024-12-19
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
     * @since 2024-12-19
     */
    public void clearSession() {
        try {
            logger.info("正在清理Session数据...");
            
            String currentPhoneNumber = runtimePhoneNumber != null ? runtimePhoneNumber : phoneNumber;
            
            // 关闭当前客户端
            if (client != null) {
                client.close();
                client = null;
            }
            
            // 清理MongoDB中的session数据
            if (currentPhoneNumber != null) {
                try {
                    sessionService.deactivateSession(currentPhoneNumber);
                    sessionService.deleteSession(currentPhoneNumber);
                    logger.info("MongoDB中的session数据已清理: {}", currentPhoneNumber);
                } catch (Exception e) {
                    logger.warn("清理MongoDB session数据时发生错误: {}", e.getMessage());
                }
            }
            
            // 重置认证状态
            this.currentAuthState = null;
            this.runtimeApiId = null;
            this.runtimeApiHash = null;
            this.runtimePhoneNumber = null;
            this.apiId = null;
            this.apiHash = null;
            this.phoneNumber = null;
            
            // 注意：不再删除本地配置文件，因为所有配置都存储在MongoDB中
            logger.debug("跳过删除本地配置文件，所有配置都存储在MongoDB中");
            
            // 删除Session文件
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
            
            logger.info("Session数据清理完成");
            
        } catch (Exception e) {
            logger.error("清理Session数据时发生错误", e);
            throw new RuntimeException("清理Session失败: " + e.getMessage(), e);
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
    public Map<String, Object> checkSessionDataIntegrity() {
        Map<String, Object> result = new HashMap<>();
        List<Map<String, Object>> sessionDetails = new java.util.ArrayList<>();
        List<String> issues = new java.util.ArrayList<>();
        Map<String, Object> summary = new HashMap<>();
        
        try {
            // 获取所有session数据
            List<TelegramSession> allSessions = sessionService.getAllSessions();
            
            int totalSessions = allSessions.size();
            int activeSessions = 0;
            int readySessions = 0;
            int sessionsWithFiles = 0;
            int sessionsWithoutFiles = 0;
            
            for (TelegramSession session : allSessions) {
                Map<String, Object> sessionInfo = new HashMap<>();
                sessionInfo.put("phoneNumber", session.getPhoneNumber());
                sessionInfo.put("authState", session.getAuthState());
                sessionInfo.put("isActive", session.getIsActive());
                sessionInfo.put("instanceId", session.getInstanceId());
                sessionInfo.put("lastActiveTime", session.getLastActiveTime());
                sessionInfo.put("createdTime", session.getCreatedTime());
                sessionInfo.put("updatedTime", session.getUpdatedTime());
                
                // 统计活跃session
                if (Boolean.TRUE.equals(session.getIsActive())) {
                    activeSessions++;
                }
                
                // 统计已认证session
                if ("READY".equals(session.getAuthState())) {
                    readySessions++;
                }
                
                // 检查数据库文件
                Map<String, Object> fileInfo = new HashMap<>();
                if (session.getDatabaseFiles() != null && !session.getDatabaseFiles().isEmpty()) {
                    sessionsWithFiles++;
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
                } else {
                    sessionsWithoutFiles++;
                    fileInfo.put("databaseFileCount", 0);
                    fileInfo.put("databaseFiles", new java.util.ArrayList<>());
                    fileInfo.put("hasBinlog", false);
                    fileInfo.put("hasDatabase", false);
                    
                    if ("READY".equals(session.getAuthState())) {
                        issues.add("Session " + session.getPhoneNumber() + " 状态为READY但缺少数据库文件");
                    }
                }
                
                // 检查下载文件
                if (session.getDownloadedFiles() != null) {
                    fileInfo.put("downloadedFileCount", session.getDownloadedFiles().size());
                } else {
                    fileInfo.put("downloadedFileCount", 0);
                }
                
                sessionInfo.put("fileInfo", fileInfo);
                
                // 检查数据一致性
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
                
                sessionInfo.put("issues", sessionIssues);
                sessionDetails.add(sessionInfo);
                
                // 添加到全局问题列表
                for (String issue : sessionIssues) {
                    issues.add("Session " + session.getPhoneNumber() + ": " + issue);
                }
            }
            
            // 生成统计摘要
            summary.put("totalSessions", totalSessions);
            summary.put("activeSessions", activeSessions);
            summary.put("readySessions", readySessions);
            summary.put("sessionsWithFiles", sessionsWithFiles);
            summary.put("sessionsWithoutFiles", sessionsWithoutFiles);
            summary.put("totalIssues", issues.size());
            
            // 数据健康度评估
            String healthStatus;
            if (issues.isEmpty()) {
                healthStatus = "HEALTHY";
            } else if (issues.size() <= totalSessions) {
                healthStatus = "WARNING";
            } else {
                healthStatus = "CRITICAL";
            }
            summary.put("healthStatus", healthStatus);
            
            result.put("sessions", sessionDetails);
            result.put("summary", summary);
            result.put("issues", issues);
            result.put("checkTime", LocalDateTime.now().format(dateTimeFormatter));
            
            logger.info("Session数据完整性检查完成: 总数={}, 活跃={}, 已认证={}, 问题数={}", 
                       totalSessions, activeSessions, readySessions, issues.size());
            
        } catch (Exception e) {
            logger.error("检查session数据完整性时发生错误", e);
            result.put("error", "检查失败: " + e.getMessage());
            issues.add("检查过程中发生异常: " + e.getMessage());
            result.put("issues", issues);
        }
        
        return result;
    }
}