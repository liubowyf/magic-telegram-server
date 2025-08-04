package com.telegram.server.entity;

import it.tdlight.client.SimpleTelegramClient;
import it.tdlight.jni.TdApi;

import java.time.LocalDateTime;

/**
 * Telegram账号实体类
 * 
 * 用于管理多个Telegram账号的信息和状态，包含账号的基本信息、
 * 认证状态、客户端连接、监听配置等完整的账号生命周期管理。
 * 
 * 主要功能：
 * 1. 存储账号基本信息（ID、昵称、手机号等）
 * 2. 管理API配置（API ID、API Hash）
 * 3. 跟踪认证状态和授权进度
 * 4. 维护客户端连接实例
 * 5. 控制消息监听开关
 * 6. 记录账号活跃时间
 * 
 * @author liubo
 * @version 1.0.0
 * @since 2024-12-19
 */
public class TelegramAccount {
    
    /**
     * 账号唯一标识
     * 使用UUID生成，用于在系统中唯一标识一个Telegram账号
     */
    private String accountId;
    
    /**
     * Telegram API ID
     * 从Telegram官网申请获得的应用程序标识符
     */
    private Integer apiId;
    
    /**
     * Telegram API Hash
     * 从Telegram官网申请获得的应用程序密钥
     */
    private String apiHash;
    
    /**
     * 手机号码
     * 用于Telegram账号认证的手机号，需包含国家代码（如+86）
     */
    private String phoneNumber;
    
    /**
     * 账号昵称
     * 用户自定义的账号别名，便于在多账号管理中识别
     */
    private String nickname;
    
    /**
     * Session存储路径
     * 该账号的TDLib session文件存储目录的绝对路径
     */
    private String sessionPath;
    
    /**
     * Telegram客户端实例
     * TDLight库的客户端对象，用于与Telegram服务器通信
     */
    private SimpleTelegramClient client;
    
    /**
     * 当前授权状态
     * TDLib返回的授权状态，表示账号当前的认证进度
     */
    private TdApi.AuthorizationState authState;
    
    /**
     * 账号业务状态
     * 自定义的账号状态枚举，用于业务逻辑判断
     */
    private AccountStatus status;
    
    /**
     * 账号创建时间
     * 记录账号在系统中的创建时间
     */
    private LocalDateTime createTime;
    
    /**
     * 最后活跃时间
     * 记录账号最后一次状态更新或操作的时间
     */
    private LocalDateTime lastActiveTime;
    
    /**
     * 消息监听开关
     * 控制是否对该账号启用消息监听功能
     */
    private boolean listeningEnabled;
    
    /**
     * 授权流程进行标志
     * 标识该账号是否正在进行认证授权流程，防止重复操作
     */
    private boolean authorizationInProgress;
    
    /**
     * 账号状态枚举
     * 
     * 定义Telegram账号在系统中的各种业务状态，用于控制账号的生命周期
     * 和操作权限。状态转换遵循特定的业务流程。
     */
    public enum AccountStatus {
        /** 已创建 - 账号刚创建，尚未配置API信息 */
        CREATED,
        
        /** 配置中 - 正在配置API ID和API Hash */
        CONFIGURING,
        
        /** 认证中 - 正在进行Telegram账号认证流程 */
        AUTHENTICATING,
        
        /** 等待手机号 - 等待用户输入手机号码 */
        WAIT_PHONE_NUMBER,
        
        /** 就绪 - 账号已认证完成，可以使用 */
        READY,
        
        /** 监听中 - 账号正在监听消息 */
        LISTENING,
        
        /** 错误 - 账号出现错误，需要处理 */
        ERROR,
        
        /** 已禁用 - 账号被禁用，不参与任何操作 */
        DISABLED
    }
    
