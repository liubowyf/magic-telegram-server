package com.telegram.server;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.ComponentScan;

/**
 * Magic Telegram Server 主应用类
 * 
 * 这是Spring Boot应用的入口点，负责启动整个Telegram消息监听服务。
 * 该应用提供了Telegram群消息的实时监听和管理功能。
 * 
 * @author liubo
 * @since 2025-08-05
 */
@SpringBootApplication
@ComponentScan(basePackages = {"com.telegram.server"})
public class MagicTelegramServerApplication {

    /**
     * 应用程序主入口方法
     * 
     * @param args 命令行参数
     */
    public static void main(String[] args) {
        SpringApplication.run(MagicTelegramServerApplication.class, args);
    }
}