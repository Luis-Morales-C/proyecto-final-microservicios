#!/bin/sh
set -e

rabbitmq-server &
rabbitmq_pid=$!

rabbitmqctl await_startup --timeout 300

rabbitmqctl import_definitions /etc/rabbitmq/definitions.json

wait "$rabbitmq_pid"