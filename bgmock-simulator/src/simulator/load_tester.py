"""
Load Testing Module for BGMock
Provides performance testing and load generation capabilities
"""

import time
import asyncio
import random
import logging
from dataclasses import dataclass, field
from datetime import datetime, timedelta
from typing import Dict, List, Optional, Tuple
from statistics import mean, median, stdev
from enum import Enum
import json

logger = logging.getLogger(__name__)


class LoadProfile(Enum):
    """Different load profiles for testing"""
    CONSTANT = "constant"  # Constant TPS
    RAMP = "ramp"  # Gradually increase TPS
    SPIKE = "spike"  # Sudden load increase
    WAVE = "wave"  # Wave pattern


@dataclass
class TransactionMetrics:
    """Metrics for a single transaction"""
    transaction_id: str
    start_time: datetime
    end_time: datetime
    status: str  # success, failed, timeout
    latency_ms: float
    from_account: str
    to_account: str
    amount: float
    error_message: Optional[str] = None


@dataclass
class PerformanceStats:
    """Overall performance statistics"""
    total_transactions: int = 0
    successful_transactions: int = 0
    failed_transactions: int = 0
    total_duration_seconds: float = 0.0
    latencies: List[float] = field(default_factory=list)

    def calculate(self):
        """Calculate derived statistics"""
        if not self.latencies:
            return

        self.avg_latency = mean(self.latencies)
        self.median_latency = median(self.latencies)
        self.min_latency = min(self.latencies)
        self.max_latency = max(self.latencies)

        if len(self.latencies) > 1:
            self.stddev_latency = stdev(self.latencies)
        else:
            self.stddev_latency = 0

        # Calculate percentiles
        sorted_latencies = sorted(self.latencies)
        self.p50_latency = sorted_latencies[int(len(sorted_latencies) * 0.50)]
        self.p95_latency = sorted_latencies[int(len(sorted_latencies) * 0.95)]
        self.p99_latency = sorted_latencies[int(len(sorted_latencies) * 0.99)]

    def get_throughput(self) -> float:
        """Get throughput in transactions per second"""
        if self.total_duration_seconds == 0:
            return 0
        return self.total_transactions / self.total_duration_seconds

    def get_success_rate(self) -> float:
        """Get success rate as percentage"""
        if self.total_transactions == 0:
            return 0
        return (self.successful_transactions / self.total_transactions) * 100


