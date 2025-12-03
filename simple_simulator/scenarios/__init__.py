# scenarios/__init__.py
from scenarios.kafka_outage_scenario import KafkaOutageScenario
from scenarios.interbank_transaction import InterbankTransactionScenario
from scenarios.clearing_outage_scenario import ClearingOutageScenario

SCENARIOS = [
    InterbankTransactionScenario,
    KafkaOutageScenario,
    ClearingOutageScenario
]

__all__ = ["SCENARIOS"]