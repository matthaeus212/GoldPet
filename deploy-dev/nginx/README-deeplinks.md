# Universal Link / App Link 배포 가이드

## 파일 위치 (Option A — Vite public 번들)

딥링크 설정 파일은 빌드 산출물에 포함되어 서빙됩니다.

```
frontend/public/
├── apple-app-site-association          # iOS Universal Link
└── .well-known/
    └── assetlinks.json                 # Android App Link
```

`npm run build` 실행 시 Vite 가 위 파일들을 `frontend/dist/` 로 그대로 복사합니다.
개발 서버에서는 `/home/dev/www/mannam/goldpet/frontend/dist` 아래에 배포되어
`app.mannamsquare.com/apple-app-site-association` 및
`app.mannamsquare.com/.well-known/assetlinks.json` 로 즉시 접근 가능합니다.

## Nginx 설정

`sites-available/mannam.conf` 의 `app.mannamsquare.com` server 블록에
두 경로에 대한 `location` 블록이 이미 추가되어 있습니다.
핵심: `default_type application/json` (Apple 요구사항) + 짧은 캐시(5분).

## 실제 값 주입 (배포 전 교체 필수)

### AASA (`frontend/public/apple-app-site-association`)
- `TEAMID` → Apple Developer Team ID (Apple Developer Portal → Membership)
- `com.goldpet.app` → 실제 iOS 앱 bundle ID (`app/ios/Runner.xcodeproj` 의 PRODUCT_BUNDLE_IDENTIFIER)

### assetlinks (`frontend/public/.well-known/assetlinks.json`)
- `PLACEHOLDER_DEV_SHA256` / `PLACEHOLDER_PROD_SHA256` → 서명 인증서 SHA-256
  - Debug 키: `keytool -list -v -keystore ~/.android/debug.keystore -alias androiddebugkey -storepass android -keypass android`
  - Release 키: `keytool -list -v -keystore <release-keystore> -alias <alias>`
  - `SHA256:` 라인 값 그대로 (콜론 포함)
- dev/prod 서로 다른 서명 인증서를 쓸 경우 `sha256_cert_fingerprints` 배열에 모두 추가하면 한 파일로 처리 가능

## 앱 측 등록

### iOS (Xcode)
- Signing & Capabilities → Associated Domains → `applinks:app.mannamsquare.com`, `applinks:app.goldpet.com`

### Android
- `app/android/app/src/main/AndroidManifest.xml` MainActivity intent-filter 에 `android:autoVerify="true"` + host `app.mannamsquare.com` / `app.goldpet.com` 추가

## 동작 검증

```bash
# iOS AASA
curl -i https://app.mannamsquare.com/apple-app-site-association
# → Content-Type: application/json, 200 OK, JSON body

# Android assetlinks
curl -i https://app.mannamsquare.com/.well-known/assetlinks.json
# → Content-Type: application/json, 200 OK, JSON array

# Google 공식 검증기
# https://developers.google.com/digital-asset-links/tools/generator
```

## 딥링크 경로 (§5.8 참고)

| kind | URL 패턴 |
|------|---------|
| walk-summary | `/w/{walkId}` |
| walk-photo | `/w/{walkId}?photo={photoId}` |
| course | `/c/{courseId}` |
| health-history | `/pet/{petId}/health` |
| health-detail | `/health/{healthId}` |
| community-post | `/post/{postId}` |
