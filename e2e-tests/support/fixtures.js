import assert from "node:assert/strict";
import { bearer, requestJson } from "./http.js";
import { esperarHastaQue } from "./polling.js";

function sufijo() {
    const tiempo = Date.now().toString(36).toUpperCase();
    const aleatorio = Math.random().toString(36).slice(2, 6).toUpperCase();
    return `${tiempo}${aleatorio}`;
}

export function fechaISO(offsetDays = 0) {
    const d = new Date();
    d.setUTCDate(d.getUTCDate() + offsetDays);
    return d.toISOString().slice(0, 10);
}

export async function autenticarAdmin(world) {
    if (world.adminToken) return world.adminToken;

    const { response, body } = await requestJson(world.baseUrl, "/auth/login", {
        method: "POST",
        body: {
            email: world.adminEmail,
            password: world.adminPassword
        }
    });

    assert.equal(response.status, 200,
        `No fue posible autenticar el ADMIN. Respuesta: ${JSON.stringify(body)}`);

    world.adminToken = body.accessToken;
    return world.adminToken;
}

export async function crearDepartamento(world) {
    await autenticarAdmin(world);
    const id = `T${sufijo()}`.slice(0, 20);

    const { response, body } = await requestJson(world.baseUrl, "/departamentos", {
        method: "POST",
        headers: bearer(world.adminToken),
        body: {
            id,
            nombre: "Pruebas Automatizadas",
            descripcion: "Departamento generado por la suite BDD"
        }
    });

    assert.equal(response.status, 201,
        `No se pudo crear el departamento ${id}: ${JSON.stringify(body)}`);

    world.departamentoId = id;
    return id;
}

export async function crearEmpleado(world, overrides = {}) {
    await autenticarAdmin(world);
    const departamentoId = overrides.departamentoId ?? await crearDepartamento(world);
    const unique = sufijo();
    const empleadoId = overrides.empleadoId ?? `E-${unique}`;
    const email = overrides.email ?? `${empleadoId.toLowerCase()}@empresa.com`;

    const payload = {
        id: empleadoId,
        nombre: "Usuario",
        apellido: "BDD",
        email,
        numeroEmpleado: `EMP-${unique}`,
        cargo: "Analista",
        area: "Pruebas",
        departamentoId,
        fechaIngreso: fechaISO(0)
    };

    const result = await requestJson(world.baseUrl, "/empleados", {
        method: "POST",
        headers: bearer(world.adminToken),
        body: payload
    });

    world.response = result.response;
    world.responseBody = result.body;

    if (result.response.status === 201) {
        world.empleadoId = empleadoId;
        world.email = email;
        world.testData.empleado = result.body?.respuesta ?? result.body;
    }

    return { ...result, empleadoId, email, payload };
}

export async function esperarTokenActivacion(world, email) {
    await autenticarAdmin(world);

    return esperarHastaQue(async () => {
        const { response, body } = await requestJson(
            world.baseUrl,
            `/auth/test/reset-token?email=${encodeURIComponent(email)}`,
            { headers: bearer(world.adminToken) }
        );

        if (response.status === 404) return false;
        if (response.status === 401 || response.status === 403) {
            throw new Error("El endpoint E2E de Auth no está autorizado");
        }
        if (response.status !== 200) {
            throw new Error(`Auth test endpoint respondió ${response.status}`);
        }

        return body;
    }, {
        descripcion:
            "el token de activación. Verifique ENABLE_DEV_ENDPOINTS=true en el .env"
    });
}

export async function activarEmpleado(world, email, password = "ClaveBDD123") {
    const tokenInfo = await esperarTokenActivacion(world, email);

    const { response, body } = await requestJson(world.baseUrl, "/auth/reset-password", {
        method: "POST",
        body: {
            token: tokenInfo.token,
            newPassword: password
        }
    });

    assert.equal(response.status, 200,
        `No se pudo activar ${email}: ${JSON.stringify(body)}`);

    world.password = password;
    return password;
}

export async function login(world, email, password) {
    return requestJson(world.baseUrl, "/auth/login", {
        method: "POST",
        body: { email, password }
    });
}

export async function crearUsuarioActivo(world) {
    const creado = await crearEmpleado(world);
    assert.equal(creado.response.status, 201,
        `No se pudo crear empleado: ${JSON.stringify(creado.body)}`);

    const password = await activarEmpleado(world, creado.email);
    const loginResult = await login(world, creado.email, password);
    assert.equal(loginResult.response.status, 200,
        `El usuario recién activado no pudo iniciar sesión: ${JSON.stringify(loginResult.body)}`);

    world.token = loginResult.body.accessToken;
    world.email = creado.email;
    world.empleadoId = creado.empleadoId;
    world.password = password;

    return creado;
}

export async function esperarPerfil(world, empleadoId) {
    await autenticarAdmin(world);

    return esperarHastaQue(async () => {
        const { response, body } = await requestJson(
            world.baseUrl,
            `/perfiles/${empleadoId}`,
            { headers: bearer(world.adminToken) }
        );
        if (response.status === 404) return false;
        if (response.status !== 200) {
            throw new Error(`Perfil respondió ${response.status}: ${JSON.stringify(body)}`);
        }
        return body;
    }, { descripcion: `el perfil de ${empleadoId}` });
}

export async function esperarNotificacion(world, empleadoId, predicado, descripcion) {
    await autenticarAdmin(world);

    return esperarHastaQue(async () => {
        const { response, body } = await requestJson(
            world.baseUrl,
            `/notificaciones/${empleadoId}`,
            { headers: bearer(world.adminToken) }
        );

        if (response.status !== 200) {
            throw new Error(`Notificaciones respondió ${response.status}`);
        }

        if (!Array.isArray(body)) return false;
        return body.find(predicado) || false;
    }, { descripcion });
}

export async function programarVacacion(world, empleadoId, inicio, fin) {
    await autenticarAdmin(world);

    const result = await esperarHastaQue(async () => {
        const intento = await requestJson(world.baseUrl, "/vacaciones", {
            method: "POST",
            headers: bearer(world.adminToken),
            body: { empleadoId, fechaInicio: inicio, fechaFin: fin }
        });

        // La réplica de empleados en vacaciones es eventual. Mientras aún no
        // haya llegado empleado.creado, reintentamos; cualquier otro 400 es real.
        if (intento.response.status === 400 &&
            JSON.stringify(intento.body).includes("no existe o se encuentra retirado")) {
            return false;
        }
        return intento;
    }, { descripcion: `la sincronización del empleado ${empleadoId} en vacaciones` });

    world.response = result.response;
    world.responseBody = result.body;
    if (result.response.status === 201) world.vacacionId = result.body.id;
    return result;
}

export async function forzarInicio(world, vacacionId) {
    await autenticarAdmin(world);
    return requestJson(world.baseUrl, `/vacaciones/${vacacionId}/forzar-inicio`, {
        method: "POST",
        headers: bearer(world.adminToken)
    });
}

export async function forzarFin(world, vacacionId) {
    await autenticarAdmin(world);
    return requestJson(world.baseUrl, `/vacaciones/${vacacionId}/forzar-fin`, {
        method: "POST",
        headers: bearer(world.adminToken)
    });
}
