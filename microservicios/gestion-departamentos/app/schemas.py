from pydantic import BaseModel, Field, field_validator
import re


class DepartamentoBase(BaseModel):

    nombre: str = Field(
        ...,
        min_length=1,
        max_length=100,
        description="Nombre del departamento"
    )

    descripcion: str = Field(
        ...,
        min_length=1,
        max_length=255,
        description="Descripción del departamento"
    )

    @field_validator("nombre")
    @classmethod
    def validar_nombre(cls, valor):

        if not valor.strip():
            raise ValueError(
                "El nombre no puede estar vacío"
            )

        if valor != valor.strip():
            raise ValueError(
                "El nombre no puede comenzar ni terminar con espacios"
            )

        if "  " in valor:
            raise ValueError(
                "El nombre no puede contener espacios consecutivos"
            )

        if not re.fullmatch(
                r"[A-Za-zÁÉÍÓÚáéíóúÑñÜü]+(?:[ -][A-Za-zÁÉÍÓÚáéíóúÑñÜü]+)*",
                valor
        ):
            raise ValueError(
                "El nombre solo puede contener letras, espacios y guiones"
            )

        return valor

    @field_validator("descripcion")
    @classmethod
    def validar_descripcion(cls, valor):

        if not valor.strip():
            raise ValueError(
                "La descripción no puede estar vacía"
            )

        if valor != valor.strip():
            raise ValueError(
                "La descripción no puede comenzar ni terminar con espacios"
            )

        if "  " in valor:
            raise ValueError(
                "La descripción no puede contener espacios consecutivos"
            )

        return valor


class DepartamentoCreate(DepartamentoBase):

    id: str = Field(
        ...,
        min_length=1,
        max_length=20,
        description="Identificador único del departamento"
    )

    @field_validator("id")
    @classmethod
    def validar_id(cls, valor):

        if not valor.strip():
            raise ValueError(
                "El ID no puede estar vacío"
            )

        if valor != valor.strip():
            raise ValueError(
                "El ID no puede comenzar ni terminar con espacios"
            )

        if " " in valor:
            raise ValueError(
                "El ID no puede contener espacios"
            )

        if not re.fullmatch(r"[A-Za-z0-9-]+", valor):
            raise ValueError(
                "El ID solo puede contener letras, números y guiones"
            )

        return valor


class DepartamentoResponse(DepartamentoBase):

    id: str

    class Config:
        from_attributes = True