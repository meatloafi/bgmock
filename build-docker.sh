#!/bin/bash

# Build Docker images for Minikube
echo "Building Docker images for Minikube..."

cd /mnt/c/Projects/bgmock

# Evaluate minikube docker environment
eval "$(minikube docker-env)"

# Build bank-service image
echo "Building bank-service image..."
docker build -f bank-service/Dockerfile -t bank-service:latest .
if [ $? -ne 0 ]; then
  echo "Failed to build bank-service"
  exit 1
fi

# Build clearing-service image
echo "Building clearing-service image..."
docker build -f clearing-service/Dockerfile -t clearing-service:latest .
if [ $? -ne 0 ]; then
  echo "Failed to build clearing-service"
  exit 1
fi

echo "Docker images built successfully!"
docker images | grep -E "(bank-service|clearing-service)"
