# syntax=docker/dockerfile:1

# EN: Dependencies first, in their own layer, so a code change does not reinstall them.
# VI: Cài thư viện trước, ở lớp riêng, để sửa code không phải cài lại.
FROM node:22-alpine AS deps
WORKDIR /app
COPY package.json package-lock.json ./
RUN --mount=type=cache,target=/root/.npm npm ci

FROM node:22-alpine AS build
WORKDIR /app
# EN: Fixed by the build: the /api and /media rewrites point at NEXBID_API_URL, and the socket URL is inlined for the browser.
# VI: Được chốt lúc build: rewrite /api và /media trỏ tới NEXBID_API_URL, còn URL socket được nhúng vào code trình duyệt.
ARG NEXBID_API_URL=http://backend:8080
ARG NEXT_PUBLIC_WS_URL=ws://localhost:8080/ws
ENV NEXT_TELEMETRY_DISABLED=1 \
    NEXBID_API_URL=$NEXBID_API_URL \
    NEXT_PUBLIC_WS_URL=$NEXT_PUBLIC_WS_URL
COPY --from=deps /app/node_modules ./node_modules
COPY . .
RUN npm run build

# EN: Runtime: only the standalone server Next.js traced, plus the static files it leaves for us to copy.
# VI: Giai đoạn chạy: chỉ có server standalone mà Next.js đã lần ra, cộng các file tĩnh nó để lại cho mình chép.
FROM node:22-alpine
WORKDIR /app
# EN: Server rendering reads the backend address at runtime. / VI: Render phía server đọc địa chỉ backend lúc chạy.
ARG NEXBID_API_URL=http://backend:8080
ENV NODE_ENV=production \
    NEXT_TELEMETRY_DISABLED=1 \
    NEXBID_API_URL=$NEXBID_API_URL \
    PORT=3000 \
    HOSTNAME=0.0.0.0

COPY --from=build --chown=node:node /app/public ./public
COPY --from=build --chown=node:node /app/.next/standalone ./
COPY --from=build --chown=node:node /app/.next/static ./.next/static

USER node
EXPOSE 3000
CMD ["node", "server.js"]
