package com.telegram.server.service;

import java.util.Map;

/**
 * Telegram服务接口
 * 
 * 定义Telegram客户端的核心功能接口，包括：
 * - API配置和认证管理
 * - 消息监听和处理
 * - 会话状态管理
 * - 账号生命周期管理
 * 
 * @author liubo
 * @date 2025-01-20
 */
public interface ITelegramService {
    
    /**
     * 初始化服务
     * 
     * 在Spring容器启动后自动调用，进行必要的初始化工作，
     * 包括TDLight库初始化、日志配置等。
     */
    void init();
    
    /**
     * 配置API信息
     * 
     * @param appId Telegram API ID
     * @param appHash Telegram API Hash
     * @return 配置是否成功
     */
    boolean configApi(int appId, String appHash);
    
    /**
     * 提交手机号进行认证
     * 
     * @param phoneNumber 手机号码
     * @return 提交是否成功
     */
    boolean submitPhoneNumber(String phoneNumber);
    
    /**
     * 开始监听消息
     * 
     * 启动Telegram客户端并开始监听新消息
     */
    void startListening();
    
    /**
     * 提交短信验证码
     * 
     * @param code 短信验证码
     * @return 包含认证结果的Map对象
     */
    Map<String, Object> submitAuthCode(String code);
    
    /**
     * 提交两步验证密码
     * 
     * @param password 两步验证密码
     * @return 密码验证是否成功
     */
    boolean submitPassword(String password);
    
    /**
     * 获取服务状态
     * 
     * @return 当前服务状态描述
     */
    String getStatus();
    
    /**
     * 获取详细的授权状态信息
     * 
     * @return 包含详细授权状态信息的Map对象
     */
    Map<String, Object> getAuthStatus();
    
    /**
     * 初始化账号
     * 
     * 创建并初始化单个Telegram账号实例
     */
    void initializeAccount();
    
    /**
     * 停止消息监听
     * 
     * 停止Telegram消息监听功能，但保持客户端连接
     */
    void stopListening();
    
    /**
     * 清理Session数据
     * 
     * 清除当前账号的所有Session数据，包括认证信息和缓存数据
     */
    void clearSession();
    
    /**
     * 检查MongoDB中session数据的完整性
     * 
     * @return 包含检查结果的详细信息Map对象
     */
    Map<String, Object> checkSessionDataIntegrity();
    
    /**
     * 关闭服务
     * 
     * 关闭Telegram服务并释放资源
     */
    void shutdown();
}