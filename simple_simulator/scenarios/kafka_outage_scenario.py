from decimal import Decimal
from scenarios.base import Scenario
from fixtures.accounts import AccountFixture
from kubernetes import client, config as k8s_config
from config import config
import time


class KafkaOutageScenario(Scenario):
    """
    Scale down Kafka broker (outage)
    Send 10 requests
    Scale up Kafka broker to get back online 
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
            
            self.log_step(2, "Scaling down Kafka broker via KafkaNodePool...")
            nodepool_name = "default"
            namespace = "kafka"

            try:
                # Load in-cluster config (if running inside pod) or kubeconfig
                try:
                    k8s_config.load_incluster_config()
                except k8s_config.ConfigException:
                    k8s_config.load_kube_config()   

                custom_api = client.CustomObjectsApi()
                
                # Scale down to 0
                custom_api.patch_namespaced_custom_object(
                    group="kafka.strimzi.io",
                    version="v1beta2",
                    namespace=namespace,
                    plural="kafkanodepools",
                    name=nodepool_name,
                    body={"spec": {"replicas": 0}}
                )
                self.log_step(3, f"Kafka KafkaNodePool scaled to 0 replicas")
                time.sleep(10)  # Wait for broker to gracefully shut down
                
            except Exception as e:
                self.log_error(f"Failed to scale down Kafka: {e}")
                return False
            
            
            transaction_ids = []
            for i in range(10):
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
            
            time.sleep(5)
            
            self.log_step(6, "Scaling up Kafka broker via KafkaNodePool...")
            try:
                # Scale back up to 1
                custom_api.patch_namespaced_custom_object(
                    group="kafka.strimzi.io",
                    version="v1beta2",
                    namespace=namespace,
                    plural="kafkanodepools",
                    name=nodepool_name,
                    body={"spec": {"replicas": 1}}
                )
                self.log_step(7, f"Kafka KafkaNodePool scaled to 1 replica")
                
            except Exception as e:
                self.log_error(f"Failed to scale up Kafka: {e}")
                return False
            
            
            self.log_step(8, "Waiting for Kafka broker pod to be ready...")
            max_wait = 120  # seconds
            interval = 3    # seconds
            start_time = time.time()
            v1 = client.CoreV1Api()

            while True:
                try:
                    pods = v1.list_namespaced_pod(
                        namespace=namespace,
                        label_selector="strimzi.io/cluster=bgmock-kafka"
                    )
                    if pods.items:
                        pod = pods.items[0]
                        if pod.status.container_statuses and all(cs.ready for cs in pod.status.container_statuses):
                            self.log_step(9, f"Kafka broker pod {pod.metadata.name} is back online ✅")
                            break
                except Exception:
                    pass

                if time.time() - start_time > max_wait:
                    self.log_error(f"Timeout waiting for Kafka broker pod to become ready")
                    return False

                time.sleep(interval)
            
            # Give Kafka a bit more time to stabilize
            time.sleep(5)
                
            self.log_step(10, "Waiting for transactions to complete...")
            all_success = True
            for idx, tx_id in enumerate(transaction_ids):
                if not tx_id:
                    all_success = False
                    continue
                final_data, final_status = self.client_a.wait_for_transaction(tx_id, timeout=180)
                if final_status != 200 or final_data.get("status") != "SUCCESS":
                    self.log_error(f"Transaction {idx+1} did not complete: {final_data}")
                    all_success = False
                else:
                    self.log_step(11, f"Transaction {idx+1} completed successfully")

            return all_success