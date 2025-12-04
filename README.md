# BG-mock

## Prerequisites
- [Minikube](https://minikube.sigs.k8s.io/docs/)  
- [Docker](https://www.docker.com/)  
- [kubectl](https://kubernetes.io/docs/tasks/tools/)  
- [Strimzi](https://strimzi.io/quickstarts/)
## Start Local Cluster
```bash
minikube start
eval $(minikube docker-env)
```

## Install Strimzi
```bash
kubectl create namespace kafka
kubectl create -f 'https://strimzi.io/install/latest?namespace=kafka' -n kafka
```

## Install Strimzi
```bash
kubectl create namespace kafka
kubectl create -f 'https://strimzi.io/install/latest?namespace=kafka' -n kafka
```
---

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
kubectl rollout restart deployment bankgood-bank-a
kubectl rollout restart deployment bankgood-bank-b
kubectl rollout restart deployment bankgood-clearing
```
## Running simulator

```bash
eval $(minikube docker-env)
docker build -t simulator:latest simple_simulator/
```

```bash
kubectl delete job simulator-job # If it exists already
kubectl apply -f k8s/simulator/simulator.yaml

# Check logs 
kubectl logs -f job/simulator-job
```

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

## Testing Locally Without Kafka

To test the service locally without Kafka, you can disable Kafka bean creation by adding the following to your `application.properties`:

```properties
kafka.enabled=false
```

This allows you to run the service and test REST endpoints without needing a Kafka broker or redeploying to Kubernetes.

### Port-forward the Database
If your database is running in Kubernetes, forward it to your local machine:

```bash
kubectl port-forward svc/<postgres_service_name> 5432:5432
```
Then configure your local application.properties to connect to the forwarded database:

```properties
spring.datasource.url=jdbc:postgresql://localhost:5432/<postgres_db_name>
spring.datasource.username=<postgres_db_user>
spring.datasource.password=<postgres_password>
spring.jpa.hibernate.ddl-auto=update
```
Now you can start your service locally and interact with it via REST endpoints.

