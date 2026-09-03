# 本机已 npm run build 后使用（国内网络更稳）。
# 构建前：cd frontend && npm ci && npm run build
FROM docker.m.daocloud.io/library/nginx:1.27-alpine
COPY nginx.conf /etc/nginx/conf.d/default.conf
COPY dist /usr/share/nginx/html
EXPOSE 80
