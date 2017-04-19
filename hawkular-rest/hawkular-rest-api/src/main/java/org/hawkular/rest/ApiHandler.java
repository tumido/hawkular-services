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
package org.hawkular.rest;

import static javax.ws.rs.core.MediaType.APPLICATION_JSON;

import java.util.Arrays;
import java.util.Date;
import java.util.HashSet;
import java.util.Set;

import javax.ws.rs.Consumes;
import javax.ws.rs.GET;
import javax.ws.rs.HeaderParam;
import javax.ws.rs.PUT;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;
import javax.ws.rs.core.Response;

import org.hawkular.client.api.Notification;
import org.hawkular.client.api.NotificationType;
import org.hawkular.inventory.paths.CanonicalPath;
import org.hawkular.listener.bus.ListenerUtils;
import org.hawkular.rest.json.ApiError;
import org.jboss.logging.Logger;

import io.swagger.annotations.ApiOperation;
import io.swagger.annotations.ApiParam;
import io.swagger.annotations.ApiResponse;
import io.swagger.annotations.ApiResponses;

/**
 * @author Jay Shaughnessy
 */
@Path("/")
public class ApiHandler {
    private static final Logger log = Logger.getLogger(ApiHandler.class);

    public static final String TENANT_HEADER_NAME = "Hawkular-Tenant";

    private final ListenerUtils utils = new ListenerUtils();

    @HeaderParam(TENANT_HEADER_NAME)
    String tenantId;

    public ApiHandler() {
        log.debug("Creating Instance.");
    }

    @GET
    @Path("/ping")
    @Consumes({ APPLICATION_JSON })
    @Produces({ APPLICATION_JSON })
    @ApiOperation("A dummy operation returning the current date on the server.")
    public Response ping() {
        return Response.ok(new Date().toString()).build();
    }

    @PUT
    @Path("/notification")
    @Consumes(APPLICATION_JSON)
    @ApiOperation(value = "Process a notification.")
    @ApiResponses(value = {
            @ApiResponse(code = 200, message = "Success, Notification Processed."),
            @ApiResponse(code = 500, message = "Internal server error.", response = ApiError.class),
            @ApiResponse(code = 400, message = "Bad Request/Invalid Parameters", response = ApiError.class)
    })
    public Response handleNotification(
            @ApiParam(value = "Notification to be handled.", name = "notification", required = true) //
            final Notification notification) {
        try {
            if (null == notification) {
                return ResponseUtil.badRequest("Notification is null");
            }
            if (null == notification.getType()) {
                return ResponseUtil.badRequest("Notification Type is null");
            }
            if (null == notification.getProperties()) {
                return ResponseUtil.badRequest("Notification Properties is null");
            }

            switch (notification.getType()) {
                case RESOURCE_ADDED:
                    handleResourceAdded(notification);
                    break;
                default:
                    return ResponseUtil.badRequest("Unhandled Notification Type: " + notification.getType());

            }

            return ResponseUtil.ok(null);

        } catch (Exception e) {
            log.debug(e.getMessage(), e);
            if (e.getCause() != null && e.getCause() instanceof IllegalArgumentException) {
                return ResponseUtil.badRequest("Bad arguments: " + e.getMessage());
            }
            return ResponseUtil.internalError(e.getMessage());
        }
    }

    private static final Set<String> SERVER_TYPES = new HashSet<>(Arrays.asList(
            "Domain Host",
            "Domain WildFly Server",
            "Domain WildFly Server Controller",
            "Host Controller",
            "WildFly Server"));

    private void handleResourceAdded(Notification notification) {
        String resourceType = notification.getProperties().get("resourceType");
        if (isEmpty(resourceType)) {
            throw new IllegalArgumentException("Required Property [resourceType] is missing or is an invalid type.");
        }
        String resourcePath = notification.getProperties().get("resourcePath");
        CanonicalPath cp;
        try {
            cp = CanonicalPath.fromString(resourcePath);
        } catch (Exception e) {
            throw new IllegalArgumentException(
                    "Required Property [resourcePath] is missing or is an invalid CanonicalPath: " + e.getMessage());
        }

        if (SERVER_TYPES.contains(resourceType)) {
            String eventId = NotificationType.RESOURCE_ADDED.name() + "_" + cp.toString();
            String message = "Added: " + resourceType;

            utils.addEvent(eventId, true, cp, "Inventory Change", message, "hawkular_event",
                    "MiddlewareServer", message);
        }
    }

    public static boolean isEmpty(String s) {
        return s == null || s.trim().isEmpty();
    }
}
