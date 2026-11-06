FROM python:3.11-slim

WORKDIR /app

COPY requirements.txt .

RUN pip install --default-timeout=300 --no-cache-dir -r requirements.txt
COPY app ./app

EXPOSE 8082

CMD ["opentelemetry-instrument", "uvicorn", "app.main:app", "--host", "0.0.0.0", "--port", "8082", "--no-access-log"]