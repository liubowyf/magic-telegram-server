package com.telegram.server.util;

import org.springframework.stereotype.Component;
import java.time.*;
import java.time.format.DateTimeFormatter;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

/**
 * 时区转换工具类
 * 提供统一的时区转换处理，确保时间数据的一致性和准确性
 * 
 * @author liubo
 * @date 2025-08-19
 */
@Component
public class TimeZoneUtil {
    
    /**
     * UTC时区常量
     */
    public static final ZoneId UTC_ZONE = ZoneId.of("UTC");
    
    /**
     * 系统默认时区
     */
    public static final ZoneId SYSTEM_ZONE = ZoneId.systemDefault();
    
    /**
     * 中国时区（上海）
     */
    public static final ZoneId CHINA_ZONE = ZoneId.of("Asia/Shanghai");
    
    /**
     * 时区缓存，避免重复创建ZoneId对象
     */
    private static final ConcurrentMap<String, ZoneId> ZONE_CACHE = new ConcurrentHashMap<>();
    
    /**
     * 日期时间格式化器
     */
    private static final DateTimeFormatter ISO_FORMATTER = DateTimeFormatter.ISO_LOCAL_DATE_TIME;
    
    // 默认构造函数，允许Spring实例化
    public TimeZoneUtil() {
        // Spring Bean 构造函数
    }
    
    /**
     * 将Unix时间戳转换为UTC LocalDateTime
     * 这是处理Telegram消息时间的核心方法
     * 
     * @param unixTimestamp Unix时间戳（秒）
     * @return UTC时区的LocalDateTime对象
     * @throws IllegalArgumentException 如果时间戳无效
     */
    public static LocalDateTime convertUnixToUtc(long unixTimestamp) {
        if (unixTimestamp < 0) {
            throw new IllegalArgumentException("Unix timestamp cannot be negative: " + unixTimestamp);
        }
        
        try {
            // 将Unix时间戳转换为Instant，然后转换为UTC的LocalDateTime
            Instant instant = Instant.ofEpochSecond(unixTimestamp);
            return LocalDateTime.ofInstant(instant, UTC_ZONE);
        } catch (Exception e) {
            throw new IllegalArgumentException("Invalid unix timestamp: " + unixTimestamp, e);
        }
    }
    
    /**
     * 将UTC LocalDateTime转换为指定时区的LocalDateTime
     * 用于显示时需要转换到用户本地时区
     * 
     * @param utcTime UTC时区的LocalDateTime
     * @param targetZone 目标时区
     * @return 目标时区的LocalDateTime对象
     * @throws IllegalArgumentException 如果参数无效
     */
    public static LocalDateTime convertUtcToLocal(LocalDateTime utcTime, ZoneId targetZone) {
        if (utcTime == null) {
            throw new IllegalArgumentException("UTC time cannot be null");
        }
        if (targetZone == null) {
            throw new IllegalArgumentException("Target zone cannot be null");
        }
        
        try {
            // 将UTC LocalDateTime转换为ZonedDateTime，然后转换到目标时区
            ZonedDateTime utcZoned = utcTime.atZone(UTC_ZONE);
            ZonedDateTime targetZoned = utcZoned.withZoneSameInstant(targetZone);
            return targetZoned.toLocalDateTime();
        } catch (Exception e) {
            throw new IllegalArgumentException("Failed to convert UTC time to target zone", e);
        }
    }
    
    /**
     * 将本地时区的LocalDateTime转换为UTC LocalDateTime
     * 用于将本地时间转换为UTC存储
     * 
     * @param localTime 本地时区的LocalDateTime
     * @return UTC时区的LocalDateTime对象
     * @throws IllegalArgumentException 如果参数无效
     */
    public static LocalDateTime convertLocalToUtc(LocalDateTime localTime) {
        if (localTime == null) {
            throw new IllegalArgumentException("Local time cannot be null");
        }
        
        try {
            // 将本地LocalDateTime转换为ZonedDateTime，然后转换到UTC时区
            ZonedDateTime localZoned = localTime.atZone(SYSTEM_ZONE);
            ZonedDateTime utcZoned = localZoned.withZoneSameInstant(UTC_ZONE);
            return utcZoned.toLocalDateTime();
        } catch (Exception e) {
            throw new IllegalArgumentException("Failed to convert local time to UTC", e);
        }
    }
    
    /**
     * 将UTC LocalDateTime转换为系统默认时区的LocalDateTime
     * 便捷方法，用于转换到系统默认时区
     * 
     * @param utcTime UTC时区的LocalDateTime
     * @return 系统默认时区的LocalDateTime对象
     */
    public static LocalDateTime convertUtcToSystem(LocalDateTime utcTime) {
        return convertUtcToLocal(utcTime, SYSTEM_ZONE);
    }
    
    /**
     * 将UTC LocalDateTime转换为中国时区的LocalDateTime
     * 便捷方法，用于转换到中国时区
     * 
     * @param utcTime UTC时区的LocalDateTime
     * @return 中国时区的LocalDateTime对象
     */
    public static LocalDateTime convertUtcToChina(LocalDateTime utcTime) {
        return convertUtcToLocal(utcTime, CHINA_ZONE);
    }
    
    /**
     * 获取当前UTC时间
     * 用于设置消息的createdTime字段
     * 
     * @return 当前UTC时间的LocalDateTime对象
     */
    public static LocalDateTime getCurrentUtc() {
        return LocalDateTime.now(UTC_ZONE);
    }
    
    /**
     * 获取当前系统时区时间
     * 
     * @return 当前系统时区的LocalDateTime对象
     */
    public static LocalDateTime getCurrentSystem() {
        return LocalDateTime.now(SYSTEM_ZONE);
    }
    

    

    
    /**
     * 将LocalDateTime转换为Unix时间戳
     * 假设输入的LocalDateTime是UTC时区
     * 
     * @param utcTime UTC时区的LocalDateTime
     * @return Unix时间戳（秒）
     * @throws IllegalArgumentException 如果时间无效
     */
    public static long convertUtcToUnix(LocalDateTime utcTime) {
        if (utcTime == null) {
            throw new IllegalArgumentException("UTC time cannot be null");
        }
        
        try {
            return utcTime.atZone(UTC_ZONE).toEpochSecond();
        } catch (Exception e) {
            throw new IllegalArgumentException("Failed to convert UTC time to unix timestamp", e);
        }
    }
    
    /**
     * 格式化UTC时间为ISO字符串
     * 
     * @param utcTime UTC时区的LocalDateTime
     * @return ISO格式的时间字符串
     */
    public static String formatUtcTime(LocalDateTime utcTime) {
        if (utcTime == null) {
            return null;
        }
        
        try {
            return utcTime.format(ISO_FORMATTER);
        } catch (Exception e) {
            throw new IllegalArgumentException("Failed to format UTC time", e);
        }
    }
    
    /**
     * 解析ISO格式的时间字符串为UTC LocalDateTime
     * 
     * @param timeString ISO格式的时间字符串
     * @return UTC时区的LocalDateTime对象
     * @throws IllegalArgumentException 如果字符串格式无效
     */
    public static LocalDateTime parseUtcTime(String timeString) {
        if (timeString == null || timeString.trim().isEmpty()) {
            throw new IllegalArgumentException("Time string cannot be null or empty");
        }
        
        try {
            return LocalDateTime.parse(timeString.trim(), ISO_FORMATTER);
        } catch (Exception e) {
            throw new IllegalArgumentException("Failed to parse time string: " + timeString, e);
        }
    }
    

    

    

}