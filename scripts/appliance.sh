#!/usr/bin/env bash
# ==============================================================================
# 🏢 Wallet Service — Plan B Appliance Bootstrap & Orchestration Script
# ==============================================================================
# Automates starting, stopping, verifying, and testing the on-premises appliance
# stack preconfigured with Portainer CE, OpenObserve, and OTel Collector.
# ==============================================================================
set -euo pipefail

COMPOSE_FILE="docker-compose.appliance.yaml"

print_banner() {
    echo "========================================================================="
    echo "🏢 Wallet Service — Low-Cost Lean Appliance Controller (Plan B)"
    echo "========================================================================="
}

usage() {
    print_banner
    echo "Usage: $0 [command] [options]"
    echo ""
    echo "Commands:"
    echo "  start, up       Start all appliance services (default: with Portainer & VictoriaLogs/VictoriaTraces)"
    echo "  stop, down      Stop all running appliance services"
    echo "  restart         Restart all appliance services"
    echo "  status, ps      Display container health, resource utilization, and endpoints"
    echo "  logs [service]  Tail logs for a specific service (or all services)"
    echo "  update-schema   Apply latest PostgreSQL schema (docker/init/schema.sql)"
    echo "  test-tx         Execute an end-to-end deposit transaction through Edge"
    echo "  test-webhook [url] Trigger a Portainer Stack Redeploy Webhook"
    echo "  clean           Stop and remove all volumes, containers, and local data"
    echo ""
    echo "Options:"
    echo "  --lean          Launch in ultra-lean mode (<8GB RAM) without telemetry (VictoriaLogs/VictoriaTraces)"
    echo "  --update-schema Apply schema migrations automatically during start/restart"
    echo ""
    exit 1
}

MODE="telemetry"
UPDATE_SCHEMA="false"
ACTION="${1:-}"

# Parse optional flags
for arg in "$@"; do
    if [ "$arg" == "--lean" ]; then
        MODE="lean"
    fi
    if [ "$arg" == "--update-schema" ] || [ "$arg" == "--migrate" ]; then
        UPDATE_SCHEMA="true"
    fi
done

apply_postgres_schema() {
    echo "▶ Applying PostgreSQL schema migrations from docker/init/schema.sql..."

    # Check if postgres container is running
    local pg_cid
    pg_cid=$(docker compose -f "$COMPOSE_FILE" ps -q postgres 2>/dev/null || true)
    if [ -z "$pg_cid" ] || [ "$(docker inspect -f '{{.State.Running}}' "$pg_cid" 2>/dev/null || echo "false")" != "true" ]; then
        echo "   PostgreSQL container is not running. Starting postgres service..."
        docker compose -f "$COMPOSE_FILE" up -d postgres
    fi

    echo "▶ Waiting for PostgreSQL to be healthy and ready..."
    local ready=false
    for i in {1..30}; do
        if docker compose -f "$COMPOSE_FILE" exec -T postgres pg_isready -U wallet -d wallet > /dev/null 2>&1; then
            ready=true
            break
        fi
        sleep 1
    done

    if [ "$ready" != "true" ]; then
        echo "❌ PostgreSQL is not ready after 30 seconds."
        exit 1
    fi

    echo "▶ Executing docker/init/schema.sql against database 'wallet'..."
    if docker compose -f "$COMPOSE_FILE" exec -T -e PGPASSWORD=wallet postgres psql -U wallet -d wallet -v ON_ERROR_STOP=1 < docker/init/schema.sql; then
        echo "✅ PostgreSQL schema updated successfully!"
    else
        echo "❌ PostgreSQL schema update failed. Review errors above."
        exit 1
    fi
}

