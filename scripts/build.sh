#!/bin/bash

# =================================
# Magic Telegram Server Docker构建脚本
# 作者: liubo
# 日期: 2025-08-25
# 描述: 用于构建Magic Telegram Server Docker镜像的脚本
# =================================

set -e  # 遇到错误立即退出

# 颜色定义
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
NC='\033[0m' # No Color

# 默认配置
DEFAULT_IMAGE_NAME="magic-telegram-server"
DEFAULT_TAG="latest"
DEFAULT_REGISTRY=""
DOCKERFILE_PATH="./Dockerfile"
BUILD_CONTEXT="."

# 显示帮助信息
show_help() {
    echo -e "${BLUE}Magic Telegram Server Docker构建脚本${NC}"
    echo ""
    echo "用法: $0 [选项]"
    echo ""
    echo "选项:"
    echo "  -n, --name NAME        镜像名称 (默认: ${DEFAULT_IMAGE_NAME})"
    echo "  -t, --tag TAG          镜像标签 (默认: ${DEFAULT_TAG})"
    echo "  -r, --registry REG     镜像仓库地址 (可选)"
    echo "  -f, --file FILE        Dockerfile路径 (默认: ${DOCKERFILE_PATH})"
    echo "  -c, --context PATH     构建上下文路径 (默认: ${BUILD_CONTEXT})"
    echo "  --no-cache             不使用构建缓存"
    echo "  --push                 构建完成后推送到仓库"
    echo "  --platform PLATFORM    指定目标平台 (如: linux/amd64,linux/arm64)"
    echo "  -v, --verbose          详细输出"
    echo "  -h, --help             显示此帮助信息"
    echo ""
    echo "示例:"
    echo "  $0                                    # 使用默认配置构建"
    echo "  $0 -t v1.2.0                         # 构建指定版本"
    echo "  $0 -r docker.io/username -t v1.2.0   # 构建并指定仓库"
    echo "  $0 --push -t v1.2.0                  # 构建并推送"
    echo "  $0 --platform linux/amd64,linux/arm64 # 多平台构建"
}

# 日志函数
log_info() {
    echo -e "${BLUE}[INFO]${NC} $1"
}

log_success() {
    echo -e "${GREEN}[SUCCESS]${NC} $1"
}

log_warning() {
    echo -e "${YELLOW}[WARNING]${NC} $1"
}

log_error() {
    echo -e "${RED}[ERROR]${NC} $1"
}

# 检查Docker是否可用
check_docker() {
    if ! command -v docker &> /dev/null; then
        log_error "Docker未安装或不在PATH中"
        exit 1
    fi
    
    if ! docker info &> /dev/null; then
        log_error "Docker守护进程未运行，请启动Docker"
        exit 1
    fi
    
    log_info "Docker检查通过"
}

# 检查构建环境
check_build_environment() {
    if [[ ! -f "$DOCKERFILE_PATH" ]]; then
        log_error "Dockerfile不存在: $DOCKERFILE_PATH"
        exit 1
    fi
    
    if [[ ! -d "$BUILD_CONTEXT" ]]; then
        log_error "构建上下文目录不存在: $BUILD_CONTEXT"
        exit 1
    fi
    
    if [[ ! -f "pom.xml" ]]; then
        log_error "pom.xml文件不存在，请确保在项目根目录执行"
        exit 1
    fi
    
    log_info "构建环境检查通过"
}

# 构建镜像
build_image() {
    local full_image_name="$1"
    local build_args=()
    
    # 添加构建参数
    build_args+=("--file" "$DOCKERFILE_PATH")
    build_args+=("--tag" "$full_image_name")
    
    if [[ "$NO_CACHE" == "true" ]]; then
        build_args+=("--no-cache")
        log_info "使用--no-cache选项"
    fi
    
    if [[ -n "$PLATFORM" ]]; then
        build_args+=("--platform" "$PLATFORM")
        log_info "目标平台: $PLATFORM"
    fi
    
    if [[ "$VERBOSE" == "true" ]]; then
        build_args+=("--progress=plain")
    fi
    
    build_args+=("$BUILD_CONTEXT")
    
    log_info "开始构建镜像: $full_image_name"
    log_info "构建命令: docker build ${build_args[*]}"
    
    if docker build "${build_args[@]}"; then
        log_success "镜像构建成功: $full_image_name"
        
        # 显示镜像信息
        local image_size=$(docker images --format "table {{.Size}}" "$full_image_name" | tail -n 1)
        log_info "镜像大小: $image_size"
        
        return 0
    else
        log_error "镜像构建失败"
        return 1
    fi
}

# 推送镜像
push_image() {
    local full_image_name="$1"
    
    log_info "开始推送镜像: $full_image_name"
    
    if docker push "$full_image_name"; then
        log_success "镜像推送成功: $full_image_name"
        return 0
    else
        log_error "镜像推送失败"
        return 1
    fi
}

# 主函数
main() {
    local image_name="$DEFAULT_IMAGE_NAME"
    local tag="$DEFAULT_TAG"
    local registry="$DEFAULT_REGISTRY"
    local push_image_flag="false"
    local no_cache="false"
    local platform=""
    local verbose="false"
    
    # 解析命令行参数
    while [[ $# -gt 0 ]]; do
        case $1 in
            -n|--name)
                image_name="$2"
                shift 2
                ;;
            -t|--tag)
                tag="$2"
                shift 2
                ;;
            -r|--registry)
                registry="$2"
                shift 2
                ;;
            -f|--file)
                DOCKERFILE_PATH="$2"
                shift 2
                ;;
            -c|--context)
                BUILD_CONTEXT="$2"
                shift 2
                ;;
            --no-cache)
                no_cache="true"
                shift
                ;;
            --push)
                push_image_flag="true"
                shift
                ;;
            --platform)
                platform="$2"
                shift 2
                ;;
            -v|--verbose)
                verbose="true"
                shift
                ;;
            -h|--help)
                show_help
                exit 0
                ;;
            *)
                log_error "未知选项: $1"
                show_help
                exit 1
                ;;
        esac
    done
    
    # 设置全局变量
    NO_CACHE="$no_cache"
    PLATFORM="$platform"
    VERBOSE="$verbose"
    
    # 构建完整镜像名称
    local full_image_name
    if [[ -n "$registry" ]]; then
        full_image_name="$registry/$image_name:$tag"
    else
        full_image_name="$image_name:$tag"
    fi
    
    log_info "=== Magic Telegram Server Docker构建 ==="
    log_info "镜像名称: $full_image_name"
    log_info "Dockerfile: $DOCKERFILE_PATH"
    log_info "构建上下文: $BUILD_CONTEXT"
    
    # 执行检查
    check_docker
    check_build_environment
    
    # 构建镜像
    if build_image "$full_image_name"; then
        # 如果需要推送
        if [[ "$push_image_flag" == "true" ]]; then
            if [[ -z "$registry" ]]; then
                log_warning "未指定仓库地址，跳过推送"
            else
                push_image "$full_image_name"
            fi
        fi
        
        log_success "=== 构建完成 ==="
        log_info "镜像: $full_image_name"
        log_info "运行命令: docker run -p 8080:8080 $full_image_name"
    else
        log_error "=== 构建失败 ==="
        exit 1
    fi
}

# 执行主函数
main "$@"