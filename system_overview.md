# Bankgiro System - Spring Boot + Kafka Architecture

## 🎯 Vad är Bankgiro?

**Förenklad förklaring:** Ett system där olika banker kan skicka pengar till varandra via en central "switch" (clearing-central). Istället för att varje bank måste integrera direkt med 100+ andra banker, pratar alla med EN central plats via Kafka.

**Real-world exempel:**
- Anna har konto i Bank A, vill betala 500 kr till Bob som har konto i Bank B
- Bank A skickar betalningen till Switch via Kafka
- Switch validerar och routar till Bank B via Kafka
- Bank B krediterar Bobs konto
- Allt måste följa ACID - inga pengar får försvinna eller skapas

---

## 🏗️ Systemarkitektur (Spring Boot + Kafka)

```
┌─────────────────────────────────────────────────────────────┐
│                     ANVÄNDARE                                │
│  Anna (Bank A)              Bob (Bank B)       Eva (Bank C)  │
└────────┬─────────────────────────┬─────────────────┬────────┘
         │                         │                 │
         ▼                         ▼                 ▼
┌─────────────────┐      ┌─────────────────┐  ┌─────────────────┐
│  BANK A SERVICE │      │  BANK B SERVICE │  │  BANK C SERVICE │
│  (Spring Boot)  │      │  (Spring Boot)  │  │  (Spring Boot)  │
├─────────────────┤      ├─────────────────┤  ├─────────────────┤
│ • REST API      │      │ • REST API      │  │ • REST API      │
│ • Kafka Producer│      │ • Kafka Producer│  │ • Kafka Producer│
│ • Kafka Consumer│      │ • Kafka Consumer│  │ • Kafka Consumer│
├─────────────────┤      ├─────────────────┤  ├─────────────────┤
│ PostgreSQL A    │      │ PostgreSQL B    │  │ PostgreSQL C    │
│ (Accounts)      │      │ (Accounts)      │  │ (Accounts)      │
└────────┬────────┘      └────────┬────────┘  └────────┬────────┘
         │                        │                     │
         └───────────────┬────────┴─────────────────────┘
                         │
                         ▼
              ┌──────────────────────┐
              │   KAFKA CLUSTER      │
              ├──────────────────────┤
              │ Topics:              │
              │ • payment.requests   │
              │ • payment.prepare    │
              │ • payment.commit     │
              │ • payment.rollback   │
              │ • payment.responses  │
              └──────────┬───────────┘
                         │
                         ▼
              ┌──────────────────────┐
              │  CLEARING SWITCH     │
              │  (Spring Boot)       │
              ├──────────────────────┤
              │ • Kafka Consumer     │
              │ • Kafka Producer     │
              │ • 2PC Coordinator    │
              │ • Transaction State  │
              ├──────────────────────┤
              │ PostgreSQL (Switch)  │
              │ (Transaction Log)    │
              └──────────────────────┘
                         │
                         ▼
                  ┌─────────────┐
                  │   Redis     │
                  │ (Locking)   │
                  └─────────────┘

┌────────────────────────────────────────────────────────────────┐
│              PYTHON SIMULATION LAYER                           │
├────────────────────────────────────────────────────────────────┤
│ • Load Generator (generera betalningar)                        │
│ • Monitoring & Metrics (samla stats)                           │
│ • Chaos Engineering (simulera fel)                             │
│ • Transaction Validator (verifiera ACID)                       │
└────────────────────────────────────────────────────────────────┘
```

---

## 📊 Dataflöde med Kafka: Betalning Steg-för-Steg

### Scenario: Anna (Bank A) → Bob (Bank B), 500 kr

