package repository

import (
	"context"
	"crypto/rand"
	"database/sql"
	"errors"
	"fmt"
	"time"

	"perfiles-service/internal/model"
)

var ErrNoEncontrado = errors.New("perfil no encontrado")
var ErrArchivado = errors.New("perfil archivado")

type Repository struct {
	db *sql.DB
}

func New(db *sql.DB) *Repository {
	return &Repository{db: db}
}

const perfilColumns = `
	id::text,
	empleado_id,
	nombre,
	email,
	telefono,
	direccion,
	ciudad,
	biografia,
	fecha_creacion,
	archivado,
	fecha_archivado`

func scanPerfil(scanner interface{ Scan(dest ...any) error }) (model.Perfil, error) {
	var perfil model.Perfil
	err := scanner.Scan(
		&perfil.ID,
		&perfil.EmpleadoID,
		&perfil.Nombre,
		&perfil.Email,
		&perfil.Telefono,
		&perfil.Direccion,
		&perfil.Ciudad,
		&perfil.Biografia,
		&perfil.FechaCreacion,
		&perfil.Archivado,
		&perfil.FechaArchivado,
	)
	return perfil, err
}

func (r *Repository) Listar(ctx context.Context) ([]model.Perfil, error) {
	rows, err := r.db.QueryContext(ctx, `SELECT `+perfilColumns+` FROM perfiles ORDER BY fecha_creacion DESC`)
	if err != nil {
		return nil, err
	}
	defer rows.Close()

	perfiles := make([]model.Perfil, 0)
	for rows.Next() {
		perfil, err := scanPerfil(rows)
		if err != nil {
			return nil, err
		}
		perfiles = append(perfiles, perfil)
	}
	return perfiles, rows.Err()
}

func (r *Repository) Consultar(ctx context.Context, empleadoID string) (model.Perfil, error) {
	row := r.db.QueryRowContext(ctx, `SELECT `+perfilColumns+` FROM perfiles WHERE empleado_id = $1`, empleadoID)
	perfil, err := scanPerfil(row)
	if errors.Is(err, sql.ErrNoRows) {
		return model.Perfil{}, ErrNoEncontrado
	}
	return perfil, err
}

func (r *Repository) Actualizar(ctx context.Context, empleadoID string, cambios model.ActualizarPerfil) (model.Perfil, error) {
	actual, err := r.Consultar(ctx, empleadoID)
	if err != nil {
		return model.Perfil{}, err
	}
	if actual.Archivado {
		return model.Perfil{}, ErrArchivado
	}

	if cambios.Telefono != nil {
		actual.Telefono = *cambios.Telefono
	}
	if cambios.Direccion != nil {
		actual.Direccion = *cambios.Direccion
	}
	if cambios.Ciudad != nil {
		actual.Ciudad = *cambios.Ciudad
	}
	if cambios.Biografia != nil {
		actual.Biografia = *cambios.Biografia
	}

	row := r.db.QueryRowContext(ctx, `
		UPDATE perfiles
		SET telefono = $2, direccion = $3, ciudad = $4, biografia = $5
		WHERE empleado_id = $1
		RETURNING `+perfilColumns,
		empleadoID, actual.Telefono, actual.Direccion, actual.Ciudad, actual.Biografia,
	)
	return scanPerfil(row)
}

func (r *Repository) ProcesarEvento(ctx context.Context, evento model.EventoEmpleado) (bool, error) {
	tx, err := r.db.BeginTx(ctx, nil)
	if err != nil {
		return false, err
	}
	defer tx.Rollback()

	result, err := tx.ExecContext(ctx, `
		INSERT INTO eventos_procesados (id)
		VALUES ($1)
		ON CONFLICT (id) DO NOTHING`, evento.ID)
	if err != nil {
		return false, err
	}
	rows, err := result.RowsAffected()
	if err != nil {
		return false, err
	}
	if rows == 0 {
		return true, nil
	}

	empleadoID := evento.Data.Identificador()

	switch evento.Type {
	case "empleado.creado":
		if empleadoID == "" || evento.Data.Nombre == "" || evento.Data.Email == "" {
			return false, fmt.Errorf("evento empleado.creado incompleto")
		}
		profileID, uuidErr := newUUID()
		if uuidErr != nil {
			return false, uuidErr
		}
		_, err = tx.ExecContext(ctx, `
			INSERT INTO perfiles (id, empleado_id, nombre, email)
			VALUES ($1::uuid, $2, $3, $4)
			ON CONFLICT (empleado_id) DO UPDATE
			SET nombre = EXCLUDED.nombre,
				email = EXCLUDED.email,
				archivado = FALSE,
				fecha_archivado = NULL`,
			profileID, empleadoID, evento.Data.Nombre, evento.Data.Email,
		)

	case "empleado.actualizado":
		if empleadoID == "" || evento.Data.Nombre == "" || evento.Data.Email == "" {
			return false, fmt.Errorf("evento empleado.actualizado incompleto")
		}
		_, err = tx.ExecContext(ctx, `
			UPDATE perfiles
			SET nombre = $2, email = $3
			WHERE empleado_id = $1`,
			empleadoID, evento.Data.Nombre, evento.Data.Email,
		)

	case "empleado.retirado":
		if empleadoID == "" {
			return false, fmt.Errorf("evento empleado.retirado incompleto")
		}
		now := time.Now().UTC()
		_, err = tx.ExecContext(ctx, `
			UPDATE perfiles
			SET archivado = TRUE, fecha_archivado = $2
			WHERE empleado_id = $1`, empleadoID, now)

	default:
		return false, fmt.Errorf("tipo de evento no soportado: %s", evento.Type)
	}

	if err != nil {
		return false, err
	}

	if err := tx.Commit(); err != nil {
		return false, err
	}
	return false, nil
}

func (r *Repository) Ping(ctx context.Context) error {
	return r.db.PingContext(ctx)
}

func newUUID() (string, error) {
	b := make([]byte, 16)
	if _, err := rand.Read(b); err != nil {
		return "", err
	}
	b[6] = (b[6] & 0x0f) | 0x40
	b[8] = (b[8] & 0x3f) | 0x80
	return fmt.Sprintf("%08x-%04x-%04x-%04x-%012x",
		b[0:4], b[4:6], b[6:8], b[8:10], b[10:16]), nil
}
