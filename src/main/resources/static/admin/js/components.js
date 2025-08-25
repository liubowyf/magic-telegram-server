/**
 * 通用UI组件库
 * @author liubo
 * @date 2025-08-21
 */

// 组件基类
class Component {
    constructor(container) {
        this.container = typeof container === 'string' ? document.querySelector(container) : container;
        this.eventListeners = [];
    }
    
    /**
     * 添加事件监听器
     * @param {HTMLElement} element - 目标元素
     * @param {string} event - 事件类型
     * @param {Function} handler - 事件处理函数
     */
    addEventListener(element, event, handler) {
        element.addEventListener(event, handler);
        this.eventListeners.push({ element, event, handler });
    }
    
    /**
     * 销毁组件，清理事件监听器
     */
    destroy() {
        this.eventListeners.forEach(({ element, event, handler }) => {
            element.removeEventListener(event, handler);
        });
        this.eventListeners = [];
    }
}

// 分页组件
class Pagination extends Component {
    constructor(container, options = {}) {
        super(container);
        this.options = {
            page: 1,
            size: 10,
            total: 0,
            maxButtons: 5,
            onPageChange: () => {},
            ...options
        };
        this.currentPage = this.options.page; // 添加currentPage属性
        this.render();
    }
    
    /**
     * 渲染分页组件
     */
    render() {
        if (!this.container) return;
        
        const totalPages = Math.ceil(this.options.total / this.options.size);
        if (totalPages <= 1) {
            this.container.innerHTML = '';
            return;
        }
        
        let html = '';
        
        // 上一页按钮
        html += `
            <button class="pagination-btn" data-page="${this.options.page - 1}" 
                    ${this.options.page <= 1 ? 'disabled' : ''}>
                <i class="fas fa-chevron-left"></i>
            </button>
        `;
        
        // 页码按钮
        const startPage = Math.max(1, this.options.page - Math.floor(this.options.maxButtons / 2));
        const endPage = Math.min(totalPages, startPage + this.options.maxButtons - 1);
        
        if (startPage > 1) {
            html += `<button class="pagination-btn" data-page="1">1</button>`;
            if (startPage > 2) {
                html += `<span class="pagination-ellipsis">...</span>`;
            }
        }
        
        for (let i = startPage; i <= endPage; i++) {
            html += `
                <button class="pagination-btn ${i === this.options.page ? 'active' : ''}" 
                        data-page="${i}">${i}</button>
            `;
        }
        
        if (endPage < totalPages) {
            if (endPage < totalPages - 1) {
                html += `<span class="pagination-ellipsis">...</span>`;
            }
            html += `<button class="pagination-btn" data-page="${totalPages}">${totalPages}</button>`;
        }
        
        // 下一页按钮
        html += `
            <button class="pagination-btn" data-page="${this.options.page + 1}" 
                    ${this.options.page >= totalPages ? 'disabled' : ''}>
                <i class="fas fa-chevron-right"></i>
            </button>
        `;
        
        // 页面信息
        html += `
            <span class="pagination-info">
                第 ${this.options.page} 页，共 ${totalPages} 页，总计 ${this.options.total} 条
            </span>
        `;
        
        this.container.innerHTML = html;
        
        // 绑定事件
        this.bindEvents();
    }
    
    /**
     * 绑定事件
     */
    bindEvents() {
        const buttons = this.container.querySelectorAll('.pagination-btn:not([disabled])');
        buttons.forEach(button => {
            this.addEventListener(button, 'click', (e) => {
                const page = parseInt(e.target.closest('button').dataset.page);
                if (page !== this.options.page) {
                    // 先更新内部状态
                    this.options.page = page;
                    this.currentPage = page;
                    // 触发页面变化回调
                    this.options.onPageChange(page);
                    // 延迟重新渲染，让数据加载完成后再更新UI
                    // this.render(); // 移除立即渲染，改为在数据加载完成后通过update方法更新
                }
            });
        });
    }
    
    /**
     * 更新分页数据
     * @param {Object} options - 新的选项
     */
    update(options) {
        this.options = { ...this.options, ...options };
        this.currentPage = this.options.page; // 更新currentPage属性
        this.render();
    }
}

