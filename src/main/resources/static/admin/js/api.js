/**
 * API接口管理
 * @author liubo
 * @date 2025-08-21
 */

// API基础配置
const API_CONFIG = {
    baseURL: '/api/admin',
    timeout: 30000,
    headers: {
        'Content-Type': 'application/json'
    }
};

// HTTP请求工具类
class HttpClient {
    /**
     * 发送HTTP请求
     * @param {string} url - 请求URL
     * @param {Object} options - 请求选项
     * @returns {Promise} 请求Promise
     */
    static async request(url, options = {}) {
        const config = {
            method: 'GET',
            headers: { ...API_CONFIG.headers },
            ...options
        };
        
        // 添加认证token（如果存在）
        const token = Utils.storage.get('admin_token');
        if (token) {
            config.headers['Authorization'] = `Bearer ${token}`;
        }
        
        try {
            const response = await fetch(API_CONFIG.baseURL + url, config);
            
            // 检查响应状态
            if (!response.ok) {
                if (response.status === 401) {
                    // 未授权，清除token并显示错误提示
                    Utils.storage.remove('admin_token');
                    alert('会话已过期，请重新认证账号');
                    // 重新加载当前页面，用户可以通过管理系统的认证弹窗重新认证
                    window.location.reload();
                    throw new Error('未授权访问');
                }
                throw new Error(`HTTP ${response.status}: ${response.statusText}`);
            }
            
            // 解析响应数据
            const contentType = response.headers.get('content-type');
            if (contentType && contentType.includes('application/json')) {
                return await response.json();
            } else {
                return await response.text();
            }
        } catch (error) {
            console.error('API请求失败:', error);
            throw error;
        }
    }
    
    /**
     * GET请求
     * @param {string} url - 请求URL
     * @param {Object} params - 查询参数
     * @returns {Promise} 请求Promise
     */
    static async get(url, params = {}) {
        const queryString = new URLSearchParams(params).toString();
        const fullUrl = queryString ? `${url}?${queryString}` : url;
        return this.request(fullUrl);
    }
    
    /**
     * POST请求
     * @param {string} url - 请求URL
     * @param {Object} data - 请求数据
     * @returns {Promise} 请求Promise
     */
    static async post(url, data = {}) {
        return this.request(url, {
            method: 'POST',
            body: JSON.stringify(data)
        });
    }
    
    /**
     * PUT请求
     * @param {string} url - 请求URL
     * @param {Object} data - 请求数据
     * @returns {Promise} 请求Promise
     */
    static async put(url, data = {}) {
        return this.request(url, {
            method: 'PUT',
            body: JSON.stringify(data)
        });
    }
    
    /**
     * DELETE请求
     * @param {string} url - 请求URL
     * @returns {Promise} 请求Promise
     */
    static async delete(url) {
        return this.request(url, {
            method: 'DELETE'
        });
    }
}

