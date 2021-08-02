package com.fuljo.polimi.middleware.pub_sub_delivered.users;

import com.fuljo.polimi.middleware.pub_sub_delivered.microservices.Service;
import com.fuljo.polimi.middleware.pub_sub_delivered.model.avro.User;
import com.fuljo.polimi.middleware.pub_sub_delivered.topics.Schemas;
import com.fuljo.polimi.middleware.pub_sub_delivered.topics.Schemas.Topic;
import com.fuljo.polimi.middleware.pub_sub_delivered.topics.Schemas.Topics;
import io.confluent.kafka.serializers.AbstractKafkaSchemaSerDeConfig;
import org.apache.commons.cli.CommandLine;
import org.apache.commons.cli.DefaultParser;
import org.apache.commons.cli.HelpFormatter;
import org.apache.commons.cli.Options;
import org.apache.kafka.clients.producer.KafkaProducer;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.eclipse.jetty.server.Server;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.ws.rs.GET;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;
import javax.ws.rs.core.MediaType;
import java.util.Properties;

import static com.fuljo.polimi.middleware.pub_sub_delivered.microservices.ServiceUtils.*;

@Path("api/users")
public class UsersService implements Service {

    public static final Logger log = LoggerFactory.getLogger(UsersService.class);

    private static final String CALL_TIMEOUT = "10000";
    private static final String ORDERS_STORE_NAME = "orders-store";
    private final String SERVICE_APP_ID = getClass().getSimpleName();

    // TODO: Add back client, if needed
    private Server jettyServer;
    private final String host;
    private int port;

    // TODO: Add Kafka streams and producer
    KafkaProducer<String, User> userProducer;

    /**
     * Instantiate the service
     *
     * @param host hostname to listen on for REST
     * @param port port to listen on for REST
     */
    public UsersService(final String host, final int port) {
        this.host = host;
        this.port = port;
    }

    @Override
    public void start(String bootstrapServers, String stateDir, Properties defaultConfig) {
        jettyServer = startJetty(port, this); // TODO: Implement handlers

        // TODO: Add producer and streams
        final Topic<String, User> users = Topics.USERS;
        userProducer = createProducer(users, bootstrapServers, defaultConfig);
        final User u = new User("fuljo", "Milan");
        final ProducerRecord<String, User> record = new ProducerRecord<>(users.name(), u.getName().toString(), u);
        userProducer.send(record);

        log.info("Started service {}", SERVICE_APP_ID);
        log.info("{} service listening at {}", SERVICE_APP_ID, jettyServer.getURI());
    }

    public <K, V> KafkaProducer<K, V> createProducer(Topic<K, V> topic, String bootstrapServers, Properties defaultConfig) {
        final Properties config = new Properties();
        config.putAll(defaultConfig);
        config.put(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers);
        config.put(ProducerConfig.ENABLE_IDEMPOTENCE_CONFIG, "true");
        config.put(ProducerConfig.RETRIES_CONFIG, String.valueOf(Integer.MAX_VALUE));
        config.put(ProducerConfig.ACKS_CONFIG, "all");
        config.put(ProducerConfig.CLIENT_ID_CONFIG, topic.name() + "-sender");

        return new KafkaProducer<>(config, topic.keySerde().serializer(), topic.valueSerde().serializer());
    }

    @Override
    public void stop() {
        // TODO: Close streams
        // TODO: Close producer

        if (jettyServer != null) {
            try {
                jettyServer.stop();
            } catch (final Exception e) {
                e.printStackTrace();
            }
        }
    }

    /**
     * Handler for GET requests
     *
     * @return response in plaintext format
     */
    @GET
    @Produces(MediaType.TEXT_PLAIN)
    public String getPlaintext() {
        return "Hello world";
    }

    /**
     * Main method to start the service
     *
     * @param args command line arguments
     */
    public static void main(String[] args) throws Exception {
        // Parse command line arguments
        final Options opts = createWebServiceOptions();
        final CommandLine cli = new DefaultParser().parse(opts, args);
        // Handle help text
        if (cli.hasOption("h")) {
            new HelpFormatter().printHelp("Users Service", opts);
            return;
        }

        // Get the config options or set defaults
        final String bootstrapServers = cli.getOptionValue("bootstrap-servers", DEFAULT_BOOTSTRAP_SERVERS);
        final String restHostname = cli.getOptionValue("hostname", "localhost");
        final int restPort = Integer.parseInt(cli.getOptionValue("port", "80"));
        final String stateDir = cli.getOptionValue("state-dir", "/tmp/kafka-streams");
        final Properties defaultConfig =
                buildPropertiesFromConfigFile(cli.getOptionValue("config-file", null));
        final String schemaRegistryUrl = cli.getOptionValue("schema-registry", DEFAULT_SCHEMA_REGISTRY_URL);

        // Configure serializers/deserializers
        Schemas.configureSerdes(schemaRegistryUrl);

        // Create and start the service
        final UsersService service = new UsersService(restHostname, restPort);
        service.start(bootstrapServers, stateDir, defaultConfig);
        addShutdownHookAndBlock(service);
    }
}
