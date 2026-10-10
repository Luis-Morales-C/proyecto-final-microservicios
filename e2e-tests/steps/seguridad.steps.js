import { Given, When, Then } from "@cucumber/cucumber";
import assert from "node:assert";

When(
    "intento acceder a un recurso protegido sin autenticación",
    async function () {
        this.response = await fetch(
            `${this.baseUrl}/empleados`
        );
    }
);

When(
    "intento acceder a un recurso protegido con un token inválido",
    async function () {
        this.response = await fetch(
            `${this.baseUrl}/empleados`,
            {
                headers: {
                    Authorization: "Bearer token-invalido"
                }
            }
        );
    }
);

Then(
    "la respuesta debe tener código {int}",
    function (codigoEsperado) {
        assert.strictEqual(
            this.response.status,
            codigoEsperado
        );
    }
);

Given(
    "que estoy autenticado como USER",
    async function () {
        const response = await fetch(
            `${this.baseUrl}/auth/login`,
            {
                method: "POST",
                headers: {
                    "Content-Type": "application/json"
                },
                body: JSON.stringify({
                    email: "laura@empresa.com",
                    password: "Laura123!"
                })
            }
        );

        assert.strictEqual(response.status, 200);

        const body = await response.json();

        this.token = body.accessToken;
        this.empleadoId = "E0021";
    }
);

When(
    "consulto mi propio empleado",
    async function () {
        this.response = await fetch(
            `${this.baseUrl}/empleados/${this.empleadoId}`,
            {
                headers: {
                    Authorization: `Bearer ${this.token}`
                }
            }
        );
    }
);

When(
    "intento modificar un empleado",
    async function () {
        this.response = await fetch(
            `${this.baseUrl}/empleados/${this.empleadoId}`,
            {
                method: "PUT",
                headers: {
                    "Content-Type": "application/json",
                    Authorization: `Bearer ${this.token}`
                },
                body: JSON.stringify({
                    nombre: "Laura Modificada"
                })
            }
        );
    }
);

Given(
    "que estoy autenticado como ADMIN",
    async function () {
        const response = await fetch(
            `${this.baseUrl}/auth/login`,
            {
                method: "POST",
                headers: {
                    "Content-Type": "application/json"
                },
                body: JSON.stringify({
                    email: process.env.ADMIN_EMAIL,
                    password: process.env.ADMIN_PASSWORD
                })
            }
        );

        assert.strictEqual(
            response.status,
            200,
            "No fue posible iniciar sesión como ADMIN"
        );

        const body = await response.json();

        this.token = body.accessToken;
        this.empleadoId = "E0021";
    }
);

When(
    "modifico un empleado",
    async function () {
        this.response = await fetch(
            `${this.baseUrl}/empleados/${this.empleadoId}`,
            {
                method: "PUT",
                headers: {
                    "Content-Type": "application/json",
                    Authorization: `Bearer ${this.token}`
                },
                body: JSON.stringify({
                    nombre: "Laura",
                    apellido: "Cardenas",
                    email: "laura@empresa.com",
                    cargo: "Analista",
                    area: "Recursos Humanos",
                    departamentoId: "RRHH"
                })
            }
        );
    }
);

Then(
    "la operación debe ser permitida",
    function () {
        assert.notStrictEqual(
            this.response.status,
            401
        );

        assert.notStrictEqual(
            this.response.status,
            403
        );
    }
);