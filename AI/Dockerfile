FROM python:3.12-slim
WORKDIR /app
COPY AI/requirements.txt .
RUN pip install --no-cache-dir -r requirements.txt
COPY AI .
ENV PYTHONUNBUFFERED=1
EXPOSE 8090
CMD ["python", "main.py"]