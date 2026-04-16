package org.jboss.sbomer.pnc.generator.core.domain.slsa;

import java.util.Map;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

@JsonIgnoreProperties(ignoreUnknown = true)
public record SlsaDependency(
    String name,
    Map<String, String> digest,
    String uri,
    SlsaAnnotations annotations
) {}
