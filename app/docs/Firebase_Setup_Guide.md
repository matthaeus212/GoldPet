# GoldPet Firebase 설정 가이드

이 가이드는 GoldPet 프로젝트의 **Google 로그인** 및 **푸시 알림(FCM)** 기능을 활성화하기 위한 Firebase 설정 절차를 설명합니다.

## 1. Firebase 프로젝트 생성

1. [Firebase Console](https://console.firebase.google.com/)에 접속합니다.
2. **"프로젝트 만들기"** 버튼을 클릭합니다.
3. 프로젝트 이름을 입력합니다 (예: `GoldPet`).
4. Google Analytics 설정 여부를 묻는 단계에서 **"사용 설정"**을 권장합니다 (선택 사항).
5. **"프로젝트 만들기"**를 완료합니다.

## 2. iOS 앱 등록 및 설정 파일 다운로드

Google 로그인과 푸시 알림을 사용하려면 iOS 앱을 Firebase 프로젝트에 등록해야 합니다.

1. Firebase 프로젝트 개요 페이지에서 **iOS 아이콘(+)**을 클릭하여 앱을 추가합니다.
2. **iOS 번들 ID** 입력 칸에 다음 ID를 정확히 입력합니다:
    * `com.mannam.goldpet`
    * *(참고: `ios/Runner.xcodeproj` 설정과 일치해야 합니다)*
3. 앱 닉네임은 "GoldPet iOS" 등으로 자유롭게 입력합니다.
4. **"앱 등록"** 버튼을 클릭합니다.
5. **"구성 파일 다운로드"** 단계에서 **`GoogleService-Info.plist`** 파일을 다운로드합니다.
6. 다운로드한 파일을 프로젝트의 다음 경로에 복사합니다:
    * `flutter/goldpet_app/ios/Runner/GoogleService-Info.plist`
    * **(중요)** Xcode를 열지 않고 파일 탐색기(Finder)에서 복사해 넣어도 되지만, 확실하게 하기 위해 Xcode에서 `Runner` 프로젝트를 열고 `Runner` 폴더 안에 드래그 앤 드롭으로 추가하는 것이 가장 좋습니다. ("Copy items if needed" 체크)

## 3. 푸시 알림(APNs) 설정

iOS에서 푸시 알림을 받으려면 Apple Developer Console에서 APNs 인증 키를 발급받아 Firebase에 등록해야 합니다.

### 3.1. APNs 인증 키 발급 (Apple Developer Console)

1. [Apple Developer Console](https://developer.apple.com/account/)에 접속합니다.
2. **Certificates, Identifiers & Profiles** > **Keys** 메뉴로 이동합니다.
3. **(+)** 버튼을 눌러 새 키를 생성합니다.
4. Key Name을 입력하고 (예: `GoldPet Push Key`), **Apple Push Notifications service (APNs)** 체크박스를 선택합니다.
5. **Continue** > **Register**를 클릭하여 키를 생성합니다.
6. **.p8** 확장자를 가진 키 파일을 다운로드합니다. (**주의:** 한 번만 다운로드 가능하므로 안전한 곳에 보관하세요)
7. **Key ID**와 **Team ID** (멤버십 정보에서 확인 가능)를 메모해 둡니다.

### 3.2. Firebase에 APNs 키 업로드

1. Firebase Console > **프로젝트 설정(톱니바퀴 아이콘)** > **클라우드 메시징** 탭으로 이동합니다.
2. **Apple 앱 구성** 섹션에서 등록한 iOS 앱을 선택합니다.
3. **APNs 인증 키** 항목의 **"업로드"** 버튼을 클릭합니다.
4. 다운로드한 **.p8 파일**을 업로드하고, **Key ID**와 **Team ID**를 입력합니다.
5. **"업로드"**를 완료합니다.

## 4. Flutter 프로젝트 설정 (개발자 수행)

위 단계까지 완료해주시면, 개발자가 다음 작업을 수행하여 연동을 마무리합니다.

1. `pubspec.yaml`에 `firebase_core`, `firebase_messaging` 패키지 추가.
2. `ios/Runner/Info.plist`에 `GoogleService-Info.plist` 설정 반영 (자동 처리됨).
3. 앱 시작 시 Firebase 초기화 코드 추가.

---

**완료 후 알려주실 내용:**

1. `GoogleService-Info.plist` 파일을 `flutter/goldpet_app/ios/Runner/` 경로에 추가하셨나요?
2. (선택) APNs 키 업로드까지 완료하셨나요? (나중에 하셔도 되지만, 푸시 테스트를 위해 필요합니다)
