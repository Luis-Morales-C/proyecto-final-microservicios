import { Given, Then, When } from "@cucumber/cucumber";
import assert from "node:assert/strict";
import { bearer, requestJson } from "../support/http.js";
import { esperarHastaQue } from "../support/polling.js";
import {
    esperarNotificacion,
    fechaISO,
    forzarFin,
    forzarInicio,
    login,
    programarVacacion
} from "../support/fixtures.js";

When("retiro al empleado activo", async function () {
    const result = await requestJson(this.baseUrl, `/empleados/${this.empleadoId}`, {
        method: "DELETE",
        headers: bearer(this.adminToken)
    });
    this.response = result.response;
    this.responseBody = result.body;
});

Then("eventualmente se registra la notificación de desvinculación", async function () {
    const n = await esperarNotificacion(
        this,
        this.empleadoId,
        item => item.tipo === "CUENTA" &&
            (item.mensaje?.includes("permanente") || item.mensaje?.includes("retiro")),
        `la notificación de retiro de ${this.empleadoId}`
    );
    assert.equal(n.empleadoId, this.empleadoId);
});

Then("eventualmente el empleado retirado no puede iniciar sesión", async function () {
    await esperarHastaQue(async () => {
        const result = await login(this, this.email, this.password);
        return result.response.status === 401;
    }, { descripcion: "la desactivación permanente de la cuenta" });
});

Then("el empleado retirado aparece en la auditoría de retirados", async function () {
    const result = await requestJson(this.baseUrl, "/empleados?estado=RETIRADO", {
        headers: bearer(this.adminToken)
    });

    assert.equal(result.response.status, 200, JSON.stringify(result.body));
    const lista = result.body?.respuesta;
    assert.ok(Array.isArray(lista), "La respuesta de auditoría no contiene una lista");
    assert.ok(
        lista.some(e => e.id === this.empleadoId || e.empleadoId === this.empleadoId),
        `No se encontró ${this.empleadoId} en la auditoría`
    );
});

When("solicito recuperar la contraseña del empleado retirado", async function () {
    const result = await requestJson(this.baseUrl, "/auth/recover-password", {
        method: "POST",
        body: { email: this.email }
    });
    this.response = result.response;
    this.responseBody = result.body;
});

Then("no se genera un token de recuperación para el empleado retirado", async function () {
    const result = await requestJson(
        this.baseUrl,
        `/auth/test/reset-token?email=${encodeURIComponent(this.email)}`,
        { headers: bearer(this.adminToken) }
    );

    assert.equal(
        result.response.status,
        404,
        `Se generó o conservó un token para una cuenta retirada: ${JSON.stringify(result.body)}`
    );
});

Given("que el empleado tiene vacaciones en curso", async function () {
    const programada = await programarVacacion(
        this,
        this.empleadoId,
        fechaISO(25),
        fechaISO(27)
    );
    assert.equal(programada.response.status, 201);

    const inicio = await forzarInicio(this, this.vacacionId);
    assert.equal(
        inicio.response.status,
        200,
        `No se pudo forzar inicio; verifique ENABLE_DEV_ENDPOINTS=true: ${JSON.stringify(inicio.body)}`
    );

    await esperarHastaQue(async () => {
        const result = await login(this, this.email, this.password);
        return result.response.status === 401;
    }, { descripcion: "la suspensión por vacaciones antes del retiro" });
});

Then("eventualmente el empleado retirado sigue sin poder iniciar sesión", async function () {
    await esperarHastaQue(async () => {
        const result = await login(this, this.email, this.password);
        return result.response.status === 401;
    }, { descripcion: "confirmar que una baja permanente no se reactiva" });
});
