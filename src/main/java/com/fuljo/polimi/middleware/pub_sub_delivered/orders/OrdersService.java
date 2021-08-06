package com.fuljo.polimi.middleware.pub_sub_delivered.orders;

import com.fuljo.polimi.middleware.pub_sub_delivered.exceptions.ValidationException;
import com.fuljo.polimi.middleware.pub_sub_delivered.exceptions.WebServiceException;
import com.fuljo.polimi.middleware.pub_sub_delivered.microservices.AbstractWebService;
import com.fuljo.polimi.middleware.pub_sub_delivered.microservices.AuthenticationHelper;
import com.fuljo.polimi.middleware.pub_sub_delivered.model.avro.*;
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
import java.net.URI;
import java.util.*;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import java.util.stream.StreamSupport;

import static com.fuljo.polimi.middleware.pub_sub_delivered.topics.Schemas.Topics.*;

@Path("api/orders-service")
public class OrdersService extends AbstractWebService {

    protected static final String CALL_TIMEOUT = "10000";
    protected static final String USERS_STORE_NAME = "users-store";
    protected static final String PRODUCTS_STORE_NAME = "products-store";
    protected static final String ORDERS_STORE_NAME = "orders-store";
    protected static final Pattern PRODUCT_ID_PATTERN = Pattern.compile("^[\\w_.-]+$");

    private KafkaProducer<String, Product> productProducer;
    private KafkaProducer<String, Order> orderProducer;
    private KafkaStreams streams;
    private KafkaStreams ordersStoreStreams;

    /**
     * Instantiate the service
     *
     * @param host hostname to listen on for REST
     * @param port port to listen on for REST
     */
    public OrdersService(final String host, final int port) {
        super(host, port);
    }

    @Override
    public void start(String bootstrapServers, String stateDir, String replicaId, Properties defaultConfig) {
        // Create the producer for products
        productProducer = createTransactionalProducer(
                bootstrapServers,
                String.format("%s-%s", SERVICE_APP_ID, PRODUCTS.name()),
                String.format("%s-%s-%s", SERVICE_APP_ID, PRODUCTS.name(), replicaId),
                PRODUCTS.keySerde(), PRODUCTS.valueSerde(),
                defaultConfig);
        // Create the producer for orders
        orderProducer = createTransactionalProducer(
                bootstrapServers,
                String.format("%s-%s", SERVICE_APP_ID, ORDERS.name()),
                String.format("%s-%s-%s", SERVICE_APP_ID, ORDERS.name(), replicaId),
                ORDERS.keySerde(), ORDERS.valueSerde(),
                defaultConfig);

        // Define the streams' topology
        StreamsBuilder builder = new StreamsBuilder();
        createMaterializedView(builder, USERS, USERS_STORE_NAME);
        createMaterializedView(builder, PRODUCTS, PRODUCTS_STORE_NAME);
        createOrderValidationStream(builder);
        createShipmentStatusUpdateStream(builder);

        // Define a separate topology to provide the orders store,
        // since we can't have multiple sources connected to the ORDERS topic
        StreamsBuilder ordersStoreBuilder = new StreamsBuilder();
        createMaterializedView(ordersStoreBuilder, ORDERS, ORDERS_STORE_NAME);

        // Build and start the streams
        streams = createStreams(builder.build(), bootstrapServers, stateDir, defaultConfig);
        ordersStoreStreams = createStreams(ordersStoreBuilder.build(), bootstrapServers, stateDir, defaultConfig);
        startStreams(new KafkaStreams[]{streams, ordersStoreStreams}, STREAMS_TIMEOUT);

        // Start the web server to provide the REST API
        jettyServer = startJetty(port, this);

        log.info("Started service {}", SERVICE_APP_ID);
        log.info("{} service listening at {}", SERVICE_APP_ID, jettyServer.getURI());
    }

