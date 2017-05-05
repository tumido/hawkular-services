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

import static org.mockito.Matchers.anyObject;
import static org.mockito.Matchers.anyString;
import static org.mockito.Mockito.when;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.Arrays;
import java.util.Base64;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicReference;
import java.util.zip.GZIPOutputStream;

import org.hawkular.inventory.api.model.ExtendedInventoryStructure;
import org.hawkular.inventory.api.model.InventoryStructure;
import org.hawkular.inventory.api.model.MetricDataType;
import org.hawkular.inventory.api.model.MetricUnit;
import org.hawkular.inventory.json.InventoryJacksonConfig;
import org.hawkular.listener.exception.InvalidInventoryChunksException;
import org.hawkular.metrics.core.service.MetricsService;
import org.hawkular.metrics.core.service.Order;
import org.hawkular.metrics.model.DataPoint;
import org.hawkular.metrics.model.Metric;
import org.hawkular.metrics.model.MetricType;
import org.hawkular.metrics.model.Tenant;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.runners.MockitoJUnitRunner;

import com.fasterxml.jackson.core.JsonFactory;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.common.base.Throwables;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Lists;

import rx.Observable;

/**
 * @author Joel Takvorian
 */
@RunWith(MockitoJUnitRunner.class)
public class InventoryHelperTest {

    private final ObjectMapper mapper = new ObjectMapper(new JsonFactory());
    private final Base64.Encoder encoder = Base64.getEncoder();

    @Mock
    private MetricsService metricsService;

    @Before
    public void setUp() {
        InventoryJacksonConfig.configure(mapper);
    }

    @Test
    public void shouldListTenantsForFeed() {
        // Data & mocks
        when(metricsService.getTenants()).thenReturn(Observable.just(
                new Tenant("t1"),
                new Tenant("t2"),
                new Tenant("t3")));
        when(metricsService.findMetricsWithFilters(anyString(), anyObject(), anyString()))
                .thenAnswer(invocationOnMock -> {
                    String tenantId = invocationOnMock.getArgumentAt(0, String.class);
                    switch (tenantId) {
                        case "t1":
                            return Observable.just(new Metric<>("metric1", null, 7, MetricType.STRING, null));
                        case "t3":
                            return Observable.just(
                                    new Metric<>("metric2", null, 7, MetricType.STRING, null),
                                    new Metric<>("metric3", null, 7, MetricType.STRING, null));
                        default:
                            return Observable.empty();
                    }
                });

        // Test & assertions
        List<String> collectedTenants = new CopyOnWriteArrayList<>();
        InventoryHelper.listTenantsForFeed(metricsService, "some_feed")
                .toList()
                .subscribe(tenants -> tenants.forEach(t -> collectedTenants.add(t.getId())), Throwables::propagate);
        Assert.assertEquals(2, collectedTenants.size());
        Assert.assertEquals("t1", collectedTenants.get(0));
        Assert.assertEquals("t3", collectedTenants.get(1));
    }

    @Test
    public void shouldListEmptyTenantsForFeed() {
        // Data & mocks
        when(metricsService.getTenants()).thenReturn(Observable.just(
                new Tenant("t1"),
                new Tenant("t2"),
                new Tenant("t3")));
        when(metricsService.findMetricsWithFilters(anyString(), anyObject(), anyString()))
                .thenReturn(Observable.empty());

        // Test & assertions
        List<String> collectedTenants = new CopyOnWriteArrayList<>();
        InventoryHelper.listTenantsForFeed(metricsService, "some_feed")
                .toList()
                .subscribe(tenants -> tenants.forEach(t -> collectedTenants.add(t.getId())), Throwables::propagate);
        Assert.assertTrue(collectedTenants.isEmpty());
    }

