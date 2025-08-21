package com.telegram.server.controller;

import com.telegram.server.dto.AccountDTO;
import com.telegram.server.dto.MessageDTO;
import com.telegram.server.dto.PageRequestDTO;
import com.telegram.server.dto.PageResponseDTO;
import com.telegram.server.service.ITelegramSessionService;
import com.telegram.server.service.ITelegramMessageService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Web管理系统控制器
 * 
 * 提供Web管理系统的API接口，包括：
 * 1. 账号管理功能（查询、删除、状态查看）
 * 2. 消息管理功能（列表查询、图片查看）
 * 3. 静态页面路由管理
 * 
 * API路径前缀：/api/admin
 * 支持跨域访问，适用于前端Web应用调用
 * 
 * @author liubo
 * @date 2025-01-21
 */
@RestController
@RequestMapping("/admin")
@CrossOrigin(origins = "*")
public class WebAdminController {
    
    /**
     * Telegram Session服务
     * 负责账号相关的数据操作
     */
    @Autowired
    private ITelegramSessionService sessionService;
    
    /**
     * Telegram消息服务
     * 负责消息相关的数据操作
     */
    @Autowired
    private ITelegramMessageService messageService;
    
    /**
     * GridFS存储管理器
     * 负责session文件的GridFS存储操作
     */
    @Autowired
    private com.telegram.server.service.gridfs.GridFSStorageManager gridfsStorageManager;
    
    // ==================== 账号管理API ====================
    
