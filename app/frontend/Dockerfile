FROM node:22-alpine AS build
WORKDIR /build

COPY package.json package-lock.json ./
RUN npm ci

COPY . .
RUN npm run build

FROM nginx:alpine AS runtime

# Single-page application: every path is served by the entry document so the
# locale-prefixed routes resolve on a hard refresh (CONSTRAINT-I18N-01).
RUN printf 'server {\n\
  listen 80;\n\
  root /usr/share/nginx/html;\n\
  index index.html;\n\
  location / {\n\
    try_files $uri $uri/ /index.html;\n\
  }\n\
}\n' > /etc/nginx/conf.d/default.conf

COPY --from=build /build/dist /usr/share/nginx/html

EXPOSE 80
