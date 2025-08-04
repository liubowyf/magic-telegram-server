package com.telegram.server.service;

import com.telegram.server.entity.TelegramAccount;
import com.telegram.server.util.SessionCredentialsManager;
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
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;

/**
 * 多账号Telegram服务管理器
 * 
 * 负责管理多个Telegram账号的完整生命周期，包括账号创建、API配置、
 * 认证流程、消息监听、会话管理等核心功能。支持同时管理多个独立的
 * Telegram账号，每个账号拥有独立的会话数据和配置信息。
 * 
 * 主要功能：
 * - 账号生命周期管理：创建、配置、初始化、删除账号
 * - 认证流程管理：手机号验证、短信验证码、两步验证密码
 * - 消息监听控制：开启/关闭指定账号的消息监听功能
 * - 会话数据管理：保存、加载、清理账号的会话数据
 * - 批量操作支持：批量开启监听、清理所有会话等
 * - 状态监控：实时获取账号状态、认证状态、连接状态
 * 
 * 技术特性：
 * - 基于TDLight库实现Telegram客户端功能
 * - 使用ConcurrentHashMap确保线程安全的账号管理
 * - 支持SOCKS5代理配置
 * - 自动会话恢复机制
 * - 加密存储API凭据
 * - 异步消息处理
 * 
 * 账号状态流转：
 * CREATED -> CONFIGURING -> AUTHENTICATING -> READY -> LISTENING
 *     ↓           ↓              ↓           ↓         ↓
 *   ERROR ←---- ERROR ←------ ERROR ←---- ERROR ← ERROR
 * 
 * 使用示例：
 * 1. 创建账号：createAccount(nickname)
 * 2. 配置API：configAccountApi(accountId, apiId, apiHash)
 * 3. 初始化客户端：initializeAccountClient(accountId)
 * 4. 提交手机号：submitPhoneNumber(accountId, phoneNumber)
 * 5. 提交验证码：submitAuthCode(accountId, code)
 * 6. 开启监听：setAccountListening(accountId, true)
 * 
 * @author liubo
 * @version 1.0
 * @since 2025.08.01
 */
@Service
public class MultiAccountTelegramService {

    /**
     * 日志记录器
     * 用于记录多账号服务的运行日志和调试信息
     */
    private static final Logger logger = LoggerFactory.getLogger(MultiAccountTelegramService.class);
    
    /**
     * JSON对象映射器
     * 用于序列化和反序列化JSON数据，特别是消息内容的格式化
     */
    private static final ObjectMapper objectMapper = new ObjectMapper();
    
    /**
     * 日期时间格式化器
     * 统一的时间格式，用于日志记录和消息时间戳格式化
     */
    private static final DateTimeFormatter dateTimeFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    /**
     * Telegram会话数据存储基础路径
     * 所有账号的会话数据都存储在此路径下的子目录中
     * 默认值：./telegram-sessions
     */
    @Value("${telegram.session.base.path:./telegram-sessions}")
    private String sessionBasePath;

    /**
     * SOCKS5代理服务器主机地址
     * 用于配置Telegram客户端的网络代理
     * 默认值：127.0.0.1（本地代理）
     */
    @Value("${proxy.socks5.host:127.0.0.1}")
    private String proxyHost;

    /**
     * SOCKS5代理服务器端口
     * 与proxyHost配合使用，构成完整的代理配置
     * 默认值：7890（常用的代理端口）
     */
    @Value("${proxy.socks5.port:7890}")
    private int proxyPort;

    /**
     * 会话凭据管理器
     * 负责安全地存储和加载各账号的API凭据信息
     */
    @Autowired
    private SessionCredentialsManager credentialsManager;

    /**
     * Telegram客户端工厂
     * 用于创建和管理多个Telegram客户端实例
     */
    private SimpleTelegramClientFactory clientFactory;
    
    /**
     * 账号信息存储容器
     * 
     * 使用ConcurrentHashMap确保线程安全的多账号管理。
     * Key: accountId（账号唯一标识符，UUID格式）
     * Value: TelegramAccount（账号完整信息对象）
     * 
     * 存储内容包括：
     * - 账号基本信息（ID、昵称、手机号）
     * - API配置信息（apiId、apiHash）
     * - 客户端实例和认证状态
     * - 会话路径和监听配置
     * - 创建时间和最后活跃时间
     */
    private final Map<String, TelegramAccount> accounts = new ConcurrentHashMap<>();
    


    /**
     * 初始化多账号Telegram服务
     * 
     * 在Spring容器启动后自动执行的初始化方法，负责设置TDLight库、
     * 配置日志系统、创建客户端工厂，并自动恢复已存在的会话。
     * 
     * 初始化步骤：
     * 1. 初始化TDLight原生库（加载必要的本地库文件）
     * 2. 配置SLF4J日志处理器，统一日志输出格式
     * 3. 创建SimpleTelegramClientFactory实例
     * 4. 自动扫描并恢复已存在的会话目录
     * 5. 重建账号对象和客户端连接
     * 
     * 异常处理：
     * - 如果初始化失败，会抛出RuntimeException
     * - 确保服务启动失败时能够被Spring容器感知
     * 
     * @throws RuntimeException 当TDLight库初始化失败或客户端工厂创建失败时
     */
    @PostConstruct
    public void init() {
        try {
            logger.info("正在初始化多账号Telegram服务...");
            
            // 初始化TDLight原生库
            Init.init();
            
            // 设置日志级别
            Log.setLogMessageHandler(1, new Slf4JLogMessageHandler());
            
            // 创建客户端工厂
            clientFactory = new SimpleTelegramClientFactory();
            
            logger.info("多账号Telegram服务初始化完成");
            
            // 自动恢复已存在的会话
            autoRecoverExistingSessions();
            
        } catch (Exception e) {
            logger.error("初始化多账号Telegram服务失败", e);
            throw new RuntimeException("Failed to initialize multi-account Telegram service", e);
        }
    }

