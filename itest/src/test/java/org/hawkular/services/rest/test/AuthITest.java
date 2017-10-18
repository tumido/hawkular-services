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

import org.hawkular.services.rest.test.TestClient.Retry;
import org.jboss.arquillian.container.test.api.RunAsClient;
import org.jboss.logging.Logger;
import org.testng.Assert;
import org.testng.annotations.Test;

/**
 * Sanity check that /status endpoints are unsecured and that other endpoints are secured.
 *
 * @author Jay Shaughnessy
 * @author Lucas Ponce
 */
public class AuthITest extends AbstractTestBase {
    private static final Logger log = Logger.getLogger(AlertingITest.class);
    public static final String GROUP = "AuthITest";
    private static final String alertingPath = "/hawkular/alerts";
    private static final String apiPath = "/hawkular/api";
    private static final String hawkularPath = "/hawkular";
    private static final String inventoryPath = "/hawkular/inventory";

    @Test(groups = { GROUP })
    @RunAsClient
    public void statusUnsecured() throws Throwable {

        String path = alertingPath + "/status";
        noAuthClient.newRequest()
                .path(path)
                .get()
                .assertWithRetries(testResponse -> {
                    testResponse
                            .assertCode(200)
                            .assertJson(status -> {
                                log.tracef("Got Alerting status [%s]", status);
                                String foundState = status.get("status").asText();
                                Assert.assertEquals(foundState, "STARTED");
                            });
                }, Retry.times(20).delay(250));

        path = apiPath + "/ping";
        noAuthClient.newRequest()
                .path(path)
                .get()
                .assertWithRetries(testResponse -> {
                    testResponse.assertCode(200);
                }, Retry.times(20).delay(250));

        path = hawkularPath + "/status";
        noAuthClient.newRequest()
                .path(path)
                .get()
                .assertWithRetries(testResponse -> {
                    testResponse.assertCode(200);
                }, Retry.times(20).delay(250));

        path = inventoryPath + "/status";
        noAuthClient.newRequest()
                .path(path)
                .get()
                .assertWithRetries(testResponse -> {
                    testResponse
                            .assertCode(200)
                            .assertJson(status -> {
                                log.tracef("Got Inventory status [%s]", status);
                                String foundState = status.get("status").asText();
                                Assert.assertEquals(foundState, "UP");
                            });
                }, Retry.times(20).delay(250));
    }

    @Test(groups = { GROUP })
    @RunAsClient
    public void secured401() throws Throwable {

        String path = alertingPath + "/events";
        noAuthClient.newRequest()
                .path(path)
                .get()
                .assertWithRetries(testResponse -> {
                    testResponse.assertCode(401);
                }, Retry.times(5).delay(250));

        path = apiPath + "/notification";
        noAuthClient.newRequest()
                .path(path)
                .putJson("{}")
                .assertWithRetries(testResponse -> {
                    testResponse.assertCode(401);
                }, Retry.times(5).delay(250));

        path = inventoryPath + "/types";
        noAuthClient.newRequest()
                .path(path)
                .get()
                .assertWithRetries(testResponse -> {
                    testResponse.assertCode(401);
                }, Retry.times(5).delay(250));
    }

    @Test(groups = { GROUP })
    @RunAsClient
    public void secured200() throws Throwable {

        String path = alertingPath + "/events";
        testClient.newRequest()
                .path(path)
                .get()
                .assertWithRetries(testResponse -> {
                    testResponse.assertCode(200);
                }, Retry.times(20).delay(250));

        path = apiPath + "/notification";
        testClient.newRequest()
                .path(path)
                .putJson("{"
                        + "\"type\":\"RESOURCE_ADDED\","
                        + "\"properties\":{\"resourceType\":\"t\",\"feedId\":\"f\",\"resourceId\":\"r\"}}")
                .assertWithRetries(testResponse -> {
                    testResponse.assertCode(200);
                }, Retry.times(20).delay(250));

        path = inventoryPath + "/types";
        testClient.newRequest()
                .path(path)
                .get()
                .assertWithRetries(testResponse -> {
                    testResponse.assertCode(200);
                }, Retry.times(20).delay(250));
    }
}
