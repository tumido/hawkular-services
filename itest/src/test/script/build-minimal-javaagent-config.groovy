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
import org.yaml.snakeyaml.Yaml
import org.yaml.snakeyaml.DumperOptions

final String DIST_DIR = properties['hawkular.services.dist']
final String CONFIGURATION_DIR = DIST_DIR + "/standalone/configuration/"
final String JAVAAGENT_CONFIG = CONFIGURATION_DIR + "hawkular-javaagent-config.yaml"
final String JAVAAGENT_CONFIG_MINIMAL = CONFIGURATION_DIR + "hawkular-javaagent-minimal-config.yaml"

// Configure to print a easier to read (for humans) YAML in case we would like to debug
DumperOptions dumperOptions = new DumperOptions()
dumperOptions.setDefaultFlowStyle(DumperOptions.FlowStyle.BLOCK)

Yaml parser = new Yaml(dumperOptions)
Map config = parser.load((JAVAAGENT_CONFIG as File).text)

config['managed-servers']['remote-dmr'][0]['name'] = 'Local'

//  Remove all resourceTypeSets except for Main
config['managed-servers']['remote-dmr'][0]['resource-type-sets'] = ['Standalone Environment']

config['metric-set-dmr'].each {
    it['metric-dmr'].findAll { it['name'] == 'Heap Used' }.each {
        it['interval'] = 5
        it['time-units'] = 'seconds'
    }
}

config['platform']['memory'] = [ 'interval': 5, 'time-units': 'seconds' ]

// Ping more frequently
config['subsystem']['ping-period-secs'] = 5

parser.dump(config, new FileWriter(JAVAAGENT_CONFIG_MINIMAL))
