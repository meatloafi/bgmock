# BGMock Digital Twin Simulator

A Python-based interactive simulator and digital twin for the BGMock banking microservices system. Provides real-time monitoring, transaction simulation, and system testing capabilities.

## Quick Start

### 1. Setup (First Time Only)

```bash
# Create virtual environment
python3 -m venv venv

# Install dependencies
./venv/bin/pip install streamlit plotly pandas confluent-kafka kafka-python requests \
  sqlalchemy psycopg2-binary python-dotenv pydantic pydantic-settings colorama pyyaml
```

### 2. Run the Simulator

```bash
./run_simulator.sh
```

Open http://localhost:8501 in your browser.

## Features

- **Real-time Dashboard** - Transaction statistics and system metrics
- **Service Health Monitoring** - Track status of Bank A, Bank B, Clearing Service, and Kafka
- **Transaction Simulation** - Create individual or batch transactions
- **Kafka Event Stream** - View real-time message flow
- **REST API Monitoring** - Track API calls and response times
- **Analytics & Visualizations** - Charts, graphs, and data export
- **Offline Mode** - Works without backend services for demos and development

## Deployment Options

### Option 1: Offline Mode (Fastest - Current Setup)

Run the simulator standalone without any backend services:

```bash
./run_simulator.sh
```

Perfect for:
- UI development and testing
- Demos and presentations
- Learning the system

### Option 2: With Kafka (Recommended for Testing)

Start Kafka with Docker Compose:

```bash
# Start Kafka
docker-compose -f docker-compose-kafka.yml up -d

# Wait ~30 seconds for Kafka to start
docker logs bgmock-kafka --tail 20

# Run simulator
./run_simulator.sh
```

The simulator will now show Kafka as online (✅) and can send/receive messages.

**Stop Kafka:**
```bash
docker-compose -f docker-compose-kafka.yml down
```

### Option 3: Full Stack (Kubernetes)

For complete integration testing with all services:

1. **Start Minikube:**
```bash
minikube start
```

2. **Install Strimzi Operator (for Kafka):**
```bash
kubectl create namespace kafka
kubectl create -f 'https://strimzi.io/install/latest?namespace=kafka' -n kafka
kubectl wait --for=condition=ready pod -l name=strimzi-cluster-operator -n kafka --timeout=300s
```

3. **Deploy Infrastructure:**
```bash
cd /Users/meatloaf/Documents/Projekt/HiQ/bgmock
kubectl apply -f k8s/kafka/kafka-cluster.yaml
kubectl apply -f k8s/postgres/
```

4. **Build and Deploy Services:**
```bash
# Build with Maven
mvn clean install -DskipTests

# Use Minikube's Docker daemon
eval $(minikube docker-env)

# Build Docker images
cd bank-service && docker build -t bank-service:latest . && cd ..
cd clearing-service && docker build -t clearing-service:latest . && cd ..

# Deploy
kubectl apply -f k8s/bank-service/
kubectl apply -f k8s/clearing-service/
```

5. **Configure Simulator:**
```bash
export KAFKA_BOOTSTRAP_SERVERS=$(minikube ip):9092
export BANK_A_URL=http://$(minikube ip):30081
export BANK_B_URL=http://$(minikube ip):30082
export CLEARING_SERVICE_URL=http://$(minikube ip):30083
```

6. **Run Simulator:**
```bash
./run_simulator.sh
```

All services should now show as online (✅) in the dashboard!

## Configuration

Environment variables (optional - create `.env` file):

```env
# Kafka
KAFKA_BOOTSTRAP_SERVERS=localhost:9092
KAFKA_GROUP_ID=digital-twin-simulator

# Bank Services
BANK_A_URL=http://localhost:30081
BANK_B_URL=http://localhost:30082

# Clearing Service
CLEARING_SERVICE_URL=http://localhost:30083

# Databases (for direct state verification)
DB_BANK_A=postgresql://bank_a_user:password@localhost:5432/bank_a_db
DB_BANK_B=postgresql://bank_b_user:password@localhost:5432/bank_b_db
DB_SWITCH=postgresql://switch_user:password@localhost:5432/switch_db
```

## Service Health Indicators

