FROM python:3.12-slim
WORKDIR /app
COPY AI/requirements.txt .
RUN pip install --no-cache-dir -r requirements.txt
COPY AI ./AI
ENV PYTHONUNBUFFERED=1
ENV PYTHONPATH=/app
EXPOSE 8090
CMD ["python", "-m", "AI.main"]