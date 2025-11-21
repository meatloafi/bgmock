#!/bin/bash

# BGMock Kubernetes Deployment Script
set -e

echo "======================================================"
echo "BGMock Kubernetes Deployment"
echo "======================================================"

NAMESPACE="${1:-bank-services}"
KAFKA_NAMESPACE="${2:-kafka}"

echo "Deploying to: $NAMESPACE (Kafka: $KAFKA_NAMESPACE)"
echo ""

# Step 1: Namespaces
echo "[1/6] Creating namespaces..."
kubectl create namespace $KAFKA_NAMESPACE 2>/dev/null || true
kubectl create namespace $NAMESPACE 2>/dev/null || true
echo "✓ Namespaces ready"
echo ""

# Step 2: Strimzi (skip if already installed)
echo "[2/6] Installing Strimzi Kafka operator..."
if ! kubectl get deployment strimzi-kafka-operator -n $KAFKA_NAMESPACE 2>/dev/null | grep -q strimzi; then
    echo "  Downloading Strimzi operator manifests..."
    kubectl apply -f https://github.com/strimzi/strimzi-kafka-operator/releases/download/0.43.0/strimzi-cluster-operator-0.43.0.yaml -n $KAFKA_NAMESPACE 2>/dev/null || true
    echo "  Waiting for operator to start..."
    sleep 10
else
    echo "  Strimzi operator already installed"
fi
echo "✓ Strimzi ready"
echo ""

# Step 3: Kafka + Topics
echo "[3/6] Deploying Kafka cluster and topics..."
kubectl apply -f k8s/kafka/kafka-cluster.yaml -n $KAFKA_NAMESPACE
sleep 5
kubectl apply -f k8s/kafka/kafka-topics.yaml -n $KAFKA_NAMESPACE
echo "  Kafka cluster started (waiting 30 seconds for stability)..."
sleep 30
echo "✓ Kafka deployed"
echo ""

# Step 4: PostgreSQL
echo "[4/6] Deploying PostgreSQL databases..."
kubectl apply -f k8s/postgres/postgres-bank-a.yaml -n $NAMESPACE
kubectl apply -f k8s/postgres/postgres-bank-b.yaml -n $NAMESPACE
kubectl apply -f k8s/postgres/postgres-switch.yaml -n $NAMESPACE
echo "  Databases deployed (waiting 20 seconds for startup)..."
sleep 20
echo "✓ PostgreSQL ready"
echo ""

# Step 5: Bank Services
echo "[5/6] Deploying Bank and Clearing services..."
kubectl apply -f k8s/bank-service/deployment-bank-a.yaml -n $NAMESPACE
kubectl apply -f k8s/bank-service/deployment-bank-b.yaml -n $NAMESPACE
kubectl apply -f k8s/clearing-service/deployment-clearing.yaml -n $NAMESPACE
echo "  Services deployed (waiting 20 seconds for startup)..."
sleep 20
echo "✓ Services deployed"
echo ""

# Step 6: Show URLs
echo "[6/6] Deployment complete"
echo ""
echo "======================================================"

MINIKUBE_IP=$(minikube ip 2>/dev/null || echo "127.0.0.1")
echo "Service URLs:"
echo "  Bank A:       http://$MINIKUBE_IP:30081"
echo "  Bank B:       http://$MINIKUBE_IP:30082"
echo "  Clearing:     http://$MINIKUBE_IP:30083"
echo "  Kafka:        bgmock-kafka-kafka-plain-bootstrap.$KAFKA_NAMESPACE.svc.cluster.local:9092"
echo ""
echo "Next: cd bgmock-simulator && pip install -r requirements.txt"
echo "      streamlit run src/ui/streamlit_app.py"
echo "======================================================"
