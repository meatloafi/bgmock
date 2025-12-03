from decimal import Decimal
from utils.k8s_utils import K8sManager
import time

class KafkaOutageStep:
    def __init__(self, nodepool_name, namespace, log_fn=print):
        self.k8s = K8sManager()
        self.nodepool_name = nodepool_name
        self.namespace = namespace
        self.log = log_fn
    
    def scale_down(self):
        """Scale down Kafka broker"""
        self.log("Scaling down Kafka...")
        self.k8s.scale_kafkanodepool(self.nodepool_name, self.namespace, 0)
        time.sleep(5)  # Wait for graceful shutdown
        return True
    
    def scale_up(self):
        """Scale up and wait for Kafka broker to be ready"""
        self.log("Scaling up Kafka...")
        self.k8s.scale_kafkanodepool(self.nodepool_name, self.namespace, 1)
        
        self.log("Waiting for broker ready...")
        if not self.k8s.wait_for_broker_ready("bgmock-kafka", self.namespace):
            return False
        time.sleep(5)  # Stabilization time
        return True

class SendTransactionsStep:
    def __init__(self, client, count=10, amount=Decimal("100"), log_fn=print):
        self.client = client
        self.count = count
        self.amount = amount
        self.log = log_fn
        self.tx_ids = []
    
    def execute(self, from_account, to_bank):
        for i in range(self.count):
            try:
                data, _ = self.client.create_transaction(
                    from_account=from_account,
                    to_bank=to_bank,
                    amount=self.amount
                )
                self.tx_ids.append(data.get("transactionId"))
                self.log(f"Sent transaction {i+1}/{self.count}")
            except Exception as e:
                self.log(f"Failed: {e}")
                self.tx_ids.append(None)
        return all(tx_id for tx_id in self.tx_ids)

class WaitTransactionsStep:
    def __init__(self, client, timeout=180, log_fn=print):
        self.client = client
        self.timeout = timeout
        self.log = log_fn
    
    def execute(self, tx_ids):
        for idx, tx_id in enumerate(tx_ids, 1):
            if not tx_id:
                continue
            data, status = self.client.wait_for_transaction(tx_id, timeout=self.timeout)
            if status != 200 or data.get("status") != "SUCCESS":
                self.log(f"❌ Transaction {idx} failed")
                return False
            self.log(f"✅ Transaction {idx} completed")
        return True

class DeploymentScaleStep:
    def __init__(self, deployment_name: str, namespace: str, log_fn=print):
        self.k8s = K8sManager()
        self.deployment_name = deployment_name
        self.namespace = namespace
        self.log = log_fn

    def scale_down(self):
        self.log(f"Scaling down deployment '{self.deployment_name}'...")
        self.k8s.scale_deployment(self.deployment_name, self.namespace, 0)
        self.log(f"Waiting for deployment '{self.deployment_name}' to fully scale down...")
        if not self.k8s.wait_for_deployment_scaled_down(self.deployment_name, self.namespace):
            return False
        return True

    def scale_up(self, replicas: int = 1):
        self.log(f"Scaling up deployment '{self.deployment_name}' to {replicas} replicas...")
        self.k8s.scale_deployment(self.deployment_name, self.namespace, replicas)

        self.log(f"Waiting for deployment '{self.deployment_name}' ready...")
        if not self.k8s.wait_for_deployment_ready(self.deployment_name, self.namespace):
            return False
        time.sleep(5)
        return True
