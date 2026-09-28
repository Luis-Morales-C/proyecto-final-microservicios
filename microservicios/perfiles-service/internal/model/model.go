package model

import "time"

type Perfil struct {
	ID             string     `json:"id"`
	EmpleadoID     string     `json:"empleadoId"`
	Nombre         string     `json:"nombre"`
	Email          string     `json:"email"`
	Telefono       string     `json:"telefono"`
	Direccion      string     `json:"direccion"`
	Ciudad         string     `json:"ciudad"`
	Biografia      string     `json:"biografia"`
	FechaCreacion  time.Time  `json:"fechaCreacion"`
	Archivado      bool       `json:"archivado"`
	FechaArchivado *time.Time `json:"fechaArchivado,omitempty"`
}

type ActualizarPerfil struct {
	Telefono  *string `json:"telefono"`
	Direccion *string `json:"direccion"`
	Ciudad    *string `json:"ciudad"`
	Biografia *string `json:"biografia"`
}

type EventoEmpleado struct {
	ID         string       `json:"id"`
	Type       string       `json:"type"`
	Version    int          `json:"version"`
	OccurredAt time.Time    `json:"occurredAt"`
	Producer   string       `json:"producer"`
	Data       EmpleadoData `json:"data"`
}

type EmpleadoData struct {
	EmpleadoID     string `json:"empleadoId"`
	LegacyID       string `json:"id,omitempty"` // tolerancia temporal para mensajes previos al catálogo v1.0
	NumeroEmpleado string `json:"numeroEmpleado"`
	Nombre         string `json:"nombre"`
	Apellido       string `json:"apellido"`
	Email          string `json:"email"`
	DepartamentoID string `json:"departamentoId"`
	Estado         string `json:"estado"`
}

func (d EmpleadoData) Identificador() string {
	if d.EmpleadoID != "" {
		return d.EmpleadoID
	}
	return d.LegacyID
}
