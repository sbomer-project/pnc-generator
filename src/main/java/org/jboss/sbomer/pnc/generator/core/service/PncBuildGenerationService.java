package org.jboss.sbomer.pnc.generator.core.service;

import jakarta.enterprise.context.ApplicationScoped;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.cyclonedx.Version;
import org.cyclonedx.generators.json.BomJsonGenerator;
import org.cyclonedx.model.Ancestors;
import org.cyclonedx.model.Bom;
import org.cyclonedx.model.Commit;
import org.cyclonedx.model.Component;
import org.cyclonedx.model.Dependency;
import org.cyclonedx.model.ExternalReference;
import org.cyclonedx.model.Hash;
import org.cyclonedx.model.Metadata;
import org.cyclonedx.model.OrganizationalEntity;
import org.cyclonedx.model.Pedigree;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.eclipse.microprofile.faulttolerance.Bulkhead;
import org.jboss.pnc.dto.Artifact;
import org.jboss.pnc.dto.Build;
import org.jboss.sbomer.events.orchestration.GenerationCreated;
import org.jboss.sbomer.pnc.generator.core.domain.slsa.SlsaDependency;
import org.jboss.sbomer.pnc.generator.core.domain.slsa.SlsaProvenance;
import org.jboss.sbomer.pnc.generator.core.domain.slsa.SlsaSubject;
import org.jboss.sbomer.pnc.generator.core.port.api.GenerationProcessor;
import org.jboss.sbomer.pnc.generator.core.port.spi.PNCService;
import org.jboss.sbomer.pnc.generator.core.port.spi.StatusUpdateService;
import org.jboss.sbomer.pnc.generator.core.port.spi.StorageService;
import org.jboss.sbomer.pnc.generator.core.utility.VcsUrl;

import com.github.packageurl.PackageURL;

import io.opentelemetry.api.trace.Span;
import io.opentelemetry.instrumentation.annotations.WithSpan;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@ApplicationScoped
@Slf4j
@RequiredArgsConstructor
public class PncBuildGenerationService implements GenerationProcessor {

    private final PNCService pncService;
    private final StorageService storageService;
    private final StatusUpdateService statusUpdateService;

    @ConfigProperty(name = "sbomer.generator.tool.name", defaultValue = "SBOMer NextGen")
    String toolName;

    @ConfigProperty(name = "sbomer.generator.tool.version", defaultValue = "1.0.0")
    String toolVersion;

    @ConfigProperty(name = "sbomer.generator.supplier.name", defaultValue = "Red Hat")
    String supplierName;

    @ConfigProperty(name = "sbomer.generator.supplier.urls", defaultValue = "https://www.redhat.com")
    List<String> supplierUrls;

    @ConfigProperty(name = "pnc.api.url", defaultValue = "https://orch.pnc.engineering.redhat.com")
    String pncApiUrl;

    @Override
    @WithSpan
    @Bulkhead(value = 10) // Can be higher than DelA since we aren't parsing massive ZIPs in memory
    public void processGeneration(GenerationCreated event) {
        String generationId = event.getData().getGenerationRequest().getGenerationId();
        String buildId = event.getData().getGenerationRequest().getTarget().getIdentifier();

        // OTel Tracing Attributes
        Span span = Span.current();
        span.setAttribute("sbom.generationId", generationId);
        span.setAttribute("pnc.buildId", buildId);

        log.info("Starting PNC Build SBOM generation for Build ID: {}", buildId);

        try {
            statusUpdateService.reportGenerating(generationId);

            // 1. Fetch Build & identify the first built artifact to fetch SLSA Provenance
            Build build = pncService.getBuild(buildId);
            if (build == null) {
                throw new IllegalStateException("PNC Build " + buildId + " not found.");
            }

            List<Artifact> builtArtifacts = pncService.getBuiltArtifacts(buildId);
            if (builtArtifacts == null || builtArtifacts.isEmpty()) {
                throw new IllegalStateException("No artifacts were built in PNC Build " + buildId);
            }

            // Provenance is returned the same for all artifact IDs in the build, so we just need to
            // get a single one (first one) (TEMPORARY until we have a provenance-by-build-id endpoint)
            String firstArtifactId = builtArtifacts.get(0).getId();
            SlsaProvenance provenance = pncService.getBuildProvenance(firstArtifactId);

            if (provenance == null || provenance.subject() == null) {
                throw new IllegalStateException("SLSA Provenance is empty or invalid for artifact " + firstArtifactId);
            }

            // 2. Generate the SBOM
            String sbomJson = generateCycloneDxJson(build, provenance);

            // 3. Store and Update
            String uploadedUrl = storageService.uploadSbom(generationId, sbomJson);
            statusUpdateService.reportFinished(generationId, List.of(uploadedUrl));

        } catch (Throwable t) {
            log.error("Generation failed for PNC Build: {}", buildId, t);
            statusUpdateService.reportFailed(generationId, "PNC Build processing failed: " + t.getMessage());
            throw new RuntimeException("Failing Kafka message due to internal processing error", t);
        }
    }

