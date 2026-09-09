def departamento_valido():
    return {
        "id": "IT",
        "nombre": "Tecnologia",
        "descripcion": "Departamento de tecnologia"
    }


def test_crear_departamento(client):
    response = client.post(
        "/departamentos",
        json=departamento_valido()
    )

    assert response.status_code == 201

    data = response.json()

    assert data["id"] == "IT"
    assert data["nombre"] == "Tecnologia"


def test_crear_departamento_duplicado(client):
    datos = departamento_valido()

    client.post(
        "/departamentos",
        json=datos
    )

    response = client.post(
        "/departamentos",
        json=datos
    )

    assert response.status_code == 400


def test_obtener_departamento(client):
    client.post(
        "/departamentos",
        json=departamento_valido()
    )

    response = client.get(
        "/departamentos/IT"
    )

    assert response.status_code == 200

    data = response.json()

    assert data["id"] == "IT"


def test_obtener_departamento_inexistente(client):
    response = client.get(
        "/departamentos/NO-EXISTE"
    )

    assert response.status_code == 404


def test_listar_departamentos(client):
    client.post(
        "/departamentos",
        json=departamento_valido()
    )

    response = client.get(
        "/departamentos"
    )

    assert response.status_code == 200

    data = response.json()

    assert isinstance(data, list)
    assert len(data) == 1


def test_listar_departamentos_vacio(client):
    response = client.get(
        "/departamentos"
    )

    assert response.status_code == 200
    assert response.json() == []


def test_body_vacio(client):
    response = client.post(
        "/departamentos"
    )

    assert response.status_code == 422


def test_json_invalido(client):
    response = client.post(
        "/departamentos",
        content='{"id": "IT"',
        headers={
            "Content-Type": "application/json"
        }
    )

    assert response.status_code == 422


def test_tipo_invalido(client):
    datos = departamento_valido()
    datos["id"] = 123

    response = client.post(
        "/departamentos",
        json=datos
    )

    assert response.status_code == 422


def test_ruta_inexistente(client):
    response = client.get(
        "/ruta-que-no-existe"
    )

    assert response.status_code == 404


def test_swagger_disponible(client):
    response = client.get("/docs")

    assert response.status_code == 200


def test_openapi_disponible(client):
    response = client.get("/openapi.json")

    assert response.status_code == 200

    data = response.json()

    assert "openapi" in data
    assert "/departamentos" in data["paths"]
    assert "/departamentos/{departamento_id}" in data["paths"]


def test_metodo_no_soportado(client):
    response = client.delete(
        "/departamentos"
    )

    assert response.status_code == 405