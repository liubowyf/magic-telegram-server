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
- 💾 **GridFS智能存储** - 基于MongoDB GridFS的高效Session存储方案
- 🗜️ **数据压缩优化** - 自动GZIP压缩，节省存储空间
- 🔒 **数据完整性校验** - SHA256哈希校验确保数据安全
- ⚡ **智能存储策略** - 根据数据大小自动选择最优存储方式
- 🎯 完整的功能闭环：账户创建 → Session流程 → 消息监听

## 系统要求

- Java 17+
- Maven 3.6+
- MongoDB 4.0+ (用于GridFS存储)
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

### Session存储配置
- 存储策略: GridFS (MongoDB)
- 压缩算法: GZIP
- 分片阈值: 8MB
- 完整性校验: SHA256

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
│   │   │       ├── TelegramService.java             # 单账户核心服务
│   │   │       ├── TelegramSessionService.java      # Session管理服务
│   │   │       ├── GridFSStorageManager.java        # GridFS存储管理器
│   │   │       ├── GridFSService.java               # GridFS核心服务
│   │   │       ├── GridFSCompressionService.java    # GridFS压缩服务
│   │   │       └── GridFSIntegrityService.java      # GridFS完整性服务
│   │   └── resources/
│   │       └── application.yml                      # 配置文件
│   └── test/
├── docs/
│   └── gridfs-migration/                           # GridFS迁移文档
│       ├── ALIGNMENT_gridfs-migration.md           # 需求对齐文档
│       └── DESIGN_gridfs-migration.md              # 架构设计文档
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
- **智能存储**: 根据数据大小自动选择GridFS或传统存储方式
- **数据压缩**: 大于8MB的数据自动进行GZIP压缩
- **完整性校验**: 使用SHA256哈希确保数据完整性

### 3. 消息监听
- 认证完成后可启动实时消息监听
- 支持群组消息的实时接收和处理
- 消息内容实时输出到控制台日志
- 支持启动/停止监听控制

## 注意事项

1. **代理设置**: 确保SOCKS5代理服务正常运行在127.0.0.1:7890
2. **MongoDB服务**: 确保MongoDB 4.0+服务正常运行，用于GridFS存储
3. **会话持久化**: 认证成功后会在MongoDB中保存会话信息，支持GridFS大文件存储
4. **存储策略**: 系统自动根据数据大小选择存储方式，大于8MB使用GridFS
5. **数据压缩**: 大文件自动进行GZIP压缩，节省存储空间
6. **群组权限**: 确保Telegram账号已加入需要监听的群组
7. **网络连接**: 需要稳定的网络连接和代理服务
8. **单账户模式**: 系统只支持单个账户，创建新账户会清理旧账户数据

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
- Session数据存储在MongoDB中，支持GridFS大文件存储
- 检查MongoDB连接状态和GridFS配置

### 存储问题
- 确认MongoDB服务正常运行
- 检查GridFS存储空间是否充足
- 验证数据压缩和完整性校验功能
- 查看存储策略配置是否正确

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

## 1.1 可以通过页面创建账号：http://localhost:8080/api/auth.html

# 2. 创建账户
curl -X POST http://localhost:8080/api/telegram/account/create

# 3. 配置API（替换为实际的API ID和Hash）
curl -X POST http://localhost:8080/api/telegram/config \
  -H "Content-Type: application/json" \
  -d '{"appId": 12345678, "appHash": "abcdef1234567890abcdef1234567890"}'

# 4. 提交手机号
curl -X POST http://localhost:8080/api/telegram/auth/phone \
  -H "Content-Type: application/json" \
  -d '{"phoneNumber": "+8613800138000"}'

# 5. 提交验证码（替换为实际收到的验证码）
curl -X POST http://localhost:8080/api/telegram/auth/code \
  -H "Content-Type: application/json" \
  -d '{"code": "12345"}'

# 6. 如需要，提交两步验证密码
curl -X POST http://localhost:8080/api/telegram/auth/password \
  -H "Content-Type: application/json" \
  -d '{"password": "your_password"}'

# 7. 开始消息监听
curl -X POST http://localhost:8080/api/telegram/listening/start

# 8. 查看认证状态
curl http://localhost:8080/api/telegram/auth/status
```

## 更新日志

### v1.1.0 (2025-01-19) - GridFS存储优化
- ✅ **GridFS智能存储** - 实现基于MongoDB GridFS的高效Session存储方案
- ✅ **数据压缩优化** - 集成GZIP压缩算法，自动压缩大于8MB的数据
- ✅ **完整性校验** - 实现SHA256哈希校验，确保数据完整性和安全性
- ✅ **智能存储策略** - 根据数据大小自动选择最优存储方式
- ✅ **存储架构重构** - 替换自定义分片机制，提升存储性能和可维护性

## TODO 列表

以下是项目的后续开发计划：

- [ ] **多账户管理** - 支持同时管理多个Telegram账户，实现账户间的独立认证和消息监听
- [ ] **消息持久化** - 基于GridFS实现消息历史存储和查询功能
- [ ] **实时消息分类** - 基于机器学习算法实现消息智能分类，支持自定义分类规则和标签管理
- [ ] **存储监控面板** - 实现GridFS存储状态监控和性能分析面板

## 作者

- **作者**: liubo
- **日期**: 2025-08-05
- **版本**: 1.1.0 (GridFS存储优化版)

## 许可证

本项目基于 [Apache License 2.0](LICENSE) 开源协议发布。

```
Copyright 2025 liubo

Licensed under the Apache License, Version 2.0 (the "License");
you may not use this file except in compliance with the License.
You may obtain a copy of the License at

    http://www.apache.org/licenses/LICENSE-2.0

Unless required by applicable law or agreed to in writing, software
distributed under the License is distributed on an "AS IS" BASIS,
WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
See the License for the specific language governing permissions and
limitations under the License.
```