    @Test
    public void shouldListMetricTypes() {
        // Data & mocks
        Metric<String> m1 = new Metric<>("inventory.123.mt.m1", null, 7, MetricType.STRING, null);
        Metric<String> m2 = new Metric<>("inventory.123.mt.m2", null, 7, MetricType.STRING, null);
        long currentTime = System.currentTimeMillis();
        when(metricsService.findMetricsWithFilters(anyString(), anyObject(), anyString()))
                .thenAnswer(invocationOnMock -> Observable.just(m1, m2));
        when(metricsService.findStringData(m1.getMetricId(), 0, currentTime, false, 0, Order.DESC))
                .thenReturn(Observable.just(
                        buildMetricTypeDatapoint(currentTime - 500, "metricType1", "metric type 1"),
                        buildMetricTypeDatapoint(currentTime - 1000, "oldMetricType1", "old metric type 1")));
        when(metricsService.findStringData(m2.getMetricId(), 0, currentTime, false, 0, Order.DESC))
                .thenReturn(Observable.just(buildMetricTypeDatapoint(currentTime - 100000, "metricType2", "metric type 2")));

        // Test & assertions
        List<org.hawkular.inventory.api.model.MetricType.Blueprint> collected = new CopyOnWriteArrayList<>();
        InventoryHelper.listMetricTypes(metricsService, "tenant", "feed", currentTime)
                .toList()
                .subscribe(collected::addAll, Throwables::propagate);
        Assert.assertEquals(2, collected.size());
        Assert.assertEquals("metricType1", collected.get(0).getId());
        Assert.assertEquals("metricType2", collected.get(1).getId());
    }

    @Test
    public void shouldListEmptyMetricTypes() {
        // Data & mocks
        long currentTime = System.currentTimeMillis();
        when(metricsService.findMetricsWithFilters(anyString(), anyObject(), anyString()))
                .thenAnswer(invocationOnMock -> Observable.empty());

        // Test & assertions
        List<org.hawkular.inventory.api.model.MetricType.Blueprint> collected = new CopyOnWriteArrayList<>();
        InventoryHelper.listMetricTypes(metricsService, "tenant", "feed", currentTime)
                .toList()
                .subscribe(collected::addAll, Throwables::propagate);
        Assert.assertTrue(collected.isEmpty());
    }

    @Test
    public void shouldListEmptyMetricTypesWhenChunksAreIncomplete() {
        // Data & mocks
        Metric<String> m1 = new Metric<>("inventory.123.mt.m1", null, 7, MetricType.STRING, null);
        long currentTime = System.currentTimeMillis();
        when(metricsService.findMetricsWithFilters(anyString(), anyObject(), anyString()))
                .thenAnswer(invocationOnMock -> Observable.just(m1));
        DataPoint<String> tempDataPoint = buildMetricTypeDatapoint(currentTime - 500, "metricType1", "metric type 1");
        DataPoint<String> dataPoint = new DataPoint<>(
                tempDataPoint.getTimestamp(),
                tempDataPoint.getValue(),
                ImmutableMap.<String, String>builder().put("chunks", "3").put("size", "1000").build());
        when(metricsService.findStringData(m1.getMetricId(), 0, currentTime, false, 0, Order.DESC))
                .thenReturn(Observable.just(dataPoint));

        // Test & assertions
        AtomicReference<Throwable> refException = new AtomicReference<>();
        InventoryHelper.listMetricTypes(metricsService, "tenant", "feed", currentTime)
                .toList()
                .subscribe(a -> {}, refException::set);
        Assert.assertEquals(InvalidInventoryChunksException.class, refException.get().getCause().getClass());
        Assert.assertTrue("Unexpected message: " + refException.get().getCause().getMessage(),
                refException.get().getCause().getMessage().contains(
                        "Inventory sanity check failure: 3 chunks expected, only 1 are available"));
    }

