# GoldPet – 최종 ERD v1.0 (PostgreSQL + PostGIS 기반)

> 본 문서는 지금까지 정의한 PRD/UX 내용을 종합하여,  
> **PostgreSQL + PostGIS** 확장을 사용하는 GoldPet의 최종 ERD 초안을 정리한 것이다.  
> 위치 기반 기능(산책 궤적, Spot, 친구 위치 검색 등)을 위해 주요 위치 컬럼은 PostGIS `geometry` 타입을 사용한다.

---

## 0. 공통 설계 원칙

- DB: **PostgreSQL**
- 확장: **PostGIS** 사용
  - 좌표계: `SRID 4326` (WGS84, 경도/위도)
  - 주요 타입:
    - `geometry(Point, 4326)` : 사용자 위치, Spot 위치 등
    - `geometry(LineString, 4326)` : 산책 경로
- 모든 테이블 공통 컬럼(필요한 곳):
  - `created_at TIMESTAMP NOT NULL`
  - `updated_at TIMESTAMP NOT NULL`
  - 소프트 삭제가 필요한 경우 `deleted_at TIMESTAMP NULL` (옵션)
- 금액/포인트(Gold 등)는 정수(INT) 기준으로 관리

---

## 1. 유저 & 펫 도메인

### 1.1 users (사용자)

| 컬럼명              | 타입                     | 설명                                     |
|---------------------|--------------------------|------------------------------------------|
| id                  | BIGSERIAL PK            | 사용자 ID                                |
| email               | VARCHAR(255) UNIQUE NULL| 이메일 (SNS 제공 시)                     |
| oauth_provider      | VARCHAR(50)             | `KAKAO`, `NAVER`, `GOOGLE`, `APPLE` 등   |
| oauth_id            | VARCHAR(255)            | OAuth 고유 ID                            |
| nickname            | VARCHAR(50)             | 닉네임                                   |
| gender              | VARCHAR(10) NULL        | 성별 (옵션)                              |
| birth_year          | INT NULL                | 출생 연도 (옵션)                         |
| main_location_text  | VARCHAR(255) NULL       | 주요 활동 지역(동/구 등 텍스트)         |
| main_location_geom  | geometry(Point, 4326)   | 주요 활동 지역 중심 좌표                 |
| profile_image_url   | VARCHAR(500) NULL       | 프로필 이미지 URL                        |
| gold_balance        | INT NOT NULL DEFAULT 0  | 현재 보유 Gold                           |
| is_active           | BOOLEAN NOT NULL DEFAULT TRUE | 계정 활성 여부                  |
| created_at          | TIMESTAMP NOT NULL      | 생성 일시                                |
| updated_at          | TIMESTAMP NOT NULL      | 수정 일시                                |

**인덱스 예시**

- `UNIQUE (oauth_provider, oauth_id)`
- `GIST (main_location_geom)` – 주변 친구 찾기(거리 검색)용

---

### 1.2 user_location_history (옵션 – 최근 위치 기록)

> 실시간/주기적 위치 기반 추천을 강화할 필요가 있을 경우 사용

| 컬럼명        | 타입                     | 설명                              |
|---------------|--------------------------|-----------------------------------|
| id            | BIGSERIAL PK            | 기록 ID                           |
| user_id       | BIGINT FK → users(id)   | 사용자 ID                         |
| location_geom | geometry(Point, 4326)   | 위치 좌표                         |
| accuracy_m    | FLOAT NULL              | 정확도(미터)                      |
| created_at    | TIMESTAMP NOT NULL      | 기록 시각                         |

**인덱스**

- `INDEX (user_id)`
- `GIST (location_geom)`
- 오래된 레코드 정기 삭제(예: 30일 기준) 정책 필요

---

### 1.3 pet_species (펫 종)

| 컬럼명    | 타입           | 설명                           |
|-----------|----------------|--------------------------------|
| id        | SERIAL PK      | 종 ID                          |
| code      | VARCHAR(50)    | 내부 코드 (`DOG`, `CAT`, ...) |
| name      | VARCHAR(50)    | 표시 이름 (개, 고양이, 조류...)|
| description | TEXT NULL    | 설명                           |
| created_at| TIMESTAMP      | 생성일                         |

