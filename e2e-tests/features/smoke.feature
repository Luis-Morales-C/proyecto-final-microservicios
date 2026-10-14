# language: es

Característica: Verificación del sistema
  Como equipo de desarrollo
  Quiero comprobar que el punto de entrada está disponible
  Para ejecutar el resto de la suite sobre un sistema operativo

  Escenario: El sistema responde correctamente
    Dado que el sistema está desplegado y operativo
    Entonces la respuesta debe tener código 200
