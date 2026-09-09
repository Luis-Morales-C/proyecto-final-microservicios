import pytest
from pydantic import ValidationError

from app.schemas import DepartamentoCreate


def datos_validos():
    return {
        "id": "IT",
        "nombre": "Tecnologia",
        "descripcion": "Departamento de tecnologia"
    }


def test_departamento_valido():
    departamento = DepartamentoCreate(
        **datos_validos()
    )

    assert departamento.id == "IT"
    assert departamento.nombre == "Tecnologia"


@pytest.mark.parametrize(
    "campo",
    [
        "id",
        "nombre",
        "descripcion"
    ]
)
def test_campo_obligatorio(campo):
    datos = datos_validos()
    datos[campo] = None

    with pytest.raises(ValidationError):
        DepartamentoCreate(**datos)


def test_id_vacio():
    datos = datos_validos()
    datos["id"] = ""

    with pytest.raises(ValidationError):
        DepartamentoCreate(**datos)


def test_id_con_espacios():
    datos = datos_validos()
    datos["id"] = "I T"

    with pytest.raises(ValidationError):
        DepartamentoCreate(**datos)


def test_id_con_caracteres_invalidos():
    datos = datos_validos()
    datos["id"] = "IT@"

    with pytest.raises(ValidationError):
        DepartamentoCreate(**datos)


def test_nombre_vacio():
    datos = datos_validos()
    datos["nombre"] = "   "

    with pytest.raises(ValidationError):
        DepartamentoCreate(**datos)


def test_nombre_con_espacios_externos():
    datos = datos_validos()
    datos["nombre"] = " Tecnologia"

    with pytest.raises(ValidationError):
        DepartamentoCreate(**datos)


def test_nombre_con_espacios_consecutivos():
    datos = datos_validos()
    datos["nombre"] = "Tecno  logia"

    with pytest.raises(ValidationError):
        DepartamentoCreate(**datos)


def test_nombre_con_caracteres_invalidos():
    datos = datos_validos()
    datos["nombre"] = "Tecnologia123"

    with pytest.raises(ValidationError):
        DepartamentoCreate(**datos)


def test_descripcion_vacia():
    datos = datos_validos()
    datos["descripcion"] = "   "

    with pytest.raises(ValidationError):
        DepartamentoCreate(**datos)


def test_descripcion_con_espacios_externos():
    datos = datos_validos()
    datos["descripcion"] = " Descripcion"

    with pytest.raises(ValidationError):
        DepartamentoCreate(**datos)


def test_descripcion_con_espacios_consecutivos():
    datos = datos_validos()
    datos["descripcion"] = "Departamento  tecnologia"

    with pytest.raises(ValidationError):
        DepartamentoCreate(**datos)


def test_nombre_muy_largo():
    datos = datos_validos()
    datos["nombre"] = "A" * 101

    with pytest.raises(ValidationError):
        DepartamentoCreate(**datos)


def test_descripcion_muy_larga():
    datos = datos_validos()
    datos["descripcion"] = "A" * 256

    with pytest.raises(ValidationError):
        DepartamentoCreate(**datos)