# GoldPet – 하이브리드 앱 아키텍처 설계 v1.0

> Flutter 네이티브 기능 + React 19 WebView 기반 하이브리드 앱 아키텍처 설계 문서  
> 본 문서는 Flutter로 Figma 디자인 구현의 어려움을 해결하기 위한 전략적 전환 계획이다.

---

## 1. 아키텍처 개요

### 1.1 핵심 전략

**"네이티브는 네이티브만, UI는 React로"**

- **Flutter (네이티브 레이어)**
  - 푸시 알림 (Firebase Cloud Messaging)
  - GPS 위치 추적 및 권한 관리
  - 카메라 및 사진 라이브러리 접근
  - 파일 시스템 접근
  - 네이티브 브릿지 (JavaScript Channel)

- **React 19 (UI 레이어)**
  - 모든 사용자 인터페이스
  - 상태 관리 (Zustand / Jotai / React Query)
  - 라우팅 (React Router v7)
  - API 통신 (Axios / Fetch)
  - 비즈니스 로직

- **통신 계층**
  - Flutter WebView (webview_flutter)
  - JavaScript Channel (MethodChannel)
  - 양방향 메시지 브릿지

### 1.2 아키텍처 다이어그램

```
┌─────────────────────────────────────────────────────────┐
│                    Flutter Container                     │
│  ┌───────────────────────────────────────────────────┐  │
│  │         React 19 WebView (UI Layer)               │  │
│  │  ┌─────────────────────────────────────────────┐  │  │
│  │  │  React Components (Figma Design)            │  │  │
│  │  │  - Onboarding, Profile, Walk, Chat, etc.    │  │  │
│  │  └─────────────────────────────────────────────┘  │  │
│  │              ↕ JavaScript Bridge                  │  │
│  └───────────────────────────────────────────────────┘  │
│  ┌───────────────────────────────────────────────────┐  │
│  │      Flutter Native Bridge (Service Layer)        │  │
│  │  - PushNotificationService                        │  │
│  │  - LocationService                                │  │
│  │  - CameraService                                  │  │
│  │  - PhotoLibraryService                            │  │
│  │  - FileSystemService                              │  │
│  └───────────────────────────────────────────────────┘  │
│  ┌───────────────────────────────────────────────────┐  │
│  │         Native Platform APIs                      │  │
│  │  - iOS: FCM, CoreLocation, AVFoundation, etc.    │  │
│  │  - Android: FCM, LocationManager, Camera2, etc.   │  │
│  └───────────────────────────────────────────────────┘  │
└─────────────────────────────────────────────────────────┘
                          ↕ HTTP/WebSocket
┌─────────────────────────────────────────────────────────┐
│              Backend API (Spring Boot)                   │
└─────────────────────────────────────────────────────────┘
```

---

## 2. 기술 스택 상세

### 2.1 Flutter 네이티브 레이어

#### 필수 패키지

```yaml
dependencies:
  flutter:
    sdk: flutter
  
  # WebView
  webview_flutter: ^4.9.0
  
  # 네이티브 기능
  firebase_messaging: ^15.1.0        # 푸시 알림
  geolocator: ^13.0.0                # GPS 위치 추적
  permission_handler: ^12.0.0        # 권한 관리
  image_picker: ^1.1.2               # 카메라/사진 라이브러리
  path_provider: ^2.1.2              # 파일 시스템
  shared_preferences: ^2.3.2         # 로컬 스토리지
  
  # 통신
  http: ^1.2.2                       # 네이티브 레이어에서의 HTTP (선택)
  
  # 유틸리티
  flutter_js: ^0.7.0                 # JavaScript 실행 (선택)
```

#### 디렉토리 구조

```
flutter/goldpet_app/lib/
├── main.dart
├── app.dart
├── core/
│   ├── bridge/
│   │   ├── javascript_bridge.dart      # JavaScript Channel 핸들러
│   │   ├── bridge_controller.dart      # 브릿지 메시지 라우팅
│   │   └── bridge_protocol.dart        # 메시지 프로토콜 정의
│   ├── services/
│   │   ├── push_notification_service.dart
│   │   ├── location_service.dart
│   │   ├── camera_service.dart
│   │   ├── photo_library_service.dart
│   │   └── file_service.dart
│   └── webview/
│       ├── webview_wrapper.dart       # WebView 위젯 래퍼
│       └── webview_controller.dart    # WebView 컨트롤러
└── native/                            # 네이티브 전용 화면
    ├── splash_screen.dart             # 스플래시 (네이티브)
    └── permission_request_screen.dart # 권한 요청 (네이티브)
```

### 2.2 React 19 UI 레이어

#### 기술 스택

```json
{
  "dependencies": {
    "react": "^19.0.0",
    "react-dom": "^19.0.0",
    "react-router-dom": "^7.0.0",
    "@tanstack/react-query": "^5.0.0",
    "zustand": "^4.5.0",
    "axios": "^1.7.0",
    "zustand": "^4.5.0",
    "@emotion/react": "^11.13.0",
    "@emotion/styled": "^11.13.0"
  },
  "devDependencies": {
    "@vitejs/plugin-react": "^4.3.0",
    "vite": "^5.4.0",
    "typescript": "^5.5.0",
    "@types/react": "^19.0.0",
    "@types/react-dom": "^19.0.0"
  }
}
```

#### 디렉토리 구조

```
react-app/
├── public/
│   ├── index.html
│   └── assets/
├── src/
│   ├── main.tsx
│   ├── App.tsx
│   ├── bridge/
│   │   ├── nativeBridge.ts           # Flutter 브릿지 클라이언트
│   │   ├── bridgeTypes.ts            # 타입 정의
│   │   └── bridgeEvents.ts           # 이벤트 리스너
│   ├── services/
│   │   ├── api/
│   │   │   ├── client.ts             # Axios 인스턴스
│   │   │   ├── auth.ts               # 인증 API
│   │   │   ├── user.ts                # 사용자 API
│   │   │   ├── walk.ts                # 산책 API
│   │   │   └── ...
│   │   └── native/
│   │       ├── pushNotification.ts   # 푸시 알림 래퍼
│   │       ├── location.ts            # 위치 서비스 래퍼
│   │       ├── camera.ts              # 카메라 래퍼
│   │       └── photoLibrary.ts        # 사진 라이브러리 래퍼
│   ├── stores/
│   │   ├── authStore.ts               # 인증 상태
│   │   ├── userStore.ts                # 사용자 상태
│   │   └── ...
│   ├── features/
│   │   ├── onboarding/
│   │   ├── auth/
│   │   ├── profile/
│   │   ├── walk/
│   │   ├── map/
│   │   ├── chat/
│   │   └── ...
│   ├── components/
│   │   ├── common/
│   │   └── ui/
│   ├── hooks/
│   └── utils/
├── vite.config.ts
├── tsconfig.json
└── package.json
```

---

## 3. JavaScript Bridge 설계

### 3.1 메시지 프로토콜

#### 기본 메시지 구조

```typescript
// bridgeTypes.ts
export interface BridgeMessage {
  id: string;                    // 요청 ID (응답 매칭용)
  method: string;                // 메서드 이름
  params?: Record<string, any>;  // 파라미터
  timestamp: number;             // 타임스탬프
}

export interface BridgeResponse {
  id: string;                    // 요청 ID와 매칭
  success: boolean;
  data?: any;
  error?: {
    code: string;
    message: string;
  };
  timestamp: number;
}
```

#### 지원 메서드 목록

```typescript
// Flutter → React (이벤트)
export type NativeEventType =
  | 'pushNotificationReceived'    // 푸시 알림 수신
  | 'locationUpdated'              // 위치 업데이트
  | 'permissionStatusChanged'      // 권한 상태 변경
  | 'appStateChanged';              // 앱 상태 변경 (foreground/background)

// React → Flutter (요청)
export type NativeMethod =
  // 푸시 알림
  | 'requestPushPermission'
  | 'getPushToken'
  | 'subscribeToTopic'
  
  // 위치
  | 'requestLocationPermission'
  | 'getCurrentLocation'
  | 'startLocationTracking'
  | 'stopLocationTracking'
  
  // 카메라/사진
  | 'requestCameraPermission'
  | 'requestPhotoPermission'
  | 'openCamera'
  | 'openPhotoLibrary'
  | 'pickImage'
  
  // 파일
  | 'saveFile'
  | 'readFile'
  | 'deleteFile'
  
  // 앱 정보
  | 'getAppVersion'
  | 'getDeviceInfo'
  | 'openSettings';
```

### 3.2 Flutter Bridge 구현

#### JavaScript Channel 핸들러

