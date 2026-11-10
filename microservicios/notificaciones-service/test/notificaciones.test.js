'use strict';

process.env.APP_RESET_URL = 'https://test.local/reset';

const test = require('node:test');
const assert = require('node:assert/strict');

const repository = require('../src/repositories/notificacionRepository');
const { pool } = require('../src/config/database');
const { procesarEvento } = require('../src/services/notificacionService');
const controller = require('../src/controllers/notificacionController');
const swagger = require('../src/swagger');
const amqp = require('amqplib');
const { iniciarConsumidor } = require('../src/consumers/notificacionConsumer');

const originalRepository = { ...repository };
const originalPoolQuery = pool.query.bind(pool);
const originalPoolConnect = pool.connect.bind(pool);
const originalConsoleLog = console.log;
const originalConsoleError = console.error;
const originalAmqpConnect = amqp.connect;

function envelope(type, data = {}, id = 'evt-1') {
    return {
        id,
        type,
        version: 1,
        occurredAt: '2026-11-07T12:00:00Z',
        producer: 'tests',
        data
    };
}

function fakeResponse() {
    return {
        statusCode: 200,
        body: undefined,
        status(code) {
            this.statusCode = code;
            return this;
        },
        json(value) {
            this.body = value;
            return this;
        }
    };
}

function restore() {
    for (const [key, value] of Object.entries(originalRepository)) {
        repository[key] = value;
    }
    pool.query = originalPoolQuery;
    pool.connect = originalPoolConnect;
    console.log = originalConsoleLog;
    console.error = originalConsoleError;
    amqp.connect = originalAmqpConnect;
}

test.afterEach(restore);

test('rechaza envelope incompleto', async () => {
    await assert.rejects(
        () => procesarEvento({ type: 'empleado.creado' }),
        /envelope canónico/
    );
});

test('evento no gestionado no persiste nada', async () => {
    const logs = [];
    console.log = (value) => logs.push(String(value));
    await procesarEvento(envelope('evento.desconocido'));
    assert.match(logs.join('\n'), /Evento no gestionado/);
});

test('empleado.creado sincroniza contacto activo', async () => {
    let captured;
    repository.registrarEmpleadoDesdeEvento = async (id, contacto) => {
        captured = { id, contacto };
        return { duplicado: false, notificacion: null };
    };
    console.log = () => {};

    await procesarEvento(envelope('empleado.creado', {
        empleadoId: 'E001', nombre: 'Ana', email: 'ana@empresa.com'
    }));

    assert.equal(captured.id, 'evt-1');
    assert.deepEqual(captured.contacto, {
        empleadoId: 'E001', nombre: 'Ana', email: 'ana@empresa.com', activo: true
    });
});

test('empleado.retirado sincroniza contacto inactivo conservando nombre', async () => {
    let captured;
    repository.registrarEmpleadoDesdeEvento = async (id, contacto) => {
        captured = contacto;
        return { duplicado: false, notificacion: null };
    };
    console.log = () => {};

    await procesarEvento(envelope('empleado.retirado', {
        empleadoId: 'E001', email: 'ana@empresa.com'
    }));

    assert.equal(captured.activo, false);
    assert.equal(captured.nombre, null);
});

test('empleado sin email es rechazado', async () => {
    await assert.rejects(
        () => procesarEvento(envelope('empleado.creado', { empleadoId: 'E001' })),
        /no contiene empleadoId o email/
    );
});

test('vacaciones.programadas crea notificacion usando contacto local', async () => {
    repository.obtenerContacto = async () => ({
        empleadoId: 'E001', nombre: 'Ana', email: 'ana@empresa.com', activo: true
    });
    let captured;
    repository.registrarDesdeEvento = async (id, notification) => {
        captured = notification;
        return { duplicado: false, notificacion: notification };
    };
    console.log = () => {};

    await procesarEvento(envelope('vacaciones.programadas', {
        empleadoId: 'E001', fechaInicio: '2026-12-01', fechaFin: '2026-12-05'
    }));

    assert.equal(captured.tipo, 'VACACIONES');
    assert.equal(captured.destinatario, 'ana@empresa.com');
    assert.match(captured.mensaje, /2026-12-01/);
    assert.match(captured.mensaje, /2026-12-05/);
});

test('vacaciones.programadas exige fechas', async () => {
    await assert.rejects(
        () => procesarEvento(envelope('vacaciones.programadas', { empleadoId: 'E001' })),
        /contrato de vacaciones.programadas/
    );
});

