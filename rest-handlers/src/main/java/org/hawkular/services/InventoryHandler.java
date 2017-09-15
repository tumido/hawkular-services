/*
 * Copyright 2017 Red Hat, Inc. and/or its affiliates
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
package org.hawkular.services;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashMap;
import java.util.Optional;
import java.util.function.Supplier;

import org.hawkular.commons.log.MsgLogger;
import org.hawkular.commons.log.MsgLogging;
import org.hawkular.handlers.RestEndpoint;
import org.hawkular.handlers.RestHandler;
import org.hawkular.services.inventory.InventoryServiceIspn;
import org.hawkular.services.inventory.model.Metric;
import org.hawkular.services.inventory.model.MetricUnit;
import org.hawkular.services.inventory.model.Operation;
import org.hawkular.services.inventory.model.Resource;
import org.hawkular.services.inventory.model.ResourceType;
import org.hawkular.services.util.ResponseUtil;

import io.vertx.core.json.Json;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.web.Router;
import io.vertx.ext.web.RoutingContext;

/**
 * @author Joel Takvorian
 */
@RestEndpoint(path = InventoryHandler.INVENTORY_PATH)
public class InventoryHandler implements RestHandler {

    static final String INVENTORY_PATH = "/inventory";

    private static final MsgLogger LOG = MsgLogging.getMsgLogger(InventoryHandler.class);

    private final InventoryServiceIspn inventoryService;

    public InventoryHandler() {
        // TODO: injection, or what?
        inventoryService = new InventoryServiceIspn();
        // Stub some data
        final Resource EAP1 = new Resource("EAP-1", "EAP-1", "EAP", "",
                Arrays.asList("child-1", "child-2"), Arrays.asList("m-1", "m-2"), new HashMap<>());
        final Resource EAP2 = new Resource("EAP-2", "EAP-2", "EAP", "",
                Arrays.asList("child-3", "child-4"), Arrays.asList("m-3", "m-4"), new HashMap<>());
        final Resource CHILD1 = new Resource("child-1", "Child 1", "FOO", "EAP-1",
                new ArrayList<>(), new ArrayList<>(), new HashMap<>());
        final Resource CHILD2 = new Resource("child-2", "Child 2", "BAR", "EAP-1",
                new ArrayList<>(), new ArrayList<>(), new HashMap<>());
        final Resource CHILD3 = new Resource("child-3", "Child 3", "FOO", "EAP-2",
                new ArrayList<>(), new ArrayList<>(), new HashMap<>());
        final Resource CHILD4 = new Resource("child-4", "Child 4", "BAR", "EAP-2",
                new ArrayList<>(), new ArrayList<>(), new HashMap<>());
        final Collection<Operation> EAP_OPS = Arrays.asList(
                new Operation("Reload", new HashMap<>()),
                new Operation("Shutdown", new HashMap<>()));
        final ResourceType TYPE_EAP = new ResourceType("EAP", EAP_OPS, new HashMap<>());
        final Metric METRIC1
                = new Metric("m-1", "memory", "Memory", MetricUnit.BYTES, 10, new HashMap<>());
        final Metric METRIC2
                = new Metric("m-2", "gc", "GC", MetricUnit.NONE, 10, new HashMap<>());
        final Metric METRIC3
                = new Metric("m-3", "memory", "Memory", MetricUnit.BYTES, 10, new HashMap<>());
        final Metric METRIC4
                = new Metric("m-4", "gc", "GC", MetricUnit.NONE, 10, new HashMap<>());
        inventoryService.addResource(EAP1);
        inventoryService.addResource(EAP2);
        inventoryService.addResource(CHILD1);
        inventoryService.addResource(CHILD2);
        inventoryService.addResource(CHILD3);
        inventoryService.addResource(CHILD4);
        inventoryService.addResourceType(TYPE_EAP);
        inventoryService.addMetric(METRIC1);
        inventoryService.addMetric(METRIC2);
        inventoryService.addMetric(METRIC3);
        inventoryService.addMetric(METRIC4);
        inventoryService.updateIndexes();
    }

