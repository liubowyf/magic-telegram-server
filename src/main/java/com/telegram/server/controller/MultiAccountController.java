package com.telegram.server.controller;

import com.telegram.server.entity.TelegramAccount;
import com.telegram.server.service.MultiAccountTelegramService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 多账号Telegram控制器
 * 
 * 提供完整的多账号Telegram管理功能，支持同时管理多个Telegram账号。
 * 主要功能包括：
 * - 账号生命周期管理：创建、配置、删除账号
 * - 认证流程管理：手机号验证、短信验证码、两步验证密码
 * - 消息监听控制：启用/禁用单个或所有账号的消息监听
 * - Session数据管理：清理、导入、恢复Session数据
 * - 服务状态监控：获取服务和账号状态信息
 * 
 * API路径前缀：/multi-telegram
 * 支持跨域访问，适用于前端Web应用调用
 * 
 * @author liubo
 * @version 1.0
 * @since 2025.08.01
 */
@RestController
@RequestMapping("/multi-telegram")
@CrossOrigin(origins = "*")
public class MultiAccountController {
    
    /**
     * 日志记录器
     * 用于记录控制器操作日志，便于调试和监控
     */
    private static final Logger logger = LoggerFactory.getLogger(MultiAccountController.class);
    
    /**
     * 多账号Telegram服务
     * 注入的业务逻辑服务，处理所有多账号相关的操作
     */
    @Autowired
    private MultiAccountTelegramService telegramService;
    
