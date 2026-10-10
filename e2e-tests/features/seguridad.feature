# language: es

Característica: Seguridad y control de acceso

  Escenario: Acceso a un recurso protegido sin token
    Cuando intento acceder a un recurso protegido sin autenticación
    Entonces la respuesta debe tener código 401

  Escenario: Acceso con token inválido
    Cuando intento acceder a un recurso protegido con un token inválido
    Entonces la respuesta debe tener código 401

  Escenario: Un usuario USER consulta su propio perfil
    Dado que estoy autenticado como USER
    Cuando consulto mi propio empleado
    Entonces la respuesta debe tener código 200

  Escenario: Un usuario USER intenta modificar información no permitida
    Dado que estoy autenticado como USER
    Cuando intento modificar un empleado
    Entonces la respuesta debe tener código 403

  Escenario: Un usuario ADMIN modifica un empleado
    Dado que estoy autenticado como ADMIN
    Cuando modifico un empleado
    Entonces la operación debe ser permitida