// 数据表格组件
class DataTable extends Component {
    constructor(container, options = {}) {
        super(container);
        this.options = {
            columns: [],
            data: [],
            loading: false,
            emptyText: '暂无数据',
            rowActions: [],
            onRowClick: null,
            ...options
        };
        this.render();
    }
    
    /**
     * 渲染表格
     */
    render() {
        if (!this.container) return;
        
        let html = '<table class="data-table">';
        
        // 表头
        html += '<thead><tr>';
        this.options.columns.forEach(column => {
            html += `<th style="${column.width ? `width: ${column.width}` : ''}">${column.title}</th>`;
        });
        if (this.options.rowActions.length > 0) {
            html += '<th style="width: 120px">操作</th>';
        }
        html += '</tr></thead>';
        
        // 表体
        html += '<tbody>';
        if (this.options.loading) {
            html += `
                <tr>
                    <td colspan="${this.options.columns.length + (this.options.rowActions.length > 0 ? 1 : 0)}" 
                        style="text-align: center; padding: 40px;">
                        <div class="spinner" style="margin: 0 auto 10px;"></div>
                        <div>加载中...</div>
                    </td>
                </tr>
            `;
        } else if (this.options.data.length === 0) {
            html += `
                <tr>
                    <td colspan="${this.options.columns.length + (this.options.rowActions.length > 0 ? 1 : 0)}" 
                        style="text-align: center; padding: 40px; color: #999;">
                        ${this.options.emptyText}
                    </td>
                </tr>
            `;
        } else {
            this.options.data.forEach((row, index) => {
                html += `<tr data-index="${index}">`;
                this.options.columns.forEach(column => {
                    let value = this.getCellValue(row, column);
                    if (column.render) {
                        value = column.render(value, row, index);
                    }
                    html += `<td>${value}</td>`;
                });
                
                // 操作列
                if (this.options.rowActions.length > 0) {
                    html += '<td class="actions-cell">';
                    this.options.rowActions.forEach(action => {
                        if (!action.condition || action.condition(row)) {
                            html += `
                                <button class="btn btn-sm ${action.class || 'btn-secondary'}" 
                                        data-action="${action.key}" data-index="${index}" 
                                        title="${action.title || action.text}">
                                    ${action.icon ? `<i class="${action.icon}"></i>` : ''}
                                    ${action.text || ''}
                                </button>
                            `;
                        }
                    });
                    html += '</td>';
                }
                
                html += '</tr>';
            });
        }
        html += '</tbody></table>';
        
        this.container.innerHTML = html;
        
        // 绑定事件
        this.bindEvents();
    }
    
    /**
     * 获取单元格值
     * @param {Object} row - 行数据
     * @param {Object} column - 列配置
     * @returns {any} 单元格值
     */
    getCellValue(row, column) {
        if (column.key.includes('.')) {
            const keys = column.key.split('.');
            let value = row;
            for (const key of keys) {
                value = value?.[key];
            }
            return value;
        }
        return row[column.key];
    }
    
    /**
     * 绑定事件
     */
    bindEvents() {
        // 行点击事件
        if (this.options.onRowClick) {
            const rows = this.container.querySelectorAll('tbody tr[data-index]');
            rows.forEach(row => {
                this.addEventListener(row, 'click', (e) => {
                    if (e.target.closest('.actions-cell')) return;
                    const index = parseInt(row.dataset.index);
                    this.options.onRowClick(this.options.data[index], index);
                });
            });
        }
        
        // 操作按钮事件
        const actionButtons = this.container.querySelectorAll('[data-action]');
        actionButtons.forEach(button => {
            this.addEventListener(button, 'click', (e) => {
                e.stopPropagation();
                const action = e.target.closest('[data-action]').dataset.action;
                const index = parseInt(e.target.closest('[data-action]').dataset.index);
                const actionConfig = this.options.rowActions.find(a => a.key === action);
                if (actionConfig && actionConfig.handler) {
                    actionConfig.handler(this.options.data[index], index);
                }
            });
        });
    }
    
    /**
     * 更新表格数据
     * @param {Array} data - 新数据
     */
    updateData(data) {
        this.options.data = data;
        this.render();
    }
    
    /**
     * 设置加载状态
     * @param {boolean} loading - 是否加载中
     */
    setLoading(loading) {
        this.options.loading = loading;
        this.render();
    }
}

