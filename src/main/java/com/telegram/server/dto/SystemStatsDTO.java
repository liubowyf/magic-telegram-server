package com.telegram.server.dto;

import java.time.LocalDateTime;

/**
 * 系统统计信息DTO
 * 
 * 用于Web管理系统中系统统计信息的数据传输，包含账号统计、消息统计、
 * 系统状态等信息，为管理界面提供数据支持。
 * 
 * @author liubo
 * @date 2025-01-21
 */
public class SystemStatsDTO {
    
    /**
     * 总账号数
     */
    private long totalAccounts;
    
    /**
     * 活跃账号数
     */
    private long activeAccounts;
    
    /**
     * 已认证账号数
     */
    private long authenticatedAccounts;
    
    /**
     * 总消息数
     */
    private long totalMessages;
    
    /**
     * 今日消息数
     */
    private long todayMessages;
    
    /**
     * 本周消息数
     */
    private long weekMessages;
    
    /**
     * 本月消息数
     */
    private long monthMessages;
    
    /**
     * 系统启动时间
     */
    private LocalDateTime systemStartTime;
    
    /**
     * 系统运行时长（秒）
     */
    private long systemUptime;
    
    /**
     * 系统状态
     */
    private String systemStatus;
    
    /**
     * 内存使用情况（MB）
     */
    private long memoryUsed;
    
    /**
     * 最大内存（MB）
     */
    private long memoryMax;
    
    /**
     * 内存使用率（百分比）
     */
    private double memoryUsagePercent;
    
    /**
     * 数据库连接状态
     */
    private String databaseStatus;
    
    /**
     * 统计时间
     */
    private LocalDateTime statsTime;
    
    // 构造函数
    public SystemStatsDTO() {
        this.statsTime = LocalDateTime.now();
    }
    
    public SystemStatsDTO(long totalAccounts, long activeAccounts, long authenticatedAccounts,
                         long totalMessages, long todayMessages, long weekMessages, long monthMessages) {
        this();
        this.totalAccounts = totalAccounts;
        this.activeAccounts = activeAccounts;
        this.authenticatedAccounts = authenticatedAccounts;
        this.totalMessages = totalMessages;
        this.todayMessages = todayMessages;
        this.weekMessages = weekMessages;
        this.monthMessages = monthMessages;
    }
    
    // Getter和Setter方法
    public long getTotalAccounts() {
        return totalAccounts;
    }
    
    public void setTotalAccounts(long totalAccounts) {
        this.totalAccounts = totalAccounts;
    }
    
    public long getActiveAccounts() {
        return activeAccounts;
    }
    
    public void setActiveAccounts(long activeAccounts) {
        this.activeAccounts = activeAccounts;
    }
    
    public long getAuthenticatedAccounts() {
        return authenticatedAccounts;
    }
    
    public void setAuthenticatedAccounts(long authenticatedAccounts) {
        this.authenticatedAccounts = authenticatedAccounts;
    }
    
    public long getTotalMessages() {
        return totalMessages;
    }
    
    public void setTotalMessages(long totalMessages) {
        this.totalMessages = totalMessages;
    }
    
    public long getTodayMessages() {
        return todayMessages;
    }
    
    public void setTodayMessages(long todayMessages) {
        this.todayMessages = todayMessages;
    }
    
    public long getWeekMessages() {
        return weekMessages;
    }
    
    public void setWeekMessages(long weekMessages) {
        this.weekMessages = weekMessages;
    }
    
    public long getMonthMessages() {
        return monthMessages;
    }
    
    public void setMonthMessages(long monthMessages) {
        this.monthMessages = monthMessages;
    }
    
    public LocalDateTime getSystemStartTime() {
        return systemStartTime;
    }
    
    public void setSystemStartTime(LocalDateTime systemStartTime) {
        this.systemStartTime = systemStartTime;
    }
    
    public long getSystemUptime() {
        return systemUptime;
    }
    
    public void setSystemUptime(long systemUptime) {
        this.systemUptime = systemUptime;
    }
    
    public String getSystemStatus() {
        return systemStatus;
    }
    
    public void setSystemStatus(String systemStatus) {
        this.systemStatus = systemStatus;
    }
    
    public long getMemoryUsed() {
        return memoryUsed;
    }
    
    public void setMemoryUsed(long memoryUsed) {
        this.memoryUsed = memoryUsed;
    }
    
    public long getMemoryMax() {
        return memoryMax;
    }
    
    public void setMemoryMax(long memoryMax) {
        this.memoryMax = memoryMax;
    }
    
    public double getMemoryUsagePercent() {
        return memoryUsagePercent;
    }
    
    public void setMemoryUsagePercent(double memoryUsagePercent) {
        this.memoryUsagePercent = memoryUsagePercent;
    }
    
    public String getDatabaseStatus() {
        return databaseStatus;
    }
    
    public void setDatabaseStatus(String databaseStatus) {
        this.databaseStatus = databaseStatus;
    }
    
    public LocalDateTime getStatsTime() {
        return statsTime;
    }
    
    public void setStatsTime(LocalDateTime statsTime) {
        this.statsTime = statsTime;
    }
    
    @Override
    public String toString() {
        return "SystemStatsDTO{" +
                "totalAccounts=" + totalAccounts +
                ", activeAccounts=" + activeAccounts +
                ", authenticatedAccounts=" + authenticatedAccounts +
                ", totalMessages=" + totalMessages +
                ", todayMessages=" + todayMessages +
                ", weekMessages=" + weekMessages +
                ", monthMessages=" + monthMessages +
                ", systemStartTime=" + systemStartTime +
                ", systemUptime=" + systemUptime +
                ", systemStatus='" + systemStatus + '\'' +
                ", memoryUsed=" + memoryUsed +
                ", memoryMax=" + memoryMax +
                ", memoryUsagePercent=" + memoryUsagePercent +
                ", databaseStatus='" + databaseStatus + '\'' +
                ", statsTime=" + statsTime +
                '}';
    }
}