# Production image of the frontend (specs/deploy.md): Angular build served by nginx.
# The server config (deploy/nginx.conf) is mounted by deploy/docker-compose.prod.yml.
FROM node:22-alpine AS build
WORKDIR /src
COPY package.json package-lock.json ./
RUN npm ci --no-audit --no-fund
COPY . .
RUN npm run build

FROM nginx:1.27-alpine
COPY --from=build /src/dist/akhana-frontend/browser /usr/share/nginx/html
