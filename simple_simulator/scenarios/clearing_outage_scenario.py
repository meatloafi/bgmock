from decimal import Decimal
from scenarios.base import Scenario
from fixtures.accounts import AccountFixture
from config import config
from utils.steps import DeploymentScaleStep, SendTransactionsStep, WaitTransactionsStep


class ClearingOutageScenario(Scenario):
    """
    Scale down clearing pod
    Send 10 transactions
    Scale up clearing pod
    Wait for transactions to finish
    """
    
    def run(self) -> bool: 
          # Step 0: Prepare accounts
        with AccountFixture(
            client_a=self.client_a,
            client_b=self.client_b,
            clearing=self.clearing,
            config=config,
            logger=lambda msg: self.log_step(0, msg)
        ):
            self.log_step(1, "Accounts ready ✅")
            
            # Step 1: Scale down clearing deployment
            outage = DeploymentScaleStep(
                deployment_name="bankgood-clearing",
                namespace="default",
                log_fn=lambda msg: self.log_step(2, msg)
            )
            if not outage.scale_down():
                return False
            
            # Step 2: Send transactions
            send = SendTransactionsStep(
                client=self.client_a,
                count=10,
                amount=Decimal("100"),
                log_fn=lambda msg: self.log_step(2, msg)
            )
            if not send.execute(from_account=config.account_number, to_bank=config.bankgood_number_b):
                self.log_error("Some transactions failed to be created ❌")
                return False
            
            # Step 3: Scale up clearing deployment
            if not outage.scale_up():
                return False
            
            # Step 4: Wait for transactions to complete 
            wait = WaitTransactionsStep(
                client=self.client_a,
                timeout=30,
                log_fn=lambda msg: self.log_step(3, msg)
            )
            if not wait.execute(send.tx_ids):
                self.log_error("Some transactions did not complete successfully ❌")
                return False
            
            return True