```dart
// lib/core/bridge/javascript_bridge.dart
import 'package:flutter/services.dart';
import 'package:webview_flutter/webview_flutter.dart';

class JavaScriptBridge {
  final WebViewController webViewController;
  final MethodChannel _channel = const MethodChannel('native_bridge');
  
  JavaScriptBridge(this.webViewController) {
    _setupChannel();
  }
  
  void _setupChannel() {
    _channel.setMethodCallHandler(_handleMethodCall);
  }
  
  Future<dynamic> _handleMethodCall(MethodCall call) async {
    switch (call.method) {
      case 'requestPushPermission':
        return await _handlePushPermission();
      case 'getCurrentLocation':
        return await _handleGetCurrentLocation();
      case 'openCamera':
        return await _handleOpenCamera(call.arguments);
      // ... 다른 메서드들
      default:
        throw PlatformException(
          code: 'METHOD_NOT_FOUND',
          message: 'Method ${call.method} not found',
        );
    }
  }
  
  // React로 이벤트 전송
  Future<void> sendEventToReact(String eventType, Map<String, dynamic> data) async {
    final script = '''
      window.dispatchEvent(new CustomEvent('nativeEvent', {
        detail: {
          type: '$eventType',
          data: ${jsonEncode(data)}
        }
      }));
    ''';
    await webViewController.runJavaScript(script);
  }
}
```

#### Bridge Controller (메시지 라우팅)

```dart
// lib/core/bridge/bridge_controller.dart
import 'dart:convert';
import 'package:webview_flutter/webview_flutter.dart';

class BridgeController {
  final WebViewController webViewController;
  final JavaScriptBridge bridge;
  
  BridgeController(this.webViewController, this.bridge) {
    _setupJavaScriptHandler();
  }
  
  void _setupJavaScriptHandler() {
    // React에서 호출하는 JavaScript 함수 등록
    webViewController.addJavaScriptChannel(
      'NativeBridge',
      onMessageReceived: (JavaScriptMessage message) async {
        final data = jsonDecode(message.message) as Map<String, dynamic>;
        await _handleMessage(data);
      },
    );
  }
  
  Future<void> _handleMessage(Map<String, dynamic> data) async {
    final method = data['method'] as String;
    final params = data['params'] as Map<String, dynamic>?;
    final id = data['id'] as String;
    
    try {
      final result = await bridge.callMethod(method, params);
      _sendResponse(id, true, result);
    } catch (e) {
      _sendResponse(id, false, null, error: e.toString());
    }
  }
  
  Future<void> _sendResponse(
    String id,
    bool success,
    dynamic data, {
    String? error,
  }) async {
    final response = {
      'id': id,
      'success': success,
      'data': data,
      'error': error,
      'timestamp': DateTime.now().millisecondsSinceEpoch,
    };
    
    final script = '''
      window.nativeBridge?.handleResponse(${jsonEncode(response)});
    ''';
    await webViewController.runJavaScript(script);
  }
}
```

### 3.3 React Bridge 클라이언트

#### Native Bridge 클라이언트

```typescript
// src/bridge/nativeBridge.ts
interface PendingRequest {
  resolve: (value: any) => void;
  reject: (error: Error) => void;
  timeout: NodeJS.Timeout;
}

class NativeBridge {
  private pendingRequests = new Map<string, PendingRequest>();
  private eventListeners = new Map<string, Set<(data: any) => void>>();
  
  constructor() {
    this.setupBridge();
  }
  
  private setupBridge() {
    // Flutter WebView에서 호출할 수 있는 전역 함수 등록
    (window as any).nativeBridge = {
      handleResponse: (response: BridgeResponse) => {
        this.handleResponse(response);
      },
    };
    
    // 네이티브 이벤트 리스너
    window.addEventListener('nativeEvent', ((e: CustomEvent) => {
      const { type, data } = e.detail;
      this.emitEvent(type, data);
    }) as EventListener);
  }
  
  async callMethod(
    method: string,
    params?: Record<string, any>
  ): Promise<any> {
    const id = this.generateId();
    const message: BridgeMessage = {
      id,
      method,
      params,
      timestamp: Date.now(),
    };
    
    return new Promise((resolve, reject) => {
      const timeout = setTimeout(() => {
        this.pendingRequests.delete(id);
        reject(new Error(`Bridge call timeout: ${method}`));
      }, 30000);
      
      this.pendingRequests.set(id, { resolve, reject, timeout });
      
      // Flutter WebView의 JavaScript Channel 호출
      if ((window as any).NativeBridge) {
        (window as any).NativeBridge.postMessage(JSON.stringify(message));
      } else {
        reject(new Error('NativeBridge not available'));
      }
    });
  }
  
  private handleResponse(response: BridgeResponse) {
    const pending = this.pendingRequests.get(response.id);
    if (!pending) return;
    
    clearTimeout(pending.timeout);
    this.pendingRequests.delete(response.id);
    
    if (response.success) {
      pending.resolve(response.data);
    } else {
      pending.reject(new Error(response.error?.message || 'Unknown error'));
    }
  }
  
  onEvent(eventType: string, callback: (data: any) => void) {
    if (!this.eventListeners.has(eventType)) {
      this.eventListeners.set(eventType, new Set());
    }
    this.eventListeners.get(eventType)!.add(callback);
    
    return () => {
      this.eventListeners.get(eventType)?.delete(callback);
    };
  }
  
  private emitEvent(eventType: string, data: any) {
    const listeners = this.eventListeners.get(eventType);
    if (listeners) {
      listeners.forEach(callback => callback(data));
    }
  }
  
  private generateId(): string {
    return `${Date.now()}-${Math.random().toString(36).substr(2, 9)}`;
  }
}

export const nativeBridge = new NativeBridge();
```

#### 네이티브 서비스 래퍼

```typescript
// src/services/native/location.ts
import { nativeBridge } from '@/bridge/nativeBridge';

export interface Location {
  latitude: number;
  longitude: number;
  accuracy?: number;
  altitude?: number;
  speed?: number;
  timestamp: number;
}

export class LocationService {
  private trackingCallback?: (location: Location) => void;
  
  async requestPermission(): Promise<boolean> {
    try {
      const result = await nativeBridge.callMethod('requestLocationPermission');
      return result.granted === true;
    } catch (error) {
      console.error('Failed to request location permission:', error);
      return false;
    }
  }
  
  async getCurrentLocation(): Promise<Location> {
    const data = await nativeBridge.callMethod('getCurrentLocation');
    return {
      latitude: data.latitude,
      longitude: data.longitude,
      accuracy: data.accuracy,
      altitude: data.altitude,
      speed: data.speed,
      timestamp: data.timestamp,
    };
  }
  
  async startTracking(
    callback: (location: Location) => void
  ): Promise<void> {
    this.trackingCallback = callback;
    
    // 이벤트 리스너 등록
    nativeBridge.onEvent('locationUpdated', (data) => {
      callback({
        latitude: data.latitude,
        longitude: data.longitude,
        accuracy: data.accuracy,
        timestamp: data.timestamp,
      });
    });
    
    await nativeBridge.callMethod('startLocationTracking', {
      interval: 1000, // 1초마다 업데이트
      accuracy: 'high',
    });
  }
  
  async stopTracking(): Promise<void> {
    await nativeBridge.callMethod('stopLocationTracking');
    this.trackingCallback = undefined;
  }
}

export const locationService = new LocationService();
```

---

## 4. 네이티브 서비스 구현

### 4.1 위치 서비스

```dart
// lib/core/services/location_service.dart
import 'package:geolocator/geolocator.dart';
import '../bridge/javascript_bridge.dart';

class LocationService {
  final JavaScriptBridge bridge;
  StreamSubscription<Position>? _positionStream;
  
  LocationService(this.bridge);
  
  Future<Map<String, dynamic>> getCurrentLocation() async {
    final position = await Geolocator.getCurrentPosition(
      desiredAccuracy: LocationAccuracy.high,
    );
    
    return {
      'latitude': position.latitude,
      'longitude': position.longitude,
      'accuracy': position.accuracy,
      'altitude': position.altitude,
      'speed': position.speed,
      'timestamp': position.timestamp.millisecondsSinceEpoch,
    };
  }
  
  Future<void> startTracking({
    int interval = 1000,
    LocationAccuracy accuracy = LocationAccuracy.high,
  }) async {
    _positionStream = Geolocator.getPositionStream(
      locationSettings: LocationSettings(
        accuracy: accuracy,
        distanceFilter: 10, // 10m 이상 이동 시 업데이트
      ),
    ).listen((position) {
      bridge.sendEventToReact('locationUpdated', {
        'latitude': position.latitude,
        'longitude': position.longitude,
        'accuracy': position.accuracy,
        'timestamp': position.timestamp.millisecondsSinceEpoch,
      });
    });
  }
  
  Future<void> stopTracking() async {
    await _positionStream?.cancel();
    _positionStream = null;
  }
}
```

### 4.2 카메라/사진 서비스