---

### 1.4 pet_breeds (펫 품종)

| 컬럼명      | 타입                   | 설명                     |
|-------------|------------------------|--------------------------|
| id          | SERIAL PK              | 품종 ID                  |
| species_id  | INT FK → pet_species  | 종 FK                    |
| name        | VARCHAR(100)           | 품종명                   |
| description | TEXT NULL              | 설명                     |
| created_at  | TIMESTAMP              | 생성일                   |

인덱스:

- `INDEX (species_id)`

---

### 1.5 pets (펫 기본 정보)

| 컬럼명          | 타입                    | 설명                                   |
|-----------------|-------------------------|----------------------------------------|
| id              | BIGSERIAL PK           | 펫 ID                                  |
| owner_user_id   | BIGINT FK → users(id)  | 소유자 사용자 ID                       |
| name            | VARCHAR(50)            | 펫 이름                                |
| species_id      | INT FK → pet_species   | 종 (개, 고양이 등)                     |
| breed_id        | INT FK → pet_breeds    | 품종                                   |
| gender          | VARCHAR(10) NULL       | 성별                                   |
| birth_date      | DATE NULL              | 생일                                   |
| weight_kg       | DECIMAL(4,1) NULL      | 체중                                   |
| is_neutered     | BOOLEAN NULL           | 중성화 여부                            |
| profile_image_url | VARCHAR(500) NULL    | 펫 프로필 이미지                       |
| temperament_tags | VARCHAR(255) NULL     | 간단 태그(쉼표 구분) 예: `활발,친화적` |
| created_at      | TIMESTAMP NOT NULL     | 생성일                                 |
| updated_at      | TIMESTAMP NOT NULL     | 수정일                                 |

인덱스:

- `INDEX (owner_user_id)`
- `INDEX (species_id, breed_id)`

---

### 1.6 pet_attribute_defs (펫 속성 정의 – 확장성용)

| 컬럼명      | 타입                 | 설명                                               |
|-------------|----------------------|----------------------------------------------------|
| id          | SERIAL PK            | 속성 정의 ID                                      |
| code        | VARCHAR(50) UNIQUE   | 속성 코드 (예: `ALLERGY`, `FAVORITE_FOOD`)        |
| name        | VARCHAR(100)         | 속성 이름                                         |
| species_id  | INT NULL FK → pet_species | 특정 종에만 적용 시 지정                      |
| data_type   | VARCHAR(20)          | `STRING`, `NUMBER`, `BOOLEAN`, `ENUM` 등          |
| enum_values | TEXT NULL            | ENUM일 경우 허용 값 리스트(JSON/text)             |
| created_at  | TIMESTAMP NOT NULL   | 생성일                                            |

---

### 1.7 pet_attribute_values (펫 속성 값)

| 컬럼명           | 타입                      | 설명                                     |
|------------------|---------------------------|------------------------------------------|
| id               | BIGSERIAL PK             | 속성 값 ID                               |
| pet_id           | BIGINT FK → pets(id)     | 펫 ID                                    |
| attribute_def_id | INT FK → pet_attribute_defs(id) | 속성 정의 ID                    |
| value_string     | TEXT NULL                | 문자열 값                                |
| value_number     | DECIMAL(10,2) NULL       | 숫자 값                                  |
| value_boolean    | BOOLEAN NULL             | boolean 값                               |
| created_at       | TIMESTAMP NOT NULL       | 생성일                                   |

> 실제 데이터는 data_type에 따라 하나의 컬럼만 사용.

---

## 2. 산책 & 위치(지도) 도메인 (PostGIS 핵심)

### 2.1 walk_albums (산책 앨범)

| 컬럼명        | 타입                    | 설명                                     |
|---------------|-------------------------|------------------------------------------|
| id            | BIGSERIAL PK           | 앨범 ID                                  |
| user_id       | BIGINT FK → users(id)  | 사용자 ID                                |
| title         | VARCHAR(100)           | 앨범 제목                                |
| description   | TEXT NULL              | 설명                                     |
| cover_walk_id | BIGINT NULL FK → walk_records(id) | 대표 산책 기록                      |
| visibility    | VARCHAR(20)            | PUBLIC / FRIENDS / PRIVATE               |
| created_at    | TIMESTAMP NOT NULL     | 생성일                                   |
| updated_at    | TIMESTAMP NOT NULL     | 수정일                                   |

