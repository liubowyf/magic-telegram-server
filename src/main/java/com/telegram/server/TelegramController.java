package com.telegram.server;

import com.telegram.server.service.TelegramService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.http.HttpStatus;

import java.util.HashMap;
import java.util.Map;

/**
 * 单账号Telegram控制器
 * 
 * 提供单账号Telegram相关的REST API接口，包括：
 * 1. 服务状态查询
 * 2. 健康检查
 * 3. API配置
 * 4. 账号认证流程（手机号、验证码、密码）
 * 5. 认证状态查询
 * 
 * 注意：此控制器主要用于单账号模式，多账号功能请使用MultiAccountController
 * 
 * @author liubo
 * @version 1.0.0
 * @since 2024-12-19
 * @see com.telegram.server.controller.MultiAccountController
 */
@RestController
@RequestMapping("/telegram")
public class TelegramController {
    
    /**
     * 单账号Telegram服务实例
     * 负责处理单账号的所有Telegram相关操作
     */
    @Autowired
    private TelegramService telegramService;
    
    /**
     * 获取单账号Telegram服务状态
     * 
     * 返回当前单账号Telegram服务的运行状态，包括连接状态、认证状态等信息。
     * 主要用于监控和调试目的。
     * 
     * @return ResponseEntity包含服务状态信息和时间戳
     */
    @GetMapping("/status")
    public ResponseEntity<Map<String, Object>> getStatus() {
        Map<String, Object> status = new HashMap<>();
        status.put("service", "Magic Telegram Server");
        status.put("status", "running");
        status.put("description", "Telegram群消息实时监听服务");
        status.put("timestamp", System.currentTimeMillis());
        
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
                return ResponseEntity.ok(response);
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
     * 提交手机号进行认证
     * 
     * Telegram认证流程的第一步，提交手机号码后系统会向该号码发送验证码。
     * 手机号格式需要包含国家代码，例如：+8613800138000
     * 
     * @param request 包含phoneNumber字段的请求体
     * @return ResponseEntity包含验证码发送结果
     */
    @PostMapping("/auth/phone")
    public ResponseEntity<Map<String, Object>> submitPhone(@RequestBody Map<String, Object> request) {
        Map<String, Object> response = new HashMap<>();
        try {
            String phoneNumber = (String) request.get("phoneNumber");
            
            if (phoneNumber == null || phoneNumber.trim().isEmpty()) {
                response.put("success", false);
                response.put("message", "手机号不能为空");
                return ResponseEntity.ok(response);
            }
            
            boolean success = telegramService.submitPhoneNumber(phoneNumber.trim());
            if (success) {
                response.put("success", true);
                response.put("message", "验证码已发送");
            } else {
                response.put("success", false);
                response.put("message", "发送验证码失败");
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
                return ResponseEntity.ok(response);
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
                return ResponseEntity.ok(response);
            }
            
            boolean success = telegramService.submitPassword(password.trim());
            if (success) {
                response.put("success", true);
                response.put("message", "密码验证成功");
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
}