    @Test
    public void shouldFailOnListMetricTypesWhenChunksAreInvalid() {
        // Data & mocks
        Metric<String> m1 = new Metric<>("inventory.123.mt.m1", null, 7, MetricType.STRING, null);
        long currentTime = System.currentTimeMillis();
        when(metricsService.findMetricsWithFilters(anyString(), anyObject(), anyString()))
                .thenAnswer(invocationOnMock -> Observable.just(m1));
        DataPoint<String> tempDataPoint = buildMetricTypeDatapoint(currentTime - 500, "metricType1", "metric type 1");
        DataPoint<String> dataPoint = new DataPoint<>(
                tempDataPoint.getTimestamp(),
                tempDataPoint.getValue(),
                ImmutableMap.<String, String>builder().put("chunks", "3").put("size", "1000").build());
        when(metricsService.findStringData(m1.getMetricId(), 0, currentTime, false, 0, Order.DESC))
                .thenReturn(Observable.just(
                        dataPoint,
                        buildMetricTypeDatapoint(currentTime - 501, "metricType1", "metric type 1"),
                        buildMetricTypeDatapoint(currentTime - 800, "metricType1", "metric type 1")));

        // Test & assertions
        AtomicReference<Throwable> refException = new AtomicReference<>();
        InventoryHelper.listMetricTypes(metricsService, "tenant", "feed", currentTime)
                .toList()
                .subscribe(a -> {}, refException::set);
        Assert.assertEquals(InvalidInventoryChunksException.class, refException.get().getCause().getClass());
        Assert.assertTrue("Unexpected message: " + refException.get().getCause().getMessage(),
                refException.get().getCause().getMessage().contains(
                        "Inventory sanity check failure: chunk n°2 timestamp is"));
    }

    @Test
    public void shouldListMetricsForType() {
        // Data & mocks
        String tenant = "tenant";
        String feed = "feed";
        Metric<String> r1 = new Metric<>("inventory.123.r.r1", null, 7, MetricType.STRING, null);
        Metric<String> r2 = new Metric<>("inventory.123.r.r2", null, 7, MetricType.STRING, null);
        long currentTime = System.currentTimeMillis();
        when(metricsService.findMetricsWithFilters(anyString(), anyObject(), anyString()))
                .thenAnswer(invocationOnMock -> Observable.just(r1, r2));
        // Each call to "buildRootResourceDatapointWithMetrics" will create a root resource
        //  that contains 2 metrics of type "metricType1" and 1 metric of type "metricType2"
        when(metricsService.findStringData(r1.getMetricId(), 0, currentTime, false, 0, Order.DESC))
                .thenReturn(Observable.just(
                        buildRootResourceDatapointWithMetrics(tenant, feed, currentTime - 500, "r1"),
                        buildRootResourceDatapointWithMetrics(tenant, feed, currentTime - 1000, "r1")));
        when(metricsService.findStringData(r2.getMetricId(), 0, currentTime, false, 0, Order.DESC))
                .thenReturn(Observable.just(
                        buildRootResourceDatapointWithMetrics(tenant, feed, currentTime - 10000, "r2")));
        org.hawkular.inventory.api.model.MetricType.Blueprint bp
                = org.hawkular.inventory.api.model.MetricType.Blueprint
                    .builder(MetricDataType.GAUGE)
                    .withId("metricType1")
                    .withName("Metric type 1")
                    .withInterval(60L)
                    .withUnit(MetricUnit.BYTES)
                    .build();

        // Test & assertions
        List<org.hawkular.inventory.api.model.Metric.Blueprint> collected = new CopyOnWriteArrayList<>();
        InventoryHelper.listMetricsForType(metricsService, tenant, feed, bp, currentTime)
                .toList()
                .subscribe(collected::addAll, Throwables::propagate);
        Assert.assertEquals(4, collected.size());
        // Make sure we only have metric1 and metric2 (their type is metricType1), and not metric3 (type metricType2)
        Assert.assertEquals("metric1", collected.get(0).getId());
        Assert.assertEquals("metric2", collected.get(1).getId());
        Assert.assertEquals("metric1", collected.get(2).getId());
        Assert.assertEquals("metric2", collected.get(3).getId());
    }

    @Test
    public void shouldListNoMetricsForType() {
        // Data & mocks
        String tenant = "tenant";
        String feed = "feed";
        long currentTime = System.currentTimeMillis();
        when(metricsService.findMetricsWithFilters(anyString(), anyObject(), anyString()))
                .thenAnswer(invocationOnMock -> Observable.empty());
        org.hawkular.inventory.api.model.MetricType.Blueprint bp
                = org.hawkular.inventory.api.model.MetricType.Blueprint
                .builder(MetricDataType.GAUGE)
                .withId("metricType1")
                .withName("Metric type 1")
                .withInterval(60L)
                .withUnit(MetricUnit.BYTES)
                .build();

        // Test & assertions
        List<org.hawkular.inventory.api.model.Metric.Blueprint> collected = new CopyOnWriteArrayList<>();
        InventoryHelper.listMetricsForType(metricsService, tenant, feed, bp, currentTime)
                .toList()
                .subscribe(collected::addAll, Throwables::propagate);
        Assert.assertTrue(collected.isEmpty());
    }

