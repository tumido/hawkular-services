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
package org.hawkular.services.rest.test;

import java.util.List;

import org.hawkular.cmdgw.ws.test.EchoCommandITest;
import org.hawkular.inventory.api.model.ExtendedInventoryStructure;
import org.hawkular.services.rest.test.TestClient.Retry;
import org.jboss.arquillian.container.test.api.RunAsClient;
import org.jboss.logging.Logger;
import org.testng.Assert;
import org.testng.annotations.Test;

import okhttp3.HttpUrl;

/**
 * Hawkular Agent integration tests.
 *
 * @author <a href="https://github.com/ppalaga">Peter Palaga</a>
 */
public class AgentITest extends AbstractTestBase {
    private static final Logger log = Logger.getLogger(AgentITest.class);
    /** The {@code tenantId} used by the Agent we test */
    private static final String testTenantId = System.getProperty("hawkular.itest.rest.tenantId");
    /** The {@code feedId} used by the Agent we test */
    private static final String testFeedId = System.getProperty("hawkular.itest.rest.feedId");

    /**
     * Checks that the metrics collected by Hawkular Agent are there in Hawkular Metrics.
     * <p>
     * A note about {@link Test#dependsOnGroups()}: we actually depend only on {@link MetricsITest#GROUP} here but we
     * want these tests to run at the very end of the suite so that it takes less to wait for the data points to appear
     * in Metrics.
     *
     * @throws Throwable
     */
    @Test(dependsOnGroups = { EchoCommandITest.GROUP, AlertingITest.GROUP, MetricsITest.GROUP })
    @RunAsClient
    public void agentCollectingMetrics() throws Throwable {
        final String wfHeapMetricId = "MI~R~[" + testFeedId + "/Local~~]~MT~WildFly Memory Metrics~Heap Used";

        /* This low level HttpUrl building is needed because wfHeapMetricId contains slashes */
        HttpUrl url = new HttpUrl.Builder()
                .scheme(httpScheme).host(host).port(httpPort)
                .encodedPath(MetricsITest.metricsPath)
                .addPathSegment("gauges")
                .addPathSegment(wfHeapMetricId)
                .addPathSegment("raw")
                .build();

        testClient.newRequest()
                .header("Hawkular-Tenant", testTenantId)
                .url(url)
                .get()
                .assertWithRetries(testResponse -> {
                    testResponse
                            .assertCode(200)
                            .assertJson(foundDataPoints -> {
                                log.infof("Request to [%s] returned datapoints [%s]", url, foundDataPoints);

                                Assert.assertTrue(foundDataPoints.isArray(), String.format(
                                        "[%s] should have returned a json array, while it returned [%s]",
                                        testResponse.getRequest(), foundDataPoints));
                                Assert.assertTrue(foundDataPoints.size() >= 1, String.format(
                                        "[%s] should have returned a json array with size >= 1, while it returned [%s]",
                                        testResponse.getRequest(), foundDataPoints));
                            });
                }, Retry.times(500).delay(100));
    }

    /**
     * Checks that the agent's ping [availability] metric is being sent and stored into Hawkular Metrics.
     * <p>
     * A note about {@link Test#dependsOnGroups()}: we actually depend only on {@link MetricsITest#GROUP} here but we
     * want these tests to run at the very end of the suite so that it takes less to wait for the data points to appear
     * in Metrics.
     *
     * @throws Throwable
     */
    @Test(dependsOnGroups = { EchoCommandITest.GROUP, AlertingITest.GROUP, MetricsITest.GROUP })
    @RunAsClient
    public void agentSendingPings() throws Throwable {
        final String pingMetricId = "hawkular-feed-availability-" + testFeedId;

        HttpUrl url = new HttpUrl.Builder()
                .scheme(httpScheme).host(host).port(httpPort)
                .encodedPath(MetricsITest.metricsPath)
                .addPathSegment("availability")
                .addPathSegment(pingMetricId)
                .addPathSegment("raw")
                .build();

        testClient.newRequest()
                .header("Hawkular-Tenant", testTenantId)
                .url(url)
                .get()
                .assertWithRetries(testResponse -> {
                    testResponse
                            .assertCode(200)
                            .assertJson(foundDataPoints -> {
                        log.infof("Request to [%s] returned datapoints [%s]", url, foundDataPoints);

                        Assert.assertTrue(foundDataPoints.isArray(), String.format(
                                "[%s] should have returned a json array, while it returned [%s]",
                                testResponse.getRequest(), foundDataPoints));
                        Assert.assertTrue(foundDataPoints.size() >= 1, String.format(
                                "[%s] should have returned a json array with size >= 1, while it returned [%s]",
                                testResponse.getRequest(), foundDataPoints));
                    });
                } , Retry.times(500).delay(100));
    }

