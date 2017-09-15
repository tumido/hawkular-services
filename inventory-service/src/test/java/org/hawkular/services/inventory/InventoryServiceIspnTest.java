package org.hawkular.services.inventory;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashMap;

import org.hawkular.services.inventory.model.Metric;
import org.hawkular.services.inventory.model.MetricUnit;
import org.hawkular.services.inventory.model.Operation;
import org.hawkular.services.inventory.model.Resource;
import org.hawkular.services.inventory.model.ResourceType;
import org.junit.Before;
import org.junit.Test;

import junit.framework.AssertionFailedError;

/**
 * @author Joel Takvorian
 */
public class InventoryServiceIspnTest {
    private static final Resource EAP1 = new Resource("EAP-1", "EAP-1", "EAP", "",
            Arrays.asList("child-1", "child-2"), Arrays.asList("m-1", "m-2"), new HashMap<>());
    private static final Resource EAP2 = new Resource("EAP-2", "EAP-2", "EAP", "",
            Arrays.asList("child-3", "child-4"), Arrays.asList("m-3", "m-4"), new HashMap<>());
    private static final Resource CHILD1 = new Resource("child-1", "Child 1", "FOO", "EAP-1",
            new ArrayList<>(), new ArrayList<>(), new HashMap<>());
    private static final Resource CHILD2 = new Resource("child-2", "Child 2", "BAR", "EAP-1",
            new ArrayList<>(), new ArrayList<>(), new HashMap<>());
    private static final Resource CHILD3 = new Resource("child-3", "Child 3", "FOO", "EAP-2",
            new ArrayList<>(), new ArrayList<>(), new HashMap<>());
    private static final Resource CHILD4 = new Resource("child-4", "Child 4", "BAR", "EAP-2",
            new ArrayList<>(), new ArrayList<>(), new HashMap<>());
    private static final Collection<Operation> EAP_OPS = Arrays.asList(
            new Operation("Reload", new HashMap<>()),
            new Operation("Shutdown", new HashMap<>()));
    private static final ResourceType TYPE_EAP = new ResourceType("EAP", EAP_OPS, new HashMap<>());
    private static final Metric METRIC1
            = new Metric("m-1", "memory", "Memory", MetricUnit.BYTES, 10, new HashMap<>());
    private static final Metric METRIC2
            = new Metric("m-2", "gc", "GC", MetricUnit.NONE, 10, new HashMap<>());
    private static final Metric METRIC3
            = new Metric("m-3", "memory", "Memory", MetricUnit.BYTES, 10, new HashMap<>());
    private static final Metric METRIC4
            = new Metric("m-4", "gc", "GC", MetricUnit.NONE, 10, new HashMap<>());

    private InventoryServiceIspn service = new InventoryServiceIspn();

    @Before
    public void setUp() {
        service.addResource(EAP1);
        service.addResource(EAP2);
        service.addResource(CHILD1);
        service.addResource(CHILD2);
        service.addResource(CHILD3);
        service.addResource(CHILD4);
        service.addResourceType(TYPE_EAP);
        service.addMetric(METRIC1);
        service.addMetric(METRIC2);
        service.addMetric(METRIC3);
        service.addMetric(METRIC4);
        service.updateIndexes();
    }

    @Test
    public void shouldFindResourcesById() {
        assertThat(service.getResourceById("EAP-1")).isPresent()
                .map(Resource::getName)
                .hasValue("EAP-1");
        assertThat(service.getResourceById("EAP-2")).isPresent()
                .map(Resource::getName)
                .hasValue("EAP-2");
        assertThat(service.getResourceById("child-1")).isPresent()
                .map(Resource::getName)
                .hasValue("Child 1");
    }

    @Test
    public void shouldNotFindResourcesById() {
        assertThat(service.getResourceById("nada")).isNotPresent();
    }

    @Test
    public void shouldGetTopResources() {
        assertThat(service.getAllTopResources())
                .extracting(Resource::getName)
                .containsExactly("EAP-1", "EAP-2");
    }

    @Test
    public void shouldGetResourceTypes() {
        assertThat(service.getAllResourceTypes())
                .extracting(ResourceType::getId)
                .containsExactly("EAP");
    }

    @Test
    public void shouldGetAllEAPs() {
        assertThat(service.getResourcesByType("EAP"))
                .extracting(Resource::getId)
                .containsExactly("EAP-1", "EAP-2");
    }

    @Test
    public void shouldGetAllFOOs() {
        assertThat(service.getResourcesByType("FOO"))
                .extracting(Resource::getId)
                .containsExactly("child-1", "child-3");
    }

    @Test
    public void shouldGetNoNada() {
        assertThat(service.getResourcesByType("nada")).isEmpty();
    }

    @Test
    public void shouldGetChildren() {
        ResourceNode tree = service.getTree("EAP-1").orElseThrow(AssertionFailedError::new);
        assertThat(tree.getChildren())
                .extracting(ResourceNode::getId)
                .containsExactly("child-1", "child-2");
    }

    @Test
    public void shouldGetEmptyChildren() {
        ResourceNode tree = service.getTree("child-1").orElseThrow(AssertionFailedError::new);
        assertThat(tree.getChildren()).isEmpty();
    }

    @Test
    public void shouldNotGetTree() {
        assertThat(service.getTree("nada")).isNotPresent();
    }

    @Test
    public void shouldGetMetrics() {
        assertThat(service.getResourceMetrics("EAP-1"))
                .isPresent()
                .hasValueSatisfying(m -> assertThat(m)
                        .extracting(Metric::getId)
                        .containsExactly("m-1", "m-2"));
    }

    @Test
    public void shouldGetEmptyMetrics() {
        assertThat(service.getResourceMetrics("child-1"))
                .isPresent()
                .hasValueSatisfying(children -> assertThat(children).isEmpty());
    }

    @Test
    public void shouldNotGetMetrics() {
        assertThat(service.getResourceMetrics("nada")).isNotPresent();
    }

    @Test
    public void shouldFailOnDetectedCycle() {
        Resource corruptedParent = new Resource("CP", "CP", "FOO", "",
                Arrays.asList("CC"), new ArrayList<>(), new HashMap<>());
        Resource corruptedChild = new Resource("CC", "CC", "BAR", "CP",
                Arrays.asList("CP"), new ArrayList<>(), new HashMap<>());
        service.addResource(corruptedParent);
        service.addResource(corruptedChild);
        service.updateIndexes();

        assertThatThrownBy(() -> service.getTree("CP"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Cycle detected");
    }
}
