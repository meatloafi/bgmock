from kubernetes import client, config as k8s_config
import time

class K8sManager:
    def __init__(self):
        try:
            k8s_config.load_incluster_config()
        except k8s_config.ConfigException:
            k8s_config.load_kube_config()
        self.v1 = client.CoreV1Api()
        self.custom_api = client.CustomObjectsApi()
        self.apps_api = client.AppsV1Api()
    
    def scale_kafkanodepool(self, name, namespace, replicas):
        self.custom_api.patch_namespaced_custom_object(
            group="kafka.strimzi.io",
            version="v1beta2",
            namespace=namespace,
            plural="kafkanodepools",
            name=name,
            body={"spec": {"replicas": replicas}}
        )
        
    def scale_clearing(self, namespace, replicas):
        """
        Scale the bankgood-clearing deployment to the desired number of replicas.
        """
        self.apps_api.patch_namespaced_deployment(
            name="bankgood-clearing",
            namespace=namespace,
            body={"spec": {"replicas": replicas}}
        )
    
    def wait_for_broker_ready(self, cluster_name, namespace, max_wait=120):
        start = time.time()
        while True:
            try:
                pods = self.v1.list_namespaced_pod(
                    namespace=namespace,
                    label_selector=f"strimzi.io/cluster={cluster_name}"
                )
                if pods.items and all(cs.ready for cs in pods.items[0].status.container_statuses):
                    return True
            except:
                pass
            if time.time() - start > max_wait:
                return False
            time.sleep(3)
            
            
    def scale_deployment(self, name: str, namespace: str, replicas: int):
        """
        Scale a deployment to the desired number of replicas.
        """
        self.apps_api.patch_namespaced_deployment(
            name=name,
            namespace=namespace,
            body={"spec": {"replicas": replicas}}
        )
        
    def wait_for_deployment_ready(self, deployment_name, namespace, max_wait=120):
        """
        Wait until all pods in the given deployment are ready.
        """
        import time

        start = time.time()
        while True:
            try:
                # List pods belonging to the deployment
                pods = self.v1.list_namespaced_pod(
                    namespace=namespace,
                    label_selector=f"app={deployment_name}"
                )
                # Check if all pods are ready
                all_ready = True
                for pod in pods.items:
                    if not pod.status.container_statuses:
                        all_ready = False
                        break
                    if not all(cs.ready for cs in pod.status.container_statuses):
                        all_ready = False
                        break
                if pods.items and all_ready:
                    return True
            except Exception:
                pass

            if time.time() - start > max_wait:
                return False

            time.sleep(3)
            
    def wait_for_deployment_scaled_down(self, deployment_name, namespace, max_wait=120):
        """
        Wait until all pods in the given deployment are terminated (scaled down to 0).
        """
        import time

        start = time.time()
        while True:
            try:
                # List pods belonging to the deployment
                pods = self.v1.list_namespaced_pod(
                    namespace=namespace,
                    label_selector=f"app={deployment_name}"
                )
                # If no pods exist, scaling down is complete
                if not pods.items:
                    return True
            except Exception:
                pass

            if time.time() - start > max_wait:
                return False

            time.sleep(3)