    /**
     * 获取账号列表
     * 
     * 分页查询所有已存储的Telegram账号信息，包括认证状态、活跃状态等。
     * 
     * @param page 页码（从1开始）
     * @param size 每页大小
     * @return ResponseEntity 包含账号列表和分页信息
     */
    @GetMapping("/accounts")
    public ResponseEntity<Map<String, Object>> getAccounts(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "10") int size) {
        return getAccountsInternal(page, size, "");
    }
    
    /**
     * 获取账号列表（POST方式）
     * 
     * 分页查询所有已存储的Telegram账号信息，包括认证状态、活跃状态等。
     * 
     * @param request 包含分页参数的请求体
     * @return ResponseEntity 包含账号列表和分页信息
     */
    @PostMapping("/accounts/list")
    public ResponseEntity<Map<String, Object>> getAccountsList(@RequestBody Map<String, Object> request) {
        int page = (Integer) request.getOrDefault("page", 0);
        int size = (Integer) request.getOrDefault("size", 10);
        String search = (String) request.getOrDefault("search", "");
        return getAccountsInternal(page, size, search);
    }
    
    /**
     * 获取账号列表的内部实现
     */
    private ResponseEntity<Map<String, Object>> getAccountsInternal(int page, int size, String search) {
        Map<String, Object> response = new HashMap<>();
        
        try {
            PageRequestDTO pageRequest = new PageRequestDTO(page, size);
            PageResponseDTO<AccountDTO> pageResponse = sessionService.getAccountsPage(pageRequest);
            
            response.put("success", true);
            response.put("data", pageResponse);
            
            return ResponseEntity.ok(response);
            
        } catch (Exception e) {
            response.put("success", false);
            response.put("message", "获取账号列表失败: " + e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(response);
        }
    }
    
    /**
     * 获取账号详细信息
     * 
     * 根据账号ID获取详细的账号信息，包括认证状态、配置信息等。
     * 
     * @param accountId 账号ID
     * @return ResponseEntity 包含账号详细信息
     */
    @GetMapping("/accounts/{accountId}")
    public ResponseEntity<Map<String, Object>> getAccountDetail(@PathVariable String accountId) {
        return getAccountDetailInternal(accountId);
    }
    
    /**
     * 获取账号详细信息（POST方式）
     * 
     * 根据账号ID获取详细的账号信息，包括认证状态、配置信息等。
     * 
     * @param request 包含账号ID的请求体
     * @return ResponseEntity 包含账号详细信息
     */
    @PostMapping("/accounts/detail")
    public ResponseEntity<Map<String, Object>> getAccountDetailPost(@RequestBody Map<String, Object> request) {
        String accountId = (String) request.get("accountId");
        return getAccountDetailInternal(accountId);
    }
    
    /**
     * 获取账号详细信息的内部实现
     */
    private ResponseEntity<Map<String, Object>> getAccountDetailInternal(String accountId) {
        Map<String, Object> response = new HashMap<>();
        
        try {
            Optional<AccountDTO> accountOpt = sessionService.getAccountById(accountId);
            AccountDTO account = accountOpt.orElse(null);
            
            if (account != null) {
                response.put("success", true);
                response.put("data", account);
            } else {
                response.put("success", false);
                response.put("message", "账号不存在");
                return ResponseEntity.status(HttpStatus.NOT_FOUND).body(response);
            }
            
            return ResponseEntity.ok(response);
            
        } catch (Exception e) {
            response.put("success", false);
            response.put("message", "获取账号详情失败: " + e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(response);
        }
    }
    
    /**
     * 删除账号
     * 
     * 根据账号ID删除指定的Telegram账号及其相关数据。
     * 
     * @param accountId 账号ID
     * @return ResponseEntity 包含删除操作结果
     */
    @DeleteMapping("/accounts/{accountId}")
    public ResponseEntity<Map<String, Object>> deleteAccount(@PathVariable String accountId) {
        Map<String, Object> response = new HashMap<>();
        
        try {
            boolean success = sessionService.deleteAccount(accountId);
            
            if (success) {
                response.put("success", true);
                response.put("message", "账号删除成功");
            } else {
                response.put("success", false);
                response.put("message", "账号删除失败，账号可能不存在");
                return ResponseEntity.status(HttpStatus.NOT_FOUND).body(response);
            }
            
            return ResponseEntity.ok(response);
            
        } catch (Exception e) {
            response.put("success", false);
            response.put("message", "删除账号失败: " + e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(response);
        }
    }
    
    // ==================== 消息管理API ====================
    
    /**
     * 获取消息列表
     * 
     * 分页查询所有已存储的Telegram消息，支持按时间、群组等条件筛选。
     * 
     * @param page 页码（从1开始）
     * @param size 每页大小
     * @param chatId 群组ID（可选）
     * @param startDate 开始日期（可选，格式：yyyy-MM-dd）
     * @param endDate 结束日期（可选，格式：yyyy-MM-dd）
     * @return ResponseEntity 包含消息列表和分页信息
     */
    @GetMapping("/messages")
    public ResponseEntity<Map<String, Object>> getMessages(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(required = false) String chatId,
            @RequestParam(required = false) String startDate,
            @RequestParam(required = false) String endDate) {
        return getMessagesInternal(page, size, chatId, startDate, endDate, "");
    }
    
    /**
     * 获取消息列表（POST方式）
     * 
     * 分页查询所有已存储的Telegram消息，支持按时间、群组等条件筛选。
     * 
     * @param request 包含查询参数的请求体
     * @return ResponseEntity 包含消息列表和分页信息
     */
    @PostMapping("/messages/list")
    public ResponseEntity<Map<String, Object>> getMessagesList(@RequestBody Map<String, Object> request) {
        int page = (Integer) request.getOrDefault("page", 0);
        int size = (Integer) request.getOrDefault("size", 20);
        String chatId = (String) request.get("chatId");
        String startDate = (String) request.get("startDate");
        String endDate = (String) request.get("endDate");
        String search = (String) request.getOrDefault("search", "");
        return getMessagesInternal(page, size, chatId, startDate, endDate, search);
    }
    
    /**
     * 获取消息列表的内部实现
     */
    private ResponseEntity<Map<String, Object>> getMessagesInternal(int page, int size, String chatId, String startDate, String endDate, String search) {
        Map<String, Object> response = new HashMap<>();
        
        try {
            PageRequestDTO pageRequest = new PageRequestDTO(page, size);
            PageResponseDTO<MessageDTO> pageResponse = messageService.getMessagesPage(pageRequest);
            
            response.put("success", true);
            response.put("data", pageResponse);
            
            return ResponseEntity.ok(response);
            
        } catch (Exception e) {
            response.put("success", false);
            response.put("message", "获取消息列表失败: " + e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(response);
        }
    }
    
    /**
     * 获取消息详细信息
     * 
     * 根据消息ID获取详细的消息信息，包括文本内容、媒体文件等。
     * 
     * @param messageId 消息ID
     * @return ResponseEntity 包含消息详细信息
     */
    @GetMapping("/messages/{messageId}")
    public ResponseEntity<Map<String, Object>> getMessageDetail(@PathVariable String messageId) {
        return getMessageDetailInternal(messageId);
    }
    
    /**
     * 获取消息详细信息（POST方式）
     * 
     * 根据消息ID获取详细的消息信息，包括文本内容、媒体文件等。
     * 
     * @param request 包含消息ID的请求体
     * @return ResponseEntity 包含消息详细信息
     */
    @PostMapping("/messages/detail")
    public ResponseEntity<Map<String, Object>> getMessageDetailPost(@RequestBody Map<String, Object> request) {
        String messageId = (String) request.get("messageId");
        return getMessageDetailInternal(messageId);
    }
    
    /**
     * 获取消息详细信息的内部实现
     */
    private ResponseEntity<Map<String, Object>> getMessageDetailInternal(String messageId) {
        Map<String, Object> response = new HashMap<>();
        
        try {
            Optional<MessageDTO> messageOpt = messageService.getMessageById(messageId);
            MessageDTO message = messageOpt.orElse(null);
            
            if (message != null) {
                response.put("success", true);
                response.put("data", message);
            } else {
                response.put("success", false);
                response.put("message", "消息不存在");
                return ResponseEntity.status(HttpStatus.NOT_FOUND).body(response);
            }
            
            return ResponseEntity.ok(response);
            
        } catch (Exception e) {
            response.put("success", false);
            response.put("message", "获取消息详情失败: " + e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(response);
        }
    }
    
    /**
     * 获取消息图片
     * 
     * 返回消息中的图片文件（Base64编码）。
     * 
     * @param messageId 消息ID
     * @return ResponseEntity 包含图片的Base64数据
     */
    @GetMapping("/messages/{messageId}/image")
    public ResponseEntity<Map<String, Object>> getMessageImage(@PathVariable String messageId) {
        return getMessageImageInternal(messageId);
    }
    
    /**
     * 获取消息图片（POST方式）
     * 
     * 返回消息中的图片文件（Base64编码）。
     * 
     * @param request 包含消息ID的请求体
     * @return ResponseEntity 包含图片的Base64数据
     */
    @PostMapping("/messages/image")
    public ResponseEntity<Map<String, Object>> getMessageImagePost(@RequestBody Map<String, Object> request) {
        String messageId = (String) request.get("id");
        return getMessageImageInternal(messageId);
    }
    
    /**
     * 获取消息图片的内部实现
     */
    private ResponseEntity<Map<String, Object>> getMessageImageInternal(String messageId) {
        Map<String, Object> response = new HashMap<>();
        
        try {
            Optional<String> imageDataOpt = messageService.getMessageImage(messageId);
            Map<String, Object> imageData = new HashMap<>();
            if (imageDataOpt.isPresent()) {
                imageData.put("imageData", imageDataOpt.get());
            }
            
            if (imageData != null && !imageData.isEmpty()) {
                response.put("success", true);
                response.put("data", imageData);
            } else {
                response.put("success", false);
                response.put("message", "图片不存在或消息不包含图片");
                return ResponseEntity.status(HttpStatus.NOT_FOUND).body(response);
            }
            
            return ResponseEntity.ok(response);
            
        } catch (Exception e) {
            response.put("success", false);
            response.put("message", "获取图片失败: " + e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(response);
        }
    }
    
    /**
     * 获取消息图片内容（直接返回图片数据）
     * 
     * @param messageId 消息ID
     * @return 图片内容响应
     */
    @GetMapping("/messages/{messageId}/image/raw")
    public ResponseEntity<?> getMessageImageRaw(@PathVariable String messageId) {
        try {
            Optional<MessageDTO> messageOpt = messageService.getMessageById(messageId);
            MessageDTO message = messageOpt.orElse(null);
            if (message == null) {
                return ResponseEntity.notFound().build();
            }
            
            if (!message.isHasImage() || message.getImageData() == null) {
                return ResponseEntity.notFound().build();
            }
            
            // 解码Base64图片数据
            byte[] imageBytes = java.util.Base64.getDecoder().decode(message.getImageData());
            
            // 设置响应头
            org.springframework.http.HttpHeaders headers = new org.springframework.http.HttpHeaders();
            if (message.getImageMimeType() != null) {
                headers.setContentType(MediaType.parseMediaType(message.getImageMimeType()));
            } else {
                headers.setContentType(MediaType.IMAGE_JPEG); // 默认JPEG
            }
            
            return ResponseEntity.ok()
                    .headers(headers)
                    .body(imageBytes);
                    
        } catch (Exception e) {
            Map<String, String> error = new HashMap<>();
            error.put("error", "获取图片失败: " + e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(error);
        }
    }
    
    // ==================== 系统状态API ====================
    
    /**
     * 获取系统统计信息
     * 
     * 返回系统的基本统计信息，包括账号数量、消息数量等。
     * 
     * @return ResponseEntity 包含系统统计信息
     */
    @GetMapping("/stats")
    public ResponseEntity<Map<String, Object>> getSystemStats() {
        return getSystemStatsInternal();
    }
    
    /**
     * 获取系统统计信息（POST方式）
     * 
     * 返回系统的基本统计信息，包括账号数量、消息数量等。
     * 
     * @param request 请求体（可为空）
     * @return ResponseEntity 包含系统统计信息
     */
    @PostMapping("/stats")
    public ResponseEntity<Map<String, Object>> getSystemStatsPost(@RequestBody(required = false) Map<String, Object> request) {
        return getSystemStatsInternal();
    }
    
    /**
     * 获取系统统计信息的内部实现
     */
    private ResponseEntity<Map<String, Object>> getSystemStatsInternal() {
        Map<String, Object> response = new HashMap<>();
        
        try {
            Map<String, Object> stats = new HashMap<>();
            // 修改字段名以匹配前端期望的格式
            stats.put("totalAccounts", sessionService.getAccountCount());
            stats.put("totalMessages", messageService.getMessageCount());
            stats.put("activeAccounts", sessionService.getActiveAccountCount());
            // 添加今日消息数统计（暂时设为0，后续可扩展）
            stats.put("todayMessages", 0);
            
            response.put("success", true);
            response.put("data", stats);
            
            return ResponseEntity.ok(response);
            
        } catch (Exception e) {
            response.put("success", false);
            response.put("message", "获取系统统计失败: " + e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(response);
        }
    }
    
    // ==================== Session文件下载API ====================
    
    /**
     * 下载账号的session文件数据包
     * 
     * 提供账号session文件的下载功能，包括数据库文件和下载文件。
     * 返回压缩后的文件数据包，供前端下载。
     * 
     * @param accountId 账号ID（手机号）
     * @return ResponseEntity 包含文件数据或错误信息
     * 
     * @author liubo
     * @date 2025-01-21
     */
    @GetMapping("/accounts/{accountId}/session-files")
    public ResponseEntity<Map<String, Object>> downloadSessionFiles(@PathVariable String accountId) {
        Map<String, Object> response = new HashMap<>();
        
        try {
            // 获取账号信息
            Optional<AccountDTO> accountOpt = sessionService.getAccountById(accountId);
            if (!accountOpt.isPresent()) {
                response.put("success", false);
                response.put("message", "账号不存在: " + accountId);
                return ResponseEntity.status(HttpStatus.NOT_FOUND).body(response);
            }
            
            // 获取session数据
            Optional<com.telegram.server.entity.TelegramSession> sessionOpt = sessionService.getSessionByPhoneNumber(accountId);
            if (!sessionOpt.isPresent()) {
                response.put("success", false);
                response.put("message", "Session数据不存在: " + accountId);
                return ResponseEntity.status(HttpStatus.NOT_FOUND).body(response);
            }
            
            com.telegram.server.entity.TelegramSession session = sessionOpt.get();
            
            // 加载完整的session数据（包括GridFS中的文件）
            com.telegram.server.entity.TelegramSession fullSession = gridfsStorageManager.loadSession(session.getId());
            if (fullSession == null) {
                response.put("success", false);
                response.put("message", "无法加载Session文件数据: " + accountId);
                return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(response);
            }
            
            // 构建文件数据包
            Map<String, Object> sessionData = new HashMap<>();
            sessionData.put("phoneNumber", fullSession.getPhoneNumber());
            sessionData.put("apiId", fullSession.getApiId());
            sessionData.put("apiHash", fullSession.getApiHash());
            sessionData.put("authState", fullSession.getAuthState());
            sessionData.put("createdTime", fullSession.getCreatedTime());
            sessionData.put("lastActiveTime", fullSession.getLastActiveTime());
            
            // 添加文件数据
            if (fullSession.getDatabaseFiles() != null && !fullSession.getDatabaseFiles().isEmpty()) {
                sessionData.put("databaseFiles", fullSession.getDatabaseFiles());
            }
            if (fullSession.getDownloadedFiles() != null && !fullSession.getDownloadedFiles().isEmpty()) {
                sessionData.put("downloadedFiles", fullSession.getDownloadedFiles());
            }
            
            // 添加存储信息
            sessionData.put("storageVersion", fullSession.getStorageVersion());
            sessionData.put("compressionType", fullSession.getCompressionType());
            sessionData.put("originalSize", fullSession.getOriginalSize());
            sessionData.put("compressedSize", fullSession.getCompressedSize());
            
            response.put("success", true);
            response.put("message", "Session文件数据获取成功");
            response.put("data", sessionData);
            response.put("filename", "session_" + accountId + "_" + System.currentTimeMillis() + ".json");
            
            return ResponseEntity.ok(response);
            
        } catch (Exception e) {
            response.put("success", false);
            response.put("message", "下载Session文件失败: " + e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(response);
        }
    }
    
    /**
     * POST方式下载账号的session文件数据包
     * 
     * @param request 请求体，包含accountId
     * @return ResponseEntity 包含文件数据或错误信息
     */
    @PostMapping("/accounts/session-files")
    public ResponseEntity<Map<String, Object>> downloadSessionFilesPost(@RequestBody Map<String, Object> request) {
        String accountId = (String) request.get("accountId");
        return downloadSessionFiles(accountId);
    }
    
    /**
     * 健康检查接口
     * 
     * 提供Web管理系统的健康状态检查。
     * 
     * @return ResponseEntity 包含健康状态信息
     */
    @GetMapping("/health")
    public ResponseEntity<Map<String, String>> health() {
        Map<String, String> health = new HashMap<>();
        health.put("status", "UP");
        health.put("service", "web-admin");
        health.put("timestamp", String.valueOf(System.currentTimeMillis()));
        
        return ResponseEntity.ok(health);
    }
}