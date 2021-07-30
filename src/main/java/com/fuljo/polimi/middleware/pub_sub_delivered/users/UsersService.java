package com.fuljo.polimi.middleware.pub_sub_delivered.users;

import com.fuljo.polimi.middleware.pub_sub_delivered.microservices.Service;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import org.apache.commons.cli.CommandLine;
import org.apache.commons.cli.DefaultParser;
import org.apache.commons.cli.HelpFormatter;
import org.apache.commons.cli.Options;
import org.eclipse.jetty.server.Server;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

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
        // TODO: Add schema registry URL, if we're ever going to use it

        // Create and start the service
        final UsersService service = new UsersService(restHostname, restPort);
        service.start(bootstrapServers, stateDir, defaultConfig);
        addShutdownHookAndBlock(service);
    }
}
