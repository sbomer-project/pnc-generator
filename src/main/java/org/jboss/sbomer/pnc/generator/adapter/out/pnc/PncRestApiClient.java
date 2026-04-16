package org.jboss.sbomer.pnc.generator.adapter.out.pnc;

import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;

import org.eclipse.microprofile.rest.client.inject.RegisterRestClient;
import org.jboss.sbomer.pnc.generator.core.domain.slsa.SlsaProvenance;

@RegisterRestClient(configKey = "pnc-api")
@Path("/pnc-rest/v2")
@Produces(MediaType.APPLICATION_JSON)
public interface PncRestApiClient {

    @GET
    @Path("/slsa/build-provenance/v1/artifacts/id/{artifactId}")
    SlsaProvenance getBuildProvenanceFallback(@PathParam("artifactId") String artifactId);

}