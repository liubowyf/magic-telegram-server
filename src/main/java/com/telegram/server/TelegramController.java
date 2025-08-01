package com.telegram.server;

import com.telegram.server.service.TelegramService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.http.HttpStatus;

import java.util.HashMap;
import java.util.Map;

/**
 * Telegram服务REST控制器
 * 
 * @author liubo
 * @date 2024-12-19
 */
@RestController
@RequestMapping("/telegram")
public class TelegramController {
    
    @Autowired
    private TelegramService telegramService;
    
    /**
     * 获取服务状态
     * 
     * @return 服务状态信息
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
     * @return 健康状态
     */
    @GetMapping("/health")
    public ResponseEntity<Map<String, String>> health() {
        Map<String, String> health = new HashMap<>();
        health.put("status", "UP");
        health.put("service", "telegram-listener");
        
        return ResponseEntity.ok(health);
    }
    
    /**
     * 配置API信息
     * 
     * @param request 包含appId和appHash的请求
     * @return 配置结果
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
     * 提交手机号
     * 
     * @param request 包含phoneNumber的请求
     * @return 提交结果
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
     * 提交验证码
     * 
     * @param request 包含code的请求
     * @return 提交结果
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
     * 提交密码（如果需要两步验证）
     * 
     * @param request 包含password的请求
     * @return 提交结果
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
     * 获取当前授权状态
     * 
     * @return 授权状态信息
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