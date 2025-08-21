package com.telegram.server.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.ViewControllerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * Web MVC配置类
 * 
 * 配置静态资源映射和页面路由，解决context-path为/api时的静态资源访问问题
 * 
 * @author liubo
 * @date 2025-01-21
 */
@Configuration
public class WebMvcConfig implements WebMvcConfigurer {

    /**
     * 配置静态资源处理器
     * 将/admin/**路径映射到classpath:/static/admin/目录
     * 这样可以直接通过http://localhost:8080/api/admin访问管理系统
     */
    @Override
    public void addResourceHandlers(ResourceHandlerRegistry registry) {
        // 配置admin管理系统静态资源
        registry.addResourceHandler("/admin/**")
                .addResourceLocations("classpath:/static/admin/")
                .setCachePeriod(3600); // 缓存1小时
        
        // 配置根路径静态资源（保持原有功能）
        registry.addResourceHandler("/**")
                .addResourceLocations("classpath:/static/")
                .setCachePeriod(3600);
    }

    /**
     * 配置视图控制器
     * 将特定路径映射到对应的HTML页面
     */
    @Override
    public void addViewControllers(ViewControllerRegistry registry) {
        // 将/admin路径重定向到/admin/index.html
        registry.addRedirectViewController("/admin", "/admin/index.html");
        
        // 将/admin/路径重定向到/admin/index.html
        registry.addRedirectViewController("/admin/", "/admin/index.html");
    }
}