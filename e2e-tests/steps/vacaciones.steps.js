import { Given, Then, When } from "@cucumber/cucumber";
import assert from "node:assert/strict";
import { bearer, requestJson } from "../support/http.js";
import { esperarHastaQue } from "../support/polling.js";
import {
    fechaISO,
    forzarFin,
    forzarInicio,
    login,
    programarVacacion,
    esperarNotificacion
} from "../support/fixtures.js";

When("programo un período de vacaciones válido", async function () {
    await programarVacacion(this, this.empleadoId, fechaISO(3), fechaISO(5));
});

Then("eventualmente se debe registrar una notificación de vacaciones programadas", async function () {
    const notificacion = await esperarNotificacion(
        this,
        this.empleadoId,
        n => n.tipo === "VACACIONES" && n.mensaje?.includes("programadas"),
        `la confirmación de vacaciones de ${this.empleadoId}`
    );
    assert.equal(notificacion.empleadoId, this.empleadoId);
});

When("intento programar vacaciones con fecha fin anterior a fecha inicio", async function () {
    const result = await requestJson(this.baseUrl, "/vacaciones", {
        method: "POST",
        headers: bearer(this.adminToken),
        body: {
            empleadoId: this.empleadoId,
            fechaInicio: fechaISO(8),
            fechaFin: fechaISO(7)
        }
    });
    this.response = result.response;
    this.responseBody = result.body;
});

Given("que el empleado ya tiene un período de vacaciones programado", async function () {
    const result = await programarVacacion(
        this,
        this.empleadoId,
        fechaISO(10),
        fechaISO(15)
    );
    assert.equal(result.response.status, 201);
    this.testData.periodoBase = result.body;
});

When("intento programar otro período que se solapa", async function () {
    const result = await requestJson(this.baseUrl, "/vacaciones", {
        method: "POST",
        headers: bearer(this.adminToken),
        body: {
            empleadoId: this.empleadoId,
            fechaInicio: fechaISO(12),
            fechaFin: fechaISO(18)
        }
    });
    this.response = result.response;
    this.responseBody = result.body;
});

Given("que el empleado tiene un período futuro programado", async function () {
    const result = await programarVacacion(
        this,
        this.empleadoId,
        fechaISO(20),
        fechaISO(22)
    );
    assert.equal(result.response.status, 201);
});

When("fuerzo el inicio del período de vacaciones", async function () {
    const result = await forzarInicio(this, this.vacacionId);
    this.response = result.response;
    this.responseBody = result.body;
    assert.equal(
        result.response.status,
        200,
        `No se pudo forzar el inicio. Verifique ENABLE_DEV_ENDPOINTS=true. ${JSON.stringify(result.body)}`
    );
});

Then("eventualmente el empleado no puede iniciar sesión", async function () {
    await esperarHastaQue(async () => {
        const result = await login(this, this.email, this.password);
        return result.response.status === 401;
    }, { descripcion: "la suspensión temporal de la cuenta" });
});

When("fuerzo el fin del período de vacaciones", async function () {
    const result = await forzarFin(this, this.vacacionId);
    this.response = result.response;
    this.responseBody = result.body;
    assert.equal(result.response.status, 200, JSON.stringify(result.body));
});

Then("eventualmente el empleado puede iniciar sesión nuevamente", async function () {
    await esperarHastaQue(async () => {
        const result = await login(this, this.email, this.password);
        return result.response.status === 200;
    }, { descripcion: "la reactivación de la cuenta al finalizar vacaciones" });
});
