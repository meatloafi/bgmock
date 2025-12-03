# scenarios/__init__.py
from scenarios.interbank_transaction import InterbankTransactionScenario
from scenarios.kafka_outage_scenario import KafkaOutageScenario
SCENARIOS = [
    InterbankTransactionScenario,
    KafkaOutageScenario
]

__all__ = ["SCENARIOS"]