package com.maiwalsh;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
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

public class CelestrakTLEProducer {
    public static void main(String[] args) {
        String confluentBrokerServer = System.getenv("CONFLUENT_BROKER_SERVER");
        System.out.println("Retrieved broker environment variable successfully.");

        String topic = System.getenv("KAFKA_TOPIC");
        System.out.println("Retrieved kafka topic environment variable successfully.");

        System.out.println(topic);
        if (topic == null || topic.isEmpty()) {
            topic = "celestrak-tle";
            System.out.println(topic);
            System.out.println("Attempting admin client creation.");
            try (AdminClient adminClient = AdminClient.create(getAdminProperties(confluentBrokerServer))) {
                Set<String> topics = adminClient.listTopics().names().get();

                System.out.println("All topics: ");
                for (String t : topics) {
                    System.out.println(t);
                }

                if (!topics.contains("celestrak-tle")) {
                    System.out.println("Creating topic: celestrak-tle");
                    NewTopic newTopic = new NewTopic("celestrak-tle", 1, (short) 1);
                    adminClient.createTopics(Collections.singletonList(newTopic)).all().get();
                } else {
                    System.out.println("Topic 'celestrak-tle' already exists. Using it.");
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

        Properties properties = new Properties();
        properties.put(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, confluentBrokerServer);
        properties.put(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, StringSerializer.class.getName());
        properties.put(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, StringSerializer.class.getName());

        try (Producer<String, String> producer = new KafkaProducer<>(properties)) {
            while (true) {
                try {
                    List<String> tleRecords = fetchAndParseTLE();
                    for (String tle : tleRecords) {
                        ProducerRecord<String, String> record = new ProducerRecord<>(topic, "TLE", tle);
                        producer.send(record, (metadata, exception) -> {
                            if (exception == null) {
                                System.out.println("Sent TLE record to topic: " + metadata.topic() +
                                        " | partition: " + metadata.partition() +
                                        " | offset: " + metadata.offset());
                            } else {
                                exception.printStackTrace();
                            }
                        });
                    }
                } catch (IOException | InterruptedException e) {
                    System.err.println("Failed to fetch or parse TLE data: " + e.getMessage());
                }
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

    private static List<String> fetchAndParseTLE() throws IOException, InterruptedException {
        String celestrakUrl = "https://celestrak.org/NORAD/elements/gp.php?GROUP=active&FORMAT=tle";
        HttpClient client = HttpClient.newHttpClient();
        HttpRequest request = HttpRequest.newBuilder().uri(URI.create(celestrakUrl)).build();
        HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());

        List<String> tleChunks = new ArrayList<>();
        if (response.statusCode() == 200) {
            String[] lines = response.body().split("\n");
            for (int i = 0; i + 2 < lines.length; i += 3) {
                String tleRecord = lines[i].trim() + "\n" + lines[i + 1].trim() + "\n" + lines[i + 2].trim();
                tleChunks.add(tleRecord);
            }
        } else {
            throw new IOException("Failed to fetch TLE data. Status code: " + response.statusCode());
        }
        return tleChunks;
    }
}
