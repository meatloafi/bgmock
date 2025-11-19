# src/simulator/state_manager.py
import threading
from typing import Dict, List, Optional
from datetime import datetime
from decimal import Decimal
import json
from collections import deque

from ..models.transaction import TransactionEvent, TransactionResponseEvent, TransactionStatus
from ..models.account import Account

class StateManager:
    """Manages the digital twin state - in-memory mirror of the system"""
    
    def __init__(self):
        self.lock = threading.RLock()
        
        # State stores
        self.accounts: Dict[str, Account] = {}  # account_id -> Account
        self.transactions: Dict[str, TransactionEvent] = {}  # transaction_id -> TransactionEvent
        self.transaction_responses: Dict[str, TransactionResponseEvent] = {}
        
        # Event streams (for UI visualization)
        self.kafka_events = deque(maxlen=100)  # Last 100 Kafka events
        self.rest_calls = deque(maxlen=50)  # Last 50 REST API calls
        
        # Statistics
        self.stats = {
            "total_transactions": 0,
            "successful_transactions": 0,
            "failed_transactions": 0,
            "pending_transactions": 0,
            "total_amount_transferred": Decimal("0.00"),
            "kafka_messages_sent": 0,
            "kafka_messages_received": 0,
            "rest_calls_made": 0
        }
        
        # System health
        self.service_health = {
            "bank_a": False,
            "bank_b": False,
            "clearing_service": False,
            "kafka": False
        }
    
    def update_account(self, account: Account):
        """Update account state"""
        with self.lock:
            self.accounts[account.account_id] = account
    
    def get_account(self, account_id: str) -> Optional[Account]:
        """Get account by ID"""
        with self.lock:
            return self.accounts.get(account_id)
    
    def get_all_accounts(self) -> List[Account]:
        """Get all accounts"""
        with self.lock:
            return list(self.accounts.values())
    
    def add_transaction(self, transaction: TransactionEvent):
        """Add a new transaction"""
        with self.lock:
            self.transactions[transaction.transaction_id] = transaction
            self.stats["total_transactions"] += 1
            self.stats["pending_transactions"] += 1
    
    def update_transaction_status(self, transaction_id: str, status: TransactionStatus, message: str = ""):
        """Update transaction status"""
        with self.lock:
            if transaction_id in self.transactions:
                transaction = self.transactions[transaction_id]
                old_status = transaction.status
                transaction.status = status
                transaction.updated_at = datetime.now()
                
                # Update statistics
                if old_status == TransactionStatus.PENDING:
                    self.stats["pending_transactions"] -= 1
                
                if status == TransactionStatus.SUCCESS:
                    self.stats["successful_transactions"] += 1
                    self.stats["total_amount_transferred"] += transaction.amount
                elif status == TransactionStatus.FAILED:
                    self.stats["failed_transactions"] += 1
                
                # Add response
                self.transaction_responses[transaction_id] = TransactionResponseEvent(
                    transaction_id=transaction_id,
                    status=status,
                    message=message
                )
    
    def add_kafka_event(self, topic: str, event_type: str, data: dict):
        """Add Kafka event to stream"""
        with self.lock:
            self.kafka_events.append({
                "timestamp": datetime.now().isoformat(),
                "topic": topic,
                "type": event_type,
                "data": data
            })
            
            if "send" in event_type.lower():
                self.stats["kafka_messages_sent"] += 1
            else:
                self.stats["kafka_messages_received"] += 1
    
    def add_rest_call(self, method: str, endpoint: str, status_code: int, response_time: float):
        """Add REST API call to history"""
        with self.lock:
            self.rest_calls.append({
                "timestamp": datetime.now().isoformat(),
                "method": method,
                "endpoint": endpoint,
                "status_code": status_code,
                "response_time": response_time
            })
            self.stats["rest_calls_made"] += 1
    
    def update_service_health(self, service: str, is_healthy: bool):
        """Update service health status"""
        with self.lock:
            self.service_health[service] = is_healthy
    
    def get_statistics(self) -> dict:
        """Get current statistics"""
        with self.lock:
            return self.stats.copy()
    
    def get_kafka_events(self, limit: int = 20) -> List[dict]:
        """Get recent Kafka events"""
        with self.lock:
            events = list(self.kafka_events)
            return events[-limit:] if len(events) > limit else events
    
    def get_rest_calls(self, limit: int = 20) -> List[dict]:
        """Get recent REST calls"""
        with self.lock:
            calls = list(self.rest_calls)
            return calls[-limit:] if len(calls) > limit else calls
    
    def export_state(self) -> dict:
        """Export entire state as JSON"""
        with self.lock:
            return {
                "timestamp": datetime.now().isoformat(),
                "accounts": {id: acc.__dict__ for id, acc in self.accounts.items()},
                "transactions": {id: tx.__dict__ for id, tx in self.transactions.items()},
                "statistics": self.get_statistics(),
                "service_health": self.service_health.copy()
            }