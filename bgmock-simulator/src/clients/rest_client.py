# src/clients/rest_client.py
import requests
from typing import Optional, List, Dict, Any
from decimal import Decimal
import logging

from models.transaction import TransactionEvent, TransactionResponseEvent, Account
from config import ServiceConfig

logger = logging.getLogger(__name__)

class BankServiceClient:
    """REST client for bank-service endpoints"""
    
    def __init__(self, service_config: ServiceConfig):
        self.config = service_config
        self.base_url = service_config.base_url
        self.session = requests.Session()
        self.session.headers.update({"Content-Type": "application/json"})
    
    # Account Management
    def create_account(self, account: Account) -> Optional[Account]:
        """Create a new account"""
        try:
            response = self.session.post(
                f"{self.base_url}/api/accounts",
                json=account.to_json()
            )
            response.raise_for_status()
            data = response.json()
            return Account(
                account_id=data.get("accountId"),
                account_number=data.get("accountNumber"),
                account_holder=data.get("accountHolder"),
                balance=Decimal(str(data.get("balance", 0)))
            )
        except Exception as e:
            logger.error(f"Failed to create account: {e}")
            return None
    
    def get_account(self, account_id: str) -> Optional[Account]:
        """Get account by ID"""
        try:
            response = self.session.get(f"{self.base_url}/api/accounts/{account_id}")
            response.raise_for_status()
            data = response.json()
            return Account(
                account_id=data.get("accountId"),
                account_number=data.get("accountNumber"),
                account_holder=data.get("accountHolder"),
                balance=Decimal(str(data.get("balance", 0)))
            )
        except Exception as e:
            logger.error(f"Failed to get account {account_id}: {e}")
            return None
    
    def get_all_accounts(self) -> List[Account]:
        """Get all accounts"""
        try:
            response = self.session.get(f"{self.base_url}/api/accounts")
            response.raise_for_status()
            accounts = []
            for data in response.json():
                accounts.append(Account(
                    account_id=data.get("accountId"),
                    account_number=data.get("accountNumber"),
                    account_holder=data.get("accountHolder"),
                    balance=Decimal(str(data.get("balance", 0)))
                ))
            return accounts
        except Exception as e:
            logger.error(f"Failed to get accounts: {e}")
            return []
    
    def adjust_balance(self, account_id: str, amount: Decimal) -> bool:
        """Adjust account balance"""
        try:
            response = self.session.post(
                f"{self.base_url}/api/accounts/{account_id}/adjust",
                params={"amount": str(amount)}
            )
            response.raise_for_status()
            return True
        except Exception as e:
            logger.error(f"Failed to adjust balance for {account_id}: {e}")
            return False
    
    # Transaction Management
    def create_transaction(self, transaction: TransactionEvent) -> Optional[TransactionEvent]:
        """Create a new transaction"""
        try:
            response = self.session.post(
                f"{self.base_url}/api/transactions",
                data=transaction.to_json()
            )
            response.raise_for_status()
            return TransactionEvent.from_json(response.json())
        except Exception as e:
            logger.error(f"Failed to create transaction: {e}")
            return None
    
    def get_transactions(self) -> List[Dict[str, Any]]:
        """Get all transactions"""
        try:
            response = self.session.get(f"{self.base_url}/api/transactions")
            response.raise_for_status()
            return response.json()
        except Exception as e:
            logger.error(f"Failed to get transactions: {e}")
            return []
    
    def health_check(self) -> bool:
        """Check if service is healthy"""
        try:
            response = self.session.get(f"{self.base_url}/health")
            return response.status_code == 200
        except:
            return False

class ClearingServiceClient:
    """REST client for clearing-service endpoints"""
    
    def __init__(self, base_url: str):
        self.base_url = base_url
        self.session = requests.Session()
        self.session.headers.update({"Content-Type": "application/json"})
    
    def initiate_transfer(self, transaction: TransactionEvent) -> bool:
        """Initiate a transfer through clearing service"""
        try:
            response = self.session.post(
                f"{self.base_url}/api/clearing/transfer",
                data=transaction.to_json()
            )
            response.raise_for_status()
            return True
        except Exception as e:
            logger.error(f"Failed to initiate transfer: {e}")
            return False
    
    def health_check(self) -> bool:
        """Check if service is healthy"""
        try:
            response = self.session.get(f"{self.base_url}/health")
            return response.status_code == 200
        except:
            return False