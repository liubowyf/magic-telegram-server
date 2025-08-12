package com.telegram.server.repository;

import com.telegram.server.entity.TelegramSession;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.data.mongodb.repository.Query;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

/**
 * Telegram Session数据访问接口
 * 
 * 提供对MongoDB中TelegramSession数据的CRUD操作。
 * 支持集群环境下的session管理，包括查询、更新、删除等操作。
 * 
 * 主要功能：
 * - 基础CRUD操作
 * - 按手机号查询session
 * - 查询活跃session
 * - 查询指定实例的session
 * - 清理过期session
 * - 批量操作支持
 * 
 * 集群支持：
 * - 支持多实例并发访问
 * - 提供session锁定查询
 * - 支持按实例ID过滤
 * 
 * @author liubo
 * @date 2025-08-11
 */
@Repository
public interface TelegramSessionRepository extends MongoRepository<TelegramSession, String> {

    /**
     * 根据手机号查询session
     * 
     * @param phoneNumber 手机号码
     * @return session信息
     */
    Optional<TelegramSession> findByPhoneNumber(String phoneNumber);

    /**
     * 查询所有活跃的session
     * 
     * @return 活跃session列表
     */
    List<TelegramSession> findByIsActiveTrue();

    /**
     * 查询指定实例的活跃session
     * 
     * @param instanceId 实例ID
     * @return session列表
     */
    List<TelegramSession> findByInstanceIdAndIsActiveTrue(String instanceId);

    /**
     * 查询指定认证状态的session
     * 
     * @param authState 认证状态
     * @return session列表
     */
    List<TelegramSession> findByAuthState(String authState);

    /**
     * 查询在指定时间之前最后活跃的session
     * 用于清理长时间未使用的session
     * 
     * @param time 时间阈值
     * @return session列表
     */
    List<TelegramSession> findByLastActiveTimeBefore(LocalDateTime time);

    /**
     * 查询在指定时间之前最后活跃且当前非活跃的session
     * 
     * @param time 时间阈值
     * @return session列表
     */
    List<TelegramSession> findByLastActiveTimeBeforeAndIsActiveFalse(LocalDateTime time);

    /**
     * 统计活跃session数量
     * 
     * @return 活跃session数量
     */
    long countByIsActiveTrue();

    /**
     * 统计指定实例的活跃session数量
     * 
     * @param instanceId 实例ID
     * @return session数量
     */
    long countByInstanceIdAndIsActiveTrue(String instanceId);

    /**
     * 检查手机号是否已存在
     * 
     * @param phoneNumber 手机号码
     * @return 是否存在
     */
    boolean existsByPhoneNumber(String phoneNumber);

    /**
     * 删除指定实例的所有session
     * 
     * @param instanceId 实例ID
     * @return 删除的记录数
     */
    long deleteByInstanceId(String instanceId);

    /**
     * 删除在指定时间之前最后活跃且当前非活跃的session
     * 
     * @param time 时间阈值
     * @return 删除的记录数
     */
    long deleteByLastActiveTimeBeforeAndIsActiveFalse(LocalDateTime time);

    /**
     * 更新指定实例的所有session为非活跃状态
     * 用于服务实例关闭时清理
     * 
     * @param instanceId 实例ID
     */
    @Query("{'instanceId': ?0}")
    void deactivateByInstanceId(String instanceId);

    /**
     * 查找可用的session（已认证）
     * 用于负载均衡分配
     * 
     * @return session列表
     */
    @Query("{'authState': 'READY'}")
    List<TelegramSession> findAvailableSessions();

    /**
     * 查找需要清理的session
     * 包括长时间未活跃的session
     * 
     * @param inactiveThreshold 非活跃时间阈值
     * @return session列表
     */
    @Query("{'$or': [" +
           "{'lastActiveTime': {'$lt': ?0}, 'isActive': false}," +
           "{'lastActiveTime': {'$exists': false}, 'createdTime': {'$lt': ?0}}" +
           "]}")
    List<TelegramSession> findSessionsToCleanup(LocalDateTime inactiveThreshold);

    /**
     * 按创建时间范围查询session
     * 
     * @param startTime 开始时间
     * @param endTime 结束时间
     * @return session列表
     */
    List<TelegramSession> findByCreatedTimeBetween(LocalDateTime startTime, LocalDateTime endTime);

    /**
     * 查询指定API ID的所有session
     * 
     * @param apiId API ID
     * @return session列表
     */
    List<TelegramSession> findByApiId(Integer apiId);

    /**
     * 查询有数据库文件的session
     * 用于数据迁移和备份
     * 
     * @return session列表
     */
    @Query("{'databaseFiles': {'$exists': true, '$ne': null}}")
    List<TelegramSession> findSessionsWithDatabaseFiles();

    /**
     * 查询没有数据库文件的session
     * 用于清理无效session
     * 
     * @return session列表
     */
    @Query("{'$or': [" +
           "{'databaseFiles': {'$exists': false}}," +
           "{'databaseFiles': null}," +
           "{'databaseFiles': {}}" +
           "]}")
    List<TelegramSession> findSessionsWithoutDatabaseFiles();
}