    @Override
    public void stop() {
        // Close streams and producers
        for (AutoCloseable c : new AutoCloseable[]{streams, ordersStoreStreams, productProducer, orderProducer}) {
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
     * Returns the products' store
     *
     * @return read-only key-value store
     */
    private ReadOnlyKeyValueStore<String, Product> productsStore() {
        return streams.store(StoreQueryParameters.fromNameAndType(PRODUCTS_STORE_NAME, QueryableStoreTypes.keyValueStore()));
    }

    /**
     * Returns the orders' store
     *
     * @return read-only key-value store
     */
    private ReadOnlyKeyValueStore<String, Order> ordersStore() {
        return ordersStoreStreams.store(StoreQueryParameters.fromNameAndType(ORDERS_STORE_NAME, QueryableStoreTypes.keyValueStore()));
    }

    /**
     * Create a stream that gets newly created orders, validates them and puts them back in the order topic
     * <p>
     * Only CREATED orders are considered from the input stream.
     * <p>
     * An order is considered valid if and only if
     * <ul>
     *     <li>It contains at least one product</li>
     *     <li>All of the requested products are available</li>
     * </ul>
     * <p>
     * The state is changed to VALIDATED or FAILED accordingly.
     *
     * @param builder streams builder
     */
    private void createOrderValidationStream(StreamsBuilder builder) {
        builder
                // stream from orders topic (partition-wise)
                .stream(ORDERS.name(), Consumed.with(ORDERS.keySerde(), ORDERS.valueSerde()))
                // filter created orders
                .filter((id, order) -> Objects.equals(order.getState(), OrderState.CREATED))
                // validate order and change state
                .transformValues(OrderValidator::new)
                // send result to topic
                .to(ORDERS.name(), Produced.with(ORDERS.keySerde(), ORDERS.valueSerde()));
    }


    /**
     * Create a stream that consumes shipments and sets the corresponding status on orders
     *
     * @param builder streams builder
     * @implNote Currently shipments and orders share the same representation, so we perform a simple copy
     */
    private void createShipmentStatusUpdateStream(StreamsBuilder builder) {
        builder
                // stream from shipments topic (partition-wise)
                .stream(SHIPMENTS.name(), Consumed.with(SHIPMENTS.keySerde(), SHIPMENTS.valueSerde()))
                // send to orders
                .to(ORDERS.name(), Produced.with(ORDERS.keySerde(), ORDERS.valueSerde()));
    }

    /**
     * HTTP handler for creating a product.
     * <p>
     * The authentication cookie must belong to an administrator to succeed.
     *
     * @param product    the product to be created
     * @param authCookie authentication cookie
     * @param timeout    timeout for the request
     * @param response   asynchronous response
     */
    @POST
    @ManagedAsync
    @Path("products")
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces({MediaType.APPLICATION_JSON, MediaType.TEXT_PLAIN})
    public void createProductHandler(
            final ProductBean product,
            @CookieParam(AuthenticationHelper.AUTH_COOKIE) final Cookie authCookie,
            @QueryParam("timeout") @DefaultValue(CALL_TIMEOUT) final Long timeout,
            @Suspended final AsyncResponse response
    ) throws ValidationException {
        // Set the timeout
        setResponseTimeout(response, timeout);

        // Check that the user has admin privileges
        AuthenticationHelper.authenticateUser(usersStore(), authCookie, UserRole.ADMIN);

        // Check if product already exists
        if (productsStore().get(product.getId()) != null) { // product already exists
            throw new WebServiceException("Product already exists", Response.Status.CONFLICT);
        }

        // Validate product (throws)
        validateProduct(product);
        Product p = ProductBean.fromBean(product);
        String id = product.getId();

        // Send record and respond when finished
        produceNewRecordWithTransaction(
                productProducer,
                new ProducerRecord<>(PRODUCTS.name(), id, p),
                response,
                () -> Response
                        .created(new URI("/api/orders-service/products/" + id))
                        .entity(ProductBean.toBean(p))
                        .build()
        );
    }

    /**
     * HTTP handler for getting the complete collection of products.
     * <p>
     * Will return a (possibly empty) JSON array
     *
     * @return the response
     */
    @GET
    @Path("products")
    @Produces(MediaType.APPLICATION_JSON)
    public Response getProductsHandler() {
        // Create iterable from store
        Iterable<KeyValue<String, Product>> iterable = () -> productsStore().all();
        List<ProductBean> products = StreamSupport
                // stream from iterator
                .stream(iterable.spliterator(), false)
                // only retain key
                .map(kv -> kv.value)
                // convert to bean, so it can be serialized
                .map(ProductBean::toBean)
                // collect to list
                .collect(Collectors.toList());
        // Respond with a JSON array
        return Response.ok(products).build();
    }

    /**
     * HTTP handler for getting a specific product.
     * <p>
     * Will return a JSON object
     *
     * @return the response
     */
    @GET
    @Path("products/{id}")
    @Produces(MediaType.APPLICATION_JSON)
    public Response getProductHandler(
            @PathParam("id") String id
    ) {
        // Get the specific product
        Product product = productsStore().get(id);
        if (product == null) {
            throw new WebServiceException("Product not found", Response.Status.NOT_FOUND);
        }
        return Response.ok(ProductBean.toBean(product)).build();
    }

    /**
     * HTTP handler for modifying a product.
     * <p>
     * The authentication cookie must belong to an administrator to succeed.
     *
     * @param id         id of the product
     * @param patch      the product with the modification
     * @param authCookie authentication cookie
     * @param timeout    timeout for the request
     * @param response   asynchronous response
     * @implNote We currently support only modifying the availability attribute
     */
    @PATCH
    @ManagedAsync
    @Path("products/{id}")
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces({MediaType.APPLICATION_JSON, MediaType.TEXT_PLAIN})
    public void patchProductHandler(
            @PathParam("id") final String id,
            final ProductBean patch,
            @CookieParam(AuthenticationHelper.AUTH_COOKIE) final Cookie authCookie,
            @QueryParam("timeout") @DefaultValue(CALL_TIMEOUT) final Long timeout,
            @Suspended final AsyncResponse response
    ) {
        // Set the timeout
        setResponseTimeout(response, timeout);

        // Check that the user has admin privileges
        AuthenticationHelper.authenticateUser(usersStore(), authCookie, UserRole.ADMIN);

        // Get the original product
        final Product product = productsStore().get(id);
        if (product == null) {
            throw new WebServiceException("Product not found", Response.Status.NOT_FOUND);
        }

        // Check if the product's availability has been modified
        if (product.getAvailable() != patch.isAvailable()) { // product modified
            // Modify the fields
            product.setAvailable(patch.isAvailable());

            // Send record and respond when finished
            produceNewRecordWithTransaction(
                    productProducer,
                    new ProducerRecord<>(PRODUCTS.name(), id, product),
                    response,
                    () -> Response.ok(ProductBean.toBean(product)).build()
            );
        } else { // not modified
            // Send patched product as a response
            response.resume(Response
                    // add the resource itself to the body
                    .ok(ProductBean.toBean(product))
                    .build());
        }
    }

    /**
     * HTTP handler for creating a new order.
     * <p>
     * The input JSON object is a reduced version of the order, which only contains
     * <ul>
     *     <li>the shipping address</li>
     *     <li>the list of products with quantities</li>
     * </ul>
     * since the other fields are automatically filled by this function
     *
     * @param newOrder   the new order to submit
     * @param authCookie authentication cookie, must belong to a customer for the operation to succeed
     * @param timeout    timeout for the request
     * @param response   async response
     */
    @POST
    @ManagedAsync
    @Path("orders")
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces({MediaType.APPLICATION_JSON, MediaType.TEXT_PLAIN})
    public void createOrderHandler(
            final NewOrderBean newOrder,
            @CookieParam(AuthenticationHelper.AUTH_COOKIE) Cookie authCookie,
            @QueryParam("timeout") @DefaultValue(CALL_TIMEOUT) final Long timeout,
            @Suspended final AsyncResponse response
    ) {
        // Set call timeout
        setResponseTimeout(response, timeout);

        // Check that the user has customer privileges
        final User customer = AuthenticationHelper.authenticateUser(usersStore(), authCookie, UserRole.CUSTOMER);

        // The order will belong to the current customer
        final String customerId = customer.getId().toString();
        // Generate new unique id for the order
        final String id = generateNewOrderId(customerId);
        // Calculate total price and check that products exist
        final double totalPrice;
        try {
            totalPrice = newOrder.getProducts().entrySet().stream()
                    // multiply quantity by price
                    .mapToDouble(e -> e.getValue() * productsStore().get(e.getKey()).getPrice())
                    // sum everything up
                    .sum();
        } catch (NullPointerException e) {
            throw new WebServiceException("One or more requested products do not exist", Response.Status.BAD_REQUEST);
        }

        // Create the actual order object
        final Order order = new Order(
                id,
                customerId,
                newOrder.getShippingAddress(),
                OrderState.CREATED,
                new HashMap<>(newOrder.getProducts()),
                totalPrice
        );

        // Send record and respond when finished
        produceNewRecordWithTransaction(
                orderProducer,
                new ProducerRecord<>(ORDERS.name(), id, order),
                response,
                () -> Response
                        .created(new URI("/api/orders-service/orders/" + id))
                        .entity(OrderBean.toBean(order))
                        .build()
        );
    }

    /**
     * Generate a new unique id for an order.
     * <p>
     * This function also checks that the order doesn't already exist in the store.
     * <p>
     * The format of the order id is customerId:uuid,
     * so we can then do a prefix scan on the store to quickly retrieve orders.
     *
     * @return new order id
     */
    protected String generateNewOrderId(String customerId) {
        String id;
        do {
            id = String.format("%s:%s", customerId, UUID.randomUUID());
        } while (ordersStore().get(id) != null);
        return id;
    }

    /**
     * HTTP handler to retrieve all the orders for a specific customer.
     * <p>
     *
     * @param authCookie authentication cookie, must belong to a customer for the operation to succeed
     */
    @GET
    @Path("orders")
    @Produces(MediaType.APPLICATION_JSON)
    public Response getCustomerOrdersHandler(
            @CookieParam(AuthenticationHelper.AUTH_COOKIE) Cookie authCookie
    ) {
        // Check that the user has customer privileges
        final User customer = AuthenticationHelper.authenticateUser(usersStore(), authCookie, UserRole.CUSTOMER);

        // The order id has a prefix of the format "customerId:"
        final String prefix = String.format("%s:", customer.getId());
        Stream<KeyValue<String, Order>> filteredStream; // filtered by prefix

        // Unfortunately, the underlying store may not support prefix scan
        try { // store supports prefix scan
            Iterable<KeyValue<String, Order>> iterable = () ->
                    ordersStore().prefixScan(prefix, ORDERS.keySerde().serializer());

            filteredStream = StreamSupport
                    // stream from iterator
                    .stream(iterable.spliterator(), false);
        } catch (UnsupportedOperationException e) { // prefix scan not supported => filter manually
            Iterable<KeyValue<String, Order>> iterable = () -> ordersStore().all();

            filteredStream = StreamSupport
                    // stream from iterator
                    .stream(iterable.spliterator(), false)
                    // filter by customer
                    .filter(kv -> kv.key.startsWith(prefix));
        }


        // Keep only the values and convert them to beans, so they can be serialized
        List<OrderBean> orders = filteredStream
                // only retain value
                .map(kv -> kv.value)
                // convert to bean, so it can be serialized
                .map(OrderBean::toBean)
                // collect to list
                .collect(Collectors.toList());
        // Respond with a JSON array
        return Response.ok(orders).build();
    }

    /**
     * HTTP handler to retrieve a specific order.
     * <p>
     *
     * @param id         id of the order to retrieve
     * @param authCookie authentication cookie, must belong to the customer owning the order for the operation to succeed
     */
    @GET
    @Path("orders/{id}")
    @Produces(MediaType.APPLICATION_JSON)
    public Response getOrderHandler(
            @PathParam("id") final String id,
            @CookieParam(AuthenticationHelper.AUTH_COOKIE) Cookie authCookie
    ) {
        // Check that the user has customer privileges
        final User customer = AuthenticationHelper.authenticateUser(usersStore(), authCookie, UserRole.CUSTOMER);

        // Get the order
        final Order order = ordersStore().get(id);

        // Check that the order exists and belongs to the authenticated customer
        if (order == null) { // not found
            throw new WebServiceException(
                    "Order not found", Response.Status.NOT_FOUND);
        }
        if (!order.getCustomerId().equals(customer.getId())) { // not owned
            throw new WebServiceException(
                    "Order does not belong to the authenticated customer", Response.Status.FORBIDDEN);
        }

        // Return the order
        return Response.ok(OrderBean.toBean(order)).build();
    }

    /**
     * Validates a given product bean
     *
     * @param product product bean
     */
    private void validateProduct(ProductBean product) throws ValidationException {
        if (!PRODUCT_ID_PATTERN.matcher(product.getId()).matches()) {
            throw new ValidationException("Invalid id format");
        }
        if (product.getName().trim().length() == 0) {
            throw new ValidationException("Name cannot be empty");
        }
        if (product.getDescription().trim().length() == 0) {
            throw new ValidationException("Description cannot be empty");
        }
        if (product.getPrice() < 0) {
            throw new ValidationException("Price cannot be negative");
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
            new HelpFormatter().printHelp("Orders Service", opts);
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
        final OrdersService service = new OrdersService(restHostname, restPort);
        service.start(bootstrapServers, stateDir, replicaId, defaultConfig);
        addShutdownHookAndBlock(service);
    }
}
