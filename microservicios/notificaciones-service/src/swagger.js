const swaggerDocument = {
    openapi: "3.0.3",

    info: {
        title: "API de Notificaciones",
        version: "1.0.0",
        description:
            "Consulta del historial de notificaciones generadas a partir de eventos de RabbitMQ."
    },

    servers: [
        {
            url: "/"
        }
    ],

    paths: {
        "/notificaciones": {
            get: {
                summary: "Listar todas las notificaciones",
                tags: ["Notificaciones"],
                responses: {
                    200: {
                        description: "Historial de notificaciones",
                        content: {
                            "application/json": {
                                schema: {
                                    type: "array",
                                    items: {
                                        $ref: "#/components/schemas/Notificacion"
                                    }
                                }
                            }
                        }
                    }
                }
            }
        },

        "/notificaciones/{empleadoId}": {
            get: {
                summary: "Listar notificaciones de un empleado",
                tags: ["Notificaciones"],
                parameters: [
                    {
                        name: "empleadoId",
                        in: "path",
                        required: true,
                        schema: {
                            type: "string"
                        }
                    }
                ],
                responses: {
                    200: {
                        description: "Notificaciones del empleado",
                        content: {
                            "application/json": {
                                schema: {
                                    type: "array",
                                    items: {
                                        $ref: "#/components/schemas/Notificacion"
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    },

    components: {
        schemas: {
            Notificacion: {
                type: "object",
                properties: {
                    id: {
                        type: "string",
                        format: "uuid"
                    },
                    tipo: {
                        type: "string",
                        enum: [
                            "BIENVENIDA",
                            "DESVINCULACION",
                            "VACACIONES"
                        ]
                    },
                    destinatario: {
                        type: "string",
                        format: "email"
                    },
                    mensaje: {
                        type: "string"
                    },
                    fechaEnvio: {
                        type: "string",
                        format: "date-time"
                    },
                    empleadoId: {
                        type: "string"
                    }
                }
            }
        }
    }
};

module.exports = swaggerDocument;