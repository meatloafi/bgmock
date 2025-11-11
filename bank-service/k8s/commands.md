# Kubernetes Commands - Quick Reference

## Essential kubectl Commands


### Generate password for postgres 

```bash
$ PASSWORD=$(openssl rand -base64 32)
```

### Update docker image and push

```bash
# Make code changes → Build → Push → Restart
docker build -t bank-a:latest .
docker tag bank-a:latest localhost:5000/bank-a:latest
docker push localhost:5000/bank-a:latest
kubectl rollout restart deployment/bankgood-bank
kubectl logs -f deployment/bankgood-bank
```

--- 

### Cluster Info
```bash
# Check cluster status
kubectl cluster-info

# Get nodes
kubectl get nodes

# Get namespaces
kubectl get namespaces
```

---

## Deployments

### View Deployments
```bash
# List all deployments
kubectl get deployments

# List deployments in specific namespace
kubectl get deployments -n kafka

# Detailed info about a deployment
kubectl describe deployment bankgood-bank

# Watch deployments in real-time
kubectl get deployments --watch
```

### Deploy/Apply
```bash
# Deploy from YAML file
kubectl apply -f k8s/02-banks/bank-a/deployment.yaml

# Deploy entire folder
kubectl apply -f k8s/02-banks/

# Deploy multiple files
kubectl apply -f k8s/01-shared/ -f k8s/02-banks/
```

### Update Deployment
```bash
# Edit deployment live (opens editor)
kubectl edit deployment bankgood-bank

# Change image directly
kubectl set image deployment/bankgood-bank \
  bank-service=localhost:5000/bank-a:v2

# Rollout restart (restart all pods)
kubectl rollout restart deployment/bankgood-bank
```

### Delete Deployment
```bash
# Delete specific deployment
kubectl delete deployment bankgood-bank

# Delete from file
kubectl delete -f k8s/02-banks/bank-a/deployment.yaml

# Delete everything in folder
kubectl delete -f k8s/02-banks/
```

---

## Pods

### View Pods
```bash
# List all pods
kubectl get pods

# List pods in namespace
kubectl get pods -n kafka

# Get pod with more details
kubectl get pods -o wide

# Describe pod
kubectl describe pod bankgood-bank-xxxxx
```

### Pod Logs
```bash
# View logs
kubectl logs pod-name

# Follow logs (like tail -f)
kubectl logs -f pod-name

# Last 100 lines
kubectl logs pod-name --tail=100

# Logs from specific container in pod
kubectl logs pod-name -c container-name

# Logs from all pods in deployment
kubectl logs deployment/bankgood-bank
```

### Delete Pods
```bash
# Delete specific pod (will restart automatically)
kubectl delete pod bankgood-bank-xxxxx

# Delete all pods for deployment (triggers restart)
kubectl delete pod -l app=bankgood-bank
```

### Access Pod
```bash
# Shell into pod
kubectl exec -it pod-name -- /bin/bash

# Run command in pod
kubectl exec pod-name -- command-here

# Port forward to pod
kubectl port-forward pod-name 8080:8080
```

---

## Services

### View Services
```bash
# List services
kubectl get svc

# Get service details
kubectl describe svc bankgood-bank

# Get service with external IP
kubectl get svc -o wide
```

### Port Forward
```bash
# Forward local port to service
kubectl port-forward svc/bankgood-bank 8080:8080

# Forward to pod
kubectl port-forward pod/bankgood-bank-xxxxx 8080:8080

# Forward to different local port
kubectl port-forward svc/bankgood-bank 3000:8080
```

---

## Updating Images (Workflow)

### Step 1: Build New Image
```bash
# Build locally
docker build -t bank-a:v2 .
```

### Step 2: Push to Registry
```bash
# Tag for minikube registry
docker tag bank-a:v2 localhost:5000/bank-a:v2

# Push
docker push localhost:5000/bank-a:v2
```

### Step 3: Update Deployment
```bash
# Option A: Change image
kubectl set image deployment/bankgood-bank \
  bank-service=localhost:5000/bank-a:v2

# Option B: Edit deployment
kubectl edit deployment bankgood-bank
# Change image: localhost:5000/bank-a:v2
# Save (Ctrl+S, then exit)
```

