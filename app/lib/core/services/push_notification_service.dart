import 'dart:async';
import 'dart:convert';
import 'dart:io';
import 'package:flutter/foundation.dart';
import 'package:http/http.dart' as http;
import 'package:shared_preferences/shared_preferences.dart';
import 'package:device_info_plus/device_info_plus.dart';
import 'package:package_info_plus/package_info_plus.dart';
import 'package:uuid/uuid.dart';
import 'package:flutter_secure_storage/flutter_secure_storage.dart';
import '../bridge/native_bridge.dart';
import '../config/environment.dart';
import '../constants/keys.dart';

import 'package:firebase_messaging/firebase_messaging.dart';
import 'package:flutter_local_notifications/flutter_local_notifications.dart';

/// Android 알림 채널
const AndroidNotificationChannel _androidChannel = AndroidNotificationChannel(
  'goldpet_notifications',
  'GoldPet 알림',
  description: 'GoldPet 앱 알림',
  importance: Importance.high,
);

/// 백그라운드 메시지 핸들러 (top-level function 필수)
@pragma('vm:entry-point')
Future<void> firebaseMessagingBackgroundHandler(RemoteMessage message) async {
  debugPrint('[FCM] Background message: ${message.messageId}');
}

/// 푸시 알림 서비스 (싱글턴)
class PushNotificationService {
  static final PushNotificationService _instance = PushNotificationService._internal();
  factory PushNotificationService() => _instance;
  PushNotificationService._internal();

  NativeBridge? bridge;
  final FlutterLocalNotificationsPlugin _localNotifications =
      FlutterLocalNotificationsPlugin();

  RemoteMessage? _pendingInitialMessage;
  String? _currentChatRoomId; // 현재 보고 있는 채팅방 ID (알림 필터링용)

  /// Inject a fake _registerDevice for unit tests to avoid real HTTP calls.
  @visibleForTesting
  Future<void> Function(String fcmToken, String accessToken)? testRegisterDeviceOverride;

  /// WebView 페이지 로드 완료 후 호출하여 pending 알림 이벤트 전달
  /// Cold boot 시 bridge setter에서 즉시 보내면 React 앱이 아직 로드되지 않아 이벤트 소실됨
  void deliverPendingMessage() {
    if (bridge != null && _pendingInitialMessage != null) {
      final msg = _pendingInitialMessage!;
      _pendingInitialMessage = null;
      // React 앱 초기화 시간 확보 후 전달
      Future.delayed(const Duration(milliseconds: 500), () {
        bridge?.sendEventToReact('pushNotificationClicked', {
          'title': msg.notification?.title,
          'body': msg.notification?.body,
          'data': msg.data,
        });
      });
    }
  }

  /// 초기화 (앱 시작 시 호출)
  Future<void> initialize() async {
    // 알림 채널 생성 (Android)
    await _localNotifications
        .resolvePlatformSpecificImplementation<
            AndroidFlutterLocalNotificationsPlugin>()
        ?.createNotificationChannel(_androidChannel);

    // flutter_local_notifications 초기화
    const androidSettings = AndroidInitializationSettings('@mipmap/ic_launcher');
    const iosSettings = DarwinInitializationSettings(
      requestAlertPermission: false,
      requestBadgePermission: false,
      requestSoundPermission: false,
    );
    const initSettings = InitializationSettings(
      android: androidSettings,
      iOS: iosSettings,
    );
    await _localNotifications.initialize(
      settings: initSettings,
      onDidReceiveNotificationResponse: (details) {
        // 로컬 알림 탭 시 React로 이벤트 전달
        if (details.payload != null) {
          try {
            final data = jsonDecode(details.payload!);
            bridge?.sendEventToReact('pushNotificationClicked', {
              'data': data,
            });
          } catch (e) {
            debugPrint('[FCM] Failed to parse notification payload: $e');
          }
        }
      },
    );

    // 알림 권한은 PermissionService.requestAllPermissions()에서 일괄 요청
    // 리스너/초기 메시지는 권한 부여 후 setupAfterPermission()에서 설정
  }

