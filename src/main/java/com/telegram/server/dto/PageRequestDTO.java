package com.telegram.server.dto;

/**
 * 分页请求数据传输对象
 * 
 * 用于Web管理系统中分页查询的请求参数封装。
 * 
 * @author liubo
 * @date 2025-01-21
 */
public class PageRequestDTO {
    
    /**
     * 页码（从0开始）
     */
    private int page;
    
    /**
     * 每页大小
     */
    private int size;
    
    /**
     * 排序字段
     */
    private String sortBy;
    
    /**
     * 排序方向（asc/desc）
     */
    private String sortDirection;
    
    // 构造函数
    public PageRequestDTO() {
        this.page = 0;
        this.size = 10;
        this.sortDirection = "desc";
    }
    
    public PageRequestDTO(int page, int size) {
        this.page = Math.max(0, page);
        this.size = Math.max(1, Math.min(100, size)); // 限制每页最大100条
        this.sortDirection = "desc";
    }
    
    public PageRequestDTO(int page, int size, String sortBy, String sortDirection) {
        this.page = Math.max(0, page);
        this.size = Math.max(1, Math.min(100, size));
        this.sortBy = sortBy;
        this.sortDirection = sortDirection != null ? sortDirection : "desc";
    }
    
    // Getter和Setter方法
    public int getPage() {
        return page;
    }
    
    public void setPage(int page) {
        this.page = Math.max(0, page);
    }
    
    public int getSize() {
        return size;
    }
    
    public void setSize(int size) {
        this.size = Math.max(1, Math.min(100, size));
    }
    
    public String getSortBy() {
        return sortBy;
    }
    
    public void setSortBy(String sortBy) {
        this.sortBy = sortBy;
    }
    
    public String getSortDirection() {
        return sortDirection;
    }
    
    public void setSortDirection(String sortDirection) {
        this.sortDirection = sortDirection;
    }
    
    /**
     * 获取跳过的记录数（用于MongoDB的skip操作）
     * 
     * @return 跳过的记录数
     */
    public int getSkip() {
        return page * size;
    }
    
    /**
     * 验证分页参数是否有效
     * 
     * @return 是否有效
     */
    public boolean isValid() {
        return page >= 0 && size > 0 && size <= 100;
    }
    
    @Override
    public String toString() {
        return "PageRequestDTO{" +
                "page=" + page +
                ", size=" + size +
                ", sortBy='" + sortBy + '\'' +
                ", sortDirection='" + sortDirection + '\'' +
                '}';
    }
}