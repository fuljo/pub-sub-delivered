package com.fuljo.polimi.middleware.pub_sub_delivered.microservices;

import org.apache.commons.cli.Option;
import org.apache.commons.cli.Options;
import org.eclipse.jetty.server.Server;
import org.eclipse.jetty.servlet.ServletContextHandler;
import org.eclipse.jetty.servlet.ServletHolder;
import org.glassfish.jersey.jackson.JacksonFeature;
import org.glassfish.jersey.server.ResourceConfig;
import org.glassfish.jersey.servlet.ServletContainer;

import javax.ws.rs.container.AsyncResponse;
import javax.ws.rs.core.Response;
import java.util.concurrent.TimeUnit;

/**
 * Abstract class for a microservice with web capabilities
 */
public abstract class AbstractWebService extends AbstractService {

    protected Server jettyServer;
    protected final String host;
    protected int port;

    /**
     * Instantiate the service
     *
     * @param host hostname to listen on for REST
     * @param port port to listen on for REST
     */
    protected AbstractWebService(final String host, final int port) {
        super();
        this.host = host;
        this.port = port;
    }

    /**
     * Start Jetty web server
     *
     * @param port     port to listen on
     * @param resource annotated resource
     * @return the started server
     */
    protected Server startJetty(final int port, final Object resource) {
        // Create servlet context handler
        final ServletContextHandler ctx = new ServletContextHandler(ServletContextHandler.SESSIONS);
        ctx.setContextPath("/");

        // Create web server
        final Server jettyServer = new Server(port);
        jettyServer.setHandler(ctx);

        // Register the resources
        final ResourceConfig rc = new ResourceConfig();
        rc.register(resource);
        rc.register(JacksonFeature.class);
        // Automatically discover exceptions
        rc.packages("com.fuljo.polimi.middleware.pub_sub_delivered.exceptions");

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
     * Creates an Options object with specification of CLI options
     *
     * @param options options objects to append to
     */
    protected static void addCliOptions(Options options) {
        AbstractService.addCliOptions(options);
        options
                .addOption(Option.builder("h")
                        .longOpt("hostname").hasArg().desc("HTTP hostname for this service").build())
                .addOption(Option.builder("p")
                        .longOpt("port").hasArg().desc("HTTP port for this service").build());
    }

    /**
     * Sets the timeout of an asynchronous response, and responds if it expires
     *
     * @param response async response
     * @param timeout  timeout in milliseconds
     */
    protected void setResponseTimeout(AsyncResponse response, Long timeout) {
        response.setTimeout(timeout, TimeUnit.MILLISECONDS);
        response.setTimeoutHandler(res -> {
            res.resume(
                    Response.status(Response.Status.GATEWAY_TIMEOUT)
                            .entity("Response timed out after " + timeout + " ms.")
                            .build()
            );
        });
    }
}