  /// 알림 권한 부여 후 리스너 및 초기 메시지 설정
  Future<void> setupAfterPermission() async {
    // APNs 자동 등록 재활성화 (iOS)
    await FirebaseMessaging.instance.setAutoInitEnabled(true);
    _setupListeners();

    // Cold start: 앱 종료 상태에서 알림 클릭으로 열린 경우
    final initialMessage = await FirebaseMessaging.instance.getInitialMessage();
    if (initialMessage != null) {
      _pendingInitialMessage = initialMessage;
    }
  }

  void _setupListeners() {
    // 포그라운드 메시지 수신
    FirebaseMessaging.onMessage.listen((RemoteMessage message) {
      debugPrint('[FCM] Foreground message: ${message.messageId}');
      debugPrint('[FCM] Data: ${message.data}');

      final notification = message.notification;
      if (notification != null) {
        debugPrint('[FCM] Notification: ${notification.title} - ${notification.body}');

        // 현재 보고 있는 채팅방의 메시지면 알림 스킵
        final messageType = message.data['type']?.toString();
        final msgTargetType = message.data['targetType']?.toString();
        final messageChatRoomId = (messageType == 'MESSAGE' || msgTargetType == 'CHAT_ROOM')
            ? message.data['targetId']?.toString()
            : null;
        if (messageChatRoomId != null && messageChatRoomId == _currentChatRoomId) {
          debugPrint('[FCM] Skipping notification - user is viewing chat room $messageChatRoomId');

          // React에는 이벤트 전달 (UI 업데이트용)
          bridge?.sendEventToReact('pushNotificationReceived', {
            'title': notification.title,
            'body': notification.body,
            'data': message.data,
          });
          return;
        }

        // 포그라운드에서 로컬 알림으로 표시
        _localNotifications.show(
          id: notification.hashCode,
          title: notification.title,
          body: notification.body,
          notificationDetails: NotificationDetails(
            android: AndroidNotificationDetails(
              _androidChannel.id,
              _androidChannel.name,
              channelDescription: _androidChannel.description,
              importance: Importance.high,
              priority: Priority.high,
              icon: '@mipmap/ic_launcher',
            ),
            iOS: const DarwinNotificationDetails(
              presentAlert: true,
              presentSound: true,
              presentBadge: true,
            ),
          ),
          payload: jsonEncode(message.data),
        );

        // React에도 이벤트 전달
        bridge?.sendEventToReact('pushNotificationReceived', {
          'title': notification.title,
          'body': notification.body,
          'data': message.data,
        });
      }
    });

    // 앱이 백그라운드에서 열릴 때 (알림 클릭 등)
    FirebaseMessaging.onMessageOpenedApp.listen((RemoteMessage message) async {
      debugPrint('[FCM] Notification opened app: ${message.messageId}');
      // WebView resume 대기 (background → foreground 전환 시)
      await Future.delayed(const Duration(milliseconds: 500));
      bridge?.sendEventToReact('pushNotificationClicked', {
        'title': message.notification?.title,
        'body': message.notification?.body,
        'data': message.data,
      });
    });
  }

  /// 푸시 알림 권한 요청
  Future<bool> requestPermission() async {
    final settings = await FirebaseMessaging.instance.requestPermission(
      alert: true,
      badge: true,
      sound: true,
    );
    debugPrint('[FCM] Permission: ${settings.authorizationStatus}');
    return settings.authorizationStatus == AuthorizationStatus.authorized;
  }

