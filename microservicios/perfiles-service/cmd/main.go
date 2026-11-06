package main

import (
	"context"
	"log"
	"net/http"
	"os"
	"os/signal"
	"syscall"
	"time"

	"perfiles-service/internal/config"
	"perfiles-service/internal/database"
	"perfiles-service/internal/events"
	"perfiles-service/internal/httpapi"
	"perfiles-service/internal/repository"

	"go.opentelemetry.io/contrib/instrumentation/net/http/otelhttp"
	"go.opentelemetry.io/otel"
	"go.opentelemetry.io/otel/exporters/otlp/otlptrace/otlptracehttp"
	"go.opentelemetry.io/otel/propagation"
	"go.opentelemetry.io/otel/sdk/resource"
	sdktrace "go.opentelemetry.io/otel/sdk/trace"
    "go.opentelemetry.io/otel/attribute"
)

func main() {
	cfg := config.Load()

	appCtx := context.Background()

	tracerProvider, err := initTracer(appCtx)
	if err != nil {
		log.Fatalf(
			"No fue posible inicializar OpenTelemetry: %v",
			err,
		)
	}

	defer func() {
		shutdownCtx, shutdownCancel := context.WithTimeout(
			context.Background(),
			5*time.Second,
		)
		defer shutdownCancel()

		if err := tracerProvider.Shutdown(shutdownCtx); err != nil {
			log.Printf(
				"Error cerrando OpenTelemetry: %v",
				err,
			)
		}
	}()

	db, err := database.Open(cfg.DatabaseURL)
	if err != nil {
		log.Fatalf(
			"No fue posible iniciar la base de datos de perfiles: %v",
			err,
		)
	}
	defer db.Close()

	repo := repository.New(db)

	ctx, cancel := signal.NotifyContext(
		context.Background(),
		os.Interrupt,
		syscall.SIGTERM,
	)
	defer cancel()

	consumer := events.NewConsumer(
		cfg.RabbitMQURL,
		cfg.RabbitMQQueue,
		repo,
	)

	go consumer.Run(ctx)

	handler := otelhttp.NewHandler(
		httpapi.New(repo),
		"perfiles-service",
	)

	server := &http.Server{
		Addr:              ":" + cfg.Port,
		Handler:           handler,
		ReadHeaderTimeout: 5 * time.Second,
	}

	go func() {
		<-ctx.Done()

		shutdownCtx, shutdownCancel := context.WithTimeout(
			context.Background(),
			10*time.Second,
		)
		defer shutdownCancel()

		if err := server.Shutdown(shutdownCtx); err != nil {
			log.Printf(
				"Error cerrando servidor HTTP: %v",
				err,
			)
		}
	}()

	log.Printf(
		"Perfiles iniciado en el puerto %s",
		cfg.Port,
	)

	if err := server.ListenAndServe(); err != nil &&
		err != http.ErrServerClosed {

		log.Fatalf(
			"Servidor HTTP de perfiles: %v",
			err,
		)
	}
}

func initTracer(
	ctx context.Context,
) (*sdktrace.TracerProvider, error) {

	exporter, err := otlptracehttp.New(ctx)
	if err != nil {
		return nil, err
	}

	res, err := resource.New(
		ctx,
		resource.WithAttributes(
			attribute.String("service.name", "perfiles-service"),
		),
	)
	if err != nil {
		return nil, err
	}

	tracerProvider := sdktrace.NewTracerProvider(
		sdktrace.WithBatcher(exporter),
		sdktrace.WithResource(res),
	)

	otel.SetTracerProvider(tracerProvider)

	otel.SetTextMapPropagator(
		propagation.NewCompositeTextMapPropagator(
			propagation.TraceContext{},
			propagation.Baggage{},
		),
	)

	return tracerProvider, nil
}