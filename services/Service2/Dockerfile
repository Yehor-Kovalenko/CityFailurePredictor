# ── Build stage ───────────────────────────────────────────────────────────────
FROM python:3.12-slim AS build
WORKDIR /app

# Install uv — fast, reproducible dep installer
COPY --from=ghcr.io/astral-sh/uv:latest /uv /usr/local/bin/uv

# Copy lockfile and project definition first (layer-cache deps)
COPY pyproject.toml uv.lock* ./

# Install deps into /app/.venv — --frozen ensures uv.lock is respected exactly
RUN uv sync --frozen --no-dev

# Copy application source
COPY app/ app/

# ── Runtime stage ─────────────────────────────────────────────────────────────
FROM python:3.12-slim AS runtime
WORKDIR /app

# Non-root user
RUN useradd -r -s /bin/false appuser
USER appuser

# Copy the pre-built venv and source from build stage
COPY --from=build /app/.venv .venv
COPY --from=build /app/app app/

ARG SERVICE_VERSION=unknown
LABEL org.opencontainers.image.version="${SERVICE_VERSION}"
LABEL org.opencontainers.image.title="service2"

EXPOSE 8082

HEALTHCHECK --interval=15s --timeout=5s --start-period=20s --retries=3 \
    CMD python -c "import urllib.request; urllib.request.urlopen('http://localhost:8082/health')" || exit 1

ENV PATH="/app/.venv/bin:$PATH"

ENTRYPOINT ["uvicorn", "app.main:app", "--host", "0.0.0.0", "--port", "8082"]