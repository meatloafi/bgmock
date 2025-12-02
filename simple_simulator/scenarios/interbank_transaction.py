from decimal import Decimal
from scenarios.base import Scenario
from fixtures.accounts import AccountFixture
from config import config

class InterbankTransactionScenario(Scenario):
    """
    Create accounts on both banks using AccountFixture
    Create a transaction from bank A to bank B
    Wait for transaction to finish
    """

    def run(self) -> bool:
        # Use the fixture as a context manager
        with AccountFixture(
            client_a=self.client_a,
            client_b=self.client_b,
            clearing=self.clearing,
            config=config,
            logger=lambda msg: self.log_step(0, msg)  # correct logger
        ):
            self.log_step(1, "Accounts are ready for the test scenario ✅")

            self.log_step(2, "Creating transaction from bank A to bank B")
            data, status_code = self.client_a.create_transaction(
                from_account=config.account_number,
                to_bank=config.bankgood_number_b,
                amount=Decimal("1000")
            )

            if status_code != 202:
                self.log_error(f"Failed to create transaction: {data}")
                return False

            transaction_id = data.get("transactionId")
            if not transaction_id:
                self.log_error(f"No transaction ID returned: {data}")
                return False

            self.log_step(3, f"Transaction created with ID {transaction_id}, waiting for completion...")

            # Wait for transaction to finish
            final_data, final_status = self.client_a.wait_for_transaction(transaction_id, timeout=30)
            if final_status != 200:
                self.log_error(f"Error while waiting for transaction: {final_data}")
                return False

            status = final_data.get("status")
            self.log_step(4, f"Transaction finished with status: {status}")

            if status != "SUCCESS":
                self.log_error(f"Transaction did not complete successfully: {final_data}")
                return False

            self.log_step(5, "Transaction completed successfully ✅")
            return True
