# BGMock - Banking Microservices System

A multi-service banking system demonstrating real-time transaction processing across two banks via an asynchronous Kafka-based clearing service, running on Kubernetes.

## Quick Start

Deploy to Kubernetes in 3-5 minutes:

```bash
# 1. Start Minikube
minikube start --cpus=4 --memory=8192 --driver=docker
eval $(minikube docker-env)

# 2. Deploy everything
chmod +x k8s/deploy-all.sh
./k8s/deploy-all.sh bank-services kafka true

# 3. Run the simulator
cd bgmock-simulator
pip install -r requirements.txt
streamlit run src/ui/streamlit_app.py
```

Open http://localhost:8501, create transactions, and watch them flow through the system.

**See DEMO.md for complete 3-hour demonstration guide.**

---

## How It Works (Brief)

```
Bank A (debit) → Clearing (route) → Bank B (credit) → Clearing (response) → Bank A (update)
    ↓                ↓                  ↓                   ↓                    ↓
transactions.   transactions.       transactions.      transactions.         Status
initiated       forwarded           processed          completed             updated
```

Transactions flow through 4 Kafka topics with guaranteed ordering per bank. Full visibility in the Streamlit dashboard.

---

## Project Structure

```
bgmock/
├── bank-service/              # Spring Boot service for Bank A/B
├── clearing-service/          # Spring Boot clearing service
├── common/                    # Shared Java event models
├── bgmock-simulator/          # Python Streamlit dashboard
├── k8s/                       # Kubernetes manifests
│   ├── kafka/                 # Kafka cluster (Strimzi)
│   ├── bank-service/          # Bank deployments
│   ├── clearing-service/      # Clearing deployment
│   ├── postgres/              # Database StatefulSets
│   └── deploy-all.sh          # One-command deployment
├── TESTING.md                 # Testing guide
├── DEMO.md                    # 3-hour demo guide
└── pom.xml                    # Maven parent
```

---

## Architecture

### Kubernetes Components

**kafka namespace:**
- Kafka cluster (1 broker, KRaft mode)
- Strimzi operator

**bank-services namespace:**
- Bank A service (2 replicas)
- Bank B service (2 replicas)
- Clearing service (2 replicas)
- PostgreSQL Bank A (StatefulSet)
- PostgreSQL Bank B (StatefulSet)
- PostgreSQL Clearing (StatefulSet)

### Services

| Service | Port | Address |
|---------|------|---------|
| Bank A | 30081 | http://192.168.49.2:30081 |
| Bank B | 30082 | http://192.168.49.2:30082 |
| Clearing | 30083 | http://192.168.49.2:30083 |
| Kafka | 9092 | bgmock-kafka-kafka-plain-bootstrap.kafka.svc.cluster.local |

### Kafka Topics

| Topic | Direction | Purpose |
|-------|-----------|---------|
| transactions.initiated | Bank A → Clearing | Outgoing transaction |
| transactions.forwarded | Clearing → Bank B | Forward to recipient |
| transactions.processed | Bank B → Clearing | Process response |
| transactions.completed | Clearing → Bank A | Final completion |

---

## Recent Fixes (MVP Complete)

1. **Bank Mapping** - Fixed hardcoded routing, now dynamic lookup
2. **Balance Validation** - Implemented proper debit/credit logic
3. **Simulator REST** - Now actually sends transactions to Bank A
4. **Kafka Topics** - Simulator now monitors correct 4-topic pipeline

See `DEMO.md` for detailed explanation of what was fixed and why.

---

## Manual Setup (if needed)

### Prerequisites

- Kubernetes cluster (Minikube recommended)
- kubectl, Helm 3.x
- Java 17, Maven 3.8+
- Docker

### Step-by-Step

```bash
# 1. Build images
cd bank-service && mvn package -DskipTests && docker build -t bank-service:latest .
cd ../clearing-service && mvn package -DskipTests && docker build -t clearing-service:latest .

# 2. Create namespaces
kubectl create namespace kafka
kubectl create namespace bank-services

# 3. Install Strimzi Kafka operator
helm repo add strimzi https://strimzi.io/charts && helm repo update
helm install strimzi-operator strimzi/strimzi-kafka-operator -n kafka

# 4. Deploy infrastructure
kubectl apply -f k8s/kafka/ -n kafka
kubectl apply -f k8s/postgres/ -n bank-services

# 5. Deploy services
kubectl apply -f k8s/bank-service/ -n bank-services
kubectl apply -f k8s/clearing-service/ -n bank-services

# 6. Verify
kubectl get pods -n bank-services
kubectl get pods -n kafka
```

---

## Common Commands

```bash
# View pods
kubectl get pods -n bank-services

# View logs
kubectl logs <pod-name> -n bank-services

# Connect to database
kubectl exec -it postgres-bank-a-0 -n bank-services -- \
  psql -U bank_a_user -d bank_a_db

# Scale service
kubectl scale deployment/bankgood-bank-a --replicas=5 -n bank-services

# View Kafka topics
kubectl exec -it bgmock-kafka-kafka-0 -n kafka -- \
  /opt/kafka/bin/kafka-topics.sh --list --bootstrap-server localhost:9092
```

---

## Troubleshooting

### Pods not starting?
```bash
kubectl describe pod <pod-name> -n bank-services
kubectl logs <pod-name> -n bank-services
```

### Services not responding?
```bash
# Test connectivity
curl http://192.168.49.2:30081/health

# Or from inside cluster
kubectl run -it --rm test --image=alpine --restart=Never -n bank-services -- \
  wget -O- http://bankgood-bank-a-service:8080/health
```

### Kafka not ready?
```bash
kubectl get kafka -n kafka
kubectl logs -l strimzi.io/cluster=bgmock-kafka -n kafka
```

---

## Technology Stack

- Spring Boot 3.2, Java 17
- Apache Kafka 4.0, Strimzi operator
- PostgreSQL 15
- Kubernetes, Docker
- Python 3.9+, Streamlit

---

## For Demonstrations

**See `DEMO.md` for:**
- Complete 3-hour demo guide
- Example transactions
- Failure scenarios
- Verification checklist
- Live monitoring walkthrough

---

## Testing

```bash
# Run tests
mvn test

# Run specific test
mvn test -Dtest=KafkaIntegrationTest
```

See `TESTING.md` for comprehensive testing documentation.

---

**Status:** MVP complete, ready for demonstration on Kubernetes ✅

