# BGMock - Banking & Clearing System

A microservices-based banking and clearing system implementation using Spring Boot, Kafka, and PostgreSQL, deployable on Kubernetes.

## Architecture Overview

The system consists of:
- **Bank Services**: Multiple bank instances (Bank A, Bank B) handling customer transactions
- **Clearing Service**: Central clearing house for inter-bank transaction processing
- **Common Module**: Shared entities, events, and test utilities
- **Kafka**: Message broker for asynchronous communication
- **PostgreSQL**: Persistent storage for each service

## Project Structure

```
bgmock/
├── bank-service/          # Bank microservice
├── clearing-service/      # Clearing house service
├── common/                # Shared code and test utilities
├── k8s/                   # Kubernetes deployment manifests
│   ├── bank-service/      # Bank deployments (bank-a, bank-b)
│   ├── clearing-service/  # Clearing service deployment
│   ├── kafka/             # Kafka cluster configuration
│   ├── postgres/          # PostgreSQL StatefulSets
│   └── commands.md        # Kubectl quick reference
├── TESTING.md             # Comprehensive testing guide
└── pom.xml                # Maven parent POM
```

## Prerequisites

- **Java 17** or higher
- **Maven 3.8+**
- **Docker Desktop** (for local development)
- **Minikube** or Kubernetes cluster (for deployment)
- **kubectl** (for K8s management)

## Quick Start

### 1. Build the Project

```bash
mvn clean install
```

### 2. Run Tests

```bash
mvn test
```

### 3. Build Docker Images

```bash
docker build -f bank-service/Dockerfile -t bank-service:latest .
docker build -f clearing-service/Dockerfile -t clearing-service:latest .
```

### 4. Deploy to Kubernetes

```bash
# Start Minikube
minikube start

# Configure Docker to use Minikube's daemon
eval $(minikube docker-env)

# Deploy infrastructure
kubectl apply -f k8s/kafka/
kubectl apply -f k8s/postgres/

# Deploy services
kubectl apply -f k8s/bank-service/
kubectl apply -f k8s/clearing-service/

# Check deployment status
kubectl get pods
kubectl get services
```

### 5. Access Services

```bash
# Get Minikube IP
minikube ip

# Access Bank A (NodePort 30081)
curl http://$(minikube ip):30081/health

# Access Bank B (NodePort 30082)
curl http://$(minikube ip):30082/health

# Or use Minikube service URLs
minikube service bankgood-bank-a-service --url
```

## Development

### Local Testing

The project uses embedded Kafka and H2 database for integration tests, allowing you to test without external dependencies:

```bash
# Run all tests
mvn test

# Run specific test class
mvn test -Dtest=KafkaIntegrationTest

# Run specific test method
mvn test -Dtest=KafkaIntegrationTest#testProducer_sendsTransactionEventCorrectly
```

See [TESTING.md](TESTING.md) for comprehensive testing documentation.

### Maven Modules

The project is organized as a multi-module Maven project:

- **Parent POM**: Manages dependency versions and plugin configurations
- **Common**: Shared models, events, and test utilities
- **Bank Service**: Bank microservice implementation
- **Clearing Service**: Clearing house implementation

Dependencies are centrally managed in the parent POM for consistency.

## Kafka Topics

The system uses the following Kafka topics for communication:

| Topic | Producer | Consumer | Purpose |
|-------|----------|----------|---------|
| `payment.requests` | Bank Services | - | Payment initiation |
| `payment.prepare` | - | Bank Services | Payment preparation |
| `transactions.outgoing` | Bank Services | Clearing Service | Outgoing transactions |
| `transactions.response` | Clearing Service | Bank Services | Transaction responses |
| `transactions.incoming.<bank>` | Clearing Service | Bank Services | Bank-specific incoming transactions |

## Configuration

### Application Properties

Each service has its own `application.properties`:
- `bank-service/src/main/resources/application.properties`
- `clearing-service/src/main/resources/application.properties`

### Environment Variables

Key environment variables (set in K8s deployments):

```bash
# Database
SPRING_DATASOURCE_URL=jdbc:postgresql://host:5432/dbname
SPRING_DATASOURCE_USERNAME=username
SPRING_DATASOURCE_PASSWORD=password

# Kafka
SPRING_KAFKA_BOOTSTRAP_SERVERS=kafka:9092

# Bank-specific
BANK_CLEARING_NUMBER=000001
```

## Kubernetes Deployment

All Kubernetes manifests are in the `/k8s` directory:

- **Kafka Cluster**: Strimzi-based KRaft mode (Zookeeper-less)
- **PostgreSQL**: StatefulSets with persistent volumes
- **Services**: Deployments with 2 replicas each
- **NodePorts**: External access to services

See [k8s/commands.md](k8s/commands.md) for kubectl quick reference.

## Technology Stack

- **Framework**: Spring Boot 3.2.0
- **Java**: 17
- **Build Tool**: Maven
- **Message Broker**: Apache Kafka
- **Database**: PostgreSQL (production), H2 (testing)
- **Container**: Docker
- **Orchestration**: Kubernetes (Minikube)
- **Testing**: JUnit 5, Spring Kafka Test, Embedded Kafka

## Common Operations

### View Logs

```bash
# List pods
kubectl get pods

# View logs
kubectl logs <pod-name>

# Follow logs in real-time
kubectl logs -f <pod-name>
```

### Restart Service

```bash
kubectl rollout restart deployment/bankgood-bank-a
```

### Scale Service

```bash
kubectl scale deployment/bankgood-bank-a --replicas=3
```

### Database Access

```bash
# Connect to PostgreSQL pod
kubectl exec -it <postgres-pod-name> -- psql -U username -d dbname

# List tables
\dt

# Exit
\q
```

## Documentation

- [TESTING.md](TESTING.md) - Comprehensive testing guide
- [k8s/commands.md](k8s/commands.md) - Kubernetes command reference
- [bank-service/system_overview.md](bank-service/system_overview.md) - Architecture deep dive (Swedish)

## Contributing

1. Make changes in a feature branch
2. Ensure all tests pass: `mvn test`
3. Build successfully: `mvn clean install`
4. Update documentation as needed

## License

Internal project for educational purposes.

---

**Last Updated**: 2025-11-17