```dart
// lib/core/services/camera_service.dart
import 'package:image_picker/image_picker.dart';
import 'dart:io';

class CameraService {
  final ImagePicker _picker = ImagePicker();
  
  Future<Map<String, dynamic>> pickImage({
    ImageSource source = ImageSource.camera,
    int maxWidth = 1920,
    int maxHeight = 1920,
    int quality = 85,
  }) async {
    final XFile? image = await _picker.pickImage(
      source: source,
      maxWidth: maxWidth.toDouble(),
      maxHeight: maxHeight.toDouble(),
      imageQuality: quality,
    );
    
    if (image == null) {
      throw Exception('No image selected');
    }
    
    final file = File(image.path);
    final bytes = await file.readAsBytes();
    
    return {
      'path': image.path,
      'name': image.name,
      'size': bytes.length,
      'mimeType': image.mimeType ?? 'image/jpeg',
      'base64': base64Encode(bytes), // 또는 파일 경로만 반환
    };
  }
  
  Future<List<Map<String, dynamic>>> pickMultipleImages({
    int maxImages = 10,
    int maxWidth = 1920,
    int maxHeight = 1920,
  }) async {
    final List<XFile> images = await _picker.pickMultiImage(
      maxWidth: maxWidth.toDouble(),
      maxHeight: maxHeight.toDouble(),
    );
    
    if (images.isEmpty) {
      return [];
    }
    
    return await Future.wait(
      images.take(maxImages).map((image) async {
        final file = File(image.path);
        final bytes = await file.readAsBytes();
        return {
          'path': image.path,
          'name': image.name,
          'size': bytes.length,
          'mimeType': image.mimeType ?? 'image/jpeg',
        };
      }),
    );
  }
}
```

### 4.3 푸시 알림 서비스

```dart
// lib/core/services/push_notification_service.dart
import 'package:firebase_messaging/firebase_messaging.dart';
import '../bridge/javascript_bridge.dart';

class PushNotificationService {
  final JavaScriptBridge bridge;
  final FirebaseMessaging _messaging = FirebaseMessaging.instance;
  
  PushNotificationService(this.bridge) {
    _setupListeners();
  }
  
  void _setupListeners() {
    // 포그라운드 메시지 처리
    FirebaseMessaging.onMessage.listen((RemoteMessage message) {
      bridge.sendEventToReact('pushNotificationReceived', {
        'title': message.notification?.title,
        'body': message.notification?.body,
        'data': message.data,
      });
    });
    
    // 백그라운드에서 알림 탭 처리
    FirebaseMessaging.onMessageOpenedApp.listen((RemoteMessage message) {
      bridge.sendEventToReact('pushNotificationReceived', {
        'title': message.notification?.title,
        'body': message.notification?.body,
        'data': message.data,
        'opened': true,
      });
    });
  }
  
  Future<bool> requestPermission() async {
    final settings = await _messaging.requestPermission(
      alert: true,
      badge: true,
      sound: true,
    );
    return settings.authorizationStatus == AuthorizationStatus.authorized;
  }
  
  Future<String?> getToken() async {
    return await _messaging.getToken();
  }
  
  Future<void> subscribeToTopic(String topic) async {
    await _messaging.subscribeToTopic(topic);
  }
}
```

---

## 5. WebView 통합

### 5.1 WebView Wrapper

```dart
// lib/core/webview/webview_wrapper.dart
import 'package:flutter/material.dart';
import 'package:webview_flutter/webview_flutter.dart';
import '../bridge/bridge_controller.dart';
import '../bridge/javascript_bridge.dart';

class WebViewWrapper extends StatefulWidget {
  final String initialUrl;
  
  const WebViewWrapper({
    super.key,
    required this.initialUrl,
  });
  
  @override
  State<WebViewWrapper> createState() => _WebViewWrapperState();
}

class _WebViewWrapperState extends State<WebViewWrapper> {
  late final WebViewController _controller;
  late final JavaScriptBridge _bridge;
  late final BridgeController _bridgeController;
  
  @override
  void initState() {
    super.initState();
    _initializeWebView();
  }
  
  void _initializeWebView() {
    _controller = WebViewController()
      ..setJavaScriptMode(JavaScriptMode.unrestricted)
      ..setNavigationDelegate(
        NavigationDelegate(
          onPageStarted: (String url) {
            // 페이지 로딩 시작
          },
          onPageFinished: (String url) {
            // 페이지 로딩 완료 시 브릿지 초기화
            _injectBridgeScript();
          },
          onWebResourceError: (WebResourceError error) {
            // 에러 처리
          },
        ),
      )
      ..loadRequest(Uri.parse(widget.initialUrl));
    
    _bridge = JavaScriptBridge(_controller);
    _bridgeController = BridgeController(_controller, _bridge);
  }
  
  Future<void> _injectBridgeScript() async {
    // React 앱에서 사용할 브릿지 스크립트 주입
    await _controller.runJavaScript('''
      (function() {
        if (window.nativeBridge) return;
        
        window.nativeBridge = {
          callMethod: function(method, params) {
            return new Promise((resolve, reject) => {
              const id = Date.now() + '-' + Math.random().toString(36).substr(2, 9);
              
              window._bridgeCallbacks = window._bridgeCallbacks || {};
              window._bridgeCallbacks[id] = { resolve, reject };
              
              NativeBridge.postMessage(JSON.stringify({
                id: id,
                method: method,
                params: params || {},
                timestamp: Date.now()
              }));
              
              setTimeout(() => {
                if (window._bridgeCallbacks[id]) {
                  delete window._bridgeCallbacks[id];
                  reject(new Error('Bridge call timeout'));
                }
              }, 30000);
            });
          },
          
          handleResponse: function(response) {
            const callback = window._bridgeCallbacks?.[response.id];
            if (callback) {
              delete window._bridgeCallbacks[response.id];
              if (response.success) {
                callback.resolve(response.data);
              } else {
                callback.reject(new Error(response.error?.message || 'Unknown error'));
              }
            }
          }
        };
      })();
    ''');
  }
  
  @override
  Widget build(BuildContext context) {
    return WebViewWidget(controller: _controller);
  }
}
```

### 5.2 메인 앱 구조

```dart
// lib/app.dart
import 'package:flutter/material.dart';
import 'core/webview/webview_wrapper.dart';
import 'native/splash_screen.dart';

class App extends StatefulWidget {
  const App({super.key});
  
  @override
  State<App> createState() => _AppState();
}

class _AppState extends State<App> {
  bool _isInitialized = false;
  
  @override
  void initState() {
    super.initState();
    _initializeApp();
  }
  
  Future<void> _initializeApp() async {
    // 네이티브 초기화 작업
    // - 권한 확인
    // - 서비스 초기화
    // - 토큰 확인 등
    
    await Future.delayed(const Duration(seconds: 2));
    
    if (mounted) {
      setState(() {
        _isInitialized = true;
      });
    }
  }
  
  @override
  Widget build(BuildContext context) {
    if (!_isInitialized) {
      return const SplashScreen();
    }
    
    // React 앱 URL (로컬 개발 서버 또는 빌드된 파일)
    const reactAppUrl = 'http://localhost:5173'; // Vite 개발 서버
    // const reactAppUrl = 'assets/web/index.html'; // 프로덕션 빌드
    
    return MaterialApp(
      title: 'GoldPet',
      debugShowCheckedModeBanner: false,
      home: WebViewWrapper(initialUrl: reactAppUrl),
    );
  }
}
```

---

## 6. React 앱 구조

### 6.1 메인 진입점

```typescript
// src/main.tsx
import React from 'react';
import ReactDOM from 'react-dom/client';
import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import App from './App';
import './index.css';

const queryClient = new QueryClient({
  defaultOptions: {
    queries: {
      retry: 1,
      refetchOnWindowFocus: false,
    },
  },
});

ReactDOM.createRoot(document.getElementById('root')!).render(
  <React.StrictMode>
    <QueryClientProvider client={queryClient}>
      <App />
    </QueryClientProvider>
  </React.StrictMode>
);
```

### 6.2 라우팅 구조

```typescript
// src/App.tsx
import { BrowserRouter, Routes, Route } from 'react-router-dom';
import { OnboardingPage } from './features/onboarding/OnboardingPage';
import { AuthPage } from './features/auth/AuthPage';
import { HomePage } from './features/home/HomePage';
import { WalkPage } from './features/walk/WalkPage';
import { ChatPage } from './features/chat/ChatPage';
import { ProfilePage } from './features/profile/ProfilePage';

export default function App() {
  return (
    <BrowserRouter>
      <Routes>
        <Route path="/onboarding" element={<OnboardingPage />} />
        <Route path="/auth" element={<AuthPage />} />
        <Route path="/" element={<HomePage />} />
        <Route path="/walk" element={<WalkPage />} />
        <Route path="/chat" element={<ChatPage />} />
        <Route path="/profile" element={<ProfilePage />} />
      </Routes>
    </BrowserRouter>
  );
}
```