    /**
     * 创建新的Telegram账号
     * 
     * 创建一个新的Telegram账号实例，分配唯一的账号ID和会话目录，
     * 并将账号信息存储到内存中。新创建的账号处于CREATED状态，
     * 需要后续配置API信息和进行认证流程。
     * 
     * 创建流程：
     * 1. 生成UUID作为唯一的账号标识符
     * 2. 基于sessionBasePath和accountId创建会话目录路径
     * 3. 创建TelegramAccount对象并设置基本信息
     * 4. 将账号信息存储到accounts映射中
     * 5. 记录账号创建日志
     * 
     * 账号初始状态：
     * - status: CREATED
     * - authState: null
     * - client: null
     * - listeningEnabled: false
     * - authorizationInProgress: false
     * 
     * @param nickname 账号昵称，用于标识和管理账号，不能为空
     * @return 新创建账号的唯一标识符（UUID格式），用于后续所有操作
     */
    public String createAccount(String nickname) {
        String accountId = UUID.randomUUID().toString();
        TelegramAccount account = new TelegramAccount(accountId, nickname);
        
        // 设置session路径
        String sessionPath = sessionBasePath + "/" + accountId;
        account.setSessionPath(sessionPath);
        
        accounts.put(accountId, account);
        
        logger.info("创建新账号: {} ({})", nickname, accountId);
        return accountId;
    }

    /**
     * 配置账号API信息
     * 
     * @param accountId 账号ID
     * @param apiId API ID
     * @param apiHash API Hash
     * @return 配置是否成功
     */
    public boolean configAccountApi(String accountId, Integer apiId, String apiHash) {
        TelegramAccount account = accounts.get(accountId);
        if (account == null) {
            logger.error("账号不存在: {}", accountId);
            return false;
        }
        
        try {
            account.setApiId(apiId);
            account.setApiHash(apiHash);
            account.setStatus(TelegramAccount.AccountStatus.CONFIGURING);
            
            // 保存API凭据到session目录
            boolean saved = credentialsManager.saveCredentials(
                account.getSessionPath(), 
                apiId, 
                apiHash, 
                account.getPhoneNumber()
            );
            
            if (saved) {
                logger.info("账号 {} API配置成功，凭据已保存到session目录", accountId);
            } else {
                logger.warn("账号 {} API配置成功，但凭据保存失败", accountId);
            }
            
            // API配置成功，等待手机号提交后再初始化客户端
            logger.info("账号 {} API配置完成，等待手机号提交", accountId);
            
            return true;
        } catch (Exception e) {
            logger.error("配置账号 {} API失败", accountId, e);
            account.setStatus(TelegramAccount.AccountStatus.ERROR);
            return false;
        }
    }

    /**
     * 初始化账号客户端
     * 
     * @param accountId 账号ID
     * @return 初始化是否成功
     */
    public boolean initializeAccountClient(String accountId) {
        TelegramAccount account = accounts.get(accountId);
        if (account == null || !account.isApiConfigured()) {
            logger.error("账号不存在或API未配置: {}", accountId);
            return false;
        }
        
        try {
            // 创建API Token
            APIToken apiToken = new APIToken(account.getApiId(), account.getApiHash());
            
            // 配置TDLib设置
            TDLibSettings settings = TDLibSettings.create(apiToken);
            Path sessionPath = Paths.get(account.getSessionPath());
            settings.setDatabaseDirectoryPath(sessionPath.resolve("data"));
            settings.setDownloadedFilesDirectoryPath(sessionPath.resolve("downloads"));
            
            // 配置代理
            configureProxy(settings);
            
            // 构建客户端 - 使用简单的用户认证
            SimpleTelegramClientBuilder builder = clientFactory.builder(settings);
            builder.addUpdateHandler(TdApi.UpdateNewMessage.class, update -> {
                handleNewMessage(accountId, update);
            });
            builder.addUpdateHandler(TdApi.UpdateAuthorizationState.class, update -> {
                handleAuthorizationState(accountId, update);
            });
            builder.addUpdateHandler(TdApi.UpdateNewChat.class, update -> {
                handleNewChat(accountId, update);
            });
            builder.addUpdateHandler(TdApi.UpdateChatLastMessage.class, update -> {
                handleChatLastMessage(accountId, update);
            });
            builder.addUpdateHandler(TdApi.UpdateConnectionState.class, update -> {
                handleConnectionState(accountId, update);
            });
            SimpleTelegramClient client = builder.build(AuthenticationSupplier.user(account.getPhoneNumber()));
            
            account.setClient(client);
            account.setStatus(TelegramAccount.AccountStatus.AUTHENTICATING);
            
            logger.info("账号 {} 客户端初始化成功", accountId);
            return true;
            
        } catch (Exception e) {
            logger.error("初始化账号 {} 客户端失败", accountId, e);
            account.setStatus(TelegramAccount.AccountStatus.ERROR);
            return false;
        }
    }

    /**
     * 提交手机号进行认证
     * 
     * @param accountId 账号ID
     * @param phoneNumber 手机号
     * @return 提交是否成功
     */
    public boolean submitPhoneNumber(String accountId, String phoneNumber) {
        TelegramAccount account = accounts.get(accountId);
        if (account == null) {
            logger.error("账号不存在: {}", accountId);
            return false;
        }
        
        // 检查是否已有授权流程在进行中
        if (account.isAuthorizationInProgress()) {
            logger.warn("账号 {} 已有授权流程在进行中，跳过重复提交", accountId);
            return false;
        }
        
        try {
            // 设置手机号
            account.setPhoneNumber(phoneNumber);
            logger.info("保存手机号: {} - {}", accountId, phoneNumber);
            
            // 初始化客户端（如果还没有初始化）
            if (!initializeAccountClient(accountId)) {
                logger.error("客户端初始化失败: {}", accountId);
                return false;
            }
            
            // 检查客户端是否成功初始化
            if (account.getClient() == null) {
                logger.error("账号不存在或客户端未初始化: {}", accountId);
                return false;
            }

            // 设置授权流程进行中，避免重复提交
            account.setAuthorizationInProgress(true);
            account.setStatus(TelegramAccount.AccountStatus.AUTHENTICATING);
            
            // 直接发送手机号进行认证（参考单账户版本的实现）
            account.getClient().send(new TdApi.SetAuthenticationPhoneNumber(phoneNumber, null), result -> {
                if (result.isError()) {
                    logger.error("提交手机号失败: {} - {}", accountId, result);
                    account.setStatus(TelegramAccount.AccountStatus.ERROR);
                    account.setAuthorizationInProgress(false);
                } else {
                    logger.info("手机号提交成功: {} - {}", accountId, phoneNumber);
                    
                    // 手机号提交成功后，保存凭据
                    credentialsManager.saveCredentials(
                        account.getSessionPath(),
                        account.getApiId(),
                        account.getApiHash(),
                        phoneNumber
                    );
                }
            });
            
            return true;
        } catch (Exception e) {
            logger.error("提交手机号异常: {} - {}", accountId, e.getMessage(), e);
            account.setAuthorizationInProgress(false);
            return false;
        }
    }