    /**
     * Checks that at least the local WildFly and operating system were inserted to Inventory by Hawkular Agent.
     * <p>
     * A note about {@link Test#dependsOnGroups()}: we want these tests to run at the very end of the suite so that it
     * takes less to wait for the resources to appear in Inventory.
     *
     * @throws Throwable
     */
    @Test(dependsOnGroups = { EchoCommandITest.GROUP, AlertingITest.GROUP, MetricsITest.GROUP })
    @RunAsClient
    public void agentDiscoverySuccess() throws Throwable {
        final String resourcesPath = "/hawkular/metrics/strings/raw/query";
        final String wfServerId = "Local~~";
        final String osId = "platform~/OPERATING_SYSTEM=itest-feed_OperatingSystem";

        testClient.newRequest()
                .header("Hawkular-Tenant", testTenantId)
                .path(resourcesPath)
                .postJson("{fromEarliest:true, order:\"DESC\", tags:\"module:inventory,feed:itest-feed,type:r\"}")
                .assertWithRetries(testResponse -> {
                    testResponse
                            .assertCode(200)
                            .assertJson(foundResources -> {

                                log.tracef("Got resources [%s]", foundResources);
                                Assert.assertTrue(foundResources.isArray(), String.format(
                                        "[%s] should have returned a json array, while it returned [%s]",
                                        testResponse.getRequest(), foundResources));
                                Assert.assertTrue(foundResources.size() >= 2, String.format(
                                        "[%s] should have returned a json array with size >= 2, while it returned [%s]",
                                        testResponse.getRequest(), foundResources));

                                List<ExtendedInventoryStructure> structures
                                        = InventoryHelper.extractStructuresFromResponse(foundResources);

                                ExtendedInventoryStructure wf = structures.stream()
                                        .filter(ext -> ext.getStructure().getRoot().getId().equals(wfServerId))
                                        .findFirst().orElseThrow(() -> new AssertionError(
                                                String.format(
                                                        "GET [%s] should return an array containing a WF server resource with id [%s]",
                                                        resourcesPath, wfServerId)));
                                log.tracef("Found a WF server resource [%s]", wf);

                                ExtendedInventoryStructure os = structures.stream()
                                        .filter(ext -> ext.getStructure().getRoot().getId().equals(osId))
                                        .findFirst().orElseThrow(() -> new AssertionError(
                                                String.format(
                                                        "GET [%s] should return an array containing an OS resource with id [%s]",
                                                        resourcesPath, osId)));
                                log.tracef("Found an OS resource [%s]", os);

                                /* test passed: both the WF server and the OS are there in the list of resources */

                            });

                }, Retry.times(500).delay(1000));

    }

    /**
     * Checks that the local WildFly discovery generated a notification.
     *
     * @throws Throwable
     */
    @Test(dependsOnMethods = { "agentDiscoverySuccess" })
    @RunAsClient
    public void agentNotificationSuccess() throws Throwable {
        StringBuffer sb = new StringBuffer("/hawkular/alerts/events");
        sb.append("?");
        sb.append("tagQuery=miq.event_type%20%3D%20hawkular_event%20AND%20miq.resource_type%20%3D%20MiddlewareServer");
        final String eventsPath = sb.toString();

        testClient.newRequest()
                .header("Hawkular-Tenant", testTenantId)
                .path(eventsPath)
                .get()
                .assertWithRetries(testResponse -> {
                    testResponse
                            .assertCode(200)
                            .assertJson(foundEvents -> {

                                log.warnf("Got events [%s]", foundEvents);
                                Assert.assertTrue(foundEvents.isArray(), String.format(
                                        "[%s] should have returned a json array, while it returned [%s]",
                                        testResponse.getRequest(), foundEvents));
                                Assert.assertTrue(foundEvents.size() == 1, String.format(
                                        "[%s] should have returned a json array with size == 1, while it returned [%s]",
                                        testResponse.getRequest(), foundEvents));
                            });

                }, Retry.times(500).delay(1000));

    }

}
