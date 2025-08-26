# Docker 部署指南

本文档详细说明如何使用Docker部署Magic Telegram Server。

## 系统要求

- Docker 20.10+
- Docker Compose 2.0+
- 至少2GB可用内存
- 至少5GB可用磁盘空间

## 部署方式

### 方式一：使用内置MongoDB（推荐）

适用于快速部署和测试环境。

```bash
# 1. 克隆项目
git clone https://github.com/liubowyf/magic-telegram-server.git
cd magic-telegram-server

# 2. 配置环境变量
cp .env.example .env
# 编辑 .env 文件，配置必要的参数

# 3. 启动服务（内置MongoDB）
docker-compose up -d

# 4. 查看服务状态
docker-compose ps

# 5. 查看日志
docker-compose logs -f magic-telegram-server
```

### 方式二：使用外部MongoDB

适用于生产环境，使用独立的MongoDB服务。

```bash
# 1. 克隆项目
git clone https://github.com/liubowyf/magic-telegram-server.git
cd magic-telegram-server

# 2. 配置环境变量
cp .env.external.example .env
# 编辑 .env 文件，配置MongoDB连接信息

# 3. 启动服务（外部MongoDB）
docker-compose -f docker/docker-compose.external.yml up -d

# 4. 查看服务状态
docker-compose -f docker/docker-compose.external.yml ps

# 5. 查看日志
docker-compose -f docker/docker-compose.external.yml logs -f magic-telegram-server
```

## 环境变量配置

### 基础配置

```bash
# 代理配置（非大陆地区可选）
PROXY_ENABLED=true
PROXY_TYPE=SOCKS5
PROXY_HOST=127.0.0.1
PROXY_PORT=7890

# 服务端口
SERVER_PORT=8080
```

> **注意**: Telegram API配置（API_ID和API_HASH）无需在环境变量中设置，完全通过Web管理界面进行配置和管理。

### MongoDB配置（外部MongoDB）

```bash
# MongoDB连接配置
MONGO_HOST=your_mongodb_host
MONGO_PORT=27017
MONGO_DATABASE=magic_telegram
MONGO_USERNAME=your_username
MONGO_PASSWORD=your_password
```

## 服务管理

### 启动服务

```bash
# 内置MongoDB
docker-compose up -d

# 外部MongoDB
docker-compose -f docker/docker-compose.external.yml up -d
```

### 停止服务

```bash
# 内置MongoDB
docker-compose down

# 外部MongoDB
docker-compose -f docker/docker-compose.external.yml down
```

### 重启服务

```bash
# 内置MongoDB
docker-compose restart

# 外部MongoDB
docker-compose -f docker/docker-compose.external.yml restart
```

### 查看日志

```bash
# 查看所有服务日志
docker-compose logs -f

# 查看特定服务日志
docker-compose logs -f magic-telegram-server
docker-compose logs -f mongodb

# 查看最近100行日志
docker-compose logs --tail=100 magic-telegram-server
```

### 进入容器

```bash
# 进入应用容器
docker-compose exec magic-telegram-server bash

# 进入MongoDB容器（仅内置MongoDB）
docker-compose exec mongodb bash
```

## 数据持久化

### 内置MongoDB

数据存储在Docker卷中：
- `mongodb_data`: MongoDB数据文件
- `mongodb_config`: MongoDB配置文件

### 外部MongoDB

数据存储在外部MongoDB服务中，需要确保外部MongoDB服务的数据持久化。

## 网络配置

### 端口映射

- `8080`: Web管理界面和API服务
- `27017`: MongoDB服务（仅内置MongoDB）

### 代理配置

如果需要使用代理访问Telegram服务器，请在`.env`文件中配置：

```bash
PROXY_ENABLED=true
PROXY_TYPE=SOCKS5
PROXY_HOST=host.docker.internal  # Docker Desktop
# 或者
PROXY_HOST=172.17.0.1            # Linux Docker
PROXY_PORT=7890
```

## 健康检查

### 服务状态检查

```bash
# 检查容器状态
docker-compose ps

# 检查服务健康状态
curl http://localhost:8080/actuator/health

# 检查Web管理界面
curl http://localhost:8080/api/admin/index.html
```

### MongoDB连接检查

```bash
# 内置MongoDB
docker-compose exec mongodb mongosh --eval "db.adminCommand('ping')"

# 外部MongoDB（在应用容器中）
docker-compose exec magic-telegram-server bash -c "echo 'db.adminCommand(\"ping\")' | mongosh $MONGO_URI"
```

## 故障排除

### 常见问题

#### 1. 容器启动失败

