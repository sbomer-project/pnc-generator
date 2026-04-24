package org.jboss.sbomer.pnc.generator.adapter.out.pnc;

import static org.junit.jupiter.api.Assertions.*;

import java.util.List;

import org.jboss.pnc.dto.Artifact;
import org.jboss.pnc.dto.Build;
import org.jboss.sbomer.pnc.generator.core.domain.slsa.SlsaProvenance;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class MockPncServiceAdapterTest {

    private MockPncServiceAdapter mockAdapter;

    @BeforeEach
    void setUp() {
        mockAdapter = new MockPncServiceAdapter();
    }

    @Test
    void testGetBuild_ReturnsMockData() {
        Build build = mockAdapter.getBuild("ANY-ID");

        assertNotNull(build);
        assertEquals("ANY-ID", build.getId());
        assertNotNull(build.getEnvironment());
        assertEquals("builder-linux-j17-mvn3.9.3:1.0.0", build.getEnvironment().getSystemImageId());

        assertNotNull(build.getAttributes());
        assertEquals("com.acme.cloud:acme-cloud-pack-parent", build.getAttributes().get("BREW_BUILD_NAME"));
    }

    @Test
    void testGetBuiltArtifacts_ReturnsThreeArtifacts() {
        List<Artifact> artifacts = mockAdapter.getBuiltArtifacts("ANY-ID");

        assertNotNull(artifacts);
        assertEquals(3, artifacts.size());

        // Check that the ZIP, POM, and YAML are present
        assertTrue(artifacts.stream().anyMatch(a -> a.getId().equals(MockPncServiceAdapter.MOCK_ARTIFACT_ID_ZIP)));
        assertTrue(artifacts.stream().anyMatch(a -> a.getId().equals(MockPncServiceAdapter.MOCK_ARTIFACT_ID_POM)));
        assertTrue(artifacts.stream().anyMatch(a -> a.getId().equals(MockPncServiceAdapter.MOCK_ARTIFACT_ID_YAML)));
    }

    @Test
    void testGetBuildProvenance_ReturnsComplexGraph() {
        SlsaProvenance provenance = mockAdapter.getBuildProvenance("ANY-ID");

        assertNotNull(provenance);
        assertEquals("https://slsa.dev/provenance/v1", provenance.predicateType());

        // Verify Subjects (What was built)
        assertNotNull(provenance.subject());
        assertEquals(3, provenance.subject().size());

        // Verify Dependencies (What was consumed)
        assertNotNull(provenance.predicate());

        // Verify the new Build Definition fields
        assertNotNull(provenance.predicate().buildDefinition());
        assertEquals("https://project-ncl.github.io/slsa-pnc-buildtypes/workflow/v1", provenance.predicate().buildDefinition().buildType());
        assertNotNull(provenance.predicate().buildDefinition().externalParameters());
        assertTrue(provenance.predicate().buildDefinition().externalParameters().repository().containsKey("revision"));

        assertNotNull(provenance.predicate().resolvedDependencies());
        assertEquals(6, provenance.predicate().resolvedDependencies().size()); // 3 meta + 3 libraries
    }
}