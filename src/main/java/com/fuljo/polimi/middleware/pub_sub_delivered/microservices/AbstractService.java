package com.fuljo.polimi.middleware.pub_sub_delivered.microservices;

import com.fuljo.polimi.middleware.pub_sub_delivered.topics.Schemas.Topic;
import org.apache.commons.cli.Option;
import org.apache.commons.cli.Options;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.producer.KafkaProducer;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.common.serialization.Serde;
import org.apache.kafka.streams.KafkaStreams;
import org.apache.kafka.streams.StreamsBuilder;
import org.apache.kafka.streams.StreamsConfig;
import org.apache.kafka.streams.kstream.Consumed;
import org.apache.kafka.streams.kstream.Materialized;
import org.apache.kafka.streams.state.Stores;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.Properties;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

/**
 * Abstract class for a microservice
 */
public abstract class AbstractService implements Service {

    protected final Logger log = LoggerFactory.getLogger(getClass());

    protected final String SERVICE_APP_ID = getClass().getSimpleName();
    protected final Long STREAMS_TIMEOUT = 60000L;

    public static final String DEFAULT_BOOTSTRAP_SERVERS = "localhost:9092";
    public static final String DEFAULT_SCHEMA_REGISTRY_URL = "http://localhost:8081";


    @Override
    public abstract void start(String bootstrapServers, String stateDir, String replicaId, Properties defaultConfig);

    @Override
    public abstract void stop();

    // Provide default empty constructor
    protected AbstractService() {
    }

    /**
     * Builds a Properties object from a property file
     *
     * @param configFile java properties file
     * @return properties object, empty if configFile is null
     * @throws IOException if the file does not exist
     */
    protected static Properties buildPropertiesFromConfigFile(final String configFile) throws IOException {
        // No file configured => empty properties
        if (configFile == null) {
            return new Properties();
        }
        // Wrong name => exception
        if (!Files.exists(Paths.get(configFile))) {
            throw new IOException(configFile + " not found.");
        }
        // File exists => read config
        final Properties properties = new Properties();
        try (InputStream inputStream = new FileInputStream(configFile)) {
            properties.load(inputStream);
        }
        return properties;
    }

