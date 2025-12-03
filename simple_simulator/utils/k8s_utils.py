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
    
    def scale_kafkanodepool(self, name, namespace, replicas):
        self.custom_api.patch_namespaced_custom_object(
            group="kafka.strimzi.io",
            version="v1beta2",
            namespace=namespace,
            plural="kafkanodepools",
            name=name,
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