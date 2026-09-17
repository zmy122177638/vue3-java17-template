#!/bin/bash

SCRIPT_DIR=$( cd -- "$( dirname -- "${BASH_SOURCE[0]}" )" &> /dev/null && pwd )
REPO_ROOT=$( cd -- "${SCRIPT_DIR}/../.." &> /dev/null && pwd )
LOG_FILE=${SCRIPT_DIR}/build-local-docker-image.log
ERROR=""
# 镜像与容器名（原值为上游 vben 的 vben-admin-local）
IMAGE_NAME="yorvix-web"
# 前端 nginx 反代的后端地址（构建期注入 nginx.conf 的 __API_UPSTREAM__ 占位符）。
# 默认用后端容器名：生产前后端在同一 Docker 网络时可直接解析。
# 覆盖方式：API_UPSTREAM=other-api:4838 ./scripts/deploy/build-local-docker-image.sh
API_UPSTREAM=${API_UPSTREAM:-yorvix-api:4838}

function stop_and_remove_container() {
    # Stop and remove the existing container
    docker stop ${IMAGE_NAME} >/dev/null 2>&1
    docker rm ${IMAGE_NAME} >/dev/null 2>&1
}

function remove_image() {
    # Remove the existing image
    docker rmi ${IMAGE_NAME} >/dev/null 2>&1
}

function install_dependencies() {
    # 在仓库根目录安装依赖：pnpm workspace 的根在仓库根，
    # 原实现在 scripts/deploy 下执行，那里没有 package.json，必然失败
    cd ${REPO_ROOT}
    pnpm install || ERROR="install_dependencies failed"
}

function build_image() {
    # 构建上下文必须是仓库根目录（Dockerfile 内是 COPY . /app）
    docker build ${REPO_ROOT} -f ${SCRIPT_DIR}/Dockerfile \
        --build-arg API_UPSTREAM=${API_UPSTREAM} -t ${IMAGE_NAME} || ERROR="build_image failed"
}

function log_message() {
    if [[ ${ERROR} != "" ]];
    then
        >&2 echo "build failed, Please check build-local-docker-image.log for more details"
        >&2 echo "ERROR: ${ERROR}"
        exit 1
    else
        echo "docker image with tag '${IMAGE_NAME}' built sussessfully. Use below sample command to run the container"
        echo ""
        echo "docker run -d -p 8010:8080 --name ${IMAGE_NAME} ${IMAGE_NAME}"
    fi
}

echo "Info: Stopping and removing existing container and image" | tee ${LOG_FILE}
stop_and_remove_container
remove_image

echo "Info: Installing dependencies" | tee -a ${LOG_FILE}
install_dependencies 1>> ${LOG_FILE} 2>> ${LOG_FILE}

if [[ ${ERROR} == "" ]]; then
    echo "Info: Building docker image" | tee -a ${LOG_FILE}
    build_image 1>> ${LOG_FILE} 2>> ${LOG_FILE}
fi

log_message | tee -a ${LOG_FILE}
