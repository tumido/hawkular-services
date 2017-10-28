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

import java.util.Date;

import javax.ws.rs.Consumes;
import javax.ws.rs.GET;
import javax.ws.rs.PUT;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;
import javax.ws.rs.core.Response;

import org.hawkular.client.api.Notification;
import org.hawkular.listener.MIQEventUtils;
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

    private final MIQEventUtils miqEventUtils = new MIQEventUtils();

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
                case AVAIL_STARTING:
                case AVAIL_CHANGE:
                    handleAvailChanged(notification);
                    break;
                default:
                    return ResponseUtil.badRequest("Unhandled Notification Type: " + notification.getType());
            }

            return ResponseUtil.ok(null);

        } catch (Exception e) {
            return ResponseUtil.onException(e, log);
        }
    }

    private void handleResourceAdded(Notification notification) {
        String resourceType = getRequiredValue(notification, "resourceType");
        String feedId = getRequiredValue(notification, "feedId");
        String resourceId = getRequiredValue(notification, "resourceId");
        miqEventUtils.handleResourceAdded(resourceType, feedId, resourceId);
    }

    private void handleAvailChanged(Notification notification) {
        String feedId = getRequiredValue(notification, "feedId");
        String resourceId = getRequiredValue(notification, "resourceId");
        String availType = getRequiredValue(notification, "availType");
        String newAvail = getRequiredValue(notification, "newAvail");
        miqEventUtils.handleResourceAvailChange(feedId, resourceId, availType, newAvail);
    }

    private String getRequiredValue(Notification notification, String key) {
        String value = notification.getProperties().get(key);
        if (isEmpty(value)) {
            throw new IllegalArgumentException("Required Property [" + key +"] is missing or is an invalid type.");
        }
        return value;
    }

    public static boolean isEmpty(String s) {
        return s == null || s.trim().isEmpty();
    }
}
