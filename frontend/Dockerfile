# ---------------------------------------------------------------------------
# Front end image, for the containerised sanity-check stack.
#
# Builds the production Angular bundle and serves it with nginx, which also
# proxies /api to the back end container. Day-to-day development still uses
# `ng serve` (`make dev`) for hot reload; this image is what the application
# actually looks like when built.
# ---------------------------------------------------------------------------

# --- build stage -----------------------------------------------------------
FROM node:24-alpine AS build
WORKDIR /build

# package*.json first, so a source change does not re-run the install layer.
# npm ci (not npm install) installs exactly what the lockfile pins, which is the
# whole point of building in a container.
COPY package.json package-lock.json ./
RUN --mount=type=cache,target=/root/.npm \
    npm ci

COPY . .
RUN npm run build

# --- runtime stage ---------------------------------------------------------
FROM nginx:1.29-alpine AS runtime

COPY nginx.conf /etc/nginx/conf.d/default.conf

# The application builder emits into dist/<project>/browser.
COPY --from=build /build/dist/frontend/browser /usr/share/nginx/html

EXPOSE 80

# 127.0.0.1, not localhost: this nginx binds IPv4 while localhost resolves to
# ::1 first, which would report a perfectly healthy container as unhealthy.
HEALTHCHECK --interval=30s --timeout=3s --start-period=5s --retries=3 \
    CMD wget --quiet --tries=1 --spider http://127.0.0.1/ || exit 1
