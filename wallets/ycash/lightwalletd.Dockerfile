FROM golang:1.22-alpine AS builder

RUN apk add --no-cache git

WORKDIR /app

# Clone Ycash lightwalletd
RUN git clone https://github.com/ycashfoundation/lightwalletd.git .

RUN CGO_ENABLED=0 go build -o lightwalletd ./cmd/server

FROM alpine:latest

RUN apk add --no-cache ca-certificates

WORKDIR /app

COPY --from=builder /app/lightwalletd /usr/local/bin/

# Expose gRPC port
EXPOSE 9067
EXPOSE 19067

ENTRYPOINT ["lightwalletd"]
