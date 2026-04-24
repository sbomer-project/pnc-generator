package org.jboss.sbomer.pnc.generator.core.service;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

import java.util.List;
import java.util.Map;

import org.jboss.pnc.dto.Artifact;
import org.jboss.pnc.dto.Build;
import org.jboss.sbomer.events.orchestration.GenerationCreated;
import org.jboss.sbomer.pnc.generator.core.domain.slsa.SlsaAnnotations;
import org.jboss.sbomer.pnc.generator.core.domain.slsa.SlsaDependency;
import org.jboss.sbomer.pnc.generator.core.domain.slsa.SlsaPredicate;
import org.jboss.sbomer.pnc.generator.core.domain.slsa.SlsaProvenance;
import org.jboss.sbomer.pnc.generator.core.domain.slsa.SlsaSubject;
import org.jboss.sbomer.pnc.generator.core.port.spi.PNCService;
import org.jboss.sbomer.pnc.generator.core.port.spi.StatusUpdateService;
import org.jboss.sbomer.pnc.generator.core.port.spi.StorageService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Answers;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class PncBuildGenerationServiceTest {

    @Mock
    PNCService pncService;

    @Mock
    StorageService storageService;

    @Mock
    StatusUpdateService statusUpdateService;

    @InjectMocks
    PncBuildGenerationService service;

    // Use deep stubs so we don't have to instantiate the nested event objects!
    @Mock(answer = Answers.RETURNS_DEEP_STUBS)
    GenerationCreated mockEvent;

    @BeforeEach
    void setUp() {
        // Set standard config values via reflection
        try {
            setField(service, "toolName", "SBOMer NextGen");
            setField(service, "toolVersion", "1.0.0");
            setField(service, "supplierName", "Red Hat");
            setField(service, "supplierUrls", List.of("https://redhat.com"));
            setField(service, "pncApiUrl", "http://mock-pnc");
        } catch (Exception e) {
            fail("Failed to set config fields");
        }

        // Mock the deeply nested method calls directly
        when(mockEvent.getData().getGenerationRequest().getGenerationId()).thenReturn("GEN-999");
        when(mockEvent.getData().getGenerationRequest().getTarget().getIdentifier()).thenReturn("BUILD-123");
    }

    private void setField(Object target, String fieldName, Object value) throws Exception {
        java.lang.reflect.Field field = target.getClass().getDeclaredField(fieldName);
        field.setAccessible(true);
        field.set(target, value);
    }

    @Test
    void testProcessGeneration_HappyPath() {
        // 1. Mock the PNC Build
        Build mockBuild = Build.builder()
                .id("BUILD-123")
                .attributes(Map.of("BREW_BUILD_NAME", "com.test:test-app", "BREW_BUILD_VERSION", "1.0.0.redhat-00001"))
                .build();
        when(pncService.getBuild("BUILD-123")).thenReturn(mockBuild);

        // 2. Mock the Built Artifacts list
        Artifact mockArtifact = Artifact.builder().id("ART-001").build();
        when(pncService.getBuiltArtifacts("BUILD-123")).thenReturn(List.of(mockArtifact));

        // 3. Mock the SLSA Provenance with the new v1.0 structure
        SlsaSubject subject = new SlsaSubject(
                "test-app-1.0.0.redhat-00001.jar",
                Map.of("sha256", "fakehash"),
                new SlsaAnnotations(null, "pkg:maven/com.test/test-app@1.0.0.redhat-00001?type=jar", null, "ART-001", "BUILD-123", null)
        );

        // Add mocked environment and repository nodes to test the new pedigree/environment extraction logic
        SlsaDependency repoDep = new SlsaDependency("repository", Map.of("gitCommit", "12345abcde"), "https://github.com/test/repo.git", null);
        SlsaDependency envDep = new SlsaDependency("environment", Map.of("sha256", "dockerhash"), "quay.io/test/builder", new SlsaAnnotations(null, null, null, null, null, "latest"));

        SlsaPredicate predicate = new SlsaPredicate(null, List.of(repoDep, envDep));
        SlsaProvenance mockProvenance = new SlsaProvenance("https://slsa.dev/provenance/v1", predicate, List.of(subject));
        when(pncService.getBuildProvenance("ART-001")).thenReturn(mockProvenance);

        // 4. Mock the storage upload
        when(storageService.uploadSbom(eq("GEN-999"), anyString())).thenReturn("http://storage/GEN-999/sbom.json");

        // Execute
        service.processGeneration(mockEvent);

        // Verify Interactions
        verify(statusUpdateService).reportGenerating("GEN-999");
        verify(pncService).getBuild("BUILD-123");
        verify(pncService).getBuiltArtifacts("BUILD-123");
        verify(pncService).getBuildProvenance("ART-001");

        ArgumentCaptor<String> jsonCaptor = ArgumentCaptor.forClass(String.class);
        verify(storageService).uploadSbom(eq("GEN-999"), jsonCaptor.capture());

        // Assert the JSON payload was generated correctly
        String generatedJson = jsonCaptor.getValue();
        assertNotNull(generatedJson);
        assertTrue(generatedJson.contains("CycloneDX"));
        assertTrue(generatedJson.contains("pkg:maven/com.test/test-app@1.0.0.redhat-00001"));
        assertTrue(generatedJson.contains("12345abcde"), "Git commit was not extracted from provenance repository node");
        assertTrue(generatedJson.contains("quay.io/test/builder"), "Builder image was not extracted from provenance environment node");

        verify(statusUpdateService).reportFinished("GEN-999", List.of("http://storage/GEN-999/sbom.json"));
    }

    @Test
    void testProcessGeneration_BuildNotFound_ThrowsException() {
        when(pncService.getBuild("BUILD-123")).thenReturn(null);

        RuntimeException thrown = assertThrows(RuntimeException.class, () -> service.processGeneration(mockEvent));

        assertTrue(thrown.getMessage().contains("internal processing error"));
        verify(statusUpdateService).reportGenerating("GEN-999");
        verify(statusUpdateService).reportFailed(eq("GEN-999"), contains("not found"));
        verifyNoInteractions(storageService);
    }

    @Test
    void testProcessGeneration_NoArtifacts_ThrowsException() {
        Build mockBuild = Build.builder().id("BUILD-123").build();
        when(pncService.getBuild("BUILD-123")).thenReturn(mockBuild);
        when(pncService.getBuiltArtifacts("BUILD-123")).thenReturn(List.of()); // Empty list

        RuntimeException thrown = assertThrows(RuntimeException.class, () -> service.processGeneration(mockEvent));

        assertTrue(thrown.getMessage().contains("internal processing error"));
        verify(statusUpdateService).reportGenerating("GEN-999");
        verify(statusUpdateService).reportFailed(eq("GEN-999"), contains("No artifacts were built"));
        verifyNoInteractions(storageService);
    }

    @Test
    void testProcessGeneration_EmptyProvenance_ThrowsException() {
        Build mockBuild = Build.builder().id("BUILD-123").build();
        when(pncService.getBuild("BUILD-123")).thenReturn(mockBuild);

        Artifact mockArtifact = Artifact.builder().id("ART-001").build();
        when(pncService.getBuiltArtifacts("BUILD-123")).thenReturn(List.of(mockArtifact));

        when(pncService.getBuildProvenance("ART-001")).thenReturn(new SlsaProvenance(null, null, null)); // Null subject

        RuntimeException thrown = assertThrows(RuntimeException.class, () -> service.processGeneration(mockEvent));

        assertTrue(thrown.getMessage().contains("internal processing error"));
        verify(statusUpdateService).reportGenerating("GEN-999");
        verify(statusUpdateService).reportFailed(eq("GEN-999"), contains("Provenance is empty or invalid"));
        verifyNoInteractions(storageService);
    }
}