package config

import (
	"strings"
	"testing"
)

func TestLoadDefaults(t *testing.T) {
	keys := []string{"PORT", "DB_HOST", "DB_PORT", "DB_NAME", "DB_USER", "DB_PASSWORD", "RABBITMQ_HOST", "RABBITMQ_PORT", "RABBITMQ_USER", "RABBITMQ_PASSWORD", "RABBITMQ_QUEUE"}
	for _, key := range keys {
		t.Setenv(key, "")
	}

	cfg := Load()
	if cfg.Port != "8084" {
		t.Fatalf("port esperado 8084, obtuvo %s", cfg.Port)
	}
	if !strings.Contains(cfg.DatabaseURL, "database-perfiles:5432/perfiles") {
		t.Fatalf("database url inesperada: %s", cfg.DatabaseURL)
	}
	if !strings.Contains(cfg.RabbitMQURL, "rabbitmq:5672") {
		t.Fatalf("rabbit url inesperada: %s", cfg.RabbitMQURL)
	}
	if cfg.RabbitMQQueue != "perfiles.empleados" {
		t.Fatalf("queue inesperada: %s", cfg.RabbitMQQueue)
	}
}

func TestLoadUsesEnvironment(t *testing.T) {
	t.Setenv("PORT", "9999")
	t.Setenv("DB_HOST", "db-test")
	t.Setenv("DB_PORT", "5544")
	t.Setenv("DB_NAME", "perfiles_test")
	t.Setenv("DB_USER", "user_test")
	t.Setenv("DB_PASSWORD", "pass_test")
	t.Setenv("RABBITMQ_HOST", "rabbit-test")
	t.Setenv("RABBITMQ_PORT", "5673")
	t.Setenv("RABBITMQ_USER", "rabbit_user")
	t.Setenv("RABBITMQ_PASSWORD", "rabbit_pass")
	t.Setenv("RABBITMQ_QUEUE", "queue.test")

	cfg := Load()
	if cfg.Port != "9999" {
		t.Fatalf("port inesperado: %s", cfg.Port)
	}
	if cfg.DatabaseURL != "postgres://user_test:pass_test@db-test:5544/perfiles_test?sslmode=disable" {
		t.Fatalf("database url inesperada: %s", cfg.DatabaseURL)
	}
	if cfg.RabbitMQURL != "amqp://rabbit_user:rabbit_pass@rabbit-test:5673/" {
		t.Fatalf("rabbit url inesperada: %s", cfg.RabbitMQURL)
	}
	if cfg.RabbitMQQueue != "queue.test" {
		t.Fatalf("queue inesperada: %s", cfg.RabbitMQQueue)
	}
}
