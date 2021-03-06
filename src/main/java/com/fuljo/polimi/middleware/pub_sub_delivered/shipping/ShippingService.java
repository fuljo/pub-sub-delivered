package com.fuljo.polimi.middleware.pub_sub_delivered.shipping;

import com.fuljo.polimi.middleware.pub_sub_delivered.exceptions.WebServiceException;
import com.fuljo.polimi.middleware.pub_sub_delivered.microservices.AbstractWebService;
import com.fuljo.polimi.middleware.pub_sub_delivered.microservices.AuthenticationHelper;
import com.fuljo.polimi.middleware.pub_sub_delivered.model.avro.Order;
import com.fuljo.polimi.middleware.pub_sub_delivered.model.avro.OrderState;
import com.fuljo.polimi.middleware.pub_sub_delivered.model.avro.User;
import com.fuljo.polimi.middleware.pub_sub_delivered.model.avro.UserRole;
import com.fuljo.polimi.middleware.pub_sub_delivered.topics.Schemas;
import org.apache.commons.cli.CommandLine;
import org.apache.commons.cli.DefaultParser;
import org.apache.commons.cli.HelpFormatter;
import org.apache.commons.cli.Options;
import org.apache.kafka.clients.producer.KafkaProducer;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.streams.KafkaStreams;
import org.apache.kafka.streams.KeyValue;
import org.apache.kafka.streams.StoreQueryParameters;
import org.apache.kafka.streams.StreamsBuilder;
import org.apache.kafka.streams.kstream.Consumed;
import org.apache.kafka.streams.kstream.Produced;
import org.apache.kafka.streams.state.QueryableStoreTypes;
import org.apache.kafka.streams.state.ReadOnlyKeyValueStore;
import org.glassfish.jersey.server.ManagedAsync;

import javax.ws.rs.*;
import javax.ws.rs.container.AsyncResponse;
import javax.ws.rs.container.Suspended;
import javax.ws.rs.core.Cookie;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import java.util.List;
import java.util.Objects;
import java.util.Properties;
import java.util.UUID;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.StreamSupport;

import static com.fuljo.polimi.middleware.pub_sub_delivered.topics.Schemas.Topics.*;

@Path("api/shipping-service")
public class ShippingService extends AbstractWebService {

    protected static final String CALL_TIMEOUT = "10000";
    protected static final String USERS_STORE_NAME = "users-store";
    protected static final String SHIPMENTS_STORE_NAME = "shipments-store";
    protected static final Pattern ADDRESS_PATTERN = Pattern.compile("^" +
            "(via|corso|viale|piazza)\\s" + // denomination
            "([a-zA-Z][a-zA-Z\\s]*),\\s?" + // name
            "(\\d+),\\s?" + // civic number
            "(\\d{5})\\s" + // postal code
            "([a-zA-Z][a-zA-Z ]*)\\s" + // city name
            "([A-Z]{2})$"); // province

    private KafkaProducer<String, Order> shipmentProducer;
    private KafkaStreams streams;

    /**
     * Instantiate the service
     *
     * @param host hostname to listen on for REST
     * @param port port to listen on for REST
     */
    public ShippingService(final String host, final int port) {
        super(host, port);
    }

    @Override
    public void start(String bootstrapServers, String stateDir, String replicaId, Properties defaultConfig) {
        // Create all topics read or written by this service
        createTopics(new Schemas.Topic[]{USERS, ORDERS, SHIPMENTS}, bootstrapServers, defaultConfig);

        // Create the producer for shipments
        shipmentProducer = createTransactionalProducer(
                bootstrapServers,
                String.format("%s-%s", SERVICE_APP_ID, SHIPMENTS.name()),
                String.format("%s-%s-%s", SERVICE_APP_ID, SHIPMENTS.name(), replicaId),
                SHIPMENTS.keySerde(), SHIPMENTS.valueSerde(),
                defaultConfig);

        // Define the streams' topology
        StreamsBuilder builder = new StreamsBuilder();
        createMaterializedView(builder, USERS, USERS_STORE_NAME, false);
        createMaterializedView(builder, SHIPMENTS, SHIPMENTS_STORE_NAME, false);
        createShipmentValidationStream(builder);

        // Build and start the streams
        streams = createStreams(builder.build(), bootstrapServers, stateDir, SERVICE_APP_ID, defaultConfig);
        startStreams(new KafkaStreams[]{streams}, STREAMS_TIMEOUT);

        // Start the web server to provide the REST API
        jettyServer = startJetty(port, this);

        log.info("Started service {}", SERVICE_APP_ID);
        log.info("{} service listening at {}", SERVICE_APP_ID, jettyServer.getURI());
    }