// API接口定义
const API = {
    /**
     * 账号管理相关接口
     */
    accounts: {
        /**
         * 获取账号列表（分页）
         * @param {number} page - 页码（从0开始）
         * @param {number} size - 每页大小
         * @param {string} search - 搜索关键词
         * @returns {Promise} 账号列表数据
         */
        async getList(params = {}) {
            const requestData = {
                page: params.page || 0,
                size: params.size || 10
            };
            if (params.search) {
                requestData.search = params.search;
            }
            if (params.status) {
                requestData.status = params.status;
            }
            return HttpClient.post('/accounts/list', requestData);
        },
        
        /**
         * 获取账号详情
         * @param {string} id - 账号ID
         * @returns {Promise} 账号详情数据
         */
        async getById(id) {
            return HttpClient.post(`/accounts/detail`, { accountId: id });
        },
        
        /**
         * 创建新账号
         * @param {Object} accountData - 账号数据
         * @returns {Promise} 创建结果
         */
        async create(accountData) {
            return HttpClient.post('/accounts', accountData);
        },
        
        /**
         * 更新账号信息
         * @param {string} id - 账号ID
         * @param {Object} accountData - 更新数据
         * @returns {Promise} 更新结果
         */
        async update(id, accountData) {
            return HttpClient.put(`/accounts/${id}`, accountData);
        },
        
        /**
         * 删除账号
         * @param {string} id - 账号ID
         * @returns {Promise} 删除结果
         */
        async delete(id) {
            return HttpClient.delete(`/accounts/${id}`);
        },
        
        /**
         * 获取账号统计信息
         * @returns {Promise} 统计数据
         */
        async getStats() {
            return HttpClient.post('/stats', {});
        },
        
        /**
         * 激活账号
         * @param {string} id - 账号ID
         * @returns {Promise} 激活结果
         */
        async activate(id) {
            return HttpClient.post(`/accounts/${id}/activate`);
        },
        
        /**
         * 停用账号
         * @param {string} id - 账号ID
         * @returns {Promise} 停用结果
         */
        async deactivate(id) {
            return HttpClient.post(`/accounts/${id}/deactivate`);
        },
        
        /**
         * 下载账号的session文件数据包
         * @param {string} accountId - 账号ID（手机号）
         * @returns {Promise} session文件数据
         */
        async downloadSessionFiles(accountId) {
            return HttpClient.get(`/accounts/${accountId}/session-files`);
        }
    },
    
    /**
     * 消息管理相关接口
     */
    messages: {
        /**
         * 获取消息列表（分页）
         * @param {number} page - 页码（从0开始）
         * @param {number} size - 每页大小
         * @param {string} search - 搜索关键词
         * @param {string} chatId - 群组ID过滤
         * @param {string} messageType - 消息类型过滤
         * @returns {Promise} 消息列表数据
         */
        async getList(params = {}) {
            const requestData = {
                page: params.page || 0,
                size: params.size || 10
            };
            if (params.search) requestData.search = params.search;
            if (params.chatId) requestData.chatId = params.chatId;
            if (params.messageType || params.type) requestData.messageType = params.messageType || params.type;
            if (params.date) requestData.date = params.date;
            return HttpClient.post('/messages/list', requestData);
        },
        
        /**
         * 获取消息详情
         * @param {string} id - 消息ID
         * @returns {Promise} 消息详情数据
         */
        async getById(id) {
            return HttpClient.post(`/messages/detail`, { messageId: id });
        },
        
        /**
         * 获取消息图片
         * @param {string} id - 消息ID
         * @returns {Promise} 图片数据（Base64）
         */
        async getImage(id) {
            return HttpClient.post(`/messages/image`, { id });
        },
        
        /**
         * 删除消息
         * @param {string} id - 消息ID
         * @returns {Promise} 删除结果
         */
        async delete(id) {
            return HttpClient.delete(`/messages/${id}`);
        },
        
        /**
         * 批量删除消息
         * @param {Array} ids - 消息ID数组
         * @returns {Promise} 删除结果
         */
        async batchDelete(ids) {
            return HttpClient.post('/messages/batch-delete', { ids });
        },
        
        /**
         * 获取消息统计信息
         * @returns {Promise} 统计数据
         */
        async getStats() {
            return HttpClient.get('/messages/stats');
        },
        
        /**
         * 获取消息总数
         * @returns {Promise} 消息总数
         */
        async getCount() {
            return HttpClient.get('/messages/count');
        },
        
        /**
         * 导出消息数据
         * @param {Object} filters - 过滤条件
         * @returns {Promise} 导出结果
         */
        async export(filters = {}) {
            return HttpClient.post('/messages/export', filters);
        }
    },
    
    /**
     * 系统管理相关接口
     */
    system: {
        /**
         * 获取系统状态
         * @returns {Promise} 系统状态数据
         */
        async getStatus() {
            return HttpClient.get('/system/status');
        },
        
        /**
         * 获取系统配置
         * @returns {Promise} 系统配置数据
         */
        async getConfig() {
            return HttpClient.get('/system/config');
        },
        
        /**
         * 更新系统配置
         * @param {Object} config - 配置数据
         * @returns {Promise} 更新结果
         */
        async updateConfig(config) {
            return HttpClient.put('/system/config', config);
        },
        
        /**
         * 清理系统缓存
         * @returns {Promise} 清理结果
         */
        async clearCache() {
            return HttpClient.post('/system/clear-cache');
        },
        
        /**
         * 获取系统日志
         * @param {number} page - 页码
         * @param {number} size - 每页大小
         * @param {string} level - 日志级别
         * @returns {Promise} 日志数据
         */
        async getLogs(page = 0, size = 50, level = '') {
            const params = { page, size };
            if (level) params.level = level;
            return HttpClient.get('/system/logs', params);
        }
    },
    
    /**
     * 认证相关接口
     */
    auth: {
        /**
         * 管理员登录
         * @param {string} username - 用户名
         * @param {string} password - 密码
         * @returns {Promise} 登录结果
         */
        async login(username, password) {
            return HttpClient.post('/auth/login', { username, password });
        },
        
        /**
         * 管理员登出
         * @returns {Promise} 登出结果
         */
        async logout() {
            return HttpClient.post('/auth/logout');
        },
        
        /**
         * 验证token有效性
         * @returns {Promise} 验证结果
         */
        async validateToken() {
            return HttpClient.get('/auth/validate');
        },
        
        /**
         * 刷新token
         * @returns {Promise} 新token
         */
        async refreshToken() {
            return HttpClient.post('/auth/refresh');
        }
    },
    
    /**
     * 仪表盘相关接口
     */
    dashboard: {
        /**
         * 获取仪表盘统计数据
         * @returns {Promise} 统计数据
         */
        async getStats() {
            return HttpClient.post('/stats');
        },
        
        /**
         * 获取最近活动
         * @param {number} limit - 限制数量
         * @returns {Promise} 活动数据
         */
        async getRecentActivity(limit = 10) {
            return HttpClient.get('/dashboard/recent-activity', { limit });
        },
        
        /**
         * 获取图表数据
         * @param {string} type - 图表类型
         * @param {string} period - 时间周期
         * @returns {Promise} 图表数据
         */
        async getChartData(type, period = '7d') {
            return HttpClient.get('/dashboard/chart', { type, period });
        }
    }
};

// 请求拦截器 - 显示加载状态
let loadingCount = 0;
const originalRequest = HttpClient.request;

HttpClient.request = async function(url, options = {}) {
    // 显示加载指示器
    if (loadingCount === 0) {
        const loading = document.getElementById('loading');
        if (loading) {
            loading.classList.add('active');
        }
    }
    loadingCount++;
    
    try {
        const result = await originalRequest.call(this, url, options);
        return result;
    } catch (error) {
        // 显示错误通知
        if (error.message !== '未授权访问') {
            Utils.showNotification(`请求失败: ${error.message}`, 'error');
        }
        throw error;
    } finally {
        // 隐藏加载指示器
        loadingCount--;
        if (loadingCount === 0) {
            const loading = document.getElementById('loading');
            if (loading) {
                loading.classList.remove('active');
            }
        }
    }
};

// 全局暴露API对象
window.API = API;
window.HttpClient = HttpClient;