FROM ghcr.io/astral-sh/uv:python3.13-bookworm-slim AS deps
WORKDIR /app
ENV UV_COMPILE_BYTECODE=1 \
    UV_LINK_MODE=copy \
    UV_PROJECT_ENVIRONMENT=/app/.venv

COPY pyproject.toml uv.lock ./
RUN --mount=type=cache,target=/root/.cache/uv uv sync --frozen --no-install-project --no-dev

COPY src ./src
RUN uv sync --frozen --no-dev

FROM deps AS test
RUN --mount=type=cache,target=/root/.cache/uv uv sync --frozen
COPY tests ./tests
RUN uv run ruff check . && uv run ruff format --check . && uv run mypy && uv run pytest

FROM python:3.13-slim AS runtime
WORKDIR /app
RUN useradd -m -u 1000 appuser
USER appuser

COPY --from=deps --chown=appuser:appuser /app/.venv /app/.venv
COPY --from=deps --chown=appuser:appuser /app/src /app/src

ENV PATH=/app/.venv/bin:$PATH \
    PYTHONUNBUFFERED=1

CMD ["python", "-m", "producer"]
