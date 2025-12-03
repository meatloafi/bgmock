# scenarios/__init__.py
from scenarios.kafka_outage_scenario import KafkaOutageScenario
from simple_simulator.scenarios.interbank_transaction import InterbankTransactionScenario

SCENARIOS = [
    InterbankTransactionScenario,
    KafkaOutageScenario
]

__all__ = ["SCENARIOS"]