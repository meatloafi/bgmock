#!/bin/bash
# BGMock Simulator Runner

# Get the directory where the script is located
SCRIPT_DIR="$( cd "$( dirname "${BASH_SOURCE[0]}" )" && pwd )"
cd "$SCRIPT_DIR"

# Detect Minikube IP
MINIKUBE_IP=$(minikube ip 2>/dev/null || echo "127.0.0.1")

echo "=================================================="
echo "🚀 Starting Simulator Configuration"
echo "=================================================="
echo "Minikube IP detected: $MINIKUBE_IP"

# Export environment variables for the Python script
export KAFKA_BOOTSTRAP_SERVERS="bgmock-kafka-kafka-bootstrap.kafka.svc.cluster.local:9092"
export KAFKA_GROUP_ID="simulator-ui-group"
export BANK_A_URL="http://$MINIKUBE_IP:30081"
export BANK_B_URL="http://$MINIKUBE_IP:30082"
export CLEARING_SERVICE_URL="http://$MINIKUBE_IP:30083"

echo "Service URLs:"
echo "  Bank A:   $BANK_A_URL"
echo "  Bank B:   $BANK_B_URL"
echo "  Clearing: $CLEARING_SERVICE_URL"
echo "  Kafka:    $KAFKA_BOOTSTRAP_SERVERS"
echo "=================================================="

# Set Python path
export PYTHONPATH="${SCRIPT_DIR}/src"

# Run
./venv/bin/streamlit run src/ui/streamlit_app.py --server.headless true