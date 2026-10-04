FROM golang:1.22-alpine AS builder

RUN apk add --no-cache git

WORKDIR /app

# Clone Pirate Chain lightwalletd (v1.0.0.0 with Ironwood support)
ARG LWD_COMMIT=43b29b4229cc68adae8a807931d26f9a5f5d98a6
RUN git clone https://github.com/PirateNetwork/lightwalletd.git . \
    && git checkout "$LWD_COMMIT"

RUN CGO_ENABLED=0 go build -o lightwalletd .

FROM alpine:latest

RUN apk add --no-cache ca-certificates

WORKDIR /app

COPY --from=builder /app/lightwalletd /usr/local/bin/

# Expose gRPC ports
EXPOSE 443
EXPOSE 9067

ENTRYPOINT ["lightwalletd"]