```bash
# 查看详细错误信息
docker-compose logs magic-telegram-server

# 检查配置文件
docker-compose config

# 重新构建镜像
docker-compose build --no-cache
```

#### 2. MongoDB连接失败

```bash
# 检查MongoDB容器状态
docker-compose ps mongodb

# 查看MongoDB日志
docker-compose logs mongodb

# 测试MongoDB连接
docker-compose exec magic-telegram-server bash -c "mongosh $MONGO_URI --eval 'db.adminCommand(\"ping\")'"
```

#### 3. 代理连接问题

```bash
# 检查代理配置
echo $PROXY_HOST $PROXY_PORT

# 测试代理连接（在容器内）
docker-compose exec magic-telegram-server bash -c "curl -x socks5://$PROXY_HOST:$PROXY_PORT https://api.telegram.org"
```

#### 4. 端口冲突

```bash
# 检查端口占用
netstat -tulpn | grep :8080
lsof -i :8080

# 修改端口映射
# 编辑 docker-compose.yml 中的 ports 配置
```

### 日志分析

```bash
# 查看启动日志
docker-compose logs --tail=50 magic-telegram-server

# 查看错误日志
docker-compose logs magic-telegram-server | grep ERROR

# 实时监控日志
docker-compose logs -f magic-telegram-server | grep -E "(ERROR|WARN|Exception)"
```

## 性能优化

### 资源限制

在`docker-compose.yml`中配置资源限制：

```yaml
services:
  magic-telegram-server:
    deploy:
      resources:
        limits:
          memory: 1G
          cpus: '0.5'
        reservations:
          memory: 512M
          cpus: '0.25'
```

### JVM优化

通过环境变量配置JVM参数：

```bash
# 在 .env 文件中添加
JAVA_OPTS=-Xmx512m -Xms256m -XX:+UseG1GC
```

## 备份与恢复

### 数据备份

```bash
# 备份MongoDB数据（内置MongoDB）
docker-compose exec mongodb mongodump --out /backup
docker cp $(docker-compose ps -q mongodb):/backup ./backup

# 备份Docker卷
docker run --rm -v mongodb_data:/data -v $(pwd):/backup alpine tar czf /backup/mongodb_backup.tar.gz -C /data .
```

### 数据恢复

```bash
# 恢复MongoDB数据
docker cp ./backup $(docker-compose ps -q mongodb):/backup
docker-compose exec mongodb mongorestore /backup

# 恢复Docker卷
docker run --rm -v mongodb_data:/data -v $(pwd):/backup alpine tar xzf /backup/mongodb_backup.tar.gz -C /data
```

## 更新升级

### 应用更新

```bash
# 1. 停止服务
docker-compose down

# 2. 拉取最新代码
git pull origin main

# 3. 重新构建镜像
docker-compose build --no-cache

# 4. 启动服务
docker-compose up -d

# 5. 验证更新
docker-compose logs -f magic-telegram-server
```

### 镜像更新

```bash
# 拉取最新镜像
docker-compose pull

# 重启服务
docker-compose up -d
```

## 监控与维护

### 系统监控

```bash
# 查看容器资源使用情况
docker stats

# 查看磁盘使用情况
docker system df

# 清理未使用的资源
docker system prune -f
```

### 定期维护

```bash
# 清理旧的镜像
docker image prune -f

# 清理未使用的卷
docker volume prune -f

# 清理未使用的网络
docker network prune -f
```

## 安全建议

1. **环境变量安全**：不要在代码中硬编码敏感信息，使用`.env`文件管理
2. **网络安全**：在生产环境中使用防火墙限制端口访问
3. **数据加密**：使用HTTPS和加密的MongoDB连接
4. **定期更新**：定期更新Docker镜像和依赖包
5. **访问控制**：限制容器的权限和网络访问

## 生产环境部署

### 推荐配置

```yaml
# docker-compose.prod.yml
version: '3.8'
services:
  magic-telegram-server:
    image: liubowyf0452/magic-telegram-server:latest
    restart: unless-stopped
    environment:
      - SPRING_PROFILES_ACTIVE=prod
    deploy:
      resources:
        limits:
          memory: 2G
          cpus: '1.0'
    healthcheck:
      test: ["CMD", "curl", "-f", "http://localhost:8080/actuator/health"]
      interval: 30s
      timeout: 10s
      retries: 3
```

### 负载均衡

```yaml
# 使用nginx进行负载均衡
nginx:
  image: nginx:alpine
  ports:
    - "80:80"
    - "443:443"
  volumes:
    - ./nginx.conf:/etc/nginx/nginx.conf
  depends_on:
    - magic-telegram-server
```

---

**作者**: liubo  
**日期**: 2025-01-15  
**版本**: 1.0.0