    /**
     * 提交验证码
     * 
     * @param accountId 账号ID
     * @param code 验证码
     * @return 提交结果
     */
    public Map<String, Object> submitAuthCode(String accountId, String code) {
        Map<String, Object> result = new HashMap<>();
        TelegramAccount account = accounts.get(accountId);
        
        if (account == null || account.getClient() == null) {
            result.put("success", false);
            result.put("message", "账号不存在或客户端未初始化");
            return result;
        }
        
        try {
            // 发送验证码到Telegram服务器
            account.getClient().send(new TdApi.CheckAuthenticationCode(code), response -> {
                if (response.isError()) {
                    logger.error("账号 {} 验证码验证失败: {}", accountId, response);
                } else {
                    logger.info("账号 {} 验证码验证成功", accountId);
                }
            });
            
            result.put("success", true);
            result.put("message", "验证码已提交");
        } catch (Exception e) {
            logger.error("账号 {} 提交验证码异常", accountId, e);
            result.put("success", false);
            result.put("message", "提交验证码时发生错误: " + e.getMessage());
        }
        
        return result;
    }

    /**
     * 提交密码
     * 
     * @param accountId 账号ID
     * @param password 密码
     * @return 提交结果
     */
    public Map<String, Object> submitPassword(String accountId, String password) {
        Map<String, Object> result = new HashMap<>();
        TelegramAccount account = accounts.get(accountId);
        
        if (account == null || account.getClient() == null) {
            result.put("success", false);
            result.put("message", "账号不存在或客户端未初始化");
            return result;
        }
        
        try {
            // 发送密码到Telegram服务器
            account.getClient().send(new TdApi.CheckAuthenticationPassword(password), response -> {
                if (response.isError()) {
                    logger.error("账号 {} 密码验证失败: {}", accountId, response);
                } else {
                    logger.info("账号 {} 密码验证成功", accountId);
                }
            });
            
            result.put("success", true);
            result.put("message", "密码已提交");
        } catch (Exception e) {
            logger.error("账号 {} 提交密码异常", accountId, e);
            result.put("success", false);
            result.put("message", "提交密码时发生错误: " + e.getMessage());
        }
        
        return result;
    }

    /**
     * 获取所有账号列表
     * 
     * @return 账号列表
     */
    public List<Map<String, Object>> getAllAccounts() {
        List<Map<String, Object>> accountList = new ArrayList<>();
        
        for (TelegramAccount account : accounts.values()) {
            Map<String, Object> accountInfo = new HashMap<>();
            accountInfo.put("accountId", account.getAccountId());
            accountInfo.put("nickname", account.getNickname());
            accountInfo.put("phoneNumber", account.getPhoneNumber());
            accountInfo.put("status", account.getStatus().name());
            accountInfo.put("isAuthenticated", account.isAuthenticated());
            accountInfo.put("listeningEnabled", account.isListeningEnabled());
            accountInfo.put("createTime", account.getCreateTime().format(dateTimeFormatter));
            accountInfo.put("lastActiveTime", account.getLastActiveTime().format(dateTimeFormatter));
            
            accountList.add(accountInfo);
        }
        
        return accountList;
    }

    /**
     * 获取账号详细状态
     * 
     * @param accountId 账号ID
     * @return 账号状态信息
     */
    public Map<String, Object> getAccountStatus(String accountId) {
        Map<String, Object> status = new HashMap<>();
        TelegramAccount account = accounts.get(accountId);
        
        if (account == null) {
            status.put("success", false);
            status.put("message", "账号不存在");
            return status;
        }
        
        status.put("success", true);
        status.put("accountId", account.getAccountId());
        status.put("nickname", account.getNickname());
        status.put("phoneNumber", account.getPhoneNumber());
        status.put("status", account.getStatus().name());
        status.put("isReady", account.isAuthenticated());
        status.put("listeningEnabled", account.isListeningEnabled());
        
        // 根据认证状态提供详细信息
        TdApi.AuthorizationState authState = account.getAuthState();
        if (authState instanceof TdApi.AuthorizationStateWaitPhoneNumber) {
            status.put("message", "等待手机号");
            status.put("nextStep", "submitPhone");
        } else if (authState instanceof TdApi.AuthorizationStateWaitCode) {
            status.put("message", "等待验证码");
            status.put("nextStep", "submitCode");
        } else if (authState instanceof TdApi.AuthorizationStateWaitPassword) {
            status.put("message", "等待密码");
            status.put("nextStep", "submitPassword");
        } else if (authState instanceof TdApi.AuthorizationStateReady) {
            status.put("message", "✅ 已授权成功，正在监听消息");
            status.put("nextStep", "ready");
        } else {
            status.put("message", "状态: " + account.getStatus().name());
            status.put("nextStep", "unknown");
        }
        
        return status;
    }

    /**
     * 启用/禁用账号消息监听
     * 
     * @param accountId 账号ID
     * @param enabled 是否启用
     * @return 操作是否成功
     */
    public boolean setAccountListening(String accountId, boolean enabled) {
        TelegramAccount account = accounts.get(accountId);
        if (account == null) {
            return false;
        }
        
        account.setListeningEnabled(enabled);
        logger.info("账号 {} 消息监听已{}", accountId, enabled ? "启用" : "禁用");
        return true;
    }

    /**
     * 删除账号
     * 
     * @param accountId 账号ID
     * @return 删除是否成功
     */
    public boolean deleteAccount(String accountId) {
        TelegramAccount account = accounts.get(accountId);
        if (account == null) {
            return false;
        }
        
        try {
            // 关闭客户端
            if (account.getClient() != null) {
                account.getClient().close();
            }
            
            // 清理账号相关资源
            
            // 移除账号
            accounts.remove(accountId);
            
            logger.info("账号 {} 已删除", accountId);
            return true;
        } catch (Exception e) {
            logger.error("删除账号 {} 失败", accountId, e);
            return false;
        }
    }