    private String generateCycloneDxJson(Build build, SlsaProvenance provenance) {
        Bom bom = new Bom();

        // --- 1. Construct Root Component (From Build Attributes) ---
        Map<String, String> attributes = build.getAttributes() != null ? build.getAttributes() : Map.of();
        String brewName = attributes.getOrDefault("BREW_BUILD_NAME", "unknown-group:unknown-name");
        String brewVersion = attributes.getOrDefault("BREW_BUILD_VERSION", "unknown-version");

        String[] nameParts = brewName.split(":");
        String groupId = nameParts.length > 1 ? nameParts[0] : "unknown-group";
        String artifactId = nameParts.length > 1 ? nameParts[1] : brewName;

        String rootPurl = String.format("pkg:maven/%s/%s@%s?type=pom", groupId, artifactId, brewVersion);

        Component rootComponent = new Component();
        rootComponent.setGroup(groupId);
        rootComponent.setName(artifactId);
        rootComponent.setVersion(brewVersion);
        rootComponent.setPurl(rootPurl);
        rootComponent.setBomRef(rootPurl);
        rootComponent.setType(Component.Type.LIBRARY);

        // Metadata Setup
        Metadata metadata = new Metadata();
        metadata.setTimestamp(new java.util.Date());
        metadata.setComponent(rootComponent);

        org.cyclonedx.model.metadata.ToolInformation toolInfo = new org.cyclonedx.model.metadata.ToolInformation();
        org.cyclonedx.model.Service toolService = new org.cyclonedx.model.Service();
        toolService.setName(toolName);
        toolService.setVersion(toolVersion);
        toolInfo.setServices(List.of(toolService));
        metadata.setToolChoice(toolInfo);

        OrganizationalEntity supplier = new OrganizationalEntity();
        supplier.setName(supplierName);
        supplier.setUrls(supplierUrls);
        metadata.setSupplier(supplier);
        bom.setMetadata(metadata);

        // --- 2. Track Dependencies for the Graph ---
        Dependency rootDependency = new Dependency(rootPurl);
        bom.addDependency(rootDependency);

        if (bom.getComponents() == null) bom.setComponents(new ArrayList<>());
        List<Dependency> subjectDependencies = new ArrayList<>();

        boolean rootFoundInSubjects = false;

        // --- 3. Map Built Subjects (with Pedigree) ---
        for (SlsaSubject subject : provenance.subject()) {
            Component subjectComp = mapSubject(subject, build);

            // Prevent duplicate bom-refs
            if (bom.getComponents().stream().noneMatch(c -> c.getBomRef().equals(subjectComp.getBomRef()))) {
                bom.addComponent(subjectComp);
            }

            if (subjectComp.getBomRef().equals(rootPurl)) {
                // This subject IS the root! Update metadata to use this rich component instead of the basic fallback
                metadata.setComponent(subjectComp);
                rootFoundInSubjects = true;
                // Do NOT add it as a dependency of itself!
            } else {
                Dependency subjectDep = new Dependency(subjectComp.getBomRef());
                rootDependency.addDependency(subjectDep); // Root depends on built subjects
                subjectDependencies.add(subjectDep);

                bom.addDependency(subjectDep);
            }
        }

        // --- 3.5. Ensure Root Component is at the top of the components array ---
        if (!rootFoundInSubjects) {
            bom.getComponents().add(0, rootComponent); // Put basic fallback at the top
        } else {
            // Move the rich root component to the very first index
            Component richRoot = bom.getComponents().stream()
                    .filter(c -> c.getBomRef().equals(rootPurl))
                    .findFirst().orElse(null);
            if (richRoot != null) {
                bom.getComponents().remove(richRoot);
                bom.getComponents().add(0, richRoot);
            }
        }

        // --- 4. Map Resolved Dependencies (Upstream) ---
        if (provenance.predicate() != null && provenance.predicate().resolvedDependencies() != null) {
            for (SlsaDependency slsaDep : provenance.predicate().resolvedDependencies()) {
                // Skip meta records (repository, environment, etc. lacking a PURL)
                if (slsaDep.annotations() == null || slsaDep.annotations().purl() == null) {
                    continue;
                }

                Component depComp = mapResolvedDependency(slsaDep);

                // Prevent duplicate bom-refs if a dependency somehow matches an already mapped subject
                if (bom.getComponents().stream().noneMatch(c -> c.getBomRef().equals(depComp.getBomRef()))) {
                    bom.addComponent(depComp);
                }

                Dependency resolvedDepNode = new Dependency(depComp.getBomRef());
                bom.addDependency(resolvedDepNode);

                // In a build, the built subjects depend on the resolved dependencies
                for (Dependency subjectDep : subjectDependencies) {
                    subjectDep.addDependency(resolvedDepNode);
                }
            }
        }

        // --- 5. Generate Deterministic UUID and JSON ---
        try {
            BomJsonGenerator generator = new BomJsonGenerator(bom, Version.VERSION_16);
            String rawJson = generator.toJsonString();

            String deterministicUuid = UUID.nameUUIDFromBytes(rawJson.getBytes(StandardCharsets.UTF_8)).toString();
            bom.setSerialNumber("urn:uuid:" + deterministicUuid);

            // Re-generate to include the newly set serialNumber
            return new BomJsonGenerator(bom, Version.VERSION_16).toJsonString();
        } catch (Exception e) {
            throw new RuntimeException("Failed to serialize CycloneDX BOM to JSON", e);
        }
    }

