/**
 * 认证弹窗组件
 * 将auth.html的认证功能集成为弹窗形式
 * 
 * @author liubo
 * @date 2025-01-21
 */
class AuthModal {
    /**
     * 构造函数
     */
    constructor() {
        this.currentStep = 1;
        this.maxSteps = 4;
        this.modal = null;
        this.onSuccess = null;
        this.onCancel = null;
        this.init();
    }

    /**
     * 初始化认证弹窗
     */
    init() {
        this.createModal();
        this.bindEvents();
    }

    /**
     * 创建弹窗HTML结构
     */
    createModal() {
        const modalHtml = `
            <div class="modal-overlay" id="auth-modal-overlay">
                <div class="modal-container auth-modal">
                    <div class="modal-header">
                        <h2>🔐 Telegram 授权</h2>
                        <p>请按步骤完成授权验证</p>
                        <button class="modal-close" id="auth-modal-close">&times;</button>
                    </div>
                    
                    <div class="modal-body">
                        <div class="step-indicator">
                            <div class="step active" id="auth-step-1">
                                <div class="step-number">1</div>
                                <div class="step-label">API配置</div>
                            </div>
                            <div class="step" id="auth-step-2">
                                <div class="step-number">2</div>
                                <div class="step-label">手机号</div>
                            </div>
                            <div class="step" id="auth-step-3">
                                <div class="step-number">3</div>
                                <div class="step-label">验证码</div>
                            </div>
                            <div class="step" id="auth-step-4">
                                <div class="step-number">4</div>
                                <div class="step-label">密码</div>
                            </div>
                        </div>
                        
                        <div id="auth-message"></div>
                        
                        <!-- 步骤1: API配置 -->
                        <div class="form-step active" id="auth-form-step-1">
                            <div class="form-group">
                                <label for="auth-appId">App ID:</label>
                                <input type="text" id="auth-appId" placeholder="请输入Telegram App ID">
                                <div class="help-text">从 https://my.telegram.org 获取</div>
                            </div>
                            <div class="form-group">
                                <label for="auth-appHash">App Hash:</label>
                                <input type="text" id="auth-appHash" placeholder="请输入Telegram App Hash">
                                <div class="help-text">从 https://my.telegram.org 获取</div>
                            </div>
                            <button class="btn btn-primary" id="auth-submit-api">下一步</button>
                            <button class="btn btn-secondary" id="auth-reset-session">重置Session</button>
                        </div>
                        
                        <!-- 步骤2: 手机号 -->
                        <div class="form-step" id="auth-form-step-2">
                            <div class="form-group">
                                <label for="auth-phoneNumber">手机号:</label>
                                <input type="tel" id="auth-phoneNumber" placeholder="请输入手机号 (如: +8613812345678)">
                                <div class="help-text">请包含国家代码，如中国号码以+86开头</div>
                            </div>
                            <button class="btn btn-primary" id="auth-submit-phone">发送验证码</button>
                            <button class="btn btn-secondary" id="auth-previous-1">上一步</button>
                        </div>
                        
                        <!-- 步骤3: 验证码 -->
                        <div class="form-step" id="auth-form-step-3">
                            <div class="form-group">
                                <label for="auth-authCode">验证码:</label>
                                <input type="text" id="auth-authCode" placeholder="请输入收到的验证码">
                                <div class="help-text">验证码已发送到您的Telegram账号</div>
                            </div>
                            <button class="btn btn-primary" id="auth-submit-code">验证</button>
                            <button class="btn btn-secondary" id="auth-previous-2">上一步</button>
                        </div>
                        
                        <!-- 步骤4: 二级密码 -->
                        <div class="form-step" id="auth-form-step-4">
                            <div class="form-group">
                                <label for="auth-password">二级密码:</label>
                                <input type="password" id="auth-password" placeholder="请输入Telegram二级密码">
                                <div class="help-text">如果您设置了两步验证，请输入密码</div>
                            </div>
                            <button class="btn btn-primary" id="auth-submit-password">完成验证</button>
                            <button class="btn btn-secondary" id="auth-skip-password">跳过 (无二级密码)</button>
                            <button class="btn btn-secondary" id="auth-previous-3">上一步</button>
                        </div>
                        
                        <!-- 完成页面 -->
                        <div class="form-step" id="auth-form-step-complete">
                            <div class="message success">
                                <h3>🎉 授权完成！</h3>
                                <p>Telegram账号已成功添加到系统中。</p>
                            </div>
                            <button class="btn btn-primary" id="auth-complete-close">关闭</button>
                        </div>
                    </div>
                </div>
            </div>
        `;
        
        // 添加到页面
        document.body.insertAdjacentHTML('beforeend', modalHtml);
        this.modal = document.getElementById('auth-modal-overlay');
    }

