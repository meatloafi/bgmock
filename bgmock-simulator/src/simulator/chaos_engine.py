"""
Chaos Engineering Module for BGMock
Provides failure injection capabilities for testing system resilience
"""

import random
import asyncio
import logging
from dataclasses import dataclass
from datetime import datetime, timedelta
from typing import Optional, Dict, List, Callable
from enum import Enum

logger = logging.getLogger(__name__)


class FailureType(Enum):
    """Types of failures that can be injected"""
    NETWORK_DELAY = "network_delay"
    SERVICE_DOWN = "service_down"
    RANDOM_FAILURE = "random_failure"
    TIMEOUT = "timeout"
    INVALID_RESPONSE = "invalid_response"


@dataclass
class ChaosEvent:
    """Represents a chaos engineering event"""
    event_id: str
    failure_type: FailureType
    service: str
    start_time: datetime
    end_time: datetime
    severity: str  # low, medium, high
    description: str
    parameters: Dict


class ChaosEngine:
    """
    Inject failures and chaos scenarios for testing system resilience
    """

    def __init__(self):
        """Initialize the Chaos Engine"""
        self.active_failures: Dict[str, ChaosEvent] = {}
        self.failure_history: List[ChaosEvent] = []
        self.enabled = True

    def inject_network_delay(
        self,
        service: str,
        delay_ms: int,
        duration_seconds: int = 30
    ) -> str:
        """
        Inject artificial network delay for a service

        Args:
            service: Service name (bank-a, bank-b, clearing)
            delay_ms: Delay in milliseconds
            duration_seconds: How long to apply the delay

        Returns:
            Event ID
        """
        event_id = f"delay_{service}_{int(datetime.now().timestamp())}"

        event = ChaosEvent(
            event_id=event_id,
            failure_type=FailureType.NETWORK_DELAY,
            service=service,
            start_time=datetime.now(),
            end_time=datetime.now() + timedelta(seconds=duration_seconds),
            severity="medium",
            description=f"Network delay of {delay_ms}ms injected to {service}",
            parameters={
                "delay_ms": delay_ms,
                "duration_seconds": duration_seconds
            }
        )

        self.active_failures[event_id] = event
        self.failure_history.append(event)
        logger.info(f"Injected network delay: {event.description}")

        return event_id

    def fail_transaction_randomly(
        self,
        probability: float = 0.5,
        duration_seconds: int = 60
    ) -> str:
        """
        Randomly fail transactions with given probability

        Args:
            probability: Probability of failure (0.0-1.0)
            duration_seconds: How long to apply random failures

        Returns:
            Event ID
        """
        if not (0.0 <= probability <= 1.0):
            raise ValueError("Probability must be between 0.0 and 1.0")

        event_id = f"random_fail_{int(datetime.now().timestamp())}"

        event = ChaosEvent(
            event_id=event_id,
            failure_type=FailureType.RANDOM_FAILURE,
            service="global",
            start_time=datetime.now(),
            end_time=datetime.now() + timedelta(seconds=duration_seconds),
            severity="high",
            description=f"Random transaction failures with {probability*100:.1f}% probability",
            parameters={
                "probability": probability,
                "duration_seconds": duration_seconds
            }
        )

        self.active_failures[event_id] = event
        self.failure_history.append(event)
        logger.info(f"Injected random failures: {event.description}")

        return event_id

    def simulate_service_outage(
        self,
        service: str,
        duration_seconds: int = 60
    ) -> str:
        """
        Simulate a service being completely down

        Args:
            service: Service name (bank-a, bank-b, clearing)
            duration_seconds: Duration of the outage

        Returns:
            Event ID
        """
        event_id = f"outage_{service}_{int(datetime.now().timestamp())}"

        event = ChaosEvent(
            event_id=event_id,
            failure_type=FailureType.SERVICE_DOWN,
            service=service,
            start_time=datetime.now(),
            end_time=datetime.now() + timedelta(seconds=duration_seconds),
            severity="high",
            description=f"{service} service outage for {duration_seconds} seconds",
            parameters={
                "duration_seconds": duration_seconds
            }
        )

        self.active_failures[event_id] = event
        self.failure_history.append(event)
        logger.warning(f"Simulated service outage: {event.description}")

        return event_id

    def inject_timeout(
        self,
        service: str,
        timeout_ms: int = 5000,
        duration_seconds: int = 30
    ) -> str:
        """
        Inject request timeouts for a service

        Args:
            service: Service name
            timeout_ms: Timeout in milliseconds
            duration_seconds: Duration of the timeout injection

        Returns:
            Event ID
        """
        event_id = f"timeout_{service}_{int(datetime.now().timestamp())}"

        event = ChaosEvent(
            event_id=event_id,
            failure_type=FailureType.TIMEOUT,
            service=service,
            start_time=datetime.now(),
            end_time=datetime.now() + timedelta(seconds=duration_seconds),
            severity="high",
            description=f"Request timeouts ({timeout_ms}ms) for {service}",
            parameters={
                "timeout_ms": timeout_ms,
                "duration_seconds": duration_seconds
            }
        )

        self.active_failures[event_id] = event
        self.failure_history.append(event)
        logger.warning(f"Injected timeouts: {event.description}")

        return event_id

    def inject_invalid_responses(
        self,
        service: str,
        probability: float = 0.3,
        duration_seconds: int = 30
    ) -> str:
        """
        Inject invalid/malformed responses from a service

        Args:
            service: Service name
            probability: Probability of invalid response
            duration_seconds: Duration of injection

        Returns:
            Event ID
        """
        event_id = f"invalid_{service}_{int(datetime.now().timestamp())}"

        event = ChaosEvent(
            event_id=event_id,
            failure_type=FailureType.INVALID_RESPONSE,
            service=service,
            start_time=datetime.now(),
            end_time=datetime.now() + timedelta(seconds=duration_seconds),
            severity="medium",
            description=f"Invalid responses from {service} ({probability*100:.1f}% probability)",
            parameters={
                "probability": probability,
                "duration_seconds": duration_seconds
            }
        )

        self.active_failures[event_id] = event
        self.failure_history.append(event)
        logger.warning(f"Injected invalid responses: {event.description}")

        return event_id

    def stop_failure(self, event_id: str) -> bool:
        """
        Stop a specific failure injection

        Args:
            event_id: ID of the failure to stop

        Returns:
            True if stopped, False if not found
        """
        if event_id in self.active_failures:
            event = self.active_failures.pop(event_id)
            logger.info(f"Stopped failure: {event.description}")
            return True
        return False

    def stop_all_failures(self):
        """Stop all active failures"""
        count = len(self.active_failures)
        self.active_failures.clear()
        logger.info(f"Stopped all {count} active failures")

    def get_active_failures(self) -> List[ChaosEvent]:
        """Get list of currently active failures"""
        now = datetime.now()
        active = []

        for event_id, event in list(self.active_failures.items()):
            if event.end_time > now:
                active.append(event)
            else:
                # Clean up expired failures
                self.active_failures.pop(event_id)

        return active

    def get_failure_history(self, limit: int = 100) -> List[ChaosEvent]:
        """Get historical failures (most recent first)"""
        return self.failure_history[-limit:][::-1]

    def should_fail_transaction(self) -> bool:
        """
        Check if a transaction should fail based on active chaos

        Returns:
            True if transaction should fail
        """
        if not self.enabled:
            return False

        active = self.get_active_failures()
        for event in active:
            if event.failure_type == FailureType.RANDOM_FAILURE:
                probability = event.parameters.get("probability", 0)
                if random.random() < probability:
                    return True

        return False

    def should_delay_request(self, service: str) -> int:
        """
        Get delay in milliseconds for a service request

        Args:
            service: Service name

        Returns:
            Delay in milliseconds (0 if no delay)
        """
        if not self.enabled:
            return 0

        active = self.get_active_failures()
        for event in active:
            if event.service == service and event.failure_type == FailureType.NETWORK_DELAY:
                return event.parameters.get("delay_ms", 0)

        return 0

    def is_service_down(self, service: str) -> bool:
        """
        Check if a service is simulated as down

        Args:
            service: Service name

        Returns:
            True if service is down
        """
        if not self.enabled:
            return False

        active = self.get_active_failures()
        for event in active:
            if event.service == service and event.failure_type == FailureType.SERVICE_DOWN:
                return True

        return False

    def get_status(self) -> Dict:
        """Get overall status of chaos engine"""
        active = self.get_active_failures()

        return {
            "enabled": self.enabled,
            "active_failures": len(active),
            "active_events": [
                {
                    "id": event.event_id,
                    "type": event.failure_type.value,
                    "service": event.service,
                    "severity": event.severity,
                    "description": event.description,
                    "end_time": event.end_time.isoformat()
                }
                for event in active
            ],
            "total_events_injected": len(self.failure_history)
        }

    def enable(self):
        """Enable chaos engine"""
        self.enabled = True
        logger.info("Chaos Engine enabled")

    def disable(self):
        """Disable chaos engine"""
        self.enabled = False
        logger.info("Chaos Engine disabled")


# Global instance
_chaos_engine = None


def get_chaos_engine() -> ChaosEngine:
    """Get or create the global chaos engine instance"""
    global _chaos_engine
    if _chaos_engine is None:
        _chaos_engine = ChaosEngine()
    return _chaos_engine
