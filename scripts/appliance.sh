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
    echo "  test-tx         Execute an end-to-end deposit transaction through Edge"
    echo "  test-webhook [url] Trigger a Portainer Stack Redeploy Webhook"
    echo "  clean           Stop and remove all volumes, containers, and local data"
    echo ""
    echo "Options:"
    echo "  --lean          Launch in ultra-lean mode (<8GB RAM) without telemetry (VictoriaLogs/VictoriaTraces)"
    echo ""
    exit 1
}

MODE="telemetry"
ACTION="${1:-}"

# Parse optional --lean flag
for arg in "$@"; do
    if [ "$arg" == "--lean" ]; then
        MODE="lean"
    fi
done

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

    test-tx)
        print_banner
        OP_ID="$(uuidgen 2>/dev/null || cat /proc/sys/kernel/random/uuid)"
        WALLET_ID="a0000000-0000-0000-0000-000000000001"
        USER_ID="b0000000-0000-0000-0000-000000000001"

        echo "▶ Sending live Deposit Transaction through Edge Ingress (Port 8080)..."
        echo "  Operation ID : $OP_ID"
        echo "  Amount       : 150.00 USD"
        echo ""

        RESPONSE=$(curl -s -w "\nHTTP_STATUS:%{http_code}" -X POST http://localhost:8080/operations/deposits \
            -H "Content-Type: application/json" \
            -H "Idempotency-Key: $OP_ID" \
            -d "{
                \"walletId\": \"$WALLET_ID\",
                \"userId\": \"$USER_ID\",
                \"amount\": \"150.00\",
                \"operationOrigin\": \"USER\"
            }")

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
