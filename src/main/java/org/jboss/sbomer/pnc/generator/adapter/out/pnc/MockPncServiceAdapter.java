package org.jboss.sbomer.pnc.generator.adapter.out.pnc;

import jakarta.annotation.Priority;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.inject.Alternative;

import java.util.List;
import java.util.Map;

import org.jboss.pnc.dto.Artifact;
import org.jboss.pnc.dto.Build;
import org.jboss.pnc.dto.BuildConfigurationRevision;
import org.jboss.pnc.dto.Environment;
import org.jboss.pnc.dto.SCMRepository;
import org.jboss.pnc.enums.BuildType;
import org.jboss.sbomer.pnc.generator.core.domain.slsa.SlsaAnnotations;
import org.jboss.sbomer.pnc.generator.core.domain.slsa.SlsaDependency;
import org.jboss.sbomer.pnc.generator.core.domain.slsa.SlsaPredicate;
import org.jboss.sbomer.pnc.generator.core.domain.slsa.SlsaProvenance;
import org.jboss.sbomer.pnc.generator.core.domain.slsa.SlsaSubject;
import org.jboss.sbomer.pnc.generator.core.port.spi.PNCService;

import io.opentelemetry.instrumentation.annotations.SpanAttribute;
import io.opentelemetry.instrumentation.annotations.WithSpan;
import io.quarkus.arc.profile.IfBuildProfile;
import lombok.extern.slf4j.Slf4j;

@ApplicationScoped
// Temporarily prod until our external service is ready, then switch to mock
@IfBuildProfile("prod")
@Alternative
@Priority(1)
@Slf4j
public class MockPncServiceAdapter implements PNCService {

    public static final String MOCK_PNC_BUILD_ID = "MOCK-BUILD-001";
    public static final String MOCK_ARTIFACT_ID_ZIP = "9999999";
    public static final String MOCK_ARTIFACT_ID_POM = "9999998";
    public static final String MOCK_ARTIFACT_ID_YAML = "9999997";

    @Override
    @WithSpan
    public Build getBuild(@SpanAttribute("pnc.buildId") String buildId) {
        log.warn("🛡️ MOCK PNC: Providing Build metadata for {}", buildId);

        Environment mockEnv = Environment.builder()
                .systemImageRepositoryUrl("quay.io/acme-internal")
                .systemImageId("builder-linux-j17-mvn3.9.3:1.0.0")
                .name("OpenJDK 17.0; Linux; Mvn 3.9.3")
                .build();

        SCMRepository mockScmRepo = SCMRepository.builder()
                .id("1750")
                .internalUrl("git@git.acme.corp:acme-internal/acme-cloud-pack.git")
                .externalUrl("https://github.com/acme-org/acme-cloud-pack.git")
                .build();

        return Build.builder()
                .id(buildId != null ? buildId : MOCK_PNC_BUILD_ID)
                .environment(mockEnv)
                .scmRepository(mockScmRepo)
                .scmUrl("https://git.acme.corp/acme-internal/acme-cloud-pack.git")
                .scmRevision("810cd23b2f83e6f7cf59c7bd81add58fa1b7fa10")
                .scmTag("2.0.0.Final-redhat-00026")
                .scmBuildConfigRevision("213344e112f8f25859c0656ef226ca5d07e2da0c")
                .buildConfigRevision(BuildConfigurationRevision.builder()
                        .buildType(BuildType.MVN)
                        .scmRevision("2.0.0.Final-redhat-1")
                        .build())
                .attributes(Map.of(
                        "BREW_BUILD_VERSION", "2.0.0.Final-redhat-00026",
                        "BREW_BUILD_NAME", "com.acme.cloud:acme-cloud-pack-parent"
                ))
                .build();
    }

