# main.py
import logging
import sys
import time
from scenarios import SCENARIOS

logging.basicConfig(
    level=logging.INFO,
    format='%(asctime)s - %(name)s - %(levelname)s - %(message)s'
)
logger = logging.getLogger(__name__)

BANK_A_URL = "http://bankgood-bank-a-service:8080"
BANK_B_URL = "http://bankgood-bank-b-service:8080"
CLEARING_SERVICE_URL = "http://bankgood-clearing-service:8080"

def main():
    logger.info("=" * 60)
    logger.info("Starting Banking Simulation")
    logger.info("=" * 60)
    time.sleep(7)
    
    passed = 0
    failed = 0
    
    for scenario_class in SCENARIOS:
        scenario = scenario_class(BANK_A_URL, BANK_B_URL, CLEARING_SERVICE_URL)
        logger.info("-" * 60)
        logger.info(f"Running: {scenario.name}")
        logger.info("-" * 60)
        
        try:
            success = scenario.run()
            if success:
                logger.info(f"✓ {scenario.name} PASSED")
                passed += 1
            else:
                logger.error(f"✗ {scenario.name} FAILED")
                failed += 1
        except Exception as e:
            logger.exception(f"✗ {scenario.name} CRASHED: {e}")
            failed += 1
        
        time.sleep(3)  # Brief pause between scenarios
    
    # Summary
    logger.info("=" * 60)
    logger.info(f"Results: {passed} passed, {failed} failed")
    logger.info("=" * 60)
    


if __name__ == "__main__":
    main()