    /**
     * 绑定事件
     */
    bindEvents() {
        // 关闭按钮
        document.getElementById('auth-modal-close').addEventListener('click', () => this.close());
        document.getElementById('auth-complete-close').addEventListener('click', () => this.close(true));
        
        // 点击遮罩关闭
        this.modal.addEventListener('click', (e) => {
            if (e.target === this.modal) {
                this.close();
            }
        });
        
        // 步骤按钮
        document.getElementById('auth-submit-api').addEventListener('click', () => this.submitApiConfig());
        document.getElementById('auth-reset-session').addEventListener('click', () => this.resetSession());
        document.getElementById('auth-submit-phone').addEventListener('click', () => this.submitPhoneNumber());
        document.getElementById('auth-submit-code').addEventListener('click', () => this.submitAuthCode());
        document.getElementById('auth-submit-password').addEventListener('click', () => this.submitPassword());
        document.getElementById('auth-skip-password').addEventListener('click', () => this.skipPassword());
        
        // 上一步按钮
        document.getElementById('auth-previous-1').addEventListener('click', () => this.previousStep());
        document.getElementById('auth-previous-2').addEventListener('click', () => this.previousStep());
        document.getElementById('auth-previous-3').addEventListener('click', () => this.previousStep());
        
        // 回车键提交
        this.modal.addEventListener('keypress', (e) => {
            if (e.key === 'Enter') {
                const activeStep = this.modal.querySelector('.form-step.active');
                const button = activeStep.querySelector('.btn-primary');
                if (button) {
                    button.click();
                }
            }
        });
    }

    /**
     * 显示弹窗
     * @param {Function} onSuccess - 成功回调
     * @param {Function} onCancel - 取消回调
     */
    show(onSuccess, onCancel) {
        this.onSuccess = onSuccess;
        this.onCancel = onCancel;
        this.modal.style.display = 'flex';
        document.body.style.overflow = 'hidden';
        
        // 检查当前状态
        this.checkCurrentStatus();
    }

    /**
     * 关闭弹窗
     * @param {boolean} isSuccess - 是否成功完成
     */
    close(isSuccess = false) {
        this.modal.style.display = 'none';
        document.body.style.overflow = '';
        
        if (isSuccess && this.onSuccess) {
            this.onSuccess();
        } else if (!isSuccess && this.onCancel) {
            this.onCancel();
        }
        
        // 重置状态
        this.reset();
    }

    /**
     * 重置弹窗状态
     */
    reset() {
        this.currentStep = 1;
        this.updateStepIndicator();
        this.clearMessage();
        
        // 清空表单
        this.modal.querySelectorAll('input').forEach(input => {
            input.value = '';
        });
    }

    /**
     * 显示消息
     * @param {string} text - 消息文本
     * @param {string} type - 消息类型 (success, error, info)
     */
    showMessage(text, type) {
        const messageDiv = document.getElementById('auth-message');
        messageDiv.innerHTML = `<div class="message ${type}">${text}</div>`;
        
        // 5秒后自动清除
        setTimeout(() => {
            this.clearMessage();
        }, 5000);
    }