    private Component mapSubject(SlsaSubject subject, Build build) {
        Component c = new Component();
        c.setType(Component.Type.LIBRARY);
        c.setName(subject.name());

        if (subject.annotations() != null && subject.annotations().purl() != null) {
            c.setPurl(subject.annotations().purl());
            c.setBomRef(subject.annotations().purl());

            // Try extracting group/name/version from PURL
            try {
                PackageURL purl = new PackageURL(subject.annotations().purl());
                c.setGroup(purl.getNamespace());
                c.setName(purl.getName());
                c.setVersion(purl.getVersion());
            } catch (Exception e) {
                log.debug("Could not parse PURL for subject {}", subject.name());
            }
        } else {
            c.setPurl("pkg:generic/" + subject.name());
            c.setBomRef("pkg:generic/" + subject.name());
            c.setVersion("unknown");
        }

        c.setHashes(mapHashes(subject.digest()));
        mapPncTraceability(c, subject.annotations(), build);
        mapPedigree(c, build);
        applyRedHatMetadataIfApplicable(c);

        return c;
    }

    private Component mapResolvedDependency(SlsaDependency slsaDep) {
        Component c = new Component();
        c.setType(Component.Type.LIBRARY);
        c.setName(slsaDep.name());
        c.setPurl(slsaDep.annotations().purl());
        c.setBomRef(slsaDep.annotations().purl());

        try {
            PackageURL purl = new PackageURL(slsaDep.annotations().purl());
            c.setGroup(purl.getNamespace());
            c.setName(purl.getName());
            c.setVersion(purl.getVersion());
        } catch (Exception e) {
            c.setVersion("unknown");
        }

        c.setHashes(mapHashes(slsaDep.digest()));
        mapPncTraceability(c, slsaDep.annotations(), null);
        applyRedHatMetadataIfApplicable(c);

        return c;
    }

    private List<Hash> mapHashes(Map<String, String> digestMap) {
        List<Hash> hashes = new ArrayList<>();
        if (digestMap == null) return hashes;

        if (digestMap.containsKey("md5")) hashes.add(new Hash(Hash.Algorithm.MD5, digestMap.get("md5")));
        if (digestMap.containsKey("sha1")) hashes.add(new Hash(Hash.Algorithm.SHA1, digestMap.get("sha1")));
        if (digestMap.containsKey("sha256")) hashes.add(new Hash(Hash.Algorithm.SHA_256, digestMap.get("sha256")));

        return hashes;
    }