The sidebar shows real-time status:
- ✅ **Green checkmark** - Service is online and responding
- ❌ **Red X** - Service is offline or unreachable

## Using the Simulator

### Create a Single Transaction

1. Use the sidebar form "Create Transaction"
2. Enter account numbers and amount
3. Choose REST API or Kafka delivery
4. Click "Send Transaction"

### Run a Simulation

1. Set number of transactions (1-100)
2. Set delay between transactions (0.5-10 seconds)
3. Click "Run Simulation"
4. Watch the dashboard update in real-time

### View Analytics

Navigate through tabs:
- **Dashboard** - Overview and statistics
- **Accounts** - View bank accounts and balances
- **Transactions** - Transaction history and timeline
- **Kafka Events** - Real-time event stream
- **REST Calls** - API call monitoring
- **Analytics** - Charts and data export

## Architecture

```
bgmock-simulator/
├── src/
│   ├── clients/          # Kafka and REST clients
│   ├── models/           # Data models (Transaction, Account)
│   ├── simulator/        # Core simulation logic
│   └── ui/              # Streamlit user interface
├── run_simulator.sh     # Startup script
├── docker-compose-kafka.yml  # Kafka setup
└── requirements.txt     # Python dependencies
```

## Troubleshooting

### Simulator won't start

**Check Python version:**
```bash
python3 --version  # Should be 3.13 or compatible
```

**Reinstall dependencies:**
```bash
rm -rf venv
python3 -m venv venv
./venv/bin/pip install -r requirements.txt
```

### Kafka shows as offline

**Check if Kafka is running:**
```bash
docker ps | grep kafka
```

**View Kafka logs:**
```bash
docker logs bgmock-kafka
```

**Restart Kafka:**
```bash
docker-compose -f docker-compose-kafka.yml restart
```

### Port 8501 already in use

**Find and kill the process:**
```bash
lsof -i :8501
kill -9 <PID>
```

**Or use a different port:**
```bash
PYTHONPATH=src ./venv/bin/streamlit run src/ui/streamlit_app.py --server.port 8502
```

### All services show as offline

This is normal! The simulator works perfectly in offline mode. Services only show as online when they're actually running and accessible.

## Technical Requirements

- Python 3.13+
- Docker (optional - for Kafka)
- Kubernetes/Minikube (optional - for full stack)
- 2GB RAM minimum
- macOS, Linux, or Windows (WSL2)

## Technology Stack

- **UI Framework**: Streamlit 1.51.0
- **Visualization**: Plotly, Pandas
- **Message Broker**: Kafka (confluent-kafka client)
- **HTTP Client**: Requests
- **Database**: SQLAlchemy, psycopg2
- **Data Validation**: Pydantic

## Key Features Explained

### Offline Mode

The simulator gracefully handles missing services:
- Continues running when backend services are unavailable
- Shows clear status indicators for each service
- Maintains full UI functionality for testing and demos
- Logs helpful warnings instead of crashing

### Kafka Integration

When Kafka is available:
- Subscribes to transaction topics automatically
- Displays real-time event stream
- Sends messages to configured topics
- Creates topics automatically if missing

### Health Monitoring

Background thread checks service health every 10 seconds:
- HTTP health endpoints for REST services
- Kafka broker connectivity check
- Updates dashboard indicators automatically

## Development

### Project Structure

- `src/config.py` - Configuration management
- `src/clients/kafka_client.py` - Kafka producer/consumer
- `src/clients/rest_client.py` - REST API clients
- `src/simulator/transaction_simulator.py` - Main simulation logic
- `src/simulator/transaction_monitor.py` - Real-time transaction and state monitoring
- `src/ui/streamlit_app.py` - User interface

### Adding New Features

1. Update models in `src/models/`
2. Add business logic in `src/simulator/`
3. Create UI components in `src/ui/`
4. Test in offline mode first
5. Test with Kafka
6. Test with full stack

## Support

For issues or questions:
- Check the troubleshooting section above
- Review service logs (`docker logs` for containers)
- Verify configuration in `.env` file
- Ensure all prerequisites are installed

## License

Internal project for educational purposes.

---

**Last Updated**: 2025-11-20
**Python Version**: 3.13.0
**Streamlit Version**: 1.51.0
**Kafka Version**: 4.0.0