// 模态框组件
class Modal extends Component {
    constructor(options = {}) {
        super(document.getElementById('modal-overlay'));
        this.options = {
            title: '提示',
            content: '',
            showCancel: true,
            cancelText: '取消',
            confirmText: '确认',
            onConfirm: () => {},
            onCancel: () => {},
            ...options
        };
        this.render();
    }
    
    /**
     * 渲染模态框
     */
    render() {
        if (!this.container) return;
        
        const modal = this.container.querySelector('.modal');
        if (!modal) return;
        
        // 设置标题
        const title = modal.querySelector('.modal-title');
        if (title) title.textContent = this.options.title;
        
        // 设置内容
        const body = modal.querySelector('.modal-body');
        if (body) {
            if (typeof this.options.content === 'string') {
                body.innerHTML = this.options.content;
            } else {
                body.innerHTML = '';
                body.appendChild(this.options.content);
            }
        }
        
        // 设置按钮
        const cancelBtn = modal.querySelector('#modal-cancel');
        const confirmBtn = modal.querySelector('#modal-confirm');
        
        if (cancelBtn) {
            cancelBtn.textContent = this.options.cancelText;
            cancelBtn.style.display = this.options.showCancel ? 'inline-flex' : 'none';
        }
        
        if (confirmBtn) {
            confirmBtn.textContent = this.options.confirmText;
        }
        
        this.bindEvents();
    }
    
    /**
     * 绑定事件
     */
    bindEvents() {
        const modal = this.container.querySelector('.modal');
        const closeBtn = modal.querySelector('.modal-close');
        const cancelBtn = modal.querySelector('#modal-cancel');
        const confirmBtn = modal.querySelector('#modal-confirm');
        
        // 关闭按钮
        if (closeBtn) {
            this.addEventListener(closeBtn, 'click', () => this.hide());
        }
        
        // 取消按钮
        if (cancelBtn) {
            this.addEventListener(cancelBtn, 'click', () => {
                this.options.onCancel();
                this.hide();
            });
        }
        
        // 确认按钮
        if (confirmBtn) {
            this.addEventListener(confirmBtn, 'click', () => {
                this.options.onConfirm();
                this.hide();
            });
        }
        
        // 点击遮罩关闭
        this.addEventListener(this.container, 'click', (e) => {
            if (e.target === this.container) {
                this.hide();
            }
        });
        
        // ESC键关闭
        this.addEventListener(document, 'keydown', (e) => {
            if (e.key === 'Escape') {
                this.hide();
            }
        });
    }
    
    /**
     * 显示模态框
     */
    show() {
        if (this.container) {
            this.container.classList.add('active');
            document.body.style.overflow = 'hidden';
        }
    }
    
    /**
     * 隐藏模态框
     */
    hide() {
        if (this.container) {
            this.container.classList.remove('active');
            document.body.style.overflow = '';
        }
        this.destroy();
    }
    
    /**
     * 静态方法：显示确认对话框
     * @param {string} message - 消息内容
     * @param {Function} onConfirm - 确认回调
     * @param {Object} options - 其他选项
     */
    static confirm(message, onConfirm, options = {}) {
        const modal = new Modal({
            title: '确认操作',
            content: `<p style="margin: 0; font-size: 16px;">${message}</p>`,
            onConfirm,
            ...options
        });
        modal.show();
        return modal;
    }
    
    /**
     * 静态方法：显示信息对话框
     * @param {string} message - 消息内容
     * @param {Object} options - 其他选项
     */
    static alert(message, options = {}) {
        const modal = new Modal({
            title: '提示',
            content: `<p style="margin: 0; font-size: 16px;">${message}</p>`,
            showCancel: false,
            ...options
        });
        modal.show();
        return modal;
    }
}

// 表单组件
class Form extends Component {
    constructor(container, options = {}) {
        super(container);
        this.options = {
            fields: [],
            data: {},
            onSubmit: () => {},
            onValidate: () => true,
            ...options
        };
        this.render();
    }
    
