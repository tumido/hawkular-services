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
package org.hawkular.listener;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

import org.hawkular.inventory.paths.CanonicalPath;
import org.hawkular.listener.bus.ListenerUtils;

/**
 * Group together MIQ events sent to alerting
 */
public class MIQEventUtils {

    private final ListenerUtils utils = new ListenerUtils();

    private static final Set<String> SERVER_TYPES = new HashSet<>(Arrays.asList(
            "Domain Host",
            "Domain WildFly Server",
            "Domain WildFly Server Controller",
            "Host Controller",
            "WildFly Server"));

    public static final String SERVER_AVAILABILITY_NAME = "Server Availability";

    public void handleResourceAdded(String resourceType, String resourcePath) {
        final String RESOURCE_ADDED = "RESOURCE_ADDED";
        CanonicalPath cp;
        try {
            cp = CanonicalPath.fromString(resourcePath);
        } catch (Exception e) {
            throw new IllegalArgumentException(
                    "Required Property [resourcePath] is missing or is an invalid CanonicalPath: " + e.getMessage());
        }

        if (SERVER_TYPES.contains(resourceType)) {
            String eventId = RESOURCE_ADDED + "_" + cp.toString();
            String message = "Added: " + resourceType;

            utils.addEvent(eventId, true, cp, "Inventory Change", message, "hawkular_event",
                    "MiddlewareServer", message);
        }
    }

    public void handleResourceAvailChange(String resourcePath, String availType, String newAvail) {
        CanonicalPath cp;
        try {
            cp = CanonicalPath.fromString(resourcePath);
        } catch (Exception e) {
            throw new IllegalArgumentException(
                    "Required Property [resourcePath] is missing or is an invalid CanonicalPath: " + e.getMessage());
        }
        if (availType.equals(SERVER_AVAILABILITY_NAME)) {
            String message = "Avail change [" + newAvail + "]: " + availType;

            utils.addEvent(null, false, cp, "Inventory Change", message, "hawkular_event",
                    "MiddlewareServer", message);
        }
    }

}
