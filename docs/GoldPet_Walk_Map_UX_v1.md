# GoldPet – 산책 지도 상세 화면 UX / 와이어프레임 (Naver Map 기반) v1

## 1. 화면 목적 & 진입 경로

### 🎯 화면 목적

단일 산책 기록에 대해 다음을 제공한다.

- 사용자가 한 번의 산책에서 걸은 **전체 코스를 Naver 지도에서 한눈에 보기**
- 산책 중에 찍은 **사진 Spot 위치를 마커로 확인**
- Spot 상세(사진/메모)를 바로 확인하고 추억을 떠올리기
- 공유/편집/삭제 같은 후속 액션으로 쉽게 이동

### 🔁 진입 경로

1. **산책 히스토리 → 특정 산책 기록 상세 → “지도에서 보기” 탭/버튼**
2. **산책 종료 직후 완료 화면 → “지도 보기” 버튼**
3. (향후) **산책 앨범 상세 → 특정 산책 선택 → 지도 보기**

---

## 2. 기본 레이아웃 (Portrait 기준)

### 전체 레이아웃 개념

```text
┌────────────────────────────────┐
│  Top AppBar                    │
│  [←] 산책 지도 보기      ⋮     │
├────────────────────────────────┤
│                                │
│         Naver Map View         │
│   - Polyline (산책 궤적)       │
│   - Spot 마커들                │
│   - 시작/끝 포인트 아이콘      │
│                                │
│    (우측에 Floating Buttons)   │
│    [내 위치]                   │
│    [전체 코스 보기]            │
│    [지도 타입 전환]            │
├────────────────────────────────┤
│   Bottom Sheet (기본 접힘)     │
│  ┌──────────────────────────┐  │
│  │  산책 요약 카드          │  │
│  │  - 날짜, 시간            │  │
│  │  - 거리, 소요시간        │  │
│  │  - 칼로리(나/펫)         │  │
│  │  - 대표 펫/산책 제목     │  │
│  │  - [공유] [앨범 추가]    │  │
│  └──────────────────────────┘  │
│  (드래그 ↑ 하면 Spot 리스트)   │
└────────────────────────────────┘
```

---

## 3. 상단 영역 – AppBar

### 구성 요소

- **좌측**: 뒤로가기 아이콘 `←`
- **타이틀**:
  - 기본: `산책 지도`
  - 또는: `{펫 이름}와의 산책` (필요 시 동적 타이틀)
- **우측**: overflow 메뉴 `⋮`
  - 메뉴 예:
    - “산책 정보 수정”
    - “이 산책 삭제”
    - “코스 공유하기(링크/이미지)”

---

## 4. 중심 영역 – Naver Map View

### 4.1 지도에서 보여줄 요소

1. **산책 궤적 (Polyline)**  
   - `WalkRecord.route_geojson` 기반 LineString
   - 시작점/끝점에 아이콘:
     - 시작: 초록/Play 아이콘 느낌
     - 끝: 빨강/Stop 아이콘 느낌

2. **사진 Spot 마커 (WalkSpot)**  
   - 각 Spot에 한 개의 마커
   - 마커 형태:
     - 작은 동그라미 + 카메라 아이콘
     - 또는 작은 썸네일 이미지(확대 시 더 크게 표시)
   - Spot이 많은 경우, 저배율에서는 간단 아이콘, 고배율에서는 썸네일 등으로 조정 가능

3. **현재 위치 버튼 (선택적)**  
   - 사용자가 지도에서 이동/확대/축소를 많이 한 뒤,
   - 다시 현재 GPS 위치로 돌아가고 싶을 때 사용

4. **지도 타입 버튼**  
   - 일반 지도 / 위성(항공사진) 전환
   - Naver Map의 지도 타입 변경 API 활용

5. **전체 코스 보기 버튼**  
   - Polyline 전체가 화면에 들어오도록 bounds fit
   - 사용자가 지도를 이동한 후에도 원래의 전체 코스를 다시 보기 편하게 제공

---

### 4.2 지도 위 컨트롤 배치 (와이어프레임)

