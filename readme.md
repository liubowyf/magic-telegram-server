# Magic Telegram Server

一个基于Spring Boot和TDLight-Java的Telegram单账户消息实时监听服务。

## 功能特性

- 🚀 基于Spring Boot 3.2.0构建
- 📱 使用TDLight-Java 3.4.0+td.1.8.26版本
- 👤 单账户管理和认证
- 🔄 实时监听Telegram群组消息
- 🌐 支持SOCKS5代理访问
- 📝 消息实时输出到控制台
- 🔐 自动会话管理和持久化
- 🎯 完整的功能闭环：账户创建 → Session流程 → 消息监听

## 系统要求

- Java 17+
- Maven 3.6+
- macOS/Linux/Windows
- 网络代理（用于访问Telegram服务器）

## 配置说明

### Telegram API配置
- API ID: 请通过 `/telegram/config` 接口配置
- API Hash: 请通过 `/telegram/config` 接口配置
- 手机号: 请通过 `/telegram/auth/phone` 接口配置

### 代理配置
- 类型: SOCKS5
- 地址: 127.0.0.1
- 端口: 7890

## 快速开始

### 1. 编译项目
```bash
mvn clean compile -s settings.xml
```

### 2. 运行应用
```bash
mvn spring-boot:run -s settings.xml
```

### 3. 单账户认证流程
按以下步骤完成单账户的创建和认证：

#### 步骤1: 创建账户
```bash
curl -X POST http://localhost:8080/telegram/account/create
```

#### 步骤2: 配置API信息
```bash
curl -X POST http://localhost:8080/telegram/config \
  -H "Content-Type: application/json" \
  -d '{"appId": YOUR_API_ID, "appHash": "YOUR_API_HASH"}'
```

#### 步骤3: 提交手机号
```bash
curl -X POST http://localhost:8080/telegram/auth/phone \
  -H "Content-Type: application/json" \
  -d '{"phoneNumber": "+8613800138000"}'
```

#### 步骤4: 提交验证码
```bash
curl -X POST http://localhost:8080/telegram/auth/code \
  -H "Content-Type: application/json" \
  -d '{"code": "12345"}'
```

#### 步骤5: 提交密码（如需要）
```bash
curl -X POST http://localhost:8080/telegram/auth/password \
  -H "Content-Type: application/json" \
  -d '{"password": "your_password"}'
```

#### 步骤6: 开始消息监听
```bash
curl -X POST http://localhost:8080/telegram/listening/start
```

## API接口

### 账户管理
- `POST /telegram/account/create` - 创建并初始化账户
- `POST /telegram/config` - 配置API信息
- `DELETE /telegram/session/clear` - 清理Session数据

### 认证流程
- `POST /telegram/auth/phone` - 提交手机号
- `POST /telegram/auth/code` - 提交验证码
- `POST /telegram/auth/password` - 提交两步验证密码
- `GET /telegram/auth/status` - 获取认证状态

### 消息监听
- `POST /telegram/listening/start` - 开始消息监听
- `POST /telegram/listening/stop` - 停止消息监听

### 服务状态
- `GET /telegram/status` - 获取服务状态
- `GET /telegram/health` - 健康检查

## 项目结构

```
magic-telegram-server/
├── src/
│   ├── main/
│   │   ├── java/com/telegram/server/
│   │   │   ├── MagicTelegramServerApplication.java  # 主入口
│   │   │   ├── controller/
│   │   │   │   └── TelegramController.java          # 单账户REST控制器
│   │   │   └── service/
│   │   │       └── TelegramService.java             # 单账户核心服务
│   │   └── resources/
│   │       └── application.yml                      # 配置文件
│   └── test/
├── telegram-session/                                # 会话数据目录
├── logs/                                           # 日志目录
├── pom.xml                                         # Maven配置
└── readme.md                                       # 项目说明
```

## 功能闭环说明

本项目实现了完整的单账户Telegram消息监听功能闭环：

### 1. 单账户创建
- 通过 `/telegram/account/create` 接口初始化账户
- 重置所有运行时配置，准备新的认证流程
- 支持重复创建，自动清理旧的客户端连接

### 2. Session流程
- **API配置**: 设置Telegram API ID和Hash
- **手机号认证**: 提交手机号码，接收验证码
- **验证码验证**: 提交短信验证码进行验证
- **密码验证**: 如开启两步验证，需提交密码
- **Session持久化**: 认证成功后自动保存会话信息

### 3. 消息监听
- 认证完成后可启动实时消息监听
- 支持群组消息的实时接收和处理
- 消息内容实时输出到控制台日志
- 支持启动/停止监听控制

## 注意事项

1. **代理设置**: 确保SOCKS5代理服务正常运行在127.0.0.1:7890
2. **会话持久化**: 认证成功后会在`telegram-session`目录保存会话信息
3. **群组权限**: 确保Telegram账号已加入需要监听的群组
4. **网络连接**: 需要稳定的网络连接和代理服务
5. **单账户模式**: 系统只支持单个账户，创建新账户会清理旧账户数据

## 故障排除

### 连接问题
- 检查代理服务是否正常运行
- 确认代理端口配置正确
- 验证网络连接状态

### 认证问题
- 确认API ID和Hash正确
- 检查手机号格式（需包含国家代码）
- 验证验证码输入是否正确
- 如有两步验证，确保密码正确

### Session问题
- 如认证失败，可使用 `/telegram/session/clear` 清理Session数据
- 清理后需重新进行完整认证流程
- Session数据存储在 `telegram-session` 目录

### 依赖问题
- 清理Maven缓存: `mvn clean`
- 重新下载依赖: `mvn dependency:resolve`
- 检查TDLight依赖是否正确下载
- 确认Java版本为17+

## 使用示例

完整的使用流程示例：

```bash
# 1. 启动应用
mvn spring-boot:run

# 2. 创建账户
curl -X POST http://localhost:8080/telegram/account/create

# 3. 配置API（替换为实际的API ID和Hash）
curl -X POST http://localhost:8080/telegram/config \
  -H "Content-Type: application/json" \
  -d '{"appId": 12345678, "appHash": "abcdef1234567890abcdef1234567890"}'

# 4. 提交手机号
curl -X POST http://localhost:8080/telegram/auth/phone \
  -H "Content-Type: application/json" \
  -d '{"phoneNumber": "+8613800138000"}'

# 5. 提交验证码（替换为实际收到的验证码）
curl -X POST http://localhost:8080/telegram/auth/code \
  -H "Content-Type: application/json" \
  -d '{"code": "12345"}'

# 6. 如需要，提交两步验证密码
curl -X POST http://localhost:8080/telegram/auth/password \
  -H "Content-Type: application/json" \
  -d '{"password": "your_password"}'

# 7. 开始消息监听
curl -X POST http://localhost:8080/telegram/listening/start

# 8. 查看认证状态
curl http://localhost:8080/telegram/auth/status
```

## 作者

- **作者**: liubo
- **日期**: 2025-08-05
- **版本**: 1.0 (单账户模式)

## 许可证

本项目仅供学习和研究使用。