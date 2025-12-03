# scenarios/base.py
import logging
from abc import ABC, abstractmethod
from clients import BankTestClient
from clients import ClearingTestClient

logger = logging.getLogger(__name__)

class Scenario(ABC):
    """Base class for all test scenarios"""
    
    def __init__(self, bank_a_url: str, bank_b_url: str, clearing_url: str):
        self.client_a = BankTestClient(bank_a_url)
        self.client_b = BankTestClient(bank_b_url)
        self.clearing = ClearingTestClient(clearing_url)
        self.name = self.__class__.__name__
    
    @abstractmethod
    def run(self) -> bool:
        """Run the scenario. Return True if successful."""
        pass
    
    def log_step(self, step_num: int, message: str):
        logger.info(f"[{self.name}] [{step_num}] {message}")
    
    def log_error(self, message: str):
        logger.error(f"[{self.name}] ERROR: {message}")