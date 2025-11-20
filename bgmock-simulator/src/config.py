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

        # Kafka Topics
        self.topics = {
            "payment_requests": "payment.requests",
            "payment_prepare": "payment.prepare",
            "payment_commit": "payment.commit",
            "payment_rollback": "payment.rollback",
            "transactions_outgoing": "transactions.outgoing",
            "transactions_response": "transactions.response",
            "transactions_incoming_a": "transactions.incoming.bank-a",
            "transactions_incoming_b": "transactions.incoming.bank-b"
        }

        # Database connections (for direct state verification)
        self.db_bank_a = os.getenv("DB_BANK_A", "postgresql://bank_a_user:password@localhost:5432/bank_a_db")
        self.db_bank_b = os.getenv("DB_BANK_B", "postgresql://bank_b_user:password@localhost:5432/bank_b_db")
        self.db_switch = os.getenv("DB_SWITCH", "postgresql://switch_user:password@localhost:5432/switch_db")

config = Config()