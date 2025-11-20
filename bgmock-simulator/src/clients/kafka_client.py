# src/clients/kafka_client.py
import json
import logging
import threading
from typing import Callable, Dict, Any, Optional
from confluent_kafka import Producer, Consumer, KafkaError
from confluent_kafka.admin import AdminClient, NewTopic

from config import config
from models.transaction import TransactionEvent, TransactionResponseEvent

logger = logging.getLogger(__name__)

class KafkaClient:
    """Kafka producer and consumer for digital twin"""
    
    def __init__(self):
        self.producer_config = {
            'bootstrap.servers': config.kafka_bootstrap_servers,
            'client.id': 'digital-twin-producer'
        }

        self.consumer_config = {
            'bootstrap.servers': config.kafka_bootstrap_servers,
            'group.id': config.kafka_group_id,
            'auto.offset.reset': 'earliest',
            'enable.auto.commit': True
        }

        self.producer = None
        self.consumers = {}
        self.consumer_threads = {}
        self.callbacks = {}
        self.kafka_available = False

        # Graceful degradation: Try to connect to Kafka, but don't crash if unavailable
        # This allows the simulator to run in offline mode for demos and development
        try:
            self.producer = Producer(self.producer_config)
            self._ensure_topics_exist()  # Auto-create missing topics
            self.kafka_available = True
            logger.info("Kafka connection established successfully")
        except Exception as e:
            # Log warning but continue - all Kafka operations will check kafka_available flag
            logger.warning(f"Kafka not available: {e}. Simulator will run in offline mode.")
            self.kafka_available = False
    
    def _ensure_topics_exist(self):
        """Create Kafka topics if they don't exist"""
        admin = AdminClient({'bootstrap.servers': config.kafka_bootstrap_servers})
        
        existing_topics = admin.list_topics(timeout=10).topics
        
        topics_to_create = []
        for topic_name in config.topics.values():
            if topic_name not in existing_topics:
                topics_to_create.append(
                    NewTopic(topic_name, num_partitions=1, replication_factor=1)
                )
        
        if topics_to_create:
            fs = admin.create_topics(topics_to_create)
            for topic, future in fs.items():
                try:
                    future.result()
                    logger.info(f"Created topic: {topic}")
                except Exception as e:
                    logger.warning(f"Topic {topic} may already exist: {e}")
    
    def send_transaction_event(self, topic: str, event: TransactionEvent):
        """Send a transaction event to Kafka"""
        # Guard clause: Skip if Kafka is unavailable (offline mode)
        if not self.kafka_available:
            logger.warning(f"Kafka not available - transaction {event.transaction_id} not sent")
            return

        try:
            # Asynchronous send - callback will be called when message is acknowledged
            self.producer.produce(
                topic=topic,
                key=event.transaction_id,  # Key for partitioning
                value=event.to_json(),      # Serialize to JSON
                callback=self._delivery_callback  # Called on success/failure
            )
            self.producer.flush()
            logger.info(f"Sent transaction {event.transaction_id} to {topic}")
        except Exception as e:
            logger.error(f"Failed to send transaction event: {e}")
    
    def send_response_event(self, topic: str, response: TransactionResponseEvent):
        """Send a response event to Kafka"""
        if not self.kafka_available:
            logger.warning(f"Kafka not available - response {response.transaction_id} not sent")
            return

        try:
            self.producer.produce(
                topic=topic,
                key=response.transaction_id,
                value=response.to_json(),
                callback=self._delivery_callback
            )
            self.producer.flush()
            logger.info(f"Sent response {response.transaction_id} to {topic}")
        except Exception as e:
            logger.error(f"Failed to send response event: {e}")
    
    def subscribe(self, topic: str, callback: Callable[[str, Dict], None]):
        """Subscribe to a Kafka topic with a callback"""
        if not self.kafka_available:
            logger.warning(f"Kafka not available - cannot subscribe to {topic}")
            return

        if topic in self.consumers:
            logger.warning(f"Already subscribed to {topic}")
            return

        try:
            consumer = Consumer(self.consumer_config)
            consumer.subscribe([topic])
            self.consumers[topic] = consumer
            self.callbacks[topic] = callback

            # Start consumer thread
            thread = threading.Thread(
                target=self._consume_loop,
                args=(topic, consumer, callback),
                daemon=True
            )
            thread.start()
            self.consumer_threads[topic] = thread
            logger.info(f"Subscribed to topic: {topic}")
        except Exception as e:
            logger.error(f"Failed to subscribe to {topic}: {e}")
    
    def _consume_loop(self, topic: str, consumer: Consumer, callback: Callable):
        """Consumer loop running in separate thread"""
        logger.info(f"Starting consumer loop for {topic}")
        
        while topic in self.consumers:
            try:
                msg = consumer.poll(1.0)
                
                if msg is None:
                    continue
                
                if msg.error():
                    if msg.error().code() == KafkaError._PARTITION_EOF:
                        continue
                    logger.error(f"Consumer error: {msg.error()}")
                    continue
                
                # Parse message
                try:
                    value = json.loads(msg.value().decode('utf-8'))
                    key = msg.key().decode('utf-8') if msg.key() else None
                    
                    # Call the callback
                    callback(key, value)
                    
                except json.JSONDecodeError as e:
                    logger.error(f"Failed to decode message: {e}")
                except Exception as e:
                    logger.error(f"Callback error: {e}")
                    
            except Exception as e:
                logger.error(f"Consumer loop error: {e}")
    
    def unsubscribe(self, topic: str):
        """Unsubscribe from a topic"""
        if topic in self.consumers:
            self.consumers[topic].close()
            del self.consumers[topic]
            del self.callbacks[topic]
            logger.info(f"Unsubscribed from topic: {topic}")
    
    def _delivery_callback(self, err, msg):
        """Callback for message delivery confirmation"""
        if err is not None:
            logger.error(f"Message delivery failed: {err}")
        else:
            logger.debug(f"Message delivered to {msg.topic()} [{msg.partition()}]")
    
    def close(self):
        """Clean shutdown"""
        for topic in list(self.consumers.keys()):
            self.unsubscribe(topic)
        if self.producer:
            self.producer.flush()