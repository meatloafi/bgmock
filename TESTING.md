# Testing Guide

## Overview

This document provides comprehensive guidance on testing the BGMock banking system. The project includes multiple test types across different modules to ensure reliability and correctness of the banking and clearing operations.

## Test Structure

The test suite is organized by module:

```
bgmock/
├── common/
│   └── src/test/
│       ├── java/
│       │   └── com/bankgood/common/
│       │       ├── config/TestKafkaConfig.java      # Shared test Kafka configuration
│       │       └── kafka/TestKafkaConsumer.java     # Generic test consumer utility
│       └── resources/
├── bank-service/
│   └── src/test/
│       ├── java/
│       │   └── com/bankgood/bank/
│       │       ├── BankServiceApplicationTests.java  # Spring Boot context test
│       │       └── KafkaIntegrationTest.java         # Kafka producer/consumer tests
│       └── resources/
│           └── application.properties                # Test configuration
└── clearing-service/
    └── src/test/
        ├── java/
        │   └── com/clearingservice/
        │       ├── ClearingServiceApplicationTests.java  # Spring Boot context test
        │       └── KafkaIntegrationTest.java             # Comprehensive Kafka tests
        └── resources/
            └── application.properties                     # Test configuration
```

## Prerequisites

### Required Software
- **Java 17** or higher
- **Maven 3.8+**
- **Docker** (optional, for running Kafka locally)

### Test Dependencies
The following test frameworks are used:
- **JUnit 5** (Jupiter) - Test framework
- **Spring Boot Test** - Integration testing support
- **Embedded Kafka** - In-memory Kafka broker for tests
- **Awaitility** - Asynchronous assertions
- **AssertJ** - Fluent assertions
- **Mockito** - Mocking framework

## Running Tests

### Run All Tests

Run all tests across all modules:

```bash
mvn clean test
```

### Run Tests for Specific Module

Run tests for a single module:

```bash
# Bank service tests
cd bank-service
mvn test

# Clearing service tests
cd clearing-service
mvn test

# Common module tests
cd common
mvn test
```

### Run a Specific Test Class

```bash
# Run specific test class in bank-service
cd bank-service
mvn test -Dtest=KafkaIntegrationTest

# Run specific test class in clearing-service
cd clearing-service
mvn test -Dtest=KafkaIntegrationTest
```

### Run a Specific Test Method

```bash
# Run single test method
mvn test -Dtest=KafkaIntegrationTest#testProducer_sendsTransactionEventCorrectly
```

### Run Tests with Verbose Output

```bash
mvn test -X  # Debug mode with detailed logging
```

### Skip Tests During Build

```bash
mvn clean install -DskipTests
```

## Test Categories

### 1. Unit Tests
Currently, most tests are integration tests. Unit tests can be added for individual service methods.

**Location**: `src/test/java/`
**Naming**: `*Test.java`

### 2. Integration Tests

#### Application Context Tests
- **Purpose**: Verify Spring Boot application context loads correctly
- **Files**:
  - `bank-service/src/test/java/com/bankgood/bank/BankServiceApplicationTests.java`
  - `clearing-service/src/test/java/com/clearingservice/ClearingServiceApplicationTests.java`

#### Kafka Integration Tests
- **Purpose**: Test Kafka message production, consumption, and end-to-end transaction flows
- **Files**:
  - `bank-service/src/test/java/com/bankgood/bank/KafkaIntegrationTest.java`
  - `clearing-service/src/test/java/com/clearingservice/KafkaIntegrationTest.java`

### 3. End-to-End Tests
Currently implemented within Kafka Integration Tests. Can be expanded into separate test class.

**Potential location**: `src/test/java/**/e2e/`

## Test Coverage Details

### Bank Service Tests

| Test Class | Test Count | Description |
|------------|-----------|-------------|
| `BankServiceApplicationTests` | 1 | Application context loading |
| `KafkaIntegrationTest` | 2 | Producer and consumer message handling |
| **Total** | **3** | |

