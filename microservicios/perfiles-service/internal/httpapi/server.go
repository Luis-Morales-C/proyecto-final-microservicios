package httpapi

import (
	"context"
	_ "embed"
	"encoding/json"
	"errors"
	"fmt"
	"net/http"
	"strings"
	"time"
	"os"

	"perfiles-service/internal/model"
	"perfiles-service/internal/repository"
	"github.com/prometheus/client_golang/prometheus/promhttp"
	"github.com/prometheus/client_golang/prometheus"
	"go.opentelemetry.io/otel/trace"
)

//go:embed openapi.json
var openAPISpec []byte

const swaggerHTML = `<!doctype html>
<html><head><meta charset="utf-8"><title>Perfiles - Swagger UI</title>
<link rel="stylesheet" href="https://unpkg.com/swagger-ui-dist@5/swagger-ui.css"></head>
<body><div id="swagger-ui"></div>
<script src="https://unpkg.com/swagger-ui-dist@5/swagger-ui-bundle.js"></script>
<script>SwaggerUIBundle({url:'/perfiles/openapi.json',dom_id:'#swagger-ui'});</script>
</body></html>`

type Server struct {
	repo *repository.Repository
}

var (
    httpRequestsTotal = prometheus.NewCounterVec(
        prometheus.CounterOpts{
            Name: "http_requests_total",
            Help: "Número total de peticiones HTTP recibidas",
        },
        []string{"method", "path", "status"},
    )

    httpRequestDuration = prometheus.NewHistogramVec(
        prometheus.HistogramOpts{
            Name: "http_request_duration_seconds",
            Help: "Duración de las peticiones HTTP en segundos",
        },
        []string{"method", "path"},
    )
)

func init() {
    prometheus.MustRegister(httpRequestsTotal)
    prometheus.MustRegister(httpRequestDuration)
}

type statusRecorder struct {
    http.ResponseWriter
    status int
}

func (r *statusRecorder) WriteHeader(status int) {
    r.status = status
    r.ResponseWriter.WriteHeader(status)
}

func New(repo *repository.Repository) http.Handler {
	s := &Server{repo: repo}
	mux := http.NewServeMux()
	mux.HandleFunc("GET /health", s.health)
	mux.Handle("GET /metrics", promhttp.Handler())
	mux.HandleFunc("GET /perfiles", s.listar)
	mux.HandleFunc("GET /perfiles/{empleadoId}", s.consultar)
	mux.HandleFunc("PUT /perfiles/{empleadoId}", s.actualizar)
	mux.HandleFunc("GET /perfiles/openapi.json", s.openapi)
	mux.HandleFunc("GET /perfiles/docs", s.docs)
	mux.HandleFunc("GET /perfiles/docs/", s.docs)
    return metricsMiddleware(loggingJSON(mux))}

func (s *Server) health(w http.ResponseWriter, r *http.Request) {
	ctx, cancel := context.WithTimeout(r.Context(), 2*time.Second)
	defer cancel()
	if err := s.repo.Ping(ctx); err != nil {
		writeJSON(w, http.StatusServiceUnavailable, map[string]any{"estado": "ERROR", "servicio": "perfiles-service"})
		return
	}
	writeJSON(w, http.StatusOK, map[string]any{"estado": "OK", "servicio": "perfiles-service"})
}

func (s *Server) listar(w http.ResponseWriter, r *http.Request) {
	perfiles, err := s.repo.Listar(r.Context())
	if err != nil {
		writeError(w, http.StatusInternalServerError, "No fue posible consultar los perfiles")
		return
	}
	writeJSON(w, http.StatusOK, perfiles)
}

func (s *Server) consultar(w http.ResponseWriter, r *http.Request) {
	perfil, err := s.repo.Consultar(r.Context(), r.PathValue("empleadoId"))
	if errors.Is(err, repository.ErrNoEncontrado) {
		writeError(w, http.StatusNotFound, "El perfil solicitado no existe")
		return
	}
	if err != nil {
		writeError(w, http.StatusInternalServerError, "No fue posible consultar el perfil")
		return
	}
	writeJSON(w, http.StatusOK, perfil)
}

