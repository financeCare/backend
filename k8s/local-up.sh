#!/usr/bin/env bash
# Local Kubernetes (kind) for FinanceCare, with the HPA working.
#   ./k8s/local-up.sh        create the cluster, build + load the image, deploy
#   ./k8s/local-down.sh      delete the cluster
# Needs: docker, kind, kubectl.
set -euo pipefail
cd "$(dirname "$0")/.."

CLUSTER=financecare
IMAGE=financecare-backend:k8s-local
# metrics-server (the HPA reads CPU from it). Pinned; kind's kubelet uses a self-signed certificate, hence the patch below.
METRICS_SERVER=https://github.com/kubernetes-sigs/metrics-server/releases/download/v0.7.2/components.yaml

kind get clusters | grep -qx "$CLUSTER" || kind create cluster --name "$CLUSTER" --config k8s/kind-config.yaml

docker build -t "$IMAGE" .
kind load docker-image "$IMAGE" --name "$CLUSTER"

kubectl apply -f "$METRICS_SERVER"
kubectl -n kube-system patch deployment metrics-server --type=json \
  -p '[{"op":"add","path":"/spec/template/spec/containers/0/args/-","value":"--kubelet-insecure-tls"}]' || true

kubectl apply -f k8s/datastores.yaml
# Throwaway credentials for the local cluster only. JWT_SECRET must be base64.
kubectl -n financecare create secret generic backend-secrets \
  --from-literal=DB_PASSWORD=local-only-password \
  --from-literal=JWT_SECRET="$(printf 'local-cluster-only-secret-key-0123456789abcdef' | base64 -w0)" \
  --from-literal=JOOBLE_API_KEY=local \
  --from-literal=MAIL_USERNAME=local@example.com \
  --from-literal=MAIL_PASSWORD=local \
  --from-literal=MINIO_SECRET_KEY=local \
  --dry-run=client -o yaml | kubectl apply -f -
kubectl apply -f k8s/backend.yaml

kubectl -n financecare rollout status deployment/postgres deployment/redis --timeout=180s
kubectl -n financecare rollout status deployment/backend --timeout=300s
echo "Backend: http://localhost:18090/api   (kubectl -n financecare get hpa -w)"
