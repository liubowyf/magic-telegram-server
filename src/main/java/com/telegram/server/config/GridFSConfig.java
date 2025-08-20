package com.telegram.server.config;

import com.mongodb.client.MongoClient;
import com.mongodb.client.gridfs.GridFSBucket;
import com.mongodb.client.gridfs.GridFSBuckets;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.gridfs.GridFsTemplate;

/**
 * GridFS配置类
 * 配置GridFS相关的Bean和服务
 * 
 * @author liubo
 * @date 2025-01-19
 */
@Configuration
public class GridFSConfig {

    @Value("${telegram.session.storage.gridfs.bucket-name:telegram_sessions}")
    private String bucketName;

    @Value("${message.storage.gridfs.bucket-name:telegram_files}")
    private String messageFilesBucketName;

    /**
     * 创建Session存储用的GridFS Bucket
     * 
     * @param mongoClient MongoDB客户端
     * @param mongoTemplate MongoDB模板
     * @return GridFS Bucket实例
     */
    @Bean("sessionGridFSBucket")
    public GridFSBucket sessionGridFSBucket(MongoClient mongoClient, MongoTemplate mongoTemplate) {
        return GridFSBuckets.create(
            mongoClient.getDatabase(mongoTemplate.getDb().getName()),
            bucketName
        );
    }

    /**
     * 创建消息文件存储用的GridFS Bucket
     * 
     * @param mongoClient MongoDB客户端
     * @param mongoTemplate MongoDB模板
     * @return GridFS Bucket实例
     */
    @Bean("messageFilesGridFSBucket")
    public GridFSBucket messageFilesGridFSBucket(MongoClient mongoClient, MongoTemplate mongoTemplate) {
        return GridFSBuckets.create(
            mongoClient.getDatabase(mongoTemplate.getDb().getName()),
            messageFilesBucketName
        );
    }

    /**
     * 创建Session存储用的GridFS模板
     * 
     * @param mongoTemplate MongoDB模板
     * @param mongoClient MongoDB客户端
     * @return GridFS模板实例
     */
    @Bean("sessionGridFsTemplate")
    public GridFsTemplate sessionGridFsTemplate(MongoTemplate mongoTemplate, MongoClient mongoClient) {
        return new GridFsTemplate(
            mongoTemplate.getMongoDatabaseFactory(), 
            mongoTemplate.getConverter(), 
            bucketName
        );
    }

    /**
     * 创建消息文件存储用的GridFS模板
     * 
     * @param mongoTemplate MongoDB模板
     * @param mongoClient MongoDB客户端
     * @return GridFS模板实例
     */
    @Bean("messageFilesGridFsTemplate")
    public GridFsTemplate messageFilesGridFsTemplate(MongoTemplate mongoTemplate, MongoClient mongoClient) {
        return new GridFsTemplate(
            mongoTemplate.getMongoDatabaseFactory(), 
            mongoTemplate.getConverter(), 
            messageFilesBucketName
        );
    }
}