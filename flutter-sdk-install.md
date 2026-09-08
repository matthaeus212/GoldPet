# Flutter SDK 설치 방법 및 절차

GoldPet 모바일 앱(`app/`) 개발을 위한 Flutter SDK 설치 가이드. macOS(Apple Silicon, arm64) 기준.

## 0. 버전 요구사항

- `app/pubspec.yaml`의 `environment.sdk: ^3.9.2` (Dart) → Dart 3.9.x를 포함하는 **Flutter 3.35.x(stable) 이상** 필요
- `app/.metadata`에 `channel: stable` 로 고정되어 있음
- 이 저장소엔 `fvm` 설정(`.fvmrc`)이 없으므로 기본은 시스템 전역에 설치한 Flutter를 그대로 사용

## 1. Flutter SDK 본체 설치

```bash
brew install --cask flutter
```

대안:
- 특정 버전을 고정하고 싶다면 [flutter.dev](https://docs.flutter.dev/get-started/install/macos)에서 zip을 받아 `~/development/flutter`에 풀고 PATH에 `flutter/bin` 추가
- 여러 Flutter 버전을 프로젝트별로 관리하려면 `fvm`(Flutter Version Management) 사용 고려 (`brew install fvm`)

## 2. PATH 확인 및 최초 진단

```bash
flutter doctor -v
```

Homebrew cask는 설치 시 PATH를 자동 연결하지만, 터미널 재시작 후에도 `flutter` 명령을 못 찾으면 `~/.zshrc`에 추가:

```bash
export PATH="$PATH:/opt/homebrew/Caskroom/flutter/*/flutter/bin"
```

## 3. Android 툴체인

Android 빌드/에뮬레이터 실행에 필요.

```bash
brew install --cask android-studio
```

1. Android Studio 최초 실행 → SDK Manager에서 Android SDK Platform, Android SDK Command-line Tools, Android SDK Build-Tools 설치
2. 라이선스 동의:
   ```bash
   flutter doctor --android-licenses   # 전부 y 입력
   ```
3. `ANDROID_HOME` 환경변수 확인 — 기본 경로는 `~/Library/Android/sdk`. `flutter doctor`가 자동 인식하지 못하면 `~/.zshrc`에 추가:
   ```bash
   export ANDROID_HOME=$HOME/Library/Android/sdk
   export PATH="$PATH:$ANDROID_HOME/cmdline-tools/latest/bin:$ANDROID_HOME/platform-tools"
   ```
4. `app/android/app/build.gradle.kts`는 `compileSdk`/`minSdk`/`targetSdk`를 Flutter가 관리하는 값(`flutter.compileSdkVersion` 등)으로 참조하므로 SDK 버전을 직접 맞출 필요는 없음

## 4. iOS 툴체인 (macOS에서만 필요)

프로젝트 iOS 배포 타깃: `app/ios/Podfile` → `platform :ios, '16.2'`

```bash
# Xcode는 App Store에서 설치
sudo xcode-select --switch /Applications/Xcode.app/Contents/Developer
sudo xcodebuild -runFirstLaunch
brew install cocoapods   # 또는: sudo gem install cocoapods
```

`app/ios/`에 `Podfile`/`Runner.xcodeproj`가 이미 커밋돼 있어 별도 `pod init`은 불필요 — `flutter run` 실행 시 자동으로 `pod install`이 수행된다.

## 5. IDE 연동 (택 1)

- VS Code: `Flutter` 확장 설치 (Dart 확장 자동 포함)
- Android Studio: `Flutter` 플러그인 설치 (Dart 플러그인 자동 포함)

## 6. 최종 점검

```bash
flutter doctor -v
```

`[✓]` 항목이 Flutter / Android toolchain / Xcode / Connected device 등에서 모두 체크되는지 확인. `[!]`나 `[✗]`가 남아있으면 해당 줄에 출력되는 안내 명령을 따라 해소한다.

## 7. 프로젝트 의존성 설치 및 실행

```bash
cd app
flutter pub get
flutter run --dart-define=ENV=local
```

Riverpod/Freezed 등 코드 생성이 필요한 모델을 수정했다면 (CLAUDE.md 참고):

```bash
dart run build_runner build --delete-conflicting-outputs
```

> 로컬 실행 시 `app/lib/core/config/environment.dart`의 `EnvironmentConfig.local`이 LAN IP(`192.168.0.87`)로 API/S3 주소를 하드코딩하고 있다. 에뮬레이터/실기기가 개발 PC에 접근하려면 본인 PC의 LAN IP로 교체해야 한다 — 자세한 내용은 `localsetting.md` §6 참고.
