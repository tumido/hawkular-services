package org.hawkular.services;

import java.util.HashMap;
import java.util.Map;

import org.hawkular.commons.log.MsgLogger;
import org.hawkular.commons.log.MsgLogging;
import org.hawkular.handlers.RestEndpoint;
import org.hawkular.handlers.RestHandler;
import org.hawkular.services.util.ManifestUtil;
import org.hawkular.services.util.ResponseUtil;

import io.vertx.ext.web.Router;
import io.vertx.ext.web.RoutingContext;

/**
 * @author Jay Shaughnessy
 * @author Lucas Ponce
 */
@RestEndpoint(path = "/status")
public class StatusHandler implements RestHandler {
    private static final MsgLogger log = MsgLogging.getMsgLogger(StatusHandler.class);
    static final String STATUS = "status";
    static final String STARTED = "STARTED";
    static final String FAILED = "FAILED";

    ManifestUtil manifestUtil;

    public StatusHandler() {
        manifestUtil = new ManifestUtil();
    }

    @Override
    public void initRoutes(String baseUrl, Router router) {
        String path = baseUrl + "/status";
        router.get(path).handler(this::status);
    }

    public void status(RoutingContext routing) {
        routing.vertx()
                .executeBlocking(future -> {
                    Map<String, String> status = new HashMap<>();
                    status.putAll(manifestUtil.getFrom());
                    status.put(STATUS, STARTED);
                    future.complete(status);
                }, res -> ResponseUtil.result(routing, res));
    }
}