---

### 2.2 walk_records (산책 기록)

> 산책 궤적을 PostGIS `LineString`으로 저장.  
> API에서는 필요시 GeoJSON 형태로 전달.

| 컬럼명        | 타입                        | 설명                                       |
|---------------|-----------------------------|--------------------------------------------|
| id            | BIGSERIAL PK               | 산책 기록 ID                               |
| user_id       | BIGINT FK → users(id)      | 사용자 ID                                  |
| pet_id        | BIGINT FK → pets(id)       | 대표 펫 ID                                 |
| album_id      | BIGINT NULL FK → walk_albums(id) | 앨범 ID                              |
| title         | VARCHAR(100)               | 산책 제목                                  |
| distance_m    | INT                        | 이동 거리(m)                               |
| duration_sec  | INT                        | 산책 시간(초)                              |
| user_calorie  | INT NULL                   | 사용자 칼로리                              |
| pet_calorie   | INT NULL                   | 펫 칼로리                                  |
| started_at    | TIMESTAMP NOT NULL         | 시작 시각                                  |
| ended_at      | TIMESTAMP NOT NULL         | 종료 시각                                  |
| route_geom    | geometry(LineString, 4326) | 산책 궤적(경로)                            |
| center_point  | geometry(Point, 4326) NULL | 궤적의 중심 포인트 (지도 초기 카메라 중심) |
| visibility    | VARCHAR(20)                | PUBLIC / FRIENDS / PRIVATE                 |
| created_at    | TIMESTAMP NOT NULL         | 생성일                                     |
| updated_at    | TIMESTAMP NOT NULL         | 수정일                                     |

**인덱스**

- `INDEX (user_id)`
- `GIST (route_geom)`
- `GIST (center_point)` – 주변 산책 코스 추천용

---

### 2.3 walk_spots (산책 중 사진 Spot)

| 컬럼명       | 타입                      | 설명                                       |
|--------------|---------------------------|--------------------------------------------|
| id           | BIGSERIAL PK             | Spot ID                                    |
| walk_id      | BIGINT FK → walk_records(id) | 산책 기록 FK                           |
| location_geom| geometry(Point, 4326)    | Spot 위치 (사진 찍은 위치)                |
| photo_url    | VARCHAR(500) NULL        | 사진 URL                                   |
| memo         | TEXT NULL                | 메모                                       |
| taken_at     | TIMESTAMP NOT NULL       | 촬영 시각                                  |
| created_at   | TIMESTAMP NOT NULL       | 생성일                                     |

**인덱스**

- `INDEX (walk_id)`
- `GIST (location_geom)`

---

## 3. 매칭 & 친구 도메인

### 3.1 likes (단방향 좋아요)

| 컬럼명       | 타입                    | 설명                                   |
|--------------|-------------------------|----------------------------------------|
| id           | BIGSERIAL PK           | 좋아요 ID                              |
| from_user_id | BIGINT FK → users(id)  | 좋아요를 보낸 사용자                   |
| to_user_id   | BIGINT FK → users(id)  | 좋아요를 받은 사용자                   |
| created_at   | TIMESTAMP NOT NULL     | 생성일                                 |
| status       | VARCHAR(20)            | ACTIVE / CANCELED                      |

**제약**

- `UNIQUE (from_user_id, to_user_id)` (중복 방지)

---

### 3.2 matches (매칭)

| 컬럼명       | 타입                    | 설명                                        |
|--------------|-------------------------|---------------------------------------------|
| id           | BIGSERIAL PK           | 매칭 ID                                     |
| user1_id     | BIGINT FK → users(id)  | 사용자 1                                    |
| user2_id     | BIGINT FK → users(id)  | 사용자 2                                    |
| status       | VARCHAR(20)            | MATCHED / BLOCKED / ENDED                  |
| last_action_by| BIGINT FK → users(id) NULL | 마지막 액션한 사용자 ID               |
| created_at   | TIMESTAMP NOT NULL     | 매칭 생성일                                 |
| updated_at   | TIMESTAMP NOT NULL     | 수정일                                      |

