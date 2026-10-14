import { Given, Then } from "@cucumber/cucumber";
import assert from "node:assert/strict";
import { bearer, requestJson } from "../support/http.js";
import {
    autenticarAdmin,
    crearUsuarioActivo
} from "../support/fixtures.js";

Given("que el sistema está desplegado y operativo", async function () {
    const { response, body } = await requestJson(this.baseUrl, "/health");
    this.response = response;
    this.responseBody = body;

    assert.equal(
        response.status,
        200,
        `El Gateway no está saludable: ${response.status} ${JSON.stringify(body)}`
    );
});

Given("que estoy autenticado como {string}", async function (rol) {
    if (rol === "ADMIN") {
        this.token = await autenticarAdmin(this);
        return;
    }

    if (rol === "USER") {
        if (!this.empleadoId || !this.password || !this.email) {
            await crearUsuarioActivo(this);
        }
        return;
    }

    throw new Error(`Rol de prueba no soportado: ${rol}`);
});

Given("que existe un empleado activo con credenciales establecidas", async function () {
    await crearUsuarioActivo(this);
});

Then("la respuesta debe tener código {int}", function (codigoEsperado) {
    assert.ok(this.response, "No existe una respuesta HTTP para validar");
    assert.equal(
        this.response.status,
        codigoEsperado,
        `Se esperaba HTTP ${codigoEsperado}, se obtuvo ${this.response.status}. ` +
        `Cuerpo: ${JSON.stringify(this.responseBody)}`
    );
});
