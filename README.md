# iot-flink-streaming-job

install Homebrew 

brew install kafka (this is will install jdk,zookeeper,kafka)

Start zookeeper
/usr/local/bin/zookeeper-server-start /usr/local/etc/zookeeper/zoo.cfg

Start Kafka
/usr/local/bin/kafka-server-start /usr/local/etc/kafka/server.properties

Test kafka is working fine
kafka-topics --list --bootstrap-server=localhost:9092

Create topics
kafka-topics --bootstrap-server localhost:9092 --topic events --create
kafka-topics --bootstrap-server localhost:9092 --topic rules --create

brew install influxdb

Check InfluxDB UI - http://localhost:8086

Create bucket
Generate API token 
url = "http://localhost:8086";
org = "gunjan-org";
bucket = "gunjan-bucket";

Update InfluxDBSink.java with valid token.

brew install grafana

Check Grafana UI - http://localhost:3000

Connect Grafana with InfluxDB










