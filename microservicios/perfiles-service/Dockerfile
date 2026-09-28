FROM golang:1.23-alpine AS build
WORKDIR /src
COPY go.mod go.sum ./
RUN go mod download
COPY . .
RUN CGO_ENABLED=0 GOOS=linux go build -trimpath -ldflags="-s -w" -o /out/perfiles-service ./cmd

FROM alpine:3.21
RUN apk add --no-cache ca-certificates wget
WORKDIR /app
COPY --from=build /out/perfiles-service /app/perfiles-service
EXPOSE 8084
ENTRYPOINT ["/app/perfiles-service"]
