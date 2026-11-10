package httpapi

import (
	"encoding/json"
	"net/http"
	"net/http/httptest"
	"strings"
	"testing"

	"perfiles-service/internal/repository"
)

func handlerWithoutDB() http.Handler {
	return New(repository.New(nil))
}

func TestDocsAvailable(t *testing.T) {
	req := httptest.NewRequest(http.MethodGet, "/perfiles/docs", nil)
	rec := httptest.NewRecorder()
	handlerWithoutDB().ServeHTTP(rec, req)
	if rec.Code != http.StatusOK {
		t.Fatalf("esperado 200, obtuvo %d", rec.Code)
	}
	if !strings.Contains(rec.Body.String(), "SwaggerUIBundle") {
		t.Fatal("docs no contiene Swagger UI")
	}
	if !strings.Contains(rec.Header().Get("Content-Type"), "text/html") {
		t.Fatalf("content-type inesperado: %s", rec.Header().Get("Content-Type"))
	}
}

func TestOpenAPIAvailable(t *testing.T) {
	req := httptest.NewRequest(http.MethodGet, "/perfiles/openapi.json", nil)
	rec := httptest.NewRecorder()
	handlerWithoutDB().ServeHTTP(rec, req)
	if rec.Code != http.StatusOK {
		t.Fatalf("esperado 200, obtuvo %d", rec.Code)
	}
	var body map[string]any
	if err := json.Unmarshal(rec.Body.Bytes(), &body); err != nil {
		t.Fatalf("openapi inválido: %v", err)
	}
	if body["openapi"] == nil {
		t.Fatal("falta campo openapi")
	}
}

func TestMetricsAvailable(t *testing.T) {
	h := handlerWithoutDB()
	// Genera al menos una observación antes del scrape para que el CounterVec
	// tenga una serie materializada.
	warmup := httptest.NewRecorder()
	h.ServeHTTP(warmup, httptest.NewRequest(http.MethodGet, "/perfiles/docs", nil))

	req := httptest.NewRequest(http.MethodGet, "/metrics", nil)
	rec := httptest.NewRecorder()
	h.ServeHTTP(rec, req)
	if rec.Code != http.StatusOK {
		t.Fatalf("esperado 200, obtuvo %d", rec.Code)
	}
	if !strings.Contains(rec.Body.String(), "http_requests_total") {
		t.Fatal("metrics no contiene métrica HTTP")
	}
}

func TestUnknownRouteReturns404(t *testing.T) {
	req := httptest.NewRequest(http.MethodGet, "/ruta-inexistente", nil)
	rec := httptest.NewRecorder()
	handlerWithoutDB().ServeHTTP(rec, req)
	if rec.Code != http.StatusNotFound {
		t.Fatalf("esperado 404, obtuvo %d", rec.Code)
	}
	if !strings.Contains(rec.Body.String(), "Recurso no encontrado") {
		t.Fatalf("mensaje inesperado: %s", rec.Body.String())
	}
}

func TestUpdateRejectsInvalidJSONBeforeRepository(t *testing.T) {
	req := httptest.NewRequest(http.MethodPut, "/perfiles/E001", strings.NewReader(`{"telefono":`))
	rec := httptest.NewRecorder()
	handlerWithoutDB().ServeHTTP(rec, req)
	if rec.Code != http.StatusBadRequest {
		t.Fatalf("esperado 400, obtuvo %d", rec.Code)
	}
	if !strings.Contains(rec.Body.String(), "JSON inválido") {
		t.Fatalf("mensaje inesperado: %s", rec.Body.String())
	}
}

func TestUpdateRejectsUnknownField(t *testing.T) {
	req := httptest.NewRequest(http.MethodPut, "/perfiles/E001", strings.NewReader(`{"campoNoPermitido":"x"}`))
	rec := httptest.NewRecorder()
	handlerWithoutDB().ServeHTTP(rec, req)
	if rec.Code != http.StatusBadRequest {
		t.Fatalf("esperado 400, obtuvo %d", rec.Code)
	}
}

func TestUpdateRequiresAtLeastOneEditableField(t *testing.T) {
	req := httptest.NewRequest(http.MethodPut, "/perfiles/E001", strings.NewReader(`{}`))
	rec := httptest.NewRecorder()
	handlerWithoutDB().ServeHTTP(rec, req)
	if rec.Code != http.StatusBadRequest {
		t.Fatalf("esperado 400, obtuvo %d", rec.Code)
	}
	if !strings.Contains(rec.Body.String(), "al menos un campo") {
		t.Fatalf("mensaje inesperado: %s", rec.Body.String())
	}
}

func TestWriteJSONSetsStatusAndContentType(t *testing.T) {
	rec := httptest.NewRecorder()
	writeJSON(rec, http.StatusCreated, map[string]string{"ok": "true"})
	if rec.Code != http.StatusCreated {
		t.Fatalf("esperado 201, obtuvo %d", rec.Code)
	}
	if rec.Header().Get("Content-Type") != "application/json" {
		t.Fatalf("content type inesperado")
	}
	if !strings.Contains(rec.Body.String(), `"ok":"true"`) {
		t.Fatalf("body inesperado: %s", rec.Body.String())
	}
}

func TestStatusRecorderDefaultsToOKAndRecordsExplicitStatus(t *testing.T) {
	base := httptest.NewRecorder()
	recorder := &statusRecorder{ResponseWriter: base, status: http.StatusOK}
	recorder.WriteHeader(http.StatusAccepted)
	if recorder.status != http.StatusAccepted {
		t.Fatalf("status no registrado: %d", recorder.status)
	}
}
