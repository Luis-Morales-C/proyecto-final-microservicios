import { When } from "@cucumber/cucumber";
import { bearer, requestJson } from "../support/http.js";
import {
    autenticarAdmin,
    crearEmpleado,
    crearDepartamento,
    esperarPerfil
} from "../support/fixtures.js";

When("consulto la lista de empleados sin token de autenticación", async function () {
    const result = await requestJson(this.baseUrl, "/empleados");
    this.response = result.response;
    this.responseBody = result.body;
});

When("consulto la lista de empleados con un token inválido", async function () {
    const result = await requestJson(this.baseUrl, "/empleados", {
        headers: bearer("token-invalido")
    });
    this.response = result.response;
    this.responseBody = result.body;
});

When("consulto la lista de empleados", async function () {
    const result = await requestJson(this.baseUrl, "/empleados", {
        headers: bearer(this.token)
    });
    this.response = result.response;
    this.responseBody = result.body;
});

When("intento retirar mi empleado como USER", async function () {
    const result = await requestJson(this.baseUrl, `/empleados/${this.empleadoId}`, {
        method: "DELETE",
        headers: bearer(this.token)
    });
    this.response = result.response;
    this.responseBody = result.body;
});

When("actualizo mi propio perfil", async function () {
    await esperarPerfil(this, this.empleadoId);

    const result = await requestJson(this.baseUrl, `/perfiles/${this.empleadoId}`, {
        method: "PUT",
        headers: bearer(this.token),
        body: {
            telefono: "3001234567",
            ciudad: "Armenia",
            biografia: "Perfil actualizado por prueba BDD"
        }
    });
    this.response = result.response;
    this.responseBody = result.body;
});

When("intento actualizar el perfil de otro empleado", async function () {
    await autenticarAdmin(this);
    const otro = await crearEmpleado(this);
    await esperarPerfil(this, otro.empleadoId);

    const result = await requestJson(this.baseUrl, `/perfiles/${otro.empleadoId}`, {
        method: "PUT",
        headers: bearer(this.token),
        body: { ciudad: "Armenia" }
    });
    this.response = result.response;
    this.responseBody = result.body;
});

When("creo un departamento como ADMIN", async function () {
    await autenticarAdmin(this);
    const id = `A${Date.now().toString(36).toUpperCase()}`.slice(0, 20);
    const result = await requestJson(this.baseUrl, "/departamentos", {
        method: "POST",
        headers: bearer(this.adminToken),
        body: {
            id,
            nombre: "Departamento Admin",
            descripcion: "Creado por escenario de autorización ADMIN"
        }
    });
    this.response = result.response;
    this.responseBody = result.body;
});