```
1. INITIATE (REST API)
   ┌────────────────────────────────────────┐
   │ Anna: POST /api/payments               │
   │ {                                      │
   │   from: "SE111...A",                   │
   │   to: "SE222...B",                     │
   │   amount: 500,                         │
   │   idempotency_key: "uuid-123"          │
   │ }                                      │
   └────────────────────────────────────────┘
                    ↓
   ┌────────────────────────────────────────┐
   │ Bank A Spring Boot validerar:          │
   │ ✓ Konto finns                          │
   │ ✓ Tillräckligt saldo                   │
   │ ✓ Inte duplicate (idempotency key)    │
   └────────────────────────────────────────┘

2. KAFKA: PAYMENT REQUEST
   ┌────────────────────────────────────────┐
   │ Bank A Producer → Kafka Topic:        │
   │ "payment.requests"                     │
   │                                        │
   │ Message:                               │
   │ {                                      │
   │   transactionId: "tx-123",             │
   │   fromBank: "BANK_A",                  │
   │   toBank: "BANK_B",                    │
   │   fromAccount: "SE111...",             │
   │   toAccount: "SE222...",               │
   │   amount: 500.00,                      │
   │   timestamp: "2025-11-10T10:00:00Z"    │
   │ }                                      │
   └────────────────────────────────────────┘
                    ↓
   ┌────────────────────────────────────────┐
   │ Switch Consumer läser från            │
   │ "payment.requests"                     │
   │ • Loggar transaction                   │
   │ • Skapar 2PC state machine            │
   └────────────────────────────────────────┘

3. PHASE 1: PREPARE (Kafka)
   ┌────────────────────────────────────────┐
   │ Switch Producer → Kafka Topic:        │
   │ "payment.prepare"                      │
   │                                        │
   │ Message (to Bank A):                   │
   │ {                                      │
   │   transactionId: "tx-123",             │
   │   bank: "BANK_A",                      │
   │   action: "DEBIT",                     │
   │   accountId: "SE111...",               │
   │   amount: 500.00                       │
   │ }                                      │
   └────────────────────────────────────────┘
                    ↓
   ┌────────────────────────────────────────┐
   │ Bank A Consumer:                       │
   │ • Läser från "payment.prepare"         │
   │ • Debiterar Anna: 1000 → 500 kr       │
   │ • Status: PREPARED                     │
   │ • Producer → "payment.responses"       │
   │   {status: "PREPARED", ack: true}      │
   └────────────────────────────────────────┘
                    ↓
   ┌────────────────────────────────────────┐
   │ Switch Producer → Kafka Topic:        │
   │ "payment.prepare"                      │
   │                                        │
   │ Message (to Bank B):                   │
   │ {                                      │
   │   transactionId: "tx-123",             │
   │   bank: "BANK_B",                      │
   │   action: "VALIDATE",                  │
   │   accountId: "SE222...",               │
   │   amount: 500.00                       │
   │ }                                      │
   └────────────────────────────────────────┘
                    ↓
   ┌────────────────────────────────────────┐
   │ Bank B Consumer:                       │
   │ • Validerar Bob's konto finns          │
   │ • Producer → "payment.responses"       │
   │   {status: "READY", ack: true}         │
   └────────────────────────────────────────┘

4. PHASE 2: COMMIT (Kafka)
   ┌────────────────────────────────────────┐
   │ Switch Consumer läser responses:       │
   │ • Bank A: PREPARED ✓                   │
   │ • Bank B: READY ✓                      │
   │ → Alla OK, skicka COMMIT               │
   └────────────────────────────────────────┘
                    ↓
   ┌────────────────────────────────────────┐
   │ Switch Producer → Kafka Topic:        │
   │ "payment.commit"                       │
   │                                        │
   │ Message (to Bank B):                   │
   │ {                                      │
   │   transactionId: "tx-123",             │
   │   bank: "BANK_B",                      │
   │   action: "CREDIT",                    │
   │   accountId: "SE222...",               │
   │   amount: 500.00                       │
   │ }                                      │
   └────────────────────────────────────────┘
                    ↓
   ┌────────────────────────────────────────┐
   │ Bank B Consumer:                       │
   │ • Krediterar Bob: 2000 → 2500 kr      │
   │ • Producer → "payment.responses"       │
   │   {status: "COMPLETED", ack: true}     │
   └────────────────────────────────────────┘
                    ↓
   ┌────────────────────────────────────────┐
   │ Switch:                                │
   │ • Uppdaterar transaction log           │
   │ • Status: COMPLETED                    │
   │ • Producer → "payment.responses"       │
   │   (notify Bank A)                      │
   └────────────────────────────────────────┘

5. CONFIRMATION
   ┌────────────────────────────────────────┐
   │ Bank A Consumer:                       │
   │ • Läser "payment.responses"            │
   │ • Uppdaterar transaction: COMPLETED    │
   │                                        │
   │ Anna får response via WebSocket/Poll:  │
   │ {                                      │
   │   status: "COMPLETED",                 │
   │   transaction_id: "tx-123"             │
   │ }                                      │
   └────────────────────────────────────────┘
```

