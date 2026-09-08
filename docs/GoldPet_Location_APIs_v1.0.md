# GoldPet – Location-based API 설계 v1.0  
(/friends/nearby, /walks/* with PostGIS & Naver Map 연동)

본 문서는 최종 PRD v2.0을 기반으로,  
**위치 기반 API** 중 핵심 엔드포인트인

- `/api/v1/friends/nearby`
- `/api/v1/walks/...` (특히 지도/주변 검색 관련)

에 대한 상세 설계를 정의한다.

---

## 1. 공통 규칙

### 1.1 Base

- Base URL (예시): `https://api.goldpet.app/api/v1`
- 모든 API는 `Authorization: Bearer <access_token>` 헤더 필요 (공개 산책 코스는 예외 가능)

### 1.2 공통 응답 포맷

```json
{
  "success": true,
  "data": { ... },
  "error": null
}
```

에러 시:

```json
{
  "success": false,
  "data": null,
  "error": {
    "code": "VALIDATION_ERROR",
    "message": "잘못된 파라미터입니다.",
    "details": {
      "radiusKm": "최소 0.1 이상이어야 합니다."
    }
  }
}
```

### 1.3 위치 파라미터

- 위도/경도는 WGS84 기준 (Naver Map과 동일)
- 요청 파라미터 예:
  - `lat` (number) : 위도
  - `lng` (number) : 경도
  - `radiusKm` (number) : 반경(km)
- 서버 내부에서는 PostGIS:
  - `ST_DWithin(geom, ST_SetSRID(ST_MakePoint(lng, lat), 4326)::geography, radiusKm * 1000)`

---

## 2. /friends/nearby – 근처 친구 조회 API

### 2.1 목적

- 내 주변(반경 N km) 반려인/펫을 지도와 리스트에서 보여주기 위한 데이터 제공.
- 지도(마커) + BottomSheet(친구 카드)를 그리기 위한 구조로 설계.

---

### 2.2 Endpoint 정의

- **Method**: `GET`
- **Path**: `/api/v1/friends/nearby`

#### 2.2.1 Request Query Parameters

| 이름        | 타입    | 필수 | 기본값 | 설명                                                  |
|------------|---------|------|--------|-------------------------------------------------------|
| lat        | number  | Y    | -      | 중심 위도                                             |
| lng        | number  | Y    | -      | 중심 경도                                             |
| radiusKm   | number  | N    | 3      | 반경 (km) – 0.1 ~ 20km 제한                          |
| species    | string  | N    | `ALL`  | `ALL`, `DOG`, `CAT`, ...                             |
| gender     | string  | N    | -      | `MALE`, `FEMALE`, `UNKNOWN` 등                        |
| minAge     | number  | N    | -      | 최소 펫 나이 (년)                                     |
| maxAge     | number  | N    | -      | 최대 펫 나이 (년)                                     |
| sort       | string  | N    | distance | `distance`, `recent_activity` 등                     |
| limit      | integer | N    | 50     | 최대 반환 개수 (1~100)                               |
| offset     | integer | N    | 0      | 페이지네이션 오프셋                                  |
| withPets   | boolean | N    | true   | 펫 요약 정보 포함 여부                                |

> 나중에 성격 태그, 활동 패턴(tag) 등 고급 필터 추가 예정.

#### 2.2.2 Filtering 정책

- 자기 자신(`current_user`) 제외
- 차단한 사용자 / 나를 차단한 사용자 제외
- 계정 비활성/정지 유저 제외
- 위치 비공개 옵션을 켠 유저 제외
- 펫이 없는 유저는 기본적으로 제외 (옵션 플래그로 허용 가능)

---

### 2.3 Response Body

```json
{
  "success": true,
  "data": {
    "center": {
      "lat": 37.123456,
      "lng": 127.123456
    },
    "radiusKm": 3,
    "totalCount": 2,
    "items": [
      {
        "userId": 101,
        "nickname": "지오",
        "profileImageUrl": "https://...",
        "distanceKm": 1.2,
        "mainLocationText": "서울 강남구 역삼동",
        "location": {
          "lat": 37.5001,
          "lng": 127.0365
        },
        "pets": [
          {
            "petId": 201,
            "name": "쿠키",
            "speciesCode": "DOG",
            "speciesName": "개",
            "breedName": "포메라니안",
            "ageYear": 3,
            "gender": "FEMALE",
            "temperamentTags": ["활발", "친화적"],
            "profileImageUrl": "https://..."
          }
        ],
        "isLikedByMe": false,
        "hasLikedMe": true,
        "matchStatus": "POTENTIAL" 
      },
      {
        "userId": 102,
        "nickname": "민지",
        "profileImageUrl": "https://...",
        "distanceKm": 0.8,
        "mainLocationText": "서울 서초구 서초동",
        "location": {
          "lat": 37.495,
          "lng": 127.025
        },
        "pets": [
          {
            "petId": 202,
            "name": "레오",
            "speciesCode": "CAT",
            "speciesName": "고양이",
            "breedName": "러시안 블루",
            "ageYear": 2,
            "gender": "MALE",
            "temperamentTags": ["차분", "온순"],
            "profileImageUrl": "https://..."
          }
        ],
        "isLikedByMe": true,
        "hasLikedMe": true,
        "matchStatus": "MATCHED"
      }
    ]
  },
  "error": null
}
```

#### 필드 설명

- `distanceKm`:
  - 서버에서 PostGIS `ST_DistanceSphere` 또는 `ST_Distance`로 계산, km로 변환
- `matchStatus`:
  - `NONE` : 상호 좋아요 없음
  - `POTENTIAL` : 상대가 나를 좋아요 함
  - `LIKED_BY_ME` : 내가 좋아요만 보낸 상태
  - `MATCHED` : 상호 좋아요로 매칭 완료

---

### 2.4 PostGIS 쿼리 예시 (개략)

```sql
SELECT
  u.id AS user_id,
  u.nickname,
  u.profile_image_url,
  u.main_location_text,
  ST_DistanceSphere(
    u.main_location_geom,
    ST_SetSRID(ST_MakePoint(:lng, :lat), 4326)
  ) / 1000.0 AS distance_km
FROM users u
WHERE
  u.id <> :currentUserId
  AND u.is_active = TRUE
  AND u.main_location_geom IS NOT NULL
  AND ST_DWithin(
    u.main_location_geom::geography,
    ST_SetSRID(ST_MakePoint(:lng, :lat), 4326)::geography,
    :radiusKm * 1000
  )
ORDER BY distance_km
LIMIT :limit OFFSET :offset;
```

> 실제 구현 시에 `species`, `age` 등의 필터는 JOIN + WHERE 절로 추가.

---

## 3. /walks – 산책 기록 관련 API

여기서는 **위치/지도와 직접 관련된 API**를 중심으로 정의한다.

- `/api/v1/walks` (리스트/생성/수정)
- `/api/v1/walks/{walkId}` (상세 조회)
- `/api/v1/walks/{walkId}/map` (지도 렌더링용 데이터)
- `/api/v1/walks/nearby` (주변 공개 산책 코스 탐색 – 향후 확장)

---

## 3.1 생성 & 수정은 요약만, 위치 연관된 부분을 강조

### 3.1.1 산책 기록 생성 (저장)

- **Method**: `POST`
- **Path**: `/api/v1/walks`

#### Request Body 예시

```json
{
  "title": "저녁 산책",
  "petId": 201,
  "albumId": null,
  "distanceM": 3200,
  "durationSec": 2700,
  "userCalorie": 210,
  "petCalorie": 120,
  "startedAt": "2025-11-18T10:02:00Z",
  "endedAt": "2025-11-18T10:47:00Z",
  "visibility": "FRIENDS",
  "route": {
    "type": "LineString",
    "coordinates": [
      [127.0365, 37.5001],
      [127.0370, 37.5010],
      [127.0380, 37.5020]
    ]
  },
  "spots": [
    {
      "lat": 37.5005,
      "lng": 127.0368,
      "photoUrl": "https://...",
      "memo": "여기 물이 깨끗함",
      "takenAt": "2025-11-18T10:15:00Z"
    },
    {
      "lat": 37.5015,
      "lng": 127.0375,
      "photoUrl": "https://...",
      "memo": "노을이 예쁜 곳",
      "takenAt": "2025-11-18T10:30:00Z"
    }
  ]
}
```

- 서버에서는:
  - `route` → `geometry(LineString, 4326)`로 저장 (`route_geom`)
  - `center_point`는 route의 중간 지점 혹은 bounding box의 중심으로 계산
  - `spots` 배열을 `walk_spots` 레코드로 생성 (`location_geom` = Point)

#### Response

```json
{
  "success": true,
  "data": {
    "walkId": 301
  },
  "error": null
}
```

---

## 3.2 /walks/{walkId} – 산책 상세 조회

- **Method**: `GET`
- **Path**: `/api/v1/walks/{walkId}`

### 3.2.1 Response 예시

```json
{
  "success": true,
  "data": {
    "walkId": 301,
    "userId": 101,
    "petId": 201,
    "albumId": null,
    "title": "저녁 산책",
    "distanceM": 3200,
    "durationSec": 2700,
    "userCalorie": 210,
    "petCalorie": 120,
    "startedAt": "2025-11-18T10:02:00Z",
    "endedAt": "2025-11-18T10:47:00Z",
    "visibility": "FRIENDS",
    "center": {
      "lat": 37.5010,
      "lng": 127.0370
    },
    "route": {
      "type": "LineString",
      "coordinates": [
        [127.0365, 37.5001],
        [127.0370, 37.5010],
        [127.0380, 37.5020]
      ]
    },
    "spots": [
      {
        "spotId": 401,
        "lat": 37.5005,
        "lng": 127.0368,
        "photoUrl": "https://...",
        "memo": "여기 물이 깨끗함",
        "takenAt": "2025-11-18T10:15:00Z"
      },
      {
        "spotId": 402,
        "lat": 37.5015,
        "lng": 127.0375,
        "photoUrl": "https://...",
        "memo": "노을이 예쁜 곳",
        "takenAt": "2025-11-18T10:30:00Z"
      }
    ]
  },
  "error": null
}
```

> 이 응답은 **상세 페이지 + 지도 페이지** 양쪽에서 재사용 가능하므로,  
> `/walks/{walkId}/map` 대신 이 엔드포인트 하나에 route/spot을 포함시키는 구조도 가능하다.  
> (필요 시 `includeRoute=true` 같은 쿼리 파라미터로 분리)

---

## 3.3 /walks/{walkId}/map – 지도 최적화 데이터 (옵션 설계)

> 선택적으로, 지도 화면에 특화된 가벼운 응답을 원할 경우 별도 엔드포인트로 분리.

- **Method**: `GET`
- **Path**: `/api/v1/walks/{walkId}/map`

### 3.3.1 Query Parameters

| 이름         | 타입   | 필수 | 설명                                         |
|--------------|--------|------|----------------------------------------------|
| simplify     | bool   | N    | true면 Polyline를 간소화(샘플링)해서 반환   |
| maxPoints    | number | N    | 간소화 시 최대 좌표 개수 (예: 500)           |

### 3.3.2 Response 예시

```json
{
  "success": true,
  "data": {
    "walkId": 301,
    "center": {
      "lat": 37.5010,
      "lng": 127.0370
    },
    "bounds": {
      "southWest": { "lat": 37.4990, "lng": 127.0350 },
      "northEast": { "lat": 37.5030, "lng": 127.0390 }
    },
    "route": {
      "type": "LineString",
      "coordinates": [
        [127.0365, 37.5001],
        [127.0370, 37.5010],
        [127.0380, 37.5020]
      ]
    },
    "spots": [
      {
        "spotId": 401,
        "lat": 37.5005,
        "lng": 127.0368,
        "thumbnailUrl": "https://...",
        "hasMemo": true
      },
      {
        "spotId": 402,
        "lat": 37.5015,
        "lng": 127.0375,
        "thumbnailUrl": "https://...",
        "hasMemo": true
      }
    ]
  },
  "error": null
}
```

> 모바일 지도 성능을 위해, 서버에서 Douglas-Peucker 등의 알고리즘으로 Polyline를 간소화하는 옵션을 제공할 수 있다.

---

## 3.4 /walks/nearby – 근처 공개 산책 코스 탐색 (향후 확장용)

> 초기 버전에서는 필수가 아니지만, 향후 “공개 산책 코스 탐색/추천” 기능을 염두에 둔 설계.

- **Method**: `GET`
- **Path**: `/api/v1/walks/nearby`

### 3.4.1 Request Query Parameters

| 이름        | 타입    | 필수 | 기본값  | 설명                                                         |
|------------|---------|------|---------|--------------------------------------------------------------|
| lat        | number  | Y    | -       | 중심 위도                                                    |
| lng        | number  | Y    | -       | 중심 경도                                                    |
| radiusKm   | number  | N    | 5       | 반경(km)                                                     |
| visibility | string  | N    | `PUBLIC`| PUBLIC만 허용 (FRIENDS는 로그인 사용자 친구 기준 필터 가능) |
| limit      | int     | N    | 50      |                                                              |
| offset     | int     | N    | 0       |                                                              |
| sort       | string  | N    | `distance` | `distance`, `popularity`, `recent`                         |

### 3.4.2 Response 예시

```json
{
  "success": true,
  "data": {
    "center": { "lat": 37.5, "lng": 127.03 },
    "radiusKm": 5,
    "totalCount": 1,
    "items": [
      {
        "walkId": 501,
        "user": {
          "userId": 201,
          "nickname": "산책러",
          "profileImageUrl": "https://..."
        },
        "title": "한강 노을 산책 코스",
        "distanceM": 4500,
        "durationSec": 3600,
        "center": { "lat": 37.52, "lng": 127.04 },
        "distanceFromCenterKm": 2.1,
        "spotCount": 5,
        "likeCount": 20,
        "createdAt": "2025-11-10T09:00:00Z"
      }
    ]
  },
  "error": null
}
```

> 이 응답은 “공개 산책 코스를 탐색하는 지도/리스트 화면”에서 사용 가능하다.

---

## 4. 에러 케이스 정리

### 4.1 공통 에러 코드 예시

| code                   | 설명                                         |
|------------------------|----------------------------------------------|
| UNAUTHORIZED           | 토큰 없음/만료                               |
| FORBIDDEN              | 권한 없음                                   |
| VALIDATION_ERROR       | 잘못된 파라미터                             |
| NOT_FOUND              | 존재하지 않는 리소스                        |
| INTERNAL_SERVER_ERROR  | 서버 내부 오류                               |
| LOCATION_REQUIRED      | 위치 정보가 필수인 API에 위치 미전달        |

### 4.2 /friends/nearby 에러 예시

- `radiusKm`이 허용 범위를 벗어나는 경우:
  - `400 VALIDATION_ERROR`
- `lat`/`lng` 미전달:
  - `400 LOCATION_REQUIRED`

### 4.3 /walks/* 에러 예시

- 다른 사람의 PRIVATE 산책 기록 조회 시:
  - `403 FORBIDDEN`
- 존재하지 않는 `walkId`:
  - `404 NOT_FOUND`

---

## 5. Swagger(OpenAPI) 구조 메모

> 실제 Swagger 문서화 시 다음과 같은 tag/paths 구조를 권장.

### 5.1 Tags

- `Friends`
  - `/friends/nearby`
- `Walks`
  - `/walks`
  - `/walks/{walkId}`
  - `/walks/{walkId}/map`
  - `/walks/nearby`

### 5.2 공통 Schema 예시

- `GeoPoint`:
  - `lat`: number, `lng`: number
- `LineStringGeoJSON`:
  - `type`: `"LineString"`
  - `coordinates`: array of `[lng, lat]`
- `FriendNearbyItem`
- `WalkDetail`
- `WalkMapData`
- `WalkNearbyItem`

이 문서(v1.0)는 **위치 기반 핵심 API** 설계를 기준으로,  
추후 Swagger(OpenAPI 3) 스펙 작성과 실제 컨트롤러/서비스 구현의 기반이 된다.
