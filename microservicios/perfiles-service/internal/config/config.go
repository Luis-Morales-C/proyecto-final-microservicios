package config

import (
	"fmt"
	"os"
)

type Config struct {
	Port          string
	DatabaseURL   string
	RabbitMQURL   string
	RabbitMQQueue string
}

func Load() Config {
	port := getenv("PORT", "8084")

	dbHost := getenv("DB_HOST", "database-perfiles")
	dbPort := getenv("DB_PORT", "5432")
	dbName := getenv("DB_NAME", "perfiles")
	dbUser := getenv("DB_USER", "perfiles")
	dbPassword := getenv("DB_PASSWORD", "perfiles")

	rabbitHost := getenv("RABBITMQ_HOST", "rabbitmq")
	rabbitPort := getenv("RABBITMQ_PORT", "5672")
	rabbitUser := getenv("RABBITMQ_USER", "admin")
	rabbitPassword := getenv("RABBITMQ_PASSWORD", "admin")

	return Config{
		Port: port,
		DatabaseURL: fmt.Sprintf(
			"postgres://%s:%s@%s:%s/%s?sslmode=disable",
			dbUser, dbPassword, dbHost, dbPort, dbName,
		),
		RabbitMQURL: fmt.Sprintf(
			"amqp://%s:%s@%s:%s/",
			rabbitUser, rabbitPassword, rabbitHost, rabbitPort,
		),
		RabbitMQQueue: getenv("RABBITMQ_QUEUE", "perfiles.empleados"),
	}
}

func getenv(key, fallback string) string {
	if value := os.Getenv(key); value != "" {
		return value
	}
	return fallback
}