    @Test
    public void shouldListNoMetricsForTypeWhenTagsDontMatchIndex() {
        // Data & mocks
        String tenant = "tenant";
        String feed = "feed";
        Metric<String> r1 = new Metric<>("inventory.123.r.r1", null, 7, MetricType.STRING, null);
        long currentTime = System.currentTimeMillis();
        when(metricsService.findMetricsWithFilters(anyString(), anyObject(), anyString()))
                .thenAnswer(invocationOnMock -> Observable.just(r1));
        when(metricsService.findStringData(r1.getMetricId(), 0, currentTime, false, 0, Order.DESC))
                .thenReturn(Observable.just(
                        buildRootResourceDatapointWithoutMetric(currentTime - 500, "r1")));
        org.hawkular.inventory.api.model.MetricType.Blueprint bp
                = org.hawkular.inventory.api.model.MetricType.Blueprint
                .builder(MetricDataType.GAUGE)
                .withId("metricType1")
                .withName("Metric type 1")
                .withInterval(60L)
                .withUnit(MetricUnit.BYTES)
                .build();

        // Test & assertions
        List<org.hawkular.inventory.api.model.Metric.Blueprint> collected = new CopyOnWriteArrayList<>();
        InventoryHelper.listMetricsForType(metricsService, tenant, feed, bp, currentTime)
                .toList()
                .subscribe(collected::addAll, Throwables::propagate);
        Assert.assertTrue(collected.isEmpty());
    }

    @Test
    public void shouldListNoMetricsForTypeWhenChunksAreIncomplete() {
        // Data & mocks
        String tenant = "tenant";
        String feed = "feed";
        Metric<String> r1 = new Metric<>("inventory.123.r.r1", null, 7, MetricType.STRING, null);
        long currentTime = System.currentTimeMillis();
        when(metricsService.findMetricsWithFilters(anyString(), anyObject(), anyString()))
                .thenAnswer(invocationOnMock -> Observable.just(r1));
        DataPoint<String> tempDataPoint = buildRootResourceDatapointWithMetrics(tenant, feed, currentTime - 500, "r1");
        DataPoint<String> dataPoint = new DataPoint<>(
                tempDataPoint.getTimestamp(),
                tempDataPoint.getValue(),
                ImmutableMap.<String, String>builder().put("chunks", "3").put("size", "1000").build());
        when(metricsService.findStringData(r1.getMetricId(), 0, currentTime, false, 0, Order.DESC))
                .thenReturn(Observable.just(dataPoint));
        org.hawkular.inventory.api.model.MetricType.Blueprint bp
                = org.hawkular.inventory.api.model.MetricType.Blueprint
                .builder(MetricDataType.GAUGE)
                .withId("metricType1")
                .withName("Metric type 1")
                .withInterval(60L)
                .withUnit(MetricUnit.BYTES)
                .build();

        // Test & assertions
        AtomicReference<Throwable> refException = new AtomicReference<>();
        InventoryHelper.listMetricsForType(metricsService, tenant, feed, bp, currentTime)
                .toList()
                .subscribe(a -> {}, refException::set);
        Assert.assertEquals(InvalidInventoryChunksException.class, refException.get().getCause().getClass());
        Assert.assertTrue("Unexpected message: " + refException.get().getCause().getMessage(),
                refException.get().getCause().getMessage().contains(
                        "Inventory sanity check failure: 3 chunks expected, only 1 are available"));
    }

