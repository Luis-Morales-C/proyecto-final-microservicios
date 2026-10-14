import { Then, When } from "@cucumber/cucumber";
import assert from "node:assert/strict";
import { bearer, requestJson } from "../support/http.js";
import {
    crearEmpleado,
    esperarTokenActivacion,
    esperarNotificacion,
    login,
    fechaISO
} from "../support/fixtures.js";

When("registro un empleado nuevo con datos válidos", async function () {
    await crearEmpleado(this);
});

Then("eventualmente auth-service debe haber creado su cuenta", async function () {
    this.testData.resetToken = await esperarTokenActivacion(this, this.email);
});

Then(
    "eventualmente se debe registrar una notificación de seguridad para el empleado",
    async function () {
        const notificacion = await esperarNotificacion(
            this,
            this.empleadoId,
            n => n.tipo === "SEGURIDAD",
            `la notificación SEGURIDAD de ${this.empleadoId}`
        );
        assert.equal(notificacion.empleadoId, this.empleadoId);
    }
);

When("establezco la contraseña del nuevo empleado", async function () {
    const tokenInfo = this.testData.resetToken ??
        await esperarTokenActivacion(this, this.email);
    this.password = "ClaveBDD123";

    const result = await requestJson(this.baseUrl, "/auth/reset-password", {
        method: "POST",
        body: {
            token: tokenInfo.token,
            newPassword: this.password
        }
    });
    this.response = result.response;
    this.responseBody = result.body;
});

Then("el nuevo empleado puede iniciar sesión correctamente", async function () {
    const result = await login(this, this.email, this.password);
    assert.equal(
        result.response.status,
        200,
        `El login del nuevo empleado falló: ${JSON.stringify(result.body)}`
    );
    assert.ok(result.body.accessToken, "El login no retornó accessToken");
});

When("intento registrar un empleado con departamento inexistente", async function () {
    const inexistente = `NO${Date.now().toString(36).toUpperCase()}`.slice(0, 20);
    await crearEmpleado(this, { departamentoId: inexistente });
});

When("intento registrar un empleado con campos faltantes", async function () {
    const result = await requestJson(this.baseUrl, "/empleados", {
        method: "POST",
        headers: bearer(this.adminToken),
        body: {
            id: `E-FALTANTE-${Date.now()}`,
            nombre: "Incompleto",
            fechaIngreso: fechaISO(0)
        }
    });
    this.response = result.response;
    this.responseBody = result.body;
});
