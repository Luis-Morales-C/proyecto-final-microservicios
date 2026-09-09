from fastapi import Depends, FastAPI, HTTPException, Request, status
from fastapi.exceptions import RequestValidationError
from fastapi.responses import JSONResponse
from sqlalchemy.exc import IntegrityError, OperationalError, SQLAlchemyError
from sqlalchemy.orm import Session

from app.crud import (
    crear_departamento,
    obtener_departamento,
    obtener_departamentos
)
from app.database import Base, engine, get_db
from app.schemas import DepartamentoCreate, DepartamentoResponse


Base.metadata.create_all(bind=engine)


app = FastAPI(
    title="Microservicio de Gestión de Departamentos",
    description=(
        "API REST para registrar y consultar departamentos. "
        "Este microservicio mantiene su propia base de datos PostgreSQL."
    ),
    version="1.0.0",
    docs_url="/docs",
    redoc_url="/redoc",
    openapi_url="/openapi.json"
)


@app.exception_handler(RequestValidationError)
async def validation_exception_handler(
    request: Request,
    exc: RequestValidationError
):
    detalles = []

    for error in exc.errors():

        ubicacion = error.get("loc", [])

        campo = (
            ubicacion[-1]
            if ubicacion
            else "body"
        )

        tipo = error.get("type")

        if tipo == "missing":
            mensaje = f"El campo '{campo}' es obligatorio."

        elif tipo == "string_too_short":
            mensaje = f"El campo '{campo}' no puede estar vacío."

        elif tipo == "string_too_long":
            mensaje = (
                f"El campo '{campo}' supera "
                "la longitud máxima permitida."
            )

        elif tipo == "string_type":
            mensaje = f"El campo '{campo}' debe ser un texto."

        elif tipo == "json_invalid":
            mensaje = "El cuerpo de la petición contiene JSON inválido."

        elif tipo == "model_attributes_type":
            mensaje = "El cuerpo de la petición debe ser un objeto JSON."

        elif tipo == "value_error":
            mensaje = error["msg"].replace(
                "Value error, ",
                ""
            )

        else:
            mensaje = (
                f"El campo '{campo}' "
                "no tiene un formato válido."
            )

        detalles.append(mensaje)

    return JSONResponse(
        status_code=status.HTTP_422_UNPROCESSABLE_ENTITY,
        content={
            "error": True,
            "mensaje": "Los datos enviados no son válidos",
            "detalles": detalles
        }
    )


@app.exception_handler(IntegrityError)
async def integrity_error_handler(
    request: Request,
    exc: IntegrityError
):
    return JSONResponse(
        status_code=status.HTTP_400_BAD_REQUEST,
        content={
            "error": True,
            "mensaje": (
                "No fue posible registrar el departamento "
                "porque el identificador ya existe."
            )
        }
    )


@app.exception_handler(OperationalError)
async def operational_error_handler(
    request: Request,
    exc: OperationalError
):
    return JSONResponse(
        status_code=status.HTTP_503_SERVICE_UNAVAILABLE,
        content={
            "error": True,
            "mensaje": (
                "La base de datos de departamentos "
                "no está disponible actualmente."
            )
        }
    )


@app.exception_handler(SQLAlchemyError)
async def sqlalchemy_error_handler(
    request: Request,
    exc: SQLAlchemyError
):
    return JSONResponse(
        status_code=status.HTTP_500_INTERNAL_SERVER_ERROR,
        content={
            "error": True,
            "mensaje": (
                "Ocurrió un error al procesar la operación "
                "en la base de datos."
            )
        }
    )


@app.exception_handler(Exception)
async def general_exception_handler(
    request: Request,
    exc: Exception
):
    return JSONResponse(
        status_code=status.HTTP_500_INTERNAL_SERVER_ERROR,
        content={
            "error": True,
            "mensaje": "Ocurrió un error interno en el servidor."
        }
    )


@app.post(
    "/departamentos",
    response_model=DepartamentoResponse,
    status_code=status.HTTP_201_CREATED,
    summary="Registrar un departamento",
    description=(
        "Registra un nuevo departamento en la base de datos. "
        "El identificador debe ser único."
    ),
    responses={
        201: {
            "description": "Departamento creado correctamente"
        },
        400: {
            "description": "El departamento ya existe"
        },
        422: {
            "description": "Datos de entrada inválidos"
        },
        500: {
            "description": "Error interno del servidor"
        },
        503: {
            "description": "Base de datos no disponible"
        }
    }
)
def registrar_departamento(
    departamento: DepartamentoCreate,
    db: Session = Depends(get_db)
):
    existente = obtener_departamento(
        db,
        departamento.id
    )

    if existente:
        raise HTTPException(
            status_code=status.HTTP_400_BAD_REQUEST,
            detail=(
                f"El departamento con id "
                f"{departamento.id} ya existe"
            )
        )

    return crear_departamento(
        db,
        departamento
    )


@app.get(
    "/departamentos/{departamento_id}",
    response_model=DepartamentoResponse,
    summary="Consultar un departamento",
    description=(
        "Obtiene un departamento utilizando "
        "su identificador."
    ),
    responses={
        200: {
            "description": "Departamento encontrado"
        },
        404: {
            "description": "Departamento no encontrado"
        },
        500: {
            "description": "Error interno del servidor"
        },
        503: {
            "description": "Base de datos no disponible"
        }
    }
)
def consultar_departamento(
    departamento_id: str,
    db: Session = Depends(get_db)
):
    departamento = obtener_departamento(
        db,
        departamento_id
    )

    if departamento is None:
        raise HTTPException(
            status_code=status.HTTP_404_NOT_FOUND,
            detail=(
                f"El departamento con id "
                f"{departamento_id} no existe"
            )
        )

    return departamento


@app.get(
    "/departamentos",
    response_model=list[DepartamentoResponse],
    summary="Listar departamentos",
    description=(
        "Obtiene todos los departamentos "
        "registrados."
    ),
    responses={
        200: {
            "description": (
                "Lista de departamentos. "
                "Puede ser una lista vacía."
            )
        },
        500: {
            "description": "Error interno del servidor"
        },
        503: {
            "description": "Base de datos no disponible"
        }
    }
)
def listar_departamentos(
    db: Session = Depends(get_db)
):
    return obtener_departamentos(db)