from fastapi import FastAPI

app = FastAPI(title="service2")


@app.get("/hello")
def hello() -> dict:
    return {"service": "service2", "status": "ok"}


@app.get("/health")
def health() -> dict:
    return {"status": "healthy"}