package com.telegram.server;

import com.telegram.server.service.TelegramService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * Magic Telegram Server 应用程序主入口
 * 
 * 这是一个基于Spring Boot的Telegram消息监听服务，支持：
 * 1. 单账号Telegram消息监听
 * 2. 多账号管理和消息监听
 * 3. 账号认证和Session管理
 * 4. RESTful API接口
 * 
 * 应用启动后会自动初始化Telegram服务并开始监听消息。
 * 
 * @author liubo
 * @version 1.0.0
 * @since 2025.08.01
 */
@SpringBootApplication
public class MagicTelegramServerApplication implements CommandLineRunner {

    /**
     * 单账号Telegram服务
     * 用于处理传统的单账号消息监听功能
     */
    @Autowired
    private TelegramService telegramService;

    /**
     * 应用程序主入口方法
     * 
     * @param args 命令行参数
     */
    public static void main(String[] args) {
        SpringApplication.run(MagicTelegramServerApplication.class, args);
    }

    /**
     * Spring Boot应用启动完成后的回调方法
     * 在所有Bean初始化完成后执行，用于启动Telegram监听服务
     * 
     * @param args 命令行参数
     * @throws Exception 启动过程中可能出现的异常
     */
    @Override
    public void run(String... args) throws Exception {
        // 启动单账号Telegram监听服务
        // 注意：多账号服务在MultiAccountTelegramService中自动初始化
        telegramService.startListening();
    }
}