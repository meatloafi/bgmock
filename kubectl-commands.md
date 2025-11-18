# Kubernetes Quick Reference

## Pod Management

**List all pods**
```bash
kubectl get pods
```

**Get pod details**
```bash
kubectl describe pod <pod-name>
```

**View pod logs**
```bash
kubectl logs <pod-name>
```

**View logs in real-time**
```bash
kubectl logs -f <pod-name>
```

**Previous logs (if pod crashed)**
```bash
kubectl logs <pod-name> -p
```

**Execute command in pod**
```bash
kubectl exec -it <pod-name> -- <command>
```

## Deployments & Rollouts

**List deployments**
```bash
kubectl get deployments
```

**Restart a deployment**
```bash
kubectl rollout restart deployment/<deployment-name>
```

**Check rollout status**
```bash
kubectl rollout status deployment/<deployment-name>
```

**Update image**
```bash
kubectl set image deployment/<deployment-name> <container-name>=<image>:<tag>
```

**Apply/update from YAML**
```bash
kubectl apply -f <file.yaml>
```

## Database Access

**Connect to PostgreSQL pod**
```bash
kubectl exec -it <postgres-pod-name> -- psql -U <user> -d <database>
```

**List tables**
```sql
\dt
```

**Describe table**
```sql
\d <table-name>
```

**Exit psql**
```sql
\q
```

## Useful Queries

**Get all resources**
```bash
kubectl get all
```

**Get StatefulSets**
```bash
kubectl get statefulsets
```

**Get services**
```bash
kubectl get services
```

**Delete a pod (will restart if in deployment)**
```bash
kubectl delete pod <pod-name>
```

## Debugging

**Describe resource (see events)**
```bash
kubectl describe <resource-type> <resource-name>
```

**Port forward to local machine**
```bash
kubectl port-forward <pod-name> 8080:8080
```

## Common Workflows

**Deploy changes after rebuilding Docker image:**
1. `eval $(minikube docker-env)` — Use Minikube's Docker
2. `docker build -t your-app:latest .` — Build image
3. `kubectl rollout restart deployment/your-app` — Restart pods

**Check if app is healthy:**
```bash
kubectl logs <pod-name> | tail -20
kubectl describe pod <pod-name>  # Check Events section
```

**Recover database:**
```bash
kubectl delete pod <db-pod-name>
kubectl logs <app-pod-name>  # Wait for app to reconnect
```