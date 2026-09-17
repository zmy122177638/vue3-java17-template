# ============================================================
# 前端（apps/web-antd）生产镜像：构建静态产物后交给 nginx 托管
#
# 构建上下文必须是【仓库根目录】：
#   docker build -f scripts/deploy/Dockerfile -t yorvix-web .
# 或直接使用：pnpm build:docker
# ============================================================
FROM node:22-slim AS builder

ENV PNPM_HOME="/pnpm"
ENV PATH="$PNPM_HOME:$PATH"
# vben 这类大型 monorepo 构建需要更大的堆，否则容易 OOM
ENV NODE_OPTIONS=--max-old-space-size=8192
ENV TZ=Asia/Shanghai
ENV CI=true

RUN npm i -g corepack

WORKDIR /app

# 复制整个仓库（node_modules / dist / target 已由根 .dockerignore 排除）
COPY . /app

# 安装依赖
RUN --mount=type=cache,id=pnpm,target=/pnpm/store pnpm install --frozen-lockfile
# 只构建 web-antd（不再全量构建、也不再按上游那样排除 docs）
RUN pnpm build:antd

FROM nginx:stable-alpine AS production

# 配置 nginx：额外声明 mjs 的 MIME（vben 产物里有 .mjs）
RUN echo "types { application/javascript js mjs; }" > /etc/nginx/conf.d/mjs.conf \
    && rm -rf /etc/nginx/conf.d/default.conf

# 复制构建产物。注意路径是 apps/web-antd/dist：
# 本仓库没有上游 vben 的 playground 应用，写错路径会导致构建在 COPY 阶段失败
COPY --from=builder /app/apps/web-antd/dist /usr/share/nginx/html

# 复制 nginx 配置（含 /api 反代到后端，见文件内注释）
# /api 反代目标（构建期替换 nginx.conf 里的 __API_UPSTREAM__ 占位符）。
# 默认 yorvix-api:4838：生产前后端在同一 Docker 网络时按后端容器名解析；
# 容器名/服务名不同时用 --build-arg API_UPSTREAM=<host>:4838 覆盖。
ARG API_UPSTREAM=yorvix-api:4838
COPY --from=builder /app/scripts/deploy/nginx.conf /etc/nginx/nginx.conf
RUN sed -i "s|__API_UPSTREAM__|${API_UPSTREAM}|g" /etc/nginx/nginx.conf

EXPOSE 8080

CMD ["nginx", "-g", "daemon off;"]