test('vacaciones sin contacto ni email es rechazada', async () => {
    repository.obtenerContacto = async () => null;
    await assert.rejects(
        () => procesarEvento(envelope('vacaciones.iniciadas', {
            empleadoId: 'E001', fechaFin: '2026-12-05'
        })),
        /No hay email/
    );
});

test('vacaciones iniciadas y finalizadas generan mensajes correctos', async () => {
    repository.obtenerContacto = async () => ({ nombre: 'Ana', email: 'ana@empresa.com' });
    const messages = [];
    repository.registrarDesdeEvento = async (id, notification) => {
        messages.push(notification.mensaje);
        return { duplicado: false, notificacion: notification };
    };
    console.log = () => {};

    await procesarEvento(envelope('vacaciones.iniciadas', {
        empleadoId: 'E001', fechaFin: '2026-12-05'
    }, 'evt-inicio'));
    await procesarEvento(envelope('vacaciones.finalizadas', {
        empleadoId: 'E001'
    }, 'evt-fin'));

    assert.match(messages[0], /suspendido/);
    assert.match(messages[1], /finalizó/);
});

test('usuario.creado persiste mensaje sin token y consola puede simular enlace', async () => {
    repository.obtenerContacto = async () => ({ empleadoId: 'E001', nombre: 'Ana' });
    repository.obtenerContactoPorEmail = async () => null;
    let persisted;
    repository.registrarDesdeEvento = async (id, notification) => {
        persisted = notification;
        return { duplicado: false, notificacion: notification };
    };
    const logs = [];
    console.log = (value) => logs.push(String(value));

    await procesarEvento(envelope('usuario.creado', {
        empleadoId: 'E001', email: 'ana@empresa.com', tokenActivacion: 'secreto-token',
        expiraEn: '2026-11-07T13:00:00Z'
    }));

    assert.equal(persisted.tipo, 'SEGURIDAD');
    assert.doesNotMatch(persisted.mensaje, /secreto-token/);
    assert.match(logs.join('\n'), /secreto-token/);
});

test('usuario.recuperacion busca contacto por email', async () => {
    repository.obtenerContactoPorEmail = async (email) => ({
        empleadoId: 'E007', nombre: 'Luis', email
    });
    repository.registrarDesdeEvento = async (id, notification) => ({
        duplicado: false, notificacion: notification
    });
    console.log = () => {};

    await procesarEvento(envelope('usuario.recuperacion', {
        email: 'luis@empresa.com', tokenRecuperacion: 'token-recuperacion'
    }));
});

test('evento de token incompleto es rechazado', async () => {
    await assert.rejects(
        () => procesarEvento(envelope('usuario.creado', { email: 'ana@empresa.com' })),
        /no cumple el contrato/
    );
});

test('cuenta desactivada permanente y temporal generan mensajes distintos', async () => {
    repository.obtenerContacto = async () => ({ nombre: 'Ana' });
    const messages = [];
    repository.registrarDesdeEvento = async (id, notification) => {
        messages.push(notification.mensaje);
        return { duplicado: false, notificacion: notification };
    };
    console.log = () => {};

    await procesarEvento(envelope('cuenta.desactivada', {
        empleadoId: 'E001', email: 'ana@empresa.com', motivo: 'RETIRO', permanente: true
    }, 'evt-perm'));
    await procesarEvento(envelope('cuenta.desactivada', {
        empleadoId: 'E001', email: 'ana@empresa.com', motivo: 'VACACIONES', permanente: false
    }, 'evt-temp'));

    assert.match(messages[0], /permanente/);
    assert.match(messages[1], /temporalmente/);
});

test('cuenta activada por fin de vacaciones usa bienvenida de regreso', async () => {
    repository.obtenerContacto = async () => ({ nombre: 'Ana' });
    let message;
    repository.registrarDesdeEvento = async (id, notification) => {
        message = notification.mensaje;
        return { duplicado: false, notificacion: notification };
    };
    console.log = () => {};

    await procesarEvento(envelope('cuenta.activada', {
        empleadoId: 'E001', email: 'ana@empresa.com', motivo: 'FIN_VACACIONES'
    }));

    assert.match(message, /Bienvenido de regreso/);
});

test('cuenta sin motivo es rechazada', async () => {
    await assert.rejects(
        () => procesarEvento(envelope('cuenta.activada', { email: 'ana@empresa.com' })),
        /no cumple el contrato/
    );
});

