/**
 * 工具函数库
 * @author liubo
 * @date 2025-08-21
 */

// 工具函数命名空间
const Utils = {
    /**
     * 格式化日期时间
     * @param {string|Date} date - 日期对象或字符串
     * @param {string} format - 格式化模式，默认 'YYYY-MM-DD HH:mm:ss'
     * @returns {string} 格式化后的日期字符串
     */
    formatDateTime(date, format = 'YYYY-MM-DD HH:mm:ss') {
        if (!date) return '-';
        
        const d = new Date(date);
        if (isNaN(d.getTime())) return '-';
        
        const year = d.getFullYear();
        const month = String(d.getMonth() + 1).padStart(2, '0');
        const day = String(d.getDate()).padStart(2, '0');
        const hours = String(d.getHours()).padStart(2, '0');
        const minutes = String(d.getMinutes()).padStart(2, '0');
        const seconds = String(d.getSeconds()).padStart(2, '0');
        
        return format
            .replace('YYYY', year)
            .replace('MM', month)
            .replace('DD', day)
            .replace('HH', hours)
            .replace('mm', minutes)
            .replace('ss', seconds);
    },
    
    /**
     * 格式化相对时间（如：2小时前）
     * @param {string|Date} date - 日期对象或字符串
     * @returns {string} 相对时间字符串
     */
    formatRelativeTime(date) {
        if (!date) return '-';
        
        const d = new Date(date);
        if (isNaN(d.getTime())) return '-';
        
        const now = new Date();
        const diff = now.getTime() - d.getTime();
        const seconds = Math.floor(diff / 1000);
        const minutes = Math.floor(seconds / 60);
        const hours = Math.floor(minutes / 60);
        const days = Math.floor(hours / 24);
        
        if (days > 0) {
            return `${days}天前`;
        } else if (hours > 0) {
            return `${hours}小时前`;
        } else if (minutes > 0) {
            return `${minutes}分钟前`;
        } else {
            return '刚刚';
        }
    },
    
    /**
     * 格式化文件大小
     * @param {number} bytes - 字节数
     * @returns {string} 格式化后的文件大小
     */
    formatFileSize(bytes) {
        if (!bytes || bytes === 0) return '0 B';
        
        const k = 1024;
        const sizes = ['B', 'KB', 'MB', 'GB', 'TB'];
        const i = Math.floor(Math.log(bytes) / Math.log(k));
        
        return parseFloat((bytes / Math.pow(k, i)).toFixed(2)) + ' ' + sizes[i];
    },
    
    /**
     * 格式化数字（添加千分位分隔符）
     * @param {number} num - 数字
     * @returns {string} 格式化后的数字字符串
     */
    formatNumber(num) {
        if (num === null || num === undefined) return '0';
        return num.toString().replace(/\B(?=(\d{3})+(?!\d))/g, ',');
    },
    
    /**
     * 防抖函数
     * @param {Function} func - 要防抖的函数
     * @param {number} wait - 等待时间（毫秒）
     * @returns {Function} 防抖后的函数
     */
    debounce(func, wait) {
        let timeout;
        return function executedFunction(...args) {
            const later = () => {
                clearTimeout(timeout);
                func(...args);
            };
            clearTimeout(timeout);
            timeout = setTimeout(later, wait);
        };
    },
    
    /**
     * 节流函数
     * @param {Function} func - 要节流的函数
     * @param {number} limit - 限制时间（毫秒）
     * @returns {Function} 节流后的函数
     */
    throttle(func, limit) {
        let inThrottle;
        return function() {
            const args = arguments;
            const context = this;
            if (!inThrottle) {
                func.apply(context, args);
                inThrottle = true;
                setTimeout(() => inThrottle = false, limit);
            }
        };
    },
    
    /**
     * 深拷贝对象
     * @param {any} obj - 要拷贝的对象
     * @returns {any} 拷贝后的对象
     */
    deepClone(obj) {
        if (obj === null || typeof obj !== 'object') return obj;
        if (obj instanceof Date) return new Date(obj.getTime());
        if (obj instanceof Array) return obj.map(item => this.deepClone(item));
        if (typeof obj === 'object') {
            const clonedObj = {};
            for (const key in obj) {
                if (obj.hasOwnProperty(key)) {
                    clonedObj[key] = this.deepClone(obj[key]);
                }
            }
            return clonedObj;
        }
    },
    
    /**
     * 生成随机ID
     * @param {number} length - ID长度，默认8位
     * @returns {string} 随机ID
     */
    generateId(length = 8) {
        const chars = 'ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789';
        let result = '';
        for (let i = 0; i < length; i++) {
            result += chars.charAt(Math.floor(Math.random() * chars.length));
        }
        return result;
    },
    
    /**
     * 验证手机号格式
     * @param {string} phone - 手机号
     * @returns {boolean} 是否有效
     */
    validatePhone(phone) {
        const phoneRegex = /^[+]?[1-9]\d{1,14}$/;
        return phoneRegex.test(phone);
    },
    
    /**
     * 验证邮箱格式
     * @param {string} email - 邮箱地址
     * @returns {boolean} 是否有效
     */
    validateEmail(email) {
        const emailRegex = /^[^\s@]+@[^\s@]+\.[^\s@]+$/;
        return emailRegex.test(email);
    },
    
    /**
     * 截断文本
     * @param {string} text - 原始文本
     * @param {number} maxLength - 最大长度
     * @param {string} suffix - 后缀，默认'...'
     * @returns {string} 截断后的文本
     */
    truncateText(text, maxLength, suffix = '...') {
        if (!text || text.length <= maxLength) return text || '';
        return text.substring(0, maxLength) + suffix;
    },
    
    /**
     * 转义HTML字符
     * @param {string} text - 原始文本
     * @returns {string} 转义后的文本
     */
    escapeHtml(text) {
        const div = document.createElement('div');
        div.textContent = text;
        return div.innerHTML;
    },
    
    /**
     * 获取URL参数
     * @param {string} name - 参数名
     * @returns {string|null} 参数值
     */
    getUrlParam(name) {
        const urlParams = new URLSearchParams(window.location.search);
        return urlParams.get(name);
    },
    
    /**
     * 设置URL参数
     * @param {string} name - 参数名
     * @param {string} value - 参数值
     */
    setUrlParam(name, value) {
        const url = new URL(window.location);
        url.searchParams.set(name, value);
        window.history.pushState({}, '', url);
    },
    
    /**
     * 本地存储操作
     */
    storage: {
        /**
         * 设置本地存储
         * @param {string} key - 键名
         * @param {any} value - 值
         */
        set(key, value) {
            try {
                localStorage.setItem(key, JSON.stringify(value));
            } catch (e) {
                console.error('设置本地存储失败:', e);
            }
        },
        
        /**
         * 获取本地存储
         * @param {string} key - 键名
         * @param {any} defaultValue - 默认值
         * @returns {any} 存储的值
         */
        get(key, defaultValue = null) {
            try {
                const item = localStorage.getItem(key);
                return item ? JSON.parse(item) : defaultValue;
            } catch (e) {
                console.error('获取本地存储失败:', e);
                return defaultValue;
            }
        },
        
        /**
         * 删除本地存储
         * @param {string} key - 键名
         */
        remove(key) {
            try {
                localStorage.removeItem(key);
            } catch (e) {
                console.error('删除本地存储失败:', e);
            }
        },
        
        /**
         * 清空本地存储
         */
        clear() {
            try {
                localStorage.clear();
            } catch (e) {
                console.error('清空本地存储失败:', e);
            }
        }
    },
    
    /**
     * 显示通知消息
     * @param {string} message - 消息内容
     * @param {string} type - 消息类型：success, error, warning, info
     * @param {number} duration - 显示时长（毫秒），默认3000
     */
    showNotification(message, type = 'info', duration = 3000) {
        // 创建通知容器（如果不存在）
        let container = document.getElementById('notification-container');
        if (!container) {
            container = document.createElement('div');
            container.id = 'notification-container';
            container.style.cssText = `
                position: fixed;
                top: 20px;
                right: 20px;
                z-index: 10000;
                pointer-events: none;
            `;
            document.body.appendChild(container);
        }
        
        // 创建通知元素
        const notification = document.createElement('div');
        notification.style.cssText = `
            background: ${this.getNotificationColor(type)};
            color: white;
            padding: 12px 20px;
            border-radius: 8px;
            margin-bottom: 10px;
            box-shadow: 0 4px 15px rgba(0,0,0,0.2);
            transform: translateX(100%);
            transition: all 0.3s ease;
            pointer-events: auto;
            cursor: pointer;
            max-width: 300px;
            word-wrap: break-word;
        `;
        notification.textContent = message;
        
        // 添加到容器
        container.appendChild(notification);
        
        // 显示动画
        setTimeout(() => {
            notification.style.transform = 'translateX(0)';
        }, 10);
        
        // 点击关闭
        notification.addEventListener('click', () => {
            this.removeNotification(notification);
        });
        
        // 自动关闭
        setTimeout(() => {
            this.removeNotification(notification);
        }, duration);
    },
    
    /**
     * 获取通知颜色
     * @param {string} type - 通知类型
     * @returns {string} 颜色值
     */
    getNotificationColor(type) {
        const colors = {
            success: '#27ae60',
            error: '#e74c3c',
            warning: '#f39c12',
            info: '#3498db'
        };
        return colors[type] || colors.info;
    },
    
    /**
     * 移除通知
     * @param {HTMLElement} notification - 通知元素
     */
    removeNotification(notification) {
        notification.style.transform = 'translateX(100%)';
        notification.style.opacity = '0';
        setTimeout(() => {
            if (notification.parentNode) {
                notification.parentNode.removeChild(notification);
            }
        }, 300);
    }
};

// 全局暴露工具函数
window.Utils = Utils;