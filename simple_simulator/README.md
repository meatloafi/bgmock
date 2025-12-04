
### Building Docker image
---

From root folder, run: 

```bash
eval $(minikube docker-env)
docker build -t simulator:latest simple_simulator/
```

### Running simulator 
---
The simulator is a Kubernetes Job and needs to be triggered to run. The easiest way is to apply the `simulator.yaml` manifest. 

From root folder, run:  

```bash
kubectl delete job simulator-job # If it exists already
kubectl apply -f k8s/simulator/simulator.yaml

# Check logs 
kubectl logs -f job/simulator-job
```