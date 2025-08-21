package com.telegram.server.dto;

import java.util.List;

/**
 * 分页响应数据传输对象
 * 
 * 用于Web管理系统中分页查询的响应数据封装。
 * 
 * @author liubo
 * @date 2025-01-21
 * @param <T> 数据类型
 */
public class PageResponseDTO<T> {
    
    /**
     * 当前页数据列表
     */
    private List<T> content;
    
    /**
     * 当前页码（从0开始）
     */
    private int page;
    
    /**
     * 每页大小
     */
    private int size;
    
    /**
     * 总记录数
     */
    private long totalElements;
    
    /**
     * 总页数
     */
    private int totalPages;
    
    /**
     * 是否为第一页
     */
    private boolean first;
    
    /**
     * 是否为最后一页
     */
    private boolean last;
    
    /**
     * 是否有下一页
     */
    private boolean hasNext;
    
    /**
     * 是否有上一页
     */
    private boolean hasPrevious;
    
    // 构造函数
    public PageResponseDTO() {}
    
    public PageResponseDTO(List<T> content, int page, int size, long totalElements) {
        this.content = content;
        this.page = page;
        this.size = size;
        this.totalElements = totalElements;
        this.totalPages = (int) Math.ceil((double) totalElements / size);
        this.first = page == 0;
        this.last = page >= totalPages - 1;
        this.hasNext = page < totalPages - 1;
        this.hasPrevious = page > 0;
    }
    
    // Getter和Setter方法
    public List<T> getContent() {
        return content;
    }
    
    public void setContent(List<T> content) {
        this.content = content;
    }
    
    public int getPage() {
        return page;
    }
    
    public void setPage(int page) {
        this.page = page;
    }
    
    public int getSize() {
        return size;
    }
    
    public void setSize(int size) {
        this.size = size;
    }
    
    public long getTotalElements() {
        return totalElements;
    }
    
    public void setTotalElements(long totalElements) {
        this.totalElements = totalElements;
        this.totalPages = (int) Math.ceil((double) totalElements / size);
        this.last = page >= totalPages;
        this.hasNext = page < totalPages;
    }
    
    public int getTotalPages() {
        return totalPages;
    }
    
    public void setTotalPages(int totalPages) {
        this.totalPages = totalPages;
    }
    
    public boolean isFirst() {
        return first;
    }
    
    public void setFirst(boolean first) {
        this.first = first;
    }
    
    public boolean isLast() {
        return last;
    }
    
    public void setLast(boolean last) {
        this.last = last;
    }
    
    public boolean isHasNext() {
        return hasNext;
    }
    
    public void setHasNext(boolean hasNext) {
        this.hasNext = hasNext;
    }
    
    public boolean isHasPrevious() {
        return hasPrevious;
    }
    
    public void setHasPrevious(boolean hasPrevious) {
        this.hasPrevious = hasPrevious;
    }
    
    /**
     * 获取当前页的记录数
     * 
     * @return 当前页记录数
     */
    public int getNumberOfElements() {
        return content != null ? content.size() : 0;
    }
    
    /**
     * 判断是否为空页
     * 
     * @return 是否为空
     */
    public boolean isEmpty() {
        return content == null || content.isEmpty();
    }
    
    @Override
    public String toString() {
        return "PageResponseDTO{" +
                "page=" + page +
                ", size=" + size +
                ", totalElements=" + totalElements +
                ", totalPages=" + totalPages +
                ", numberOfElements=" + getNumberOfElements() +
                ", first=" + first +
                ", last=" + last +
                ", hasNext=" + hasNext +
                ", hasPrevious=" + hasPrevious +
                '}';
    }
}