```text
┌────────────────────────────────┐
│ [←] 산책 지도             [⋮] │
├────────────────────────────────┤
│  ┌──────────────────────────┐ │
│  │                          │ │
│  │      NAVER MAP VIEW      │ │
│  │                          │ │
│  │   ─ 산책코스 Polyline    │ │
│  │   ● Spot 마커들          │ │
│  │   🟢 시작점 / 🔴 끝점     │ │
│  │                          │ │
│  └──────────────────────────┘ │
│                              │
│                      ○       │
│                      ○ [전체코스]  │
│                      ○ [지도타입]  │
├────────────────────────────────┤
│  ⬆ 드래그 가능한 BottomSheet │
└────────────────────────────────┘
```

- 우측 하단에 세로로 정렬된 **FloatingActionButton 3개**:
  - `myLocation` (현재 위치로 이동)
  - `fitBounds` (전체 코스 보기)
  - `mapTypeToggle` (지도 타입 전환)

---

## 5. 하단 – Bottom Sheet 설계

BottomSheet는 **“접힌 상태(요약)” + “펼친 상태(상세)”** 두 상태를 가진다.

### 5.1 접힌 상태 (요약 카드)

```text
┌────────────────────────────────┐
│  오늘의 산책 · 🐶 쿠키        │
│  3.2km · 45분 · 210kcal(나)   │
│  펫 120kcal · 2025.11.18 19:03│
│  [공개범위: 친구만]    [공유] │
└────────────────────────────────┘
```

**포함 정보 예시**

- 산책 제목 또는 “{펫 이름}와의 산책”
- 날짜/시각
- 거리, 시간, 칼로리(사용자/펫)
- 공개 범위 (PUBLIC / FRIENDS / PRIVATE)
- 공유 버튼 (추후 링크/이미지로 공유 기능과 연동)
- 선택적으로 “앨범” 표시/변경 버튼

### 5.2 펼친 상태 (Spot 리스트 + 상세 정보)

접힌 상태에서 드래그 업(또는 핸들 탭) 시 펼쳐짐.

```text
┌────────────────────────────────┐
│  오늘의 산책 · 🐶 쿠키        │
│  3.2km · 45분 · 210kcal(나)   │
│  펫 120kcal · 2025.11.18 19:03│
│  [공개범위: 친구만]    [공유] │
├────────────────────────────────┤
│  Spot 타임라인/리스트        │
│  ┌────────────────────────┐  │
│  │ [사진썸네일] 19:12      │  │
│  │ 강아지가 물 마시는 곳  │  │
│  │ 메모: 여기 물이 깨끗함 │  │
│  └────────────────────────┘  │
│  ┌────────────────────────┐  │
│  │ [사진썸네일] 19:28      │  │
│  │ 노을 포인트             │  │
│  │ 메모: 하늘 색깔 미쳤다  │  │
│  └────────────────────────┘  │
│  …                          │
└────────────────────────────────┘
```

- Spot 리스트에서 특정 Spot 카드를 탭하면:
  - 지도 카메라가 해당 Spot 위치로 이동
  - Spot 마커를 강조(색/크기/애니메이션 등)
- 반대로 지도에서 Spot 마커를 탭하면:
  - BottomSheet가 자동으로 해당 Spot 카드 위치로 스크롤 및 하이라이트

---

## 6. 상태/인터랙션 플로우

### 6.1 초기 진입 플로우

1. `WalkMapPage`로 이동 시 `walkId` 또는 `WalkRecordDetail`을 파라미터로 전달
2. 페이지 진입 후:
   - 로딩 인디케이터 표시
   - 백엔드 API `GET /api/v1/walks/{walkId}` 호출
3. 응답 수신 시:
   - `route_geojson` → Polyline 데이터로 변환
   - `spots` → Spot 목록 + Marker 목록으로 변환
   - Polyline 전체 bounds를 계산하여 카메라 초기 위치/줌 설정

### 6.2 Spot 마커 탭

- 마커 탭 → 해당 Spot을 “선택 상태”로 만들고:
  - BottomSheet가 하단이라면 살짝 위로 올리면서
  - Spot 리스트 중 해당 아이템으로 스크롤 & 강조(배경색/테두리)

