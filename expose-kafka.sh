#!/bin/bash
echo "======================================================"
echo "🔗 Exposing Kafka for Local Simulator"
echo "======================================================"

# kill existing port forward if any
pkill -f "port-forward.*9092" || true

echo "Forwarding Kafka port 9092..."
kubectl port-forward -n kafka svc/bgmock-kafka-kafka-bootstrap 9092:9292 &

echo "Waiting for connection..."
sleep 5
echo "✅ Kafka is now accessible at localhost:9092"