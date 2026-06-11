FROM golang:1.22-alpine AS builder

RUN apk add --no-cache git

WORKDIR /app

# Clone Pirate Chain lightwalletd
RUN git clone https://github.com/PirateNetwork/lightwalletd.git .

RUN CGO_ENABLED=0 go build -o lightwalletd .

FROM alpine:latest

RUN apk add --no-cache ca-certificates

WORKDIR /app

COPY --from=builder /app/lightwalletd /usr/local/bin/

# Expose gRPC ports
EXPOSE 443
EXPOSE 9067

ENTRYPOINT ["lightwalletd"]
