#!/usr/bin/env bash
set -Eeuo pipefail

# =========================
# Docker 설치 원샷 스크립트 (Ubuntu)
# - 에러 발생 시 즉시 중단
# - 설치 결과(버전/상태/테스트) 출력
# =========================

# sudo 권한 확인
if [[ $EUID -ne 0 ]] && ! sudo -v &>/dev/null; then
  echo "❌ 이 스크립트는 sudo 권한이 필요합니다."
  echo "다음 명령으로 실행하세요:"
  echo "  sudo $0"
  exit 1
fi

LOG_FILE="/var/log/docker-install-$(date +%Y%m%d-%H%M%S).log"
exec > >(tee -a "$LOG_FILE") 2>&1

on_error() {
  local exit_code=$?
  echo
  echo "❌ ERROR: line $1 에서 실패 (exit code: $exit_code)"
  echo "로그 파일: $LOG_FILE"
  echo "마지막 80줄:"
  tail -n 80 "$LOG_FILE" || true
  exit "$exit_code"
}
trap 'on_error $LINENO' ERR

echo "== Docker 설치 시작 =="
echo "로그: $LOG_FILE"
echo "사용자: $(id -un) (UID=$(id -u))"
echo "호스트: $(hostname)"
echo "시간: $(date)"
echo

# ---- 0) OS 체크 ----
if [[ ! -f /etc/os-release ]]; then
  echo "❌ /etc/os-release 가 없습니다. Ubuntu 환경인지 확인하세요."
  exit 1
fi

. /etc/os-release
echo "OS: ${PRETTY_NAME:-unknown}"
echo "CODENAME: ${VERSION_CODENAME:-unknown}"
echo "ARCH: $(dpkg --print-architecture)"
echo

if [[ "${ID:-}" != "ubuntu" ]]; then
  echo "⚠️  이 스크립트는 Ubuntu 기준입니다. 현재 ID=${ID:-unknown}"
  echo "계속 진행하려면 Ctrl+C로 중단하고 수동으로 진행하세요."
  sleep 2
fi

# ---- 1) 기존 충돌 패키지 제거 (있으면) ----
echo "== 1) 기존/충돌 패키지 제거 =="
sudo apt-get remove -y docker.io docker-doc docker-compose docker-compose-v2 podman-docker containerd runc || true
echo

# ---- 2) 필수 패키지 설치 ----
echo "== 2) 필수 패키지 설치 =="
sudo apt-get update
sudo apt-get install -y ca-certificates curl gnupg
sudo install -m 0755 -d /etc/apt/keyrings
echo

# ---- 3) 기존 Docker 설정 완전 정리 ----
echo "== 3) 기존 Docker 설정 완전 정리 =="
# 모든 Docker 관련 APT 소스 파일 제거
sudo rm -f /etc/apt/sources.list.d/docker*.list
sudo rm -f /etc/apt/sources.list.d/docker*.sources  # Ubuntu 24.04+ DEB822 형식
sudo rm -f /etc/apt/sources.list.d/archive_uri-*docker*.list

# 모든 Docker 관련 GPG 키 제거
sudo rm -f /etc/apt/keyrings/docker*
sudo rm -f /usr/share/keyrings/docker*

# APT 캐시 정리
sudo apt-get clean
echo

# ---- 4) Docker 공식 GPG Key 등록 ----
echo "== 4) Docker GPG Key 등록 =="
curl -fsSL https://download.docker.com/linux/ubuntu/gpg | sudo gpg --dearmor -o /etc/apt/keyrings/docker.gpg
sudo chmod a+r /etc/apt/keyrings/docker.gpg
echo

# ---- 5) Docker 공식 APT repo 등록 ----
echo "== 5) Docker APT repo 등록 =="
ARCH="$(dpkg --print-architecture)"
CODENAME="$VERSION_CODENAME"
if [[ -z "$CODENAME" ]]; then
  echo "❌ VERSION_CODENAME 을 알 수 없습니다. /etc/os-release 확인 필요"
  exit 1
fi

sudo tee /etc/apt/sources.list.d/docker.list >/dev/null <<EOF
deb [arch=${ARCH} signed-by=/etc/apt/keyrings/docker.gpg] https://download.docker.com/linux/ubuntu ${CODENAME} stable
EOF

sudo apt-get update
echo

# ---- 6) Docker Engine + Compose 설치 ----
echo "== 6) Docker Engine + Compose 설치 =="
sudo apt-get install -y docker-ce docker-ce-cli containerd.io docker-buildx-plugin docker-compose-plugin
echo

# ---- 7) 서비스 활성화 ----
echo "== 7) docker 서비스 enable/start =="
sudo systemctl enable --now docker
echo

# ---- 8) 설치 결과 출력 ----
echo "== 8) 설치 결과(버전/상태) =="
echo "-- docker version --"
docker --version || true
echo "-- docker compose version --"
docker compose version || true
echo "-- systemctl status docker (요약) --"
sudo systemctl --no-pager --full status docker | sed -n '1,25p' || true
echo

# ---- 9) 테스트: hello-world ----
echo "== 9) 테스트: hello-world 실행 =="
sudo docker run --rm hello-world
echo

# ---- 10) (옵션) sudo 없이 docker 사용: docker 그룹 추가 ----
# 기본은 보안상 자동 적용하지 않음.
# 원하면 아래 환경변수를 주고 실행:
#   INSTALL_DOCKER_GROUP=1 ./install-docker.sh
if [[ "${INSTALL_DOCKER_GROUP:-0}" == "1" ]]; then
  echo "== 10) docker 그룹 설정(옵션) =="
  sudo groupadd docker 2>/dev/null || true
  sudo usermod -aG docker "$USER"
  echo "✅ 현재 사용자($USER)를 docker 그룹에 추가했습니다."
  echo "⚠️  새 그룹 권한 적용을 위해 아래 중 하나가 필요합니다:"
  echo "   - 로그아웃 후 재로그인"
  echo "   - 또는: newgrp docker"
  echo
else
  echo "== 10) docker 그룹 설정은 스킵 =="
  echo "sudo 없이 docker 쓰려면 다음처럼 실행하세요:"
  echo "  INSTALL_DOCKER_GROUP=1 $0"
  echo
fi

echo "🎉 Docker 설치 완료!"
echo "로그 파일: $LOG_FILE"