    /**
     * 默认构造函数
     * 
     * 创建一个新的Telegram账号实例，初始化基本属性：
     * - 设置创建时间和最后活跃时间为当前时间
     * - 设置账号状态为CREATED
     * - 默认启用消息监听
     */
    public TelegramAccount() {
        this.createTime = LocalDateTime.now();
        this.lastActiveTime = LocalDateTime.now();
        this.status = AccountStatus.CREATED;
        this.listeningEnabled = true;
    }
    
    /**
     * 带参数的构造函数
     * 
     * 创建一个指定ID和昵称的Telegram账号实例。
     * 会调用默认构造函数进行基本初始化。
     * 
     * @param accountId 账号唯一标识符
     * @param nickname 账号昵称
     */
    public TelegramAccount(String accountId, String nickname) {
        this();
        this.accountId = accountId;
        this.nickname = nickname;
    }
    
    // Getter和Setter方法
    public String getAccountId() {
        return accountId;
    }
    
    public void setAccountId(String accountId) {
        this.accountId = accountId;
    }
    
    public Integer getApiId() {
        return apiId;
    }
    
    public void setApiId(Integer apiId) {
        this.apiId = apiId;
    }
    
    public String getApiHash() {
        return apiHash;
    }
    
    public void setApiHash(String apiHash) {
        this.apiHash = apiHash;
    }
    
    public String getPhoneNumber() {
        return phoneNumber;
    }
    
    public void setPhoneNumber(String phoneNumber) {
        this.phoneNumber = phoneNumber;
    }
    
    public String getNickname() {
        return nickname;
    }
    
    public void setNickname(String nickname) {
        this.nickname = nickname;
    }
    
    public String getSessionPath() {
        return sessionPath;
    }
    
    public void setSessionPath(String sessionPath) {
        this.sessionPath = sessionPath;
    }
    
    public SimpleTelegramClient getClient() {
        return client;
    }
    
    public void setClient(SimpleTelegramClient client) {
        this.client = client;
    }
    
    public TdApi.AuthorizationState getAuthState() {
        return authState;
    }
    
    public void setAuthState(TdApi.AuthorizationState authState) {
        this.authState = authState;
        this.lastActiveTime = LocalDateTime.now();
    }
    
    public AccountStatus getStatus() {
        return status;
    }
    
    public void setStatus(AccountStatus status) {
        this.status = status;
        this.lastActiveTime = LocalDateTime.now();
    }
    
    public LocalDateTime getCreateTime() {
        return createTime;
    }
    
    public void setCreateTime(LocalDateTime createTime) {
        this.createTime = createTime;
    }
    
    public LocalDateTime getLastActiveTime() {
        return lastActiveTime;
    }
    
    public void setLastActiveTime(LocalDateTime lastActiveTime) {
        this.lastActiveTime = lastActiveTime;
    }
    
    public boolean isListeningEnabled() {
        return listeningEnabled;
    }
    
    public void setListeningEnabled(boolean listeningEnabled) {
        this.listeningEnabled = listeningEnabled;
        this.updateLastActiveTime();
    }
    

    
    public boolean isAuthorizationInProgress() {
        return authorizationInProgress;
    }
    
    public void setAuthorizationInProgress(boolean authorizationInProgress) {
        this.authorizationInProgress = authorizationInProgress;
    }
    
    /**
     * 检查账号是否已配置API信息
     */
    public boolean isApiConfigured() {
        return apiId != null && apiHash != null && !apiHash.trim().isEmpty();
    }
    
    /**
     * 检查账号是否已认证
     */
    public boolean isAuthenticated() {
        return authState instanceof TdApi.AuthorizationStateReady;
    }
    
    /**
     * 更新最后活跃时间
     */
    public void updateLastActiveTime() {
        this.lastActiveTime = LocalDateTime.now();
    }
    
    @Override
    public String toString() {
        return "TelegramAccount{" +
                "accountId='" + accountId + '\'' +
                ", nickname='" + nickname + '\'' +
                ", phoneNumber='" + phoneNumber + '\'' +
                ", status=" + status +
                ", listeningEnabled=" + listeningEnabled +
                '}';
    }
}