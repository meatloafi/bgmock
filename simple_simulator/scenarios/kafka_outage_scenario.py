from decimal import Decimal
from scenarios.base import Scenario
from fixtures.accounts import AccountFixture
from kubernetes import client, config as k8s_config
from config import config
import subprocess
import time


class KafkaOutageScenario(Scenario):
    """
    Kill the Kafka broker
    Send 10 requests
    Wait for Kafka broker to get back online 
    Wait for transactions to finish
    """
    
    
    def run(self) -> bool:
        
        with AccountFixture(
            client_a=self.client_a,
            client_b=self.client_b,
            clearing=self.clearing,
            config=config,
            logger=lambda msg: self.log_step(0, msg) 
        ): 
            self.log_step(1, "Accounts are ready for the test scenario ✅")
            
            self.log_step(2, "Killing Kafka broker pod...")
            pod_name = "bgmock-kafka-default-0"
            namespace = "kafka"

            try:
                # Load in-cluster config (if running inside pod) or kubeconfig
                try:
                    k8s_config.load_incluster_config()
                except k8s_config.ConfigException:
                    k8s_config.load_kube_config()   

                v1 = client.CoreV1Api()
                v1.delete_namespaced_pod(name=pod_name, namespace=namespace)
                self.log_step(3, f"Kafka broker pod {pod_name} deleted")
            except Exception as e:
                self.log_error(f"Failed to delete Kafka broker pod: {e}")
                return False
            
            
            self.log_step(4, "Sending 3 transactions while Kafka broker is down...")
            transaction_ids = []
            for i in range(3):
                try:
                    data, status_code = self.client_a.create_transaction(
                        from_account=config.account_number,
                        to_bank=config.bankgood_number_b,
                        amount=Decimal("100")
                    )
                    tx_id = data.get("transactionId")
                    transaction_ids.append(tx_id)
                    self.log_step(5, f"Sent transaction {i+1}, ID: {tx_id}")
                except Exception as e:
                    self.log_error(f"Transaction {i+1} failed to send: {e}")
                    transaction_ids.append(None)
                    
            self.log_step(5, "Polling Kafka broker pod until its ready...")
            max_wait = 120  # seconds
            interval = 3    # seconds
            start_time = time.time()

            while True:
                try:
                    pod = v1.read_namespaced_pod(name=pod_name, namespace=namespace)
                    if pod.status.container_statuses and pod.status.container_statuses[0].ready:
                        self.log_step(7, f"Kafka broker pod {pod_name} is back online ✅")
                        break
                except client.exceptions.ApiException:
                    # Pod might not exist yet
                    pass

                if time.time() - start_time > max_wait:
                    self.log_error(f"Timeout waiting for Kafka broker pod {pod_name} to become ready")
                    return False

                time.sleep(interval)
                
            self.log_step(8, "Waiting for transactions to complete via Outbox retry mechanism...")
            all_success = True
            for idx, tx_id in enumerate(transaction_ids):
                if not tx_id:
                    all_success = False
                    continue
                final_data, final_status = self.client_a.wait_for_transaction(tx_id, timeout=60)
                if final_status != 200 or final_data.get("status") != "COMPLETED":
                    self.log_error(f"Transaction {idx+1} did not complete: {final_data}")
                    all_success = False
                else:
                    self.log_step(9, f"Transaction {idx+1} completed successfully")

            return all_success
                
        