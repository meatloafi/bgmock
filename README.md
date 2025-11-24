### Quickstart

```bash
minikube start

eval $(minikube docker-env)

docker build -t bank-service:latest bank-service
docker build -t clearing-service:latest clearing-service

kubectl apply -f k8s/postgres/
kubectl apply -f k8s/kafka/ 

// Wait for infrastructure to start
kubectl get pods 
kubectl get pods -n kafka 

kubectl apply -f k8s/clearing-service/
kubectl apply -f k8s/bank-service/
 ```