### 6.3 상태 관리 예시

```typescript
// src/stores/authStore.ts
import { create } from 'zustand';
import { persist } from 'zustand/middleware';

interface AuthState {
  token: string | null;
  user: User | null;
  isAuthenticated: boolean;
  login: (token: string, user: User) => void;
  logout: () => void;
}

export const useAuthStore = create<AuthState>()(
  persist(
    (set) => ({
      token: null,
      user: null,
      isAuthenticated: false,
      login: (token, user) => {
        set({ token, user, isAuthenticated: true });
      },
      logout: () => {
        set({ token: null, user: null, isAuthenticated: false });
      },
    }),
    {
      name: 'auth-storage',
    }
  )
);
```

---

## 7. 빌드 및 배포 전략

### 7.1 개발 환경

#### React 개발 서버

```bash
# React 앱 디렉토리
cd react-app
npm install
npm run dev  # Vite 개발 서버 시작 (http://localhost:5173)
```

#### Flutter 개발

```bash
# Flutter 앱 디렉토리
cd flutter/goldpet_app
flutter pub get
flutter run  # WebView가 http://localhost:5173 로드
```

### 7.2 프로덕션 빌드

#### React 앱 빌드

```bash
cd react-app
npm run build  # dist/ 폴더에 빌드 결과물 생성
```

#### Flutter에 React 빌드 결과물 포함

```
flutter/goldpet_app/
├── assets/
│   └── web/
│       ├── index.html
│       ├── assets/
│       └── ...
```

```dart
// pubspec.yaml
flutter:
  assets:
    - assets/web/
```

```dart
// WebView 로드 시
final reactAppUrl = kIsWeb
    ? 'http://localhost:5173'
    : 'file://${await rootBundle.loadString('assets/web/index.html')}';
```

### 7.3 CI/CD 파이프라인

```yaml
# .github/workflows/build.yml
name: Build Hybrid App

on:
  push:
    branches: [main]

jobs:
  build-react:
    runs-on: ubuntu-latest
    steps:
      - uses: actions/checkout@v3
      - uses: actions/setup-node@v3
      - run: |
          cd react-app
          npm ci
          npm run build
      - uses: actions/upload-artifact@v3
        with:
          name: react-build
          path: react-app/dist
  
  build-flutter:
    needs: build-react
    runs-on: ubuntu-latest
    steps:
      - uses: actions/checkout@v3
      - uses: subosito/flutter-action@v2
      - uses: actions/download-artifact@v3
        with:
          name: react-build
          path: flutter/goldpet_app/assets/web
      - run: |
          cd flutter/goldpet_app
          flutter pub get
          flutter build apk --release
          flutter build ios --release
```

---

## 8. 성능 최적화

### 8.1 WebView 성능

- **캐싱 전략**: React 앱을 로컬에 저장하고 버전 관리
  - 빌드된 React 앱을 `assets/web/`에 포함
  - 버전별 캐싱으로 업데이트 관리
- **프리로딩**: 앱 시작 시 WebView 미리 초기화
  - 스플래시 화면 동안 WebView 준비
- **메모리 관리**: 불필요한 리소스 해제
  - 페이지 전환 시 이전 컴포넌트 언마운트
  - 이미지 캐시 크기 제한

### 8.2 React 앱 최적화

- **코드 스플리팅**: React.lazy()로 라우트별 분리
  ```typescript
  const WalkPage = lazy(() => import('./features/walk/WalkPage'));
  const ChatPage = lazy(() => import('./features/chat/ChatPage'));
  ```
- **이미지 최적화**: WebP 포맷, lazy loading
  - 네이티브에서 이미지 리사이징 후 전달
  - Intersection Observer로 lazy loading
- **번들 크기**: Tree shaking, 압축
  - Vite의 자동 트리 쉐이킹 활용
  - Gzip/Brotli 압축
- **상태 관리 최적화**
  - React Query로 서버 상태 캐싱
  - Zustand로 클라이언트 상태 최소화

### 8.3 네이티브 통신 최적화

- **배치 처리**: 여러 요청을 한 번에 처리
  ```typescript
  // 여러 위치 업데이트를 한 번에 전송
  const batchLocationUpdates = debounce((locations) => {
    nativeBridge.callMethod('batchLocationUpdate', { locations });
  }, 1000);
  ```
- **캐싱**: 자주 사용하는 데이터 캐싱
  - 위치 정보는 5초 캐시
  - 권한 상태는 메모리 캐시
- **에러 재시도**: 네트워크 오류 시 자동 재시도
  - Exponential backoff 전략
- **이벤트 최적화**
  - 위치 업데이트는 필요한 경우만 전송
  - Throttle/Debounce 적용

### 8.4 Naver Map 최적화

- **지도 타일 캐싱**
  - 네이티브 레벨에서 타일 캐싱 활용
  - 오프라인 모드 지원 (선택)
- **마커 최적화**
  - 가상화(Virtualization)로 보이는 마커만 렌더링
  - 클러스터링으로 마커 수 감소
- **Polyline 최적화**
  - Douglas-Peucker 알고리즘으로 좌표 간소화
  - 서버에서 간소화된 경로 제공 옵션 활용

### 8.5 배터리 최적화

- **위치 추적 최적화**
  - 산책 중에만 고정밀 위치 추적
  - 백그라운드에서는 낮은 정확도 사용
- **백그라운드 작업 최소화**
  - WebView는 포그라운드에서만 활성화
  - 네이티브 서비스만 백그라운드 실행

---

## 9. 보안 고려사항

### 9.1 JavaScript Bridge 보안

- **메서드 화이트리스트**: 허용된 메서드만 호출 가능
  ```dart
  final allowedMethods = [
    'getCurrentLocation',
    'openCamera',
    // ... 허용된 메서드만
  ];
  
  if (!allowedMethods.contains(method)) {
    throw PlatformException(code: 'METHOD_NOT_ALLOWED');
  }
  ```
- **파라미터 검증**: 모든 입력값 검증
  - 타입 체크
  - 범위 검증 (예: 위도/경도 범위)
  - SQL Injection 방지 (파일 경로 등)
- **타임아웃**: 요청 타임아웃 설정
  - 기본 30초, 위치 요청은 10초
- **Rate Limiting**: 과도한 요청 방지
  - 메서드별 호출 빈도 제한

### 9.2 WebView 보안

- **HTTPS 강제**: 프로덕션에서는 HTTPS만 허용
  ```dart
  if (kReleaseMode) {
    _controller.setNavigationDelegate(
      NavigationDelegate(
        onNavigationRequest: (request) {
          if (!request.url.startsWith('https://')) {
            return NavigationDecision.prevent;
          }
          return NavigationDecision.navigate;
        },
      ),
    );
  }
  ```
- **CSP (Content Security Policy)**: XSS 방지
  - React 앱에 CSP 헤더 추가
  - 인라인 스크립트 제한
- **로컬 파일 접근 제한**: 민감한 파일 접근 차단
  - 파일 경로 검증
  - 상위 디렉토리 접근 차단

### 9.3 데이터 보안

- **토큰 관리**
  - Access Token은 메모리에만 저장
  - Refresh Token은 Secure Storage 사용
  - 토큰 만료 시 자동 갱신
- **민감 정보 암호화**
  - 위치 정보는 전송 시 암호화 (선택)
  - 로컬 저장소는 암호화된 SharedPreferences 사용
- **API 통신 보안**
  - 모든 API 요청에 HTTPS 사용
  - Certificate Pinning 고려 (선택)

### 9.4 권한 관리

- **최소 권한 원칙**
  - 필요한 권한만 요청
  - 권한 사용 목적 명확히 설명
- **권한 상태 모니터링**
  - 권한 거부 시 대체 플로우 제공
  - 설정 화면으로 안내

### 9.5 콘텐츠 필터링

- **이미지 필터링**
  - AWS Rekognition 또는 Google Cloud Vision API
  - NSFW 스코어 0.7 이상 차단
- **텍스트 필터링**
  - 욕설/혐오 표현 필터링 (서버 측)
  - 신고 기능 통합

---

## 10. 마이그레이션 계획

### 10.1 단계별 전환

#### Phase 1: 인프라 구축 (1-2주)
- [ ] Flutter WebView 통합
- [ ] JavaScript Bridge 구현
- [ ] 기본 네이티브 서비스 구현 (위치, 카메라, 푸시)
- [ ] React 앱 기본 구조 설정
- [ ] 개발 환경 설정 (Vite, TypeScript, 라우팅)

#### Phase 2: 핵심 기능 이전 (2-3주)
- [ ] 온보딩 화면 React로 이전
  - [ ] 온보딩 캐러셀
  - [ ] SNS 로그인 페이지
