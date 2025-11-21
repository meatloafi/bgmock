# src/config.py
import os
from dataclasses import dataclass
from typing import List
from dotenv import load_dotenv

load_dotenv()

@dataclass
class ServiceConfig:
    """Configuration for a bank service"""
    name: str
    base_url: str
    clearing_number: str
    kafka_topic_prefix: str

class Config:
    """Global configuration"""
    def __init__(self):
        # Kafka Configuration
        self.kafka_bootstrap_servers = os.getenv("KAFKA_BOOTSTRAP_SERVERS", "localhost:9092")
        self.kafka_group_id = os.getenv("KAFKA_GROUP_ID", "digital-twin-simulator")

        # Service Configurations
        self.bank_a = ServiceConfig(
            name="Bank A",
            base_url=os.getenv("BANK_A_URL", "http://localhost:30081"),
            clearing_number="000001",
            kafka_topic_prefix="bank-a"
        )

        self.bank_b = ServiceConfig(
            name="Bank B",
            base_url=os.getenv("BANK_B_URL", "http://localhost:30082"),
            clearing_number="000002",
            kafka_topic_prefix="bank-b"
        )

        self.clearing_service_url = os.getenv("CLEARING_SERVICE_URL", "http://localhost:30083")

        # Kafka Topics - actual implementation uses these 4 topics for transaction flow:
        # transactions.initiated → Bank A sends to Clearing
        # transactions.forwarded → Clearing forwards to Bank B
        # transactions.processed → Bank B responds to Clearing
        # transactions.completed → Clearing responds to Bank A
        self.topics = {
            "transactions_initiated": "transactions.initiated",      # Bank A → Clearing
            "transactions_forwarded": "transactions.forwarded",      # Clearing → Bank B
            "transactions_processed": "transactions.processed",      # Bank B → Clearing
            "transactions_completed": "transactions.completed"       # Clearing → Bank A
        }

        # Database connections (for direct state verification)
        self.db_bank_a = os.getenv("DB_BANK_A", "postgresql://bank_a_user:password@localhost:5432/bank_a_db")
        self.db_bank_b = os.getenv("DB_BANK_B", "postgresql://bank_b_user:password@localhost:5432/bank_b_db")
        self.db_switch = os.getenv("DB_SWITCH", "postgresql://switch_user:password@localhost:5432/switch_db")

config = Config()