test('resultado duplicado se descarta sin imprimir notificacion', async () => {
    repository.registrarEmpleadoDesdeEvento = async () => ({ duplicado: true, notificacion: null });
    const logs = [];
    console.log = (value) => logs.push(String(value));

    await procesarEvento(envelope('empleado.creado', {
        empleadoId: 'E001', nombre: 'Ana', email: 'ana@empresa.com'
    }));

    assert.match(logs.join('\n'), /duplicado descartado/);
});

test('repository listarTodas y listarPorEmpleado devuelven filas', async () => {
    pool.query = async (sql, params) => {
        if (params) assert.deepEqual(params, ['E001']);
        return { rows: [{ id: 'n1' }] };
    };
    assert.deepEqual(await repository.listarTodas(), [{ id: 'n1' }]);
    assert.deepEqual(await repository.listarPorEmpleado('E001'), [{ id: 'n1' }]);
});

test('repository obtiene contacto por id y por email', async () => {
    pool.query = async (sql, params) => ({ rows: params[0] === 'nada' ? [] : [{ empleadoId: 'E001' }] });
    assert.deepEqual(await repository.obtenerContacto('E001'), { empleadoId: 'E001' });
    assert.equal(await repository.obtenerContacto('nada'), null);
    assert.deepEqual(await repository.obtenerContactoPorEmail('ana@empresa.com'), { empleadoId: 'E001' });
});

test('registrarDesdeEvento detecta duplicado y hace rollback', async () => {
    const calls = [];
    const client = {
        async query(sql) {
            calls.push(String(sql).trim());
            if (String(sql).includes('INSERT INTO eventos_procesados')) return { rowCount: 0 };
            return { rows: [] };
        },
        release() { calls.push('RELEASE'); }
    };
    pool.connect = async () => client;

    const result = await repository.registrarDesdeEvento('evt-dup', {
        tipo: 'CUENTA', destinatario: 'a@b.com', mensaje: 'x'
    });

    assert.equal(result.duplicado, true);
    assert.ok(calls.some((call) => call === 'ROLLBACK'));
    assert.ok(calls.includes('RELEASE'));
});

test('registrarDesdeEvento persiste y confirma transaccion', async () => {
    const calls = [];
    const saved = { id: 'n1', tipo: 'CUENTA' };
    const client = {
        async query(sql) {
            const text = String(sql);
            calls.push(text.trim());
            if (text.includes('INSERT INTO eventos_procesados')) return { rowCount: 1 };
            if (text.includes('INSERT INTO notificaciones')) return { rows: [saved] };
            return { rows: [] };
        },
        release() { calls.push('RELEASE'); }
    };
    pool.connect = async () => client;

    const result = await repository.registrarDesdeEvento('evt-ok', {
        tipo: 'CUENTA', destinatario: 'a@b.com', mensaje: 'x', empleadoId: 'E001'
    });

    assert.equal(result.duplicado, false);
    assert.deepEqual(result.notificacion, saved);
    assert.ok(calls.some((call) => call === 'COMMIT'));
});

test('registrarEmpleadoDesdeEvento hace upsert de contacto', async () => {
    const calls = [];
    const client = {
        async query(sql) {
            const text = String(sql);
            calls.push(text.trim());
            if (text.includes('INSERT INTO eventos_procesados')) return { rowCount: 1 };
            return { rows: [] };
        },
        release() { calls.push('RELEASE'); }
    };
    pool.connect = async () => client;

    const result = await repository.registrarEmpleadoDesdeEvento('evt-contacto', {
        empleadoId: 'E001', nombre: 'Ana', email: 'ana@empresa.com', activo: true
    });

    assert.equal(result.duplicado, false);
    assert.ok(calls.some((call) => call.includes('INSERT INTO empleados_contacto')));
    assert.ok(calls.some((call) => call === 'COMMIT'));
});

test('repository hace rollback si falla una transaccion', async () => {
    const calls = [];
    const client = {
        async query(sql) {
            const text = String(sql);
            calls.push(text.trim());
            if (text.includes('INSERT INTO eventos_procesados')) throw new Error('db fail');
            return { rows: [] };
        },
        release() { calls.push('RELEASE'); }
    };
    pool.connect = async () => client;

    await assert.rejects(
        () => repository.registrarDesdeEvento('evt-error', {
            tipo: 'CUENTA', destinatario: 'a@b.com', mensaje: 'x'
        }),
        /db fail/
    );
    assert.ok(calls.some((call) => call === 'ROLLBACK'));
});