  /// FCM 토큰 가져오기 (iOS에서는 APNs 토큰을 먼저 기다림)
  Future<String?> getToken() async {
    try {
      // iOS에서는 APNs 토큰이 준비될 때까지 대기
      if (Platform.isIOS) {
        String? apnsToken;
        for (int i = 0; i < 10; i++) {
          apnsToken = await FirebaseMessaging.instance.getAPNSToken();
          if (apnsToken != null) {
            debugPrint('[FCM] APNs token ready');
            break;
          }
          debugPrint('[FCM] Waiting for APNs token... (${i + 1}/10)');
          await Future.delayed(const Duration(seconds: 2));
        }
        if (apnsToken == null) {
          debugPrint('[FCM] APNs token not available after retries');
          return null;
        }
      }

      final token = await FirebaseMessaging.instance.getToken();
      debugPrint('[FCM] Token: $token');
      return token;
    } catch (e) {
      debugPrint('[FCM] Failed to get token: $e');
      return null;
    }
  }

  /// 안정적인 deviceId를 Keychain(iOS)/EncryptedPrefs(Android)에서 가져오거나 생성
  /// iOS에서 SharedPreferences는 앱 재설치 시 초기화되므로 flutter_secure_storage 사용
  Future<String> getOrCreateDeviceId() async {
    const storage = FlutterSecureStorage(
      iOptions: IOSOptions(accessibility: KeychainAccessibility.first_unlock),
    );
    const key = 'device_id';
    String? deviceId = await storage.read(key: key);

    // SharedPreferences에서 마이그레이션 (기존 사용자 호환)
    if (deviceId == null) {
      final prefs = await SharedPreferences.getInstance();
      deviceId = prefs.getString(key);
      if (deviceId != null) {
        await storage.write(key: key, value: deviceId);
        await prefs.remove(key);
        debugPrint('[FCM] Migrated deviceId to secure storage: $deviceId');
      }
    }

    if (deviceId == null) {
      deviceId = const Uuid().v4();
      await storage.write(key: key, value: deviceId);
      debugPrint('[FCM] Generated new deviceId: $deviceId');
    } else {
      debugPrint('[FCM] Using existing deviceId: $deviceId');
    }
    return deviceId;
  }

  /// 디바이스 모델명 가져오기
  Future<String> _getDeviceName() async {
    try {
      final deviceInfo = DeviceInfoPlugin();
      if (Platform.isAndroid) {
        final androidInfo = await deviceInfo.androidInfo;
        return androidInfo.model;
      } else if (Platform.isIOS) {
        final iosInfo = await deviceInfo.iosInfo;
        return iosInfo.utsname.machine;
      }
    } catch (e) {
      debugPrint('[FCM] Failed to get device name: $e');
    }
    return Platform.isAndroid ? 'Android' : 'iOS';
  }

  /// 앱 버전 가져오기
  Future<String> _getAppVersion() async {
    try {
      final packageInfo = await PackageInfo.fromPlatform();
      return '${packageInfo.version}+${packageInfo.buildNumber}';
    } catch (e) {
      debugPrint('[FCM] Failed to get app version: $e');
      return '0.1.0';
    }
  }

  /// Register FCM token with backend (new device registration endpoint)
  Future<void> registerTokenWithBackend(String accessToken) async {
    try {
      final token = await getToken();
      if (token == null) {
        debugPrint('[FCM] Token is null, skipping registration');
        return;
      }

      await _registerDevice(token, accessToken);

      // Listen for token refresh — read fresh JWT from storage, not the
      // closure-captured accessToken which may be stale after JWT rotation.
      FirebaseMessaging.instance.onTokenRefresh.listen((newToken) {
        debugPrint('[FCM] Token refreshed, re-registering...');
        handleFcmTokenRefresh(newToken);
      });
    } catch (e) {
      debugPrint('[FCM] Failed to register token: $e');
    }
  }

  /// Reads the current JWT from secure storage and re-registers the device
  /// with the new FCM token. Extracted so it can be unit-tested without
  /// touching real Firebase streams.
  @visibleForTesting
  Future<void> handleFcmTokenRefresh(String newFcmToken) async {
    const storage = FlutterSecureStorage(
      iOptions: IOSOptions(accessibility: KeychainAccessibility.first_unlock),
    );
    final currentToken = await storage.read(key: StorageKeys.accessToken);
    if (currentToken == null) {
      debugPrint('[FCM] No access token in storage — skipping FCM re-registration');
      return;
    }
    if (testRegisterDeviceOverride != null) {
      await testRegisterDeviceOverride!(newFcmToken, currentToken);
    } else {
      _registerDevice(newFcmToken, currentToken);
    }
  }

