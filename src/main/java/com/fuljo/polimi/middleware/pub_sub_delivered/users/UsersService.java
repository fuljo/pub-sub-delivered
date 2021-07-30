package com.fuljo.polimi.middleware.pub_sub_delivered.users;

import com.fuljo.polimi.middleware.pub_sub_delivered.microservices.Service;
import org.eclipse.jetty.server.Server;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
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

        if(jettyServer != null) {
            try {
                jettyServer.stop();
            } catch (final Exception e) {
                e.printStackTrace();
            }
        }
    }

    /**
     * Handler for GET requests
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
        // TODO: Add CLI options

        final String bootstrapServers = DEFAULT_BOOTSTRAP_SERVERS;
        final String restHostname = "localhost";
        final int restPort = 80;
        final String stateDir = "/tmp/kafka-streams";

        final Properties defaultConfig = new Properties();

        // TODO: Add schema registry URL, if we're ever going to use it

        final UsersService service = new UsersService(restHostname, restPort);
        service.start(bootstrapServers, stateDir, defaultConfig);
        addShutdownHookAndBlock(service);
    }
}
