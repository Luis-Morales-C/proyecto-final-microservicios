#!/bin/sh
set -e

rabbitmq-server &
rabbitmq_pid=$!

until rabbitmq-diagnostics -q ping; do
  sleep 2
done
rabbitmqctl import_definitions /etc/rabbitmq/definitions.json

wait "$rabbitmq_pid"