package com.telegram.server.service;

import com.telegram.server.entity.TelegramSession;

import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Telegram Session管理服务接口
 * 
 * 定义了Telegram session数据管理的核心功能，支持集群部署。
 * 提供session的创建、更新、查询、删除等操作，以及文件数据的序列化和反序列化。
 * 
 * 主要功能：
 * - Session CRUD操作
 * - 文件数据与MongoDB的转换
 * - 集群环境下的session分配
 * - Session生命周期管理
 * - 数据迁移支持
 * 
 * 集群支持：
 * - 多实例session共享
 * - 负载均衡分配
 * - 实例故障恢复
 * - Session锁定机制
 * 
 * @author liubo
 * @date 2025-01-20
 */
public interface ITelegramSessionService {

    /**
     * 初始化服务
     */
    void init();

    /**
     * 创建或更新session
     * 
     * @param phoneNumber 手机号码
     * @param apiId API ID
     * @param apiHash API Hash
     * @return session对象
     */
    TelegramSession createOrUpdateSession(String phoneNumber, Integer apiId, String apiHash);

    /**
     * 根据手机号获取session
     * 
     * @param phoneNumber 手机号码
     * @return session对象
     */
    Optional<TelegramSession> getSessionByPhoneNumber(String phoneNumber);

    /**
     * 根据手机号查找session（别名方法）
     * 
     * @param phoneNumber 手机号码
     * @return session对象
     */
    Optional<TelegramSession> findByPhoneNumber(String phoneNumber);

    /**
     * 激活session
     * 
     * @param phoneNumber 手机号码
     * @return 是否成功激活
     */
    boolean activateSession(String phoneNumber);

    /**
     * 停用session
     * 
     * @param phoneNumber 手机号码
     * @return 是否成功停用
     */
    boolean deactivateSession(String phoneNumber);

    /**
     * 更新session的认证状态
     * 
     * @param phoneNumber 手机号码
     * @param authState 认证状态
     */
    void updateAuthState(String phoneNumber, String authState);

    /**
     * 更新session的认证状态
     * 
     * @param phoneNumber 手机号
     * @param isAuthenticated 是否已认证
     */
    void updateAuthenticationStatus(String phoneNumber, boolean isAuthenticated);

    /**
     * 保存session文件数据到MongoDB
     * 使用分片存储管理器处理大文件存储
     * 
     * @param phoneNumber 手机号码
     * @param sessionPath session文件路径
     */
    void saveSessionFiles(String phoneNumber, String sessionPath);

    /**
     * 从MongoDB恢复session文件到本地
     * 
     * @param phoneNumber 手机号码
     * @param sessionPath session文件路径
     * @return 是否成功恢复
     */
    boolean restoreSessionFiles(String phoneNumber, String sessionPath);

    /**
     * 删除session
     * 
     * @param phoneNumber 手机号码
     * @return 是否成功删除
     */
    boolean deleteSession(String phoneNumber);

    /**
     * 获取可用的session（已认证且非活跃）
     * 
     * @return session列表
     */
    List<TelegramSession> getAvailableSessions();



    /**
     * 清理过期的session
     * 
     * @return 清理的session数量
     */
    int cleanupExpiredSessions();

    /**
     * 获取实例ID
     * 
     * @return 实例ID
     */
    String getInstanceId();

    /**
     * 获取session统计信息
     * 
     * @return 统计信息
     */
    Map<String, Object> getSessionStats();

    /**
     * 获取所有session数据
     * 
     * 用于数据完整性检查和系统诊断，返回数据库中的所有session记录。
     * 
     * @return 所有session的列表
     */
    List<TelegramSession> getAllSessions();

    // ==================== Web管理系统API方法 ====================
    
    /**
     * 分页获取账号列表
     * 
     * @param pageRequest 分页请求参数
     * @return 分页响应结果
     */
    com.telegram.server.dto.PageResponseDTO<com.telegram.server.dto.AccountDTO> getAccountsPage(com.telegram.server.dto.PageRequestDTO pageRequest);
    
    /**
     * 根据ID获取账号详情
     * 
     * @param accountId 账号ID（手机号）
     * @return 账号详情
     */
    Optional<com.telegram.server.dto.AccountDTO> getAccountById(String accountId);
    
    /**
     * 删除账号
     * 
     * @param accountId 账号ID（手机号）
     * @return 是否删除成功
     */
    boolean deleteAccount(String accountId);
    
    /**
     * 获取账号总数
     * 
     * @return 账号总数
     */
    long getAccountCount();
    
    /**
     * 获取活跃账号数量
     * 
     * @return 活跃账号数量
     */
    long getActiveAccountCount();
}