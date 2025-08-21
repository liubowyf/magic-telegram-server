package com.telegram.server.controller.admin;

import com.telegram.server.service.gridfs.GridFSIntegrityChecker;
import com.telegram.server.service.gridfs.GridFSService.BucketType;
import org.bson.types.ObjectId;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * GridFS管理控制器
 * 提供GridFS数据完整性检查和管理功能
 * 
 * @author liubo
 * @date 2025-01-19
 */
@RestController
@RequestMapping("/admin/gridfs")
@ConditionalOnProperty(name = "telegram.session.storage.strategy", havingValue = "gridfs")
public class GridFSAdminController {

    private static final Logger logger = LoggerFactory.getLogger(GridFSAdminController.class);

    @Autowired
    private GridFSIntegrityChecker integrityChecker;

    /**
     * 检查单个文件的完整性
     * 
     * @param bucketType bucket类型 (SESSION, DATA)
     * @param fileId 文件ID
     * @return 检查结果
     */
    @GetMapping("/check/{bucketType}/{fileId}")
    public ResponseEntity<Map<String, Object>> checkFile(
            @PathVariable String bucketType,
            @PathVariable String fileId) {
        
        try {
            BucketType bucket = BucketType.valueOf(bucketType.toUpperCase());
            ObjectId objectId = new ObjectId(fileId);
            
            GridFSIntegrityChecker.CheckResult result = integrityChecker.checkFile(bucket, objectId);
            
            Map<String, Object> response = new HashMap<>();
            response.put("fileId", result.getFileId().toString());
            response.put("filename", result.getFilename());
            response.put("valid", result.isValid());
            response.put("issue", result.getIssue());
            response.put("suggestion", result.getSuggestion());
            
            return ResponseEntity.ok(response);
            
        } catch (IllegalArgumentException e) {
            logger.error("无效的参数: bucketType={}, fileId={}", bucketType, fileId, e);
            Map<String, Object> error = new HashMap<>();
            error.put("error", "无效的参数: " + e.getMessage());
            return ResponseEntity.badRequest().body(error);
        } catch (Exception e) {
            logger.error("检查文件完整性时发生异常: bucketType={}, fileId={}", bucketType, fileId, e);
            Map<String, Object> error = new HashMap<>();
            error.put("error", "内部服务器错误: " + e.getMessage());
            return ResponseEntity.internalServerError().body(error);
        }
    }

    /**
     * 批量检查文件完整性
     * 
     * @param bucketType bucket类型 (SESSION, DATA)
     * @param fileIds 文件ID列表
     * @return 检查结果列表
     */
    @PostMapping("/check/{bucketType}")
    public ResponseEntity<Map<String, Object>> checkFiles(
            @PathVariable String bucketType,
            @RequestBody List<String> fileIds) {
        
        try {
            BucketType bucket = BucketType.valueOf(bucketType.toUpperCase());
            List<ObjectId> objectIds = fileIds.stream()
                    .map(ObjectId::new)
                    .collect(Collectors.toList());
            
            List<GridFSIntegrityChecker.CheckResult> results = integrityChecker.checkFiles(bucket, objectIds);
            
            Map<String, Object> response = new HashMap<>();
            response.put("totalFiles", results.size());
            response.put("validFiles", results.stream().filter(GridFSIntegrityChecker.CheckResult::isValid).count());
            response.put("invalidFiles", results.stream().filter(r -> !r.isValid()).count());
            
            List<Map<String, Object>> resultList = results.stream().map(result -> {
                Map<String, Object> item = new HashMap<>();
                item.put("fileId", result.getFileId().toString());
                item.put("filename", result.getFilename());
                item.put("valid", result.isValid());
                item.put("issue", result.getIssue());
                item.put("suggestion", result.getSuggestion());
                return item;
            }).collect(Collectors.toList());
            
            response.put("results", resultList);
            
            return ResponseEntity.ok(response);
            
        } catch (IllegalArgumentException e) {
            logger.error("无效的参数: bucketType={}, fileIds={}", bucketType, fileIds, e);
            Map<String, Object> error = new HashMap<>();
            error.put("error", "无效的参数: " + e.getMessage());
            return ResponseEntity.badRequest().body(error);
        } catch (Exception e) {
            logger.error("批量检查文件完整性时发生异常: bucketType={}, fileIds={}", bucketType, fileIds, e);
            Map<String, Object> error = new HashMap<>();
            error.put("error", "内部服务器错误: " + e.getMessage());
            return ResponseEntity.internalServerError().body(error);
        }
    }

    /**
     * 生成完整性检查报告
     * 
     * @param bucketType bucket类型 (SESSION, DATA)
     * @param fileIds 文件ID列表
     * @return 格式化的报告
     */
    @PostMapping("/report/{bucketType}")
    public ResponseEntity<Map<String, Object>> generateReport(
            @PathVariable String bucketType,
            @RequestBody List<String> fileIds) {
        
        try {
            BucketType bucket = BucketType.valueOf(bucketType.toUpperCase());
            List<ObjectId> objectIds = fileIds.stream()
                    .map(ObjectId::new)
                    .collect(Collectors.toList());
            
            List<GridFSIntegrityChecker.CheckResult> results = integrityChecker.checkFiles(bucket, objectIds);
            String report = integrityChecker.generateReport(results);
            
            Map<String, Object> response = new HashMap<>();
            response.put("report", report);
            response.put("timestamp", System.currentTimeMillis());
            
            return ResponseEntity.ok(response);
            
        } catch (IllegalArgumentException e) {
            logger.error("无效的参数: bucketType={}, fileIds={}", bucketType, fileIds, e);
            Map<String, Object> error = new HashMap<>();
            error.put("error", "无效的参数: " + e.getMessage());
            return ResponseEntity.badRequest().body(error);
        } catch (Exception e) {
            logger.error("生成完整性报告时发生异常: bucketType={}, fileIds={}", bucketType, fileIds, e);
            Map<String, Object> error = new HashMap<>();
            error.put("error", "内部服务器错误: " + e.getMessage());
            return ResponseEntity.internalServerError().body(error);
        }
    }

    /**
     * 健康检查端点
     * 
     * @return 服务状态
     */
    @GetMapping("/health")
    public ResponseEntity<Map<String, Object>> health() {
        Map<String, Object> response = new HashMap<>();
        response.put("status", "UP");
        response.put("service", "GridFS Admin Controller");
        response.put("timestamp", System.currentTimeMillis());
        return ResponseEntity.ok(response);
    }
}