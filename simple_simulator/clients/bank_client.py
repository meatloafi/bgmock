import requests
import time
import uuid
from decimal import Decimal

class BankTestClient:
    def __init__(self, base_url):
        self.base_url = base_url.rstrip("/")

    # ===================== TRANSACTIONS =====================
    def create_transaction(self, from_account: str, to_bank: str, amount: Decimal):
        payload = {
            "fromAccountNumber": from_account,
            "toBankgoodNumber": to_bank,
            "amount": str(amount)  # send as string like your API expects
        }
        response = requests.post(f"{self.base_url}/bank/transaction/outgoing", json=payload, timeout=15)
        return response.json(), response.status_code

    def get_transaction_status(self, transaction_id: str):
        response = requests.get(f"{self.base_url}/bank/transaction/outgoing/{transaction_id}", timeout=15)
        return response.json(), response.status_code


    # ===================== ACCOUNT  =====================
    # Example placeholder
    def create_account(self, account_number: str, account_holder: str, initial_balance: Decimal):
        payload = {
            "accountNumber": account_number,
            "accountHolder": account_holder,
            "balance": str(initial_balance)
        }
        response = requests.post(f"{self.base_url}/bank/account", json=payload, timeout=15)
        return response.json(), response.status_code
    


    def delete_account(self, account_number: str):

        response = requests.delete(f"{self.base_url}/bank/account/{account_number}",timeout=15)

        return {}, response.status_code

    def wait_for_transaction(self, transaction_id: str, timeout: int = 30, poll_interval: float = 1.0):
        """
        Poll the transaction status until it's no longer PENDING, or timeout is reached.
        Returns the final transaction info and status code.
        """
        start = time.time()
        while True:
            data, status = self.get_transaction_status(transaction_id)
            if status != 200:
                return data, status  # API returned error
            if data.get("status") != "PENDING":
                return data, status
            if (time.time() - start) > timeout:
                return {"error": "Timeout waiting for transaction"}, 408
            time.sleep(poll_interval)