# PNC Generator Service

The **PNC Generator Service** is a specialized microservice within the SBOMer architecture responsible for executing SBOM generation requests based on builds performed by [Project Newcastle (PNC)](https://github.com/project-ncl).

Unlike legacy generators that require heavy, resource-intensive build pods, this service acts as a high-performance **API-Driven Synthesizer**. It retrieves structured build metadata and SLSA provenance from PNC to construct a complete, cryptographically-linked SBOM in the CycloneDX specification.

---

## Architecture

This service follows **Hexagonal Architecture (Ports and Adapters)** to decouple the business logic of SBOM construction from external infrastructure constraints like Kafka or PNC APIs.

### 1. Core Domain (Business Logic)
* **`PncBuildGenerationService`**: The "Brain". It manages the orchestration of the generation request. It fetches the PNC Build metadata, identifies produced artifacts, retrieves the SLSA Provenance graph, and synthesizes them into a single aggregate SBOM.
* **SLSA-to-CycloneDX Mapping**: Maps the SLSA `subject` array to primary components and the `resolvedDependencies` array to upstream library components. It ensures the root component is enriched with hashes and pedigree and is duplicated at the top of the components array for platform compatibility.

### 2. Driving Adapters (Input)
* **`KafkaRequestConsumer`**: Listens to the `generation.created` topic. If the request's target type matches `PNC_BUILD`, it initiates the generation process.

### 3. Driven Adapters (Output)
* **`PncServiceAdapter`**: Communicates with the PNC APIs to retrieve Build metadata, artifact lists, and SLSA provenance. It wraps the official PNC Java clients and includes a Quarkus REST Client fallback (`PncRestApiClient`) for the new SLSA provenance endpoints.
* **`HttpStorageServiceAdapter`**: Packages the generated JSON SBOMs and uploads them atomically to the [Manifest Storage Service](https://github.com/sbomer-project/manifest-storage-service).
* **`KafkaStatusUpdateService`**: Sends `generation.update` events (GENERATING, FINISHED, FAILED) back to the control plane.

---

## Features

### 1. API-Driven Synthesis (No Tekton/Maven)
This service eliminates the need for Tekton build pods or the `cyclonedx-maven-plugin`. By synthesizing the SBOM from PNC's existing database records, it reduces generation time from minutes to milliseconds and significantly lowers CPU and memory consumption.

### 2. SLSA Provenance Integration
The service utilizes the SLSA Build Provenance specification to build the dependency tree. It distinguishes between **Subjects** (the artifacts produced by the build) and **Resolved Dependencies** (the artifacts consumed during the build), creating a highly accurate execution-based dependency graph.

### 3. Red Hat Ecosystem Tagging
The service automatically detects Red Hat artifacts via `.redhat-xxxxx` version suffixes or PURLs. When detected, it stamps the CycloneDX components with Red Hat Publisher, Supplier, and official Red Hat Maven distribution metadata.

### 4. Native Traceability
Using SLSA provenance annotations, the generator injects `pnc-artifact-id` and `pnc-build-id` into every component (both built and consumed) as CycloneDX `ExternalReferences`, providing a direct cryptographic link back to the build system records.

### 5. Deterministic Serial Numbers
To ensure reproducible SBOMs and predictable updates, the service generates UUID v3 (Name-based) serial numbers based on the raw JSON content of the BOM before finalization.

---

## Configuration

| Property | Description | Default |
| :--- | :--- | :--- |
| `sbomer.generator.tool.name` | The name of the tool injected into the Root Component metadata. | `SBOMer NextGen` |
| `sbomer.generator.tool.version` | The version of the tool injected into the Root Component metadata. | `1.0.0` |
| `sbomer.generator.supplier.name` | The supplier name injected into the Root Component metadata. | `Red Hat` |
| `sbomer.generator.supplier.urls` | Comma-separated list of supplier URLs for the Root Component metadata. | `https://www.redhat.com` |
| `pnc.api.url` | The base URL for the PNC official Java Clients to fetch build data. | *(Environment specific)* |

---

## Development Environment Setup

We can run this component in a **Minikube Environment** by injecting it as part of the `sbomer-platform` helm chart.

### 1. Prerequisites
* **Podman** (or Docker)
* **Minikube**
* **Helm**, **Kubectl**, **Maven**, **Java 17+**

### 2. Prepare the Cluster
First, ensure the `sbomer` Minikube profile is running:

```bash
./hack/setup-local-dev.sh
```

### 3. Run the Component with Helm in Minikube
Use the provided script to build the `pnc-generator` image and deploy it into the cluster:

```bash
./hack/run-helm-with-local-build.sh
```

### 4. Verify the Installation
Verify the status of the generator pod:

```bash
kubectl get pods -n sbomer | grep pnc-generator
```

Trigger a test generation using the provided script:

```bash
./hack/test-pnc-gen.sh
```

*(Note: During local development, the service uses the `MockPncServiceAdapter` to provide anonymized, fake build data, avoiding the need for a direct VPN connection to internal PNC production servers).*