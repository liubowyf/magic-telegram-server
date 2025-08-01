package com.telegram.server;

import com.telegram.server.service.TelegramService;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.ConfigurableApplicationContext;

/**
 * Magic Telegram Server 主应用程序入口
 * 
 * @author liubo
 * @date 2024-12-19
 */
@SpringBootApplication
public class MagicTelegramServerApplication {

    public static void main(String[] args) {
        // 启动Spring Boot应用
        ConfigurableApplicationContext context = SpringApplication.run(MagicTelegramServerApplication.class, args);
        
        // 获取Telegram服务并启动监听
        TelegramService telegramService = context.getBean(TelegramService.class);
        telegramService.startListening();
    }
}