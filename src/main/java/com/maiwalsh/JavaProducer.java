package com.maiwalsh;

import java.time.Instant;
import java.util.Collections;
import java.util.Properties;
import java.util.Set;
import java.util.concurrent.ExecutionException;

import org.apache.kafka.clients.admin.AdminClient;
import org.apache.kafka.clients.admin.AdminClientConfig;
import org.apache.kafka.clients.admin.NewTopic;
import org.apache.kafka.clients.producer.KafkaProducer;
import org.apache.kafka.clients.producer.Producer;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.common.serialization.StringSerializer;

public class JavaProducer {
    public static void main(String[] args) {
        // Load configuration from environment variables
        String confluentBrokerServer = System.getenv("CONFLUENT_BROKER_SERVER");
        System.out.println("Retrieved broker environment variable successfully.");

        String topic = System.getenv("KAFKA_TOPIC");
        System.out.println("Retrieved kafka topic environment variable successfully.");

        // Check if topic is null or empty and assign default topic if necessary
            System.out.println(topic);
        if (topic == null || topic.isEmpty()) {
            topic = "test-topic";
            System.out.println(topic);

            // Check if "test-topic" exists in Kafka, and create it if it does not exist
            System.out.println("Attempting admin client creation.");
            try (AdminClient adminClient = AdminClient.create(getAdminProperties(confluentBrokerServer))) {
                Set<String> topics = adminClient.listTopics().names().get();
                
                System.out.println("All topics: ");
                for (String t : topics) {
                    System.out.println(t);
                }

                if (!topics.contains("test-topic")) {
                    System.out.println("Creating topic: test-topic");
                    NewTopic newTopic = new NewTopic("test-topic", 1, (short) 1);
                    adminClient.createTopics(Collections.singletonList(newTopic)).all().get();
                } else {
                    System.out.println("Topic 'test-topic' already exists. Using it.");
                }
            } catch (InterruptedException | ExecutionException e) {
                System.err.println("Error while checking/creating topic: " + e.getMessage());
                System.exit(1);
            }
        }
        System.out.println(topic);

        int intervalMs = Integer.parseInt(System.getenv("PRODUCER_INTERVAL_MS"));

        if (confluentBrokerServer == null || topic == null || System.getenv("PRODUCER_INTERVAL_MS") == null) {
            System.err.println("Missing required environment variables: CONFLUENT_BROKER_SERVER, KAFKA_TOPIC, PRODUCER_INTERVAL_MS");
            System.exit(1);
        }

        // Kafka producer configuration
        Properties properties = new Properties();
        properties.put(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, confluentBrokerServer);
        properties.put(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, StringSerializer.class.getName());
        properties.put(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, StringSerializer.class.getName());

        // Create Kafka producer instance
        try (Producer<String, String> producer = new KafkaProducer<>(properties)) {
            while (true) {
                String timestamp = Instant.now().toString();
                ProducerRecord<String, String> record = new ProducerRecord<>(topic, "heartbeat", timestamp);

                producer.send(record, (metadata, exception) -> {
                    if (exception == null) {
                        System.out.println("Sent heartbeat: '" + timestamp + "' to topic: " + metadata.topic() +
                                           " | partition: " + metadata.partition() +
                                           " | offset: " + metadata.offset());
                    } else {
                        exception.printStackTrace();
                    }
                });

                Thread.sleep(intervalMs);
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            System.err.println("Producer interrupted: " + e.getMessage());
        } catch (Exception e) {
            System.err.println("Producer error: " + e.getMessage());
        }
    }
    private static Properties getAdminProperties(String bootstrapServers) {
        Properties props = new Properties();
        props.put(AdminClientConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers);
        return props;
    }
}
