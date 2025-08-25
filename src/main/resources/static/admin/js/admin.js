/**
 * Telegram管理系统主要逻辑
 * @author liubo
 * @date 2025-08-21
 */

// 全局应用对象
const TelegramAdmin = {
    // 当前页面
    currentPage: 'dashboard',
    
    // 组件实例
    components: {
        accountTable: null,
        accountPagination: null,
        messageTable: null,
        messagePagination: null,
        authModal: null
    },
    
    // 数据缓存
    cache: {
        accounts: [],
        messages: [],
        stats: {}
    },
    
    /**
     * 初始化应用
     */
    init() {
        console.log('TelegramAdmin 初始化开始');
        
        // 初始化认证弹窗组件
        this.components.authModal = new AuthModal();
        
        this.initSidebar();
        
        // 确保DOM完全加载后再加载页面数据
        setTimeout(() => {
            console.log('开始加载dashboard页面...');
            // 重置当前页面状态，确保首次加载时能正确执行
            this.currentPage = null;
            this.loadPage('dashboard');
        }, 100);
        
        this.startAutoRefresh();
        
        console.log('TelegramAdmin 初始化完成');
    },
    
    /**
     * 初始化侧边栏
     */
    initSidebar() {
        const menuItems = document.querySelectorAll('.menu-item');
        menuItems.forEach(item => {
            item.addEventListener('click', (e) => {
                e.preventDefault();
                
                // 移除所有活跃状态
                menuItems.forEach(mi => mi.classList.remove('active'));
                // 添加当前项的活跃状态
                item.classList.add('active');
                
                const page = item.dataset.page;
                if (page) {
                    this.loadPage(page);
                }
            });
        });
        
        // 侧边栏切换按钮
        const sidebarToggle = document.querySelector('.sidebar-toggle');
        const sidebar = document.querySelector('.sidebar');
        if (sidebarToggle && sidebar) {
            sidebarToggle.addEventListener('click', () => {
                sidebar.classList.toggle('collapsed');
            });
        }
        
        // 退出登录按钮
        const logoutBtn = document.querySelector('.logout-btn');
        if (logoutBtn) {
            logoutBtn.addEventListener('click', () => {
                this.logout();
            });
        }
    },
    

    
    /**
     * 添加新账号 - 显示Telegram认证弹窗
     */
    showAddAccountAuth() {
        if (!this.components.authModal) {
            this.components.authModal = new AuthModal();
        }
        
        this.components.authModal.show(
            () => {
                // 认证成功回调
                Utils.showNotification('账号添加成功！', 'success');
                // 刷新账号列表
                this.loadAccountsData(true);
            },
            () => {
                // 取消回调
                Utils.showNotification('已取消添加账号', 'info');
            }
        );
    },
    

    
    /**
     * 加载页面
     * @param {string} page - 页面名称
     */
    async loadPage(page) {
        if (this.currentPage === page) return;
        
        this.currentPage = page;
        
        // 更新页面标题
        this.updatePageTitle(page);
        
        // 隐藏所有页面
        const pages = document.querySelectorAll('.page');
        pages.forEach(p => p.classList.remove('active'));
        
        // 显示目标页面
        const targetPage = document.getElementById(`${page}-page`);
        if (targetPage) {
            targetPage.classList.add('active');
        } else {
            console.error(`页面元素未找到: ${page}-page`);
            return;
        }
        
        // 根据页面类型执行相应的初始化
        try {
            switch (page) {
                case 'dashboard':
                    await this.loadDashboardStats();
                    break;
                case 'accounts':
                    // 绑定账号页面事件并加载数据
                    this.bindAccountEvents();
                    await this.loadAccountsData();
                    break;
                case 'messages':
                    // 绑定消息页面事件并加载数据
                    this.bindMessageEvents();
                    await this.loadMessagesData();
                    break;
                case 'settings':
                    // 绑定设置页面事件
                    this.bindSettingsEvents();
                    break;
                default:
                    console.warn(`未知页面类型: ${page}`);
            }
        } catch (error) {
            console.error('加载页面失败:', error);
            if (typeof Utils !== 'undefined' && Utils.showNotification) {
                Utils.showNotification('页面加载失败，请重试', 'error');
            }
        }
    },
    
    /**
     * 更新页面标题
     * @param {string} page - 页面名称
     */
    updatePageTitle(page) {
        const titleElement = document.querySelector('.page-title');
        if (!titleElement) return;
        
        const titleMap = {
            'dashboard': '仪表盘',
            'accounts': '账号管理',
            'messages': '消息管理',
            'settings': '系统设置'
        };
        
        titleElement.textContent = titleMap[page] || '未知页面';
    },
    
    /**
     * 退出登录
     */
    logout() {
        Components.Modal.confirm(
            '确定要退出登录吗？',
            () => {
                Utils.removeStorage('admin_token');
                location.reload();
            }
        );
    },
    
    /**
     * 加载仪表盘
     * @param {HTMLElement} container - 容器元素
     */
    async loadDashboard(container) {
        container.innerHTML = `
            <div class="page-header">
                <h1>仪表盘</h1>
                <p>系统概览和统计信息</p>
            </div>
            
            <div class="stats-grid" id="stats-grid">
                <div class="stat-card">
                    <div class="stat-icon">
                        <i class="fas fa-users"></i>
                    </div>
                    <div class="stat-content">
                        <div class="stat-number" id="total-accounts">0</div>
                        <div class="stat-label">总账号数</div>
                    </div>
                </div>
                
                <div class="stat-card">
                    <div class="stat-icon">
                        <i class="fas fa-user-check"></i>
                    </div>
                    <div class="stat-content">
                        <div class="stat-number" id="active-accounts">0</div>
                        <div class="stat-label">活跃账号</div>
                    </div>
                </div>
                
                <div class="stat-card">
                    <div class="stat-icon">
                        <i class="fas fa-comments"></i>
                    </div>
                    <div class="stat-content">
                        <div class="stat-number" id="total-messages">0</div>
                        <div class="stat-label">总消息数</div>
                    </div>
                </div>
                
                <div class="stat-card">
                    <div class="stat-icon">
                        <i class="fas fa-comment"></i>
                    </div>
                    <div class="stat-content">
                        <div class="stat-number" id="today-messages">0</div>
                        <div class="stat-label">今日消息</div>
                    </div>
                </div>
            </div>
            
            <div class="dashboard-charts">
                <div class="chart-card">
                    <h3>最近7天消息统计</h3>
                    <div id="message-chart" class="chart-placeholder">
                        <div class="spinner"></div>
                        <div>加载图表中...</div>
                    </div>
                </div>
                
                <div class="chart-card">
                    <h3>账号状态分布</h3>
                    <div id="account-chart" class="chart-placeholder">
                        <div class="spinner"></div>
                        <div>加载图表中...</div>
                    </div>
                </div>
            </div>
        `;
        
        // 加载统计数据
        await this.loadDashboardStats();
    },
    
    /**
     * 加载仪表盘统计数据
     */
    async loadDashboardStats() {
        console.log('开始执行loadDashboardStats');
        try {
            const response = await API.dashboard.getStats();
            console.log('API响应:', response);
            console.log('response.data:', response.data);
            
            if (response.success && response.data) {
                const stats = response.data;
                console.log('提取的stats数据:', stats);
                
                // 缓存统计数据
                this.cache.stats = stats;
                
                // 通过ID选择器更新统计数据
                const totalAccountsEl = document.getElementById('total-accounts');
                const activeAccountsEl = document.getElementById('active-accounts');
                const totalMessagesEl = document.getElementById('total-messages');
                const todayMessagesEl = document.getElementById('today-messages');
                
                console.log('检查统计元素:');
                console.log('- total-accounts元素:', totalAccountsEl ? '存在' : '不存在');
                console.log('- active-accounts元素:', activeAccountsEl ? '存在' : '不存在');
                console.log('- total-messages元素:', totalMessagesEl ? '存在' : '不存在');
                console.log('- today-messages元素:', todayMessagesEl ? '存在' : '不存在');
                
                if (totalAccountsEl) totalAccountsEl.textContent = stats.totalAccounts || 0;
                if (activeAccountsEl) activeAccountsEl.textContent = stats.activeAccounts || 0;
                if (totalMessagesEl) totalMessagesEl.textContent = stats.totalMessages || 0;
                if (todayMessagesEl) todayMessagesEl.textContent = stats.todayMessages || 0;
                
                console.log('更新统计数据:');
                console.log('- 总账号数:', stats.totalAccounts);
                console.log('- 活跃账号:', stats.activeAccounts);
                console.log('- 总消息数:', stats.totalMessages);
                console.log('- 今日消息:', stats.todayMessages);
                
                console.log('仪表盘统计数据更新完成');
                this.loadCharts(stats);
            } else {
                console.error('API响应失败或无数据:', response);
            }
        } catch (error) {
            console.error('loadDashboardStats执行错误:', error);
        }
    },
    
    /**
     * 加载图表
     */
    async loadCharts() {
        // 这里可以集成图表库如Chart.js或ECharts
        // 暂时显示占位内容
        const messageChart = document.getElementById('message-chart');
        const accountChart = document.getElementById('account-chart');
        
        if (messageChart) {
            messageChart.innerHTML = '<div class="chart-placeholder">图表功能开发中...</div>';
        }
        
        if (accountChart) {
            accountChart.innerHTML = '<div class="chart-placeholder">图表功能开发中...</div>';
        }
    },
    
    /**
     * 加载账号管理页面
     * @param {HTMLElement} container - 容器元素
     */
    async loadAccounts(container) {
        container.innerHTML = `
            <div class="page-header">
                <h1>账号管理</h1>
                <div class="page-actions">
                    <button class="btn btn-primary" id="add-account-btn">
                        <i class="fas fa-plus"></i> 添加账号
                    </button>
                </div>
            </div>
            
            <div class="page-filters">
                <div class="search-box">
                    <input type="text" id="account-search" placeholder="搜索手机号或昵称...">
                    <i class="fas fa-search"></i>
                </div>
                
                <select id="account-status-filter">
                    <option value="">全部状态</option>
                    <option value="ACTIVE">活跃</option>
                    <option value="INACTIVE">未激活</option>
                    <option value="BANNED">已封禁</option>
                </select>
                
                <button class="btn btn-secondary" id="refresh-accounts-btn">
                    <i class="fas fa-sync-alt"></i> 刷新
                </button>
            </div>
            
            <div class="table-container">
                <div id="accounts-table"></div>
            </div>
            
            <div id="accounts-pagination"></div>
        `;
        
        // 绑定事件
        this.bindAccountEvents();
        
        // 加载账号数据
        await this.loadAccountsData();
    },
    
    /**
     * 绑定账号页面事件
     */
    bindAccountEvents() {
        // 移除之前的事件监听器（如果存在）
        this.unbindAccountEvents();
        
        // 添加账号按钮
        const addBtn = document.getElementById('add-account-btn');
        if (addBtn) {
            this.accountEventHandlers = this.accountEventHandlers || {};
            this.accountEventHandlers.addAccount = () => this.showAddAccountAuth();
            addBtn.addEventListener('click', this.accountEventHandlers.addAccount);
        }
        
        // 搜索框
        const searchInput = document.getElementById('account-search');
        if (searchInput) {
            this.accountEventHandlers = this.accountEventHandlers || {};
            let searchTimeout;
            this.accountEventHandlers.search = () => {
                clearTimeout(searchTimeout);
                searchTimeout = setTimeout(() => {
                    this.loadAccountsData();
                }, 500);
            };
            searchInput.addEventListener('input', this.accountEventHandlers.search);
        }
        
        // 状态筛选
        const statusFilter = document.getElementById('account-status-filter');
        if (statusFilter) {
            this.accountEventHandlers = this.accountEventHandlers || {};
            this.accountEventHandlers.statusFilter = () => {
                this.loadAccountsData();
            };
            statusFilter.addEventListener('change', this.accountEventHandlers.statusFilter);
        }
        
        // 刷新按钮
        const refreshBtn = document.getElementById('refresh-accounts-btn');
        if (refreshBtn) {
            this.accountEventHandlers = this.accountEventHandlers || {};
            this.accountEventHandlers.refresh = () => {
                this.loadAccountsData(true);
            };
            refreshBtn.addEventListener('click', this.accountEventHandlers.refresh);
        }
    },
    
    /**
     * 解绑账号页面事件
     */
    unbindAccountEvents() {
        if (!this.accountEventHandlers) return;
        
        const addBtn = document.getElementById('add-account-btn');
        if (addBtn && this.accountEventHandlers.addAccount) {
            addBtn.removeEventListener('click', this.accountEventHandlers.addAccount);
        }
        
        const searchInput = document.getElementById('account-search');
        if (searchInput && this.accountEventHandlers.search) {
            searchInput.removeEventListener('input', this.accountEventHandlers.search);
        }
        
        const statusFilter = document.getElementById('account-status-filter');
        if (statusFilter && this.accountEventHandlers.statusFilter) {
            statusFilter.removeEventListener('change', this.accountEventHandlers.statusFilter);
        }
        
        const refreshBtn = document.getElementById('refresh-accounts-btn');
        if (refreshBtn && this.accountEventHandlers.refresh) {
            refreshBtn.removeEventListener('click', this.accountEventHandlers.refresh);
        }
        
        this.accountEventHandlers = {};
    },
    
    /**
     * 加载账号数据
     * @param {boolean} forceRefresh - 是否强制刷新
     */
    async loadAccountsData(forceRefresh = false) {
        return this.loadAccountsDataWithPage(0, forceRefresh);
    },

    /**
     * 加载指定页码的账号数据
     * @param {number} pageNumber - 页码（从0开始）
     * @param {boolean} forceRefresh - 是否强制刷新
     */
    async loadAccountsDataWithPage(pageNumber = 0, forceRefresh = false) {
        const tableContainer = document.getElementById('accounts-table');
        const paginationContainer = document.getElementById('accounts-pagination');
        
        if (!tableContainer) {
            console.warn('账号表格容器未找到');
            return;
        }
        
        // 获取筛选条件
        const search = document.getElementById('account-search')?.value || '';
        const status = document.getElementById('account-status-filter')?.value || '';
        
        try {
            // 显示加载状态
            if (this.components.accountTable) {
                this.components.accountTable.setLoading(true);
            } else {
                tableContainer.innerHTML = '<div class="loading-message">加载中...</div>';
            }
            
            const params = {
                page: pageNumber, // 使用传入的页码
                size: 10,
                search: search.trim(),
                status: status
            };
            
            const response = await API.accounts.getList(params);
            console.log('API响应数据:', response); // 调试日志
            const accounts = response.data?.content || [];
            this.cache.accounts = accounts;
            
            // 创建或更新表格
            if (!this.components.accountTable) {
                this.components.accountTable = new Components.DataTable(tableContainer, {
                    columns: [
                        { key: 'phoneNumber', title: '手机号', width: '150px' },
                        { key: 'id', title: 'ID', width: '120px' },
                        { 
                            key: 'authStatus', 
                            title: '认证状态', 
                            width: '100px',
                            render: (value) => {
                                const statusMap = {
                                    'READY': '<span class="status-tag status-success">已就绪</span>',
                                    'ACTIVE': '<span class="status-tag status-success">已认证</span>',
                                    'INACTIVE': '<span class="status-tag status-warning">未认证</span>',
                                    'BANNED': '<span class="status-tag status-danger">已封禁</span>'
                                };
                                return statusMap[value] || '<span class="status-tag">未知</span>';
                            }
                        },
                        { 
                            key: 'active', 
                            title: '活跃状态', 
                            width: '100px',
                            render: (value) => value ? 
                                '<span class="status-tag status-success">活跃</span>' : 
                                '<span class="status-tag status-secondary">不活跃</span>'
                        },
                        { 
                            key: 'createdAt', 
                            title: '创建时间', 
                            width: '150px',
                            render: (value) => Utils.formatDateTime(value)
                        }
                    ],
                    data: accounts,
                    rowActions: [
                        {
                            key: 'edit',
                            text: '编辑',
                            icon: 'fas fa-edit',
                            class: 'btn-primary',
                            handler: (row) => this.showEditAccountModal(row)
                        },
                        {
                            key: 'delete',
                            text: '删除',
                            icon: 'fas fa-trash',
                            class: 'btn-danger',
                            handler: (row) => this.deleteAccount(row)
                        }
                    ]
                });
                // 暴露到全局对象以便调试
                window.messageTable = this.components.messageTable;
                window.messagePagination = this.components.messagePagination;
            } else {
                this.components.accountTable.setLoading(false);
                this.components.accountTable.updateData(accounts);
            }
            
            // 创建或更新分页
            if (paginationContainer) {
                if (!this.components.accountPagination) {
                    this.components.accountPagination = new Components.Pagination(
                        paginationContainer,
                        {
                            page: (response.data?.page || 0) + 1, // 转换为1开始的页码
                            size: response.data?.size || 10,
                            total: response.data?.totalElements || 0,
                            onPageChange: (page) => {
                                // 使用当前的筛选条件加载数据
                                const currentSearch = document.getElementById('account-search')?.value || '';
                                const currentStatus = document.getElementById('account-status-filter')?.value || '';
                                this.loadAccountsDataWithPage(page - 1, false);
                            }
                        }
                    );
                    // 暴露到全局对象以便调试
                    window.messagePagination = this.components.messagePagination;
                } else {
                    this.components.accountPagination.update({
                        page: (response.data?.page || 0) + 1,
                        size: response.data?.size || 10,
                        total: response.data?.totalElements || 0
                    });
                }
            }
            
        } catch (error) {
            console.error('加载账号数据失败:', error);
            Utils.showNotification('加载账号数据失败: ' + (error.message || '未知错误'), 'error');
            
            // 显示错误状态
            if (this.components.accountTable) {
                this.components.accountTable.setLoading(false);
            } else {
                tableContainer.innerHTML = '<div class="error-message">加载失败，请重试</div>';
            }
        }
    },
    
    /**
     * 显示添加账号模态框
     */
    showAddAccountModal() {
        // 调用新的认证弹窗来添加账号
        this.showAddAccountAuth();
    },
    
    /**
     * 显示编辑账号模态框
     * @param {Object} account - 账号数据
     */
    showEditAccountModal(account) {
        // 创建增强的账号详情内容
        const content = document.createElement('div');
        content.className = 'account-detail-modal';
        
        content.innerHTML = `
            <div class="account-detail-content">
                <div class="detail-section">
                    <h3>基本信息</h3>
                    <div class="detail-row">
                        <label>手机号:</label>
                        <span class="detail-value">${account.phoneNumber || account.phone || account.id}</span>
                    </div>
                    <div class="detail-row">
                        <label>App ID:</label>
                        <span class="detail-value">${account.apiId || '未设置'}</span>
                    </div>
                    <div class="detail-row">
                        <label>App Hash:</label>
                        <span class="detail-value">${account.apiHash || '未设置'}</span>
                    </div>
                    <div class="detail-row">
                        <label>认证状态:</label>
                        <span class="detail-value status-${(account.authStatus || '').toLowerCase()}">
                            ${this.getAuthStatusText(account.authStatus)}
                        </span>
                    </div>
                    <div class="detail-row">
                        <label>活跃状态:</label>
                        <span class="detail-value ${account.active ? 'active' : 'inactive'}">
                            ${account.active ? '活跃' : '非活跃'}
                        </span>
                    </div>
                </div>
                
                <div class="detail-section">
                    <h3>时间信息</h3>
                    <div class="detail-row">
                        <label>创建时间:</label>
                        <span class="detail-value">${Utils.formatDateTime(account.createdAt)}</span>
                    </div>
                    <div class="detail-row">
                        <label>最后更新:</label>
                        <span class="detail-value">${Utils.formatDateTime(account.lastUpdated)}</span>
                    </div>
                    <div class="detail-row">
                        <label>最后活跃:</label>
                        <span class="detail-value">${Utils.formatDateTime(account.lastActiveAt)}</span>
                    </div>
                </div>
                
                <div class="detail-section">
                    <h3>Session数据文件</h3>
                    <div class="detail-row">
                        <label>数据文件包:</label>
                        <div class="session-file-actions">
                            <button class="btn btn-primary btn-sm" id="download-session-btn">
                                <i class="fas fa-download"></i> 下载Session文件
                            </button>
                            <span class="file-info">包含完整的session数据和配置信息</span>
                        </div>
                    </div>
                </div>
                
                <div class="detail-section">
                    <h3>备注信息</h3>
                    <div class="detail-row">
                        <label>备注:</label>
                        <textarea id="account-remarks" class="form-control" rows="3" placeholder="请输入备注信息...">${account.remarks || ''}</textarea>
                    </div>
                </div>
            </div>
        `;
        
        // 绑定下载按钮事件
        const downloadBtn = content.querySelector('#download-session-btn');
        downloadBtn.addEventListener('click', async () => {
            await this.downloadSessionFiles(account.phoneNumber || account.phone || account.id);
        });
        
        const modal = new Components.Modal({
            title: '账号详情',
            content: content,
            confirmText: '保存备注',
            cancelText: '关闭',
            onConfirm: async () => {
                const remarks = content.querySelector('#account-remarks').value;
                try {
                    await API.accounts.update(account.id, { remarks: remarks });
                    Utils.showNotification('备注保存成功', 'success');
                    modal.hide();
                    this.loadAccountsData(true);
                } catch (error) {
                    Utils.showNotification(error.message || '保存备注失败', 'error');
                }
            }
        });
        
        modal.show();
    },
    
    /**
     * 获取认证状态文本
     * @param {string} status - 认证状态
     * @returns {string} 状态文本
     */
    getAuthStatusText(status) {
        const statusMap = {
            'ACTIVE': '已认证',
            'INACTIVE': '未认证',
            'BANNED': '已封禁',
            'PENDING': '认证中',
            'ERROR': '认证错误'
        };
        return statusMap[status] || '未知状态';
    },
    
    /**
     * 下载账号的session文件
     * @param {string} accountId - 账号ID
     */
    async downloadSessionFiles(accountId) {
        try {
            Utils.showNotification('正在准备下载...', 'info');
            
            const response = await API.accounts.downloadSessionFiles(accountId);
            
            if (response.success) {
                // 创建下载链接
                const dataStr = JSON.stringify(response.data, null, 2);
                const dataBlob = new Blob([dataStr], { type: 'application/json' });
                const url = URL.createObjectURL(dataBlob);
                
                // 创建下载链接并触发下载
                const link = document.createElement('a');
                link.href = url;
                link.download = response.filename || `session_${accountId}_${Date.now()}.json`;
                document.body.appendChild(link);
                link.click();
                document.body.removeChild(link);
                
                // 释放URL对象
                URL.revokeObjectURL(url);
                
                Utils.showNotification('Session文件下载成功', 'success');
            } else {
                Utils.showNotification(response.message || 'Session文件下载失败', 'error');
            }
        } catch (error) {
            console.error('下载Session文件失败:', error);
            Utils.showNotification('下载Session文件失败: ' + error.message, 'error');
        }
    },
    
    /**
     * 删除账号
     * @param {Object} account - 账号数据
     */
    deleteAccount(account) {
        Components.Modal.confirm(
            `确定要删除账号 "${account.phone}" 吗？此操作不可恢复。`,
            async () => {
                try {
                    await API.accounts.delete(account.id);
                    Utils.showNotification('删除账号成功', 'success');
                    this.loadAccountsData(true);
                } catch (error) {
                    Utils.showNotification(error.message || '删除账号失败', 'error');
                }
            }
        );
    },
    
    /**
     * 加载消息管理页面
     * @param {HTMLElement} container - 容器元素
     */
    async loadMessages(container) {
        container.innerHTML = `
            <div class="page-header">
                <h1>消息管理</h1>
                <div class="page-actions">
                    <button class="btn btn-secondary" id="export-messages-btn">
                        <i class="fas fa-download"></i> 导出消息
                    </button>
                </div>
            </div>
            
            <div class="page-filters">
                <div class="search-box">
                    <input type="text" id="message-search" placeholder="搜索消息内容...">
                    <i class="fas fa-search"></i>
                </div>
                
                <select id="message-type-filter">
                    <option value="">全部类型</option>
                    <option value="text">文本</option>
                    <option value="photo">图片</option>
                    <option value="video">视频</option>
                    <option value="document">文档</option>
                </select>
                
                <input type="date" id="message-date-filter">
                
                <button class="btn btn-secondary" id="refresh-messages-btn">
                    <i class="fas fa-sync-alt"></i> 刷新
                </button>
            </div>
            
            <div class="table-container">
                <div id="messages-table"></div>
            </div>
            
            <div id="messages-pagination"></div>
        `;
        
        // 绑定事件
        this.bindMessageEvents();
        
        // 加载消息数据
        await this.loadMessagesData();
    },
    
    /**
     * 绑定消息页面事件
     */
    bindMessageEvents() {
        // 移除之前的事件监听器（如果存在）
        this.unbindMessageEvents();
        
        // 导出按钮
        const exportBtn = document.getElementById('export-messages-btn');
        if (exportBtn) {
            this.messageEventHandlers = this.messageEventHandlers || {};
            this.messageEventHandlers.export = () => this.exportMessages();
            exportBtn.addEventListener('click', this.messageEventHandlers.export);
        }
        
        // 搜索框
        const searchInput = document.getElementById('message-search');
        if (searchInput) {
            this.messageEventHandlers = this.messageEventHandlers || {};
            let searchTimeout;
            this.messageEventHandlers.search = () => {
                clearTimeout(searchTimeout);
                searchTimeout = setTimeout(() => {
                    this.loadMessagesData();
                }, 300);
            };
            searchInput.addEventListener('input', this.messageEventHandlers.search);
        }
        
        // 类型筛选
        const typeFilter = document.getElementById('message-type-filter');
        if (typeFilter) {
            this.messageEventHandlers = this.messageEventHandlers || {};
            this.messageEventHandlers.typeFilter = () => {
                this.loadMessagesData();
            };
            typeFilter.addEventListener('change', this.messageEventHandlers.typeFilter);
        }
        
        // 日期筛选
        const dateFilter = document.getElementById('message-date-filter');
        if (dateFilter) {
            this.messageEventHandlers = this.messageEventHandlers || {};
            this.messageEventHandlers.dateFilter = () => {
                this.loadMessagesData();
            };
            dateFilter.addEventListener('change', this.messageEventHandlers.dateFilter);
        }
        
        // 刷新按钮
        const refreshBtn = document.getElementById('refresh-messages-btn');
        if (refreshBtn) {
            this.messageEventHandlers = this.messageEventHandlers || {};
            this.messageEventHandlers.refresh = () => {
                this.loadMessagesData(true);
            };
            refreshBtn.addEventListener('click', this.messageEventHandlers.refresh);
        }
    },
    
    /**
     * 解绑消息页面事件
     */
    unbindMessageEvents() {
        if (!this.messageEventHandlers) return;
        
        const exportBtn = document.getElementById('export-messages-btn');
        if (exportBtn && this.messageEventHandlers.export) {
            exportBtn.removeEventListener('click', this.messageEventHandlers.export);
        }
        
        const searchInput = document.getElementById('message-search');
        if (searchInput && this.messageEventHandlers.search) {
            searchInput.removeEventListener('input', this.messageEventHandlers.search);
        }
        
        const typeFilter = document.getElementById('message-type-filter');
        if (typeFilter && this.messageEventHandlers.typeFilter) {
            typeFilter.removeEventListener('change', this.messageEventHandlers.typeFilter);
        }
        
        const dateFilter = document.getElementById('message-date-filter');
        if (dateFilter && this.messageEventHandlers.dateFilter) {
            dateFilter.removeEventListener('change', this.messageEventHandlers.dateFilter);
        }
        
        const refreshBtn = document.getElementById('refresh-messages-btn');
        if (refreshBtn && this.messageEventHandlers.refresh) {
            refreshBtn.removeEventListener('click', this.messageEventHandlers.refresh);
        }
        
        this.messageEventHandlers = {};
    },
    
    /**
     * 加载消息数据
     * @param {boolean} forceRefresh - 是否强制刷新
     */
    async loadMessagesData(forceRefresh = false) {
        return this.loadMessagesDataWithPage(0, forceRefresh);
    },

    /**
     * 加载指定页码的消息数据
     * @param {number} pageNumber - 页码（从0开始）
     * @param {boolean} forceRefresh - 是否强制刷新
     */
    async loadMessagesDataWithPage(pageNumber = 0, forceRefresh = false) {
        const tableContainer = document.getElementById('messages-table');
        const paginationContainer = document.getElementById('messages-pagination');
        
        if (!tableContainer) {
            console.warn('消息表格容器未找到');
            return;
        }
        
        // 获取筛选条件
        const search = document.getElementById('message-search')?.value || '';
        const type = document.getElementById('message-type-filter')?.value || '';
        const date = document.getElementById('message-date-filter')?.value || '';
        
        try {
            // 显示加载状态
            if (this.components.messageTable) {
                this.components.messageTable.setLoading(true);
            } else {
                tableContainer.innerHTML = '<div class="loading-message">加载中...</div>';
            }
            
            const params = {
                page: pageNumber, // 使用传入的页码
                size: 10,
                search: search.trim(),
                type: type,
                date: date
            };
            
            const response = await API.messages.getList(params);
            console.log('消息API响应数据:', response);
            
            if (response.success && response.data) {
                console.log('=== 开始更新表格数据 ===');
                const messages = response.data.content || [];
                this.cache.messages = messages;
                
                // 创建或更新表格
                if (!this.components.messageTable) {
                    this.components.messageTable = new Components.DataTable(tableContainer, {
                        loading: false,
                        columns: [
                            { key: 'messageId', title: '消息ID', width: '100px' },
                            { key: 'chatTitle', title: '群组', width: '150px' },
                            { key: 'senderName', title: '发送者', width: '120px' },
                            { 
                                key: 'messageType', 
                                title: '类型', 
                                width: '80px',
                                render: (value) => {
                                    const typeMap = {
                                        'text': '<span class="status-tag">文本</span>',
                                        'photo': '<span class="status-tag status-info">图片</span>',
                                        'video': '<span class="status-tag status-warning">视频</span>',
                                        'document': '<span class="status-tag status-secondary">文档</span>'
                                    };
                                    return typeMap[value] || '<span class="status-tag">其他</span>';
                                }
                            },
                            { 
                                key: 'textContent', 
                                title: '内容', 
                                render: (value) => Utils.truncateText(value || '', 50)
                            },
                            { 
                                key: 'messageTime', 
                                title: '时间', 
                                width: '150px',
                                render: (value) => Utils.formatDateTime(value)
                            }
                        ],
                        data: messages,
                        rowActions: [
                            {
                                key: 'view',
                                text: '查看',
                                icon: 'fas fa-eye',
                                class: 'btn-primary',
                                handler: (row) => this.showMessageDetail(row)
                            },
                            {
                                key: 'delete',
                                text: '删除',
                                icon: 'fas fa-trash',
                                class: 'btn-danger',
                                handler: (row) => this.deleteMessage(row)
                            }
                        ]
                    });
                    // 暴露到全局对象以便调试
                    window.messageTable = this.components.messageTable;
                } else {
                    // 先关闭加载状态，再更新数据
                    this.components.messageTable.setLoading(false);
                    this.components.messageTable.updateData(messages);
                }
                
                // 创建或更新分页
                if (paginationContainer) {
                    if (!this.components.messagePagination) {
                        this.components.messagePagination = new Components.Pagination(
                            paginationContainer,
                            {
                                page: (response.data?.page || 0) + 1, // 转换为1开始的页码
                                size: response.data?.size || 10,
                                total: response.data?.totalElements || 0,
                                onPageChange: (page) => {
                                    // 立即设置表格为加载状态
                                    if (this.components.messageTable) {
                                        this.components.messageTable.setLoading(true);
                                    }
                                    // 使用当前的筛选条件加载数据
                                    this.loadMessagesDataWithPage(page - 1, false);
                                }
                            }
                        );
                        // 暴露到全局对象以便调试
                        window.messagePagination = this.components.messagePagination;
                    } else {
                        this.components.messagePagination.update({
                            page: (response.data?.page || 0) + 1,
                            size: response.data?.size || 10,
                            total: response.data?.totalElements || 0
                        });
                    }
                }
            }
            
        } catch (error) {
            console.error('加载消息数据失败:', error);
            Utils.showNotification('加载消息数据失败: ' + (error.message || '未知错误'), 'error');
            
            // 显示错误状态
            if (this.components.messageTable) {
                this.components.messageTable.setLoading(false);
                // 清空数据并显示错误信息
                this.components.messageTable.updateData([]);
            } else {
                tableContainer.innerHTML = '<div class="error-message">加载失败，请重试</div>';
            }
            
            // 重置分页组件状态
            if (this.components.messagePagination) {
                this.components.messagePagination.update({
                    page: 1,
                    size: 10,
                    total: 0
                });
            }
        }
    },
    
    /**
     * 显示消息详情
     * @param {Object} message - 消息数据
     */
    async showMessageDetail(message) {
        try {
            const response = await API.messages.getById(message.id);
            const detail = response.data; // 修复：从API响应中提取data字段
            
            const content = document.createElement('div');
            content.className = 'message-detail-modal';
            
            content.innerHTML = `
                <div class="message-detail-content">
                    <div class="detail-section">
                        <h3>基本信息</h3>
                        <div class="detail-row">
                            <label>消息ID:</label>
                            <span class="detail-value">${detail.messageId || '未知'}</span>
                        </div>
                        <div class="detail-row">
                            <label>消息类型:</label>
                            <span class="detail-value message-type-${(detail.messageType || '').toLowerCase()}">
                                ${this.getMessageTypeText(detail.messageType)}
                            </span>
                        </div>
                        <div class="detail-row">
                            <label>消息状态:</label>
                            <span class="detail-value">${detail.imageStatus || '正常'}</span>
                        </div>
                    </div>
                    
                    <div class="detail-section">
                        <h3>来源信息</h3>
                        <div class="detail-row">
                            <label>所属群组:</label>
                            <span class="detail-value group-info">
                                ${detail.chatTitle || '私聊消息'}
                                ${detail.chatId ? `<small>(ID: ${detail.chatId})</small>` : ''}
                            </span>
                        </div>
                        <div class="detail-row">
                            <label>发送者:</label>
                            <span class="detail-value sender-info">
                                ${detail.senderName || '未知发送者'}
                                ${detail.senderId ? `<small>(ID: ${detail.senderId})</small>` : ''}
                            </span>
                        </div>
                    </div>
                    
                    <div class="detail-section">
                        <h3>时间信息</h3>
                        <div class="detail-row">
                            <label>发送时间:</label>
                            <span class="detail-value">${detail.messageTime ? Utils.formatDateTime(detail.messageTime) : '未知'}</span>
                        </div>
                        <div class="detail-row">
                            <label>接收时间:</label>
                            <span class="detail-value">${detail.createdAt ? Utils.formatDateTime(detail.createdAt) : '未知'}</span>
                        </div>
                    </div>
                    
                    <div class="detail-section">
                        <h3>消息内容</h3>
                        <div class="detail-row full-width">
                            <label>文本内容:</label>
                            <div class="message-content-display">
                                ${detail.textContent ? Utils.escapeHtml(detail.textContent) : '无文本内容'}
                            </div>
                        </div>
                    </div>
                    
                    ${detail.hasImage ? `
                        <div class="detail-section">
                            <h3>媒体文件</h3>
                            ${detail.imageFilename ? `
                                <div class="detail-row">
                                    <label>文件名:</label>
                                    <span class="detail-value">${Utils.escapeHtml(detail.imageFilename)}</span>
                                </div>
                            ` : ''}
                            ${detail.imageMimeType ? `
                                <div class="detail-row">
                                    <label>文件类型:</label>
                                    <span class="detail-value">${detail.imageMimeType}</span>
                                </div>
                            ` : ''}
                            <div class="detail-row">
                                <label>图片状态:</label>
                                <span class="detail-value">${detail.imageStatus || '未知'}</span>
                            </div>
                        </div>
                    ` : ''}
                    
                    ${detail.rawMessageData ? `
                        <div class="detail-section">
                            <h3>技术信息</h3>
                            <div class="detail-row">
                                <label>原始数据:</label>
                                <span class="detail-value">已存储</span>
                            </div>
                        </div>
                    ` : ''}
                </div>
            `;
            
            const modal = new Components.Modal({
                title: '消息详情',
                content: content,
                showCancel: false,
                confirmText: '关闭',
                size: 'large'
            });
            
            modal.show();
            
        } catch (error) {
            console.error('加载消息详情失败:', error);
            Utils.showNotification('加载消息详情失败: ' + error.message, 'error');
        }
    },
    
    /**
     * 获取消息类型文本
     * @param {string} type - 消息类型
     * @returns {string} 类型文本
     */
    getMessageTypeText(type) {
        const typeMap = {
            'TEXT': '文本消息',
            'PHOTO': '图片消息',
            'VIDEO': '视频消息',
            'AUDIO': '音频消息',
            'VOICE': '语音消息',
            'DOCUMENT': '文档消息',
            'STICKER': '贴纸消息',
            'ANIMATION': '动画消息',
            'LOCATION': '位置消息',
            'CONTACT': '联系人消息',
            'POLL': '投票消息',
            'VENUE': '地点消息',
            'GAME': '游戏消息',
            'INVOICE': '发票消息',
            'SUCCESSFUL_PAYMENT': '支付消息',
            'CONNECTED_WEBSITE': '网站连接',
            'PASSPORT_DATA': '护照数据',
            'PROXIMITY_ALERT_TRIGGERED': '接近提醒',
            'VIDEO_CHAT_STARTED': '视频通话开始',
            'VIDEO_CHAT_ENDED': '视频通话结束',
            'VIDEO_CHAT_PARTICIPANTS_INVITED': '视频通话邀请',
            'WEB_APP_DATA': 'Web应用数据',
            'FORUM_TOPIC_CREATED': '论坛主题创建',
            'FORUM_TOPIC_CLOSED': '论坛主题关闭',
            'FORUM_TOPIC_REOPENED': '论坛主题重开',
            'GENERAL_FORUM_TOPIC_HIDDEN': '论坛主题隐藏',
            'GENERAL_FORUM_TOPIC_UNHIDDEN': '论坛主题显示',
            'WRITE_ACCESS_ALLOWED': '写入权限允许',
            'USER_SHARED': '用户分享',
            'CHAT_SHARED': '聊天分享'
        };
        return typeMap[type] || type || '未知类型';
    },
    
    /**
     * 删除消息
     * @param {Object} message - 消息数据
     */
    deleteMessage(message) {
        Components.Modal.confirm(
            `确定要删除这条消息吗？此操作不可恢复。`,
            async () => {
                try {
                    await API.messages.delete(message.id);
                    Utils.showNotification('删除消息成功', 'success');
                    this.loadMessagesData(true);
                } catch (error) {
                    Utils.showNotification(error.message || '删除消息失败', 'error');
                }
            }
        );
    },
    
    /**
     * 导出消息
     */
    async exportMessages() {
        try {
            Utils.showNotification('正在导出消息...', 'info');
            
            // 获取当前筛选条件
            const search = document.getElementById('message-search')?.value || '';
            const type = document.getElementById('message-type-filter')?.value || '';
            const date = document.getElementById('message-date-filter')?.value || '';
            
            const params = {
                search: search.trim(),
                type: type,
                date: date
            };
            
            const blob = await API.messages.export(params);
            
            // 创建下载链接
            const url = window.URL.createObjectURL(blob);
            const a = document.createElement('a');
            a.href = url;
            a.download = `messages_${Utils.formatDate(new Date())}.xlsx`;
            document.body.appendChild(a);
            a.click();
            document.body.removeChild(a);
            window.URL.revokeObjectURL(url);
            
            Utils.showNotification('导出成功', 'success');
            
        } catch (error) {
            Utils.showNotification('导出失败', 'error');
        }
    },
    
    /**
     * 加载设置页面
     * @param {HTMLElement} container - 容器元素
     */
    async loadSettings(container) {
        container.innerHTML = `
            <div class="page-header">
                <h1>系统设置</h1>
                <p>管理系统配置和参数</p>
            </div>
            
            <div class="settings-container">
                <div class="settings-section">
                    <h3>基本设置</h3>
                    <div class="setting-item">
                        <label>系统名称</label>
                        <input type="text" value="Telegram管理系统" readonly>
                    </div>
                    <div class="setting-item">
                        <label>版本号</label>
                        <input type="text" value="1.0.0" readonly>
                    </div>
                </div>
                
                <div class="settings-section">
                    <h3>数据设置</h3>
                    <div class="setting-item">
                        <label>数据保留天数</label>
                        <input type="number" value="30" min="1" max="365">
                        <small>超过此天数的数据将被自动清理</small>
                    </div>
                    <div class="setting-item">
                        <label>自动备份</label>
                        <label class="checkbox-label">
                            <input type="checkbox" checked>
                            启用自动备份
                        </label>
                    </div>
                </div>
                
                <div class="settings-section">
                    <h3>通知设置</h3>
                    <div class="setting-item">
                        <label>邮件通知</label>
                        <label class="checkbox-label">
                            <input type="checkbox">
                            启用邮件通知
                        </label>
                    </div>
                    <div class="setting-item">
                        <label>短信通知</label>
                        <label class="checkbox-label">
                            <input type="checkbox">
                            启用短信通知
                        </label>
                    </div>
                </div>
                
                <div class="settings-actions">
                    <button class="btn btn-primary" id="save-settings-btn">
                        <i class="fas fa-save"></i> 保存设置
                    </button>
                    <button class="btn btn-secondary" id="reset-settings-btn">
                        <i class="fas fa-undo"></i> 重置
                    </button>
                </div>
            </div>
        `;
        
        // 绑定设置页面事件
        this.bindSettingsEvents();
    },
    
    /**
     * 绑定设置页面事件
     */
    bindSettingsEvents() {
        const saveBtn = document.getElementById('save-settings-btn');
        const resetBtn = document.getElementById('reset-settings-btn');
        
        if (saveBtn) {
            saveBtn.addEventListener('click', () => {
                Utils.showNotification('设置已保存', 'success');
            });
        }
        
        if (resetBtn) {
            resetBtn.addEventListener('click', () => {
                Components.Modal.confirm('确定要重置所有设置吗？', () => {
                    Utils.showNotification('设置已重置', 'success');
                    this.loadSettings(document.querySelector('.content-area'));
                });
            });
        }
    },
    
    /**
     * 开始自动刷新
     */
    startAutoRefresh() {
        // 每5分钟自动刷新当前页面数据
        setInterval(() => {
            if (this.currentPage === 'dashboard') {
                this.loadDashboardStats();
            }
        }, 5 * 60 * 1000);
    }
};

// 页面加载完成后初始化应用
document.addEventListener('DOMContentLoaded', () => {
    TelegramAdmin.init();
});

// 全局暴露
window.TelegramAdmin = TelegramAdmin;