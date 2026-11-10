import pytest
from pydantic import ValidationError

from app.schemas import DepartamentoCreate


def valido(**cambios):
    data = {
        "id": "IT-01",
        "nombre": "Tecnologia",
        "descripcion": "Departamento de tecnologia",
    }
    data.update(cambios)
    return data


def test_schema_acepta_departamento_valido():
    dep = DepartamentoCreate(**valido())
    assert dep.id == "IT-01"
    assert dep.nombre == "Tecnologia"


@pytest.mark.parametrize("identificador", ["", " IT", "IT ", "I T", "IT_01", "IT@01"])
def test_schema_rechaza_id_invalido(identificador):
    with pytest.raises(ValidationError):
        DepartamentoCreate(**valido(id=identificador))


@pytest.mark.parametrize("nombre", ["", " Tecnologia", "Tecnologia ", "Tec  nologia", "Tecnologia_1"])
def test_schema_rechaza_nombre_invalido(nombre):
    with pytest.raises(ValidationError):
        DepartamentoCreate(**valido(nombre=nombre))


@pytest.mark.parametrize("descripcion", ["", " descripcion", "descripcion ", "descripcion  doble"])
def test_schema_rechaza_descripcion_invalida(descripcion):
    with pytest.raises(ValidationError):
        DepartamentoCreate(**valido(descripcion=descripcion))
