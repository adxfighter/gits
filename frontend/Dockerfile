# syntax=docker/dockerfile:1.7
FROM node:24-alpine AS build
WORKDIR /src
COPY package.json package-lock.json ./
RUN --mount=type=cache,target=/root/.npm npm ci --no-audit --no-fund
COPY . .
RUN npm run build

FROM nginx:1.30-alpine
# Security fixes released after the base image was built (docs/security-checklist.md)
RUN apk upgrade --no-cache
COPY nginx.conf /etc/nginx/conf.d/default.conf
COPY --from=build /src/dist/gits-web/browser/ /usr/share/nginx/html/