**제약**

- `CHECK (user1_id < user2_id)` – 중복/역순 방지
- `UNIQUE (user1_id, user2_id)`

---

## 4. 채팅 도메인

### 4.1 chat_rooms

| 컬럼명        | 타입                    | 설명                                           |
|---------------|-------------------------|------------------------------------------------|
| id            | BIGSERIAL PK           | 채팅방 ID                                      |
| room_type     | VARCHAR(20)            | `DIRECT`, `GROUP`, `AI_PET`                    |
| title         | VARCHAR(100) NULL      | 그룹방/AI 방 제목                              |
| owner_user_id | BIGINT FK → users(id) NULL | 그룹/AI 방 생성자                          |
| match_id      | BIGINT FK → matches(id) NULL | DIRECT 방일 경우 연관 매칭 ID            |
| created_at    | TIMESTAMP NOT NULL     | 생성일                                         |
| updated_at    | TIMESTAMP NOT NULL     | 수정일                                         |

---

### 4.2 chat_room_participants

| 컬럼명     | 타입                    | 설명                         |
|------------|-------------------------|------------------------------|
| id         | BIGSERIAL PK           | 참가 ID                      |
| room_id    | BIGINT FK → chat_rooms | 채팅방 ID                    |
| user_id    | BIGINT FK → users(id)  | 사용자 ID                    |
| role       | VARCHAR(20)            | `OWNER`, `MEMBER` 등         |
| joined_at  | TIMESTAMP NOT NULL     | 입장 시각                    |
| left_at    | TIMESTAMP NULL         | 나간 시각                    |

**제약**

- `UNIQUE (room_id, user_id)`

---

### 4.3 file_attachments (파일 공통 테이블)

| 컬럼명       | 타입                    | 설명                                |
|--------------|-------------------------|-------------------------------------|
| id           | BIGSERIAL PK           | 파일 ID                             |
| owner_user_id| BIGINT FK → users(id)  | 업로드한 사용자                     |
| file_type    | VARCHAR(20)            | `IMAGE`, `VIDEO`, `DOCUMENT`, ...   |
| mime_type    | VARCHAR(100)           | MIME 타입                           |
| url          | VARCHAR(500)           | 파일 경로(S3 등)                    |
| size_bytes   | BIGINT NULL            | 파일 크기                           |
| width        | INT NULL               | 이미지/영상 폭                      |
| height       | INT NULL               | 이미지/영상 높이                    |
| duration_sec | INT NULL               | 영상 길이(초)                       |
| created_at   | TIMESTAMP NOT NULL     | 업로드 일시                         |

---

### 4.4 chat_messages

| 컬럼명        | 타입                       | 설명                                      |
|---------------|----------------------------|-------------------------------------------|
| id            | BIGSERIAL PK              | 메시지 ID                                 |
| room_id       | BIGINT FK → chat_rooms(id)| 채팅방 ID                                 |
| sender_user_id| BIGINT FK → users(id) NULL| 보낸 사람(시스템 메시지는 NULL)          |
| message_type  | VARCHAR(20)               | `TEXT`, `IMAGE`, `VIDEO`, `FILE`, `EMOTICON`, `EMOJI`, `SYSTEM` |
| text_content  | TEXT NULL                 | 텍스트 내용                               |
| file_id       | BIGINT FK → file_attachments(id) NULL | 첨부 파일                        |
| emoticon_id   | BIGINT FK → emoticons(id) NULL | 이모티콘                                |
| emoji_code    | VARCHAR(50) NULL          | 유니코드 이모지 코드                     |
| created_at    | TIMESTAMP NOT NULL        | 전송 시각                                 |
| read_count    | INT NOT NULL DEFAULT 0    | 읽은 사람 수(옵션)                       |

인덱스:

- `INDEX (room_id, created_at)`

---

### 4.5 emoticon_packs / emoticons

**emoticon_packs**

