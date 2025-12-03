from scenarios.base import Scenario
from fixtures.accounts import AccountFixture
from utils.steps import KafkaOutageStep, SendTransactionsStep, WaitTransactionsStep
from config import config


class KafkaOutageScenario(Scenario):
    def run(self) -> bool:
        with AccountFixture(...):
            self.log_step(1, "Accounts ready ✅")
            
            # Step 1: Scale down
            outage = KafkaOutageStep("default", "kafka", log_fn=lambda m: self.log_step(2, m))
            if not outage.scale_down():
                return False
            
            # Step 2: Send transactions (while Kafka is down)
            send = SendTransactionsStep(self.client_a, count=10, log_fn=lambda m: self.log_step(3, m))
            if not send.execute(config.account_number, config.bankgood_number_b):
                return False
            
            # Step 3: Scale up
            if not outage.scale_up():
                return False
            
            # Step 4: Wait for transactions
            wait = WaitTransactionsStep(self.client_a, log_fn=lambda m: self.log_step(4, m))
            return wait.execute(send.tx_ids)