### Step 4: Verify Rollout
```bash
# Watch rollout progress
kubectl rollout status deployment/bankgood-bank

# Check history of rollouts
kubectl rollout history deployment/bankgood-bank

# Rollback to previous version
kubectl rollout undo deployment/bankgood-bank
```

---

## ConfigMaps & Secrets

### View
```bash
# List configmaps
kubectl get configmap

# View configmap contents
kubectl describe configmap bank-a-config

# Get as YAML
kubectl get configmap bank-a-config -o yaml
```

### Create/Update
```bash
# Create from file
kubectl create configmap bank-a-config --from-file=application.yml

# Delete and recreate
kubectl delete configmap bank-a-config
kubectl create configmap bank-a-config --from-file=application.yml
```

---

## Namespaces

### Create Namespace
```bash
# Create
kubectl create namespace bank-a

# Apply from file
kubectl apply -f k8s/01-shared/namespace.yaml
```

### Use Namespace
```bash
# Get pods in specific namespace
kubectl get pods -n bank-a

# Set default namespace (avoid -n flag)
kubectl config set-context --current --namespace=bank-a

# Get current namespace
kubectl config view | grep namespace
```

### Delete Namespace
```bash
# Delete entire namespace (deletes all resources in it)
kubectl delete namespace bank-a
```

---

## Debugging

### Check Status
```bash
# Get events (what went wrong?)
kubectl get events --sort-by='.lastTimestamp'

# Describe for detailed error info
kubectl describe pod pod-name

# Check deployment status
kubectl get deployment -o wide
```

### View Logs
```bash
# Current pod logs
kubectl logs pod-name

# Previous pod logs (if pod crashed and restarted)
kubectl logs pod-name --previous

# Follow logs in real-time
kubectl logs -f deployment/bankgood-bank
```

### Test Connectivity
```bash
# Port forward and curl
kubectl port-forward svc/bankgood-bank 8080:8080
curl http://localhost:8080/api/accounts

# Exec into pod and test
kubectl exec -it pod-name -- curl http://another-service:8080
```

---

## Common Workflows

### Deploy Everything
```bash
kubectl apply -f k8s/01-shared/
kubectl apply -f k8s/02-banks/
kubectl apply -f k8s/03-switch/
```

### Update Bank A Code
```bash
# 1. Build new image
docker build -t bank-a:latest .

# 2. Push to registry
docker tag bank-a:latest localhost:5000/bank-a:latest
docker push localhost:5000/bank-a:latest

# 3. Restart deployment (pulls new image)
kubectl rollout restart deployment/bankgood-bank

# 4. Watch rollout
kubectl rollout status deployment/bankgood-bank

# 5. Check logs
kubectl logs -f deployment/bankgood-bank
```

### Delete Everything
```bash
# Delete all deployments
kubectl delete -f k8s/

# Delete specific namespace
kubectl delete namespace bank-a

# Start fresh
kubectl apply -f k8s/
```

### Troubleshoot Crashing Pod
```bash
# 1. Check pod status
kubectl get pods

# 2. Describe pod for error
kubectl describe pod pod-name

# 3. Check logs
kubectl logs pod-name

# 4. Check previous logs (if crashed)
kubectl logs pod-name --previous

# 5. Port forward and test
kubectl port-forward svc/bankgood-bank 8080:8080
curl http://localhost:8080/health
```

---

## Quick Aliases

Add to your `.bashrc` or PowerShell profile:

```bash
alias k=kubectl
alias kgp="kubectl get pods"
alias kgd="kubectl get deployments"
alias kgn="kubectl get namespaces"
alias kdel="kubectl delete"
alias klog="kubectl logs -f"
alias kex="kubectl exec -it"
alias kpf="kubectl port-forward"
```

Then use:
```bash
k get pods
klog deployment/bankgood-bank
kex pod-name -- /bin/bash
```

---

## Helpful One-Liners

```bash
# Get all resources
kubectl get all

# Get all in namespace
kubectl get all -n kafka

# Delete everything (careful!)
kubectl delete all --all

# Watch pods
watch kubectl get pods

# Get resource YAML
kubectl get deployment bankgood-bank -o yaml

# Apply with dry-run (see what would happen)
kubectl apply -f deployment.yaml --dry-run=client
```