    private void mapPncTraceability(Component c, org.jboss.sbomer.pnc.generator.core.domain.slsa.SlsaAnnotations annotations, Build build) {
        if (annotations == null) return;

        // Artifact ID Traceability
        if (annotations.artifactId() != null) {
            addExternalReference(c, ExternalReference.Type.BUILD_SYSTEM, pncApiUrl + "/pnc-rest/v2/artifacts/" + annotations.artifactId(), "pnc-artifact-id");
        }

        // Build ID Traceability
        if (annotations.buildId() != null) {
            addExternalReference(c, ExternalReference.Type.BUILD_SYSTEM, pncApiUrl + "/pnc-rest/v2/builds/" + annotations.buildId(), "pnc-build-id");
        }

        // Environment & VCS Traceability (Only available for Subjects, passed via `build` param)
        if (build != null) {
            if (build.getEnvironment() != null) {
                addExternalReference(c, ExternalReference.Type.BUILD_META, build.getEnvironment().getSystemImageRepositoryUrl() + "/" + build.getEnvironment().getSystemImageId(), "pnc-environment-image");
            }
            if (build.getScmRepository() != null && build.getScmRepository().getExternalUrl() != null) {
                addExternalReference(c, ExternalReference.Type.VCS, build.getScmRepository().getExternalUrl(), null);
            }
        }
    }

    private void mapPedigree(Component component, Build build) {
        if (build == null) return;

        List<Component> ancestorsList = new ArrayList<>();
        List<Commit> commitsList = new ArrayList<>();

        if (build.getScmUrl() != null && build.getScmRevision() != null) {
            String fullUrl = build.getScmTag() != null && !build.getScmTag().isBlank()
                    ? build.getScmUrl() + "#" + build.getScmTag() : build.getScmUrl();
            addPedigreeAncestor(ancestorsList, commitsList, component.getName(), fullUrl, build.getScmRevision());
        }

        if (build.getScmRepository() != null && build.getScmRepository().getExternalUrl() != null
                && build.getScmBuildConfigRevision() != null && build.getBuildConfigRevision() != null) {
            String fullUrl = build.getScmRepository().getExternalUrl() + "#" + build.getBuildConfigRevision().getScmRevision();
            addPedigreeAncestor(ancestorsList, commitsList, component.getName(), fullUrl, build.getScmBuildConfigRevision());
        }

        if (!ancestorsList.isEmpty() || !commitsList.isEmpty()) {
            Pedigree pedigree = new Pedigree();
            if (!ancestorsList.isEmpty()) {
                Ancestors ancestors = new Ancestors();
                ancestors.setComponents(ancestorsList);
                pedigree.setAncestors(ancestors);
            }
            if (!commitsList.isEmpty()) pedigree.setCommits(commitsList);
            component.setPedigree(pedigree);
        }
    }

    private void addPedigreeAncestor(List<Component> ancestorsList, List<Commit> commitsList, String fallbackName, String fullUrl, String commitHash) {
        if (fullUrl == null || fullUrl.isBlank()) return;

        // Ancestor logic
        try {
            VcsUrl vcsUrl = VcsUrl.create(fullUrl);
            PackageURL packageURL = vcsUrl.toPackageURL(commitHash);
            Component ancestor = new Component();
            ancestor.setType(Component.Type.LIBRARY);
            ancestor.setName(packageURL.getName());
            ancestor.setVersion(commitHash);
            ancestor.setPurl(packageURL.toString());
            ancestor.setBomRef(packageURL.toString());
            ancestorsList.add(ancestor);
        } catch (Exception e) {
            Component ancestor = new Component();
            ancestor.setType(Component.Type.LIBRARY);
            ancestor.setName(fallbackName);
            ancestor.setVersion(commitHash);
            ancestorsList.add(ancestor);
        }

        // Commit logic
        Commit commit = new Commit();
        commit.setUid(commitHash);
        commit.setUrl(fullUrl);
        commitsList.add(commit);
    }

    private void applyRedHatMetadataIfApplicable(Component c) {
        if (c.getVersion() != null && c.getVersion().contains("redhat-") ||
                (c.getPurl() != null && c.getPurl().contains("redhat-"))) {

            c.setPublisher(supplierName);
            OrganizationalEntity org = new OrganizationalEntity();
            org.setName(supplierName);
            org.setUrls(supplierUrls);
            c.setSupplier(org);

            addExternalReference(c, ExternalReference.Type.DISTRIBUTION, "https://maven.repository.redhat.com/ga/", null);
        }
    }

    private void addExternalReference(Component component, ExternalReference.Type type, String url, String comment) {
        List<ExternalReference> refs = component.getExternalReferences() != null
                ? new ArrayList<>(component.getExternalReferences()) : new ArrayList<>();
        ExternalReference ref = new ExternalReference();
        ref.setType(type);
        ref.setUrl(url);
        if (comment != null) ref.setComment(comment);
        refs.add(ref);
        component.setExternalReferences(refs);
    }
}