    /**
     * 清空账号session数据
     * 
     * @param accountId 账号ID
     * @return 清空是否成功
     */
    public boolean clearAccountSession(String accountId) {
        TelegramAccount account = accounts.get(accountId);
        if (account == null) {
            logger.error("账号不存在: {}", accountId);
            return false;
        }
        
        try {
            // 关闭客户端连接
            if (account.getClient() != null) {
                account.getClient().close();
                account.setClient(null);
            }
            
            // 清理session目录
            Path sessionPath = Paths.get(account.getSessionPath());
            if (java.nio.file.Files.exists(sessionPath)) {
                // 删除session目录下的所有文件
                java.nio.file.Files.walk(sessionPath)
                    .sorted(java.util.Comparator.reverseOrder())
                    .map(Path::toFile)
                    .forEach(java.io.File::delete);
                
                logger.info("账号 {} session数据已清空", accountId);
            }
            
            // 重置账号状态
            account.setStatus(TelegramAccount.AccountStatus.CREATED);
            account.setAuthState(null);
            account.setListeningEnabled(false);
            
            return true;
        } catch (Exception e) {
            logger.error("清空账号 {} session失败", accountId, e);
            return false;
        }
    }

    /**
     * 清空所有session数据并重新配置
     * 
     * @return 操作结果
     */
    public Map<String, Object> clearAllSessionsAndReconfigure() {
        Map<String, Object> result = new HashMap<>();
        
        try {
            logger.info("开始清空所有session数据...");
            
            int clearedCount = 0;
            List<String> failedAccounts = new ArrayList<>();
            
            // 清空所有账号的session
            for (String accountId : new ArrayList<>(accounts.keySet())) {
                if (clearAccountSession(accountId)) {
                    clearedCount++;
                } else {
                    failedAccounts.add(accountId);
                }
            }
            
            // 清空账号列表
            accounts.clear();
            
            // 清空session基础目录
            Path baseSessionDir = Paths.get(sessionBasePath);
            if (java.nio.file.Files.exists(baseSessionDir)) {
                try {
                    java.nio.file.Files.walk(baseSessionDir)
                        .sorted(java.util.Comparator.reverseOrder())
                        .map(Path::toFile)
                        .forEach(file -> {
                            if (!file.equals(baseSessionDir.toFile())) {
                                file.delete();
                            }
                        });
                    logger.info("session基础目录已清空: {}", baseSessionDir);
                } catch (Exception e) {
                    logger.warn("清空session基础目录失败: {}", e.getMessage());
                }
            }
            
            result.put("success", true);
            result.put("message", "所有session数据已清空，系统已重置");
            result.put("clearedCount", clearedCount);
            result.put("failedAccounts", failedAccounts);
            
            logger.info("所有session数据清空完成，清空 {} 个账号", clearedCount);
            
        } catch (Exception e) {
            logger.error("清空所有session失败: {}", e.getMessage(), e);
            result.put("success", false);
            result.put("message", "清空session失败: " + e.getMessage());
        }
        
        return result;
    }
    
    /**
     * 配置代理设置
     */
    private void configureProxy(TDLibSettings settings) {
        if (proxyHost != null && !proxyHost.trim().isEmpty()) {
            try {
                TdApi.ProxyTypeSocks5 proxyType = new TdApi.ProxyTypeSocks5();
                proxyType.username = "";
                proxyType.password = "";
                
                TdApi.AddProxy addProxy = new TdApi.AddProxy();
                addProxy.server = proxyHost;
                addProxy.port = proxyPort;
                addProxy.enable = true;
                addProxy.type = proxyType;
                
                logger.info("配置SOCKS5代理: {}:{}", proxyHost, proxyPort);
            } catch (Exception e) {
                logger.warn("配置代理失败，将使用直连: {}", e.getMessage());
            }
        }
    }



    /**
     * 处理新消息
     */
    private void handleNewMessage(String accountId, TdApi.UpdateNewMessage update) {
        TelegramAccount account = accounts.get(accountId);
        if (account == null || !account.isListeningEnabled()) {
            return;
        }
        
        try {
            TdApi.Message message = update.message;
            String messageText = getMessageText(message.content);
            
            // 处理所有类型的消息，不仅仅是文本消息
            if (messageText != null) {
                // 创建消息JSON对象
                ObjectNode messageJson = objectMapper.createObjectNode();
                messageJson.put("accountId", accountId);
                messageJson.put("accountNickname", account.getNickname());
                messageJson.put("messageId", message.id);
                messageJson.put("chatId", message.chatId);
                messageJson.put("text", messageText);
                messageJson.put("timestamp", LocalDateTime.now().format(dateTimeFormatter));
                
                // 添加消息类型和详细信息
                addMessageTypeAndDetails(messageJson, message.content);
                
                // 添加发送者信息
                if (message.senderId instanceof TdApi.MessageSenderUser) {
                    TdApi.MessageSenderUser userSender = (TdApi.MessageSenderUser) message.senderId;
                    messageJson.put("senderId", userSender.userId);
                    messageJson.put("senderType", "user");
                } else if (message.senderId instanceof TdApi.MessageSenderChat) {
                    TdApi.MessageSenderChat chatSender = (TdApi.MessageSenderChat) message.senderId;
                    messageJson.put("senderId", chatSender.chatId);
                    messageJson.put("senderType", "chat");
                }
                
                logger.info("[账号:{}] 收到消息: {}", account.getNickname(), messageJson.toString());
                account.updateLastActiveTime();
            }
        } catch (Exception e) {
            logger.error("账号 {} 处理消息失败", accountId, e);
        }
    }

    /**
     * 处理授权状态变化
     */
    private void handleAuthorizationState(String accountId, TdApi.UpdateAuthorizationState update) {
        TelegramAccount account = accounts.get(accountId);
        if (account == null) {
            return;
        }
        
        account.setAuthState(update.authorizationState);
        
        if (update.authorizationState instanceof TdApi.AuthorizationStateReady) {
            account.setStatus(TelegramAccount.AccountStatus.READY);
            account.setAuthorizationInProgress(false);
            logger.info("账号 {} 授权完成，开始监听消息", accountId);
            
            // 初始化消息接收功能，确保能接收所有聊天的消息
            initializeMessageReceiving(accountId);
        } else if (update.authorizationState instanceof TdApi.AuthorizationStateWaitPhoneNumber) {
            logger.info("账号 {} 等待手机号", accountId);
            account.setStatus(TelegramAccount.AccountStatus.WAIT_PHONE_NUMBER);
            // 手机号现在直接在submitPhoneNumber方法中发送，不需要在这里处理
        } else if (update.authorizationState instanceof TdApi.AuthorizationStateWaitCode) {
            logger.info("账号 {} 等待验证码", accountId);
            // 授权流程继续进行中，不重置标志
        } else if (update.authorizationState instanceof TdApi.AuthorizationStateWaitPassword) {
            logger.info("账号 {} 等待密码", accountId);
            // 授权流程继续进行中，不重置标志
        } else if (update.authorizationState instanceof TdApi.AuthorizationStateClosed) {
            logger.info("账号 {} 连接已关闭", accountId);
            account.setAuthorizationInProgress(false);
            account.setStatus(TelegramAccount.AccountStatus.ERROR);
        } else if (update.authorizationState instanceof TdApi.AuthorizationStateClosing) {
            logger.info("账号 {} 连接正在关闭", accountId);
            account.setAuthorizationInProgress(false);
        } else {
            logger.warn("账号 {} 未处理的授权状态: {}", accountId, update.authorizationState.getClass().getSimpleName());
            // 对于未知状态，重置授权进行标志以避免卡死
            account.setAuthorizationInProgress(false);
        }
    }

