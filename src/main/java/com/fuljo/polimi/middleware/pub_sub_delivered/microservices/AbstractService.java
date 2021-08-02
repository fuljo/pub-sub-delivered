package com.fuljo.polimi.middleware.pub_sub_delivered.microservices;

import com.fuljo.polimi.middleware.pub_sub_delivered.topics.Schemas;
import org.apache.commons.cli.Option;
import org.apache.commons.cli.Options;
import org.apache.kafka.clients.producer.KafkaProducer;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.Properties;

/**
 * Abstract class for a microservice
 */
public abstract class AbstractService implements Service {

    protected final Logger log = LoggerFactory.getLogger(getClass());

    protected final String SERVICE_APP_ID = getClass().getSimpleName();

    public static final String DEFAULT_BOOTSTRAP_SERVERS = "localhost:9092";
    public static final String DEFAULT_SCHEMA_REGISTRY_URL = "http://localhost:8081";


    @Override
    public abstract void start(String bootstrapServers, String stateDir, Properties defaultConfig);

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
                .addOption(Option.builder("h").longOpt("help").hasArg(false).desc("Show help").build());
    }

    /**
     * Creates a new producer
     * @param topic topic to write to (serde for K, V must be already configured)
     * @param bootstrapServers addresses of the bootstrap servers
     * @param defaultConfig default configuration provided by the user
     * @param <K> key type
     * @param <V> value type
     * @return the producer
     */
    protected  <K, V> KafkaProducer<K, V> createProducer(Schemas.Topic<K, V> topic, String bootstrapServers, Properties defaultConfig) {
        final Properties config = new Properties();
        config.putAll(defaultConfig);
        config.put(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers);
        config.put(ProducerConfig.ENABLE_IDEMPOTENCE_CONFIG, "true");
        config.put(ProducerConfig.RETRIES_CONFIG, String.valueOf(Integer.MAX_VALUE));
        config.put(ProducerConfig.ACKS_CONFIG, "all");
        config.put(ProducerConfig.CLIENT_ID_CONFIG, String.format("%s-%s", SERVICE_APP_ID, topic.name()));

        return new KafkaProducer<>(config, topic.keySerde().serializer(), topic.valueSerde().serializer());
    }
}
