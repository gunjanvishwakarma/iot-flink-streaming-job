# Setting up IoT Flink Streaming Job with Kafka, InfluxDB, and Grafana

This document provides step-by-step instructions for setting up an IoT Flink streaming job that uses Kafka as the data source, InfluxDB as the data storage, and Grafana as the visualization tool.

## Prerequisites

1. **Homebrew:** Install Homebrew package manager if not already installed.

```bash
/bin/bash -c "$(curl -fsSL https://raw.githubusercontent.com/Homebrew/install/HEAD/install.sh)"
```

2. **Kafka:** Install Kafka using Homebrew. This will also install JDK and Zookeeper.

```bash
brew install kafka
```

3. **Start Zookeeper:**

```bash
/usr/local/bin/zookeeper-server-start /usr/local/etc/zookeeper/zoo.cfg
```

4. **Start Kafka:**

```bash
/usr/local/bin/kafka-server-start /usr/local/etc/kafka/server.properties
```

5. **Test Kafka:** Ensure Kafka is working correctly.

```bash
kafka-topics --list --bootstrap-server=localhost:9092
```

6. **Create Kafka Topics:**

```bash
kafka-topics --bootstrap-server localhost:9092 --topic events --create
kafka-topics --bootstrap-server localhost:9092 --topic rules --create
```

7. **InfluxDB:** Install InfluxDB using Homebrew.

```bash
brew install influxdb
brew services start influxdb
```

8. **Access InfluxDB UI:** Open InfluxDB UI in your browser at http://localhost:8086.

9. **Create Bucket and Generate API Token:** In the InfluxDB UI:

    - Create a bucket (e.g., `gunjan-bucket`) within your chosen organization (e.g., `gunjan-org`).
    - Generate an API token for authentication.

10. **Update InfluxDBSink.java:** In your Flink project, update the `InfluxDBSink.java` file with the valid API token and connection details:

```java
String url = "http://localhost:8086";
String token = "your-api-token";
String org = "gunjan-org";
String bucket = "gunjan-bucket";
```

11. **Grafana:** Install Grafana using Homebrew.

```bash
brew install grafana
```

12. **Access Grafana UI:** Open Grafana UI in your browser at http://localhost:3000.

13. **Connect Grafana with InfluxDB:**

- Add InfluxDB as a data source in Grafana:
    - Configure URL: `http://localhost:8086`
    - Configure Database: `gunjan-bucket`
    - Configure Token: Use the API token generated in step 9.

## Conclusion

You have successfully set up an IoT Flink streaming job with Kafka as the data source, InfluxDB as the data storage, and Grafana as the visualization tool.