    /**
     * 处理新聊天
     */
    private void handleNewChat(String accountId, TdApi.UpdateNewChat update) {
        // 可以在这里处理新聊天逻辑
    }

    /**
     * 处理聊天最后消息更新
     */
    private void handleChatLastMessage(String accountId, TdApi.UpdateChatLastMessage update) {
        // 可以在这里处理聊天最后消息更新逻辑
    }

    /**
     * 处理连接状态变化
     */
    private void handleConnectionState(String accountId, TdApi.UpdateConnectionState update) {
        TelegramAccount account = accounts.get(accountId);
        if (account != null) {
            logger.info("账号 {} 连接状态: {}", accountId, update.state.getClass().getSimpleName());
        }
    }

    /**
     * 初始化消息接收功能
     * 
     * 在客户端授权成功后调用，用于激活实时消息接收功能。
     * 通过获取聊天列表和设置相关选项来确保能够接收到所有新消息，
     * 包括那些在手机客户端中未"打开"的群组和聊天。
     * 
     * 执行的操作：
     * 1. 获取主聊天列表以激活消息接收
     * 2. 获取归档聊天列表
     * 3. 设置在线状态为true
     * 4. 启用消息数据库同步
     * 5. 启用文件数据库
     * 
     * 注意：此方法必须在授权完成后调用，否则可能无法正常接收消息。
     * 
     * @param accountId 账号ID
     */
    private void initializeMessageReceiving(String accountId) {
        TelegramAccount account = accounts.get(accountId);
        if (account == null || account.getClient() == null) {
            logger.error("账号 {} 不存在或客户端未初始化", accountId);
            return;
        }
        
        SimpleTelegramClient client = account.getClient();
        
        try {
            // 获取主聊天列表以激活消息接收
            TdApi.GetChats getMainChats = new TdApi.GetChats(new TdApi.ChatListMain(), 100);
            client.send(getMainChats, result -> {
                if (result.isError()) {
                    logger.error("账号 {} 获取主聊天列表失败: {}", accountId, result.getError().message);
                } else {
                    logger.info("账号 {} 主聊天列表获取成功", accountId);
                }
            });
            
            // 获取归档聊天列表
            TdApi.GetChats getArchivedChats = new TdApi.GetChats(new TdApi.ChatListArchive(), 100);
            client.send(getArchivedChats, result -> {
                if (result.isError()) {
                    logger.warn("账号 {} 获取归档聊天列表失败: {}", accountId, result.getError().message);
                } else {
                    logger.info("账号 {} 归档聊天列表获取成功", accountId);
                }
            });
            
            // 设置在线状态
            client.send(new TdApi.SetOption("online", new TdApi.OptionValueBoolean(true)), result -> {
                if (result.isError()) {
                    logger.warn("账号 {} 设置在线状态失败: {}", accountId, result.getError().message);
                } else {
                    logger.debug("账号 {} 在线状态设置成功", accountId);
                }
            });
            
            // 启用消息数据库同步
            client.send(new TdApi.SetOption("use_message_database", new TdApi.OptionValueBoolean(true)), result -> {
                if (result.isError()) {
                    logger.warn("账号 {} 启用消息数据库失败: {}", accountId, result.getError().message);
                } else {
                    logger.debug("账号 {} 消息数据库启用成功", accountId);
                }
            });
            
            // 启用文件数据库
            client.send(new TdApi.SetOption("use_file_database", new TdApi.OptionValueBoolean(true)), result -> {
                if (result.isError()) {
                    logger.warn("账号 {} 启用文件数据库失败: {}", accountId, result.getError().message);
                } else {
                    logger.debug("账号 {} 文件数据库启用成功", accountId);
                }
            });
            
            // 启用聊天信息数据库
            client.send(new TdApi.SetOption("use_chat_info_database", new TdApi.OptionValueBoolean(true)), result -> {
                if (result.isError()) {
                    logger.warn("账号 {} 启用聊天信息数据库失败: {}", accountId, result.getError().message);
                } else {
                    logger.debug("账号 {} 聊天信息数据库启用成功", accountId);
                }
            });
            
            logger.info("账号 {} 消息接收初始化完成，现在应该能接收所有聊天的消息", accountId);
            
        } catch (Exception e) {
            logger.error("账号 {} 初始化消息接收失败", accountId, e);
        }
    }