    @Override
    public void stop() {
        // Close streams and producers
        for (AutoCloseable c : new AutoCloseable[]{streams, shipmentProducer}) {
            try {
                c.close();
            } catch (final Exception e) {
                log.error("Error while closing service " + SERVICE_APP_ID, e);
            }
        }

        if (jettyServer != null) {
            try {
                jettyServer.stop();
            } catch (final Exception e) {
                log.error("Error while closing Jetty for service " + SERVICE_APP_ID, e);
            }
        }
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
     * Returns the shipments' store
     *
     * @return read-only key-value store
     */
    private ReadOnlyKeyValueStore<String, Order> shipmentsStore() {
        return streams.store(StoreQueryParameters.fromNameAndType(SHIPMENTS_STORE_NAME, QueryableStoreTypes.keyValueStore()));
    }

    /**
     * Create a stream that gets validated orders, validates them and puts them in the shipments topic
     * <p>
     * Only VALIDATED orders are considered from the input stream.
     * <p>
     * A shipment is considered valid if and only if
     * <ul>
     *     <li>its address matches a particular pattern</li>
     * </ul>
     * <p>
     * The state is changed to SHIPPING or FAILED accordingly.
     *
     * @param builder streams builder
     */
    private void createShipmentValidationStream(StreamsBuilder builder) {
        builder
                // stream from orders topic (partition-wise
                .stream(ORDERS.name(), Consumed.with(ORDERS.keySerde(), ORDERS.valueSerde()))
                // filter validated orders
                .filter((id, order) -> Objects.equals(order.getState(), OrderState.VALIDATED))
                // validate address and change state
                .transformValues(ShipmentValidator::new)
                // write to shipments topic
                .to(SHIPMENTS.name(), Produced.with(SHIPMENTS.keySerde(), SHIPMENTS.valueSerde()));
    }

    /**
     * HTTP handler for getting the complete collection of shipments.
     * <p>
     * Will return a (possibly empty) JSON array
     *
     * @param authCookie authentication cookie, must belong to a delivery man to succeed
     * @return the response
     */
    @GET
    @Path("shipments")
    @Produces({MediaType.APPLICATION_JSON, MediaType.TEXT_PLAIN})
    public Response getShipmentsHandler(
            @CookieParam(AuthenticationHelper.AUTH_COOKIE) Cookie authCookie
    ) {
        // Check that the user is a delivery man
        AuthenticationHelper.authenticateUser(usersStore(), authCookie, UserRole.DELIVERY);

        // Create iterable from store
        Iterable<KeyValue<String, Order>> iterable = () -> shipmentsStore().all();
        List<ShipmentBean> shipments = StreamSupport
                // stream from iterator
                .stream(iterable.spliterator(), false)
                // only retain key
                .map(kv -> kv.value)
                // convert to bean, so it can be serialized
                .map(ShipmentBean::toBean)
                // collect to list
                .collect(Collectors.toList());
        // Respond with a JSON array
        return Response.ok(shipments).build();
    }

    /**
     * HTTP handler for getting a specific shipment.
     * <p>
     * Will return a JSON object
     *
     * @param id         id of the shipment to retrieve (same as order)
     * @param authCookie authentication cookie, must belong to a delivery man to succeed
     * @return the response
     */
    @GET
    @Path("shipments/{id}")
    @Produces({MediaType.APPLICATION_JSON, MediaType.TEXT_PLAIN})
    public Response getShipmentHandler(
            @PathParam("id") final String id,
            @CookieParam(AuthenticationHelper.AUTH_COOKIE) Cookie authCookie
    ) {
        // Check that the user is a delivery man
        AuthenticationHelper.authenticateUser(usersStore(), authCookie, UserRole.DELIVERY);

        // Get from store
        Order shipment = shipmentsStore().get(id);
        if (shipment == null) { // not found
            throw new WebServiceException("Shipment not found", Response.Status.NOT_FOUND);
        }

        // Convert to bean and respond
        return Response.ok(ShipmentBean.toBean(shipment)).build();
    }

    /**
     * HTTP handler for notifying a completed shipment.
     * <p>
     *
     * @param id         id of the shipment (same as order)
     * @param authCookie authentication cookie, must belong to a delivery man to succeed
     * @param timeout    timeout for the request
     * @param response   asynchronous response
     */
    @POST
    @ManagedAsync
    @Path("shipments/{id}/notify-shipped")
    @Produces({MediaType.APPLICATION_JSON, MediaType.TEXT_PLAIN})
    public void notifyShippedHandler(
            @PathParam("id") final String id,
            @CookieParam(AuthenticationHelper.AUTH_COOKIE) Cookie authCookie,
            @QueryParam("timeout") @DefaultValue(CALL_TIMEOUT) Long timeout,
            @Suspended final AsyncResponse response
    ) {
        setResponseTimeout(response, timeout);

        // Check that the user is a delivery man
        AuthenticationHelper.authenticateUser(usersStore(), authCookie, UserRole.DELIVERY);

        // Get the shipment
        final Order shipment = shipmentsStore().get(id);
        if (shipment == null) { // not found
            throw new WebServiceException("Shipment not found", Response.Status.NOT_FOUND);
        }
        if (!Objects.equals(shipment.getState(), OrderState.SHIPPING)) { // invalid state transition
            throw new WebServiceException(
                    "The shipment is in state " + shipment.getState() + " where it can't be marked as shipped",
                    Response.Status.BAD_REQUEST);
        }

        // Modify the state
        final Order updatedShipment = Order.newBuilder(shipment)
                .setState(OrderState.SHIPPED)
                .build();

        synchronized (shipmentProducer) { // avoid interference between different transactions of the same producer
            // Send record and respond when finished
            produceNewRecordWithTransaction(
                    shipmentProducer,
                    new ProducerRecord<>(SHIPMENTS.name(), id, updatedShipment),
                    response,
                    () -> Response
                            .ok(ShipmentBean.toBean(updatedShipment))
                            .build()
            );
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
            new HelpFormatter().printHelp("Shipping Service", opts);
            return;
        }

        // Get the config options or set defaults
        final String bootstrapServers = cli.getOptionValue("bootstrap-servers", DEFAULT_BOOTSTRAP_SERVERS);
        final String restHostname = cli.getOptionValue("hostname", "localhost");
        final int restPort = Integer.parseInt(cli.getOptionValue("port", "80"));
        final String stateDir = cli.getOptionValue("state-dir", "/tmp/kafka-streams");
        final String replicaId = cli.getOptionValue("replica-id", UUID.randomUUID().toString());
        final Properties defaultConfig =
                buildPropertiesFromConfigFile(cli.getOptionValue("config-file", null));
        final String schemaRegistryUrl = cli.getOptionValue("schema-registry", DEFAULT_SCHEMA_REGISTRY_URL);

        // Configure serializers/deserializers
        Schemas.configureSerdes(schemaRegistryUrl);

        // Create and start the service
        final ShippingService service = new ShippingService(restHostname, restPort);
        service.start(bootstrapServers, stateDir, replicaId, defaultConfig);
        addShutdownHookAndBlock(service);
    }
}
