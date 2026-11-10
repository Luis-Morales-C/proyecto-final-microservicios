def test_health_up(client):
    response = client.get("/health")
    assert response.status_code == 200
    data = response.json()
    assert data["status"] == "UP"
    assert data["checks"]["database"] == "UP"


def test_metrics_prometheus_disponible(client):
    response = client.get("/metrics")
    assert response.status_code == 200
    assert "text/plain" in response.headers["content-type"]
    assert "http_request" in response.text or "python_" in response.text


def test_openapi_declara_bearer_auth(client):
    response = client.get("/departamentos/openapi.json")
    assert response.status_code == 200
    schema = response.json()
    security = schema["components"]["securitySchemes"]["BearerAuth"]
    assert security["type"] == "http"
    assert security["scheme"] == "bearer"
    assert schema["paths"]["/departamentos"]["get"]["security"] == [{"BearerAuth": []}]