    /**
     * 清除消息
     */
    clearMessage() {
        const messageDiv = document.getElementById('auth-message');
        if (messageDiv) {
            messageDiv.innerHTML = '';
        }
    }

    /**
     * 更新步骤指示器
     */
    updateStepIndicator() {
        for (let i = 1; i <= this.maxSteps; i++) {
            const step = document.getElementById(`auth-step-${i}`);
            const formStep = document.getElementById(`auth-form-step-${i}`);
            
            if (step && formStep) {
                step.classList.remove('active', 'completed');
                formStep.classList.remove('active');
                
                if (i < this.currentStep) {
                    step.classList.add('completed');
                } else if (i === this.currentStep) {
                    step.classList.add('active');
                    formStep.classList.add('active');
                }
            }
        }
    }

    /**
     * 下一步
     */
    nextStep() {
        if (this.currentStep < this.maxSteps) {
            this.currentStep++;
            this.updateStepIndicator();
        }
    }

    /**
     * 上一步
     */
    previousStep() {
        if (this.currentStep > 1) {
            this.currentStep--;
            this.updateStepIndicator();
        }
    }

    /**
     * 显示完成页面
     */
    showComplete() {
        document.getElementById('auth-form-step-complete').classList.add('active');
        
        // 隐藏所有步骤
        for (let i = 1; i <= this.maxSteps; i++) {
            const step = document.getElementById(`auth-step-${i}`);
            const formStep = document.getElementById(`auth-form-step-${i}`);
            
            if (formStep) formStep.classList.remove('active');
            if (step) {
                step.classList.add('completed');
                step.classList.remove('active');
            }
        }
    }

    /**
     * 提交API配置
     */
    async submitApiConfig() {
        const appId = document.getElementById('auth-appId').value.trim();
        const appHash = document.getElementById('auth-appHash').value.trim();
        
        if (!appId || !appHash) {
            this.showMessage('请填写完整的App ID和App Hash', 'error');
            return;
        }
        
        try {
            // 先清理现有session
            this.showMessage('正在清理现有session...', 'info');
            await fetch('/api/telegram/session/clear', {
                method: 'DELETE'
            });
            
            // 等待一秒确保清理完成
            await new Promise(resolve => setTimeout(resolve, 1000));
            
            const response = await fetch('/api/telegram/config', {
                method: 'POST',
                headers: {
                    'Content-Type': 'application/json',
                },
                body: JSON.stringify({ appId: parseInt(appId), appHash: appHash })
            });
            
            const result = await response.json();
            if (result.success) {
                this.showMessage('API配置成功', 'success');
                this.nextStep();
            } else {
                this.showMessage(result.message || 'API配置失败', 'error');
            }
        } catch (error) {
            this.showMessage('网络错误: ' + error.message, 'error');
        }
    }

    /**
     * 重置Session
     */
    async resetSession() {
        try {
            this.showMessage('正在重置Session...', 'info');
            const response = await fetch('/api/telegram/session/clear', {
                method: 'DELETE'
            });
            
            const result = await response.json();
            
            if (result.success) {
                this.showMessage('Session重置成功！', 'success');
                // 重置到第一步
                this.currentStep = 1;
                this.updateStepIndicator();
            } else {
                this.showMessage(result.message || 'Session重置失败', 'error');
            }
        } catch (error) {
            this.showMessage('网络错误: ' + error.message, 'error');
        }
    }

