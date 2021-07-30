package com.fuljo.polimi.middleware.pub_sub_delivered.microservices;

import java.util.Properties;

public interface Service {

    // TODO: Add docs
    void start(String bootstrapServers, String stateDir, Properties defaultConfig);

    void stop();
}
