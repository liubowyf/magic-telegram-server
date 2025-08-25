package com.telegram.server.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.util.concurrent.Executor;
import java.util.concurrent.ThreadPoolExecutor;

/**
 * 异步处理配置类
 * 配置消息处理的线程池，优化大量群组消息的处理性能
 * 
 * @author liubo
 * @date 2025-08-19
 */
@Configuration
@EnableAsync
public class AsyncConfig {

    private static final Logger logger = LoggerFactory.getLogger(AsyncConfig.class);

    @Value("${telegram.async.core-pool-size:10}")
    private int corePoolSize;

    @Value("${telegram.async.max-pool-size:50}")
    private int maxPoolSize;

    @Value("${telegram.async.queue-capacity:1000}")
    private int queueCapacity;

    @Value("${telegram.async.keep-alive-seconds:60}")
    private int keepAliveSeconds;

    @Value("${telegram.async.thread-name-prefix:TelegramMessage-}")
    private String threadNamePrefix;

    /**
     * 配置消息处理专用线程池
     * 针对大量群组消息处理进行优化
     * 
     * @return 消息处理线程池执行器
     */
    @Bean("messageProcessingExecutor")
    public Executor messageProcessingExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        
        // 核心线程数：保持活跃的线程数量
        executor.setCorePoolSize(corePoolSize);
        
        // 最大线程数：高峰期最大线程数量
        executor.setMaxPoolSize(maxPoolSize);
        
        // 队列容量：等待处理的任务队列大小
        executor.setQueueCapacity(queueCapacity);
        
        // 线程空闲时间：超过核心线程数的线程在空闲指定时间后被回收
        executor.setKeepAliveSeconds(keepAliveSeconds);
        
        // 线程名称前缀：便于日志追踪和问题排查
        executor.setThreadNamePrefix(threadNamePrefix);
        
        // 拒绝策略：当线程池和队列都满时，由调用线程执行任务
        executor.setRejectedExecutionHandler(new ThreadPoolExecutor.CallerRunsPolicy());
        
        // 等待所有任务完成后再关闭线程池
        executor.setWaitForTasksToCompleteOnShutdown(true);
        
        // 等待时间：最多等待60秒让任务完成
        executor.setAwaitTerminationSeconds(60);
        
        // 初始化线程池
        executor.initialize();
        
        logger.info("消息处理线程池配置完成: 核心线程数={}, 最大线程数={}, 队列容量={}, 线程前缀={}", 
            corePoolSize, maxPoolSize, queueCapacity, threadNamePrefix);
        
        return executor;
    }

    /**
     * 配置通用异步任务线程池
     * 用于处理其他异步任务
     * 
     * @return 通用异步任务执行器
     */
    @Bean("taskExecutor")
    public Executor taskExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        
        // 通用线程池配置相对保守
        executor.setCorePoolSize(5);
        executor.setMaxPoolSize(20);
        executor.setQueueCapacity(500);
        executor.setKeepAliveSeconds(60);
        executor.setThreadNamePrefix("Async-");
        executor.setRejectedExecutionHandler(new ThreadPoolExecutor.CallerRunsPolicy());
        executor.setWaitForTasksToCompleteOnShutdown(true);
        executor.setAwaitTerminationSeconds(60);
        
        executor.initialize();
        
        logger.info("通用异步线程池配置完成");
        
        return executor;
    }
}