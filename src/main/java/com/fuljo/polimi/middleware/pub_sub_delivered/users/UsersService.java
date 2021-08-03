package com.fuljo.polimi.middleware.pub_sub_delivered.users;

import com.fuljo.polimi.middleware.pub_sub_delivered.microservices.AbstractWebService;
import com.fuljo.polimi.middleware.pub_sub_delivered.microservices.ServiceUtils;
import com.fuljo.polimi.middleware.pub_sub_delivered.model.avro.User;
import com.fuljo.polimi.middleware.pub_sub_delivered.model.beans.UserBean;
import com.fuljo.polimi.middleware.pub_sub_delivered.topics.Schemas;
import org.apache.commons.cli.CommandLine;
import org.apache.commons.cli.DefaultParser;
import org.apache.commons.cli.HelpFormatter;
import org.apache.commons.cli.Options;
import org.apache.kafka.clients.producer.KafkaProducer;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.common.KafkaException;
import org.apache.kafka.common.errors.AuthorizationException;
import org.apache.kafka.common.errors.OutOfOrderSequenceException;
import org.apache.kafka.common.errors.ProducerFencedException;
import org.apache.kafka.streams.KafkaStreams;
import org.apache.kafka.streams.StoreQueryParameters;
import org.apache.kafka.streams.StreamsBuilder;
import org.apache.kafka.streams.kstream.Consumed;
import org.apache.kafka.streams.kstream.Materialized;
import org.apache.kafka.streams.state.QueryableStoreTypes;
import org.apache.kafka.streams.state.ReadOnlyKeyValueStore;
import org.apache.kafka.streams.state.Stores;
import org.glassfish.jersey.server.ManagedAsync;

import javax.validation.ValidationException;
import javax.ws.rs.*;
import javax.ws.rs.container.AsyncResponse;
import javax.ws.rs.container.Suspended;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.Properties;
import java.util.regex.Pattern;

import static com.fuljo.polimi.middleware.pub_sub_delivered.topics.Schemas.Topics.USERS;


@Path("api/users-service")
public class UsersService extends AbstractWebService {

    private static final String CALL_TIMEOUT = "10000";
    private static final String USERS_STORE_NAME = "users-store";
    private static final Pattern USER_ID_PATTERN = Pattern.compile("^[\\w_.-]{5,20}$");
    private static final Pattern EMAIL_PATTERN = Pattern.compile("^[a-zA-Z0-9_!#$%&’*+/=?`{|}~^.-]+@[a-zA-Z0-9.-]+$");

    private KafkaProducer<String, User> userProducer;
    private KafkaStreams streams;


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
    public void start(String bootstrapServers, String stateDir, String replicaId, Properties defaultConfig) {

        // Create the producer
        userProducer = createTransactionalProducer(
                bootstrapServers,
                String.format("%s-%s", SERVICE_APP_ID, USERS.name()),
                String.format("%s-%s-%s", SERVICE_APP_ID, USERS.name(), replicaId),
                USERS.keySerde(), USERS.valueSerde(),
                defaultConfig);

        // Create the streams
        streams = createStreams(bootstrapServers, stateDir, defaultConfig);
        startStreams(streams, STREAMS_TIMEOUT);

        // Start the web server to provide the REST API
        jettyServer = startJetty(port, this);

        log.info("Started service {}", SERVICE_APP_ID);
        log.info("{} service listening at {}", SERVICE_APP_ID, jettyServer.getURI());
    }

    @Override
    public void stop() {
        if (streams != null) {
            streams.close();
        }
        if (userProducer != null) {
            userProducer.close();
        }

        if (jettyServer != null) {
            try {
                jettyServer.stop();
            } catch (final Exception e) {
                e.printStackTrace();
            }
        }
    }

    /**
     * Create streams for this service
     * <p>
     *
     * @param bootstrapServers urls of boostrap servers
     * @param stateDir         local directory to checkpoint state
     * @return the configuration
     * @implNote We create a GlobalKTable to retrieve the users, which is more costly than a regular KTable.
     * A solution which scales better would be to build a KTable for each partition:
     * <ul>
     *     <li>requests for local keys are handled locally</li>
     *     <li>requests for non-local keys are routed towards the other replicas</li>
     * </ul>
     * However, we would need to manually set up an RPC layer to discover and communicate to other partitions,
     * which has a non-negligible programming cost and is out of the scope of this project.
     * @implNote We use a non-persisted in-memory store for the users, since the USERS topic is already persisted,
     * and the USERS table can be computed from it.
     */
    private KafkaStreams createStreams(String bootstrapServers,
                                       String stateDir,
                                       Properties defaultConfig) {
        // Define topology
        StreamsBuilder builder = new StreamsBuilder();
        builder
                // Materialized view of the users
                .globalTable(USERS.name(),
                        Consumed.with(USERS.keySerde(), USERS.valueSerde()),
                        Materialized.as(Stores.inMemoryKeyValueStore(USERS_STORE_NAME)));

        // Create config
        Properties config = new Properties();
        config.putAll(defaultConfig);
        config.putAll(defaultStreamsConfig(bootstrapServers, stateDir, SERVICE_APP_ID, false));

        // Build streams
        return new KafkaStreams(builder.build(), config);
    }

