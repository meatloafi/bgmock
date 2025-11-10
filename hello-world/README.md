# Bankgood Hello World - Minikube Deployment

Ett enkelt Spring Boot Hello World-projekt för Bankgood-systemet, deployat på Minikube.

## Förutsättningar

- Docker Desktop installerat och igång
- Minikube installerat
- kubectl installerat
- Java 17 (om du vill köra lokalt)
- Maven (om du vill bygga lokalt)

## Snabbstart

### 1. Starta Minikube

```bash
minikube start
```

### 2. Konfigurera Docker för Minikube

```bash
# På Windows (PowerShell)
& minikube -p minikube docker-env --shell powershell | Invoke-Expression

# På Mac/Linux
eval $(minikube docker-env)
```

### 3. Bygga Docker Image

```bash
cd hello-world
docker build -t bankgood-hello:v1 .
```

### 4. Verifiera att imagen finns

```bash
docker images | grep bankgood-hello
```

### 5. Deploya till Minikube

```bash
kubectl apply -f k8s/deployment.yaml
```

### 6. Kontrollera deployment

```bash
# Se pods
kubectl get pods

# Se service
kubectl get svc bankgood-hello-service

# Se deployment
kubectl get deployment bankgood-hello
```

### 7. Testa applikationen

```bash
# Hämta Minikube IP
minikube ip

# Testa Hello World endpoint
curl http://$(minikube ip):30080/

# Testa Health endpoint
curl http://$(minikube ip):30080/health
```

Eller öppna i webbläsare:
```bash
minikube service bankgood-hello-service
```

## Förväntad Output

- **/ endpoint**: `Hello World from Bankgood System! 🏦`
- **/health endpoint**: `OK`

## Hantera Deployment

### Skala upp/ner replicas

```bash
kubectl scale deployment bankgood-hello --replicas=3
```

### Se logs

```bash
# Lista pods
kubectl get pods

# Visa logs för en specifik pod
kubectl logs <pod-name>

# Följ logs i realtid
kubectl logs -f <pod-name>
```

### Radera deployment

```bash
kubectl delete -f k8s/deployment.yaml
```

## Troubleshooting

### Problem: Pods startar inte

```bash
# Kontrollera pod status
kubectl get pods

# Se detaljer om pod
kubectl describe pod <pod-name>

# Kolla events
kubectl get events --sort-by=.metadata.creationTimestamp
```

### Problem: ImagePullBackOff eller ErrImagePull

Detta betyder att Kubernetes inte hittar din Docker image.

**Lösning:**
1. Kontrollera att du har kört `eval $(minikube docker-env)` i samma terminal där du bygger imagen
2. Verifiera att `imagePullPolicy: Never` finns i deployment.yaml
3. Kör `docker images` för att se att `bankgood-hello:v1` finns

### Problem: Kan inte nå applikationen på port 30080

```bash
# Kontrollera att service är skapad
kubectl get svc

# Kontrollera Minikube IP
minikube ip

# Försök öppna med minikube service kommando
minikube service bankgood-hello-service --url
```

### Problem: CrashLoopBackOff

```bash
# Se logs för att identifiera felet
kubectl logs <pod-name>

# Kolla pod beskrivning
kubectl describe pod <pod-name>
```

Vanliga orsaker:
- Fel i Java-koden
- Port redan används
- Minnes- eller resursproblem

### Problem: Pods i Pending status

```bash
# Kontrollera resurser
kubectl describe pod <pod-name>

# Se nod-resurser
kubectl top nodes
```

## Uppdatera Applikationen

1. Gör dina ändringar i koden
2. Bygg om Docker imagen:
   ```bash
   docker build -t bankgood-hello:v2 .
   ```
3. Uppdatera deployment.yaml med ny image tag (v2)
4. Applicera ändringen:
   ```bash
   kubectl apply -f k8s/deployment.yaml
   ```

Eller använd rolling update:
```bash
kubectl set image deployment/bankgood-hello bankgood-hello=bankgood-hello:v2
```

## Struktur

```
hello-world/
├── src/
│   ├── main/
│   │   ├── java/com/bankgood/hello/
│   │   │   ├── HelloWorldApplication.java
│   │   │   └── HelloController.java
│   │   └── resources/
│   │       └── application.properties
│   └── test/
├── k8s/
│   └── deployment.yaml
├── Dockerfile
├── pom.xml
└── README.md
```

## Teknisk Stack

- **Framework**: Spring Boot 3.2.0
- **Java**: 17
- **Build Tool**: Maven
- **Container**: Docker
- **Orchestration**: Kubernetes (Minikube)
- **Base Image**: Eclipse Temurin Alpine

## Endpoints

| Endpoint | Metod | Beskrivning |
|----------|-------|-------------|
| `/` | GET | Hello World meddelande |
| `/health` | GET | Health check endpoint |

## Service Information

- **Service Type**: NodePort
- **NodePort**: 30080
- **Target Port**: 8080
- **Replicas**: 2

## Stoppa Minikube

```bash
minikube stop
```

## Radera Minikube cluster

```bash
minikube delete
```
