package com.telegram.server.lifecycle;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationContext;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.context.event.ContextClosedEvent;
import org.springframework.context.event.ContextRefreshedEvent;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.stereotype.Component;

import java.util.concurrent.TimeUnit;

/**
 * 应用生命周期管理器
 * 
 * 负责管理Spring应用的生命周期事件，特别是在应用关闭时
 * 优雅地处理异步任务和线程池的关闭，防止BeanCreationNotAllowedException。
 * 
 * 主要功能：
 * - 监听应用启动和关闭事件
 * - 优雅关闭异步任务线程池
 * - 提供应用状态检查方法
 * - 记录生命周期事件日志
 * 
 * @author liubo
 * @date 2025-01-19
 */
@Component
public class ApplicationLifecycleManager {

    private static final Logger logger = LoggerFactory.getLogger(ApplicationLifecycleManager.class);

    @Autowired
    private ApplicationContext applicationContext;

    /**
     * 应用是否正在关闭
     */
    private volatile boolean isShuttingDown = false;

    /**
     * 应用是否已完全启动
     */
    private volatile boolean isFullyStarted = false;

    /**
     * 监听应用启动完成事件
     * 
     * @param event 上下文刷新事件
     */
    @EventListener
    public void handleContextRefreshed(ContextRefreshedEvent event) {
        if (event.getApplicationContext() == this.applicationContext) {
            isFullyStarted = true;
            logger.info("应用启动完成，生命周期管理器已激活");
        }
    }

    /**
     * 监听应用关闭事件
     * 在应用关闭时优雅地处理异步任务
     * 
     * @param event 上下文关闭事件
     */
    @EventListener
    public void handleContextClosed(ContextClosedEvent event) {
        if (event.getApplicationContext() == this.applicationContext) {
            logger.info("应用开始关闭，启动优雅关闭流程");
            isShuttingDown = true;
            
            // 优雅关闭异步任务线程池
            shutdownAsyncExecutors();
            
            logger.info("应用关闭流程完成");
        }
    }

    /**
     * 优雅关闭异步任务线程池
     */
    private void shutdownAsyncExecutors() {
        try {
            // 关闭消息处理线程池
            shutdownExecutor("messageProcessingExecutor", "消息处理线程池");
            
            // 关闭通用任务线程池
            shutdownExecutor("taskExecutor", "通用任务线程池");
            
        } catch (Exception e) {
            logger.error("关闭异步执行器时发生错误", e);
        }
    }

    /**
     * 关闭指定的线程池执行器
     * 
     * @param beanName 执行器Bean名称
     * @param description 执行器描述
     */
    private void shutdownExecutor(String beanName, String description) {
        try {
            if (applicationContext.containsBean(beanName)) {
                Object bean = applicationContext.getBean(beanName);
                if (bean instanceof ThreadPoolTaskExecutor) {
                    ThreadPoolTaskExecutor executor = (ThreadPoolTaskExecutor) bean;
                    
                    logger.info("开始关闭{}: {}", description, beanName);
                    
                    // 停止接受新任务
                    executor.shutdown();
                    
                    // 等待现有任务完成
                    if (!executor.getThreadPoolExecutor().awaitTermination(30, TimeUnit.SECONDS)) {
                        logger.warn("{}在30秒内未能完全关闭，强制关闭", description);
                        executor.getThreadPoolExecutor().shutdownNow();
                        
                        // 再等待一段时间
                        if (!executor.getThreadPoolExecutor().awaitTermination(10, TimeUnit.SECONDS)) {
                            logger.error("{}强制关闭后仍有任务未完成", description);
                        }
                    }
                    
                    logger.info("{}关闭完成", description);
                }
            }
        } catch (Exception e) {
            logger.error("关闭{}时发生错误: {}", description, e.getMessage(), e);
        }
    }

    /**
     * 检查应用是否正在关闭
     * 
     * @return 是否正在关闭
     */
    public boolean isShuttingDown() {
        return isShuttingDown;
    }

    /**
     * 检查应用是否已完全启动
     * 
     * @return 是否已完全启动
     */
    public boolean isFullyStarted() {
        return isFullyStarted;
    }

    /**
     * 检查应用是否处于活跃状态
     * 即已完全启动且未开始关闭
     * 
     * @return 是否处于活跃状态
     */
    public boolean isActive() {
        return isFullyStarted && !isShuttingDown;
    }

    /**
     * 检查Spring容器是否处于活跃状态
     * 结合ApplicationContext状态和生命周期管理器状态进行综合判断
     * 
     * @return 容器是否活跃
     */
    public boolean isApplicationContextActive() {
        try {
            // 首先检查生命周期管理器状态
            if (!isActive()) {
                return false;
            }
            
            // 然后检查ApplicationContext状态
            if (applicationContext instanceof ConfigurableApplicationContext) {
                ConfigurableApplicationContext configurableContext = (ConfigurableApplicationContext) applicationContext;
                return configurableContext.isActive() && configurableContext.isRunning();
            }
            
            return true;
        } catch (Exception e) {
            logger.warn("检查ApplicationContext状态时发生异常: {}", e.getMessage());
            return false;
        }
    }

    /**
     * 获取应用状态信息
     * 
     * @return 状态信息字符串
     */
    public String getStatusInfo() {
        return String.format("ApplicationLifecycleManager状态: 已启动=%s, 正在关闭=%s, 活跃=%s", 
            isFullyStarted, isShuttingDown, isActive());
    }
}