    @Override
    public void initRoutes(String baseUrl, Router router) {
        String path = baseUrl + INVENTORY_PATH;
        router.get(path + "/res").handler(this::topResources);
        router.get(path + "/res/type/:type").handler(this::resourcesByType);
        router.get(path + "/res/:id/metrics").handler(this::metrics);
        router.get(path + "/res/:id").handler(this::resourceById);
        router.get(path + "/type").handler(this::resourceTypes);
        router.get(path + "/type/:type").handler(this::resourceType);
        router.get(path + "/tree/:id").handler(this::resourceTree);
    }

    private void resourceById(RoutingContext routing) {
        String id = routing.request().getParam("id");
        if (id == null) {
            ResponseUtil.badRequest(routing, "Missing parameter: id");
            return;
        }
        queryOptional(() -> inventoryService.getResourceById(id),
                routing,
                "Resource id " + id + " not found")
                .ifPresent(r -> {
                    try {
                        routing.response().end(Json.encode(r));
                    } catch (Exception e) {
                        LOG.error(e.getMessage(), e);
                        ResponseUtil.internalServerError(routing, e.toString());
                    }
                });
    }

    private void topResources(RoutingContext routing) {
        try {
            Collection<Resource> resources = inventoryService.getAllTopResources();
            routing.response().end(Json.encode(resources));
        } catch (Exception e) {
            LOG.error(e.getMessage(), e);
            ResponseUtil.internalServerError(routing, e.toString());
        }
    }

    private void resourceTypes(RoutingContext routing) {
        try {
            Collection<ResourceType> types = inventoryService.getAllResourceTypes();
            routing.response().end(Json.encode(types));
        } catch (Exception e) {
            LOG.error(e.getMessage(), e);
            ResponseUtil.internalServerError(routing, e.toString());
        }
    }

    private void resourceType(RoutingContext routing) {
        String type = routing.request().getParam("type");
        if (type == null) {
            throw new ResponseUtil.BadRequestException("Missing parameter: type");
        }
        queryOptional(() -> inventoryService.getResourceType(type),
                routing,
                "Resource type " + type + " not found")
                .ifPresent(rt -> {
                    try {
                        JsonObject json = new JsonObject(Json.encode(rt));
                        routing.response().end(json.encode());
                    } catch (Exception e) {
                        LOG.error(e.getMessage(), e);
                        ResponseUtil.internalServerError(routing, e.toString());
                    }
                });
    }

    private void resourcesByType(RoutingContext routing) {
        String type = routing.request().getParam("type");
        if (type == null) {
            throw new ResponseUtil.BadRequestException("Missing parameter: type");
        }
        try {
            Collection<Resource> resources = inventoryService.getResourcesByType(type);
            routing.response().end(Json.encode(resources));
        } catch (Exception e) {
            LOG.error(e.getMessage(), e);
            ResponseUtil.internalServerError(routing, e.toString());
        }
    }

    private void metrics(RoutingContext routing) {
        String id = routing.request().getParam("id");
        if (id == null) {
            throw new ResponseUtil.BadRequestException("Missing parameter: id");
        }
        try {
            Collection<Metric> metrics = inventoryService.getResourceMetrics(id)
                    .orElseThrow(() -> new ResponseUtil.NotFoundException("Resource id " + id + " not found"));
            routing.response().end(Json.encode(metrics));
        } catch (Exception e) {
            LOG.error(e.getMessage(), e);
            ResponseUtil.internalServerError(routing, e.toString());
        }
    }

    private static <T> Optional<T> queryOptional(Supplier<Optional<T>> supplier, RoutingContext routing, String msg) {
        try {
            Optional<T> result = supplier.get();
            if (!result.isPresent()) {
                ResponseUtil.notFound(routing, msg);
            }
            return result;
        } catch (Exception e) {
            LOG.error(e.getMessage(), e);
            ResponseUtil.internalServerError(routing, e.toString());
            return Optional.empty();
        }
    }

    private void resourceTree(RoutingContext routing) {
        String id = routing.request().getParam("id");
        if (id == null) {
            ResponseUtil.badRequest(routing, "Missing parameter: id");
            return;
        }
        queryOptional(() -> inventoryService.getTree(id),
                routing,
                "Resource id " + id + " not found")
                .ifPresent(tree -> {
                    try {
                        routing.response().end(Json.encode(tree));
                    } catch (Exception e) {
                        LOG.error(e.getMessage(), e);
                        ResponseUtil.internalServerError(routing, e.toString());
                    }
                });
    }
}
