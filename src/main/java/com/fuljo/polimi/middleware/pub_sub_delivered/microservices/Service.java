package com.fuljo.polimi.middleware.pub_sub_delivered.microservices;

import java.util.Properties;

public interface Service {

    /**
     * Start the service, including:
     *
     * <ul>
     *     <li>Initializing producers/consumers</li>
     *     <li>Initializing streams</li>
     *     <li>Starting the REST web server, if used</li>
     * </ul>
     * @param bootstrapServers urls of bootstrap servers
     * @param stateDir directory where to sae state
     * @param defaultConfig default configuration values for Kafka
     */
    void start(String bootstrapServers, String stateDir, String replicaId, Properties defaultConfig);

    /**
     * Stop the service gracefully
     */
    void stop();
}