    @Test
    public void shouldRebuildChunks() throws JsonProcessingException, InvalidInventoryChunksException {
        org.hawkular.inventory.api.model.Resource.Blueprint bp
                = org.hawkular.inventory.api.model.Resource.Blueprint.builder()
                .withId("typeId")
                .withName("typeName")
                .build();
        ExtendedInventoryStructure ext = new ExtendedInventoryStructure(InventoryStructure.Offline.of(bp).build());
        byte[] compressed = gzip(mapper.writeValueAsString(ext));
        // Artifically split in chunks (we don't care about their size)
        byte[] first = Arrays.copyOfRange(compressed, 0, 10);
        byte[] second = Arrays.copyOfRange(compressed, 10, 20);
        byte[] third = Arrays.copyOfRange(compressed, 20, compressed.length);
        Map<String, String> tags = ImmutableMap.<String, String>builder()
                .put("chunks", "3")
                .put("size", String.valueOf(compressed.length))
                .build();
        List<DataPoint<String>> dataPoints = Lists.newArrayList(
                new DataPoint<>(100L, encoder.encodeToString(first), tags),
                new DataPoint<>(99L, encoder.encodeToString(second)),
                new DataPoint<>(98L, encoder.encodeToString(third))
        );

        // Now rebuild
        ExtendedInventoryStructure inv = InventoryHelper.rebuildFromChunks(dataPoints);
        Assert.assertNotNull(inv);
        Assert.assertEquals("typeId", inv.getStructure().getRoot().getId());
    }

    @Test(expected=InvalidInventoryChunksException.class)
    public void shouldFailOnRebuildChunksSanityCheck()
            throws JsonProcessingException, InvalidInventoryChunksException {
        org.hawkular.inventory.api.model.Resource.Blueprint bp
                = org.hawkular.inventory.api.model.Resource.Blueprint.builder()
                .withId("typeId")
                .withName("typeName")
                .build();
        ExtendedInventoryStructure ext = new ExtendedInventoryStructure(InventoryStructure.Offline.of(bp).build());
        byte[] compressed = gzip(mapper.writeValueAsString(ext));
        // Artifically split in chunks (we don't care about their size)
        byte[] first = Arrays.copyOfRange(compressed, 0, 10);
        byte[] second = Arrays.copyOfRange(compressed, 10, 20);
        byte[] third = Arrays.copyOfRange(compressed, 20, compressed.length);
        Map<String, String> tags = ImmutableMap.<String, String>builder()
                .put("chunks", "3")
                .put("size", String.valueOf(compressed.length))
                .build();
        List<DataPoint<String>> dataPoints = Lists.newArrayList(
                new DataPoint<>(100L, encoder.encodeToString(first), tags),
                new DataPoint<>(99L, encoder.encodeToString(second)),
                new DataPoint<>(80L, encoder.encodeToString(third)) // invalid timestamp
        );

        // Now rebuild & expect exception
        InventoryHelper.rebuildFromChunks(dataPoints);
    }

    @Test(expected=InvalidInventoryChunksException.class)
    public void shouldFailOnRebuildChunksWithNotEnoughDatapoints()
            throws JsonProcessingException, InvalidInventoryChunksException {
        org.hawkular.inventory.api.model.Resource.Blueprint bp
                = org.hawkular.inventory.api.model.Resource.Blueprint.builder()
                .withId("typeId")
                .withName("typeName")
                .build();
        ExtendedInventoryStructure ext = new ExtendedInventoryStructure(InventoryStructure.Offline.of(bp).build());
        byte[] compressed = gzip(mapper.writeValueAsString(ext));
        // Artifically split in chunks (we don't care about their size)
        byte[] first = Arrays.copyOfRange(compressed, 0, 10);
        byte[] second = Arrays.copyOfRange(compressed, 10, 20);
        Map<String, String> tags = ImmutableMap.<String, String>builder()
                .put("chunks", "3")
                .put("size", String.valueOf(compressed.length))
                .build();
        List<DataPoint<String>> dataPoints = Lists.newArrayList(
                new DataPoint<>(100L, encoder.encodeToString(first), tags),
                new DataPoint<>(99L, encoder.encodeToString(second))
        );

        // Now rebuild & expect exception
        InventoryHelper.rebuildFromChunks(dataPoints);
    }

