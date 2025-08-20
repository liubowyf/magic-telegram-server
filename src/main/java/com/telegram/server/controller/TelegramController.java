package com.telegram.server.controller;

import com.telegram.server.service.ITelegramService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.http.HttpStatus;

import java.util.HashMap;
import java.util.Map;

/**
 * 单账号Telegram控制器
 * 
 * 提供完整的单账号Telegram管理功能，包括：
 * 1. 账号创建和初始化
 * 2. API配置管理
 * 3. 认证流程管理（手机号、验证码、密码）
 * 4. 消息监听控制
 * 5. Session数据管理
 * 6. 服务状态监控
 * 
 * API路径前缀：/api/telegram
 * 支持跨域访问，适用于前端Web应用调用
 * 
 * @author liubo
 * @version 1.0
 * @since 2025-08-05
 */
@RestController
@RequestMapping("/telegram")
@CrossOrigin(origins = "*")
public class TelegramController {
    
    /**
     * 单账号Telegram服务实例
     * 负责处理单账号的所有Telegram相关操作
     */
    @Autowired
    private ITelegramService telegramService;
    
    /**
     * 创建并初始化Telegram账号
     * 
     * 初始化单个Telegram账号实例，准备进行API配置和认证流程。
     * 这是使用系统的第一步操作。
     * 
     * @return ResponseEntity 包含创建结果
     *         - success: 是否创建成功
     *         - message: 操作结果消息
     *         - status: 账号状态信息
     */
    @PostMapping("/account/create")
    public ResponseEntity<Map<String, Object>> createAccount() {
        Map<String, Object> response = new HashMap<>();
        
        try {
            // 初始化账号
            telegramService.initializeAccount();
            
            response.put("success", true);
            response.put("message", "账号创建成功，请配置API信息");
            response.put("status", telegramService.getAuthStatus());
            
            return ResponseEntity.ok(response);
            
        } catch (Exception e) {
            response.put("success", false);
            response.put("message", "创建账号失败: " + e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(response);
        }
    }
    
    /**
     * 配置Telegram API信息
     * 
     * 设置Telegram API的appId和appHash，这是连接Telegram服务的必要凭证。
     * 需要在Telegram官网申请获得这些凭证信息。
     * 
     * @param request 包含appId和appHash的请求体
     * @return ResponseEntity包含配置操作的结果信息
     */
    @PostMapping("/config")
    public ResponseEntity<Map<String, Object>> configApi(@RequestBody Map<String, Object> request) {
        Map<String, Object> response = new HashMap<>();
        try {
            Integer appId = (Integer) request.get("appId");
            String appHash = (String) request.get("appHash");
            
            if (appId == null || appHash == null || appHash.trim().isEmpty()) {
                response.put("success", false);
                response.put("message", "App ID和App Hash不能为空");
                return ResponseEntity.badRequest().body(response);
            }
            
            boolean success = telegramService.configApi(appId, appHash);
            if (success) {
                response.put("success", true);
                response.put("message", "API配置成功");
            } else {
                response.put("success", false);
                response.put("message", "API配置失败");
            }
        } catch (Exception e) {
            response.put("success", false);
            response.put("message", "配置API时发生错误: " + e.getMessage());
        }
        return ResponseEntity.ok(response);
    }
    
    /**
     * 提交手机号码进行认证
     * 
     * Telegram认证流程的第一步，提交手机号码以接收短信验证码。
     * 手机号需要包含国家代码，例如：+8613800138000
     * 
     * @param request 包含phoneNumber字段的请求体
     * @return ResponseEntity包含手机号提交结果
     */
    @PostMapping("/auth/phone")
    public ResponseEntity<Map<String, Object>> submitPhone(@RequestBody Map<String, Object> request) {
        Map<String, Object> response = new HashMap<>();
        try {
            String phoneNumber = (String) request.get("phoneNumber");
            
            if (phoneNumber == null || phoneNumber.trim().isEmpty()) {
                response.put("success", false);
                response.put("message", "手机号不能为空");
                return ResponseEntity.badRequest().body(response);
            }
            
            boolean success = telegramService.submitPhoneNumber(phoneNumber.trim());
            if (success) {
                response.put("success", true);
                response.put("message", "手机号提交成功，请输入验证码");
            } else {
                response.put("success", false);
                response.put("message", "手机号提交失败");
            }
        } catch (Exception e) {
            response.put("success", false);
            response.put("message", "提交手机号时发生错误: " + e.getMessage());
        }
        return ResponseEntity.ok(response);
    }
    
    /**
     * 提交短信验证码
     * 
     * Telegram认证流程的第二步，提交收到的短信验证码进行验证。
     * 如果账号开启了两步验证，验证码通过后还需要提交密码。
     * 
     * @param request 包含code字段的请求体
     * @return ResponseEntity包含验证码验证结果，可能需要进一步密码验证
     */
    @PostMapping("/auth/code")
    public ResponseEntity<Map<String, Object>> submitCode(@RequestBody Map<String, Object> request) {
        Map<String, Object> response = new HashMap<>();
        try {
            String code = (String) request.get("code");
            
            if (code == null || code.trim().isEmpty()) {
                response.put("success", false);
                response.put("message", "验证码不能为空");
                return ResponseEntity.badRequest().body(response);
            }
            
            Map<String, Object> result = telegramService.submitAuthCode(code.trim());
            response.putAll(result);
        } catch (Exception e) {
            response.put("success", false);
            response.put("message", "提交验证码时发生错误: " + e.getMessage());
        }
        return ResponseEntity.ok(response);
    }
    
    /**
     * 提交两步验证密码
     * 
     * Telegram认证流程的第三步（可选），当账号开启两步验证时需要提交密码。
     * 只有在submitCode返回需要密码验证的情况下才需要调用此接口。
     * 
     * @param request 包含password字段的请求体
     * @return ResponseEntity包含密码验证结果
     */
    @PostMapping("/auth/password")
    public ResponseEntity<Map<String, Object>> submitPassword(@RequestBody Map<String, Object> request) {
        Map<String, Object> response = new HashMap<>();
        try {
            String password = (String) request.get("password");
            
            if (password == null || password.trim().isEmpty()) {
                response.put("success", false);
                response.put("message", "密码不能为空");
                return ResponseEntity.badRequest().body(response);
            }
            
            boolean success = telegramService.submitPassword(password.trim());
            if (success) {
                response.put("success", true);
                response.put("message", "密码验证成功，认证完成");
            } else {
                response.put("success", false);
                response.put("message", "密码验证失败");
            }
        } catch (Exception e) {
            response.put("success", false);
            response.put("message", "提交密码时发生错误: " + e.getMessage());
        }
        return ResponseEntity.ok(response);
    }
    
    /**
     * 获取当前认证授权状态
     * 
     * 查询当前账号的认证状态，包括是否已登录、认证进度等信息。
     * 可用于判断是否需要进行认证流程或认证是否已完成。
     * 
     * @return ResponseEntity包含详细的认证状态信息
     */
    @GetMapping("/auth/status")
    public ResponseEntity<Map<String, Object>> getAuthStatus() {
        Map<String, Object> response = new HashMap<>();
        try {
            Map<String, Object> authStatus = telegramService.getAuthStatus();
            response.putAll(authStatus);
        } catch (Exception e) {
            response.put("success", false);
            response.put("message", "获取授权状态时发生错误: " + e.getMessage());
        }
        return ResponseEntity.ok(response);
    }
    
    /**
     * 开始消息监听
     * 
     * 启动Telegram消息监听功能，开始接收和处理群组消息。
     * 只有在认证完成后才能成功启动监听。
     * 
     * @return ResponseEntity包含监听启动结果
     */
    @PostMapping("/listening/start")
    public ResponseEntity<Map<String, Object>> startListening() {
        Map<String, Object> response = new HashMap<>();
        try {
            telegramService.startListening();
            response.put("success", true);
            response.put("message", "消息监听已启动");
        } catch (Exception e) {
            response.put("success", false);
            response.put("message", "启动消息监听失败: " + e.getMessage());
        }
        return ResponseEntity.ok(response);
    }
    
    /**
     * 停止消息监听
     * 
     * 停止Telegram消息监听功能。
     * 
     * @return ResponseEntity包含监听停止结果
     */
    @PostMapping("/listening/stop")
    public ResponseEntity<Map<String, Object>> stopListening() {
        Map<String, Object> response = new HashMap<>();
        try {
            telegramService.stopListening();
            response.put("success", true);
            response.put("message", "消息监听已停止");
        } catch (Exception e) {
            response.put("success", false);
            response.put("message", "停止消息监听失败: " + e.getMessage());
        }
        return ResponseEntity.ok(response);
    }
    
    /**
     * 清理Session数据
     * 
     * 清除当前账号的所有Session数据，包括认证信息和缓存数据。
     * 清理后需要重新进行认证流程。
     * 
     * @return ResponseEntity包含清理结果
     */
    @DeleteMapping("/session/clear")
    public ResponseEntity<Map<String, Object>> clearSession() {
        Map<String, Object> response = new HashMap<>();
        try {
            telegramService.clearSession();
            response.put("success", true);
            response.put("message", "Session数据已清理");
        } catch (Exception e) {
            response.put("success", false);
            response.put("message", "清理Session失败: " + e.getMessage());
        }
        return ResponseEntity.ok(response);
    }
    
    /**
     * 获取服务状态
     * 
     * 返回当前Telegram服务的运行状态，包括连接状态、认证状态等信息。
     * 主要用于监控和调试目的。
     * 
     * @return ResponseEntity包含服务状态信息和时间戳
     */
    @GetMapping("/status")
    public ResponseEntity<Map<String, Object>> getStatus() {
        Map<String, Object> status = new HashMap<>();
        status.put("service", "Magic Telegram Server");
        status.put("status", "running");
        status.put("description", "单账号Telegram消息监听服务");
        status.put("timestamp", System.currentTimeMillis());
        status.put("authStatus", telegramService.getAuthStatus());
        
        return ResponseEntity.ok(status);
    }
    
    /**
     * 健康检查接口
     * 
     * 提供服务健康状态检查，用于负载均衡器和监控系统检测服务可用性。
     * 返回简单的UP状态表示服务正常运行。
     * 
     * @return ResponseEntity包含健康状态信息
     */
    @GetMapping("/health")
    public ResponseEntity<Map<String, String>> health() {
        Map<String, String> health = new HashMap<>();
        health.put("status", "UP");
        health.put("service", "telegram-listener");
        
        return ResponseEntity.ok(health);
    }
    
    /**
     * 检查MongoDB中的session数据状态
     * 
     * 用于诊断session数据完整性问题，检查数据库中存储的session信息。
     * 包括认证状态、文件数据、活跃状态等关键信息。
     * 
     * @return ResponseEntity 包含session数据检查结果
     *         - sessions: session列表及详细信息
     *         - summary: 数据统计摘要
     *         - issues: 发现的数据问题
     * 
     * @author liubo
     * @since 2025-01-20
     */
    @GetMapping("/session/check")
    public ResponseEntity<Map<String, Object>> checkSessionData() {
        Map<String, Object> response = new HashMap<>();
        try {
            Map<String, Object> checkResult = telegramService.checkSessionDataIntegrity();
            response.put("success", true);
            response.put("data", checkResult);
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            response.put("success", false);
            response.put("message", "检查session数据失败: " + e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(response);
        }
    }
}