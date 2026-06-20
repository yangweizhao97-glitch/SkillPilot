FROM python:3.12-slim

WORKDIR /app
ENV PYTHONPATH=/app
ARG PIP_INDEX_URL=https://pypi.org/simple
COPY requirements.txt .
RUN --mount=type=cache,target=/root/.cache/pip pip install --index-url "$PIP_INDEX_URL" --default-timeout=120 --retries=10 -r requirements.txt
COPY app ./app
COPY tests ./tests

EXPOSE 8090
CMD ["uvicorn", "app.main:app", "--host", "0.0.0.0", "--port", "8090"]
