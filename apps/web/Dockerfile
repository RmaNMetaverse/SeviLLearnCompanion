FROM node:24-alpine AS build
WORKDIR /app
COPY apps/web/package*.json ./
RUN npm ci
COPY apps/web/ ./
ENV NEXT_TELEMETRY_DISABLED=1
RUN npm run build
FROM node:24-alpine AS runtime
WORKDIR /app
ENV NODE_ENV=production SEVIL_HOST=0.0.0.0 PORT=3000
COPY --from=build --chown=node:node /app/out ./out
COPY --from=build --chown=node:node /app/scripts/serve.cjs ./scripts/serve.cjs
USER node
EXPOSE 3000
CMD ["node","scripts/serve.cjs"]
