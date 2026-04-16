#!/bin/bash

# Exit immediately if a command fails
set -e

# Variables for minikube profile and pnc-generator
SBOM_SERVICE_IMAGE="pnc-generator:latest"
PROFILE="sbomer"
TAR_FILE="pnc-generator.tar"

echo "--- Building and inserting pnc-generator image into Minikube registry ---"

bash ./hack/build-with-schemas.sh prod,mock

podman build --format docker -t "$SBOM_SERVICE_IMAGE" -f src/main/docker/Dockerfile.jvm .

echo "--- Exporting pnc-generator image to archive ---"
if [ -f "$TAR_FILE" ]; then
    rm "$TAR_FILE"
fi
podman save -o "$TAR_FILE" "$SBOM_SERVICE_IMAGE"

echo "--- Loading pnc-generator into Minikube ---"
# This sends the file to Minikube
minikube -p "$PROFILE" image load "$TAR_FILE"

echo "--- Cleanup ---"
rm "$TAR_FILE"

echo "Done! Image '$SBOM_SERVICE_IMAGE' is ready in cluster '$PROFILE'."