    /**
     * 渲染表单
     */
    render() {
        if (!this.container) return;
        
        let html = '';
        
        this.options.fields.forEach(field => {
            html += '<div class="form-group">';
            
            // 标签
            if (field.label) {
                html += `<label for="${field.key}">${field.label}</label>`;
            }
            
            // 输入控件
            const value = this.options.data[field.key] || field.defaultValue || '';
            
            switch (field.type) {
                case 'text':
                case 'email':
                case 'password':
                case 'number':
                    html += `
                        <input type="${field.type}" id="${field.key}" name="${field.key}" 
                               value="${Utils.escapeHtml(value)}" 
                               placeholder="${field.placeholder || ''}" 
                               ${field.required ? 'required' : ''} 
                               ${field.readonly ? 'readonly' : ''}>
                    `;
                    break;
                    
                case 'textarea':
                    html += `
                        <textarea id="${field.key}" name="${field.key}" 
                                  placeholder="${field.placeholder || ''}" 
                                  ${field.required ? 'required' : ''} 
                                  ${field.readonly ? 'readonly' : ''} 
                                  rows="${field.rows || 3}">${Utils.escapeHtml(value)}</textarea>
                    `;
                    break;
                    
                case 'select':
                    html += `<select id="${field.key}" name="${field.key}" ${field.required ? 'required' : ''}>`;
                    if (field.placeholder) {
                        html += `<option value="">${field.placeholder}</option>`;
                    }
                    (field.options || []).forEach(option => {
                        const selected = value === option.value ? 'selected' : '';
                        html += `<option value="${option.value}" ${selected}>${option.label}</option>`;
                    });
                    html += '</select>';
                    break;
                    
                case 'checkbox':
                    const checked = value ? 'checked' : '';
                    html += `
                        <label class="checkbox-label">
                            <input type="checkbox" id="${field.key}" name="${field.key}" 
                                   value="1" ${checked}>
                            ${field.checkboxLabel || ''}
                        </label>
                    `;
                    break;
            }
            
            // 帮助文本
            if (field.help) {
                html += `<small class="form-help">${field.help}</small>`;
            }
            
            html += '</div>';
        });
        
        this.container.innerHTML = html;
        this.bindEvents();
    }
    
    /**
     * 绑定事件
     */
    bindEvents() {
        const form = this.container.closest('form') || this.container;
        
        // 表单提交事件
        this.addEventListener(form, 'submit', (e) => {
            e.preventDefault();
            if (this.validate()) {
                this.options.onSubmit(this.getData());
            }
        });
    }
    
    /**
     * 获取表单数据
     * @returns {Object} 表单数据
     */
    getData() {
        const data = {};
        this.options.fields.forEach(field => {
            const element = this.container.querySelector(`[name="${field.key}"]`);
            if (element) {
                if (field.type === 'checkbox') {
                    data[field.key] = element.checked;
                } else {
                    data[field.key] = element.value;
                }
            }
        });
        return data;
    }
    
    /**
     * 设置表单数据
     * @param {Object} data - 表单数据
     */
    setData(data) {
        this.options.data = { ...this.options.data, ...data };
        this.render();
    }
    
    /**
     * 验证表单
     * @returns {boolean} 是否验证通过
     */
    validate() {
        let isValid = true;
        
        // 清除之前的错误状态
        this.container.querySelectorAll('.form-error').forEach(el => el.remove());
        this.container.querySelectorAll('.error').forEach(el => el.classList.remove('error'));
        
        // 基础验证
        this.options.fields.forEach(field => {
            const element = this.container.querySelector(`[name="${field.key}"]`);
            if (element && field.required && !element.value.trim()) {
                this.showFieldError(element, `${field.label || field.key}不能为空`);
                isValid = false;
            }
        });
        
        // 自定义验证
        if (isValid && this.options.onValidate) {
            const customResult = this.options.onValidate(this.getData());
            if (customResult !== true) {
                isValid = false;
                if (typeof customResult === 'string') {
                    Utils.showNotification(customResult, 'error');
                }
            }
        }
        
        return isValid;
    }
    
    /**
     * 显示字段错误
     * @param {HTMLElement} element - 字段元素
     * @param {string} message - 错误消息
     */
    showFieldError(element, message) {
        element.classList.add('error');
        const errorEl = document.createElement('div');
        errorEl.className = 'form-error';
        errorEl.textContent = message;
        errorEl.style.cssText = 'color: #e74c3c; font-size: 12px; margin-top: 5px;';
        element.parentNode.appendChild(errorEl);
    }
}

// 全局暴露组件
window.Components = {
    Component,
    Pagination,
    DataTable,
    Modal,
    Form
};