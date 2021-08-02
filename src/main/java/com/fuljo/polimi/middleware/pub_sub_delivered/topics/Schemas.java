package com.fuljo.polimi.middleware.pub_sub_delivered.topics;

import com.fuljo.polimi.middleware.pub_sub_delivered.model.avro.User;
import io.confluent.kafka.serializers.AbstractKafkaSchemaSerDeConfig;
import io.confluent.kafka.streams.serdes.avro.SpecificAvroSerde;
import org.apache.kafka.common.serialization.Serde;
import org.apache.kafka.common.serialization.Serdes;

import java.util.HashMap;
import java.util.Map;

/**
 * Utility class to store topics and their configuration.
 * <p>
 * In this way topics' schemas can be shared between different services
 * without including the package of the other services
 */
public class Schemas {

    /**
     * Wrapper for Kafka topic, including key and value serdes
     *
     * @param <K> type of the key
     * @param <V> type of the value
     */
    public static class Topic<K, V> {

        private final String name;
        private final Serde<K> keySerde;
        private final Serde<V> valueSerde;

        /**
         * Create a new topic
         *
         * @param name       name (Kafka identifier) of the topic
         * @param keySerde   key serializer/deserializer
         * @param valueSerde value serializer/deserializer
         */
        public Topic(String name, Serde<K> keySerde, Serde<V> valueSerde) {
            this.name = name;
            this.keySerde = keySerde;
            this.valueSerde = valueSerde;
        }

        public String name() {
            return name;
        }

        public Serde<K> keySerde() {
            return keySerde;
        }

        public Serde<V> valueSerde() {
            return valueSerde;
        }

        @Override
        public String toString() {
            return name;
        }
    }

    /**
     * Utility class to store the topics for our application
     */
    public static class Topics {

        public final static Map<String, Topic<?, ?>> ALL = new HashMap<>();
        public static Topic<String, User> USERS;

        static {
            createTopics();
        }

        private static void createTopics() {
            USERS = new Topic<>("users", Serdes.String(), new SpecificAvroSerde<>());
            ALL.put("users", USERS);
        }
    }

    /**
     * Apply configuration options to serializers/and deserializers for all topics
     * @param schemaRegistryUrl url of the schema registry
     */
    public static void configureSerdes(final String schemaRegistryUrl) {
        Map<String, String> config = new HashMap<>();
        config.put(AbstractKafkaSchemaSerDeConfig.SCHEMA_REGISTRY_URL_CONFIG, schemaRegistryUrl);
        for (final Topic<?, ?> topic : Topics.ALL.values()) {
            configureSerde(topic.keySerde, config, true);
            configureSerde(topic.valueSerde, config, false);
        }
    }

    private static void configureSerde(final Serde<?> serde, final Map<String, String> config, final boolean isKey) {
        // We don't need to set the schema registry URL for embedded serdes
        if (serde instanceof SpecificAvroSerde) {
            serde.configure(config, isKey);
        }
    }
}
