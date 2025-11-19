"""
BGMock Simulator Package
Provides chaos engineering, load testing, and monitoring capabilities
"""

from .chaos_engine import get_chaos_engine, ChaosEngine, FailureType
from .load_tester import get_load_tester, LoadTester, LoadProfile

__all__ = [
    'get_chaos_engine',
    'ChaosEngine',
    'FailureType',
    'get_load_tester',
    'LoadTester',
    'LoadProfile',
]

__version__ = '1.0.0'
