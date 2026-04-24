package org.jboss.sbomer.pnc.generator.adapter.out.pnc;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

import java.lang.reflect.Field;
import java.util.List;

import org.jboss.pnc.client.BuildClient;
import org.jboss.pnc.client.ClientException;
import org.jboss.pnc.client.RemoteCollection;
import org.jboss.pnc.client.RemoteResourceException;
import org.jboss.pnc.client.RemoteResourceNotFoundException;
import org.jboss.pnc.dto.Artifact;
import org.jboss.pnc.dto.Build;
import org.jboss.sbomer.pnc.generator.core.domain.slsa.SlsaProvenance;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class PncServiceAdapterTest {

    @Mock
    PncRestApiClient pncRestClient;

    @Mock
    BuildClient buildClient;

    @InjectMocks
    PncServiceAdapter adapter;

    @BeforeEach
    void setUp() throws Exception {
        Field field = PncServiceAdapter.class.getDeclaredField("buildClient");
        field.setAccessible(true);
        field.set(adapter, buildClient);
    }

    // --- getBuild() Tests ---

    @Test
    void testGetBuild_Success() throws ClientException {
        Build expectedBuild = Build.builder().id("12345").build();
        when(buildClient.getSpecific("12345")).thenReturn(expectedBuild);

        Build actualBuild = adapter.getBuild("12345");

        assertNotNull(actualBuild);
        assertEquals("12345", actualBuild.getId());
        verify(buildClient, times(1)).getSpecific("12345");
    }

    @Test
    void testGetBuild_NotFound_ReturnsNull() throws ClientException {
        RemoteResourceNotFoundException notFoundMock = mock(RemoteResourceNotFoundException.class);
        when(buildClient.getSpecific("999")).thenThrow(notFoundMock);

        Build actualBuild = adapter.getBuild("999");

        assertNull(actualBuild);
        verify(buildClient, times(1)).getSpecific("999");
    }

    @Test
    void testGetBuild_GenericException_ThrowsRuntimeException() throws ClientException {
        RemoteResourceException errorMock = mock(RemoteResourceException.class);
        when(buildClient.getSpecific("error-id")).thenThrow(errorMock);

        RuntimeException thrown = assertThrows(RuntimeException.class, () -> adapter.getBuild("error-id"));
        assertTrue(thrown.getMessage().contains("Failed to retrieve build from PNC"));
    }

    // --- getBuiltArtifacts() Tests ---

    @Test
    void testGetBuiltArtifacts_Success() throws ClientException {
        @SuppressWarnings("unchecked")
        RemoteCollection<Artifact> mockCollection = mock(RemoteCollection.class);
        when(mockCollection.getAll()).thenReturn(List.of(
                Artifact.builder().id("art-1").build(),
                Artifact.builder().id("art-2").build()
        ));

        when(buildClient.getBuiltArtifacts("12345")).thenReturn(mockCollection);

        List<Artifact> artifacts = adapter.getBuiltArtifacts("12345");

        assertNotNull(artifacts);
        assertEquals(2, artifacts.size());
        assertEquals("art-1", artifacts.get(0).getId());
    }

    @Test
    void testGetBuiltArtifacts_NotFound_ThrowsException() throws ClientException {
        RemoteResourceNotFoundException notFoundMock = mock(RemoteResourceNotFoundException.class);
        when(buildClient.getBuiltArtifacts(anyString())).thenThrow(notFoundMock);

        RuntimeException thrown = assertThrows(RuntimeException.class, () -> adapter.getBuiltArtifacts("999"));
        assertTrue(thrown.getMessage().contains("Built artifacts not found"));
    }

    // --- getBuildProvenance() Tests ---

    @Test
    void testGetBuildProvenance_Success() {
        SlsaProvenance mockProvenance = new SlsaProvenance("https://slsa.dev/provenance/v1", null, List.of());
        when(pncRestClient.getBuildProvenanceFallback("art-1")).thenReturn(mockProvenance);

        SlsaProvenance result = adapter.getBuildProvenance("art-1");

        assertNotNull(result);
        assertEquals("https://slsa.dev/provenance/v1", result.predicateType());
        verify(pncRestClient, times(1)).getBuildProvenanceFallback("art-1");
    }

    @Test
    void testGetBuildProvenance_Failure_ThrowsException() {
        when(pncRestClient.getBuildProvenanceFallback(anyString())).thenThrow(new RuntimeException("Network Error"));

        RuntimeException thrown = assertThrows(RuntimeException.class, () -> adapter.getBuildProvenance("art-error"));
        assertTrue(thrown.getMessage().contains("Failed to retrieve SLSA provenance"));
    }
}