- [ ] 인증 플로우 React로 이전
  - [ ] 프로필 설정
  - [ ] 위치 권한 및 설정
- [ ] 프로필 화면 React로 이전
  - [ ] 사용자 프로필 편집
  - [ ] 펫 등록/편집

#### Phase 3: 지도 및 위치 기능 (3-4주)
- [ ] Naver Map Web API 통합
  - [ ] 지도 초기화 및 기본 컨트롤
  - [ ] 마커 클러스터링 구현
- [ ] 산책 기능 (GPS 통합)
  - [ ] 산책 추적 (네이티브 GPS + React UI)
  - [ ] 산책 기록 저장
  - [ ] 산책 지도 상세 화면
- [ ] 친구 지도 화면
  - [ ] 근처 친구 조회 API 연동
  - [ ] 마커 및 카드 연동
  - [ ] 필터 및 정렬 기능

#### Phase 4: 소셜 기능 (2-3주)
- [ ] 채팅 기능
  - [ ] 채팅방 리스트
  - [ ] 1:1 채팅 (WebSocket 또는 폴링)
  - [ ] 이미지 전송 (네이티브 카메라 연동)
- [ ] 커뮤니티 기능
  - [ ] 게시글 피드
  - [ ] 게시글 작성/수정
  - [ ] 이미지 업로드 (NSFW 필터링)

#### Phase 5: 게이미피케이션 및 고급 기능 (2-3주)
- [ ] 뱃지 시스템
  - [ ] 뱃지 목록 및 상세
  - [ ] 뱃지 획득 알림
- [ ] 챌린지 시스템
  - [ ] 챌린지 목록
  - [ ] 진행 상황 추적
- [ ] 장소 체크인
  - [ ] 지오펜싱 모니터링
  - [ ] 체크인 UI

#### Phase 6: AI 및 Gold 시스템 (2주)
- [ ] AI 펫 프로필 생성
  - [ ] 비동기 처리 플로우
  - [ ] 푸시 알림 연동
- [ ] Gold 시스템
  - [ ] Gold 잔액 조회
  - [ ] IAP 연동 (네이티브)
  - [ ] Gold 사용 내역

#### Phase 7: 최적화 및 테스트 (2주)
- [ ] 성능 최적화
  - [ ] WebView 캐싱 전략
  - [ ] 이미지 최적화
  - [ ] 번들 크기 최적화
- [ ] 통합 테스트
  - [ ] Bridge 통신 테스트
  - [ ] 네이티브 기능 테스트
  - [ ] E2E 테스트
- [ ] 사용자 테스트
  - [ ] 베타 테스트
  - [ ] 피드백 수집 및 개선

### 10.2 기존 코드 처리

- **Flutter UI 코드**: 점진적으로 제거
  - Phase 2부터 React로 대체
  - 기존 코드는 백업 후 삭제
- **비즈니스 로직**: React로 이전 또는 공유
  - API 호출 로직은 React로 완전 이전
  - 공통 유틸리티는 TypeScript로 재작성
- **네이티브 기능**: 그대로 유지 및 확장
  - 기존 서비스는 Bridge로 노출
  - 새로운 기능 추가 시 Bridge 메서드 확장

### 10.2 기존 코드 처리

- **Flutter UI 코드**: 점진적으로 제거
- **비즈니스 로직**: React로 이전 또는 공유
- **네이티브 기능**: 그대로 유지 및 확장

---

## 11. 장단점 분석

### 11.1 장점

1. **디자인 구현 용이성**
   - React 생태계의 풍부한 UI 라이브러리
   - Figma 디자인을 정확하게 구현 가능
   - 웹 개발자 풀 활용 가능

2. **개발 속도**
   - 웹 기술로 빠른 프로토타이핑
   - Hot Reload로 빠른 개발 사이클
   - 크로스 플랫폼 UI 코드 재사용

3. **유지보수성**
   - UI와 네이티브 로직 분리
   - 웹 기술 스택의 풍부한 도구
   - 버전 관리 및 배포 용이

### 11.2 단점

1. **성능**
   - WebView 오버헤드
   - 네이티브 앱 대비 느린 렌더링
   - 메모리 사용량 증가

2. **복잡성**
   - 두 가지 기술 스택 관리
   - 디버깅 어려움 (Flutter + React)
   - 빌드 프로세스 복잡

3. **제한사항**
   - 일부 네이티브 기능 접근 제한
   - 플랫폼별 차이점 처리 필요
   - 오프라인 기능 구현 복잡

---

## 12. GoldPet 특화 기능 통합

### 12.1 Naver Map 통합

#### React에서 Naver Map Web API 사용

```typescript
// src/services/map/naverMapService.ts
export class NaverMapService {
  private map: naver.maps.Map | null = null;
  
  async initializeMap(containerId: string, center: { lat: number; lng: number }) {
    const mapOptions = {
      center: new naver.maps.LatLng(center.lat, center.lng),
      zoom: 15,
      mapTypeControl: true,
    };
    
    this.map = new naver.maps.Map(containerId, mapOptions);
    return this.map;
  }
  
  // 마커 클러스터링 (핫플레이스 대응)
  createClusterer(markers: naver.maps.Marker[]) {
    const clusterer = new naver.maps.MarkerClustering({
      minClusterSize: 2,
      maxZoom: 15,
      map: this.map!,
      markers: markers,
    });
    return clusterer;
  }
  
  // Polyline 그리기 (산책 경로)
  drawPolyline(coordinates: number[][]) {
    const path = coordinates.map(
      ([lng, lat]) => new naver.maps.LatLng(lat, lng)
    );
    
    const polyline = new naver.maps.Polyline({
      map: this.map!,
      path: path,
      strokeColor: '#FF6B6B',
      strokeWeight: 5,
      strokeOpacity: 0.8,
    });
    
    return polyline;
  }
}
```

#### Bridge를 통한 네이티브 위치 서비스 연동

```typescript
// React에서 네이티브 GPS와 Naver Map 연동
const locationService = new LocationService();

// 위치 추적 시작
await locationService.startTracking((location) => {
  // Naver Map 중심 업데이트
  naverMapService.setCenter(location.latitude, location.longitude);
  
  // 산책 경로에 포인트 추가
  walkPolyline.addPoint(location.latitude, location.longitude);
});
```

### 12.2 게이미피케이션 기능

#### 뱃지 시스템

```typescript
// src/features/gamification/badgeService.ts
export class BadgeService {
  async checkBadgeProgress(userId: number) {
    // 산책 거리, 연속 일수 등 체크
    const progress = await api.get(`/badges/progress`);
    
    // 뱃지 획득 조건 달성 시 네이티브 푸시 알림
    if (progress.newBadges.length > 0) {
      await nativeBridge.callMethod('showNotification', {
        title: '새로운 뱃지를 획득했어요!',
        body: `${progress.newBadges[0].name} 뱃지를 획득했습니다.`,
      });
    }
  }
}
```

#### 챌린지 시스템

```typescript
// src/features/gamification/challengeService.ts
export class ChallengeService {
  async getActiveChallenges() {
    return await api.get('/challenges/active');
  }
  
  async updateChallengeProgress(challengeId: number, progress: number) {
    return await api.post(`/challenges/${challengeId}/progress`, { progress });
  }
}
```

#### 장소 체크인 (지오펜싱)

```typescript
// src/features/places/checkInService.ts
export class CheckInService {
  async checkNearbyPlaces(currentLocation: Location) {
    const places = await api.get('/places/nearby', {
      params: {
        lat: currentLocation.latitude,
        lng: currentLocation.longitude,
        radiusKm: 0.1, // 100m 반경
      },
    });
    
    // 네이티브에서 지오펜싱 모니터링 시작
    await nativeBridge.callMethod('startGeofencing', {
      places: places.map(p => ({
        id: p.id,
        lat: p.lat,
        lng: p.lng,
        radius: 50, // 50m 반경
      })),
    });
  }
  
  // 네이티브에서 체크인 가능 이벤트 수신
  setupCheckInListener() {
    nativeBridge.onEvent('geofenceEntered', async (data) => {
      const place = data.place;
      // 체크인 버튼 활성화 UI 표시
      showCheckInButton(place);
    });
  }
}
```

### 12.3 위치 기반 서비스 고도화

#### 산책 속도 제한 (어뷰징 방지)

```dart
// lib/core/services/walk_service.dart
class WalkService {
  final LocationService locationService;
  List<Position> _trackedPositions = [];
  
  Future<void> trackWalk() async {
    await locationService.startTracking((position) {
      _trackedPositions.add(position);
      
      // 속도 검증
      if (_trackedPositions.length >= 2) {
        final last = _trackedPositions[_trackedPositions.length - 1];
        final prev = _trackedPositions[_trackedPositions.length - 2];
        
        final distance = Geolocator.distanceBetween(
          prev.latitude,
          prev.longitude,
          last.latitude,
          last.longitude,
        );
        
        final timeDiff = last.timestamp.difference(prev.timestamp).inSeconds;
        final speedKmh = (distance / timeDiff) * 3.6;
        
        // 15km/h 이상이면 경고
        if (speedKmh > 15) {
          bridge.sendEventToReact('walkSpeedWarning', {
            'speed': speedKmh,
            'message': '산책 속도가 너무 빠릅니다. 차량 이동이 감지되었습니다.',
          });
        }
      }
    });
  }
}
```

