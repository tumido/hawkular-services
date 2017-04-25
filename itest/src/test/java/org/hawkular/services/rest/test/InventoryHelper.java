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

import java.io.BufferedReader;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;
import java.util.stream.StreamSupport;
import java.util.zip.GZIPInputStream;

import org.hawkular.inventory.api.model.ExtendedInventoryStructure;
import org.hawkular.inventory.json.InventoryJacksonConfig;

import com.fasterxml.jackson.core.JsonFactory;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.common.base.Throwables;

/**
 * Provide some functions to read inventory responses
 * @author Joel Takvorian
 */
final class InventoryHelper {

    private static final ObjectMapper MAPPER;

    static {
        MAPPER = new ObjectMapper(new JsonFactory());
        InventoryJacksonConfig.configure(MAPPER);
    }

    private InventoryHelper() {
    }

    static List<ExtendedInventoryStructure> extractStructuresFromResponse(JsonNode response) {
        return StreamSupport.stream(response.spliterator(), true)
                .map(node -> node.get("data"))
                .map(InventoryHelper::rebuildFromChunks)
                .filter(Optional::isPresent)
                .map(Optional::get)
                .collect(Collectors.toList());
    }

    private static Optional<ExtendedInventoryStructure> rebuildFromChunks(JsonNode dataNode) {
        if (!dataNode.has(0)) {
            return Optional.empty();
        }
        try {
            JsonNode masterNode = dataNode.get(0);
            final byte[] all;
            if (masterNode.has("tags") && masterNode.get("tags").has("chunks")) {
                int nbChunks = masterNode.get("tags").get("chunks").asInt();
                int totalSize = masterNode.get("tags").get("size").asInt();
                byte[] master = masterNode.get("value").binaryValue();
                if (master.length == 0) {
                    return Optional.empty();
                }
                all = new byte[totalSize];
                int pos = 0;
                System.arraycopy(master, 0, all, pos, master.length);
                pos += master.length;
                for (int i = 1; i < nbChunks; i++) {
                    JsonNode slaveNode = dataNode.get(i);
                    byte[] slave = slaveNode.get("value").binaryValue();
                    System.arraycopy(slave, 0, all, pos, slave.length);
                    pos += slave.length;
                }
            } else {
                // Not chunked
                all = masterNode.get("value").binaryValue();
            }
            String decompressed = decompress(all);
            ExtendedInventoryStructure structure = MAPPER.readValue(decompressed, ExtendedInventoryStructure.class);
            return Optional.of(structure);
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