    /**
     * 添加消息类型和详细信息
     * 
     * 为消息JSON对象添加消息类型、文件信息等详细数据，
     * 特别针对图片、视频、文档等媒体消息提供完整的元数据。
     * 
     * @param messageJson 消息JSON对象
     * @param content 消息内容
     */
    private void addMessageTypeAndDetails(ObjectNode messageJson, TdApi.MessageContent content) {
        if (content instanceof TdApi.MessageText) {
            messageJson.put("messageType", "text");
        } else if (content instanceof TdApi.MessagePhoto) {
            TdApi.MessagePhoto photoMessage = (TdApi.MessagePhoto) content;
            messageJson.put("messageType", "photo");
            
            // 添加图片详细信息
            ObjectNode photoInfo = objectMapper.createObjectNode();
            if (photoMessage.caption != null && !photoMessage.caption.text.isEmpty()) {
                photoInfo.put("caption", photoMessage.caption.text);
            }
            
            // 获取最大尺寸的图片信息
            if (photoMessage.photo.sizes.length > 0) {
                TdApi.PhotoSize largestPhoto = photoMessage.photo.sizes[photoMessage.photo.sizes.length - 1];
                photoInfo.put("width", largestPhoto.width);
                photoInfo.put("height", largestPhoto.height);
                photoInfo.put("fileId", largestPhoto.photo.id);
                photoInfo.put("fileSize", largestPhoto.photo.size);
                
                // 如果有本地路径，添加本地路径信息
                if (largestPhoto.photo.local != null && largestPhoto.photo.local.path != null && !largestPhoto.photo.local.path.isEmpty()) {
                    photoInfo.put("localPath", largestPhoto.photo.local.path);
                }
                
                // 如果有远程路径，添加远程路径信息
                if (largestPhoto.photo.remote != null && largestPhoto.photo.remote.id != null && !largestPhoto.photo.remote.id.isEmpty()) {
                    photoInfo.put("remoteId", largestPhoto.photo.remote.id);
                }
            }
            
            messageJson.set("photoInfo", photoInfo);
        } else if (content instanceof TdApi.MessageVideo) {
            TdApi.MessageVideo videoMessage = (TdApi.MessageVideo) content;
            messageJson.put("messageType", "video");
            
            ObjectNode videoInfo = objectMapper.createObjectNode();
            if (videoMessage.caption != null && !videoMessage.caption.text.isEmpty()) {
                videoInfo.put("caption", videoMessage.caption.text);
            }
            videoInfo.put("duration", videoMessage.video.duration);
            videoInfo.put("width", videoMessage.video.width);
            videoInfo.put("height", videoMessage.video.height);
            videoInfo.put("fileId", videoMessage.video.video.id);
            videoInfo.put("fileSize", videoMessage.video.video.size);
            
            messageJson.set("videoInfo", videoInfo);
        } else if (content instanceof TdApi.MessageDocument) {
            TdApi.MessageDocument docMessage = (TdApi.MessageDocument) content;
            messageJson.put("messageType", "document");
            
            ObjectNode docInfo = objectMapper.createObjectNode();
            if (docMessage.caption != null && !docMessage.caption.text.isEmpty()) {
                docInfo.put("caption", docMessage.caption.text);
            }
            docInfo.put("fileName", docMessage.document.fileName);
            docInfo.put("mimeType", docMessage.document.mimeType);
            docInfo.put("fileId", docMessage.document.document.id);
            docInfo.put("fileSize", docMessage.document.document.size);
            
            messageJson.set("documentInfo", docInfo);
        } else if (content instanceof TdApi.MessageSticker) {
            TdApi.MessageSticker stickerMessage = (TdApi.MessageSticker) content;
            messageJson.put("messageType", "sticker");
            
            ObjectNode stickerInfo = objectMapper.createObjectNode();
            stickerInfo.put("emoji", stickerMessage.sticker.emoji);
            stickerInfo.put("width", stickerMessage.sticker.width);
            stickerInfo.put("height", stickerMessage.sticker.height);
            stickerInfo.put("fileId", stickerMessage.sticker.sticker.id);
            
            messageJson.set("stickerInfo", stickerInfo);
        } else if (content instanceof TdApi.MessageAnimation) {
            TdApi.MessageAnimation animMessage = (TdApi.MessageAnimation) content;
            messageJson.put("messageType", "animation");
            
            ObjectNode animInfo = objectMapper.createObjectNode();
            if (animMessage.caption != null && !animMessage.caption.text.isEmpty()) {
                animInfo.put("caption", animMessage.caption.text);
            }
            animInfo.put("duration", animMessage.animation.duration);
            animInfo.put("width", animMessage.animation.width);
            animInfo.put("height", animMessage.animation.height);
            animInfo.put("fileId", animMessage.animation.animation.id);
            animInfo.put("fileSize", animMessage.animation.animation.size);
            
            messageJson.set("animationInfo", animInfo);
        } else if (content instanceof TdApi.MessageVoiceNote) {
            TdApi.MessageVoiceNote voiceMessage = (TdApi.MessageVoiceNote) content;
            messageJson.put("messageType", "voice");
            
            ObjectNode voiceInfo = objectMapper.createObjectNode();
            if (voiceMessage.caption != null && !voiceMessage.caption.text.isEmpty()) {
                voiceInfo.put("caption", voiceMessage.caption.text);
            }
            voiceInfo.put("duration", voiceMessage.voiceNote.duration);
            voiceInfo.put("fileId", voiceMessage.voiceNote.voice.id);
            voiceInfo.put("fileSize", voiceMessage.voiceNote.voice.size);
            
            messageJson.set("voiceInfo", voiceInfo);
        } else if (content instanceof TdApi.MessageAudio) {
            TdApi.MessageAudio audioMessage = (TdApi.MessageAudio) content;
            messageJson.put("messageType", "audio");
            
            ObjectNode audioInfo = objectMapper.createObjectNode();
            if (audioMessage.caption != null && !audioMessage.caption.text.isEmpty()) {
                audioInfo.put("caption", audioMessage.caption.text);
            }
            audioInfo.put("duration", audioMessage.audio.duration);
            audioInfo.put("title", audioMessage.audio.title);
            audioInfo.put("performer", audioMessage.audio.performer);
            audioInfo.put("fileName", audioMessage.audio.fileName);
            audioInfo.put("mimeType", audioMessage.audio.mimeType);
            audioInfo.put("fileId", audioMessage.audio.audio.id);
            audioInfo.put("fileSize", audioMessage.audio.audio.size);
            
            messageJson.set("audioInfo", audioInfo);
        } else {
            messageJson.put("messageType", "unknown");
        }
    }

    /**
     * 获取消息文本内容
     */
    private String getMessageText(TdApi.MessageContent content) {
        if (content instanceof TdApi.MessageText) {
            TdApi.MessageText textMessage = (TdApi.MessageText) content;
            return textMessage.text.text;
        } else if (content instanceof TdApi.MessagePhoto) {
            TdApi.MessagePhoto photoMessage = (TdApi.MessagePhoto) content;
            return "[图片] " + (photoMessage.caption != null ? photoMessage.caption.text : "");
        } else if (content instanceof TdApi.MessageVideo) {
            TdApi.MessageVideo videoMessage = (TdApi.MessageVideo) content;
            return "[视频] " + (videoMessage.caption != null ? videoMessage.caption.text : "");
        } else if (content instanceof TdApi.MessageDocument) {
            TdApi.MessageDocument docMessage = (TdApi.MessageDocument) content;
            return "[文档] " + (docMessage.caption != null ? docMessage.caption.text : "");
        } else if (content instanceof TdApi.MessageSticker) {
            return "[贴纸]";
        } else if (content instanceof TdApi.MessageAnimation) {
            TdApi.MessageAnimation animMessage = (TdApi.MessageAnimation) content;
            return "[动图] " + (animMessage.caption != null ? animMessage.caption.text : "");
        } else if (content instanceof TdApi.MessageVoiceNote) {
            TdApi.MessageVoiceNote voiceMessage = (TdApi.MessageVoiceNote) content;
            return "[语音] " + (voiceMessage.caption != null ? voiceMessage.caption.text : "");
        } else if (content instanceof TdApi.MessageAudio) {
            TdApi.MessageAudio audioMessage = (TdApi.MessageAudio) content;
            return "[音频] " + (audioMessage.caption != null ? audioMessage.caption.text : "");
        }
        return null;
    }

