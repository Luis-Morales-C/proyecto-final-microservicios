# Imagen con definitions.json DENTRO: evita el bind mount, que falla cuando
# "docker compose" se ejecuta desde el contenedor Jenkins (la ruta se resuelve en el host).
FROM curlimages/curl:8.16.0
COPY definitions.json /definitions.json