func (s *Server) actualizar(w http.ResponseWriter, r *http.Request) {
	var body model.ActualizarPerfil
	decoder := json.NewDecoder(http.MaxBytesReader(w, r.Body, 1<<20))
	decoder.DisallowUnknownFields()
	if err := decoder.Decode(&body); err != nil {
		writeError(w, http.StatusBadRequest, "JSON inválido: "+err.Error())
		return
	}
	if body.Telefono == nil && body.Direccion == nil && body.Ciudad == nil && body.Biografia == nil {
		writeError(w, http.StatusBadRequest, "Debe enviar al menos un campo editable")
		return
	}

	perfil, err := s.repo.Actualizar(r.Context(), r.PathValue("empleadoId"), body)
	if errors.Is(err, repository.ErrNoEncontrado) {
		writeError(w, http.StatusNotFound, "El perfil solicitado no existe")
		return
	}
	if errors.Is(err, repository.ErrArchivado) {
		writeError(w, http.StatusConflict, "No se puede modificar un perfil archivado")
		return
	}
	if err != nil {
		writeError(w, http.StatusInternalServerError, "No fue posible actualizar el perfil")
		return
	}
	writeJSON(w, http.StatusOK, perfil)
}

func (s *Server) openapi(w http.ResponseWriter, _ *http.Request) {
	w.Header().Set("Content-Type", "application/json")
	_, _ = w.Write(openAPISpec)
}

func (s *Server) docs(w http.ResponseWriter, _ *http.Request) {
	w.Header().Set("Content-Type", "text/html; charset=utf-8")
	_, _ = fmt.Fprint(w, swaggerHTML)
}

func writeError(w http.ResponseWriter, status int, message string) {
	writeJSON(w, status, map[string]any{"error": true, "mensaje": message})
}

func writeJSON(w http.ResponseWriter, status int, value any) {
	w.Header().Set("Content-Type", "application/json")
	w.WriteHeader(status)
	_ = json.NewEncoder(w).Encode(value)
}

func loggingJSON(next http.Handler) http.Handler {
	return http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
		if strings.HasPrefix(r.URL.Path, "/perfiles") ||
			r.URL.Path == "/health" ||
			r.URL.Path == "/metrics" {

			next.ServeHTTP(w, r)
			return
		}

		writeError(w, http.StatusNotFound, "Recurso no encontrado")
	})
}

func metricsMiddleware(next http.Handler) http.Handler {
    return http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
        start := time.Now()

        recorder := &statusRecorder{
            ResponseWriter: w,
            status:         http.StatusOK,
        }

        next.ServeHTTP(recorder, r)

        spanContext := trace.SpanContextFromContext(r.Context())

        traceID := ""
        spanID := ""

        if spanContext.IsValid() {
        	traceID = spanContext.TraceID().String()
        	spanID = spanContext.SpanID().String()
        }

        logEntry := map[string]any{
        	"timestamp": time.Now().UTC().Format(time.RFC3339Nano),
        	"level":     "INFO",
        	"service":   "perfiles-service",
        	"message":   fmt.Sprintf("%s %s status=%d", r.Method, r.URL.Path, recorder.status),
        	"traceId":   traceID,
        	"spanId":    spanID,
        	"method":    r.Method,
        	"path":      r.URL.Path,
        	"status":    recorder.status,
        }

        _ = json.NewEncoder(os.Stdout).Encode(logEntry)

        path := r.URL.Path

        httpRequestsTotal.WithLabelValues(
            r.Method,
            path,
            fmt.Sprintf("%d", recorder.status),
        ).Inc()

        httpRequestDuration.WithLabelValues(
            r.Method,
            path,
        ).Observe(time.Since(start).Seconds())
    })
}