    /**
     * 自动恢复已存在的会话
     * 扫描session目录，恢复已有的Telegram账号并自动开始监听
     */
    private void autoRecoverExistingSessions() {
        try {
            logger.info("开始自动恢复现有sessions...");
            
            // 检查session基础路径
            Path baseSessionDir = Paths.get(sessionBasePath);
            if (!java.nio.file.Files.exists(baseSessionDir)) {
                logger.info("session基础目录不存在: {}", baseSessionDir.toAbsolutePath());
                return;
            }
            
            // 扫描所有子目录，每个子目录代表一个账号的session
            java.nio.file.Files.list(baseSessionDir)
                .filter(java.nio.file.Files::isDirectory)
                .forEach(accountSessionDir -> {
                    try {
                        recoverAccountSession(accountSessionDir);
                    } catch (Exception e) {
                        logger.error("恢复账号session失败: {}", accountSessionDir, e);
                    }
                });
            
            // 同时检查旧的单一session目录（向后兼容）
            checkLegacySessionDirectories();
            
            if (accounts.isEmpty()) {
                logger.info("未发现可恢复的session，系统将以全新状态启动");
            } else {
                logger.info("成功恢复 {} 个账号", accounts.size());
            }
            
        } catch (Exception e) {
            logger.error("自动恢复session时发生错误: {}", e.getMessage(), e);
        }
    }
    
    /**
     * 恢复单个账号的session
     */
    private void recoverAccountSession(Path accountSessionDir) {
        String sessionPath = accountSessionDir.toString();
        String accountId = accountSessionDir.getFileName().toString();
        
        // 检查是否有有效的session数据
        Path databaseDir = accountSessionDir.resolve("database");
        if (!java.nio.file.Files.exists(databaseDir)) {
            logger.debug("跳过无效的session目录: {}", sessionPath);
            return;
        }
        
        logger.info("发现有效的session目录: {}", sessionPath);
        
        // 尝试加载保存的API凭据
        Map<String, Object> credentials = credentialsManager.loadCredentials(sessionPath);
        
        // 创建账号对象
        String nickname = "恢复的账号-" + accountId.substring(0, Math.min(8, accountId.length()));
        TelegramAccount account = new TelegramAccount(accountId, nickname);
        account.setSessionPath(sessionPath);
        
        if (credentials != null) {
            // 成功加载API凭据，设置为已配置状态
            Integer apiId = (Integer) credentials.get("apiId");
            String apiHash = (String) credentials.get("apiHash");
            String phoneNumber = (String) credentials.get("phoneNumber");
            
            account.setApiId(apiId);
            account.setApiHash(apiHash);
            if (phoneNumber != null) {
                account.setPhoneNumber(phoneNumber);
            }
            account.setStatus(TelegramAccount.AccountStatus.READY);
            
            logger.info("账号 {} 已恢复，API凭据已加载", accountId);
            
            // 尝试自动初始化客户端
            try {
                if (initializeAccountClient(accountId)) {
                    logger.info("账号 {} 客户端初始化成功，session已完全恢复", accountId);
                } else {
                    logger.warn("账号 {} 客户端初始化失败，需要重新认证", accountId);
                }
            } catch (Exception e) {
                logger.warn("账号 {} 自动初始化失败: {}", accountId, e.getMessage());
            }
            
        } else {
            // 未找到API凭据，需要重新配置
            account.setStatus(TelegramAccount.AccountStatus.CREATED);
            logger.info("账号 {} 已恢复，但需要重新配置API信息", accountId);
        }
        
        // 添加到账号列表
        accounts.put(accountId, account);
    }
    
    /**
     * 检查旧的单一session目录（向后兼容）
     */
    public void checkLegacySessionDirectories() {
        checkLegacySessionDirectories("旧版恢复账号");
    }
    
    public void checkLegacySessionDirectories(String nickname) {
        String[] legacyPaths = {
            "./telegram-session",
            "./telegram-sessions"
        };
        
        for (String legacyPath : legacyPaths) {
            Path legacyDir = Paths.get(legacyPath);
            if (java.nio.file.Files.exists(legacyDir) && java.nio.file.Files.isDirectory(legacyDir)) {
                Path databaseDir = legacyDir.resolve("database");
                if (java.nio.file.Files.exists(databaseDir)) {
                    logger.info("发现旧版session目录: {}", legacyPath);
                    
                    // 为旧session创建一个账号
                    String accountId = "legacy-" + System.currentTimeMillis();
                    TelegramAccount account = new TelegramAccount(accountId, nickname);
                    account.setSessionPath(legacyPath);
                    
                    // 尝试加载凭据
                    Map<String, Object> credentials = credentialsManager.loadCredentials(legacyPath);
                    if (credentials != null) {
                        Integer apiId = (Integer) credentials.get("apiId");
                        String apiHash = (String) credentials.get("apiHash");
                        String phoneNumber = (String) credentials.get("phoneNumber");
                        
                        account.setApiId(apiId);
                        account.setApiHash(apiHash);
                        if (phoneNumber != null) {
                            account.setPhoneNumber(phoneNumber);
                        }
                        account.setStatus(TelegramAccount.AccountStatus.READY);
                        
                        logger.info("旧版账号 {} 已恢复，API凭据已加载", accountId);
                    } else {
                        account.setStatus(TelegramAccount.AccountStatus.CREATED);
                        logger.info("旧版账号 {} 已恢复，但需要重新配置API信息", accountId);
                    }
                    
                    accounts.put(accountId, account);
                    break; // 只处理第一个找到的旧版目录
                }
            }
        }
    }

