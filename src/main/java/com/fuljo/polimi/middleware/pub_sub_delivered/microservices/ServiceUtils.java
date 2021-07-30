package com.fuljo.polimi.middleware.pub_sub_delivered.microservices;

import org.eclipse.jetty.server.Server;
import org.eclipse.jetty.servlet.ServletContextHandler;
import org.eclipse.jetty.servlet.ServletHolder;
import org.glassfish.jersey.jackson.JacksonFeature;
import org.glassfish.jersey.server.ResourceConfig;
import org.glassfish.jersey.servlet.ServletContainer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class ServiceUtils {

    private static final Logger log = LoggerFactory.getLogger(ServiceUtils.class);

    public static final String DEFAULT_BOOTSTRAP_SERVERS = "localhost:9092";

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
     *
     * Blocking might be needed to keep a web server running
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
}
