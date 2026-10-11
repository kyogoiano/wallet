#!/usr/bin/env bash
# ==============================================================================
# 🏢 Wallet Service — Edge Gateway Ingress CLI & HMAC-SHA256 Signer
# ==============================================================================
# Deterministically signs and sends requests to the Reactive Edge Gateway
# according to the WALLET-HMAC-V1 protocol specification (REQ-SEC-002).
# ==============================================================================
set -euo pipefail

# Default configuration
EDGE_HOST="${EDGE_HOST:-http://localhost:8080}"
KEY_ID="${WALLET_KEY_ID:-wallet-key-dev-1}"
SECRET="${WALLET_SECRET:-wallet-secret-dev-key-32-bytes!!}"

get_epoch_ms() {
    local ms
    if ms=$(python3 -c 'import time; print(int(time.time() * 1000))' 2>/dev/null); then
        echo "$ms"
    elif ms=$(($(date +%s%N 2>/dev/null || echo "$(date +%s)000000000") / 1000000)); then
        echo "$ms"
    else
        echo "$(($(date +%s) * 1000))"
    fi
}

gen_uuid() {
    uuidgen 2>/dev/null || cat /proc/sys/kernel/random/uuid 2>/dev/null || python3 -c 'import uuid; print(uuid.uuid4())'
}

usage() {
    cat <<EOF
Usage: $0 [command|METHOD] [PATH] [PAYLOAD]

Commands:
  deposit  [amount] [walletId]   Send a deposit operation (default: 150.00)
  transfer [amount] [from] [to]  Send a transfer operation (default: 150.00)
  stream   <operationId>         Listen to real-time Server-Sent Events stream
  call     <METHOD> <PATH> [JSON] Send an arbitrary HMAC-signed request

Examples:
  $0 deposit 200.00
  $0 transfer 50.00 11111111-1111-1111-1111-111111111111 22222222-2222-2222-2222-222222222222
  $0 stream a0000000-0000-0000-0000-000000000001
  $0 POST /operations/transfers '{"sourceAccountId":"...","targetAccountId":"...","amount":100.00}'
  $0 GET /operations/a0000000-0000-0000-0000-000000000001/stream

Environment Variables:
  EDGE_HOST       Base URL of the Edge Gateway (default: http://localhost:8080)
  WALLET_KEY_ID   HMAC Key ID (default: wallet-key-dev-1)
  WALLET_SECRET   HMAC Secret (default: wallet-secret-dev-key-32-bytes!!)
EOF
    exit 1
}

# Subcommands routing
ACTION="${1:-}"

case "$ACTION" in
    deposit)
        AMOUNT="${2:-150.00}"
        WALLET_ID="${3:-a0000000-0000-0000-0000-000000000001}"
        USER_ID="b0000000-0000-0000-0000-000000000001"
        PAYLOAD="{\"walletId\":\"$WALLET_ID\",\"userId\":\"$USER_ID\",\"amount\":\"$AMOUNT\",\"operationOrigin\":\"USER\"}"
        METHOD="POST"
        URI_PATH="/operations/deposits"
        ;;
    transfer)
        AMOUNT="${2:-150.00}"
        FROM_WALLET="${3:-11111111-1111-1111-1111-111111111111}"
        TO_WALLET="${4:-22222222-2222-2222-2222-222222222222}"
        PAYLOAD="{\"sourceAccountId\":\"$FROM_WALLET\",\"targetAccountId\":\"$TO_WALLET\",\"amount\":$AMOUNT,\"currency\":\"BRL\"}"
        METHOD="POST"
        URI_PATH="/operations/transfers"
        ;;
    stream)
        OP_ID="${2:-}"
        if [ -z "$OP_ID" ]; then
            echo "Error: operationId required for stream"
            usage
        fi
        METHOD="GET"
        URI_PATH="/operations/$OP_ID/stream"
        PAYLOAD=""
        ;;
    call)
        METHOD="${2:-POST}"
        URI_PATH="${3:-}"
        PAYLOAD="${4:-}"
        if [ -z "$URI_PATH" ]; then
            usage
        fi
        ;;
    POST|PUT|DELETE|PATCH)
        METHOD="$1"
        URI_PATH="${2:-}"
        PAYLOAD="${3:-}"
        if [ -z "$URI_PATH" ]; then
            usage
        fi
        ;;
    GET|HEAD)
        METHOD="$1"
        URI_PATH="${2:-}"
        PAYLOAD=""
        if [ -z "$URI_PATH" ]; then
            usage
        fi
        ;;
    "")
        usage
        ;;
    *)
        # If first arg starts with /operations, assume POST
        if [[ "$1" == /operations* ]]; then
            METHOD="POST"
            URI_PATH="$1"
            PAYLOAD="${2:-}"
        else
            usage
        fi
        ;;
