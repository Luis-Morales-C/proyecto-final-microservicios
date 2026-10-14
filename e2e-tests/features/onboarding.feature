# language: es

Característica: Onboarding de empleados
  Como administrador del sistema
  Quiero registrar nuevos empleados
  Para que su cuenta y sus notificaciones se creen automáticamente

  Antecedentes:
    Dado que el sistema está desplegado y operativo
    Y que estoy autenticado como "ADMIN"

  Escenario: Registro exitoso genera cuenta y notificación de seguridad
    Cuando registro un empleado nuevo con datos válidos
    Entonces la respuesta debe tener código 201
    Y eventualmente auth-service debe haber creado su cuenta
    Y eventualmente se debe registrar una notificación de seguridad para el empleado

  Escenario: El nuevo empleado establece su contraseña e inicia sesión
    Cuando registro un empleado nuevo con datos válidos
    Entonces la respuesta debe tener código 201
    Y eventualmente auth-service debe haber creado su cuenta
    Cuando establezco la contraseña del nuevo empleado
    Entonces la respuesta debe tener código 200
    Y el nuevo empleado puede iniciar sesión correctamente

  Escenario: Registro rechazado por departamento inexistente
    Cuando intento registrar un empleado con departamento inexistente
    Entonces la respuesta debe tener código 400

  Escenario: Registro rechazado por campos obligatorios faltantes
    Cuando intento registrar un empleado con campos faltantes
    Entonces la respuesta debe tener código 400
