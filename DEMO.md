# Demo Guide

## What Was Fixed

1. **Bank Mapping** - Transactions now route to correct bank (was hardcoded to Bank A)
2. **Balance Validation** - Sender balance checked before debit, recipient credited on receipt
3. **Simulator** - Actually sends transactions via REST (was only monitoring)
4. **Kafka Topics** - Simulator monitors actual 4-topic pipeline

## Quick Demo (5 minutes)

### 1. Deploy (3 min)
```bash
minikube start --cpus=4 --memory=8192 --driver=docker
eval $(minikube docker-env)
chmod +x k8s/deploy-all.sh && ./k8s/deploy-all.sh
```

### 2. Run Simulator (2 min)
```bash
cd bgmock-simulator
pip install -r requirements.txt
streamlit run src/ui/streamlit_app.py
```

### 3. Create Transaction
- Open http://localhost:8501
- Go to "Create Transaction" section
- From: 1111111111 (Alice, Bank A, 10,000)
- To: 3333333333 (Charlie, Bank B, 7,500)
- Amount: 100

**Watch:**
- Alice's balance drops: 10,000 → 9,900 (debit happens immediately)
- 4 Kafka events appear in sequence in "Kafka Events" tab:
  1. transactions.initiated (Bank A → Clearing)
  2. transactions.forwarded (Clearing → Bank B)
  3. transactions.processed (Bank B → Clearing)
  4. transactions.completed (Clearing → Bank A)
- Charlie's balance increases: 7,500 → 7,600 (credit after clearing)

## What This Proves

✅ **Multi-bank routing** - Transaction correctly routed from Bank A to Bank B
✅ **Balance tracking** - Both banks' balances updated correctly
✅ **Asynchronous messaging** - Kafka carries message through 4 stages
✅ **End-to-end flow** - Transaction completes with proper status
✅ **Kubernetes reliability** - Services recover from failures, scale horizontally

## Test Failure Scenario

Try to send 10,000 from Bob (only has 5,000) → Transaction rejected, balance stays 5,000

## Test Load

Go to "Advanced Testing" tab → Run "Constant Load" at 5 tx/sec for 30 sec → All 150 succeed

## Verify Database

```bash
# See that data is actually persisted
kubectl exec -it postgres-bank-a-0 -n bank-services -- \
  psql -U bank_a_user -d bank_a_db -c "SELECT account_number, balance FROM accounts;"

# See transaction logs
kubectl exec -it postgres-bank-a-0 -n bank-services -- \
  psql -U bank_a_user -d bank_a_db -c "SELECT * FROM outgoing_transactions LIMIT 1;"
```

## Key Talking Points

- **Kafka for decoupling:** Bank A doesn't wait for Bank B response
- **Message ordering:** Messages keyed by bank clearing number for consistent ordering
- **Kubernetes self-healing:** Kill a pod, watch it auto-restart
- **Scalability:** `kubectl scale deployment/bankgood-bank-a --replicas=5` adds more instances

## Cleanup

```bash
kubectl delete namespace bank-services kafka
minikube stop
```
