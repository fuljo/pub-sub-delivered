package com.fuljo.polimi.middleware.pub_sub_delivered.users;

import com.fuljo.polimi.middleware.pub_sub_delivered.microservices.AbstractWebService;
import com.fuljo.polimi.middleware.pub_sub_delivered.model.avro.User;
import com.fuljo.polimi.middleware.pub_sub_delivered.topics.Schemas;
import com.fuljo.polimi.middleware.pub_sub_delivered.topics.Schemas.Topic;
import com.fuljo.polimi.middleware.pub_sub_delivered.topics.Schemas.Topics;
import org.apache.commons.cli.CommandLine;
import org.apache.commons.cli.DefaultParser;
import org.apache.commons.cli.HelpFormatter;
import org.apache.commons.cli.Options;
import org.apache.kafka.clients.producer.KafkaProducer;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.clients.producer.ProducerRecord;

import javax.ws.rs.GET;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;
import javax.ws.rs.core.MediaType;
import java.util.Properties;


@Path("api/users")
public class UsersService extends AbstractWebService {

    private static final String CALL_TIMEOUT = "10000";
    private static final String USERS_STORE_NAME = "orders-store";

    // TODO: Add Kafka streams and producer
    KafkaProducer<String, User> userProducer;

    /**
     * Instantiate the service
     *
     * @param host hostname to listen on for REST
     * @param port port to listen on for REST
     */
    public UsersService(final String host, final int port) {
        super(host, port);
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
     * Start the service from command line
     *
     * @param args command line arguments
     */
    public static void main(String[] args) throws Exception {
        // Parse command line arguments
        final Options opts = new Options();
        AbstractWebService.addCliOptions(opts);
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