    private DataPoint<String> buildRootResourceDatapointWithMetrics(String tenant, String feed, long time, String resourceId) {
        org.hawkular.inventory.api.model.Resource.Blueprint bp
                = org.hawkular.inventory.api.model.Resource.Blueprint.builder()
                .withId(resourceId)
                .withName("resource")
                .build();
        InventoryStructure.Builder builder = InventoryStructure.Offline.of(bp);
        org.hawkular.inventory.api.model.Metric.Blueprint bpMetric1
                = org.hawkular.inventory.api.model.Metric.Blueprint.builder()
                .withId("metric1")
                .withName("Metric 1")
                .withMetricTypePath("/t;" + tenant + "/f;" + feed + "/mt;metricType1")
                .build();
        builder.addChild(bpMetric1);
        org.hawkular.inventory.api.model.Metric.Blueprint bpMetric2
                = org.hawkular.inventory.api.model.Metric.Blueprint.builder()
                .withId("metric2")
                .withName("Metric 2")
                .withMetricTypePath("/t;" + tenant + "/f;" + feed + "/mt;metricType1")
                .build();
        builder.addChild(bpMetric2);
        org.hawkular.inventory.api.model.Metric.Blueprint bpMetric3
                = org.hawkular.inventory.api.model.Metric.Blueprint.builder()
                .withId("metric3")
                .withName("Metric 3")
                .withMetricTypePath("/t;" + tenant + "/f;" + feed + "/mt;metricType2")
                .build();
        builder.addChild(bpMetric3);
        Map<String, Collection<String>> resourceTypesIndex = ImmutableMap.of();
        Map<String, Collection<String>> metricTypesIndex = ImmutableMap.<String, Collection<String>>builder()
                .put("metricType1", ImmutableList.of("m;metric1", "m;metric2"))
                .put("metricType2", ImmutableList.of("m;metric3"))
                .build();
        ExtendedInventoryStructure ext = new ExtendedInventoryStructure(builder.build(), resourceTypesIndex, metricTypesIndex);
        try {
            String json = mapper.writeValueAsString(ext);
            return new DataPoint<>(time, encoder.encodeToString(gzip(json)));
        } catch (JsonProcessingException e) {
            throw Throwables.propagate(e);
        }
    }

    private DataPoint<String> buildRootResourceDatapointWithoutMetric(long time, String resourceId) {
        org.hawkular.inventory.api.model.Resource.Blueprint bp
                = org.hawkular.inventory.api.model.Resource.Blueprint.builder()
                .withId(resourceId)
                .withName("resource")
                .build();
        InventoryStructure.Builder builder = InventoryStructure.Offline.of(bp);
        Map<String, Collection<String>> resourceTypesIndex = new HashMap<>();
        Map<String, Collection<String>> metricTypesIndex = new HashMap<>();
        ExtendedInventoryStructure ext = new ExtendedInventoryStructure(builder.build(), resourceTypesIndex, metricTypesIndex);
        try {
            String json = mapper.writeValueAsString(ext);
            return new DataPoint<>(time, encoder.encodeToString(gzip(json)));
        } catch (JsonProcessingException e) {
            throw Throwables.propagate(e);
        }
    }

    private DataPoint<String> buildMetricTypeDatapoint(long time, String typeId, String typeName) {
        org.hawkular.inventory.api.model.MetricType.Blueprint bp
                = org.hawkular.inventory.api.model.MetricType.Blueprint
                    .builder(MetricDataType.GAUGE)
                    .withId(typeId)
                    .withName(typeName)
                    .withInterval(60L)
                    .withUnit(MetricUnit.BYTES)
                    .build();
        InventoryStructure struct = InventoryStructure.Offline.of(bp).build();
        ExtendedInventoryStructure ext = new ExtendedInventoryStructure(struct);
        try {
            String json = mapper.writeValueAsString(ext);
            return new DataPoint<>(time, encoder.encodeToString(gzip(json)));
        } catch (JsonProcessingException e) {
            throw Throwables.propagate(e);
        }
    }

    private static byte[] gzip(String data) {
        ByteArrayOutputStream obj = new ByteArrayOutputStream();
        try {
            GZIPOutputStream gzip = new GZIPOutputStream(obj);
            gzip.write(data.getBytes("UTF-8"));
            gzip.close();
            return obj.toByteArray();
        } catch (IOException e) {
            throw Throwables.propagate(e);
        }
    }
}
