#!/usr/bin/env bash
set -euo pipefail

PROJECT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
ENV_FILE="${PROJECT_DIR}/.env"

# Colors for output.
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
NC='\033[0m' # No Color

log_info() { echo -e "${GREEN}[INFO]${NC} $*"; }
log_warn() { echo -e "${YELLOW}[WARN]${NC} $*"; }
log_error() { echo -e "${RED}[ERROR]${NC} $*" >&2; }

# --- Check Docker ---
if ! command -v docker &>/dev/null; then
  log_error "Docker is not installed. Please install Docker first: https://docs.docker.com/get-docker/"
  exit 1
fi

if docker compose version &>/dev/null; then
  COMPOSE_CMD="docker compose"
elif command -v docker-compose &>/dev/null; then
  COMPOSE_CMD="docker-compose"
else
  log_error "Docker Compose is not installed. Please install Docker Compose plugin."
  exit 1
fi

if ! docker info &>/dev/null; then
  log_error "Docker daemon is not running. Please start Docker first."
  exit 1
fi

log_info "Using Docker Compose command: ${COMPOSE_CMD}"

# --- Generate or load .env ---
if [[ -f "${ENV_FILE}" ]]; then
  log_info "Loading existing configuration from ${ENV_FILE}"
  # shellcheck source=/dev/null
  source "${ENV_FILE}"
fi

# Default values.
DEFAULT_PORT="${PORT:-80}"
DEFAULT_DATA_DIR="${DATA_DIR:-${PROJECT_DIR}/data}"
DEFAULT_ADMIN_PASSWORD="${ADMIN_PASSWORD:-}"

# Non-interactive mode: if all required vars are already set, skip prompts.
NON_INTERACTIVE=false
if [[ -n "${PORT:-}" && -n "${DATA_DIR:-}" && -n "${ADMIN_PASSWORD:-}" ]]; then
  NON_INTERACTIVE=true
fi

# Helper to prompt with default.
prompt_with_default() {
  local prompt_text="$1"
  local default_value="$2"
  local result
  if [[ -n "${default_value}" ]]; then
    read -rp "${prompt_text} [${default_value}]: " result
  else
    read -rp "${prompt_text}: " result
  fi
  echo "${result:-${default_value}}"
}

# Generate random password if no default.
generate_password() {
  tr -dc 'A-Za-z0-9' </dev/urandom | head -c 16 || true
}

if [[ -z "${DEFAULT_ADMIN_PASSWORD}" ]]; then
  DEFAULT_ADMIN_PASSWORD="$(generate_password)"
  log_warn "Generated random admin password: ${DEFAULT_ADMIN_PASSWORD}"
fi

if [[ "${NON_INTERACTIVE}" == "true" ]]; then
  log_info "Running in non-interactive mode using existing configuration"
else
  echo ""
  echo "Please configure the deployment (press Enter to accept defaults):"
  PORT="$(prompt_with_default "HTTP port" "${DEFAULT_PORT}")"
  DATA_DIR="$(prompt_with_default "Host data directory" "${DEFAULT_DATA_DIR}")"
  ADMIN_PASSWORD="$(prompt_with_default "Admin password" "${DEFAULT_ADMIN_PASSWORD}")"
fi

# Convert DATA_DIR to absolute path.
mkdir -p "${DATA_DIR}"
DATA_DIR="$(cd "${DATA_DIR}" && pwd)"

# Write .env.
cat > "${ENV_FILE}" <<EOF
PORT=${PORT}
DATA_DIR=${DATA_DIR}
ADMIN_PASSWORD=${ADMIN_PASSWORD}
EOF

log_info "Configuration saved to ${ENV_FILE}"

# --- Deploy ---
cd "${PROJECT_DIR}"
log_info "Stopping existing containers (if any)..."
${COMPOSE_CMD} down || true

log_info "Building and starting containers..."
${COMPOSE_CMD} up -d --build

# --- Wait for health check ---
log_info "Waiting for backend health check..."
HEALTH_URL="http://localhost:${PORT}/health"
for i in $(seq 1 60); do
  if curl -fsS "${HEALTH_URL}" &>/dev/null; then
    log_info "Backend is healthy."
    break
  fi
  if [[ ${i} -eq 60 ]]; then
    log_error "Backend health check timed out after 60 seconds."
    log_error "Check logs with: ${COMPOSE_CMD} logs -f backend"
    exit 1
  fi
  sleep 1
done

# --- Output access info ---
echo ""
echo "=============================================="
echo "  OFD Converter deployed successfully!"
echo "=============================================="
echo ""
echo "  Frontend:     http://localhost:${PORT}"
echo "  Admin page:   http://localhost:${PORT}/admin"
echo "  API base:     http://localhost:${PORT}/api/"
echo "  Health check: http://localhost:${PORT}/health"
echo ""
echo "  Admin password: ${ADMIN_PASSWORD}"
echo "  Data directory: ${DATA_DIR}"
echo ""
echo "  View logs:"
echo "    ${COMPOSE_CMD} logs -f backend"
echo "    ${COMPOSE_CMD} logs -f frontend"
echo ""
echo "=============================================="