### Vid Fel: Rollback via Kafka
```
Bank A: PREPARED → Bank B: ERROR (konto spärrat)
             ↓
Switch → Kafka: "payment.rollback"
             ↓
Bank A Consumer: Credit Anna (återför 500 kr)
             ↓
Transaction: ROLLED_BACK
```

---

## 🗂️ Kafka Topics Design

### Topic: `payment.requests`
**Producer:** Banks  
**Consumer:** Switch  
**Purpose:** Initiera nya betalningar

**Message Schema:**
```json
{
  "transactionId": "uuid",
  "fromBank": "BANK_A",
  "toBank": "BANK_B",
  "fromAccount": "SE1234567890",
  "toAccount": "SE0987654321",
  "amount": 500.00,
  "currency": "SEK",
  "idempotencyKey": "uuid",
  "timestamp": "2025-11-10T10:00:00Z"
}
```

### Topic: `payment.prepare`
**Producer:** Switch  
**Consumer:** Banks  
**Purpose:** Phase 1 - reserve funds

**Message Schema:**
```json
{
  "transactionId": "uuid",
  "bank": "BANK_A",
  "action": "DEBIT|VALIDATE",
  "accountId": "SE1234567890",
  "amount": 500.00,
  "timestamp": "2025-11-10T10:00:01Z"
}
```

### Topic: `payment.commit`
**Producer:** Switch  
**Consumer:** Banks  
**Purpose:** Phase 2 - complete transfer

**Message Schema:**
```json
{
  "transactionId": "uuid",
  "bank": "BANK_B",
  "action": "CREDIT",
  "accountId": "SE0987654321",
  "amount": 500.00,
  "timestamp": "2025-11-10T10:00:02Z"
}
```

### Topic: `payment.rollback`
**Producer:** Switch  
**Consumer:** Banks  
**Purpose:** Rollback on failure

**Message Schema:**
```json
{
  "transactionId": "uuid",
  "bank": "BANK_A",
  "action": "ROLLBACK",
  "accountId": "SE1234567890",
  "amount": 500.00,
  "reason": "Recipient account blocked",
  "timestamp": "2025-11-10T10:00:03Z"
}
```

### Topic: `payment.responses`
**Producer:** Banks  
**Consumer:** Switch  
**Purpose:** ACK/NACK for 2PC phases

**Message Schema:**
```json
{
  "transactionId": "uuid",
  "bank": "BANK_A",
  "phase": "PREPARE|COMMIT|ROLLBACK",
  "status": "SUCCESS|FAILED",
  "message": "Optional error message",
  "timestamp": "2025-11-10T10:00:01.5Z"
}
```

---

## 🗄️ Datamodeller (Spring Boot Entities)

### Account Entity (per bank)
```java
@Entity
@Table(name = "accounts")
public class Account {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;
    
    @Column(unique = true, nullable = false)
    private String accountNumber;
    
    @Column(nullable = false, precision = 15, scale = 2)
    private BigDecimal balance;
    
    @Version
    private Long version; // Optimistic locking
    
    @CreationTimestamp
    private LocalDateTime createdAt;
    
    @UpdateTimestamp
    private LocalDateTime updatedAt;
}
```

### Transaction Entity (per bank + switch)
```java
@Entity
@Table(name = "transactions")
public class Transaction {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;
    
    @Column(nullable = false)
    private UUID fromAccountId;
    
    @Column(nullable = false)
    private UUID toAccountId;
    
    @Column(nullable = false)
    private String fromBank;
    
    @Column(nullable = false)
    private String toBank;
    
    @Column(nullable = false, precision = 15, scale = 2)
    private BigDecimal amount;
    
    @Enumerated(EnumType.STRING)
    private TransactionStatus status; // PENDING, PREPARED, COMPLETED, ROLLED_BACK
    
    @Column(unique = true)
    private String idempotencyKey;
    
    @CreationTimestamp
    private LocalDateTime createdAt;
    
    private LocalDateTime completedAt;
}
```

### TransactionLog Entity (switch only - immutable)
```java
@Entity
@Table(name = "transaction_logs")
public class TransactionLog {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;
    
    @Column(nullable = false)
    private UUID transactionId;
    
    @Enumerated(EnumType.STRING)
    private LogEvent event; // REQUEST, PREPARE, COMMIT, ROLLBACK, COMPLETE
    
    @Column(nullable = false)
    private String bankId;
    
    @CreationTimestamp
    private LocalDateTime timestamp;
    
    @Column(columnDefinition = "jsonb") // PostgreSQL JSONB
    private String payload;
}
```