class LoadTester:
    """
    Generate load for performance testing and monitoring
    """

    def __init__(self, bank_a_url: str, bank_b_url: str, clearing_url: str):
        """
        Initialize the Load Tester

        Args:
            bank_a_url: URL of Bank A service
            bank_b_url: URL of Bank B service
            clearing_url: URL of Clearing service
        """
        self.bank_a_url = bank_a_url
        self.bank_b_url = bank_b_url
        self.clearing_url = clearing_url

        self.is_running = False
        self.transactions: List[TransactionMetrics] = []
        self.stats = PerformanceStats()
        self.start_time: Optional[datetime] = None

    def generate_load(
        self,
        tps: int = 10,
        duration_seconds: int = 60,
        profile: LoadProfile = LoadProfile.CONSTANT,
        account_pairs: Optional[List[Tuple[str, str]]] = None
    ) -> Dict:
        """
        Generate transactions at specified rate

        Args:
            tps: Target transactions per second
            duration_seconds: Duration of load test
            profile: Load profile (constant, ramp, spike, wave)
            account_pairs: List of (from_account, to_account) tuples

        Returns:
            Dictionary with test results
        """
        logger.info(f"Starting load test: {tps} TPS for {duration_seconds}s ({profile.value} profile)")

        self.is_running = True
        self.transactions = []
        self.stats = PerformanceStats()
        self.start_time = datetime.now()

        start_time = time.time()
        transaction_count = 0
        last_transaction_time = start_time

        default_pairs = [
            ("ACC001", "ACC002"),  # Bank A to Bank B
            ("ACC002", "ACC001"),  # Bank B to Bank A
            ("ACC003", "ACC004"),
            ("ACC005", "ACC006"),
        ]

        account_pairs = account_pairs or default_pairs

        try:
            while time.time() - start_time < duration_seconds and self.is_running:
                current_tps = self._get_current_tps(
                    profile,
                    tps,
                    time.time() - start_time,
                    duration_seconds
                )

                current_time = time.time()
                time_since_last = current_time - last_transaction_time

                # Calculate target interval between transactions
                target_interval = 1.0 / current_tps if current_tps > 0 else 0.1

                if time_since_last >= target_interval:
                    from_account, to_account = random.choice(account_pairs)

                    # Simulate transaction
                    tx_metrics = self._simulate_transaction(
                        from_account,
                        to_account,
                        random.uniform(100, 10000)
                    )

                    self.transactions.append(tx_metrics)
                    transaction_count += 1
                    last_transaction_time = current_time

                    # Brief sleep to prevent CPU spinning
                    if time_since_last < target_interval:
                        time.sleep(0.001)
                else:
                    time.sleep(0.001)

        except Exception as e:
            logger.error(f"Error during load test: {e}")
        finally:
            self.is_running = False
            self._finalize_test()

        return self._get_test_results()

    def _get_current_tps(
        self,
        profile: LoadProfile,
        base_tps: int,
        elapsed_seconds: float,
        duration_seconds: int
    ) -> float:
        """Calculate TPS based on load profile"""
        progress = elapsed_seconds / duration_seconds if duration_seconds > 0 else 0

        if profile == LoadProfile.CONSTANT:
            return base_tps

        elif profile == LoadProfile.RAMP:
            # Ramp from 0 to base_tps
            return base_tps * progress

        elif profile == LoadProfile.SPIKE:
            # Normal load, then spike at 50% duration
            if progress < 0.5:
                return base_tps
            else:
                return base_tps * 3

        elif profile == LoadProfile.WAVE:
            # Wave pattern: oscillate between base_tps and base_tps*2
            wave_period = duration_seconds / 3
            wave_progress = (elapsed_seconds % wave_period) / wave_period
            return base_tps + (base_tps * wave_progress)

        return base_tps

    def _simulate_transaction(
        self,
        from_account: str,
        to_account: str,
        amount: float
    ) -> TransactionMetrics:
        """Simulate a transaction and measure latency"""
        tx_id = f"TX{int(time.time()*1000)}"
        start = time.time()

        # Simulate transaction with random latency (50-500ms)
        latency = random.uniform(0.05, 0.5)
        time.sleep(latency)

        end = time.time()
        latency_ms = (end - start) * 1000

        # Simulate occasional failures (5% failure rate)
        status = "failed" if random.random() < 0.05 else "success"

        metrics = TransactionMetrics(
            transaction_id=tx_id,
            start_time=datetime.now(),
            end_time=datetime.now(),
            status=status,
            latency_ms=latency_ms,
            from_account=from_account,
            to_account=to_account,
            amount=amount,
            error_message=None if status == "success" else "Simulated failure"
        )

        return metrics

    def _finalize_test(self):
        """Finalize test and calculate statistics"""
        if not self.transactions:
            return

        total_duration = (self.transactions[-1].end_time - self.transactions[0].start_time).total_seconds()

        self.stats.total_transactions = len(self.transactions)
        self.stats.successful_transactions = sum(1 for t in self.transactions if t.status == "success")
        self.stats.failed_transactions = sum(1 for t in self.transactions if t.status == "failed")
        self.stats.total_duration_seconds = total_duration
        self.stats.latencies = [t.latency_ms for t in self.transactions]

        self.stats.calculate()

    def _get_test_results(self) -> Dict:
        """Get comprehensive test results"""
        return {
            "test_duration_seconds": self.stats.total_duration_seconds,
            "total_transactions": self.stats.total_transactions,
            "successful_transactions": self.stats.successful_transactions,
            "failed_transactions": self.stats.failed_transactions,
            "success_rate_percent": self.stats.get_success_rate(),
            "throughput_tps": self.stats.get_throughput(),
            "latency": {
                "avg_ms": getattr(self.stats, 'avg_latency', 0),
                "median_ms": getattr(self.stats, 'median_latency', 0),
                "min_ms": getattr(self.stats, 'min_latency', 0),
                "max_ms": getattr(self.stats, 'max_latency', 0),
                "p50_ms": getattr(self.stats, 'p50_latency', 0),
                "p95_ms": getattr(self.stats, 'p95_latency', 0),
                "p99_ms": getattr(self.stats, 'p99_latency', 0),
                "stddev_ms": getattr(self.stats, 'stddev_latency', 0),
            }
        }

    def measure_latency(self) -> Dict:
        """Measure end-to-end latency with multiple samples"""
        logger.info("Measuring latency with 100 sample transactions")

        latencies = []
        for _ in range(100):
            tx = self._simulate_transaction("ACC001", "ACC002", 1000)
            latencies.append(tx.latency_ms)

        sorted_latencies = sorted(latencies)

        return {
            "samples": len(latencies),
            "avg_ms": mean(latencies),
            "median_ms": median(latencies),
            "min_ms": min(latencies),
            "max_ms": max(latencies),
            "p50_ms": sorted_latencies[int(len(sorted_latencies) * 0.50)],
            "p95_ms": sorted_latencies[int(len(sorted_latencies) * 0.95)],
            "p99_ms": sorted_latencies[int(len(sorted_latencies) * 0.99)],
        }

    def calculate_throughput(self) -> Dict:
        """Calculate system throughput from recent transactions"""
        if not self.transactions:
            return {
                "throughput_tps": 0,
                "transactions_measured": 0,
                "duration_seconds": 0
            }

        duration = (self.transactions[-1].end_time - self.transactions[0].start_time).total_seconds()
        throughput = len(self.transactions) / duration if duration > 0 else 0

        return {
            "throughput_tps": throughput,
            "transactions_measured": len(self.transactions),
            "duration_seconds": duration,
            "successful_transactions": sum(1 for t in self.transactions if t.status == "success"),
            "failed_transactions": sum(1 for t in self.transactions if t.status == "failed"),
        }

    def get_transaction_history(self, limit: int = 50) -> List[Dict]:
        """Get recent transaction history"""
        recent = self.transactions[-limit:]
        return [
            {
                "transaction_id": t.transaction_id,
                "from_account": t.from_account,
                "to_account": t.to_account,
                "amount": t.amount,
                "status": t.status,
                "latency_ms": round(t.latency_ms, 2),
                "timestamp": t.start_time.isoformat(),
                "error": t.error_message
            }
            for t in recent
        ]

    def stop(self):
        """Stop the current load test"""
        self.is_running = False
        logger.info("Load test stopped")

    def get_status(self) -> Dict:
        """Get current status"""
        return {
            "is_running": self.is_running,
            "transactions_generated": len(self.transactions),
            "start_time": self.start_time.isoformat() if self.start_time else None,
            "current_stats": self._get_test_results()
        }


# Global instance
_load_tester = None


def get_load_tester(
    bank_a_url: str = "http://localhost:8081",
    bank_b_url: str = "http://localhost:8082",
    clearing_url: str = "http://localhost:8083"
) -> LoadTester:
    """Get or create the global load tester instance"""
    global _load_tester
    if _load_tester is None:
        _load_tester = LoadTester(bank_a_url, bank_b_url, clearing_url)
    return _load_tester
