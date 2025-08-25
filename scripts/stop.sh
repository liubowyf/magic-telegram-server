#!/bin/bash

# =================================
# Magic Telegram Server 停止脚本
# 作者: liubo
# 日期: 2025-01-15
# 描述: 简化的Docker Compose停止脚本
# =================================

set -euo pipefail

# 颜色定义
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
NC='\033[0m' # No Color

# 日志函数
log_info() {
    echo -e "${BLUE}[INFO]${NC} $1"
}

log_success() {
    echo -e "${GREEN}[SUCCESS]${NC} $1"
}

log_error() {
    echo -e "${RED}[ERROR]${NC} $1"
}

# 显示帮助信息
show_help() {
    echo "使用方法: $0 [选项]"
    echo ""
    echo "选项:"
    echo "  -h, --help      显示此帮助信息"
    echo ""
    echo "示例:"
    echo "  $0              # 停止所有服务"
}

# 停止服务
stop_services() {
    log_info "停止 Magic Telegram Server..."
    
    # 检查 docker-compose.yml 是否存在
    if [[ ! -f "docker-compose.yml" ]]; then
        log_error "未找到 docker-compose.yml 文件"
        exit 1
    fi
    
    # 停止服务
    if docker compose down; then
        log_success "服务已停止"
    else
        log_error "停止服务失败"
        exit 1
    fi
}

# 主函数
main() {
    # 解析命令行参数
    while [[ $# -gt 0 ]]; do
        case $1 in
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
    
    # 停止服务
    stop_services
}

# 执行主函数
main "$@"