    /**
     * 获取指定账号
     */
    public Map<String, Object> getAccount(String accountId) {
        TelegramAccount account = accounts.get(accountId);
        if (account == null) {
            return null;
        }
        
        Map<String, Object> result = new HashMap<>();
        result.put("accountId", account.getAccountId());
        result.put("nickname", account.getNickname());
        result.put("phoneNumber", account.getPhoneNumber());
        result.put("status", account.getStatus().toString());
        result.put("apiId", account.getApiId());
        result.put("apiHash", account.getApiHash());
        result.put("listeningEnabled", account.isListeningEnabled());
        result.put("createdAt", account.getCreateTime());
        
        return result;
    }
    
    /**
     * 配置账号API信息
     */
    public boolean configAccount(String accountId, Integer apiId, String apiHash, String phoneNumber) {
        TelegramAccount account = accounts.get(accountId);
        if (account == null) {
            logger.error("账号不存在: {}", accountId);
            return false;
        }
        
        try {
            account.setApiId(apiId);
            account.setApiHash(apiHash);
            account.setPhoneNumber(phoneNumber);
            account.setStatus(TelegramAccount.AccountStatus.CONFIGURING);
            account.updateLastActiveTime();
            
            logger.info("账号 {} API配置成功", accountId);
            return true;
            
        } catch (Exception e) {
            logger.error("配置账号 {} API信息失败", accountId, e);
            return false;
        }
    }
    
    /**
     * 获取账号认证状态
     */
    public Map<String, Object> getAccountAuthStatus(String accountId) {
        Map<String, Object> status = new HashMap<>();
        
        TelegramAccount account = accounts.get(accountId);
        if (account == null) {
            status.put("success", false);
            status.put("message", "账号不存在");
            return status;
        }
        
        status.put("success", true);
        status.put("accountId", account.getAccountId());
        status.put("nickname", account.getNickname());
        status.put("phoneNumber", account.getPhoneNumber());
        status.put("status", account.getStatus().toString());
        status.put("authState", account.getAuthState() != null ? account.getAuthState().getClass().getSimpleName() : "未知");
        status.put("isAuthenticated", account.isAuthenticated());
        status.put("isApiConfigured", account.isApiConfigured());
        status.put("listeningEnabled", account.isListeningEnabled());
        status.put("createTime", account.getCreateTime().toString());
        status.put("lastActiveTime", account.getLastActiveTime().toString());
        
        return status;
    }
    
    /**
     * 设置监听状态
     */
    public Map<String, Object> setListeningEnabled(String accountId, Boolean enabled) {
        Map<String, Object> result = new HashMap<>();
        TelegramAccount account = accounts.get(accountId);
        if (account == null) {
            logger.error("账号不存在: {}", accountId);
            result.put("success", false);
            result.put("message", "账号不存在");
            return result;
        }
        
        try {
            account.setListeningEnabled(enabled);
            account.updateLastActiveTime();
            
            if (enabled && account.isAuthenticated()) {
                account.setStatus(TelegramAccount.AccountStatus.LISTENING);
            } else if (!enabled) {
                account.setStatus(TelegramAccount.AccountStatus.READY);
            }
            
            logger.info("账号 {} 监听状态设置为: {}", accountId, enabled);
            result.put("success", true);
            result.put("message", "监听状态设置成功");
            return result;
            
        } catch (Exception e) {
            logger.error("设置账号 {} 监听状态失败", accountId, e);
            result.put("success", false);
            result.put("message", "设置监听状态失败: " + e.getMessage());
            return result;
        }
    }
    
    /**
     * 获取服务状态
     */
    public Map<String, Object> getServiceStatus() {
        Map<String, Object> status = new HashMap<>();
        
        status.put("totalAccounts", accounts.size());
        status.put("activeAccounts", accounts.values().stream()
                .mapToLong(account -> account.getStatus() == TelegramAccount.AccountStatus.LISTENING ? 1 : 0)
                .sum());
        status.put("authenticatedAccounts", accounts.values().stream()
                .mapToLong(account -> account.isAuthenticated() ? 1 : 0)
                .sum());
        status.put("serviceRunning", true);
        status.put("timestamp", java.time.LocalDateTime.now().toString());
        
        return status;
    }
    
    /**
     * 开启所有账号监听
     */
    public Map<String, Object> enableAllListening() {
        Map<String, Object> result = new HashMap<>();
        int totalAccounts = accounts.size();
        int enabledCount = 0;
        int alreadyEnabledCount = 0;
        int errorCount = 0;
        
        if (totalAccounts == 0) {
            result.put("success", false);
            result.put("message", "没有可用的账号");
            return result;
        }
        
        for (TelegramAccount account : accounts.values()) {
            try {
                if (account.isAuthenticated()) {
                    if (!account.isListeningEnabled()) {
                        account.setListeningEnabled(true);
                        account.setStatus(TelegramAccount.AccountStatus.LISTENING);
                        account.updateLastActiveTime();
                        enabledCount++;
                        logger.info("账号 {} ({}) 监听已开启", account.getAccountId(), account.getNickname());
                    } else {
                        alreadyEnabledCount++;
                    }
                } else {
                    logger.warn("账号 {} ({}) 未认证，跳过开启监听", account.getAccountId(), account.getNickname());
                    errorCount++;
                }
            } catch (Exception e) {
                logger.error("开启账号 {} 监听失败", account.getAccountId(), e);
                errorCount++;
            }
        }
        
        StringBuilder message = new StringBuilder();
        message.append(String.format("总计 %d 个账号，", totalAccounts));
        if (enabledCount > 0) {
            message.append(String.format("成功开启 %d 个，", enabledCount));
        }
        if (alreadyEnabledCount > 0) {
            message.append(String.format("已开启 %d 个，", alreadyEnabledCount));
        }
        if (errorCount > 0) {
            message.append(String.format("失败 %d 个", errorCount));
        } else {
            message.append("全部处理完成");
        }
        
        result.put("success", true);
        result.put("message", message.toString());
        result.put("totalAccounts", totalAccounts);
        result.put("enabledCount", enabledCount);
        result.put("alreadyEnabledCount", alreadyEnabledCount);
        result.put("errorCount", errorCount);
        
        return result;
    }
    
    /**
     * 服务关闭时的清理工作
     */
    @PreDestroy
    public void shutdown() {
        logger.info("正在关闭多账号Telegram服务...");
        
        for (TelegramAccount account : accounts.values()) {
            try {
                if (account.getClient() != null) {
                    account.getClient().close();
                }
            } catch (Exception e) {
                logger.error("关闭账号 {} 客户端失败", account.getAccountId(), e);
            }
        }
        
        accounts.clear();
        // 清理所有资源
        
        logger.info("多账号Telegram服务已关闭");
    }
}