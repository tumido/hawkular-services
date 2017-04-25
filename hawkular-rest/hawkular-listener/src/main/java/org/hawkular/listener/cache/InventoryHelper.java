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
package org.hawkular.listener.cache;

import java.io.BufferedReader;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.zip.GZIPInputStream;

import org.hawkular.inventory.api.model.ExtendedInventoryStructure;
import org.hawkular.inventory.api.model.Metric;
import org.hawkular.inventory.api.model.MetricType;
import org.hawkular.inventory.json.InventoryJacksonConfig;
import org.hawkular.inventory.paths.RelativePath;
import org.hawkular.metrics.core.service.MetricsService;
import org.hawkular.metrics.core.service.Order;
import org.hawkular.metrics.model.DataPoint;
import org.hawkular.metrics.model.Tenant;

import com.fasterxml.jackson.core.JsonFactory;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.common.base.Throwables;

import rx.Observable;

/**
 * Helper methods to read inventory data as produced by the agents
 * @author Joel Takvorian
 */
public final class InventoryHelper {

    private static final ObjectMapper MAPPER = new ObjectMapper(new JsonFactory());

    static {
        InventoryJacksonConfig.configure(MAPPER);
    }

    private InventoryHelper() {
    }

    /**
     * Get the list of tenants that have the given feed id
     */
    static Observable<Tenant> listTenantsForFeed(MetricsService metricsService, String feedId) {
        return metricsService.getTenants()
                .flatMap(tenant -> metricsService.findMetricsWithFilters(
                        tenant.getId(),
                        org.hawkular.metrics.model.MetricType.STRING,
                        "module:inventory,feed:" + feedId)
                        .isEmpty().filter(isEmpty -> !isEmpty).map(any -> tenant));
    }

    /**
     * Get the list of all metric types for given tenant and feed
     */
    static Observable<MetricType.Blueprint> listMetricTypes(MetricsService metricsService,
                                                            String tenantId,
                                                            String feedId) {
        String tags = "module:inventory,feed:" + feedId + ",type:mt";
        return metricsService.findMetricsWithFilters(tenantId, org.hawkular.metrics.model.MetricType.STRING, tags)
                .flatMap(metric -> {
                    return metricsService.findStringData(metric.getMetricId(), 0, System.currentTimeMillis(),
                            false, 0, Order.DESC)
                            .toList()
                            .map(InventoryHelper::rebuildFromChunks)
                            .filter(Objects::nonNull)
                            .map(inv -> inv.getStructure().get(RelativePath.fromString("")))
                            .filter(bp -> bp instanceof MetricType.Blueprint)
                            .map(bp -> (MetricType.Blueprint)bp);
                });
    }

    /**
     * Get the list of metrics for given tenant, feed and metric type
     */
    static Observable<Metric.Blueprint> listMetricsForType(MetricsService metricsService,
                                                           String tenantId,
                                                           String feedId,
                                                           MetricType.Blueprint metricType) {
        String escapedForRegex = Pattern.quote("|" + metricType.getId() + "|");
        String tags = "module:inventory,feed:" + feedId + ",type:r,mtypes:.*" + escapedForRegex + ".*";
        return metricsService.findMetricsWithFilters(tenantId, org.hawkular.metrics.model.MetricType.STRING, tags)
                .flatMap(metric -> {
                    return metricsService.findStringData(metric.getMetricId(), 0, System.currentTimeMillis(),
                            false, 0, Order.DESC)
                            .toList()
                            .map(InventoryHelper::rebuildFromChunks)
                            .map(inv -> extractMetricsForType(inv, metricType.getId()))
                            .flatMap(Observable::from);
                });
    }

    private static List<Metric.Blueprint> extractMetricsForType(ExtendedInventoryStructure inv, String metricTypeId) {
        if (inv == null || inv.getMetricTypesIndex() == null || !inv.getMetricTypesIndex().containsKey(metricTypeId)) {
            return Collections.emptyList();
        }
        return inv.getMetricTypesIndex().get(metricTypeId).stream()
                .map(relPath -> inv.getStructure().get(RelativePath.fromString(relPath)))
                .filter(bp -> bp instanceof Metric.Blueprint)
                .map(bp -> (Metric.Blueprint)bp)
                .collect(Collectors.toList());

    }

    private static ExtendedInventoryStructure rebuildFromChunks(List<DataPoint<String>> datapoints) {
        if (datapoints.isEmpty()) {
            return null;
        }
        try {
            DataPoint<String> masterNode = datapoints.get(0);
            final byte[] all;
            if (masterNode.getTags().containsKey("chunks")) {
                int nbChunks = Integer.parseInt(masterNode.getTags().get("chunks"));
                int totalSize = Integer.parseInt(masterNode.getTags().get("size"));
                byte[] master = masterNode.getValue().getBytes();
                if (master.length == 0) {
                    return null;
                }
                all = new byte[totalSize];
                int pos = 0;
                System.arraycopy(master, 0, all, pos, master.length);
                pos += master.length;
                for (int i = 1; i < nbChunks; i++) {
                    DataPoint<String> slaveNode = datapoints.get(i);
                    byte[] slave = slaveNode.getValue().getBytes();
                    System.arraycopy(slave, 0, all, pos, slave.length);
                    pos += slave.length;
                }
            } else {
                // Not chunked
                all = masterNode.getValue().getBytes();
            }
            String decompressed = decompress(all);
            return MAPPER.readValue(decompressed, ExtendedInventoryStructure.class);
        } catch (Exception e) {
            throw Throwables.propagate(e);
        }
    }

    private static String decompress(byte[] gzipped) throws IOException {
        if ((gzipped == null) || (gzipped.length == 0)) {
            return "";
        }
        StringBuilder outStr = new StringBuilder();
        GZIPInputStream gis = new GZIPInputStream(new ByteArrayInputStream(gzipped));
        BufferedReader bufferedReader = new BufferedReader(new InputStreamReader(gis, "UTF-8"));
        String line;
        while ((line = bufferedReader.readLine()) != null) {
            outStr.append(line);
        }
        return outStr.toString();
    }
}