| 컬럼명      | 타입                    | 설명                                |
|-------------|-------------------------|-------------------------------------|
| id          | BIGSERIAL PK           | 이모티콘 팩 ID                      |
| name        | VARCHAR(100)           | 팩 이름                             |
| description | TEXT NULL              | 설명                                |
| price_gold  | INT NOT NULL DEFAULT 0 | 필요 Gold (0이면 무료)             |
| is_active   | BOOLEAN NOT NULL DEFAULT TRUE | 사용 가능 여부              |
| created_at  | TIMESTAMP NOT NULL     | 생성일                              |

**emoticons**

| 컬럼명        | 타입                        | 설명                         |
|---------------|-----------------------------|------------------------------|
| id            | BIGSERIAL PK               | 이모티콘 ID                  |
| pack_id       | BIGINT FK → emoticon_packs | 이모티콘 팩 ID               |
| code          | VARCHAR(50)                | 내부 코드                    |
| image_url     | VARCHAR(500)               | 이미지 URL                   |
| created_at    | TIMESTAMP NOT NULL         | 생성일                       |

---

## 5. 커뮤니티 도메인

### 5.1 community_categories

| 컬럼명 | 타입          | 설명               |
|--------|---------------|--------------------|
| id     | SERIAL PK     | 카테고리 ID        |
| code   | VARCHAR(50)   | 코드 (`HEALTH`, `WALK`, ...) |
| name   | VARCHAR(100)  | 이름               |

---

### 5.2 community_posts

| 컬럼명       | 타입                     | 설명                                 |
|--------------|--------------------------|--------------------------------------|
| id           | BIGSERIAL PK            | 게시글 ID                            |
| user_id      | BIGINT FK → users(id)   | 작성자                               |
| category_id  | INT FK → community_categories(id) | 카테고리                   |
| title        | VARCHAR(200)            | 제목                                 |
| content      | TEXT                    | 본문                                 |
| visibility   | VARCHAR(20)             | PUBLIC / FRIENDS                     |
| view_count   | INT NOT NULL DEFAULT 0  | 조회수                               |
| like_count   | INT NOT NULL DEFAULT 0  | 좋아요 수                            |
| created_at   | TIMESTAMP NOT NULL      | 생성일                               |
| updated_at   | TIMESTAMP NOT NULL      | 수정일                               |

---

### 5.3 community_post_images (또는 file_attachments 재사용)

| 컬럼명  | 타입                           | 설명                     |
|---------|--------------------------------|--------------------------|
| id      | BIGSERIAL PK                  | ID                       |
| post_id | BIGINT FK → community_posts   | 게시글 ID                |
| file_id | BIGINT FK → file_attachments  | 첨부 파일 ID             |
| sort_order | INT NOT NULL DEFAULT 0     | 정렬 순서                |

---

### 5.4 community_comments / community_post_likes (요약)

**community_comments**

- id, post_id, user_id, content, created_at, updated_at, parent_comment_id(대댓글)

**community_post_likes**

- id, post_id, user_id, created_at  
- `UNIQUE (post_id, user_id)`

---

## 6. Gold & 과금 도메인

### 6.1 gold_transactions

| 컬럼명         | 타입                    | 설명                                     |
|----------------|-------------------------|------------------------------------------|
| id             | BIGSERIAL PK           | 트랜잭션 ID                              |
| user_id        | BIGINT FK → users(id)  | 사용자 ID                                |
| type           | VARCHAR(20)            | `CHARGE`, `USE`, `REFUND` 등             |
| amount         | INT                    | 변동량(+ 충전, - 사용)                   |
| balance_after  | INT                    | 이 트랜잭션 후 잔액                      |
| reason_code    | VARCHAR(50)            | `AI_PROFILE`, `LIKE_EXTRA`, ...          |
| reference_id   | BIGINT NULL            | 관련 엔티티 ID (예: ai_profile_jobs.id)  |
| created_at     | TIMESTAMP NOT NULL     | 생성일                                   |

---

### 6.2 iap_receipts (인앱 결제 영수증)

