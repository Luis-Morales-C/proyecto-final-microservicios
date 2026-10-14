# language: es

Característica: Seguridad y control de acceso
  Como sistema de autenticación
  Quiero controlar el acceso a los recursos
  Para que solo los usuarios autorizados realicen operaciones

  Antecedentes:
    Dado que el sistema está desplegado y operativo

  Escenario: Acceso denegado sin token de autenticación
    Cuando consulto la lista de empleados sin token de autenticación
    Entonces la respuesta debe tener código 401

  Escenario: Acceso denegado con token inválido
    Cuando consulto la lista de empleados con un token inválido
    Entonces la respuesta debe tener código 401

  Escenario: Un USER puede consultar recursos pero no eliminarlos
    Dado que existe un empleado activo con credenciales establecidas
    Y que estoy autenticado como "USER"
    Cuando consulto la lista de empleados
    Entonces la respuesta debe tener código 200
    Cuando intento retirar mi empleado como USER
    Entonces la respuesta debe tener código 403

  Escenario: Un USER actualiza su propio perfil pero no el de otro empleado
    Dado que existe un empleado activo con credenciales establecidas
    Y que estoy autenticado como "USER"
    Cuando actualizo mi propio perfil
    Entonces la respuesta debe tener código 200
    Cuando intento actualizar el perfil de otro empleado
    Entonces la respuesta debe tener código 403

  Escenario: Un ADMIN puede realizar operaciones de escritura
    Dado que estoy autenticado como "ADMIN"
    Cuando creo un departamento como ADMIN
    Entonces la respuesta debe tener código 201
