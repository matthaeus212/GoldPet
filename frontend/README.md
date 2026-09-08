# GoldPet React App

GoldPet 하이브리드 앱의 React UI 레이어입니다.

## 개발 시작하기

### 1. 의존성 설치

```bash
npm install
```

### 2. 개발 서버 실행

```bash
npm run dev
```

개발 서버는 `http://localhost:5173`에서 실행됩니다.

### 3. Flutter 앱과 함께 실행

다른 터미널에서:

```bash
cd ../goldpet_app
flutter run
```

Flutter 앱의 WebView가 React 개발 서버를 자동으로 로드합니다.

## 프로젝트 구조

```
src/
├── bridge/              # Flutter Bridge 통신
│   ├── nativeBridge.ts
│   └── bridgeTypes.ts
├── services/            # 서비스 레이어
│   ├── api/            # API 클라이언트
│   └── native/         # 네이티브 서비스 래퍼
├── stores/             # 상태 관리 (Zustand)
├── features/           # 기능별 페이지
│   ├── splash/
│   ├── onboarding/
│   ├── auth/
│   └── ...
├── components/         # 공통 컴포넌트
├── theme/              # 디자인 시스템
└── App.tsx            # 메인 앱 컴포넌트
```

## 주요 기능

- **React 19**: 최신 React 기능 사용
- **TypeScript**: 타입 안전성 보장
- **React Router**: 클라이언트 사이드 라우팅
- **React Query**: 서버 상태 관리
- **Zustand**: 클라이언트 상태 관리
- **Bridge 통신**: Flutter 네이티브 기능 연동

## 빌드

```bash
npm run build
```

빌드 결과물은 `dist/` 폴더에 생성되며, Flutter 앱의 `assets/web/`에 복사하여 사용합니다.
