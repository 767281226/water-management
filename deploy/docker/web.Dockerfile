# 前端镜像：Node 20 打包，Nginx 托管页面并反向代理 /api 到后端
# 构建上下文为仓库根目录（见 docker-compose.yml）

FROM node:20-alpine AS build
# 内网或国内网络可指定 npm 镜像，如 https://registry.npmmirror.com
ARG NPM_REGISTRY=
WORKDIR /src
COPY frontend/package.json frontend/package-lock.json ./
RUN if [ -n "$NPM_REGISTRY" ]; then npm config set registry "$NPM_REGISTRY"; fi \
    && npm ci --no-audit --no-fund
COPY frontend/ ./
RUN npm run build

FROM nginx:1.27-alpine
ENV TZ=Asia/Shanghai
COPY deploy/docker/nginx.conf /etc/nginx/conf.d/default.conf
COPY --from=build /src/dist /usr/share/nginx/html
EXPOSE 80
HEALTHCHECK --interval=15s --timeout=5s --retries=3 CMD wget -q -O /dev/null http://127.0.0.1/ || exit 1