    @Override
    @WithSpan
    public List<Artifact> getBuiltArtifacts(@SpanAttribute("pnc.buildId") String buildId) {
        log.warn("🛡️ MOCK PNC: Providing Built Artifacts for {}", buildId);

        Artifact artifactZip = Artifact.builder()
                .id(MOCK_ARTIFACT_ID_ZIP)
                .identifier("com.acme.cloud:acme-cloud-pack:zip:2.0.0.Final-redhat-00026")
                .purl("pkg:maven/com.acme.cloud/acme-cloud-pack@2.0.0.Final-redhat-00026?type=zip")
                .filename("acme-cloud-pack-2.0.0.Final-redhat-00026.zip")
                .md5("cb60a9f1c944450f0f52d53cf1277d2e")
                .sha1("cb15697df1fc65c51e62a37a606d70fb973f260e")
                .sha256("c7d4778eb203284f71f374723c0ac083e66ae1e1536d030ab25141f8f55ea2e2")
                .build();

        Artifact artifactPom = Artifact.builder()
                .id(MOCK_ARTIFACT_ID_POM)
                .identifier("com.acme.cloud:acme-cloud-pack-parent:pom:2.0.0.Final-redhat-00026")
                .purl("pkg:maven/com.acme.cloud/acme-cloud-pack-parent@2.0.0.Final-redhat-00026?type=pom")
                .filename("acme-cloud-pack-parent-2.0.0.Final-redhat-00026.pom")
                .md5("a1b2c3d4e5f6a7b8c9d0e1f2a3b4c5d6")
                .sha1("1234567890abcdef1234567890abcdef12345678")
                .sha256("abcdef1234567890abcdef1234567890abcdef1234567890abcdef1234567890")
                .build();

        Artifact artifactYaml = Artifact.builder()
                .id(MOCK_ARTIFACT_ID_YAML)
                .identifier("com.acme.cloud:acme-cloud-pack:yaml:2.0.0.Final-redhat-00026:manifest")
                .purl("pkg:maven/com.acme.cloud/acme-cloud-pack@2.0.0.Final-redhat-00026?classifier=manifest&type=yaml")
                .filename("acme-cloud-pack-2.0.0.Final-redhat-00026-manifest.yaml")
                .md5("f1e2d3c4b5a6f7e8d9c0b1a2f3e4d5c6")
                .sha1("fedcba0987654321fedcba0987654321fedcba09")
                .sha256("0987654321fedcba0987654321fedcba0987654321fedcba0987654321fedcba")
                .build();

        return List.of(artifactZip, artifactPom, artifactYaml);
    }

