from sqlalchemy.orm import Session

from app.models import Departamento
from app.schemas import DepartamentoCreate


def crear_departamento(db: Session, departamento: DepartamentoCreate):
    nuevo_departamento = Departamento(
        id=departamento.id,
        nombre=departamento.nombre,
        descripcion=departamento.descripcion
    )

    db.add(nuevo_departamento)
    db.commit()
    db.refresh(nuevo_departamento)

    return nuevo_departamento


def obtener_departamento(db: Session, departamento_id: str):
    return db.query(Departamento).filter(
        Departamento.id == departamento_id
    ).first()


def obtener_departamentos(db: Session):
    return db.query(Departamento).all()