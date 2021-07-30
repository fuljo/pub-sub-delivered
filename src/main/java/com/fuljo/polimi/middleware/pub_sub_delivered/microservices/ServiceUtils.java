package com.fuljo.polimi.middleware.pub_sub_delivered.microservices;

import org.apache.commons.cli.Option;
import org.apache.commons.cli.Options;
import org.eclipse.jetty.server.Server;
import org.eclipse.jetty.servlet.ServletContextHandler;
import org.eclipse.jetty.servlet.ServletHolder;
import org.glassfish.jersey.jackson.JacksonFeature;
import org.glassfish.jersey.server.ResourceConfig;
import org.glassfish.jersey.servlet.ServletContainer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.Properties;

public class ServiceUtils {

    private static final Logger log = LoggerFactory.getLogger(ServiceUtils.class);

    public static final String DEFAULT_BOOTSTRAP_SERVERS = "localhost:9092";

    /**
     * Builds a Properties object from a property file
     *
     * @param configFile java properties file
     * @return properties object, empty if configFile is null
     * @throws IOException if the file does not exist
     */
    public static Properties buildPropertiesFromConfigFile(final String configFile) throws IOException {
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
     * Start Jetty web server
     *
     * @param port    port to listen on
     * @param binding object with annotated handlers
     * @return the started server
     */
    public static Server startJetty(final int port, final Object binding) {
        // Create servlet context handler
        final ServletContextHandler ctx = new ServletContextHandler(ServletContextHandler.SESSIONS);
        ctx.setContextPath("/");

        // Create web server
        final Server jettyServer = new Server(port);
        jettyServer.setHandler(ctx);

        // Register the resources
        final ResourceConfig rc = new ResourceConfig();
        rc.register(binding);
        rc.register(JacksonFeature.class);

        // Initialize the servlet container and holder
        final ServletContainer sc = new ServletContainer(rc);
        final ServletHolder holder = new ServletHolder(sc);

        // Add Jersey servlet to Jetty container
        ctx.addServlet(holder, "/*");

        try {
            jettyServer.start();
        } catch (final Exception e) {
            throw new RuntimeException(e);
        }
        log.debug("Listening on {}", jettyServer.getURI());
        return jettyServer;
    }

    /**
     * Add hooks to gracefully shut down the service, blocks the current thread
     * <p>
     * Blocking might be needed to keep a web server running
     *
     * @param service create hooks for this service
     * @throws InterruptedException when current thread is interrupted
     */
    public static void addShutdownHookAndBlock(final Service service) throws InterruptedException {
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
     * Creates an Options object with specification of CLI options, including HTTP hostname and port
     *
     * @return options specification
     */
    public static Options createWebServiceOptions() {
        final Options opts = new Options();
        opts
                .addOption(Option.builder("b")
                        .longOpt("bootstrap-servers").hasArg().desc("Kafka cluster bootstrap server string").build())
                .addOption(Option.builder("s")
                        .longOpt("schema-registry").hasArg().desc("Schema Registry URL").build())
                .addOption(Option.builder("h")
                        .longOpt("hostname").hasArg().desc("HTTP hostname for this service").build())
                .addOption(Option.builder("p")
                        .longOpt("port").hasArg().desc("HTTP port for this service").build())
                .addOption(Option.builder("c")
                        .longOpt("config-file").hasArg().desc("Properties file with Kafka configuration").build())
                .addOption(Option.builder("d")
                        .longOpt("state-dir").hasArg().desc("Directory for state storage").build())
                .addOption(Option.builder("h").longOpt("help").hasArg(false).desc("Show help").build());
        return opts;
    }
}
