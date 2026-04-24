package org.jboss.sbomer.pnc.generator.core.domain.slsa;

import java.util.Map;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

@JsonIgnoreProperties(ignoreUnknown = true)
public record SlsaExternalParameters(
    Map<String, Object> build,
    Map<String, String> environment,
    Map<String, String> repository
) {}
