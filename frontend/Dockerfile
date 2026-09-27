# Placeholder image until the Angular app is generated (prompt P00, frontend part / P10).
# It will become a multi-stage build: node (ng build) -> nginx.
FROM nginx:1.28-alpine
COPY nginx.conf /etc/nginx/conf.d/default.conf
COPY placeholder/ /usr/share/nginx/html/
