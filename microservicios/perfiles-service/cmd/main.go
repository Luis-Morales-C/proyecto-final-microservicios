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
)

func main() {
	cfg := config.Load()

	db, err := database.Open(cfg.DatabaseURL)
	if err != nil {
		log.Fatalf("No fue posible iniciar la base de datos de perfiles: %v", err)
	}
	defer db.Close()

	repo := repository.New(db)
	ctx, cancel := signal.NotifyContext(context.Background(), os.Interrupt, syscall.SIGTERM)
	defer cancel()

	consumer := events.NewConsumer(cfg.RabbitMQURL, cfg.RabbitMQQueue, repo)
	go consumer.Run(ctx)

	server := &http.Server{
		Addr:              ":" + cfg.Port,
		Handler:           httpapi.New(repo),
		ReadHeaderTimeout: 5 * time.Second,
	}

	go func() {
		<-ctx.Done()
		shutdownCtx, shutdownCancel := context.WithTimeout(context.Background(), 10*time.Second)
		defer shutdownCancel()
		_ = server.Shutdown(shutdownCtx)
	}()

	log.Printf("Perfiles iniciado en el puerto %s", cfg.Port)
	if err := server.ListenAndServe(); err != nil && err != http.ErrServerClosed {
		log.Fatalf("Servidor HTTP de perfiles: %v", err)
	}
}