test('controller listarTodas responde 200', async () => {
    repository.listarTodas = async () => [{ id: 'n1' }];
    const res = fakeResponse();
    await controller.listarTodas({}, res);
    assert.equal(res.statusCode, 200);
    assert.deepEqual(res.body, [{ id: 'n1' }]);
});

test('controller listarTodas responde 500 si repository falla', async () => {
    repository.listarTodas = async () => { throw new Error('db'); };
    console.error = () => {};
    const res = fakeResponse();
    await controller.listarTodas({}, res);
    assert.equal(res.statusCode, 500);
    assert.match(res.body.mensaje, /Error al consultar/);
});

test('controller listarPorEmpleado usa parametro y responde 200', async () => {
    let received;
    repository.listarPorEmpleado = async (id) => { received = id; return []; };
    const res = fakeResponse();
    await controller.listarPorEmpleado({ params: { empleadoId: 'E009' } }, res);
    assert.equal(received, 'E009');
    assert.equal(res.statusCode, 200);
});

test('swagger declara BearerAuth y rutas principales', () => {
    assert.equal(swagger.openapi, '3.0.3');
    assert.ok(swagger.components.securitySchemes.BearerAuth);
    assert.ok(swagger.paths['/notificaciones']);
    assert.ok(swagger.paths['/notificaciones/{empleadoId}']);
});


test('consumidor declara las tres colas y confirma eventos no gestionados', async () => {
    const callbacks = new Map();
    const acked = [];
    const channel = {
        async prefetch(value) { assert.equal(value, 1); },
        async assertQueue(name, options) { assert.equal(options.durable, true); },
        async consume(name, callback, options) {
            callbacks.set(name, callback);
            assert.equal(options.noAck, false);
        },
        ack(message) { acked.push(message); },
        nack() { throw new Error('no debería hacer nack'); }
    };
    const handlers = {};
    const connection = {
        async createChannel() { return channel; },
        on(event, callback) { handlers[event] = callback; }
    };
    amqp.connect = async (options) => {
        assert.equal(options.hostname, process.env.RABBITMQ_HOST || 'rabbitmq');
        return connection;
    };
    console.log = () => {};

    const returned = await iniciarConsumidor();
    assert.equal(returned, connection);
    assert.deepEqual([...callbacks.keys()].sort(), [
        'notificaciones.empleados',
        'notificaciones.seguridad',
        'notificaciones.vacaciones'
    ]);

    const message = { content: Buffer.from(JSON.stringify(envelope('evento.no-soportado'))) };
    await callbacks.get('notificaciones.empleados')(message);
    assert.equal(acked.length, 1);
    assert.equal(typeof handlers.error, 'function');
    assert.equal(typeof handlers.close, 'function');
});

test('consumidor procesa evento válido y hace ack', async () => {
    let callback;
    let acked = false;
    const channel = {
        async prefetch() {},
        async assertQueue() {},
        async consume(name, cb) {
            if (name === 'notificaciones.empleados') callback = cb;
        },
        ack() { acked = true; },
        nack() { throw new Error('no debería hacer nack'); }
    };
    amqp.connect = async () => ({
        async createChannel() { return channel; },
        on() {}
    });
    repository.registrarEmpleadoDesdeEvento = async () => ({ duplicado: false, notificacion: null });
    console.log = () => {};

    await iniciarConsumidor();
    const message = { content: Buffer.from(JSON.stringify(envelope('empleado.creado', {
        empleadoId: 'E001', nombre: 'Ana', email: 'ana@empresa.com'
    }))) };
    await callback(message);
    assert.equal(acked, true);
});

test('consumidor hace nack y reencola cuando el mensaje es inválido', async () => {
    let callback;
    let nackArgs;
    const channel = {
        async prefetch() {},
        async assertQueue() {},
        async consume(name, cb) {
            if (name === 'notificaciones.empleados') callback = cb;
        },
        ack() { throw new Error('no debería hacer ack'); },
        nack(...args) { nackArgs = args; }
    };
    amqp.connect = async () => ({
        async createChannel() { return channel; },
        on() {}
    });
    console.log = () => {};
    console.error = () => {};

    await iniciarConsumidor();
    const message = { content: Buffer.from('{json-invalido') };
    await callback(message);
    assert.deepEqual(nackArgs, [message, false, true]);
});
