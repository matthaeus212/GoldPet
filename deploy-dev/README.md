# deploy-dev: 개발 서버 환경

mannamsquare.com 개발 서버 배포에 필요한 설정 파일들입니다.

## 환경

| 환경 | 도메인 | 폴더 |
|-----|--------|------|
| **local** | localhost | deploy-local/ |
| **dev** | *.mannamsquare.com | deploy-dev/ (현재) |
| **prod** | *.goldpet.com | deploy-prod/ (예정) |

## 폴더 구조

```
deploy-dev/
├── docker/
│   ├── docker-compose.yml    # 서버용 Docker 서비스
│   └── install-docker.sh     # Docker 설치 스크립트 (Ubuntu)
├── nginx/
│   ├── nginx.conf            # Nginx 메인 설정
│   ├── sites-available/
│   │   ├── mannam.conf       # mannamsquare.com 설정
│   │   └── jenkins.mannamsquare.com
│   └── ssl/
│       └── ssl-params.conf   # SSL/TLS 공통 설정
├── systemd/
│   └── services/
│       └── goldpet-api.service  # Spring Boot API 서비스
├── logrotate/
│   ├── logrotate-goldpet-api    # API 로그 로테이션
│   └── logrotate-goldpet-nginx  # Nginx 로그 로테이션
└── scripts/
    ├── deploy-api.sh         # API 배포
    ├── deploy-frontend.sh    # Frontend 배포
    └── deploy-admin.sh       # Admin 배포
```

## 서버 설정 적용

### 1. Docker 설치 (최초 1회)

```bash
sudo ./docker/install-docker.sh
```

### 2. Docker 서비스 시작

```bash
cd docker
docker compose up -d
```

### 3. Nginx 설정

```bash
# 설정 파일 복사
sudo cp nginx/nginx.conf /etc/nginx/
sudo cp nginx/sites-available/* /etc/nginx/sites-available/
sudo mkdir -p /etc/nginx/ssl
sudo cp nginx/ssl/* /etc/nginx/ssl/

# 사이트 활성화
sudo ln -sf /etc/nginx/sites-available/mannam.conf /etc/nginx/sites-enabled/
sudo ln -sf /etc/nginx/sites-available/jenkins.mannamsquare.com /etc/nginx/sites-enabled/

# 테스트 및 재로드
sudo nginx -t && sudo systemctl reload nginx
```

### 4. Systemd 서비스

```bash
sudo cp systemd/services/goldpet-api.service /etc/systemd/system/
sudo systemctl daemon-reload
sudo systemctl enable --now goldpet-api
```

### 5. 로그 로테이션

```bash
sudo cp logrotate/* /etc/logrotate.d/
```

## 도메인 구성 (mannamsquare.com)

| 도메인 | 서비스 | 포트 |
|-------|--------|------|
| mannamsquare.com | Next.js 웹사이트 | 443 |
| api.mannamsquare.com | Spring Boot API | 8081 |
| app.mannamsquare.com | React Frontend | 443 (정적) |
| admin.mannamsquare.com | React Admin | 443 (정적) |
| jenkins.mannamsquare.com | Jenkins CI/CD | 8080 |

## Jenkins CI/CD

| 프로젝트 | 파라미터 | dev | prod |
|---------|---------|-----|------|
| API | PROFILE | dev | prod |
| Frontend | ENV | dev | prod |
| Admin | ENV | dev | prod |

## 관련 문서

- 로컬 개발: [deploy-local/](../deploy-local/)
- 개발 가이드: [docs/LOCAL_DEVELOPMENT.md](../docs/LOCAL_DEVELOPMENT.md)
