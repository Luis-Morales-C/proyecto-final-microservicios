# language: es

Característica: Offboarding de empleados
  Como administrador de RRHH
  Quiero retirar empleados de forma lógica
  Para conservar auditoría y bloquear definitivamente su cuenta

  Antecedentes:
    Dado que el sistema está desplegado y operativo
    Y que existe un empleado activo con credenciales establecidas
    Y que estoy autenticado como "ADMIN"

  Escenario: Desvinculación completa de un empleado
    Cuando retiro al empleado activo
    Entonces la respuesta debe tener código 200
    Y eventualmente se registra la notificación de desvinculación
    Y eventualmente el empleado retirado no puede iniciar sesión
    Y el empleado retirado aparece en la auditoría de retirados

  Escenario: La recuperación de contraseña no genera token para un empleado retirado
    Cuando retiro al empleado activo
    Entonces la respuesta debe tener código 200
    Y eventualmente el empleado retirado no puede iniciar sesión
    Cuando solicito recuperar la contraseña del empleado retirado
    Entonces la respuesta debe tener código 202
    Y no se genera un token de recuperación para el empleado retirado

  Escenario: Retirar durante vacaciones no reactiva la cuenta al finalizar el período
    Dado que el empleado tiene vacaciones en curso
    Cuando retiro al empleado activo
    Entonces la respuesta debe tener código 200
    Cuando fuerzo el fin del período de vacaciones
    Entonces eventualmente el empleado retirado sigue sin poder iniciar sesión