---

## 🔐 ACID Implementation med Kafka

### Atomicity
- **2-Phase Commit via Kafka:** PREPARE topic → COMMIT topic
- **Transaction log:** Varje Kafka message loggas innan execution
- **Compensation:** Vid failure, ROLLBACK topic
- **Kafka Transactions:** Producer transactions för atomic multi-topic writes

### Consistency
- **Database constraints:** `CHECK (balance >= 0)`
- **Optimistic locking:** `@Version` på Account
- **Idempotency:** Unique idempotencyKey per transaction
- **Message ordering:** Kafka partitions by transactionId

### Isolation
- **Database:** `@Transactional` med SERIALIZABLE
- **Kafka consumer:** Single-threaded per partition
- **Optimistic locking:** Version checks på updates
- **Redis distributed lock:** För critical sections

### Durability
- **PostgreSQL WAL:** Write-Ahead Logging
- **Kafka persistence:** Messages persisted to disk
- **Kafka replication:** Replication factor = 3
- **Acks:** `acks=all` för producers

---

## 🐍 Python Simulation Layer

### 1. Load Generator
**Purpose:** Generera realistisk betalnings-traffic

```python
# simulation/load_generator.py
import random
from kafka import KafkaProducer
import json
from faker import Faker

class PaymentLoadGenerator:
    def __init__(self, kafka_bootstrap_servers):
        self.producer = KafkaProducer(
            bootstrap_servers=kafka_bootstrap_servers,
            value_serializer=lambda v: json.dumps(v).encode('utf-8')
        )
        self.faker = Faker('sv_SE')
    
    def generate_payment(self, from_bank, to_bank):
        """Generate random payment"""
        return {
            'transactionId': str(uuid.uuid4()),
            'fromBank': from_bank,
            'toBank': to_bank,
            'fromAccount': self.faker.iban(),
            'toAccount': self.faker.iban(),
            'amount': round(random.uniform(10, 10000), 2),
            'currency': 'SEK',
            'idempotencyKey': str(uuid.uuid4()),
            'timestamp': datetime.utcnow().isoformat()
        }
    
    def run(self, tps=10, duration_seconds=60):
        """Generate load at target TPS"""
        for _ in range(tps * duration_seconds):
            payment = self.generate_payment('BANK_A', 'BANK_B')
            self.producer.send('payment.requests', payment)
            time.sleep(1.0 / tps)
```

### 2. ACID Validator
**Purpose:** Verifiera att ACID-egenskaper hålls

```python
# simulation/acid_validator.py
from kafka import KafkaConsumer
import psycopg2

class ACIDValidator:
    def __init__(self, kafka_servers, db_connections):
        self.consumer = KafkaConsumer(
            'payment.responses',
            bootstrap_servers=kafka_servers
        )
        self.dbs = db_connections
    
    def validate_atomicity(self, transaction_id):
        """Verify all-or-nothing"""
        # Check if money disappeared or was created
        pass
    
    def validate_consistency(self):
        """Verify total balance unchanged"""
        total_before = sum(self.get_all_balances())
        # Wait for transactions
        time.sleep(10)
        total_after = sum(self.get_all_balances())
        assert total_before == total_after, "Money created/destroyed!"
    
    def validate_isolation(self):
        """Check for race conditions"""
        # Run concurrent transactions on same account
        # Verify no dirty reads
        pass
```

### 3. Chaos Engineering
**Purpose:** Simulera fel och testa resilience

```python
# simulation/chaos.py
import random
from kubernetes import client, config

class ChaosEngineer:
    def kill_random_pod(self, namespace='bank-a'):
        """Kill random pod to test recovery"""
        v1 = client.CoreV1Api()
        pods = v1.list_namespaced_pod(namespace)
        pod = random.choice(pods.items)
        v1.delete_namespaced_pod(pod.metadata.name, namespace)
        print(f"Killed pod: {pod.metadata.name}")
    
    def inject_network_latency(self, target_pod):
        """Inject 500ms network latency"""
        # Using tc (traffic control)
        pass
    
    def simulate_kafka_partition(self):
        """Simulate Kafka broker failure"""
        pass
```

