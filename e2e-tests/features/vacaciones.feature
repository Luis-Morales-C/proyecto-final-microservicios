# language: es

Característica: Gestión y ciclo de vida de vacaciones
  Como administrador de RRHH
  Quiero programar vacaciones con reglas consistentes
  Para suspender y reactivar automáticamente el acceso del empleado

  Antecedentes:
    Dado que el sistema está desplegado y operativo
    Y que existe un empleado activo con credenciales establecidas
    Y que estoy autenticado como "ADMIN"

  Escenario: Programación exitosa genera notificación de confirmación
    Cuando programo un período de vacaciones válido
    Entonces la respuesta debe tener código 201
    Y eventualmente se debe registrar una notificación de vacaciones programadas

  Escenario: Rechazo por fechas incoherentes
    Cuando intento programar vacaciones con fecha fin anterior a fecha inicio
    Entonces la respuesta debe tener código 418

  Escenario: Rechazo por solapamiento
    Dado que el empleado ya tiene un período de vacaciones programado
    Cuando intento programar otro período que se solapa
    Entonces la respuesta debe tener código 400

  Escenario: La cuenta se suspende al iniciar las vacaciones y se reactiva al terminar
    Dado que el empleado tiene un período futuro programado
    Cuando fuerzo el inicio del período de vacaciones
    Entonces eventualmente el empleado no puede iniciar sesión
    Cuando fuerzo el fin del período de vacaciones
    Entonces eventualmente el empleado puede iniciar sesión nuevamente
