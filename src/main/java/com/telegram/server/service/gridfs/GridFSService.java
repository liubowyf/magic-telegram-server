package com.telegram.server.service.gridfs;

import com.mongodb.client.gridfs.GridFSBucket;
import com.mongodb.client.gridfs.GridFSDownloadStream;
import com.mongodb.client.gridfs.GridFSUploadStream;
import com.mongodb.client.gridfs.model.GridFSFile;
import com.mongodb.client.gridfs.model.GridFSUploadOptions;
import org.bson.Document;
import org.bson.types.ObjectId;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.Optional;

/**
 * GridFS基础服务类
 * 封装MongoDB GridFS的基础CRUD操作
 * 
 * @author liubo
 * @date 2025-01-19
 */
@Service
public class GridFSService {

    private static final Logger logger = LoggerFactory.getLogger(GridFSService.class);

    private final GridFSBucket sessionGridFSBucket;
    private final GridFSBucket messageFilesGridFSBucket;

    public GridFSService(
            @Qualifier("sessionGridFSBucket") GridFSBucket sessionGridFSBucket,
            @Qualifier("messageFilesGridFSBucket") GridFSBucket messageFilesGridFSBucket) {
        this.sessionGridFSBucket = sessionGridFSBucket;
        this.messageFilesGridFSBucket = messageFilesGridFSBucket;
    }

    /**
     * 存储文件到GridFS
     * 
     * @param bucketType bucket类型（session或messageFiles）
     * @param filename 文件名
     * @param data 文件数据
     * @param metadata 文件元数据
     * @return 文件ID
     * @throws GridFSException 存储异常
     */
    public ObjectId storeFile(BucketType bucketType, String filename, byte[] data, Document metadata) throws GridFSException {
        try {
            GridFSBucket bucket = getBucket(bucketType);
            
            GridFSUploadOptions options = new GridFSUploadOptions();
            if (metadata != null) {
                options.metadata(metadata);
            }
            
            try (InputStream inputStream = new ByteArrayInputStream(data)) {
                ObjectId fileId = bucket.uploadFromStream(filename, inputStream, options);
                logger.debug("文件存储成功: bucket={}, filename={}, fileId={}, size={}", 
                    bucketType, filename, fileId, data.length);
                return fileId;
            }
        } catch (Exception e) {
            logger.error("文件存储失败: bucket={}, filename={}, error={}", bucketType, filename, e.getMessage(), e);
            throw new GridFSException("文件存储失败: " + e.getMessage(), e);
        }
    }

    /**
     * 存储文件流到GridFS
     * 
     * @param bucketType bucket类型
     * @param filename 文件名
     * @param inputStream 输入流
     * @param metadata 文件元数据
     * @return 文件ID
     * @throws GridFSException 存储异常
     */
    public ObjectId storeFileStream(BucketType bucketType, String filename, InputStream inputStream, Document metadata) throws GridFSException {
        try {
            GridFSBucket bucket = getBucket(bucketType);
            
            GridFSUploadOptions options = new GridFSUploadOptions();
            if (metadata != null) {
                options.metadata(metadata);
            }
            
            ObjectId fileId = bucket.uploadFromStream(filename, inputStream, options);
            logger.debug("文件流存储成功: bucket={}, filename={}, fileId={}", bucketType, filename, fileId);
            return fileId;
        } catch (Exception e) {
            logger.error("文件流存储失败: bucket={}, filename={}, error={}", bucketType, filename, e.getMessage(), e);
            throw new GridFSException("文件流存储失败: " + e.getMessage(), e);
        }
    }

    /**
     * 从GridFS读取文件
     * 
     * @param bucketType bucket类型
     * @param fileId 文件ID
     * @return 文件数据，如果文件不存在返回空
     * @throws GridFSException 读取异常
     */
    public Optional<byte[]> readFile(BucketType bucketType, ObjectId fileId) throws GridFSException {
        try {
            GridFSBucket bucket = getBucket(bucketType);
            
            try (GridFSDownloadStream downloadStream = bucket.openDownloadStream(fileId);
                 ByteArrayOutputStream outputStream = new ByteArrayOutputStream()) {
                
                byte[] buffer = new byte[8192];
                int bytesRead;
                while ((bytesRead = downloadStream.read(buffer)) != -1) {
                    outputStream.write(buffer, 0, bytesRead);
                }
                
                byte[] data = outputStream.toByteArray();
                logger.debug("文件读取成功: bucket={}, fileId={}, size={}", bucketType, fileId, data.length);
                return Optional.of(data);
            }
        } catch (com.mongodb.MongoGridFSException e) {
            if (e.getMessage().contains("FileNotFound")) {
                logger.debug("文件不存在: bucket={}, fileId={}", bucketType, fileId);
                return Optional.empty();
            }
            logger.error("文件读取失败: bucket={}, fileId={}, error={}", bucketType, fileId, e.getMessage(), e);
            throw new GridFSException("文件读取失败: " + e.getMessage(), e);
        } catch (Exception e) {
            logger.error("文件读取失败: bucket={}, fileId={}, error={}", bucketType, fileId, e.getMessage(), e);
            throw new GridFSException("文件读取失败: " + e.getMessage(), e);
        }
    }

