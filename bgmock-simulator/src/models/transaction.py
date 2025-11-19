# src/models/transaction.py
from dataclasses import dataclass, field
from datetime import datetime
from decimal import Decimal
from enum import Enum
from typing import Optional
import uuid
import json

class TransactionStatus(Enum):
    PENDING = "PENDING"
    SUCCESS = "SUCCESS"
    FAILED = "FAILED"

@dataclass
class TransactionEvent:
    """Matches Java TransactionEvent"""
    transaction_id: str = field(default_factory=lambda: str(uuid.uuid4()))
    from_account_id: Optional[str] = None
    from_clearing_number: str = ""
    from_account_number: str = ""
    to_bankgood_number: str = ""
    to_clearing_number: Optional[str] = None
    to_account_number: Optional[str] = None
    amount: Decimal = Decimal("0.00")
    status: TransactionStatus = TransactionStatus.PENDING
    created_at: datetime = field(default_factory=datetime.now)
    updated_at: datetime = field(default_factory=datetime.now)
    
    def to_json(self) -> str:
        return json.dumps({
            "transactionId": self.transaction_id,
            "fromAccountId": self.from_account_id,
            "fromClearingNumber": self.from_clearing_number,
            "fromAccountNumber": self.from_account_number,
            "toBankgoodNumber": self.to_bankgood_number,
            "toClearingNumber": self.to_clearing_number,
            "toAccountNumber": self.to_account_number,
            "amount": float(self.amount),
            "status": self.status.value,
            "createdAt": self.created_at.isoformat(),
            "updatedAt": self.updated_at.isoformat()
        })
    
    @classmethod
    def from_json(cls, data: dict) -> 'TransactionEvent':
        return cls(
            transaction_id=data.get("transactionId"),
            from_account_id=data.get("fromAccountId"),
            from_clearing_number=data.get("fromClearingNumber", ""),
            from_account_number=data.get("fromAccountNumber", ""),
            to_bankgood_number=data.get("toBankgoodNumber", ""),
            to_clearing_number=data.get("toClearingNumber"),
            to_account_number=data.get("toAccountNumber"),
            amount=Decimal(str(data.get("amount", 0))),
            status=TransactionStatus(data.get("status", "PENDING")),
            created_at=datetime.fromisoformat(data.get("createdAt", datetime.now().isoformat())),
            updated_at=datetime.fromisoformat(data.get("updatedAt", datetime.now().isoformat()))
        )

@dataclass
class TransactionResponseEvent:
    """Matches Java TransactionResponseEvent"""
    transaction_id: str
    status: TransactionStatus
    message: str = ""
    
    def to_json(self) -> str:
        return json.dumps({
            "transactionId": self.transaction_id,
            "status": self.status.value,
            "message": self.message
        })
    
    @classmethod
    def from_json(cls, data: dict) -> 'TransactionResponseEvent':
        return cls(
            transaction_id=data.get("transactionId"),
            status=TransactionStatus(data.get("status")),
            message=data.get("message", "")
        )

# src/models/account.py
@dataclass
class Account:
    """Bank account model"""
    account_id: Optional[str] = None
    account_number: str = ""
    account_holder: str = ""
    balance: Decimal = Decimal("0.00")
    version: Optional[int] = None
    created_at: Optional[datetime] = None
    updated_at: Optional[datetime] = None
    
    def to_json(self) -> dict:
        return {
            "accountId": self.account_id,
            "accountNumber": self.account_number,
            "accountHolder": self.account_holder,
            "balance": float(self.balance)
        }