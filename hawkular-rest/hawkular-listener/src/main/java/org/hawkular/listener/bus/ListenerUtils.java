/*
 * Copyright 2016-2017 Red Hat, Inc. and/or its affiliates
 * and other contributors as indicated by the @author tags.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.hawkular.listener.bus;

import java.util.Collections;
import java.util.UUID;

import javax.naming.InitialContext;

import org.hawkular.alerts.api.model.event.Event;
import org.hawkular.alerts.api.services.AlertsService;
import org.jboss.logging.Logger;

public class ListenerUtils {
    private static final String ALERTS_SERVICE = "java:global/hawkular-metrics/hawkular-alerts/CassAlertsServiceImpl";

    // TODO [lponce] let configure this, but new inventory won't deal with tenantId,
    // TODO MiQ works with a default tenant 'hawkular' this is for compability with alerting multi-tenant feature
    private static final String DEFAULT_TENANT = "hawkular";
    private final Logger log = Logger.getLogger(ListenerUtils.class);

    public InitialContext ctx;
    public AlertsService alerts;

    public ListenerUtils() {
    }

    /**
     * @param feedId the feed associated with the resource
     * @param resourceId the ID of the resource
     * @param category the event category
     * @param text the event text
     * @param miqEventType the MIQ event type
     * @param miqResourceType the MIQ event resource type
     * @param miqMessage optional message for the MIQ event
     */
    public void addEvent(String feedId, String resourceId, String category, String text, String miqEventType,
                         String miqResourceType, String miqMessage) {
        addEvent(null, false, feedId, resourceId, category, text, miqEventType, miqResourceType,
                miqMessage);
    }

    /**
     * @param eventId if null will be a generated UUID
     * @param checkExists addEvent only if event with the provided eventId does not already exist
     * @param feedId the feed associated with the resource
     * @param resourceId the ID of the resource
     * @param category the event category
     * @param text the event text
     * @param miqEventType the MIQ event type
     * @param miqResourceType the MIQ event resource type
     * @param miqMessage optional message for the MIQ event
     */
    public void addEvent(String eventId, boolean checkExists, String feedId, String resourceId, String category, String text,
                         String miqEventType,
                         String miqResourceType, String miqMessage) {
        try {
            init();

            String tenantId = DEFAULT_TENANT;
            eventId = (null == eventId || eventId.isEmpty()) ? UUID.randomUUID().toString() : eventId;

            if (checkExists) {
                if (null != alerts.getEvent(tenantId, eventId, true)) {
                    return;
                }
            }

            Event event = new Event(tenantId, eventId, category, text);
            event.addContext("feed_id", feedId);
            event.addContext("resource_id", resourceId);
            event.addContext("message", miqMessage);
            event.addTag("miq.event_type", miqEventType);
            event.addTag("miq.resource_type", miqResourceType);

            log.debugf("Received message [%s] and forwarding it as [%s]", miqMessage, event);

            alerts.addEvents(Collections.singleton(event));

        } catch (Exception e) {
            log.errorf("Error processing event for message [%s]: %s", miqMessage, e);
        }
    }

    private synchronized void init() throws Exception {
        if (ctx == null) {
            ctx = new InitialContext();
        }
        if (alerts == null) {
            alerts = (AlertsService) ctx.lookup(ALERTS_SERVICE);
        }
    }

}