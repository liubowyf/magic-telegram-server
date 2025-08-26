# Magic Telegram Server Dockerfile
# 作者: liubo
# 日期: 2025-01-15
# 描述: 多阶段构建的生产就绪Docker镜像，支持多架构（amd64/arm64）

# ================================
# 第一阶段: Maven构建阶段
# ================================
FROM --platform=$BUILDPLATFORM eclipse-temurin:17-jdk-alpine AS builder

# 声明构建参数
ARG TARGETPLATFORM
ARG BUILDPLATFORM
ARG TARGETOS
ARG TARGETARCH

# 安装Maven和构建工具
RUN apk add --no-cache maven git

# 显示构建平台信息
RUN echo "构建平台: $BUILDPLATFORM" && \
    echo "目标平台: $TARGETPLATFORM" && \
    echo "目标架构: $TARGETARCH"

# 设置工作目录
WORKDIR /build

# 复制Maven配置文件和源代码
COPY pom.xml .
COPY settings.xml .
COPY src/ src/

# 构建应用（完全跳过测试编译和执行，增加重试机制）
RUN mvn clean package -DskipTests -Dmaven.test.skip=true -B \
    -s settings.xml \
    -Dmaven.wagon.http.retryHandler.count=3 \
    -Dmaven.wagon.http.pool=false \
    -Dmaven.wagon.httpconnectionManager.ttlSeconds=120 \
    -Dmaven.wagon.http.connectionTimeout=60000 \
    -Dmaven.wagon.http.readTimeout=60000

# 验证构建产物
RUN ls -la target/ && \
    test -f target/*.jar

# ================================
# 第二阶段: 运行时阶段
# ================================
FROM --platform=$TARGETPLATFORM eclipse-temurin:17-jre AS runtime

# 设置维护者信息
LABEL maintainer="liubo" \
      version="v1.2.0" \
      description="Magic Telegram Server - 基于Spring Boot的Telegram消息处理服务" \
      org.opencontainers.image.source="https://github.com/your-repo/magic-telegram-server"

# 安装必要的系统工具
RUN apt-get update && apt-get install -y \
        curl \
        ca-certificates \
        tzdata \
    && rm -rf /var/lib/apt/lists/*

# 设置工作目录
WORKDIR /app

# 创建必要的目录
RUN mkdir -p /app/logs /app/data /app/config

# 从构建阶段复制JAR文件
COPY --from=builder /build/target/*.jar app.jar

# 显示运行时平台信息
RUN echo "运行时架构: $(uname -m)"

# 设置环境变量
ENV JAVA_OPTS="-Xms512m -Xmx1024m -XX:+UseG1GC -XX:+UseContainerSupport" \
    SPRING_PROFILES_ACTIVE="docker" \
    SERVER_PORT="8080" \
    TZ="Asia/Shanghai"

# 暴露端口
EXPOSE 8080

# 健康检查
HEALTHCHECK --interval=30s --timeout=10s --start-period=60s --retries=3 \
    CMD curl -f http://localhost:8080/actuator/health || exit 1

# 启动命令
CMD ["sh", "-c", "java $JAVA_OPTS -jar app.jar"]

# ================================
# 构建说明
# ================================
# 构建命令:
# docker build -t magic-telegram-server:v1.2.0 .
# docker build -t magic-telegram-server:latest .
#
# 运行命令:
# docker run -d --name telegram-server \
#   -p 8080:8080 \
#   -e MONGODB_URI="mongodb://localhost:27017/telegram" \
#   magic-telegram-server:v1.2.0
#
# 环境变量说明:
# - MONGODB_URI: MongoDB连接字符串
# - TELEGRAM_API_ID: Telegram API ID
# - TELEGRAM_API_HASH: Telegram API Hash
# - TELEGRAM_PHONE_NUMBER: Telegram手机号
# - JAVA_OPTS: JVM参数
# - SPRING_PROFILES_ACTIVE: Spring配置文件
# ================================