    /**
     * 从GridFS读取文件流
     * 
     * @param bucketType bucket类型
     * @param fileId 文件ID
     * @return 下载流，如果文件不存在返回空
     * @throws GridFSException 读取异常
     */
    public Optional<GridFSDownloadStream> readFileStream(BucketType bucketType, ObjectId fileId) throws GridFSException {
        try {
            GridFSBucket bucket = getBucket(bucketType);
            GridFSDownloadStream downloadStream = bucket.openDownloadStream(fileId);
            logger.debug("文件流读取成功: bucket={}, fileId={}", bucketType, fileId);
            return Optional.of(downloadStream);
        } catch (com.mongodb.MongoGridFSException e) {
            if (e.getMessage().contains("FileNotFound")) {
                logger.debug("文件不存在: bucket={}, fileId={}", bucketType, fileId);
                return Optional.empty();
            }
            logger.error("文件流读取失败: bucket={}, fileId={}, error={}", bucketType, fileId, e.getMessage(), e);
            throw new GridFSException("文件流读取失败: " + e.getMessage(), e);
        } catch (Exception e) {
            logger.error("文件流读取失败: bucket={}, fileId={}, error={}", bucketType, fileId, e.getMessage(), e);
            throw new GridFSException("文件流读取失败: " + e.getMessage(), e);
        }
    }

    /**
     * 删除GridFS文件
     * 
     * @param bucketType bucket类型
     * @param fileId 文件ID
     * @return 是否删除成功
     * @throws GridFSException 删除异常
     */
    public boolean deleteFile(BucketType bucketType, ObjectId fileId) throws GridFSException {
        try {
            GridFSBucket bucket = getBucket(bucketType);
            bucket.delete(fileId);
            logger.debug("文件删除成功: bucket={}, fileId={}", bucketType, fileId);
            return true;
        } catch (com.mongodb.MongoGridFSException e) {
            if (e.getMessage().contains("FileNotFound")) {
                logger.debug("文件不存在，无需删除: bucket={}, fileId={}", bucketType, fileId);
                return false;
            }
            logger.error("文件删除失败: bucket={}, fileId={}, error={}", bucketType, fileId, e.getMessage(), e);
            throw new GridFSException("文件删除失败: " + e.getMessage(), e);
        } catch (Exception e) {
            logger.error("文件删除失败: bucket={}, fileId={}, error={}", bucketType, fileId, e.getMessage(), e);
            throw new GridFSException("文件删除失败: " + e.getMessage(), e);
        }
    }

    /**
     * 获取文件元数据
     * 
     * @param bucketType bucket类型
     * @param fileId 文件ID
     * @return 文件元数据，如果文件不存在返回空
     * @throws GridFSException 查询异常
     */
    public Optional<GridFSFile> getFileMetadata(BucketType bucketType, ObjectId fileId) throws GridFSException {
        try {
            GridFSBucket bucket = getBucket(bucketType);
            GridFSFile gridFSFile = bucket.find(com.mongodb.client.model.Filters.eq("_id", fileId)).first();
            
            if (gridFSFile != null) {
                logger.debug("文件元数据获取成功: bucket={}, fileId={}, filename={}", 
                    bucketType, fileId, gridFSFile.getFilename());
                return Optional.of(gridFSFile);
            } else {
                logger.debug("文件不存在: bucket={}, fileId={}", bucketType, fileId);
                return Optional.empty();
            }
        } catch (Exception e) {
            logger.error("文件元数据获取失败: bucket={}, fileId={}, error={}", bucketType, fileId, e.getMessage(), e);
            throw new GridFSException("文件元数据获取失败: " + e.getMessage(), e);
        }
    }

    /**
     * 检查文件是否存在
     * 
     * @param bucketType bucket类型
     * @param fileId 文件ID
     * @return 文件是否存在
     * @throws GridFSException 检查异常
     */
    public boolean fileExists(BucketType bucketType, ObjectId fileId) throws GridFSException {
        return getFileMetadata(bucketType, fileId).isPresent();
    }

    /**
     * 获取指定类型的GridFS Bucket
     * 
     * @param bucketType bucket类型
     * @return GridFS Bucket
     */
    private GridFSBucket getBucket(BucketType bucketType) {
        return switch (bucketType) {
            case SESSION -> sessionGridFSBucket;
            case MESSAGE_FILES -> messageFilesGridFSBucket;
        };
    }

    /**
     * GridFS Bucket类型枚举
     */
    public enum BucketType {
        /** Session存储bucket */
        SESSION,
        /** 消息文件存储bucket */
        MESSAGE_FILES
    }

    /**
     * GridFS操作异常
     */
    public static class GridFSException extends Exception {
        public GridFSException(String message) {
            super(message);
        }

        public GridFSException(String message, Throwable cause) {
            super(message, cause);
        }
    }
}