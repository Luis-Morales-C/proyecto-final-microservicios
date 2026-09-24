const { Pool } = require("pg");

const pool = new Pool({
    host: process.env.DB_HOST || "database-notificaciones",
    port: Number(process.env.DB_PORT || 5432),
    database: process.env.DB_NAME || "notificaciones",
    user: process.env.DB_USER,
    password: process.env.DB_PASSWORD
});

async function inicializarBaseDeDatos() {
    await pool.query(`
    CREATE TABLE IF NOT EXISTS notificaciones (
      id UUID PRIMARY KEY,
      tipo VARCHAR(30) NOT NULL,
      destinatario VARCHAR(255) NOT NULL,
      mensaje TEXT NOT NULL,
      fecha_envio TIMESTAMPTZ NOT NULL DEFAULT NOW(),
      empleado_id VARCHAR(255) NOT NULL
    )
  `);

    await pool.query(`
    CREATE TABLE IF NOT EXISTS eventos_procesados (
      id VARCHAR(255) PRIMARY KEY,
      procesado_en TIMESTAMPTZ NOT NULL DEFAULT NOW()
    )
  `);

    console.log(
        "Tablas de Notificaciones inicializadas correctamente"
    );
}

module.exports = {
    pool,
    inicializarBaseDeDatos
};