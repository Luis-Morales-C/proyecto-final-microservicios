from sqlalchemy import Column, String

from app.database import Base


class Departamento(Base):

    __tablename__ = "departamentos"

    id = Column(
        String(20),
        primary_key=True,
        index=True,
        nullable=False
    )

    nombre = Column(
        String(100),
        nullable=False
    )

    descripcion = Column(
        String(255),
        nullable=False
    )