    /**
     * 创建新的Telegram账号
     * 
     * 在系统中创建一个新的Telegram账号实例，分配唯一的账号ID。
     * 创建后的账号需要进一步配置API信息和完成认证流程才能使用。
     * 
     * @param request 请求体，包含账号信息
     *                - nickname: 账号昵称，用于标识和管理账号
     * @return ResponseEntity 包含创建结果
     *         - success: 是否创建成功
     *         - message: 操作结果消息
     *         - accountId: 新创建的账号ID（成功时返回）
     */
    @PostMapping("/accounts")
    public ResponseEntity<Map<String, Object>> createAccount(@RequestBody Map<String, String> request) {
        Map<String, Object> response = new HashMap<>();
        
        try {
            String nickname = request.get("nickname");
            if (nickname == null || nickname.trim().isEmpty()) {
                response.put("success", false);
                response.put("message", "账号昵称不能为空");
                return ResponseEntity.badRequest().body(response);
            }
            
            String accountId = telegramService.createAccount(nickname);
            response.put("success", true);
            response.put("message", "账号创建成功");
            response.put("accountId", accountId);
            
            return ResponseEntity.ok(response);
            
        } catch (Exception e) {
            logger.error("创建账号失败", e);
            response.put("success", false);
            response.put("message", "创建账号失败: " + e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(response);
        }
    }
    
    /**
     * 获取所有账号列表
     * 
     * 返回系统中所有已创建的Telegram账号信息，包括账号状态、
     * 认证状态、监听状态等详细信息。
     * 
     * @return ResponseEntity<List<Map<String, Object>>> 账号列表
     *         每个账号包含：accountId, nickname, status, authStatus, 
     *         listeningEnabled, phoneNumber等信息
     */
    @GetMapping("/accounts")
    public ResponseEntity<List<Map<String, Object>>> getAllAccounts() {
        try {
            List<Map<String, Object>> accounts = telegramService.getAllAccounts();
            return ResponseEntity.ok(accounts);
        } catch (Exception e) {
            logger.error("获取账号列表失败", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }
    
    /**
     * 获取指定账号的详细信息
     * 
     * 根据账号ID获取特定账号的完整信息，包括配置状态、
     * 认证状态、监听状态等。
     * 
     * @param accountId 账号ID，唯一标识符
     * @return ResponseEntity 包含账号详细信息或错误信息
     *         成功时返回完整的账号信息对象
     *         失败时返回错误消息和状态码
     */
    @GetMapping("/accounts/{accountId}")
    public ResponseEntity<Map<String, Object>> getAccount(@PathVariable("accountId") String accountId) {
        try {
            Map<String, Object> account = telegramService.getAccount(accountId);
            if (account != null) {
                return ResponseEntity.ok(account);
            } else {
                Map<String, Object> response = new HashMap<>();
                response.put("success", false);
                response.put("message", "账号不存在");
                return ResponseEntity.status(HttpStatus.NOT_FOUND).body(response);
            }
        } catch (Exception e) {
            logger.error("获取账号信息失败", e);
            Map<String, Object> response = new HashMap<>();
            response.put("success", false);
            response.put("message", "获取账号信息失败: " + e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(response);
        }
    }
    
    /**
     * 配置账号的Telegram API信息
     * 
     * 为指定账号配置Telegram API ID和API Hash，这是账号
     * 能够连接到Telegram服务器的必要凭据。需要从Telegram
     * 官方开发者平台获取这些信息。
     * 
     * @param accountId 账号ID
     * @param request 请求体，包含API配置信息
     *                - appId/apiId: Telegram API ID（支持两种字段名）
     *                - appHash/apiHash: Telegram API Hash（支持两种字段名）
     * @return ResponseEntity 配置结果
     *         - success: 是否配置成功
     *         - message: 操作结果消息
     */
    @PostMapping("/accounts/{accountId}/config")
    public ResponseEntity<Map<String, Object>> configAccount(
            @PathVariable("accountId") String accountId,
            @RequestBody Map<String, Object> request) {
        
        Map<String, Object> response = new HashMap<>();
        
        try {
            // 支持前端发送的 appId 字段名
            Integer apiId = null;
            if (request.get("appId") != null) {
                Object appIdObj = request.get("appId");
                if (appIdObj instanceof Integer) {
                    apiId = (Integer) appIdObj;
                } else if (appIdObj instanceof String) {
                    apiId = Integer.parseInt((String) appIdObj);
                } else {
                    apiId = Integer.parseInt(appIdObj.toString());
                }
            } else if (request.get("apiId") != null) {
                Object apiIdObj = request.get("apiId");
                if (apiIdObj instanceof Integer) {
                    apiId = (Integer) apiIdObj;
                } else if (apiIdObj instanceof String) {
                    apiId = Integer.parseInt((String) apiIdObj);
                } else {
                    apiId = Integer.parseInt(apiIdObj.toString());
                }
            }
            
            String apiHash = (String) request.get("appHash");
            if (apiHash == null) {
                apiHash = (String) request.get("apiHash");
            }
            
            if (apiId == null || apiId <= 0 || apiHash == null || apiHash.trim().isEmpty()) {
                response.put("success", false);
                response.put("message", "API ID和API Hash都不能为空");
                return ResponseEntity.badRequest().body(response);
            }
            
            boolean success = telegramService.configAccountApi(accountId, apiId, apiHash);
            response.put("success", success);
            response.put("message", success ? "API配置成功" : "API配置失败");
            
            return ResponseEntity.ok(response);
            
        } catch (Exception e) {
            logger.error("配置账号API失败", e);
            response.put("success", false);
            response.put("message", "配置账号API失败: " + e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(response);
        }
    }
    
    /**
     * 提交手机号码开始认证流程
     * 
     * 向Telegram服务器提交手机号码，开始账号认证流程。
     * 提交成功后，Telegram会向该手机号发送验证码短信。
     * 
     * @param accountId 账号ID
     * @param request 请求体
     *                - phoneNumber: 手机号码（包含国家代码，如+86）
     * @return ResponseEntity 提交结果
     *         - success: 是否提交成功
     *         - message: 操作结果消息
     */
    @PostMapping("/accounts/{accountId}/auth/phone")
    public ResponseEntity<Map<String, Object>> submitPhoneNumber(
            @PathVariable("accountId") String accountId,
            @RequestBody Map<String, String> request) {
        
        Map<String, Object> response = new HashMap<>();
        
        try {
            String phoneNumber = request.get("phoneNumber");
            if (phoneNumber == null || phoneNumber.trim().isEmpty()) {
                response.put("success", false);
                response.put("message", "手机号不能为空");
                return ResponseEntity.badRequest().body(response);
            }
            
            boolean success = telegramService.submitPhoneNumber(accountId, phoneNumber);
            response.put("success", success);
            response.put("message", success ? "手机号已提交，请等待验证码" : "手机号提交失败");
            
            return ResponseEntity.ok(response);
            
        } catch (Exception e) {
            logger.error("提交手机号失败", e);
            response.put("success", false);
            response.put("message", "提交手机号失败: " + e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(response);
        }
    }
    
    /**
     * 提交短信验证码完成认证
     * 
     * 提交从Telegram收到的短信验证码来验证手机号码。
     * 如果账号启用了两步验证，可能还需要进一步提交密码。
     * 
     * @param accountId 账号ID
     * @param request 请求体
     *                - code: 6位数字验证码
     * @return ResponseEntity 验证结果
     *         包含认证状态和下一步操作指引
     */
    @PostMapping("/accounts/{accountId}/auth/code")
    public ResponseEntity<Map<String, Object>> submitAuthCode(
            @PathVariable("accountId") String accountId,
            @RequestBody Map<String, String> request) {
        
        try {
            String code = request.get("code");
            if (code == null || code.trim().isEmpty()) {
                Map<String, Object> response = new HashMap<>();
                response.put("success", false);
                response.put("message", "验证码不能为空");
                return ResponseEntity.badRequest().body(response);
            }
            
            Map<String, Object> result = telegramService.submitAuthCode(accountId, code);
            return ResponseEntity.ok(result);
            
        } catch (Exception e) {
            logger.error("提交验证码失败", e);
            Map<String, Object> response = new HashMap<>();
            response.put("success", false);
            response.put("message", "提交验证码失败: " + e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(response);
        }
    }
    
    /**
     * 提交两步验证密码
     * 
     * 对于启用了两步验证的账号，在验证码验证成功后，
     * 需要进一步提交两步验证密码来完成完整的认证流程。
     * 
     * @param accountId 账号ID
     * @param request 请求体
     *                - password: 两步验证密码
     * @return ResponseEntity 密码验证结果
     *         包含最终的认证状态
     */
    @PostMapping("/accounts/{accountId}/auth/password")
    public ResponseEntity<Map<String, Object>> submitPassword(
            @PathVariable("accountId") String accountId,
            @RequestBody Map<String, String> request) {
        
        try {
            String password = request.get("password");
            if (password == null || password.trim().isEmpty()) {
                Map<String, Object> response = new HashMap<>();
                response.put("success", false);
                response.put("message", "密码不能为空");
                return ResponseEntity.badRequest().body(response);
            }
            
            Map<String, Object> result = telegramService.submitPassword(accountId, password);
            return ResponseEntity.ok(result);
            
        } catch (Exception e) {
            logger.error("提交密码失败", e);
            Map<String, Object> response = new HashMap<>();
            response.put("success", false);
            response.put("message", "提交密码失败: " + e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(response);
        }
    }
    
    /**
     * 获取账号当前认证状态
     * 
     * 查询指定账号的认证进度和状态，包括是否已配置API、
     * 是否已提交手机号、是否需要验证码、是否需要密码等。
     * 
     * @param accountId 账号ID
     * @return ResponseEntity 认证状态信息
     *         包含详细的认证进度和下一步操作提示
     */
    @GetMapping("/accounts/{accountId}/auth/status")
    public ResponseEntity<Map<String, Object>> getAuthStatus(@PathVariable("accountId") String accountId) {
        try {
            Map<String, Object> status = telegramService.getAccountAuthStatus(accountId);
            return ResponseEntity.ok(status);
        } catch (Exception e) {
            logger.error("获取认证状态失败", e);
            Map<String, Object> response = new HashMap<>();
            response.put("success", false);
            response.put("message", "获取认证状态失败: " + e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(response);
        }
    }
    
    /**
     * 设置账号消息监听状态
     * 
     * 启用或禁用指定账号的消息监听功能。启用后，账号将
     * 开始监听接收到的消息并进行相应处理。
     * 
     * @param accountId 账号ID
     * @param request 请求体
     *                - enabled: 是否启用监听（true/false）
     * @return ResponseEntity 设置结果
     *         - success: 是否设置成功
     *         - message: 操作结果消息
     */
    @PostMapping("/accounts/{accountId}/listening")
    public ResponseEntity<Map<String, Object>> setListeningStatus(
            @PathVariable("accountId") String accountId,
            @RequestBody Map<String, Boolean> request) {
        
        Map<String, Object> response = new HashMap<>();
        
        try {
            Boolean enabled = request.get("enabled");
            if (enabled == null) {
                response.put("success", false);
                response.put("message", "监听状态参数不能为空");
                return ResponseEntity.badRequest().body(response);
            }
            
            Map<String, Object> result = telegramService.setListeningEnabled(accountId, enabled);
            boolean success = (Boolean) result.get("success");
            response.put("success", success);
            response.put("message", success ? "监听状态设置成功" : "监听状态设置失败");
            
            return ResponseEntity.ok(response);
            
        } catch (Exception e) {
            logger.error("设置监听状态失败", e);
            response.put("success", false);
            response.put("message", "设置监听状态失败: " + e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(response);
        }
    }
    
    /**
     * 删除指定账号
     * 
     * 从系统中完全删除指定的Telegram账号，包括其配置信息、
     * Session数据等。此操作不可逆，请谨慎使用。
     * 
     * @param accountId 要删除的账号ID
     * @return ResponseEntity 删除结果
     *         - success: 是否删除成功
     *         - message: 操作结果消息
     */
    @DeleteMapping("/accounts/{accountId}")
    public ResponseEntity<Map<String, Object>> deleteAccount(@PathVariable("accountId") String accountId) {
        Map<String, Object> response = new HashMap<>();
        
        try {
            boolean success = telegramService.deleteAccount(accountId);
            response.put("success", success);
            response.put("message", success ? "账号删除成功" : "账号删除失败");
            
            return ResponseEntity.ok(response);
            
        } catch (Exception e) {
            logger.error("删除账号失败", e);
            response.put("success", false);
            response.put("message", "删除账号失败: " + e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(response);
        }
    }
    
    /**
     * 清空指定账号的Session数据
     * 
     * 删除指定账号的所有Session数据，账号将需要重新进行
     * 认证流程。适用于解决认证问题或重置账号状态。
     * 
     * @param accountId 账号ID
     * @return ResponseEntity 清空结果
     *         - success: 是否清空成功
     *         - message: 操作结果消息
     */
    @DeleteMapping("/accounts/{accountId}/session")
    public ResponseEntity<Map<String, Object>> clearAccountSession(@PathVariable("accountId") String accountId) {
        Map<String, Object> response = new HashMap<>();
        
        try {
            boolean success = telegramService.clearAccountSession(accountId);
            
            if (success) {
                response.put("success", true);
                response.put("message", "账号session数据已清空");
                return ResponseEntity.ok(response);
            } else {
                response.put("success", false);
                response.put("message", "清空账号session失败");
                return ResponseEntity.badRequest().body(response);
            }
            
        } catch (Exception e) {
            logger.error("清空账号session失败", e);
            response.put("success", false);
            response.put("message", "清空session时发生错误: " + e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(response);
        }
    }
    
    /**
     * 清空所有账号的Session数据并重新配置
     * 
     * 批量清空系统中所有账号的Session数据，并重新初始化
     * 多账号服务。这是一个重置操作，所有账号都需要重新认证。
     * 
     * @return ResponseEntity 批量清空结果
     *         包含操作成功状态和影响的账号数量
     */
    @DeleteMapping("/sessions/clear-all")
    public ResponseEntity<Map<String, Object>> clearAllSessions() {
        try {
            Map<String, Object> result = telegramService.clearAllSessionsAndReconfigure();
            
            if ((Boolean) result.get("success")) {
                return ResponseEntity.ok(result);
            } else {
                return ResponseEntity.badRequest().body(result);
            }
            
        } catch (Exception e) {
            logger.error("清空所有session失败", e);
            Map<String, Object> response = new HashMap<>();
            response.put("success", false);
            response.put("message", "清空所有session时发生错误: " + e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(response);
        }
    }
    
    /**
     * 获取多账号服务整体状态
     * 
     * 返回多账号Telegram服务的整体运行状态，包括服务是否
     * 正常运行、账号总数、认证账号数、监听账号数等统计信息。
     * 
     * @return ResponseEntity 服务状态信息
     *         包含服务运行状态和各项统计数据
     */
    @GetMapping("/status")
    public ResponseEntity<Map<String, Object>> getServiceStatus() {
        try {
            Map<String, Object> status = telegramService.getServiceStatus();
            return ResponseEntity.ok(status);
        } catch (Exception e) {
            logger.error("获取服务状态失败", e);
            Map<String, Object> response = new HashMap<>();
            response.put("success", false);
            response.put("message", "获取服务状态失败: " + e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(response);
        }
    }
    
    /**
     * 导入旧版单账户Session到多账户管理系统
     * 
     * 将旧版本的单账户Session数据导入到新的多账户管理系统中，
     * 实现平滑的版本升级和数据迁移。
     * 
     * @param request 请求体（可选）
     *                - nickname: 导入账号的昵称（可选，默认为"旧版恢复账号"）
     * @return ResponseEntity 导入结果
     *         - success: 是否导入成功
     *         - message: 操作结果消息
     */
    @PostMapping("/import-legacy-session")
    public ResponseEntity<Map<String, Object>> importLegacySession(@RequestBody(required = false) Map<String, String> request) {
        Map<String, Object> response = new HashMap<>();
        
        try {
            String nickname = "旧版恢复账号";
            if (request != null && request.containsKey("nickname")) {
                nickname = request.get("nickname");
            }
            
            // 调用已有的检查旧版session目录的方法
            telegramService.checkLegacySessionDirectories(nickname);
            
            response.put("success", true);
            response.put("message", "单账户session导入完成，请刷新账号列表查看");
            
            return ResponseEntity.ok(response);
            
        } catch (Exception e) {
            logger.error("导入单账户session失败", e);
            response.put("success", false);
            response.put("message", "导入单账户session失败: " + e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(response);
        }
    }
    
    /**
     * 批量开启所有账号的消息监听
     * 
     * 一键启用系统中所有已认证账号的消息监听功能，
     * 适用于服务重启后快速恢复监听状态。
     * 
     * @return ResponseEntity 批量开启结果
     *         - success: 是否操作成功
     *         - message: 操作结果消息，包含成功启用的账号数量
     */
    @PostMapping("/enable-all-listening")
    public ResponseEntity<Map<String, Object>> enableAllListening() {
        Map<String, Object> response = new HashMap<>();
        
        try {
            Map<String, Object> result = telegramService.enableAllListening();
            response.put("success", result.get("success"));
            response.put("message", result.get("message"));
            
            return ResponseEntity.ok(response);
            
        } catch (Exception e) {
            logger.error("开启所有账号监听失败", e);
            response.put("success", false);
            response.put("message", "开启所有账号监听失败: " + e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(response);
        }
    }
}