  Future<void> _registerDevice(String fcmToken, String accessToken) async {
    try {
      final deviceId = await getOrCreateDeviceId();
      final deviceName = await _getDeviceName();
      final appVersion = await _getAppVersion();
      final deviceType = Platform.isAndroid ? 'ANDROID' : 'IOS';

      final currentEnv = EnvironmentConfig.fromEnv();

      // Try new device registration endpoint first
      final uri = Uri.parse('${currentEnv.apiBaseUrl}/api/v1/users/me/devices');
      final response = await http.put(
        uri,
        headers: {
          'Content-Type': 'application/json',
          'Authorization': 'Bearer $accessToken',
        },
        body: jsonEncode({
          'deviceId': deviceId,
          'fcmToken': fcmToken,
          'deviceType': deviceType,
          'deviceName': deviceName,
          'appVersion': appVersion,
        }),
      );

      if (response.statusCode == 200 || response.statusCode == 201) {
        debugPrint('[FCM] Device registered with backend (deviceId: $deviceId)');
        // Pass deviceId to React side via bridge
        bridge?.sendEventToReact('deviceRegistered', {
          'deviceId': deviceId,
          'fcmToken': fcmToken,
        });
      } else {
        debugPrint('[FCM] Device registration failed: ${response.statusCode}, falling back to legacy endpoint');
        await _sendTokenToBackendLegacy(fcmToken, accessToken);
      }
    } catch (e) {
      debugPrint('[FCM] Error registering device: $e');
      // Fallback to legacy endpoint
      try {
        await _sendTokenToBackendLegacy(fcmToken, accessToken);
      } catch (fallbackErr) {
        debugPrint('[FCM] Legacy fallback also failed: $fallbackErr');
      }
    }
  }

  /// Legacy FCM token endpoint (backward compatibility)
  Future<void> _sendTokenToBackendLegacy(String fcmToken, String accessToken) async {
    final currentEnv = EnvironmentConfig.fromEnv();
    final uri = Uri.parse('${currentEnv.apiBaseUrl}/api/v1/users/me/fcm-token');
    final response = await http.put(
      uri,
      headers: {
        'Content-Type': 'application/json',
        'Authorization': 'Bearer $accessToken',
      },
      body: jsonEncode({'fcmToken': fcmToken}),
    );

    if (response.statusCode == 200) {
      debugPrint('[FCM] Token registered with backend (legacy endpoint)');
    } else {
      debugPrint('[FCM] Legacy token registration failed: ${response.statusCode}');
    }
  }

  /// 토픽 구독
  Future<void> subscribeToTopic(String topic) async {
    await FirebaseMessaging.instance.subscribeToTopic(topic);
  }

  /// 로그아웃 시 현재 디바이스 비활성화
  Future<void> deactivateDevice(String accessToken) async {
    try {
      final deviceId = await getOrCreateDeviceId();
      final currentEnv = EnvironmentConfig.fromEnv();
      final uri = Uri.parse(
          '${currentEnv.apiBaseUrl}/api/v1/users/me/devices/$deviceId');
      final response = await http.delete(
        uri,
        headers: {
          'Authorization': 'Bearer $accessToken',
        },
      );
      if (response.statusCode == 200 || response.statusCode == 204) {
        debugPrint('[FCM] Device deactivated on logout (deviceId: $deviceId)');
      } else {
        debugPrint('[FCM] Device deactivation failed: ${response.statusCode}');
      }
    } catch (e) {
      debugPrint('[FCM] Error deactivating device: $e');
    }
  }

  /// 현재 채팅방 설정 (푸시 알림 필터링용)
  void setCurrentChatRoom(String? chatRoomId) {
    _currentChatRoomId = chatRoomId;
    debugPrint('[FCM] Current chat room set to: $chatRoomId');
  }
}
