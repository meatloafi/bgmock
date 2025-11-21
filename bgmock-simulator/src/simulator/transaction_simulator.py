# src/simulator/transaction_simulator.py
import random
import time
import logging
from decimal import Decimal
from typing import Optional, List
import uuid

from clients.rest_client import BankServiceClient, ClearingServiceClient
from clients.kafka_client import KafkaClient
from models.transaction import TransactionEvent, TransactionStatus, Account
from simulator.transaction_monitor import TransactionMonitor
from config import config

logger = logging.getLogger(__name__)

class TransactionSimulator:
    """Main simulator orchestrating transactions"""

    def __init__(self, state_manager: TransactionMonitor):
        """Initialize the transaction simulator with all required clients and monitoring"""
        self.state_manager = state_manager

        # Initialize all service clients (REST and Kafka)
        # These clients handle communication with bank services and message broker
        self.bank_a_client = BankServiceClient(config.bank_a)
        self.bank_b_client = BankServiceClient(config.bank_b)
        self.clearing_client = ClearingServiceClient(config.clearing_service_url)
        self.kafka_client = KafkaClient()

        # Report Kafka availability to monitoring (graceful degradation if offline)
        self.state_manager.update_service_health("kafka", self.kafka_client.kafka_available)

        # Subscribe to Kafka topics only if broker is available
        # This prevents errors when running in offline mode
        if self.kafka_client.kafka_available:
            self._setup_kafka_subscriptions()

        # Create test accounts in both banks for simulation
        self._initialize_test_accounts()

        # Start background thread to continuously monitor service health
        self._start_health_monitoring()
    
    def _setup_kafka_subscriptions(self):
        """Setup Kafka topic subscriptions to monitor transaction flow"""
        topics_callbacks = {
            # All 4 topics used in the transaction flow
            config.topics["transactions_initiated"]: self._on_transaction_initiated,
            config.topics["transactions_forwarded"]: self._on_transaction_forwarded,
            config.topics["transactions_processed"]: self._on_transaction_processed,
            config.topics["transactions_completed"]: self._on_transaction_completed,
        }

        for topic, callback in topics_callbacks.items():
            self.kafka_client.subscribe(topic, callback)
            logger.info(f"Subscribed to Kafka topic: {topic}")
    
    def _on_transaction_initiated(self, key: str, value: dict):
        """Handle transaction initiated event from Bank A → Clearing"""
        transaction_id = value.get("transactionId")
        logger.info(f"[INITIATED] Transaction {transaction_id} sent from Bank A to Clearing")
        self.state_manager.add_kafka_event(
            topic="transactions.initiated",
            event_type="receive",
            data=value
        )

    def _on_transaction_forwarded(self, key: str, value: dict):
        """Handle transaction forwarded event from Clearing → Bank B"""
        transaction_id = value.get("transactionId")
        logger.info(f"[FORWARDED] Transaction {transaction_id} forwarded from Clearing to Bank B")
        self.state_manager.add_kafka_event(
            topic="transactions.forwarded",
            event_type="receive",
            data=value
        )

    def _on_transaction_processed(self, key: str, value: dict):
        """Handle transaction processed event from Bank B → Clearing"""
        transaction_id = value.get("transactionId")
        status = value.get("status", "UNKNOWN")
        logger.info(f"[PROCESSED] Transaction {transaction_id} processed by Bank B with status {status}")
        self.state_manager.add_kafka_event(
            topic="transactions.processed",
            event_type="receive",
            data=value
        )

    def _on_transaction_completed(self, key: str, value: dict):
        """Handle transaction completed event from Clearing → Bank A"""
        transaction_id = value.get("transactionId")
        status = value.get("status", "UNKNOWN")
        logger.info(f"[COMPLETED] Transaction {transaction_id} completed with status {status}")
        self.state_manager.add_kafka_event(
            topic="transactions.completed",
            event_type="receive",
            data=value
        )

        # Update transaction state with final status
        if transaction_id:
            try:
                final_status = TransactionStatus(status)
                message = value.get("message", "Transaction completed")
                self.state_manager.update_transaction_status(transaction_id, final_status, message)
            except ValueError:
                logger.warning(f"Unknown transaction status: {status}")
    
    def _initialize_test_accounts(self):
        """Create test accounts if they don't exist"""
        test_accounts = [
            Account(
                account_number="1111111111",
                account_holder="Alice Anderson",
                balance=Decimal("10000.00")
            ),
            Account(
                account_number="2222222222",
                account_holder="Bob Brown",
                balance=Decimal("5000.00")
            ),
            Account(
                account_number="3333333333",
                account_holder="Charlie Chen",
                balance=Decimal("7500.00")
            ),
            Account(
                account_number="4444444444",
                account_holder="Diana Davis",
                balance=Decimal("3000.00")
            )
        ]
        
        # Create accounts in Bank A
        for i, account in enumerate(test_accounts[:2]):
            created = self.bank_a_client.create_account(account)
            if created:
                self.state_manager.update_account(created)
                logger.info(f"Created account in Bank A: {created.account_number}")
        
        # Create accounts in Bank B  
        for account in test_accounts[2:]:
            created = self.bank_b_client.create_account(account)
            if created:
                self.state_manager.update_account(created)
                logger.info(f"Created account in Bank B: {created.account_number}")
    
    def _start_health_monitoring(self):
        """Start monitoring service health"""
        import threading
        
        def monitor():
            while True:
                self.state_manager.update_service_health(
                    "bank_a", 
                    self.bank_a_client.health_check()
                )
                self.state_manager.update_service_health(
                    "bank_b",
                    self.bank_b_client.health_check()
                )
                self.state_manager.update_service_health(
                    "clearing_service",
                    self.clearing_client.health_check()
                )
                time.sleep(10)  # Check every 10 seconds
        
        thread = threading.Thread(target=monitor, daemon=True)
        thread.start()
    
    def create_transaction(
        self,
        from_account: str,
        to_bankgood: str,
        amount: Decimal,
        use_rest: bool = True
    ) -> Optional[TransactionEvent]:
        """Create a new transaction and send it to the system"""

        transaction = TransactionEvent(
            transaction_id=str(uuid.uuid4()),
            from_clearing_number=config.bank_a.clearing_number,
            from_account_number=from_account,
            to_bankgood_number=to_bankgood,
            amount=amount,
            status=TransactionStatus.PENDING
        )

        # Add to simulator state tracking
        self.state_manager.add_transaction(transaction)

        if use_rest:
            # Send via REST API (preferred method)
            start = time.time()
            result = self.bank_a_client.create_transaction(transaction)
            elapsed = time.time() - start

            self.state_manager.add_rest_call(
                method="POST",
                endpoint="/api/transactions",
                status_code=200 if result else 500,
                response_time=elapsed
            )

            if result:
                logger.info(f"✓ Transaction created via REST: {transaction.transaction_id}")
                return result
            else:
                logger.warning(f"✗ Failed to create transaction via REST: {transaction.transaction_id}")
        else:
            # Send via Kafka directly (fallback method)
            if self.kafka_client.kafka_available:
                self.kafka_client.send_transaction_event(
                    config.topics["transactions_initiated"],
                    transaction
                )

                self.state_manager.add_kafka_event(
                    topic="transactions.initiated",
                    event_type="send",
                    data=transaction.__dict__
                )

                logger.info(f"✓ Transaction sent via Kafka: {transaction.transaction_id}")
                return transaction
            else:
                logger.warning(f"✗ Kafka not available, cannot send transaction: {transaction.transaction_id}")

        return None
    
    def simulate_random_transaction(self) -> Optional[TransactionEvent]:
        """Generate a random transaction"""
        accounts = self.state_manager.get_all_accounts()
        
        if len(accounts) < 2:
            logger.warning("Not enough accounts for simulation")
            return None
        
        # Random source and destination
        from_account = random.choice(accounts)
        to_account = random.choice([a for a in accounts if a != from_account])
        
        # Random amount (10-500)
        amount = Decimal(str(random.randint(10, 500)))
        
        # Ensure sufficient balance
        if from_account.balance < amount:
            amount = from_account.balance * Decimal("0.1")  # 10% of balance
        
        return self.create_transaction(
            from_account=from_account.account_number,
            to_bankgood=to_account.account_number,  # In real system, this would be bankgood number
            amount=amount,
            use_rest=random.choice([True, False])  # Randomly choose REST or Kafka
        )
    
    def run_simulation(self, num_transactions: int = 10, delay: float = 2.0):
        """Run automated simulation"""
        logger.info(f"Starting simulation with {num_transactions} transactions")
        
        for i in range(num_transactions):
            transaction = self.simulate_random_transaction()
            if transaction:
                logger.info(f"Simulated transaction {i+1}/{num_transactions}: {transaction.transaction_id}")
            
            time.sleep(delay)
        
        logger.info("Simulation completed")