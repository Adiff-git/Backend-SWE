FROM python:3.12-slim-bookworm@sha256:44cf1793efa7c7a476bb0f2cff81443f4a072c90c1cb127e2924933daa3e8c64 AS dependencies
WORKDIR /build
COPY requirements.lock ./
RUN python -m venv /opt/venv \
    && /opt/venv/bin/python -m pip install --no-cache-dir --require-hashes -r requirements.lock

FROM python:3.12-slim-bookworm@sha256:44cf1793efa7c7a476bb0f2cff81443f4a072c90c1cb127e2924933daa3e8c64 AS runtime
ENV PATH="/opt/venv/bin:${PATH}" \
    PYTHONDONTWRITEBYTECODE=1 \
    PYTHONUNBUFFERED=1 \
    AWS_EC2_METADATA_DISABLED=true
RUN groupadd --gid 10001 bootstrap \
    && useradd --uid 10001 --gid 10001 --no-create-home --shell /usr/sbin/nologin bootstrap \
    && mkdir -p /app /catalog-checkpoints/.bootstrap /reports \
    && chown -R 10001:10001 /app /catalog-checkpoints /reports
COPY --from=dependencies /opt/venv /opt/venv
WORKDIR /app
COPY --chown=10001:10001 . /app/
USER 10001:10001
ENTRYPOINT ["python", "/app/bootstrap.py"]
