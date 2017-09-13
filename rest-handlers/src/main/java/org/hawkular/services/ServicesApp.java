package org.hawkular.services;

import org.hawkular.commons.log.MsgLogger;
import org.hawkular.commons.log.MsgLogging;
import org.hawkular.commons.properties.HawkularProperties;
import org.hawkular.handlers.BaseApplication;

/**
 * @author Jay Shaughnessy
 * @author Lucas Ponce
 */
public class ServicesApp implements BaseApplication {
    private static final MsgLogger log = MsgLogging.getMsgLogger(ServicesApp.class);

    private static final String BASE_URL = "hawkular-services.base-url";
    private static final String BASE_URL_DEFAULT = "/hawkular";

    String baseUrl = HawkularProperties.getProperty(BASE_URL, BASE_URL_DEFAULT);

    public void start() {
        log.infof("Hawkular Services app started on [ %s ] ", baseUrl());
    }

    public void stop() {
        log.infof("Hawkular Services app stopped on [ %s ] ", baseUrl());
    }

    public String baseUrl() {
        return baseUrl;
    }
}