#### 마커 클러스터링

```typescript
// React에서 Naver Map 클러스터링 적용
import { MarkerClustering } from '@navermaps/marker-clustering';

const setupFriendMapMarkers = (friends: Friend[]) => {
  const markers = friends.map(friend => 
    new naver.maps.Marker({
      position: new naver.maps.LatLng(friend.location.lat, friend.location.lng),
      title: friend.nickname,
    })
  );
  
  // 클러스터링 적용
  const clusterer = new MarkerClustering({
    minClusterSize: 2,
    maxZoom: 15,
    map: naverMap,
    markers: markers,
  });
  
  return clusterer;
};
```

### 12.4 Gold 시스템 (유료/무료 구분)

```typescript
// src/features/gold/goldService.ts
export interface GoldBalance {
  paidGold: number;    // 유료 Gold (환불 가능)
  freeGold: number;     // 무료 Gold (환불 불가)
  totalGold: number;
}

export class GoldService {
  async getBalance(): Promise<GoldBalance> {
    return await api.get('/gold/balance');
  }
  
  async useGold(amount: number, purpose: string) {
    // 무료 Gold 우선 차감 로직은 서버에서 처리
    return await api.post('/gold/use', { amount, purpose });
  }
  
  async purchaseGold(packageId: number) {
    // 네이티브 IAP 호출
    const result = await nativeBridge.callMethod('purchaseGold', {
      packageId,
    });
    
    // 서버에 구매 결과 전송
    return await api.post('/gold/purchase', {
      transactionId: result.transactionId,
      packageId,
    });
  }
}
```

### 12.5 콘텐츠 필터링 (NSFW)

```typescript
// 이미지 업로드 전 필터링
export class ContentFilterService {
  async uploadImage(file: File): Promise<string> {
    // 네이티브에서 이미지 필터링 (AWS Rekognition 연동)
    const filterResult = await nativeBridge.callMethod('filterImage', {
      imagePath: file.path,
    });
    
    if (filterResult.nsfwScore > 0.7) {
      throw new Error('부적절한 이미지입니다.');
    }
    
    // 필터링 통과 시 서버 업로드
    return await api.upload('/files/images', file);
  }
}
```

### 12.6 AI 비동기 처리

```typescript
// src/features/ai/aiPetService.ts
export class AIPetService {
  async generatePetProfile(petId: number, style: string) {
    // Gold 차감 및 요청 ID 반환
    const request = await api.post('/ai/pet-profile', {
      petId,
      style,
    });
    
    // 비동기 처리 시작 (서버에서 큐에 추가)
    // 클라이언트는 대기하지 않음
    
    // 푸시 알림 리스너 설정
    nativeBridge.onEvent('pushNotificationReceived', (notification) => {
      if (notification.data?.type === 'ai_complete' && 
          notification.data?.requestId === request.id) {
        // AI 생성 완료 알림
        showAIPetResult(notification.data.resultUrl);
      }
    });
    
    return request.id;
  }
}
```

### 12.7 온보딩 플로우 (React 구현)

```typescript
// src/features/onboarding/OnboardingFlow.tsx
export const OnboardingFlow = () => {
  const [step, setStep] = useState<'onboarding' | 'login' | 'profile' | 'pet'>('onboarding');
  
  return (
    <Routes>
      <Route path="/onboarding" element={<OnboardingCarousel />} />
      <Route path="/login" element={<SocialLoginPage />} />
      <Route path="/profile/setup" element={<ProfileSetupPage />} />
      <Route path="/location/permission" element={<LocationPermissionPage />} />
      <Route path="/location/select" element={<LocationSelectPage />} />
      <Route path="/pet/intro" element={<PetIntroPage />} />
      <Route path="/pet/basic" element={<PetBasicInfoPage />} />
      <Route path="/pet/detail" element={<PetDetailInfoPage />} />
      <Route path="/pet/photo" element={<PetPhotoPage />} />
      <Route path="/congrats" element={<CongratsPage />} />
    </Routes>
  );
};
```

### 12.8 지도 화면 구현

#### 친구 지도 화면

```typescript
// src/features/friends/FriendMapPage.tsx
export const FriendMapPage = () => {
  const [friends, setFriends] = useState<Friend[]>([]);
  const [selectedFriend, setSelectedFriend] = useState<Friend | null>(null);
  
  useEffect(() => {
    // 현재 위치 가져오기
    locationService.getCurrentLocation().then(location => {
      // 근처 친구 조회
      api.get('/friends/nearby', {
        params: {
          lat: location.latitude,
          lng: location.longitude,
          radiusKm: 3,
        },
      }).then(response => {
        setFriends(response.data.items);
        setupMapMarkers(response.data.items);
      });
    });
  }, []);
  
  return (
    <div>
      <NaverMap 
        markers={friends.map(f => ({
          id: f.userId,
          position: { lat: f.location.lat, lng: f.location.lng },
          onClick: () => setSelectedFriend(f),
        }))}
      />
      <FriendBottomSheet 
        friends={friends}
        selectedFriend={selectedFriend}
        onSelect={setSelectedFriend}
      />
    </div>
  );
};
```

#### 산책 지도 상세 화면

```typescript
// src/features/walk/WalkMapPage.tsx
export const WalkMapPage = ({ walkId }: { walkId: number }) => {
  const [walk, setWalk] = useState<WalkDetail | null>(null);
  
  useEffect(() => {
    api.get(`/walks/${walkId}`).then(response => {
      const walkData = response.data;
      setWalk(walkData);
      
      // Polyline 그리기
      naverMapService.drawPolyline(walkData.route.coordinates);
      
      // Spot 마커 추가
      walkData.spots.forEach(spot => {
        naverMapService.addMarker({
          position: { lat: spot.lat, lng: spot.lng },
          icon: spot.photoUrl,
          onClick: () => showSpotDetail(spot),
        });
      });
    });
  }, [walkId]);
  
  return (
    <div>
      <NaverMap />
      <WalkBottomSheet walk={walk} />
    </div>
  );
};
```

### 12.9 프라이버시 설정 (Ghost Mode)

```typescript
// src/features/settings/PrivacySettings.tsx
export const PrivacySettings = () => {
  const [locationVisibility, setLocationVisibility] = useState<'public' | 'friends' | 'ghost'>('friends');
  
  const handleVisibilityChange = async (value: string) => {
    await api.put('/users/me/privacy', {
      locationVisibility: value,
    });
    
    setLocationVisibility(value);
    
    // Ghost Mode 활성화 시 위치 추적 중지
    if (value === 'ghost') {
      await locationService.stopTracking();
    }
  };
  
  return (
    <Select value={locationVisibility} onChange={handleVisibilityChange}>
      <option value="public">전체 공개</option>
      <option value="friends">친구만 공개</option>
      <option value="ghost">유령 모드 (위치 비공개)</option>
    </Select>
  );
};
```

### 12.10 API 클라이언트 구조

```typescript
// src/services/api/client.ts
import axios from 'axios';
import { useAuthStore } from '@/stores/authStore';

const apiClient = axios.create({
  baseURL: import.meta.env.VITE_API_BASE_URL || 'http://localhost:8080/api/v1',
});

// 요청 인터셉터: 토큰 추가
apiClient.interceptors.request.use((config) => {
  const token = useAuthStore.getState().token;
  if (token) {
    config.headers.Authorization = `Bearer ${token}`;
  }
  return config;
});

// 응답 인터셉터: 토큰 갱신
apiClient.interceptors.response.use(
  (response) => response,
  async (error) => {
    if (error.response?.status === 401) {
      // Refresh token으로 재시도
      const newToken = await refreshToken();
      if (newToken) {
        error.config.headers.Authorization = `Bearer ${newToken}`;
        return apiClient.request(error.config);
      }
    }
    return Promise.reject(error);
  }
);

export default apiClient;
```

---

## 13. Figma 디자인 통합 및 UI/UX 작업 계획

### 13.1 Figma 디자인 링크

모든 화면 디자인은 Figma에서 관리되며, 아래 링크를 통해 접근할 수 있습니다.

