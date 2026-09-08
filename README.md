# 🐕 GoldPet

**GoldPet**은 반려동물의 산책, 프로필 관리, AI 프로필 생성, 친구 매칭, 커뮤니티 등 반려동물 중심의 통합 서비스를 제공하는 모바일 앱입니다.

---

## ✨ 주요 기능

- **회원 및 프로필:** SNS 간편가입, 사용자 및 반려동물 프로필 관리
- **산책 기능:** GPS 기반 산책 기록, 경로 저장, 산책 앨범 및 공개 범위 설정
- **친구 찾기 & 매칭:** 필터 기반 친구 추천 및 '좋아요'를 통한 상호 매칭
- **채팅:** 1:1 채팅, 그룹 채팅, AI 펫 챗봇 기능
- **AI 펫 프로필 생성:** 내 반려동물 사진으로 특별한 AI 프로필 이미지/영상 생성
- **커뮤니티:** 카테고리별 게시판 및 정보 공유
- **Gold 시스템:** 인앱 재화를 통한 프리미엄 기능 사용

---

## 🛠️ 기술 스택

- **Frontend:** Flutter
- **Backend:** Spring Boot (Kotlin/Java 21), Python (AI 서버)
- **Database:** PostgreSQL, Redis
- **Infrastructure:** AWS (ECS, RDS, S3, ElastiCache), Docker

---

## 🚀 로컬 개발 환경 실행

이 프로젝트는 Docker Compose를 사용하여 로컬 개발 환경을 쉽게 구성할 수 있습니다.

1.  **Docker 설치:** 시스템에 Docker와 Docker Compose가 설치되어 있는지 확인합니다.

2.  **프로젝트 클론:**
    ```bash
    git clone https://github.com/your-repo/goldpet.git
    cd goldpet
    ```

3.  **Docker Compose 실행:**
    프로젝트 루트 디렉토리에서 아래 명령어를 실행하여 모든 서비스(백엔드, AI, DB 등)를 시작합니다.
    ```bash
    docker-compose up -d
    ```

4.  **서비스 확인:**
    -   **Backend API Server:** `http://localhost:8080`
    -   **AI Server:** `http://localhost:8001` (Docker mapped port)
    -   **PostgreSQL:** `localhost:5432`
    -   **Redis:** `localhost:6380` (Docker mapped port)

---

> 이 README는 프로젝트의 주요 문서들을 기반으로 자동 생성 및 요약되었습니다.  
> 더 상세한 내용은 `@docs` 폴더 및 `GEMINI.md` 파일을 참고하세요.