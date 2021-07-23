package rules;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.gson.JsonObject;
import com.gunjan.Rule;
import lombok.Data;
import lombok.EqualsAndHashCode;
import org.apache.kafka.clients.producer.KafkaProducer;
import org.apache.kafka.clients.producer.Producer;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.common.serialization.StringSerializer;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Properties;

public class PublishEvent {

    private static ObjectMapper objectMapper = new ObjectMapper();

    public static void main(String[] args) throws IOException {
        Events events = objectMapper.readValue(new File("src/main/java/rules/payloads.json"), Events.class);
        events.getData().stream().forEach(rule -> {
            try {
                System.out.println(rule);
                Thread.sleep(1000);
                createProducer().send(new ProducerRecord("devicePayload",objectMapper.writeValueAsString(rule)));
            } catch (JsonProcessingException | InterruptedException e) {
                e.printStackTrace();
            }
        });

    }

    private static Producer<String, String> createProducer() {
        Properties props = new Properties();
        props.put(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG,
                "localhost:9092");
        props.put(ProducerConfig.CLIENT_ID_CONFIG, "KafkaExampleProducer");
        props.put(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG,
                StringSerializer.class.getName());
        props.put(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG,
                StringSerializer.class.getName());
        return new KafkaProducer<>(props);
    }


    public static Properties getKafkaProperties() {
        Properties props = new Properties();

        //Assign localhost id
        props.put("bootstrap.servers", "localhost:9092");

        //Set acknowledgements for producer requests.
        props.put("acks", "all");

        props.put("enable.idempotence", true);

        //If the request fails, the producer can automatically retry,
        props.put("retries", 10);

        //Specify buffer size in config
        props.put("batch.size", 32768);

        //Reduce the no of requests less than 0
        props.put("linger.ms", 20);

        props.put("compression.type", "snappy");

        //If the producer produces faster than the broker can take, the records will be buffered in memory.
        // Below is the size of the send buffer.
        props.put("buffer.memory", 33554432); // 32 MB

        // If send buffer is full, producer will block for 60000 ms before throwing exception
        props.put("max.block.ms", 60000);

        props.put("key.serializer", StringSerializer.class.getName());

        props.put("value.serializer", StringSerializer.class.getName());
        return props;
    }
}


class Events {
    @JsonProperty("data")
    private List<Event> data = new ArrayList<>();

    public List<Event> getData() {
        return data;
    }

    public void setData(List<Event> data) {
        this.data = data;
    }
}

@Data
@EqualsAndHashCode(callSuper = false)
class Event {
    @JsonProperty("shape")
    private String shape;

    @JsonProperty("border")
    private String border;

    @JsonProperty("x")
    private Long x;

    @JsonProperty("y")
    private Long y;

    @JsonProperty("color")
    private String color;
}