**Test Details:**
1. **Application Context Test**: Verifies Spring Boot application starts successfully
2. **Payment Producer Test**: Validates message sending to `payment.requests` topic
3. **Payment Consumer Test**: Validates message consumption from `payment.prepare` topic

### Clearing Service Tests

| Test Class | Test Count | Description |
|------------|-----------|-------------|
| `ClearingServiceApplicationTests` | 1 | Application context loading |
| `KafkaIntegrationTest` | 6 | Comprehensive transaction flow tests |
| **Total** | **7** | |

**Test Details:**
1. **Application Context Test**: Verifies Spring Boot application starts successfully
2. **Producer Test**: Validates TransactionEvent sending
3. **Transaction Event Listener Test**: Validates incoming transaction processing
4. **Transaction Response Listener Test**: Validates response handling
5. **End-to-End Success Flow**: Complete transaction from Bank A → Clearing → Bank B
6. **Error Scenario - Missing Mapping**: Handles recipient not found
7. **Error Scenario - Bank B Failure**: Handles transaction failure at recipient bank

### Kafka Topics Tested

| Topic | Direction | Purpose |
|-------|-----------|---------|
| `payment.requests` | Bank → ? | Payment request initiation |
| `payment.prepare` | ? → Bank | Payment preparation messages |
| `transactions.outgoing` | Bank A → Clearing | Outgoing transaction events |
| `transactions.response` | Clearing ↔ Bank | Transaction response events |
| `transactions.incoming.<bankname>` | Clearing → Bank B | Bank-specific incoming transactions |

## Adding New Tests

### 1. Create Test Class

```java
package com.bankgood.bank;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
class MyNewTest {

    @Test
    void testSomething() {
        // Arrange
        String expected = "result";

        // Act
        String actual = performOperation();

        // Assert
        assertThat(actual).isEqualTo(expected);
    }
}
```

### 2. Kafka Integration Test Template

```java
@SpringBootTest
@EmbeddedKafka(
    partitions = 1,
    brokerProperties = {"listeners=PLAINTEXT://localhost:9094", "port=9094"},
    topics = {"your.topic.name"}
)
@TestPropertySource(properties = {
    "spring.kafka.bootstrap-servers=${spring.embedded.kafka.brokers}"
})
@Import(TestKafkaConfig.class)
class MyKafkaIntegrationTest {

    @Autowired
    private KafkaTemplate<String, YourEvent> kafkaTemplate;

    @Test
    @Timeout(10)
    void testKafkaFlow() throws Exception {
        // Your test implementation
    }
}
```

### 3. Test Naming Conventions

- **Test Classes**: `*Test.java` or `*Tests.java`
- **Integration Tests**: `*IntegrationTest.java`
- **Test Methods**: Use descriptive names with underscores
  - Format: `test<Component>_<scenario>_<expectedBehavior>`
  - Example: `testProducer_sendsMessage_successfully()`

### 4. Update This Documentation

When adding new tests:
1. Update the relevant table in **Test Coverage Details**
2. Add any new test categories to **Test Categories**
3. Document any new Kafka topics in **Kafka Topics Tested**

## Test Configuration

### Embedded Kafka Configuration

Tests use Spring Kafka Test's `@EmbeddedKafka` annotation to run an in-memory Kafka broker:

```java
@EmbeddedKafka(
    partitions = 1,
    brokerProperties = {"listeners=PLAINTEXT://localhost:9094", "port=9094"},
    topics = {"transactions.outgoing", "transactions.response"}
)
```

**Port Assignments:**
- Bank Service: `9093`
- Clearing Service: `9094`

### Test Properties

Test-specific properties are in `src/test/resources/application.properties`:

```properties
spring.kafka.bootstrap-servers=${spring.embedded.kafka.brokers}
spring.kafka.consumer.group-id=test-group
spring.kafka.consumer.auto-offset-reset=earliest
```

