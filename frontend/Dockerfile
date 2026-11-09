# Build stage: install exact dependencies and build the static SPA.
FROM node:22-alpine AS build
WORKDIR /app
COPY package.json package-lock.json ./
RUN npm ci
COPY . .
RUN npm run build

# Runtime stage: Caddy serves the static files (no Node.js in production).
# In production the root Caddy (infra/caddy) proxies "/" here and "/api" to the backend.
FROM caddy:2-alpine
COPY Caddyfile /etc/caddy/Caddyfile
COPY --from=build /app/dist /srv
RUN chown -R 1000:1000 /data /config
USER 1000
EXPOSE 8080