    /**
     * 提交手机号
     */
    async submitPhoneNumber() {
        const phoneNumber = document.getElementById('auth-phoneNumber').value.trim();
        
        if (!phoneNumber) {
            this.showMessage('请输入手机号', 'error');
            return;
        }
        
        try {
            // 先检查当前状态
            const statusResponse = await fetch('/api/telegram/status');
            const status = await statusResponse.text();
            
            if (status.includes('AuthorizationStateWaitCode')) {
                // 如果已经在等待验证码，直接跳转
                this.showMessage('验证码已发送，请输入验证码', 'info');
                this.nextStep();
                return;
            }
            
            const response = await fetch('/api/telegram/auth/phone', {
                method: 'POST',
                headers: {
                    'Content-Type': 'application/json',
                },
                body: JSON.stringify({ phoneNumber: phoneNumber })
            });
            
            const result = await response.json();
            if (result.success) {
                this.showMessage('验证码已发送', 'success');
                this.nextStep();
            } else {
                this.showMessage(result.message || '发送验证码失败', 'error');
            }
        } catch (error) {
            this.showMessage('网络错误: ' + error.message, 'error');
        }
    }

    /**
     * 提交验证码
     */
    async submitAuthCode() {
        const code = document.getElementById('auth-authCode').value.trim();
        
        if (!code) {
            this.showMessage('请输入验证码', 'error');
            return;
        }
        
        try {
            const response = await fetch('/api/telegram/auth/code', {
                method: 'POST',
                headers: {
                    'Content-Type': 'application/json',
                },
                body: JSON.stringify({ code: code })
            });
            
            const result = await response.json();
            if (result.success) {
                if (result.needPassword) {
                    this.showMessage('验证码正确，请输入二级密码', 'info');
                    this.nextStep();
                } else {
                    this.showMessage('验证成功！', 'success');
                    this.showComplete();
                }
            } else {
                this.showMessage(result.message || '验证码错误', 'error');
            }
        } catch (error) {
            this.showMessage('网络错误: ' + error.message, 'error');
        }
    }

    /**
     * 提交密码
     */
    async submitPassword() {
        const password = document.getElementById('auth-password').value.trim();
        
        if (!password) {
            this.showMessage('请输入密码', 'error');
            return;
        }
        
        try {
            const response = await fetch('/api/telegram/auth/password', {
                method: 'POST',
                headers: {
                    'Content-Type': 'application/json',
                },
                body: JSON.stringify({ password: password })
            });
            
            const result = await response.json();
            if (result.success) {
                this.showMessage('验证成功！', 'success');
                this.showComplete();
            } else {
                this.showMessage(result.message || '密码错误', 'error');
            }
        } catch (error) {
            this.showMessage('网络错误: ' + error.message, 'error');
        }
    }

    /**
     * 跳过密码
     */
    skipPassword() {
        this.showMessage('已跳过二级密码验证', 'info');
        this.showComplete();
    }

    /**
     * 检查当前授权状态并跳转到正确步骤
     */
    async checkCurrentStatus() {
        try {
            const response = await fetch('/api/telegram/status');
            const status = await response.text();
            
            if (status.includes('AuthorizationStateWaitCode')) {
                // 如果已经在等待验证码，跳转到验证码步骤
                this.currentStep = 3;
                this.showMessage('检测到已发送验证码，请输入验证码', 'info');
            } else if (status.includes('AuthorizationStateWaitPassword')) {
                // 如果在等待密码，跳转到密码步骤
                this.currentStep = 4;
                this.showMessage('检测到需要输入二级密码', 'info');
            } else if (status.includes('AuthorizationStateReady')) {
                // 如果已经授权完成
                this.showComplete();
                return;
            } else if (status.includes('AuthorizationStateWaitPhoneNumber')) {
                // 如果在等待手机号，跳转到手机号步骤
                this.currentStep = 2;
            }
            
            this.updateStepIndicator();
        } catch (error) {
            console.log('无法获取状态，从第一步开始');
            this.updateStepIndicator();
        }
    }

    /**
     * 销毁弹窗
     */
    destroy() {
        if (this.modal) {
            this.modal.remove();
            this.modal = null;
        }
    }
}

// 导出类
window.AuthModal = AuthModal;