## Troubleshooting

### Common Issues

#### 1. Test Timeout
**Symptom**: Tests fail with timeout exceptions

**Solutions**:
- Increase `@Timeout` value on test methods
- Check Awaitility wait times: `await().atMost(10, TimeUnit.SECONDS)`
- Verify embedded Kafka broker started successfully

#### 2. Port Already in Use
**Symptom**: `BindException: Address already in use`

**Solutions**:
- Change embedded Kafka port in `@EmbeddedKafka` annotation
- Kill existing processes: `lsof -ti:9094 | xargs kill -9`

#### 3. Context Load Failures
**Symptom**: Application context fails to load

**Solutions**:
- Check `application.properties` configuration
- Verify all required beans are available
- Review dependency injection errors in stack trace

#### 4. Kafka Message Not Received
**Symptom**: Awaitility timeout waiting for message

**Solutions**:
- Verify topic names match between producer and consumer
- Check consumer is subscribed to correct topic
- Ensure message serialization/deserialization is correct
- Add debug logging to listeners

### Debug Tips

1. **Enable Debug Logging**:
   ```properties
   logging.level.org.springframework.kafka=DEBUG
   logging.level.com.bankgood=DEBUG
   logging.level.com.clearingservice=DEBUG
   ```

2. **Use `@DirtiesContext`**: Force new application context for each test
   ```java
   @DirtiesContext(classMode = ClassMode.AFTER_EACH_TEST_METHOD)
   ```

3. **Inspect Kafka Metrics**: Check embedded broker logs for message flow

## Continuous Integration

### GitHub Actions (Placeholder)

```yaml
# .github/workflows/test.yml
name: Run Tests

on: [push, pull_request]

jobs:
  test:
    runs-on: ubuntu-latest
    steps:
      - uses: actions/checkout@v3
      - name: Set up JDK 17
        uses: actions/setup-java@v3
        with:
          java-version: '17'
          distribution: 'temurin'
      - name: Run tests
        run: mvn clean test
```

### Jenkins (Placeholder)

```groovy
// Jenkinsfile
pipeline {
    agent any
    stages {
        stage('Test') {
            steps {
                sh 'mvn clean test'
            }
        }
    }
}
```

## Performance Testing

**Future Enhancement**: Add performance tests to measure:
- Message throughput
- Transaction processing latency
- System behavior under load

**Recommended Tools**:
- JMeter
- Gatling
- Spring Boot Actuator metrics

## Test Reporting

### Generate Test Reports

```bash
mvn surefire-report:report
```

View reports at: `target/site/surefire-report.html`

### Code Coverage (Future)

Add JaCoCo plugin to measure code coverage:

```xml
<plugin>
    <groupId>org.jacoco</groupId>
    <artifactId>jacoco-maven-plugin</artifactId>
</plugin>
```

## Best Practices

1. **Isolation**: Each test should be independent and not rely on execution order
2. **Cleanup**: Use `@BeforeEach` and `@AfterEach` to set up and tear down test data
3. **Assertions**: Use descriptive assertion messages for easier debugging
4. **Timeouts**: Always set reasonable timeouts for async operations
5. **Naming**: Use clear, descriptive test names that explain the scenario
6. **Documentation**: Update this document when adding new test types or modules

## Resources

- [Spring Boot Testing Documentation](https://docs.spring.io/spring-boot/docs/current/reference/html/features.html#features.testing)
- [Spring Kafka Testing](https://docs.spring.io/spring-kafka/reference/html/#testing)
- [JUnit 5 User Guide](https://junit.org/junit5/docs/current/user-guide/)
- [AssertJ Documentation](https://assertj.github.io/doc/)
- [Awaitility Guide](https://github.com/awaitility/awaitility/wiki/Usage)

---

**Last Updated**: 2025-11-17
**Maintained By**: BGMock Development Team
