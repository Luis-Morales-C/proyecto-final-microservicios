package model

import "testing"

func TestEmpleadoDataIdentificadorPrefiereEmpleadoID(t *testing.T) {
	data := EmpleadoData{EmpleadoID: "E001", LegacyID: "LEGACY"}
	if got := data.Identificador(); got != "E001" {
		t.Fatalf("esperado E001, obtuvo %s", got)
	}
}

func TestEmpleadoDataIdentificadorUsaLegacyComoFallback(t *testing.T) {
	data := EmpleadoData{LegacyID: "E002"}
	if got := data.Identificador(); got != "E002" {
		t.Fatalf("esperado E002, obtuvo %s", got)
	}
}

func TestEmpleadoDataIdentificadorVacio(t *testing.T) {
	if got := (EmpleadoData{}).Identificador(); got != "" {
		t.Fatalf("esperado vacío, obtuvo %s", got)
	}
}