    /**
     * Returns the users' store
     *
     * @return read-only key-value store
     */
    private ReadOnlyKeyValueStore<String, User> usersStore() {
        return streams.store(StoreQueryParameters.fromNameAndType(USERS_STORE_NAME, QueryableStoreTypes.keyValueStore()));
    }

    /**
     * HTTP handler for registering a new user
     *
     * @param user     user to be created
     * @param timeout  timeout to wait for the request (optional)
     * @param response asynchronous response
     */
    @POST
    @ManagedAsync
    @Path("users")
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces({MediaType.APPLICATION_JSON, MediaType.TEXT_PLAIN})
    public void registerUserHandler(
            final UserBean user,
            @QueryParam("timeout") @DefaultValue(CALL_TIMEOUT) final Long timeout,
            @Suspended final AsyncResponse response
    ) {
        // Set the timeout
        setResponseTimeout(response, timeout);

        // Validate user data
        try {
            validateUser(user);
        } catch (ValidationException e) {
            response.resume(Response
                    .status(Response.Status.BAD_REQUEST)
                    .entity(e.getMessage())
                    .build());
            return;
        }

        // Create the user if another one with the same name does not exist
        String id = user.getId();
        if (usersStore().get(id) == null) { // user does not exist => create
            // Use a transaction
            try {
                final User u = UserBean.fromBean(user);
                userProducer.beginTransaction();
                // Send record and respond when finished
                userProducer.send(new ProducerRecord<>(USERS.name(), id, u), (recordMetadata, e) -> {
                    // This is a callback after the record has been inserted and committed
                    if (e != null) { // exception
                        response.resume(e.getMessage());
                    } else { // user successfully created
                        try {
                            response.resume(Response
                                    // build URL to the new resource
                                    .created(new URI("/api/users/user/" + id))
                                    // add the resource itself to the body
                                    .entity(UserBean.toBean(u))
                                    .build());
                        } catch (URISyntaxException ex) {
                            response.resume(ex.getMessage());
                        }
                    }
                });
                userProducer.commitTransaction();
            } catch (ProducerFencedException | OutOfOrderSequenceException | AuthorizationException e) {
                // Unrecoverable exception => close the service
                log.error("The producer suffered an unrecoverable error, stopping service " + SERVICE_APP_ID, e);
                this.stop();
                // TODO: Maybe exit(1)
            } catch (KafkaException e) {
                // Abort transaction and try again
                log.warn("Registration of user \"" + id + "\" aborted", e);
                userProducer.abortTransaction();
            }
        } else { // user exists => error
            response.resume(
                    Response.status(Response.Status.BAD_REQUEST)
                            .entity("User \"" + id + "\" already exists")
                            .build()
            );
        }
    }

    /**
     * HTTP handler for retrieving specific user's data
     *
     * @param id id of the user
     * @return the response
     */
    @GET
    @Path("users/{id}")
    @Produces({MediaType.APPLICATION_JSON, MediaType.TEXT_PLAIN})
    public Response getUserHandler(
            @PathParam("id") final String id
    ) {
        User user = usersStore().get(id);
        if (user != null) { // user found
            return Response.ok(UserBean.toBean(user)).build();
        } else { // user not found
            return Response.status(Response.Status.NOT_FOUND).entity("User not found").build();
        }
    }

    /**
     * HTTP user login handler
     * <p>
     * Sets an authentication cookie on the User-Agent upon successful login
     *
     * @param id id of the user
     * @return response
     */
    @POST
    @Path("login")
    @Consumes(MediaType.APPLICATION_FORM_URLENCODED)
    public Response loginHandler(
            @FormParam("user-id") String id
    ) {
        // Check if the user exists
        User user = usersStore().get(id);
        if (user != null) { // successful login
            // Generate cookie with auth token
            return Response.ok().cookie(ServiceUtils.generateAuthCookie(id)).build();
        } else { // not authorized
            return Response.status(Response.Status.UNAUTHORIZED).entity("User not found").build();
        }
    }

    /**
     * Validates a given user bean
     *
     * @param user user bean
     * @throws ValidationException if the user is not valid. The message contains the reason.
     */
    void validateUser(UserBean user) {
        // Validate id
        if (!USER_ID_PATTERN.matcher(user.getId()).matches()) {
            throw new ValidationException(
                    "Invalid User: The user id shall be between 5 and 20 characters long. " +
                    "Only alphanumerical characters, hyphens and underscores are allowed");
        }
        // Validate name
        if (user.getName().trim().length() == 0) {
            throw new ValidationException("The name cannot be empty");
        }
        // Validate email
        if (!EMAIL_PATTERN.matcher(user.getEmail()).matches()) {
            throw new ValidationException(
                    "Invalid User: The email does not meet the requirements of RFC 5322");
        }
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
        final String replicaId = cli.getOptionValue("replica-id", "1");
        final Properties defaultConfig =
                buildPropertiesFromConfigFile(cli.getOptionValue("config-file", null));
        final String schemaRegistryUrl = cli.getOptionValue("schema-registry", DEFAULT_SCHEMA_REGISTRY_URL);

        // Configure serializers/deserializers
        Schemas.configureSerdes(schemaRegistryUrl);

        // Create and start the service
        final UsersService service = new UsersService(restHostname, restPort);
        service.start(bootstrapServers, stateDir, replicaId, defaultConfig);
        addShutdownHookAndBlock(service);
    }
}
