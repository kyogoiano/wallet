#!/usr/bin/env bash
# ==============================================================================
# 🏢 Wallet Service — Shell Authentication & Testing Helpers (WALLET-HMAC-V1)
# ==============================================================================
# Sourcing this file exports shell helper functions and default credentials
# to compute timestamps, canonical requests, and HMAC signatures before calling curl.
#
# Usage:
#   source scripts/wallet-security-env.sh
# ==============================================================================

export WALLET_KEY_ID="${WALLET_KEY_ID:-wallet-key-dev-1}"
export WALLET_SECRET="${WALLET_SECRET:-wallet-secret-dev-key-32-bytes!!}"
export KEY_ID="$WALLET_KEY_ID"
export SECRET="$WALLET_SECRET"
export EDGE_HOST="${EDGE_HOST:-http://localhost:8080}"

wallet_timestamp() {
    python3 -c 'import time; print(int(time.time() * 1000))' 2>/dev/null || \
    echo "$(($(date +%s%N 2>/dev/null || echo "$(date +%s)000000000") / 1000000))"
}

wallet_uuid() {
    uuidgen 2>/dev/null || cat /proc/sys/kernel/random/uuid 2>/dev/null || python3 -c 'import uuid; print(uuid.uuid4())'
}

wallet_body_hash() {
    local payload="${1:-}"
    if [ -n "$payload" ]; then
        printf "%s" "$payload" | sha256sum | awk '{print $1}'
    else
        echo "e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855"
    fi
}

wallet_canonical() {
    local method="${1:-POST}"
    local path="${2:-/operations/transfers}"
    local key_id="${3:-${WALLET_KEY_ID:-wallet-key-dev-1}}"
    local timestamp="${4:-}"
    local op_id="${5:-}"
    local body_hash="${6:-e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855}"
    printf "WALLET-HMAC-V1\n%s\n%s\n\n%s\n%s\n%s\n%s" "$method" "$path" "$key_id" "$timestamp" "$op_id" "$body_hash"
}

wallet_sign() {
    local canonical="$1"
    local secret="${2:-${WALLET_SECRET:-wallet-secret-dev-key-32-bytes!!}}"
    printf "%s" "$canonical" | openssl dgst -sha256 -hmac "$secret" 2>/dev/null | awk '{print $2}'
}

wallet_curl() {
    local method="${1:-POST}"
    local path="${2:-/operations/transfers}"
    local op_id=""
    local nonce=""
    local payload=""

    if [[ "$method" == "transfer" ]]; then
        local amount="${2:-150.00}"
        local from="${3:-a0000000-0000-0000-0000-000000000001}"
        local to="${4:-415f3af5-f559-4d95-9b4e-abdd1dfa18a8}"
        method="POST"
        path="/operations/transfers"
        op_id="${OP_ID:-$(wallet_uuid)}"
        nonce="${NONCE:-be9215b0-009c-4816-a4fe-b32229928b75}"
        payload="{\"sourceAccountId\":\"$from\",\"targetAccountId\":\"$to\",\"amount\":$amount,\"currency\":\"BRL\"}"
    elif [[ "$#" -eq 3 ]]; then
        op_id="${OP_ID:-$(wallet_uuid)}"
        nonce="${NONCE:-be9215b0-009c-4816-a4fe-b32229928b75}"
        payload="$3"
    elif [[ "$#" -ge 5 ]]; then
        op_id="$3"
        nonce="$4"
        payload="$5"
    else
        op_id="${3:-${OP_ID:-$(wallet_uuid)}}"
        nonce="${4:-${NONCE:-be9215b0-009c-4816-a4fe-b32229928b75}}"
        payload="${5:-}"
    fi

    if [[ "$nonce" == "random" || "$nonce" == "new" ]]; then
        nonce="$(wallet_uuid)"
    fi

    local key_id="${WALLET_KEY_ID:-wallet-key-dev-1}"
    local secret="${WALLET_SECRET:-wallet-secret-dev-key-32-bytes!!}"

    local timestamp
    local body_hash
    local canonical
    local signature

    timestamp=$(wallet_timestamp)
    body_hash=$(wallet_body_hash "$payload")
    canonical=$(wallet_canonical "$method" "$path" "$key_id" "$timestamp" "$op_id" "$body_hash")
    signature=$(wallet_sign "$canonical" "$secret")

    echo "▶ Request: $method ${EDGE_HOST:-http://localhost:8080}${path}"
    echo "  Content-Type    : application/json"
    echo "  Idempotency-Key : $op_id"
    echo "  X-Nonce         : $nonce"
    echo "  X-Key-Id        : $key_id"
    echo "  X-Timestamp     : $timestamp"
    echo "  X-Signature     : $signature"
    if [ -n "$payload" ]; then
        echo "  Payload         : $payload"
    fi
    echo "-------------------------------------------------------------------------"

    local curl_cmd=(
        curl -i -X "$method" "${EDGE_HOST:-http://localhost:8080}${path}"
        -H "Content-Type: application/json"
        -H "Idempotency-Key: $op_id"
        -H "X-Nonce: $nonce"
        -H "X-Key-Id: $key_id"
        -H "X-Timestamp: $timestamp"
        -H "X-Signature: $signature"
    )

    if [ -n "$payload" ]; then
        curl_cmd+=(-d "$payload")
    fi

    "${curl_cmd[@]}"
}
