from fastapi import Depends, FastAPI, HTTPException, Request, status
from fastapi.exceptions import RequestValidationError
from fastapi.responses import JSONResponse
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
    description="API REST para la gestión de departamentos",
    version="1.0.0"
)


@app.exception_handler(RequestValidationError)
async def validation_exception_handler(
        request: Request,
        exc: RequestValidationError
):
    detalles = []

    for error in exc.errors():

        campo = error["loc"][-1]

        if error["type"] == "missing":
            detalles.append(
                f"El campo '{campo}' es obligatorio."
            )

        elif error["type"] == "string_too_short":
            detalles.append(
                f"El campo '{campo}' no puede estar vacío."
            )

        elif error["type"] == "string_too_long":
            detalles.append(
                f"El campo '{campo}' supera la longitud máxima permitida."
            )

        elif error["type"] == "string_type":
            detalles.append(
                f"El campo '{campo}' debe ser un texto."
            )

        elif error["type"] == "value_error":
            detalles.append(
                error["msg"].replace("Value error, ", "")
            )

        else:
            detalles.append(
                f"El campo '{campo}' no tiene un formato válido."
            )

    return JSONResponse(
        status_code=status.HTTP_422_UNPROCESSABLE_ENTITY,
        content={
            "error": "Datos inválidos",
            "detalles": detalles
        }
    )


@app.post(
    "/departamentos",
    response_model=DepartamentoResponse,
    status_code=status.HTTP_201_CREATED
)
def registrar_departamento(
        departamento: DepartamentoCreate,
        db: Session = Depends(get_db)
):
    existente = obtener_departamento(db, departamento.id)

    if existente:
        raise HTTPException(
            status_code=status.HTTP_400_BAD_REQUEST,
            detail=f"El departamento con id {departamento.id} ya existe"
        )

    return crear_departamento(db, departamento)


@app.get(
    "/departamentos/{departamento_id}",
    response_model=DepartamentoResponse
)
def consultar_departamento(
        departamento_id: str,
        db: Session = Depends(get_db)
):
    departamento = obtener_departamento(db, departamento_id)

    if departamento is None:
        raise HTTPException(
            status_code=status.HTTP_404_NOT_FOUND,
            detail=f"El departamento con id {departamento_id} no existe"
        )

    return departamento


@app.get(
    "/departamentos",
    response_model=list[DepartamentoResponse]
)
def listar_departamentos(
        db: Session = Depends(get_db)
):
    return obtener_departamentos(db)