esac

# Normalize URI path
if [[ "$URI_PATH" != /* ]]; then
    URI_PATH="/$URI_PATH"
fi

OP_ID="$(gen_uuid)"
NONCE="$(gen_uuid)"
TIMESTAMP="$(get_epoch_ms)"

if [ -n "$PAYLOAD" ]; then
    BODY_HASH=$(printf "%s" "$PAYLOAD" | sha256sum | awk '{print $1}')
else
    BODY_HASH="e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855"
fi

# Build canonical representation (8 newline-delimited fields, no trailing newline)
if [ "$METHOD" == "GET" ]; then
    CANONICAL=$(printf "WALLET-HMAC-V1\nGET\n%s\n\n%s\n%s\n\n%s" "$URI_PATH" "$KEY_ID" "$TIMESTAMP" "$BODY_HASH")
else
    CANONICAL=$(printf "WALLET-HMAC-V1\n%s\n%s\n\n%s\n%s\n%s\n%s" "$METHOD" "$URI_PATH" "$KEY_ID" "$TIMESTAMP" "$OP_ID" "$BODY_HASH")
fi

SIGNATURE=$(printf "%s" "$CANONICAL" | openssl dgst -sha256 -hmac "$SECRET" 2>/dev/null | awk '{print $2}')

# Handle Server-Sent Events stream
if [[ "$URI_PATH" == */stream ]] && [ "$METHOD" == "GET" ]; then
    echo "▶ Connecting to Server-Sent Events stream: ${EDGE_HOST}${URI_PATH}"
    echo "  Key ID    : $KEY_ID"
    echo "  Timestamp : $TIMESTAMP"
    echo "  Signature : $SIGNATURE"
    echo "-------------------------------------------------------------------------"
    exec curl -N -H "Accept: text/event-stream" \
        -H "X-Key-Id: $KEY_ID" \
        -H "X-Timestamp: $TIMESTAMP" \
        -H "X-Signature: $SIGNATURE" \
        -H "X-Nonce: $NONCE" \
        "${EDGE_HOST}${URI_PATH}"
fi

# Standard HTTP request
CURL_ARGS=(
    -s
    -w "\nHTTP_STATUS:%{http_code}"
    -X "$METHOD"
    -H "X-Key-Id: $KEY_ID"
    -H "X-Timestamp: $TIMESTAMP"
    -H "X-Signature: $SIGNATURE"
    -H "X-Nonce: $NONCE"
)

if [ "$METHOD" != "GET" ]; then
    CURL_ARGS+=(-H "Idempotency-Key: $OP_ID")
fi

if [ -n "$PAYLOAD" ]; then
    CURL_ARGS+=(-H "Content-Type: application/json" -d "$PAYLOAD")
fi

RESPONSE=$(curl "${CURL_ARGS[@]}" "${EDGE_HOST}${URI_PATH}")
BODY=$(echo "$RESPONSE" | sed -e '$d')
STATUS=$(echo "$RESPONSE" | tail -n1 | cut -d: -f2)

echo "HTTP Status Code : $STATUS"
echo "Response Payload :"
if command -v jq >/dev/null 2>&1; then
    echo "$BODY" | jq . 2>/dev/null || echo "$BODY"
else
    echo "$BODY"
fi
