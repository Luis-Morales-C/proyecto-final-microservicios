package events

import (
	"context"
	"encoding/json"
	"fmt"
	"log"
	"time"

	amqp "github.com/rabbitmq/amqp091-go"

	"perfiles-service/internal/model"
	"perfiles-service/internal/repository"
)

type Consumer struct {
	url        string
	queue      string
	repository *repository.Repository
}

func NewConsumer(url, queue string, repo *repository.Repository) *Consumer {
	return &Consumer{url: url, queue: queue, repository: repo}
}

func (c *Consumer) Run(ctx context.Context) {
	for {
		if ctx.Err() != nil {
			return
		}

		if err := c.consume(ctx); err != nil {
			log.Printf("RabbitMQ perfiles: %v. Reintentando en 5s", err)
			select {
			case <-ctx.Done():
				return
			case <-time.After(5 * time.Second):
			}
		}
	}
}

func (c *Consumer) consume(ctx context.Context) error {
	conn, err := amqp.Dial(c.url)
	if err != nil {
		return fmt.Errorf("conectar: %w", err)
	}
	defer conn.Close()

	channel, err := conn.Channel()
	if err != nil {
		return fmt.Errorf("crear canal: %w", err)
	}
	defer channel.Close()

	if err := channel.Qos(1, 0, false); err != nil {
		return err
	}

	if _, err := channel.QueueDeclare(c.queue, true, false, false, false, nil); err != nil {
		return fmt.Errorf("declarar cola: %w", err)
	}

	messages, err := channel.Consume(c.queue, "perfiles-service", false, false, false, false, nil)
	if err != nil {
		return fmt.Errorf("consumir cola: %w", err)
	}

	log.Printf("Perfiles escuchando cola %s", c.queue)

	for {
		select {
		case <-ctx.Done():
			return nil
		case message, ok := <-messages:
			if !ok {
				return fmt.Errorf("canal de entregas cerrado")
			}

			var event model.EventoEmpleado
			if err := json.Unmarshal(message.Body, &event); err != nil {
				log.Printf("Evento inválido descartado: %v", err)
				_ = message.Ack(false)
				continue
			}

			if event.Type != "empleado.creado" && event.Type != "empleado.actualizado" && event.Type != "empleado.retirado" {
				log.Printf("Evento no gestionado por perfiles: %s", event.Type)
				_ = message.Ack(false)
				continue
			}

			duplicate, err := c.repository.ProcesarEvento(ctx, event)
			if err != nil {
				log.Printf("Error procesando evento %s: %v", event.ID, err)
				_ = message.Nack(false, true)
				continue
			}

			if duplicate {
				log.Printf("Evento duplicado descartado en perfiles: %s", event.ID)
			}
			_ = message.Ack(false)
		}
	}
}
