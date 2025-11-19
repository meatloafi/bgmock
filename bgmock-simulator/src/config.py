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

@dataclass
class Config:
    """Global configuration"""
    # Kafka Configuration
    kafka_bootstrap_servers: str = os.getenv("KAFKA_BOOTSTRAP_SERVERS", "localhost:9092")
    kafka_group_id: str = os.getenv("KAFKA_GROUP_ID", "digital-twin-simulator")
    
    # Service Configurations
    bank_a: ServiceConfig = ServiceConfig(
        name="Bank A",
        base_url=os.getenv("BANK_A_URL", "http://localhost:30081"),
        clearing_number="000001",
        kafka_topic_prefix="bank-a"
    )
    
    bank_b: ServiceConfig = ServiceConfig(
        name="Bank B", 
        base_url=os.getenv("BANK_B_URL", "http://localhost:30082"),
        clearing_number="000002",
        kafka_topic_prefix="bank-b"
    )
    
    clearing_service_url: str = os.getenv("CLEARING_SERVICE_URL", "http://localhost:30083")
    
    # Kafka Topics
    topics = {
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
    db_bank_a: str = os.getenv("DB_BANK_A", "postgresql://bank_a_user:password@localhost:5432/bank_a_db")
    db_bank_b: str = os.getenv("DB_BANK_B", "postgresql://bank_b_user:password@localhost:5432/bank_b_db")
    db_switch: str = os.getenv("DB_SWITCH", "postgresql://switch_user:password@localhost:5432/switch_db")

config = Config()