    /**
     * Add hooks to gracefully shut down the service, blocks the current thread
     * <p>
     * Blocking might be needed to keep a web server or a consumer polling running
     *
     * @throws InterruptedException when current thread is interrupted
     */
    protected static void addShutdownHookAndBlock(Service service) throws InterruptedException {
        // Gracefully stop the service on exception
        Thread.currentThread().setUncaughtExceptionHandler((t, e) -> service.stop());

        // Gracefully stop the service on shutdown
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            try {
                service.stop();
            } catch (final Exception ignored) {
            }
        }));
        Thread.currentThread().join();
    }

    /**
     * Creates an Options object with specification of CLI options
     *
     * @param options options objects to append to
     */
    protected static void addCliOptions(Options options) {
        options
                .addOption(Option.builder("b")
                        .longOpt("bootstrap-servers").hasArg().desc("Kafka cluster bootstrap server string").build())
                .addOption(Option.builder("s")
                        .longOpt("schema-registry").hasArg().desc("Schema Registry URL").build())
                .addOption(Option.builder("c")
                        .longOpt("config-file").hasArg().desc("Properties file with Kafka configuration").build())
                .addOption(Option.builder("d")
                        .longOpt("state-dir").hasArg().desc("Directory for state storage").build())
                .addOption(Option.builder("r")
                        .longOpt("replica-id").hasArg().desc("Id to distinguish this replica (e.g. 1)").build())
                .addOption(Option.builder("h").longOpt("help").hasArg(false).desc("Show help").build());
    }

    /**
     * Returns a default configuration for a producer
     *
     * @param bootstrapServers urls of bootstrap servers
     * @param clientId         client id for kafka identification
     * @return configuration
     */
    protected Properties defaultProducerConfig(String bootstrapServers, String clientId) {
        final Properties config = new Properties();
        config.put(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers);
        config.put(ProducerConfig.ENABLE_IDEMPOTENCE_CONFIG, "true");
        config.put(ProducerConfig.RETRIES_CONFIG, String.valueOf(Integer.MAX_VALUE));
        config.put(ProducerConfig.ACKS_CONFIG, "all");
        config.put(ProducerConfig.CLIENT_ID_CONFIG, clientId);
        return config;
    }

    /**
     * Creates a new producer
     *
     * @param bootstrapServers urls of bootstrap servers
     * @param clientId         client id for kafka identification
     * @param keySerde         key serializer/deserializer
     * @param valueSerde       value serializer/deserializer
     * @param defaultConfig    default configuration provided by the user
     * @param <K>              key type
     * @param <V>              value type
     * @return the producer
     */
    protected <K, V> KafkaProducer<K, V> createProducer(String bootstrapServers,
                                                        String clientId,
                                                        Serde<K> keySerde,
                                                        Serde<V> valueSerde,
                                                        Properties defaultConfig) {
        // Configure
        final Properties config = new Properties();
        config.putAll(defaultConfig);
        config.putAll(defaultProducerConfig(bootstrapServers, clientId));
        // Create
        return new KafkaProducer<>(config, keySerde.serializer(), valueSerde.serializer());
    }

    /**
     * Creates a new producer
     *
     * @param bootstrapServers urls of bootstrap servers
     * @param clientId         client id for kafka identification
     * @param transactionalId  id of this producer for transactional purposes
     * @param keySerde         key serializer/deserializer
     * @param valueSerde       value serializer/deserializer
     * @param defaultConfig    default configuration provided by the user
     * @param <K>              key type
     * @param <V>              value type
     * @return the producer
     */
    protected <K, V> KafkaProducer<K, V> createTransactionalProducer(String bootstrapServers,
                                                                     String clientId,
                                                                     String transactionalId,
                                                                     Serde<K> keySerde,
                                                                     Serde<V> valueSerde,
                                                                     Properties defaultConfig) {
        // Configure
        final Properties config = new Properties();
        config.putAll(defaultConfig);
        config.putAll(defaultProducerConfig(bootstrapServers, clientId));
        config.put(ProducerConfig.TRANSACTIONAL_ID_CONFIG, transactionalId);
        // Create
        KafkaProducer<K, V> producer = new KafkaProducer<>(config, keySerde.serializer(), valueSerde.serializer());
        // Fence off previous instances of this producer (with same transactional id)
        producer.initTransactions();
        return producer;
    }

    /**
     * Create streams configuration with some default values
     *
     * @param bootstrapServers     urls of boostrap servers
     * @param stateDir             local directory to checkpoint state
     * @param appId                id of the whole application
     * @param exactlyOnceSemantics whether to use exactly once or at least once
     * @return the configuration
     */
    protected Properties defaultStreamsConfig(String bootstrapServers,
                                              String stateDir,
                                              String appId,
                                              Boolean exactlyOnceSemantics) {
        Properties config = new Properties();
        config.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest");
        config.put(StreamsConfig.APPLICATION_ID_CONFIG, appId);
        config.put(StreamsConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers);
        config.put(StreamsConfig.REPLICATION_FACTOR_CONFIG, 2); // TODO: Change this
        config.put(StreamsConfig.STATE_DIR_CONFIG, stateDir);
        config.put(StreamsConfig.PROCESSING_GUARANTEE_CONFIG, exactlyOnceSemantics ? "exactly_once" : "at_least_once");
        return config;
    }

    /**
     * Creates a global materialized view of a topic via an in-memory key-value store.
     * The given topic is assumed to be already persisted.
     *
     * @param topic            the topic
     * @param storeName        local name for the key-value store
     * @param bootstrapServers urls of the bootstrap servers
     * @param defaultConfig    config provided by the  user
     * @param <K>              key type
     * @param <V>              value type
     * @return a kafka streams instance, yet to be started
     * @implNote We create a GlobalKTable to retrieve the records, which is more costly than a regular KTable.
     * A solution which scales better would be to build a KTable for each partition:
     * <ul>
     *     <li>requests for local keys are handled locally</li>
     *     <li>requests for non-local keys are routed towards the other replicas</li>
     * </ul>
     * However, we would need to manually set up an RPC layer to discover and communicate to other partitions,
     * which has a non-negligible programming cost and is out of the scope of this project.
     * @implNote We use a non-persisted in-memory store for the records, since the topic is already persisted,
     * and the resulting table can be computed from it.
     */
    protected <K, V> KafkaStreams createMaterializedView(Topic<K, V> topic,
                                                         String storeName,
                                                         String bootstrapServers,
                                                         String stateDir,
                                                         Properties defaultConfig) {
        // Define topology
        StreamsBuilder builder = new StreamsBuilder();
        builder
                // Materialized view of the users
                .globalTable(topic.name(),
                        Consumed.with(topic.keySerde(), topic.valueSerde()),
                        Materialized.as(Stores.inMemoryKeyValueStore(storeName)));

        // Create config
        Properties config = new Properties();
        config.putAll(defaultConfig);
        config.putAll(defaultStreamsConfig(bootstrapServers, stateDir, SERVICE_APP_ID, true));

        // Build streams
        return new KafkaStreams(builder.build(), config);
    }


    /**
     * Starts all the instances of streams and waits (in parallel) until they are running
     *
     * @param streamsArray the streams object
     * @param timeout timeout to wait for the streams to start, in milliseconds
     * @throws RuntimeException if the streams take more than timeout to start
     */
    protected void startStreams(KafkaStreams[] streamsArray, Long timeout) {
        if (streamsArray.length == 0) {
            return;
        }

        final CountDownLatch startLatch = new CountDownLatch(streamsArray.length);

        // Set up the callback for all the streams instances
        log.info("Starting streams...");
        for (KafkaStreams streams : streamsArray) {
            streams.cleanUp(); // TODO: Remove this in production

            // We want to wait until we transition to running
            streams.setStateListener((newState, oldState) -> {
                if (newState == KafkaStreams.State.RUNNING && oldState != KafkaStreams.State.RUNNING) {
                    startLatch.countDown();
                }
            });

            // Give the start signal
            streams.start();
        }

        // Wait for the streams to start
        try {
            if (!startLatch.await(timeout, TimeUnit.MILLISECONDS)) {
                throw new RuntimeException("Streams are still re-balancing after " + timeout / 1e3 + " seconds");
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}
