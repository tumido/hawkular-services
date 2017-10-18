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

final String BUILD_DIR = properties['project.build.directory']
final String CONFIGURATION_DIR = BUILD_DIR + "/feature-pack-resources/content/standalone/configuration/"
final String JAVAAGENT_CONFIG = CONFIGURATION_DIR + "hawkular-javaagent-config.yaml"
final String JAVAAGENT_CONFIG_SSL = CONFIGURATION_DIR + "hawkular-javaagent-config-ssl.yaml"

// Configure to print a easier to read (for humans) YAML
DumperOptions dumperOptions = new DumperOptions()
dumperOptions.setDefaultFlowStyle(DumperOptions.FlowStyle.BLOCK)

Yaml parser = new Yaml(dumperOptions)
Map config = parser.load((JAVAAGENT_CONFIG as File).text)

def template = { property, defaultValue = null ->
    "\${${property}:${defaultValue == null ? properties[property] : defaultValue}}".toString()}

// Configure javaagent for non ssl mode
config['subsystem']['enabled'] = template('hawkular.agent.enabled')
config['storage-adapter']['username'] = template('hawkular.rest.user')
config['storage-adapter']['password'] = template('hawkular.rest.password')
config['storage-adapter']['url'] = template('hawkular.rest.host', 'http://127.0.0.1:8080')

// Disable local-dmr and use a remote-dmr which allows a more detailed configuration.
config['managed-servers']['local-dmr']['enabled'] = false
config['managed-servers']['remote-dmr'][0]['name'] = 'Local'
config['managed-servers']['remote-dmr'][0]['enabled'] = true
config['managed-servers']['remote-dmr'][0]['port'] = template('jboss.management.http.port', 9990)
config['managed-servers']['remote-dmr'][0]['username'] = template('hawkular.rest.user')
config['managed-servers']['remote-dmr'][0]['password'] = template('hawkular.rest.password')
config['managed-servers']['remote-dmr'][0]['resource-type-sets'].add('Hawkular')

parser.dump(config, new FileWriter(JAVAAGENT_CONFIG))

// Create a SSL version
config['storage-adapter']['security-realm'] = 'HawkularAgentRealm'
config['storage-adapter']['url'] = template('hawkular.rest.host', 'https://127.0.0.1:8443')

config['managed-servers']['remote-dmr'][0]['security-realm'] = 'HawkularAgentRealm'
config['managed-servers']['remote-dmr'][0]['use-ssl'] = true
config['managed-servers']['remote-dmr'][0]['port'] = template('jboss.management.https.port', 9993)

securityRealm = new HashMap()
securityRealm['name'] = 'HawkularAgentRealm'
securityRealm['keystore-path'] = "\${env.JBOSS_HOME}/standalone/configuration//hawkular.keystore"
securityRealm['keystore-password'] = 'hawkular'

config['security-realm'] = [securityRealm]

parser.dump(config, new FileWriter(JAVAAGENT_CONFIG_SSL))