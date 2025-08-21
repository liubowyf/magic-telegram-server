package com.telegram.server.service.gridfs;

import com.mongodb.client.gridfs.model.GridFSFile;
import com.telegram.server.service.gridfs.GridFSService.BucketType;
import com.telegram.server.service.gridfs.GridFSService.GridFSException;
import org.bson.Document;
import org.bson.types.ObjectId;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.util.*;
import java.util.zip.GZIPInputStream;

/**
 * GridFS数据完整性检查工具
 * 用于检查和修复GridFS中的数据格式问题
 * 
 * @author liubo
 * @date 2025-01-19
 */
@Service
@ConditionalOnProperty(name = "telegram.session.storage.strategy", havingValue = "gridfs")
public class GridFSIntegrityChecker {

    private static final Logger logger = LoggerFactory.getLogger(GridFSIntegrityChecker.class);

    @Autowired
    private GridFSService gridfsService;

    /**
     * 检查结果类
     */
    public static class CheckResult {
        private final ObjectId fileId;
        private final String filename;
        private final boolean isValid;
        private final String issue;
        private final String suggestion;

        public CheckResult(ObjectId fileId, String filename, boolean isValid, String issue, String suggestion) {
            this.fileId = fileId;
            this.filename = filename;
            this.isValid = isValid;
            this.issue = issue;
            this.suggestion = suggestion;
        }

        // Getters
        public ObjectId getFileId() { return fileId; }
        public String getFilename() { return filename; }
        public boolean isValid() { return isValid; }
        public String getIssue() { return issue; }
        public String getSuggestion() { return suggestion; }

        @Override
        public String toString() {
            return String.format("CheckResult{fileId=%s, filename='%s', valid=%s, issue='%s', suggestion='%s'}",
                    fileId, filename, isValid, issue, suggestion);
        }
    }

    /**
     * 检查单个GridFS文件的完整性
     * 
     * @param bucketType bucket类型
     * @param fileId 文件ID
     * @return 检查结果
     */
    public CheckResult checkFile(BucketType bucketType, ObjectId fileId) {
        try {
            // 获取文件元数据
            Optional<GridFSFile> fileOpt = gridfsService.getFileMetadata(bucketType, fileId);
            if (!fileOpt.isPresent()) {
                return new CheckResult(fileId, "unknown", false, "文件不存在", "删除相关引用");
            }

            GridFSFile file = fileOpt.get();
            String filename = file.getFilename();
            Document metadata = file.getMetadata();

            // 读取文件数据
            Optional<byte[]> dataOpt = gridfsService.readFile(bucketType, fileId);
            if (!dataOpt.isPresent()) {
                return new CheckResult(fileId, filename, false, "无法读取文件数据", "检查GridFS存储状态");
            }

            byte[] data = dataOpt.get();
            String compressionType = metadata != null ? metadata.getString("compressionType") : null;

            // 检查压缩格式一致性
            if ("gzip".equals(compressionType)) {
                if (!isValidGzipData(data)) {
                    // 检查是否是未压缩的JSON数据
                    if (isLikelyJsonData(data)) {
                        return new CheckResult(fileId, filename, false, 
                                "元数据标记为GZIP但数据是未压缩的JSON", 
                                "更新元数据compressionType为none或重新压缩数据");
                    } else {
                        return new CheckResult(fileId, filename, false, 
                                "元数据标记为GZIP但数据格式无效", 
                                "检查数据损坏或重新生成");
                    }
                }
            } else if ("none".equals(compressionType) || compressionType == null) {
                if (isValidGzipData(data)) {
                    return new CheckResult(fileId, filename, false, 
                            "元数据标记为未压缩但数据是GZIP格式", 
                            "更新元数据compressionType为gzip");
                }
            }

            return new CheckResult(fileId, filename, true, null, null);

        } catch (GridFSException e) {
            logger.error("检查文件完整性时发生GridFS异常: fileId={}", fileId, e);
            return new CheckResult(fileId, "unknown", false, "GridFS异常: " + e.getMessage(), "检查MongoDB连接和权限");
        } catch (Exception e) {
            logger.error("检查文件完整性时发生未知异常: fileId={}", fileId, e);
            return new CheckResult(fileId, "unknown", false, "未知异常: " + e.getMessage(), "联系技术支持");
        }
    }

    /**
     * 批量检查GridFS文件完整性
     * 
     * @param bucketType bucket类型
     * @param fileIds 文件ID列表
     * @return 检查结果列表
     */
    public List<CheckResult> checkFiles(BucketType bucketType, List<ObjectId> fileIds) {
        List<CheckResult> results = new ArrayList<>();
        
        for (ObjectId fileId : fileIds) {
            CheckResult result = checkFile(bucketType, fileId);
            results.add(result);
            
            if (!result.isValid()) {
                logger.warn("发现数据完整性问题: {}", result);
            }
        }
        
        return results;
    }

    /**
     * 生成完整性检查报告
     * 
     * @param results 检查结果列表
     * @return 格式化的报告字符串
     */
    public String generateReport(List<CheckResult> results) {
        StringBuilder report = new StringBuilder();
        report.append("=== GridFS数据完整性检查报告 ===").append("\n");
        report.append("检查时间: ").append(new Date()).append("\n");
        report.append("总文件数: ").append(results.size()).append("\n");
        
        long validCount = results.stream().filter(CheckResult::isValid).count();
        long invalidCount = results.size() - validCount;
        
        report.append("有效文件: ").append(validCount).append("\n");
        report.append("问题文件: ").append(invalidCount).append("\n\n");
        
        if (invalidCount > 0) {
            report.append("=== 问题详情 ===").append("\n");
            for (CheckResult result : results) {
                if (!result.isValid()) {
                    report.append("文件ID: ").append(result.getFileId()).append("\n");
                    report.append("文件名: ").append(result.getFilename()).append("\n");
                    report.append("问题: ").append(result.getIssue()).append("\n");
                    report.append("建议: ").append(result.getSuggestion()).append("\n");
                    report.append("---").append("\n");
                }
            }
        }
        
        return report.toString();
    }

    /**
     * 检查数据是否是有效的GZIP格式
     * 
     * @param data 待检查的数据
     * @return 如果是有效的GZIP数据则返回true
     */
    private boolean isValidGzipData(byte[] data) {
        if (data == null || data.length < 2) {
            return false;
        }
        
        // GZIP文件的魔数是0x1f, 0x8b
        if ((data[0] & 0xff) != 0x1f || (data[1] & 0xff) != 0x8b) {
            return false;
        }
        
        // 尝试解压缩来验证数据完整性
        try (ByteArrayInputStream bais = new ByteArrayInputStream(data);
             GZIPInputStream gzipIn = new GZIPInputStream(bais)) {
            
            byte[] buffer = new byte[1024];
            while (gzipIn.read(buffer) != -1) {
                // 只是读取数据来验证格式，不需要保存
            }
            return true;
        } catch (IOException e) {
            return false;
        }
    }

    /**
     * 检查数据是否可能是JSON格式
     * 
     * @param data 待检查的数据
     * @return 如果数据看起来像JSON则返回true
     */
    private boolean isLikelyJsonData(byte[] data) {
        if (data == null || data.length < 2) {
            return false;
        }
        
        // 检查是否以JSON对象或数组开始和结束
        char firstChar = (char) data[0];
        char lastChar = (char) data[data.length - 1];
        
        return (firstChar == '{' && lastChar == '}') || (firstChar == '[' && lastChar == ']');
    }
}