#!/bin/bash

# =================================
# Magic Telegram Server 启动脚本
# 作者: liubo
# 日期: 2025-01-15
# 描述: 简化的Docker Compose启动脚本
# =================================

set -euo pipefail

# 脚本配置
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PROJECT_ROOT="$(dirname "$SCRIPT_DIR")"

# 默认配置
MODE="internal"  # internal 或 external
DETACH=true

# 颜色定义
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
NC='\033[0m' # No Color

# 显示帮助信息
show_help() {
    cat << EOF
使用方法: $0 [选项]

选项:
  -m, --mode MODE           部署模式 (internal|external) [默认: internal]
  -f, --foreground          前台运行
  -h, --help                显示此帮助信息

部署模式说明:
  internal  - 使用内置MongoDB (默认)
  external  - 使用外部MongoDB

示例:
  $0                        # 使用默认配置启动
  $0 -m external            # 使用外部MongoDB启动
  $0 -f                     # 前台运行

EOF
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

log_debug() {
    if [[ "$VERBOSE" == "true" ]]; then
        echo -e "${PURPLE}[DEBUG]${NC} $1"
    fi
}

# 检查Docker环境
check_docker() {
    if ! command -v docker &> /dev/null; then
        log_error "Docker未安装或不在PATH中"
        exit 1
    fi
    
    if ! docker compose version &> /dev/null; then
        log_error "Docker Compose未安装或不可用"
        exit 1
    fi
    
    log_info "Docker环境检查通过"
}

# 检查环境文件
check_env_file() {
    local env_file="$1"
    
    if [[ ! -f "$env_file" ]]; then
        log_warning "环境文件 $env_file 不存在"
        
        # 根据模式提供相应的示例文件
        local example_file
        if [[ "$MODE" == "external" ]]; then
            example_file=".env.external.example"
        else
            example_file=".env.example"
        fi
        
        if [[ -f "$example_file" ]]; then
            log_info "发现示例文件 $example_file，正在复制..."
            cp "$example_file" "$env_file"
            log_warning "请编辑 $env_file 文件并设置正确的配置值"
        else
            log_error "示例文件 $example_file 不存在，请手动创建 $env_file"
            exit 1
        fi
    fi
    
    log_info "环境文件检查通过: $env_file"
}

# 获取Docker Compose命令
get_compose_cmd() {
    if command -v docker-compose &> /dev/null; then
        echo "docker-compose"
    else
        echo "docker compose"
    fi
}

# 构建Docker Compose参数
build_compose_args() {
    local mode="$1"
    local args=()
    
    # 添加环境文件
    if [[ -f "$ENV_FILE" ]]; then
        args+=("--env-file" "$ENV_FILE")
    fi
    
    # 根据模式选择compose文件
    case "$mode" in
        "external")
            args+=("-f" "docker-compose.external.yml")
            ;;
        "dev")
            args+=("-f" "docker-compose.dev.yml")
            ;;
        *)
            args+=("-f" "docker-compose.yml")
            ;;
    esac
    
    # 添加profile
    if [[ -n "$PROFILE" ]]; then
        IFS=',' read -ra PROFILES <<< "$PROFILE"
        for p in "${PROFILES[@]}"; do
            args+=("--profile" "$p")
        done
    fi
    
    echo "${args[@]}"
}

# 启动服务
start_services() {
    local mode="$1"
    
    log_info "=== 启动 Magic Telegram Server ($mode 模式) ==="
    
    # 选择compose文件
    local compose_file="docker-compose.yml"
    local env_file=".env"
    
    if [[ "$mode" == "external" ]]; then
        compose_file="docker-compose.external.yml"
        env_file=".env.external"
    fi
    
    # 构建启动命令
    local compose_cmd=("docker" "compose" "--env-file" "$env_file" "-f" "$compose_file" "up")
    
    if [[ "$DETACH" == "true" ]]; then
        compose_cmd+=("--detach")
    fi
    
    # 执行启动命令
    log_info "启动服务..."
    if "${compose_cmd[@]}"; then
        log_success "服务启动成功"
        
        if [[ "$DETACH" == "true" ]]; then
            log_info "服务正在后台运行"
            log_info "查看状态: docker compose --env-file $env_file ps"
            log_info "查看日志: docker compose --env-file $env_file logs -f"
            
            echo ""
            log_info "=== 访问地址 ==="
            log_info "应用: http://localhost:8080"
        fi
    else
        log_error "服务启动失败"
        exit 1
    fi
}



# 解析命令行参数
parse_args() {
    while [[ $# -gt 0 ]]; do
        case $1 in
            -m|--mode)
                MODE="$2"
                shift 2
                ;;
            -f|--foreground)
                DETACH=false
                shift
                ;;
            -h|--help)
                show_help
                exit 0
                ;;
            *)
                log_error "未知参数: $1"
                show_help
                exit 1
                ;;
        esac
    done
}

# 主函数
main() {
    # 解析命令行参数
    parse_args "$@"
    
    # 检查Docker环境
    check_docker
    
    # 检查环境文件
    local env_file=".env"
    if [[ "$MODE" == "external" ]]; then
        env_file=".env.external"
    fi
    check_env_file "$env_file"
    
    # 启动服务
    start_services "$MODE"
}

# 执行主函数
main "$@"