package com.telegram.server.repository;

import com.telegram.server.entity.SessionShard;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.data.mongodb.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

/**
 * Session分片数据访问层
 * 提供对SessionShard实体的CRUD操作
 * 
 * @author liubo
 * @date 2025-01-19
 */
@Repository
public interface SessionShardRepository extends MongoRepository<SessionShard, String> {

    /**
     * 根据session ID查找所有分片
     * 
     * @param sessionId 主session ID
     * @return 分片列表，按分片序号排序
     */
    @Query("{'sessionId': ?0}")
    List<SessionShard> findBySessionIdOrderByShardIndex(String sessionId);

    /**
     * 根据session ID和分片类型查找分片
     * 
     * @param sessionId 主session ID
     * @param shardType 分片类型
     * @return 分片列表，按分片序号排序
     */
    @Query("{'sessionId': ?0, 'shardType': ?1}")
    List<SessionShard> findBySessionIdAndShardTypeOrderByShardIndex(String sessionId, String shardType);

    /**
     * 根据session ID、分片类型和分片序号查找特定分片
     * 
     * @param sessionId 主session ID
     * @param shardType 分片类型
     * @param shardIndex 分片序号
     * @return 分片对象
     */
    @Query("{'sessionId': ?0, 'shardType': ?1, 'shardIndex': ?2}")
    Optional<SessionShard> findBySessionIdAndShardTypeAndShardIndex(String sessionId, String shardType, Integer shardIndex);

    /**
     * 删除指定session的所有分片
     * 
     * @param sessionId 主session ID
     * @return 删除的分片数量
     */
    @Query(delete = true, value = "{'sessionId': ?0}")
    long deleteBySessionId(String sessionId);

    /**
     * 删除指定session和分片类型的所有分片
     * 
     * @param sessionId 主session ID
     * @param shardType 分片类型
     * @return 删除的分片数量
     */
    @Query(delete = true, value = "{'sessionId': ?0, 'shardType': ?1}")
    long deleteBySessionIdAndShardType(String sessionId, String shardType);

    /**
     * 统计指定session的分片数量
     * 
     * @param sessionId 主session ID
     * @return 分片数量
     */
    @Query(value = "{'sessionId': ?0}", count = true)
    long countBySessionId(String sessionId);

    /**
     * 统计指定session和分片类型的分片数量
     * 
     * @param sessionId 主session ID
     * @param shardType 分片类型
     * @return 分片数量
     */
    @Query(value = "{'sessionId': ?0, 'shardType': ?1}", count = true)
    long countBySessionIdAndShardType(String sessionId, String shardType);

    /**
     * 检查指定session是否存在分片
     * 
     * @param sessionId 主session ID
     * @return 是否存在分片
     */
    boolean existsBySessionId(String sessionId);

    /**
     * 检查指定session和分片类型是否存在分片
     * 
     * @param sessionId 主session ID
     * @param shardType 分片类型
     * @return 是否存在分片
     */
    boolean existsBySessionIdAndShardType(String sessionId, String shardType);

    /**
     * 获取指定session的最大分片序号
     * 
     * @param sessionId 主session ID
     * @param shardType 分片类型
     * @return 最大分片序号，如果没有分片则返回null
     */
    @Query(value = "{'sessionId': ?0, 'shardType': ?1}", fields = "{'shardIndex': 1}", sort = "{'shardIndex': -1}")
    Optional<SessionShard> findTopBySessionIdAndShardTypeOrderByShardIndexDesc(String sessionId, String shardType);
}