**Figma 파일**: [GoldPet-Local](https://www.figma.com/design/VIjCdyPTnywcM9HzpoOqlh/GoldPet-Local)

#### 13.1.1 인증 및 온보딩 화면

| 화면 | Figma 링크 | Node ID | 우선순위 |
|------|-----------|---------|---------|
| Splash | [링크](https://www.figma.com/design/VIjCdyPTnywcM9HzpoOqlh/GoldPet-Local?node-id=4017-21056&m=dev) | 4017-21056 | P0 |
| Onboarding 1 | [링크](https://www.figma.com/design/VIjCdyPTnywcM9HzpoOqlh/GoldPet-Local?node-id=4017-20145&m=dev) | 4017-20145 | P0 |
| Onboarding 2 | [링크](https://www.figma.com/design/VIjCdyPTnywcM9HzpoOqlh/GoldPet-Local?node-id=4017-20342&m=dev) | 4017-20342 | P0 |
| Onboarding 3 | [링크](https://www.figma.com/design/VIjCdyPTnywcM9HzpoOqlh/GoldPet-Local?node-id=4017-20540&m=dev) | 4017-20540 | P0 |
| Onboarding 4 | [링크](https://www.figma.com/design/VIjCdyPTnywcM9HzpoOqlh/GoldPet-Local?node-id=4017-20737&m=dev) | 4017-20737 | P0 |
| 권한 안내 | - | - | P0 |
| 혜택 안내 | [링크](https://www.figma.com/design/VIjCdyPTnywcM9HzpoOqlh/GoldPet-Local?node-id=4017-20935&m=dev) | 4017-20935 | P0 |
| 로그인 | [링크](https://www.figma.com/design/VIjCdyPTnywcM9HzpoOqlh/GoldPet-Local?node-id=4017-22523&m=dev) | 4017-22523 | P0 |

#### 13.1.2 회원가입 화면

| 화면 | Figma 링크 | Node ID | 우선순위 |
|------|-----------|---------|---------|
| 약관 동의 | [링크](https://www.figma.com/design/VIjCdyPTnywcM9HzpoOqlh/GoldPet-Local?node-id=4017-22778&m=dev) | 4017-22778 | P0 |
| 약관 동의 (체크됨) | [링크](https://www.figma.com/design/VIjCdyPTnywcM9HzpoOqlh/GoldPet-Local?node-id=4017-22810&m=dev) | 4017-22810 | P0 |
| 일반회원 가입 입력 폼 | [링크](https://www.figma.com/design/VIjCdyPTnywcM9HzpoOqlh/GoldPet-Local?node-id=4017-22718&m=dev) | 4017-22718 | P0 |
| 아이디 중복체크 활성화 | [링크](https://www.figma.com/design/VIjCdyPTnywcM9HzpoOqlh/GoldPet-Local?node-id=4017-22733&m=dev) | 4017-22733 | P0 |
| 가입 에러 메시지 | [링크](https://www.figma.com/design/VIjCdyPTnywcM9HzpoOqlh/GoldPet-Local?node-id=4017-22874&m=dev) | 4017-22874 | P0 |
| SNS 가입 추가 정보 | [링크](https://www.figma.com/design/VIjCdyPTnywcM9HzpoOqlh/GoldPet-Local?node-id=4017-22842&m=dev) | 4017-22842 | P0 |
| 회원가입 완료 | [링크](https://www.figma.com/design/VIjCdyPTnywcM9HzpoOqlh/GoldPet-Local?node-id=4017-22854&m=dev) | 4017-22854 | P0 |

#### 13.1.3 계정 관리 화면

| 화면 | Figma 링크 | Node ID | 우선순위 |
|------|-----------|---------|---------|
| 아이디 찾기 | [링크](https://www.figma.com/design/VIjCdyPTnywcM9HzpoOqlh/GoldPet-Local?node-id=4017-22571&m=dev) | 4017-22571 | P1 |
| 아이디 없음 | [링크](https://www.figma.com/design/VIjCdyPTnywcM9HzpoOqlh/GoldPet-Local?node-id=4017-22595&m=dev) | 4017-22595 | P1 |
| 아이디 찾음 (일반) | [링크](https://www.figma.com/design/VIjCdyPTnywcM9HzpoOqlh/GoldPet-Local?node-id=4017-22608&m=dev) | 4017-22608 | P1 |
| 아이디 찾음 (SNS) | [링크](https://www.figma.com/design/VIjCdyPTnywcM9HzpoOqlh/GoldPet-Local?node-id=4017-22621&m=dev) | 4017-22621 | P1 |
| 비밀번호 변경 | [링크](https://www.figma.com/design/VIjCdyPTnywcM9HzpoOqlh/GoldPet-Local?node-id=4017-22631&m=dev) | 4017-22631 | P1 |
| 비밀번호 변경 폼 | [링크](https://www.figma.com/design/VIjCdyPTnywcM9HzpoOqlh/GoldPet-Local?node-id=4017-22657&m=dev) | 4017-22657 | P1 |
| 휴면계정 안내 | [링크](https://www.figma.com/design/VIjCdyPTnywcM9HzpoOqlh/GoldPet-Local?node-id=4017-22690&m=dev) | 4017-22690 | P2 |
| 탈퇴 계정 안내 | [링크](https://www.figma.com/design/VIjCdyPTnywcM9HzpoOqlh/GoldPet-Local?node-id=4017-22703&m=dev) | 4017-22703 | P2 |
| 비밀번호 변경일 경과 안내 | [링크](https://www.figma.com/design/VIjCdyPTnywcM9HzpoOqlh/GoldPet-Local?node-id=4017-22670&m=dev) | 4017-22670 | P2 |
| 자동로그아웃 안내 | [링크](https://www.figma.com/design/VIjCdyPTnywcM9HzpoOqlh/GoldPet-Local?node-id=4017-22681&m=dev) | 4017-22681 | P2 |

#### 13.1.4 메인 기능 화면

| 화면 | Figma 링크 | Node ID | 우선순위 |
|------|-----------|---------|---------|
| 홈 | [링크](https://www.figma.com/design/VIjCdyPTnywcM9HzpoOqlh/GoldPet-Local?node-id=4017-21066&m=dev) | 4017-21066 | P0 |
| 친구찾기 (기본) | [링크](https://www.figma.com/design/VIjCdyPTnywcM9HzpoOqlh/GoldPet-Local?node-id=4017-23041&m=dev) | 4017-23041 | P0 |
| 친구찾기 (필터) | [링크](https://www.figma.com/design/VIjCdyPTnywcM9HzpoOqlh/GoldPet-Local?node-id=4017-23133&m=dev) | 4017-23133 | P0 |
| 서로좋아해 | [링크](https://www.figma.com/design/VIjCdyPTnywcM9HzpoOqlh/GoldPet-Local?node-id=4017-21374&m=dev) | 4017-21374 | P0 |
| 나를좋아해 | [링크](https://www.figma.com/design/VIjCdyPTnywcM9HzpoOqlh/GoldPet-Local?node-id=4017-21354&m=dev) | 4017-21354 | P0 |
| 채팅 리스트 | [링크](https://www.figma.com/design/VIjCdyPTnywcM9HzpoOqlh/GoldPet-Local?node-id=4017-22019&m=dev) | 4017-22019 | P0 |
| 채팅 없음 | [링크](https://www.figma.com/design/VIjCdyPTnywcM9HzpoOqlh/GoldPet-Local?node-id=4017-22431&m=dev) | 4017-22431 | P0 |
| 대화요청 | [링크](https://www.figma.com/design/VIjCdyPTnywcM9HzpoOqlh/GoldPet-Local?node-id=4017-22084&m=dev) | 4017-22084 | P0 |
| 대화요청 안내 | [링크](https://www.figma.com/design/VIjCdyPTnywcM9HzpoOqlh/GoldPet-Local?node-id=4017-22462&m=dev) | 4017-22462 | P0 |
| 채팅 대화창 | [링크](https://www.figma.com/design/VIjCdyPTnywcM9HzpoOqlh/GoldPet-Local?node-id=4017-22317&m=dev) | 4017-22317 | P0 |
| 산책 (기본) | [링크](https://www.figma.com/design/VIjCdyPTnywcM9HzpoOqlh/GoldPet-Local?node-id=4017-21396&m=dev) | 4017-21396 | P0 |
| 산책 기록 상세 | [링크](https://www.figma.com/design/VIjCdyPTnywcM9HzpoOqlh/GoldPet-Local?node-id=4017-21673&m=dev) | 4017-21673 | P0 |
| 산책 랭킹 | [링크](https://www.figma.com/design/VIjCdyPTnywcM9HzpoOqlh/GoldPet-Local?node-id=4017-21429&m=dev) | 4017-21429 | P1 |
| 산책 시작 | [링크](https://www.figma.com/design/VIjCdyPTnywcM9HzpoOqlh/GoldPet-Local?node-id=4017-21598&m=dev) | 4017-21598 | P0 |
| 커뮤니티 (기본) | [링크](https://www.figma.com/design/VIjCdyPTnywcM9HzpoOqlh/GoldPet-Local?node-id=4017-22890&m=dev) | 4017-22890 | P0 |
| 커뮤니티 (카테고리) | [링크](https://www.figma.com/design/VIjCdyPTnywcM9HzpoOqlh/GoldPet-Local?node-id=4017-22933&m=dev) | 4017-22933 | P0 |
| 글상세/댓글 | [링크](https://www.figma.com/design/VIjCdyPTnywcM9HzpoOqlh/GoldPet-Local?node-id=4017-22968&m=dev) | 4017-22968 | P0 |
| 글작성 | - | - | P0 |
| 프로필 (반려동물) | [링크](https://www.figma.com/design/VIjCdyPTnywcM9HzpoOqlh/GoldPet-Local?node-id=4017-21766&m=dev) | 4017-21766 | P0 |
| 프로필 (반려인) | [링크](https://www.figma.com/design/VIjCdyPTnywcM9HzpoOqlh/GoldPet-Local?node-id=4017-21891&m=dev) | 4017-21891 | P0 |
| 설정 | - | - | P1 |

### 13.2 Figma MCP 통합 워크플로우

하이브리드 앱 개발 시 Figma 디자인을 React 컴포넌트로 변환하는 워크플로우입니다.

#### 13.2.1 필수 작업 흐름

1. **디자인 컨텍스트 가져오기**
   ```typescript
   // Figma MCP를 통해 디자인 구조 가져오기
   // get_design_context(nodeId) 호출
   ```

2. **스크린샷 가져오기**
   ```typescript
   // 시각적 참조를 위한 스크린샷
   // get_screenshot(nodeId) 호출
   ```

3. **에셋 다운로드**
   ```typescript
   // 필요한 이미지/아이콘 다운로드
   // fetch_mcp_resource() 사용
   ```

4. **React 컴포넌트 구현**
   ```typescript
   // Figma 출력(React + Tailwind)을 프로젝트 컨벤션에 맞게 변환
   // - Tailwind → 프로젝트 디자인 시스템 토큰
   // - 기존 컴포넌트 재사용
   // - 라우팅/상태관리 패턴 준수
   ```

5. **검증**
   ```typescript
   // Figma 스크린샷과 1:1 비교
   // 시각적 패리티 확인
   ```

#### 13.2.2 구현 규칙

- **디자인 시스템 준수**
  - Figma의 색상/타이포그래피를 프로젝트 디자인 토큰으로 변환
  - 기존 컴포넌트(Button, Input 등) 재사용
  - Tailwind 유틸리티 클래스는 프로젝트 스타일 시스템으로 대체

- **컴포넌트 구조**
  ```typescript
  // src/features/{feature}/components/{ScreenName}.tsx
  // 예: src/features/onboarding/components/OnboardingScreen1.tsx
  
  export const OnboardingScreen1 = () => {
    // Figma 디자인 기반 구현
    // 네이티브 기능 필요 시 Bridge 사용
    return (
      <div>
        {/* Figma 디자인 구조 반영 */}
      </div>
    );
  };
  ```

- **상태 관리 통합**
  - React Query로 서버 상태 관리
  - Zustand로 클라이언트 상태 관리
  - 네이티브 이벤트는 Bridge 이벤트 리스너로 처리

### 13.3 화면별 구현 우선순위

#### Phase 1: 인증 및 온보딩 (1-2주)
- [ ] Splash 화면
- [ ] Onboarding 1-4 화면
- [ ] 권한 안내 화면
- [ ] 혜택 안내 화면
- [ ] 로그인 화면
- [ ] 회원가입 플로우 (일반/SNS)

#### Phase 2: 메인 기능 (2-3주)
- [ ] 홈 화면
- [ ] 친구찾기 화면 (기본/필터/목록)
- [ ] 채팅 화면 (리스트/대화창)
- [ ] 산책 화면 (기본/시작/상세)

#### Phase 3: 커뮤니티 및 프로필 (1-2주)
- [ ] 커뮤니티 화면 (기본/카테고리/상세/작성)
- [ ] 프로필 화면 (반려동물/반려인)
- [ ] 설정 화면

#### Phase 4: 계정 관리 (1주)
- [ ] 아이디 찾기
- [ ] 비밀번호 변경
- [ ] 기타 계정 관련 화면

### 13.4 Figma 디자인 토큰 추출

#### 13.4.1 색상 시스템

```typescript
// src/theme/colors.ts
// Figma에서 추출한 색상 팔레트
export const colors = {
  primary: {
    main: '#614108',      // 골드펫 브랜드 컬러
    light: '#FFF7E6',      // 배경색
    dark: '#4A3206',
  },
  secondary: {
    // Figma 디자인에서 추출
  },
  // ... 기타 색상
};
```

#### 13.4.2 타이포그래피

```typescript
// src/theme/typography.ts
// Pretendard 폰트 사용 (기존 Flutter와 동일)
export const typography = {
  fontFamily: 'Pretendard, -apple-system, sans-serif',
  h1: {
    fontSize: '32px',
    fontWeight: 700,
    lineHeight: 1.2,
  },
  // ... 기타 타이포그래피 스타일
};
```

#### 13.4.3 스페이싱

```typescript
// src/theme/spacing.ts
// Figma 디자인에서 사용된 간격 값
export const spacing = {
  xs: '4px',
  sm: '8px',
  md: '16px',
  lg: '24px',
  xl: '32px',
  // ... 기타 간격
};
```

### 13.5 컴포넌트 라이브러리 구성

#### 13.5.1 공통 컴포넌트

```typescript
// src/components/common/
├── Button/
│   ├── PrimaryButton.tsx
│   ├── SecondaryButton.tsx
│   └── IconButton.tsx
├── Input/
│   ├── TextInput.tsx
│   ├── PasswordInput.tsx
│   └── SearchInput.tsx
├── Card/
│   ├── PetCard.tsx
│   ├── FriendCard.tsx
│   └── WalkCard.tsx
├── Modal/
│   ├── BaseModal.tsx
│   └── ConfirmModal.tsx
└── ...
```

#### 13.5.2 기능별 컴포넌트

```typescript
// src/features/{feature}/components/
// 예: src/features/onboarding/components/
├── OnboardingCarousel.tsx
├── OnboardingScreen1.tsx
├── OnboardingScreen2.tsx
└── ...
```

### 13.6 반응형 및 접근성

- **모바일 우선**: 모든 화면은 모바일(375px 기준) 우선 설계
- **터치 타겟**: 최소 44x44px 터치 영역 확보
- **접근성**: ARIA 레이블, 키보드 네비게이션 지원
- **다크모드**: 향후 확장 고려 (현재는 라이트 모드만)

### 13.7 디자인 검증 체크리스트

각 화면 구현 완료 시 확인:

- [ ] Figma 디자인과 시각적 일치도 95% 이상
- [ ] 모든 인터랙션 상태 구현 (hover, active, disabled 등)
- [ ] 에러 상태 및 로딩 상태 UI 구현
- [ ] 네이티브 기능 연동 확인 (카메라, 위치 등)
- [ ] 반응형 레이아웃 확인
- [ ] 접근성 검증 (스크린 리더 테스트)

---

## 14. 결론 및 권장사항

### 13.1 권장사항

1. **점진적 전환**: 한 번에 모든 것을 바꾸지 말고 단계적으로 전환
2. **성능 모니터링**: WebView 성능 지속적으로 모니터링
3. **사용자 테스트**: 각 단계마다 사용자 테스트 진행
4. **폴백 전략**: 문제 발생 시 빠르게 롤백할 수 있는 계획 수립
5. **Naver Map 최적화**: 클러스터링 및 지도 타일 캐싱 활용
6. **어뷰징 방지**: 속도 제한 및 지오펜싱 검증 강화

### 13.2 다음 단계

1. 프로토타입 개발 (1주)
   - 기본 Bridge 통신 테스트
   - Naver Map Web API 연동
2. 핵심 기능 검증 (2주)
   - 위치 추적 및 산책 기록
   - 친구 지도 화면
3. 전체 기능 이전 (4-6주)
   - 온보딩 플로우
   - 게이미피케이션 기능
   - AI 비동기 처리
4. 최적화 및 배포 (2주)
   - 성능 최적화
   - 보안 검증
   - 사용자 테스트

### 13.3 주요 고려사항

1. **Naver Map 라이선스**: Web API 사용 시 라이선스 정책 확인
2. **위치 정확도**: GPS 정확도 향상을 위한 네이티브 최적화
3. **배터리 최적화**: 백그라운드 위치 추적 시 배터리 소모 최소화
4. **오프라인 지원**: 지도 타일 및 기본 데이터 캐싱 전략

---

**문서 버전**: v1.1  
**작성일**: 2024  
**최종 수정일**: 2024  
**보강 내용**: GoldPet PRD v2.1 및 보완 문서 반영

