package rules;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.gunjan.Rule;
import org.apache.kafka.common.serialization.StringSerializer;
import org.apache.kafka.clients.producer.*;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Properties;

public class PublishRule {

    private static ObjectMapper objectMapper = new ObjectMapper();

    public static void main(String[] args) throws IOException {

        InputStream inputStream = PublishRule.class.getClassLoader().getResourceAsStream("shapes-rules.json");
        
        Rules rules = objectMapper.readValue(inputStream, Rules.class);
        rules.getData().stream().forEach(rule -> {
            try {
                System.out.println(rule);
                createProducer().send(new ProducerRecord("rules",objectMapper.writeValueAsString(rule)));
                Thread.sleep(1000);
            } catch (JsonProcessingException e) {
                e.printStackTrace();
            } catch (Exception e){
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
}


