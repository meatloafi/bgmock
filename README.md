# BG-mock

## Prerequisites
- [Minikube](https://minikube.sigs.k8s.io/docs/)  
- [Docker](https://www.docker.com/)  
- [kubectl](https://kubernetes.io/docs/tasks/tools/)  
- [Strimzi](https://strimzi.io/quickstarts/)
---
## Start Local Cluster
```bash
minikube start

# 
eval $(minikube docker-env)
```
---

## Build Docker images
```bash 
docker build -t bank-service:latest bank-service
docker build -t clearing-service:latest clearing-service
```
---

## Deploy infrastructure
```bash 
kubectl apply -f k8s/postgres/
kubectl apply -f k8s/kafka/   # Kafka operator

# Wait for pods to start
kubectl get pods
kubectl get pods -n kafka
```
---

## Deploy services
```bash 
kubectl apply -f k8s/clearing-service/
kubectl apply -f k8s/bank-service/
```
---

## Updating Deployment with new docker image 
```bash 
# Rebuild image
docker build -t clearing-service:latest clearing-service

# Apply changes
kubectl rollout restart deployment clearing-service
kubectl rollout status deployment clearing-service
```

---
## Testing endpoints

1. Port forward service 
```bash 
kubectl port-forward svc/bankgood-bank-a-service 8080:8080
```

2. Send a request to the service
```bash
# Health check
curl http://localhost:8080/bank/health

# Create mock transaction
curl -X POST http://localhost:8080/api/transactions \
  -H "Content-Type: application/json" \
  -d '{
        "fromAccountId": "550e8400-e29b-41d4-a716-446655440000",
        "fromClearingNumber": "000001",
        "fromAccountNumber": "9876543210",
        "toBankgoodNumber": "000002",
        "amount": 2500.50,
        "status": "PENDING"
      }'
```
