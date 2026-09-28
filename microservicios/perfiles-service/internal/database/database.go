package database

import (
	"context"
	"database/sql"
	"fmt"
	"time"

	_ "github.com/lib/pq"
)

func Open(databaseURL string) (*sql.DB, error) {
	db, err := sql.Open("postgres", databaseURL)
	if err != nil {
		return nil, err
	}

	db.SetMaxOpenConns(10)
	db.SetMaxIdleConns(5)
	db.SetConnMaxLifetime(30 * time.Minute)

	ctx, cancel := context.WithTimeout(context.Background(), 10*time.Second)
	defer cancel()

	if err := db.PingContext(ctx); err != nil {
		db.Close()
		return nil, fmt.Errorf("conectar con PostgreSQL: %w", err)
	}

	if err := migrate(ctx, db); err != nil {
		db.Close()
		return nil, err
	}

	return db, nil
}

func migrate(ctx context.Context, db *sql.DB) error {
	statements := []string{
		`CREATE TABLE IF NOT EXISTS perfiles (
			id UUID PRIMARY KEY,
			empleado_id VARCHAR(255) NOT NULL UNIQUE,
			nombre VARCHAR(255) NOT NULL,
			email VARCHAR(255) NOT NULL,
			telefono VARCHAR(50) NOT NULL DEFAULT '',
			direccion VARCHAR(255) NOT NULL DEFAULT '',
			ciudad VARCHAR(120) NOT NULL DEFAULT '',
			biografia TEXT NOT NULL DEFAULT '',
			fecha_creacion TIMESTAMPTZ NOT NULL DEFAULT NOW(),
			archivado BOOLEAN NOT NULL DEFAULT FALSE,
			fecha_archivado TIMESTAMPTZ NULL
		)`,
		`CREATE TABLE IF NOT EXISTS eventos_procesados (
			id VARCHAR(255) PRIMARY KEY,
			procesado_en TIMESTAMPTZ NOT NULL DEFAULT NOW()
		)`,
	}

	for _, statement := range statements {
		if _, err := db.ExecContext(ctx, statement); err != nil {
			return fmt.Errorf("crear esquema de perfiles: %w", err)
		}
	}

	return nil
}
