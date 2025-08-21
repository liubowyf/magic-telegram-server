package com.telegram.server.dto;

import java.time.LocalDateTime;

/**
 * 账号数据传输对象
 * 
 * 用于Web管理系统中账号信息的数据传输，包含账号的基本信息和状态。
 * 
 * @author liubo
 * @date 2025-01-21
 */
public class AccountDTO {
    
    /**
     * 账号ID
     */
    private String id;
    
    /**
     * 手机号码
     */
    private String phoneNumber;
    
    /**
     * 认证状态
     */
    private String authStatus;
    
    /**
     * 是否活跃
     */
    private boolean active;
    
    /**
     * 创建时间
     */
    private LocalDateTime createdAt;
    
    /**
     * 最后更新时间
     */
    private LocalDateTime lastUpdated;
    
    /**
     * 最后活跃时间
     */
    private LocalDateTime lastActiveAt;
    
    /**
     * API ID
     */
    private Integer apiId;
    
    /**
     * API Hash
     */
    private String apiHash;
    
    /**
     * 备注信息
     */
    private String remarks;
    
    // 构造函数
    public AccountDTO() {}
    
    public AccountDTO(String id, String phoneNumber, String authStatus, boolean active) {
        this.id = id;
        this.phoneNumber = phoneNumber;
        this.authStatus = authStatus;
        this.active = active;
    }
    
    // Getter和Setter方法
    public String getId() {
        return id;
    }
    
    public void setId(String id) {
        this.id = id;
    }
    
    public String getPhoneNumber() {
        return phoneNumber;
    }
    
    public void setPhoneNumber(String phoneNumber) {
        this.phoneNumber = phoneNumber;
    }
    
    public String getAuthStatus() {
        return authStatus;
    }
    
    public void setAuthStatus(String authStatus) {
        this.authStatus = authStatus;
    }
    
    public boolean isActive() {
        return active;
    }
    
    public void setActive(boolean active) {
        this.active = active;
    }
    
    public LocalDateTime getCreatedAt() {
        return createdAt;
    }
    
    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }
    
    public LocalDateTime getLastUpdated() {
        return lastUpdated;
    }
    
    public void setLastUpdated(LocalDateTime lastUpdated) {
        this.lastUpdated = lastUpdated;
    }
    
    public LocalDateTime getLastActiveAt() {
        return lastActiveAt;
    }
    
    public void setLastActiveAt(LocalDateTime lastActiveAt) {
        this.lastActiveAt = lastActiveAt;
    }
    
    public Integer getApiId() {
        return apiId;
    }
    
    public void setApiId(Integer apiId) {
        this.apiId = apiId;
    }
    
    public String getApiHash() {
        return apiHash;
    }
    
    public void setApiHash(String apiHash) {
        this.apiHash = apiHash;
    }
    
    public String getRemarks() {
        return remarks;
    }
    
    public void setRemarks(String remarks) {
        this.remarks = remarks;
    }
    
    @Override
    public String toString() {
        return "AccountDTO{" +
                "id='" + id + '\'' +
                ", phoneNumber='" + phoneNumber + '\'' +
                ", authStatus='" + authStatus + '\'' +
                ", active=" + active +
                ", createdAt=" + createdAt +
                ", lastUpdated=" + lastUpdated +
                ", lastActiveAt=" + lastActiveAt +
                ", apiId=" + apiId +
                ", apiHash='" + apiHash + '\'' +
                ", remarks='" + remarks + '\'' +
                '}';
    }
}