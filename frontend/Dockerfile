# syntax=docker/dockerfile:1

# EN: Dependencies first, in their own layer, so a code change does not reinstall them.
# VI: Cài thư viện trước, ở lớp riêng, để sửa code không phải cài lại.
FROM node:22-alpine AS deps
WORKDIR /app
COPY package.json package-lock.json ./
RUN --mount=type=cache,target=/root/.npm npm ci

FROM node:22-alpine AS build
WORKDIR /app
ENV NEXT_TELEMETRY_DISABLED=1
COPY --from=deps /app/node_modules ./node_modules
COPY . .
RUN npm run build

# EN: Runtime: only the standalone server Next.js traced, plus the static files it leaves for us to copy.
# VI: Giai đoạn chạy: chỉ có server standalone mà Next.js đã lần ra, cộng các file tĩnh nó để lại cho mình chép.
FROM node:22-alpine
WORKDIR /app
ENV NODE_ENV=production \
    NEXT_TELEMETRY_DISABLED=1 \
    PORT=3000 \
    HOSTNAME=0.0.0.0

COPY --from=build --chown=node:node /app/public ./public
COPY --from=build --chown=node:node /app/.next/standalone ./
COPY --from=build --chown=node:node /app/.next/static ./.next/static

USER node
EXPOSE 3000
CMD ["node", "server.js"]