| 컬럼명        | 타입                    | 설명                      |
|---------------|-------------------------|---------------------------|
| id            | BIGSERIAL PK           | 영수증 ID                 |
| user_id       | BIGINT FK → users(id)  | 사용자 ID                 |
| platform      | VARCHAR(20)            | `GOOGLE`, `APPLE`         |
| receipt_data  | TEXT                   | 영수증 원본(암호화)       |
| order_id      | VARCHAR(100)           | 스토어 주문 ID            |
| purchased_gold| INT                    | 구매 Gold 수량            |
| verified      | BOOLEAN NOT NULL DEFAULT FALSE | 검증 여부       |
| created_at    | TIMESTAMP NOT NULL     | 생성일                    |
| verified_at   | TIMESTAMP NULL         | 검증 완료 시각            |

---

## 7. AI & 기타 도메인

### 7.1 ai_profile_jobs (AI 펫 프로필 생성 요청)

| 컬럼명        | 타입                     | 설명                                       |
|---------------|--------------------------|--------------------------------------------|
| id            | BIGSERIAL PK            | 작업 ID                                    |
| pet_id        | BIGINT FK → pets(id)    | 대상 펫                                    |
| user_id       | BIGINT FK → users(id)   | 요청한 사용자                              |
| style         | VARCHAR(50)             | `PIXAR`, `ANIME`, `DISNEY` 등              |
| status        | VARCHAR(20)             | `REQUESTED`, `PROCESSING`, `DONE`, `FAILED`|
| input_image_id| BIGINT FK → file_attachments(id) NULL | 입력 사진 |
| result_image_id| BIGINT FK → file_attachments(id) NULL | 결과 이미지 |
| result_video_id| BIGINT FK → file_attachments(id) NULL | 결과 영상 |
| used_gold     | INT                     | 사용된 Gold                                |
| created_at    | TIMESTAMP NOT NULL      | 생성일                                     |
| completed_at  | TIMESTAMP NULL          | 완료일                                     |

---

### 7.2 notifications (알림)

| 컬럼명       | 타입                     | 설명                            |
|--------------|--------------------------|---------------------------------|
| id           | BIGSERIAL PK            | 알림 ID                         |
| user_id      | BIGINT FK → users(id)   | 대상 사용자                     |
| type         | VARCHAR(50)             | 알림 유형 (`MATCH`, `LIKE`, ...)|
| title        | VARCHAR(100)            | 제목                            |
| message      | TEXT                    | 메시지                          |
| payload_json | JSONB NULL              | 추가 데이터                     |
| is_read      | BOOLEAN NOT NULL DEFAULT FALSE | 읽음 여부               |
| created_at   | TIMESTAMP NOT NULL      | 생성일                          |
| read_at      | TIMESTAMP NULL          | 읽은 시간                       |

---

## 8. 관계 요약 (하이라이트)

- **users 1 ─ * pets**
- **pet_species 1 ─ * pet_breeds**
- **pets 1 ─ * pet_attribute_values**, `pet_attribute_defs 1 ─ * pet_attribute_values`
- **users 1 ─ * walk_albums**
- **walk_albums 1 ─ * walk_records**
- **walk_records 1 ─ * walk_spots**
- **users 1 ─ * likes (from), users 1 ─ * likes (to)**
- **likes 2-way → matches (1)** (비즈니스 로직으로 관리)
- **matches 1 ─ 1 chat_rooms(DIRECT)** (또는 N:1 관계)
- **chat_rooms 1 ─ * chat_room_participants**
- **chat_rooms 1 ─ * chat_messages**
- **file_attachments 1 ─ * chat_messages**, `file_attachments 1 ─ * community_post_images`, etc.
- **users 1 ─ * community_posts, community_comments, community_post_likes**
- **users 1 ─ * gold_transactions, iap_receipts**
- **pets 1 ─ * ai_profile_jobs**

---

이 ERD 초안은:

- GoldPet 전체 기능(PRD/UX 기준)을 커버하는 **핵심 데이터 모델**이며,
- 위치 기반 기능을 위해 **PostGIS geometry 타입**을 적용한 구조이다.
- 구현 단계에서 세부 컬럼 타입/길이/인덱스는 퍼포먼스와 실제 사용 패턴에 맞추어 조정할 수 있다.