### 4. Metrics Collector
**Purpose:** Samla och visualisera metrics

```python
# simulation/metrics.py
from kafka import KafkaConsumer
from prometheus_client import Counter, Histogram, Gauge
import time

class MetricsCollector:
    def __init__(self, kafka_servers):
        self.consumer = KafkaConsumer(
            'payment.responses',
            bootstrap_servers=kafka_servers
        )
        
        # Prometheus metrics
        self.success_counter = Counter(
            'payments_success_total',
            'Total successful payments'
        )
        self.failure_counter = Counter(
            'payments_failed_total',
            'Total failed payments'
        )
        self.latency_histogram = Histogram(
            'payment_latency_seconds',
            'Payment latency'
        )
    
    def run(self):
        """Consume responses and update metrics"""
        for message in self.consumer:
            response = json.loads(message.value)
            
            if response['status'] == 'SUCCESS':
                self.success_counter.inc()
            else:
                self.failure_counter.inc()
            
            # Calculate latency from timestamp
            latency = self._calculate_latency(response)
            self.latency_histogram.observe(latency)
```

---

## 📝 Spring Boot Project Structure

```
bankgiro-system/
├── bank-service/                    # Spring Boot per bank
│   ├── src/main/java/com/bankgiro/bank/
│   │   ├── BankApplication.java
│   │   ├── config/
│   │   │   ├── KafkaConfig.java     # Kafka producer/consumer config
│   │   │   └── DatabaseConfig.java
│   │   ├── entity/
│   │   │   ├── Account.java
│   │   │   └── Transaction.java
│   │   ├── repository/
│   │   │   ├── AccountRepository.java
│   │   │   └── TransactionRepository.java
│   │   ├── service/
│   │   │   ├── AccountService.java
│   │   │   ├── PaymentService.java
│   │   │   └── TransactionService.java
│   │   ├── kafka/
│   │   │   ├── PaymentRequestProducer.java
│   │   │   ├── PrepareConsumer.java
│   │   │   ├── CommitConsumer.java
│   │   │   └── RollbackConsumer.java
│   │   └── controller/
│   │       ├── AccountController.java
│   │       └── PaymentController.java
│   ├── src/main/resources/
│   │   ├── application.yml
│   │   └── schema.sql
│   └── pom.xml
│
├── switch-service/                  # Spring Boot clearing switch
│   ├── src/main/java/com/bankgiro/switch/
│   │   ├── SwitchApplication.java
│   │   ├── config/
│   │   │   └── KafkaConfig.java
│   │   ├── entity/
│   │   │   ├── Transaction.java
│   │   │   └── TransactionLog.java
│   │   ├── repository/
│   │   │   ├── TransactionRepository.java
│   │   │   └── TransactionLogRepository.java
│   │   ├── service/
│   │   │   ├── SwitchService.java
│   │   │   ├── TwoPhaseCommitCoordinator.java
│   │   │   └── RoutingService.java
│   │   └── kafka/
│   │       ├── PaymentRequestConsumer.java
│   │       ├── ResponseConsumer.java
│   │       ├── PrepareProducer.java
│   │       ├── CommitProducer.java
│   │       └── RollbackProducer.java
│   └── pom.xml
│
├── simulation/                      # Python simulations
│   ├── requirements.txt
│   ├── load_generator.py
│   ├── acid_validator.py
│   ├── chaos.py
│   └── metrics.py
│
├── k8s/                             # Kubernetes manifests
│   ├── kafka/
│   │   ├── kafka-deployment.yaml
│   │   └── zookeeper-deployment.yaml
│   ├── banks/
│   │   ├── bank-a-deployment.yaml
│   │   ├── bank-b-deployment.yaml
│   │   └── bank-c-deployment.yaml
│   ├── switch/
│   │   └── switch-deployment.yaml
│   └── monitoring/
│       ├── prometheus.yaml
│       └── grafana.yaml
│
├── docker-compose.yml               # Local development
└── README.md
```

---

## 🚀 Implementation Plan (Spring Boot + Kafka)

### FASE 1: Setup & Foundation (1 dag)
**Infrastructure:**
- [x] Kafka cluster (3 brokers, Zookeeper)
- [x] PostgreSQL per bank + switch
- [x] Redis för distributed locking
- [x] Spring Boot project structure

**Deliverable:** Local dev environment med docker-compose

---

