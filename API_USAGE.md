# 📡 API Usage (cURL Examples)

Below are basic examples to interact with the Wallet API. All mutating operations (POST) require an **Idempotency-Key** (UUID) in the header.

> Base URL:
http://localhost:8080

---

## 🪪 Create Wallet

### Without initial balance
```bash
curl -X POST http://localhost:8080/wallets \
  -H "Content-Type: application/json" \
  -H "Idempotency-Key: $(uuidgen)" \
  -d '{}'
```

### With initial balance
```bash
curl -X POST http://localhost:8080/wallets \
  -H "Content-Type: application/json" \
  -H "Idempotency-Key: $(uuidgen)" \
  -d '{
        "initialBalance": 200
      }'
```

---

## 💰 Get Current Balance

```bash
curl http://localhost:8080/wallets/{walletId}/balance
```

---

## 🕰 Get Historical Balance

```bash
curl "http://localhost:8080/wallets/{walletId}/balance/historical?at=2026-03-26T10:00:00Z"
```

---

## 📜 Get Ledger Entries (Audit)

```bash
curl "http://localhost:8080/wallets/{walletId}/ledger?limit=100"
```

---

## 💸 Deposit

```bash
curl -X POST http://localhost:8080/operations/deposit \
  -H "Content-Type: application/json" \
  -H "Idempotency-Key: $(uuidgen)" \
  -d '{
        "walletId": "WALLET_ID",
        "amount": 100
      }'
```

---

## 💳 Withdraw

```bash
curl -X POST http://localhost:8080/operations/withdraw \
  -H "Content-Type: application/json" \
  -H "Idempotency-Key: $(uuidgen)" \
  -d '{
        "walletId": "WALLET_ID",
        "amount": 50
      }'
```

---

## 🔄 Transfer Between Wallets

```bash
curl -X POST http://localhost:8080/operations/transfer \
  -H "Content-Type: application/json" \
  -H "Idempotency-Key: $(uuidgen)" \
  -d '{
        "from": "FROM_WALLET_ID",
        "to": "TO_WALLET_ID",
        "amount": 50
      }'
```

---

## 🔁 Replay Wallet (Ledger Rebuild)

```bash
curl -X POST http://localhost:8080/wallets/{walletId}/replay \
  -H "Idempotency-Key: $(uuidgen)"
```

---

# 🧪 Example Flow (End-to-End)

```bash
# 1. Create wallets
W1=$(curl -s -X POST http://localhost:8080/wallets -H "Content-Type: application/json" -H "Idempotency-Key: $(uuidgen)" -d '{}' | jq -r '.walletId')
W2=$(curl -s -X POST http://localhost:8080/wallets -H "Content-Type: application/json" -H "Idempotency-Key: $(uuidgen)" -d '{}' | jq -r '.walletId')

# 2. Deposit into W1
curl -X POST http://localhost:8080/operations/deposit \
  -H "Content-Type: application/json" \
  -H "Idempotency-Key: $(uuidgen)" \
  -d "{\"walletId\":\"$W1\",\"amount\":200}"

# 3. Transfer to W2
curl -X POST http://localhost:8080/operations/transfer \
  -H "Content-Type: application/json" \
  -H "Idempotency-Key: $(uuidgen)" \
  -d "{\"from\":\"$W1\",\"to\":\"$W2\",\"amount\":50}"

# 4. Check balances
curl http://localhost:8080/wallets/$W1/balance
curl http://localhost:8080/wallets/$W2/balance
```

---

# ⚠️ Notes

- All mutating operations require an **Idempotency-Key** (UUID)
- Amount must be **positive**
- Transfers are **atomic**
- Ledger is **immutable and hash-chained**
- Replay can be used for **reconciliation and recovery**