case "$ACTION" in
    start|up)
        print_banner
        echo "▶ Preparing local host directories..."
        mkdir -p ./spool-data

        # Detect stale PostgreSQL 17 /data directory to avoid PG 18 migration halt
        if [ -d "./postgres-data/data" ]; then
            echo "⚠️  Found legacy ./postgres-data/data directory from prior runs."
            echo "   Cleaning stale data to allow PostgreSQL 18 major-version cluster init..."
            rm -rf ./postgres-data
        fi

        # Synchronize PostgreSQL schema before Core starts to prevent out-of-sync schema errors
        echo "▶ Synchronizing PostgreSQL schema before starting application services..."
        apply_postgres_schema

        if [ "$MODE" == "telemetry" ]; then
            echo "▶ Launching complete stack (Edge + Core + NATS + DB + Portainer CE + VictoriaLogs + VictoriaTraces)..."
            docker compose -f "$COMPOSE_FILE" --profile telemetry up -d --build
        else
            echo "▶ Launching lean stack (<8GB RAM mode, without local VictoriaLogs/VictoriaTraces)..."
            docker compose -f "$COMPOSE_FILE" up -d --build
        fi

        echo ""
        echo "▶ Waiting for Core (port 8081) and Edge (port 8080) healthiness..."
        for i in {1..30}; do
            if curl -s -f http://localhost:8080/actuator/health/readiness > /dev/null 2>&1 && \
               curl -s -f http://localhost:8081/actuator/health > /dev/null 2>&1; then
                echo "✅ All wallet services are healthy and ready!"
                break
            fi
            sleep 2
        done

        echo ""
        echo "========================================================================="
        echo "🎉 Appliance Ready! Preconfigured Services & Dashboards:"
        echo "========================================================================="
        echo "  🌐 Portainer CE (Dashboard) : https://localhost:9443  (or http://localhost:9000)"
        if [ "$MODE" == "telemetry" ]; then
            echo "  📜 VictoriaLogs UI (Logs)    : http://localhost:9428/select/vmui/"
            echo "  🔍 VictoriaTraces UI (Traces): http://localhost:10428/select/vmui/"
        fi
        echo "  ⚡ Edge Gateway Ingress     : http://localhost:8080 (HTTP) | udp://localhost:8443 (QUIC)"
        echo "  🏦 Core Management (Mgmt)   : http://localhost:8081"
        echo "========================================================================="
        echo "  💡 Hint: Run '$0 test-tx' to execute a live transaction."
        echo "========================================================================="
        ;;

    stop|down)
        print_banner
        echo "▶ Gracefully shutting down appliance services..."
        docker compose -f "$COMPOSE_FILE" --profile telemetry down
        echo "✅ Appliance stopped."
        ;;

    restart)
        $0 stop
        $0 start "${@:2}"
        ;;

    status|ps)
        print_banner
        docker compose -f "$COMPOSE_FILE" --profile telemetry ps
        echo ""
        echo "▶ Probing Service Endpoints:"
        printf "  Edge Readiness Probe (8080) : "
        curl -s -o /dev/null -w "%{http_code}\n" http://localhost:8080/actuator/health/readiness || echo "UNREACHABLE"
        printf "  Core Health Probe (8081)    : "
        curl -s -o /dev/null -w "%{http_code}\n" http://localhost:8081/actuator/health || echo "UNREACHABLE"
        printf "  Portainer HTTP (9000)       : "
        curl -s -o /dev/null -w "%{http_code}\n" http://localhost:9000 || echo "UNREACHABLE"
        printf "  VictoriaLogs (9428)         : "
        curl -s -o /dev/null -w "%{http_code}\n" http://localhost:9428/health || echo "DISABLED"
        printf "  VictoriaTraces (10428)      : "
        curl -s -o /dev/null -w "%{http_code}\n" http://localhost:10428/health || echo "DISABLED"
        ;;

    logs)
        SERVICE="${2:-}"
        if [ -n "$SERVICE" ]; then
            docker compose -f "$COMPOSE_FILE" --profile telemetry logs -f "$SERVICE"
        else
            docker compose -f "$COMPOSE_FILE" --profile telemetry logs -f --tail=100
        fi
        ;;

    update-schema|update-db|migrate)
        print_banner
        apply_postgres_schema
        ;;

    test-tx)
        print_banner
        OP_ID="$(uuidgen 2>/dev/null || cat /proc/sys/kernel/random/uuid)"
        NONCE="$(uuidgen 2>/dev/null || cat /proc/sys/kernel/random/uuid)"
        WALLET_ID="a0000000-0000-0000-0000-000000000001"
        USER_ID="b0000000-0000-0000-0000-000000000001"
        KEY_ID="wallet-key-dev-1"
        SECRET="wallet-secret-dev-key-32-bytes!!"

        echo "▶ Sending live Deposit Transaction through Edge Ingress (Port 8080)..."
        echo "  Operation ID : $OP_ID"
        echo "  Nonce        : $NONCE"
        echo "  Amount       : 150.00 USD"
        echo "  Key ID       : $KEY_ID"
        echo ""

        TIMESTAMP=$(date +%s%3N 2>/dev/null || echo "$(($(date +%s) * 1000))")
        PAYLOAD="{\"walletId\":\"$WALLET_ID\",\"userId\":\"$USER_ID\",\"amount\":\"150.00\",\"operationOrigin\":\"USER\"}"
        BODY_HASH=$(printf "%s" "$PAYLOAD" | sha256sum | awk '{print $1}')
        CANONICAL=$(printf "WALLET-HMAC-V1\nPOST\n/operations/deposits\n\n%s\n%s\n%s\n%s" "$KEY_ID" "$TIMESTAMP" "$OP_ID" "$BODY_HASH")
        SIGNATURE=$(printf "%s" "$CANONICAL" | openssl dgst -sha256 -hmac "$SECRET" 2>/dev/null | awk '{print $2}')

        RESPONSE=$(curl -s -w "\nHTTP_STATUS:%{http_code}" -X POST http://localhost:8080/operations/deposits \
            -H "Content-Type: application/json" \
            -H "Idempotency-Key: $OP_ID" \
            -H "X-Nonce: $NONCE" \
            -H "X-Key-Id: $KEY_ID" \
            -H "X-Timestamp: $TIMESTAMP" \
            -H "X-Signature: $SIGNATURE" \
            -d "$PAYLOAD")

        BODY=$(echo "$RESPONSE" | sed -e '$d')
        STATUS=$(echo "$RESPONSE" | tail -n1 | cut -d: -f2)

        echo "HTTP Status Code : $STATUS"
        echo "Response Payload : $BODY"
        echo ""
        if [ "$STATUS" == "200" ] || [ "$STATUS" == "202" ]; then
            echo "✅ Transaction accepted and published to NATS JetStream!"
            echo "   Inspect logs in VictoriaLogs at: http://localhost:9428/select/vmui/ (Search: $OP_ID)"
            echo "   Inspect traces in VictoriaTraces at: http://localhost:10428/select/vmui/ (Search: $OP_ID)"
            echo "   Inspect the container state in Portainer at: https://localhost:9443"

            echo ""
            echo "▶ Verifying Replay Protection (Replaying same X-Nonce: $NONCE)..."
            REPLAY_RESPONSE=$(curl -s -w "\nHTTP_STATUS:%{http_code}" -X POST http://localhost:8080/operations/deposits \
                -H "Content-Type: application/json" \
                -H "Idempotency-Key: $OP_ID" \
                -H "X-Nonce: $NONCE" \
                -H "X-Key-Id: $KEY_ID" \
                -H "X-Timestamp: $TIMESTAMP" \
                -H "X-Signature: $SIGNATURE" \
                -d "$PAYLOAD")
            REPLAY_BODY=$(echo "$REPLAY_RESPONSE" | sed -e '$d')
            REPLAY_STATUS=$(echo "$REPLAY_RESPONSE" | tail -n1 | cut -d: -f2)
            echo "Replay Status Code : $REPLAY_STATUS"
            echo "Replay Response    : $REPLAY_BODY"
            if [ "$REPLAY_STATUS" == "401" ]; then
                echo "✅ Replay Protection Verified: Duplicate nonce correctly rejected (HTTP 401 DUPLICATE_NONCE)!"
            else
                echo "⚠️ Replay test did not return HTTP 401 (got $REPLAY_STATUS)."
            fi
        else
            echo "⚠️ Unexpected status $STATUS. Check logs with '$0 logs edge' or '$0 logs core'."
        fi
        ;;

    test-webhook)
        WEBHOOK_URL="${2:-}"
        print_banner
        if [ -z "$WEBHOOK_URL" ]; then
            echo "Usage: $0 test-webhook <PORTAINER_WEBHOOK_URL>"
            echo ""
            echo "Example:"
            echo "  $0 test-webhook http://localhost:9000/api/stacks/webhooks/d5f4a7c1-2b8e-4a92-9e8a-81a1796d8b94"
            echo ""
            echo "💡 Where to find this in Portainer:"
            echo "   1. Open Portainer: https://localhost:9443 or http://localhost:9000"
            echo "   2. Go to Stacks -> Select your appliance stack"
            echo "   3. Enable 'Webhook' under Stack details / Automatic updates and copy the URL."
            exit 1
        fi
        echo "▶ Triggering Portainer Stack Webhook..."
        echo "  URL: $WEBHOOK_URL"
        echo ""
        RESPONSE=$(curl -k -s -w "\nHTTP_STATUS:%{http_code}" -X POST "$WEBHOOK_URL")
        STATUS=$(echo "$RESPONSE" | tail -n1 | cut -d: -f2)
        BODY=$(echo "$RESPONSE" | sed -e '$d')

        echo "HTTP Status Code : $STATUS"
        [ -n "$BODY" ] && echo "Response Payload : $BODY"
        echo ""
        if [ "$STATUS" == "200" ] || [ "$STATUS" == "204" ]; then
            echo "✅ Portainer Webhook dispatched successfully!"
            echo "   Portainer is pulling updated images and redeploying the stack."
            echo "   Check Portainer logs with: docker logs -f wallet-appliance-portainer"
        else
            echo "⚠️ Portainer Webhook returned HTTP $STATUS."
            echo "   Verify the webhook token and ensure Portainer is reachable."
        fi
        ;;

    clean)
        print_banner
        echo "⚠️ WARNING: This will destroy all appliance data, volumes, and caches!"
        read -p "Are you sure? [y/N] " -n 1 -r
        echo ""
        if [[ $REPLY =~ ^[Yy]$ ]]; then
            docker compose -f "$COMPOSE_FILE" --profile telemetry down -v --remove-orphans
            rm -rf ./spool-data ./postgres-data
            echo "✅ Cleaned up all containers, volumes, and local data."
        else
            echo "Operation cancelled."
        fi
        ;;

    *)
        usage
        ;;
esac