### FASE 2: Bank Service Core (2 dagar)
**Spring Boot Components:**
- Account CRUD (JPA repository)
- Transaction management
- REST API endpoints
- Unit tests (JUnit + Mockito)

**Kafka Integration:**
- Producer: Send to `payment.requests`
- Consumer: Listen to `payment.prepare`, `payment.commit`, `payment.rollback`
- Integration tests med Embedded Kafka

**Deliverable:** En fungerande bank med Kafka integration

---

### FASE 3: Clearing Switch (2-3 dagar)
**Spring Boot Components:**
- Transaction log (immutable)
- 2PC state machine
- Timeout handling (Scheduled tasks)
- Bank registry

**Kafka Integration:**
- Consumer: `payment.requests`
- Producer: `payment.prepare`, `payment.commit`, `payment.rollback`
- Consumer: `payment.responses` (för ACK/NACK)

**Tests:**
- Integration: Multi-bank payment flow
- Chaos: Simulated bank failures

**Deliverable:** Switch som kan koordinera 3+ banker

---

### FASE 4: Python Simulations (1-2 dagar)
**Components:**
- Load generator (parameterbar TPS)
- ACID validator (verifiera invariants)
- Chaos engineering (kill pods, network issues)
- Metrics collector (Prometheus integration)

**Deliverable:** Simulation suite för testing och demo

---

### FASE 5: Kubernetes Deployment (1-2 dagar)
**K8s Resources:**
- Kafka StatefulSet (3 replicas)
- Bank Deployments (2 replicas each)
- Switch Deployment (2 replicas)
- PostgreSQL StatefulSets
- Services, Ingress, ConfigMaps, Secrets

**Monitoring:**
- Prometheus för metrics
- Grafana dashboards
- Kafka monitoring (Kafka Manager/Kafdrop)

**Deliverable:** Fully deployed system i Kubernetes

---

### FASE 6: Testing & Tuning (1 dag)
**Load Testing:**
- 50 TPS sustained för 5 minuter
- 100 TPS burst test
- Latency measurements (p50, p95, p99)

**Chaos Testing:**
- Kill bank pods during transactions
- Kill Kafka brokers
- Network partitions
- Database connection failures

**ACID Validation:**
- No money created/destroyed
- All transactions atomic
- Concurrent transaction handling

**Deliverable:** Test report med metrics

---

## ⏱️ Tidsestimat

**Med Spring Boot + Kafka:**
- Fase 1 (Setup): 1 dag
- Fase 2 (Bank Service): 2 dagar
- Fase 3 (Switch): 2-3 dagar
- Fase 4 (Python): 1-2 dagar
- Fase 5 (K8s): 1-2 dagar
- Fase 6 (Testing): 1 dag

**Total: 8-11 dagar** för en person

---

## 🎯 Minimal Viable Product (Spring Boot)

```yaml
MVP Scope:
  Backend: Spring Boot 3.x + Java 17
  Message Broker: Kafka 3.x
  Databases: PostgreSQL 15
  Cache: Redis 7
  Simulation: Python 3.11
  Deployment: Kubernetes (Minikube)
  
Banks: 3 (Bank A, B, C)
Kafka Topics: 5 (requests, prepare, commit, rollback, responses)
Target TPS: 50
  
Features:
  ✅ Account CRUD (REST API)
  ✅ Payment via Kafka (2PC)
  ✅ Transaction status query
  ✅ Idempotency
  ✅ ACID guarantees
  ✅ Python load generator
  ✅ Python ACID validator
  ✅ Basic monitoring
  
Testing:
  ✅ JUnit + Mockito (80% coverage)
  ✅ Integration with TestContainers
  ✅ Load testing med Python
  ✅ Chaos engineering
```

---

## 🚀 Next Steps

1. **Setup Kafka cluster** (docker-compose)
2. **Create Spring Boot bank-service** (Fase 2)
3. **Implement Kafka producers/consumers**
4. **Test payment flow** locally
5. **Build switch-service** (Fase 3)
6. **Python simulations** (Fase 4)
7. **Deploy to Kubernetes** (Fase 5)

**Vill ni att jag:**
- **A)** Skapar docker-compose.yml för local setup? (Kafka + PostgreSQL + Redis)
- **B)** Börjar med Spring Boot bank-service kod?
- **C)** Visar Kafka producer/consumer implementation?
- **D)** Skapar Python load generator först?