    @Override
    @WithSpan
    public SlsaProvenance getBuildProvenance(@SpanAttribute("pnc.artifactId") String artifactId) {
        log.warn("🛡️ MOCK PNC: Providing SLSA Provenance for {}", artifactId);

        // --- SUBJECTS (What was built) ---
        SlsaSubject subjectZip = new SlsaSubject(
                "acme-cloud-pack-2.0.0.Final-redhat-00026.zip",
                Map.of("sha256", "c7d4778eb203284f71f374723c0ac083e66ae1e1536d030ab25141f8f55ea2e2", "sha1", "cb15697df1fc65c51e62a37a606d70fb973f260e", "md5", "cb60a9f1c944450f0f52d53cf1277d2e"),
                new SlsaAnnotations(
                        "com.acme.cloud:acme-cloud-pack:zip:2.0.0.Final-redhat-00026",
                        "pkg:maven/com.acme.cloud/acme-cloud-pack@2.0.0.Final-redhat-00026?type=zip",
                        "https://nexus.acme.corp/api/content/maven/hosted/builds/...",
                        MOCK_ARTIFACT_ID_ZIP,
                        MOCK_PNC_BUILD_ID
                )
        );

        SlsaSubject subjectPom = new SlsaSubject(
                "acme-cloud-pack-parent-2.0.0.Final-redhat-00026.pom",
                Map.of("sha256", "abcdef1234567890abcdef1234567890abcdef1234567890abcdef1234567890", "sha1", "1234567890abcdef1234567890abcdef12345678", "md5", "a1b2c3d4e5f6a7b8c9d0e1f2a3b4c5d6"),
                new SlsaAnnotations(
                        "com.acme.cloud:acme-cloud-pack-parent:pom:2.0.0.Final-redhat-00026",
                        "pkg:maven/com.acme.cloud/acme-cloud-pack-parent@2.0.0.Final-redhat-00026?type=pom",
                        "https://nexus.acme.corp/api/content/maven/hosted/builds/...",
                        MOCK_ARTIFACT_ID_POM,
                        MOCK_PNC_BUILD_ID
                )
        );

        SlsaSubject subjectYaml = new SlsaSubject(
                "acme-cloud-pack-2.0.0.Final-redhat-00026-manifest.yaml",
                Map.of("sha256", "0987654321fedcba0987654321fedcba0987654321fedcba0987654321fedcba", "sha1", "fedcba0987654321fedcba0987654321fedcba09", "md5", "f1e2d3c4b5a6f7e8d9c0b1a2f3e4d5c6"),
                new SlsaAnnotations(
                        "com.acme.cloud:acme-cloud-pack:yaml:2.0.0.Final-redhat-00026:manifest",
                        "pkg:maven/com.acme.cloud/acme-cloud-pack@2.0.0.Final-redhat-00026?classifier=manifest&type=yaml",
                        "https://nexus.acme.corp/api/content/maven/hosted/builds/...",
                        MOCK_ARTIFACT_ID_YAML,
                        MOCK_PNC_BUILD_ID
                )
        );

        // --- DEPENDENCIES (What was consumed) ---
        SlsaDependency dep1 = new SlsaDependency(
                "acme-clustering-web-service-8.1.1.GA-redhat-00015.jar",
                Map.of("sha256", "29140e5f3f35430050dec8d5eb7c026d45781fac422960bb63f9e80f66eba6bb", "sha1", "dummy-sha1-1", "md5", "dummy-md5-1"),
                "https://nexus.acme.corp/api/content/maven/hosted/builds/...",
                new SlsaAnnotations(
                        "com.acme.corp:acme-clustering-web-service:jar:8.1.1.GA-redhat-00015",
                        "pkg:maven/com.acme.corp/acme-clustering-web-service@8.1.1.GA-redhat-00015?type=jar",
                        "https://nexus.acme.corp/api/content/maven/hosted/builds/...",
                        "8888",
                        "BUILD-8888"
                )
        );

        // Standard OSS Dependency - no redhat suffix (Should NOT receive Red Hat publisher/supplier metadata)
        SlsaDependency dep2 = new SlsaDependency(
                "slf4j-api-1.7.30.pom",
                Map.of("sha256", "7e0747751e9b67e19dcb5206f04ea22cc03d250c422426402eadd03513f2c314", "sha1", "dummy-sha1-2", "md5", "dummy-md5-2"),
                "https://nexus.acme.corp/api/content/maven/hosted/shared-imports/...",
                new SlsaAnnotations(
                        "org.slf4j:slf4j-api:pom:1.7.30",
                        "pkg:maven/org.slf4j/slf4j-api@1.7.30?type=pom",
                        "https://nexus.acme.corp/api/content/maven/hosted/shared-imports/...",
                        "8889",
                        null // External OSS might not have a PNC Build ID!
                )
        );

        // Another Red Hat built dependency
        SlsaDependency dep3 = new SlsaDependency(
                "jackson-core-2.17.0.redhat-00001.jar",
                Map.of("sha256", "55be130f6a68038088a261856c4e383ce79957a0fc1a29ecb213a9efd6ef4389", "sha1", "dummy-sha1-3", "md5", "dummy-md5-3"),
                "https://nexus.acme.corp/api/content/maven/hosted/builds/...",
                new SlsaAnnotations(
                        "com.fasterxml.jackson.core:jackson-core:jar:2.17.0.redhat-00001",
                        "pkg:maven/com.fasterxml.jackson.core/jackson-core@2.17.0.redhat-00001?type=jar",
                        "https://nexus.acme.corp/api/content/maven/hosted/builds/...",
                        "8890",
                        "BUILD-8890"
                )
        );

        SlsaPredicate predicate = new SlsaPredicate(List.of(
                new SlsaDependency("repository", null, null, null), // Skip 1
                new SlsaDependency("repository.downstream", null, null, null), // Skip 2
                new SlsaDependency("environment", null, null, null), // Skip 3
                dep1,
                dep2,
                dep3
        ));

        return new SlsaProvenance(predicate, List.of(subjectZip, subjectPom, subjectYaml));
    }
}