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
eval $(minikube docker-env)
```

## Build Docker images
```bash 
docker build -t bank-service:latest bank-service
docker build -t clearing-service:latest clearing-service
```

## Deploy infrastructure
```bash 
kubectl apply -f k8s/postgres/
kubectl apply -f k8s/kafka/   # Kafka operator

# Wait for pods to start
kubectl get pods
kubectl get pods -n kafka
```

## Deploy services
```bash 
kubectl apply -f k8s/clearing-service/
kubectl apply -f k8s/bank-service/
```

## Updating Deployment with new docker image 
```bash 
# Rebuild image
docker build -t clearing-service:latest clearing-service

# Apply changes
kubectl rollout restart deployment clearing-service
kubectl rollout status deployment clearing-service
```