### 6.3 지도 패닝/줌

- 사용자가 자유롭게 이동/확대/축소 가능
- “전체 코스 보기” 버튼 탭 시:
  - Polyline bounds 기준으로 다시 카메라 이동

### 6.4 공유 / 앨범 관련 액션

- 공유 버튼:
  - 코스 링크 공유(공개 범위가 PUBLIC/FRIENDS일 경우)
  - 이후 버전에서 지도 + 궤적 + 대표 사진 렌더링 이미지 공유 고려
- 앨범 관련:
  - “앨범 추가/변경” 버튼 → WalkAlbum 선택/생성 모달로 이동

---

## 7. Flutter 기준 위젯 트리(개략 설계)

```dart
class WalkMapPage extends StatelessWidget {
  final int walkId;

  const WalkMapPage({super.key, required this.walkId});

  @override
  Widget build(BuildContext context) {
    return Scaffold(
      appBar: AppBar(
        leading: const BackButton(),
        title: const Text('산책 지도'),
        actions: [
          IconButton(
            icon: const Icon(Icons.more_vert),
            onPressed: () {
              // TODO: 산책 수정/삭제/공유 메뉴
            },
          ),
        ],
      ),
      body: Stack(
        children: [
          // 1) Naver Map 영역
          GoldPetNaverMapView(
            polylines: const [], // route_geojson -> Polyline 변환 결과
            markers: const [],   // WalkSpot -> Marker 변환 결과
            onMarkerTap: (markerId) {
              // TODO: Spot 선택 상태와 BottomSheet 동기화
            },
          ),

          // 2) 우측 하단 Floating 버튼(내 위치 / 전체 코스 / 지도 타입)
          Positioned(
            right: 16,
            bottom: 120,
            child: Column(
              children: [
                FloatingActionButton(
                  heroTag: 'loc',
                  mini: true,
                  onPressed: () {
                    // TODO: 현재 위치로 카메라 이동
                  },
                  child: const Icon(Icons.my_location),
                ),
                const SizedBox(height: 8),
                FloatingActionButton(
                  heroTag: 'fit',
                  mini: true,
                  onPressed: () {
                    // TODO: 전체 코스 보기 (fitBounds)
                  },
                  child: const Icon(Icons.route),
                ),
                const SizedBox(height: 8),
                FloatingActionButton(
                  heroTag: 'mapType',
                  mini: true,
                  onPressed: () {
                    // TODO: 지도 타입 전환
                  },
                  child: const Icon(Icons.layers),
                ),
              ],
            ),
          ),

          // 3) DraggableScrollableSheet 기반 BottomSheet
          const WalkBottomSheet(), // 산책 요약 + Spot 리스트
        ],
      ),
    );
  }
}
```

- 실제 구현에서는 상태관리(Riverpod/Bloc 등)를 활용해:
  - `WalkRecordDetail` 로딩 상태
  - `selectedSpotId`
  - `polylines`, `markers`
  - BottomSheet 스크롤/전개 상태  
  를 하나의 ViewModel/State로 관리하는 것을 권장.

---

## 8. 향후 확장 포인트

- **공개 산책 코스 탐색 화면**
  - 같은 레이아웃을 사용하되, 카드 영역에 “작성자 정보 + 좋아요 수”를 추가
  - 다른 사용자의 산책 코스를 브라우징하기 위한 모드로 사용

- **친구와 함께한 산책**
  - 동일 시간대에 함께 산책한 사람들의 코스를 여러 Polyline으로 표시
  - 각 코스에 다른 색을 적용해 구분 가능

- **도전 과제/미션**
  - 특정 구간을 다른 색상으로 표시 (예: “급경사 구간”, “빨리 걸은 구간” 등)
  - 특정 Spot을 체크포인트로 사용하여 gamification 적용

---

이 문서는 **GoldPet의 단일 산책 기록에 대한 “산책 지도 상세 화면” UX/와이어프레임 v1** 설계를 정의한다.  
Naver Map 기반 구현을 전제로 하며, 향후 산책 공유/코스 탐색/미션 시스템의 기반이 되는 화면이다.
