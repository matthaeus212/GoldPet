import 'dart:async';
import 'dart:convert';
import 'dart:io';
import 'package:flutter/services.dart';
import 'package:flutter/material.dart';
import 'package:dio/dio.dart';
import 'package:path_provider/path_provider.dart';
import 'package:share_plus/share_plus.dart';
import 'package:flutter_file_dialog/flutter_file_dialog.dart';
import 'package:url_launcher/url_launcher.dart';
import 'package:webview_flutter/webview_flutter.dart';
import 'bridge_protocol.dart';
import 'bridge_url_guard.dart';
import '../config/environment.dart';
import '../services/location_service.dart';
import '../services/camera_service.dart';
import '../services/push_notification_service.dart';
import '../services/permission_service.dart';
import '../../services/native_auth_service.dart';
import 'package:flutter_secure_storage/flutter_secure_storage.dart';
import '../constants/keys.dart';
import '../constants/app_constants.dart';
import '../api/api_client.dart';
import '../../features/walk/screens/walk_screen.dart';
import '../../features/walk/screens/course_walk_screen.dart';

/// JavaScript와 Flutter 간 통신을 담당하는 브릿지
class NativeBridge {
  final WebViewController webViewController;
  late final LocationService locationService;
  late final CameraService cameraService;
  late final PushNotificationService pushNotificationService;
  late final NativeAuthService nativeAuthService;
  late final PermissionService permissionService;

  /// Overlay color change callback (set by HybridWebView)
  /// Parameters: (topColor, bottomColor) — null means clear overlay
  Function(Color?, Color?)? onOverlayColorChange;

  NativeBridge(this.webViewController) {
    locationService = LocationService();
    cameraService = CameraService();
    pushNotificationService = PushNotificationService();
    nativeAuthService = NativeAuthService();
    permissionService = PermissionService();

    // Bridge 연결
    locationService.bridge = this;
    pushNotificationService.bridge = this;
  }

  /// React로 이벤트 전송
  void sendEventToReact(String eventName, Map<String, dynamic> data) {
    final message = jsonEncode({'type': eventName, 'data': data});
    final targetOrigin = currentEnv.appBaseUrl;
    webViewController.runJavaScript("window.postMessage($message, '$targetOrigin')");
  }

  /// 메서드 호출 처리
  Future<dynamic> handleMethodCall(
    String method,
    Map<String, dynamic>? params,
  ) async {
    try {
      switch (method) {
        // 로그인
        case BridgeProtocol.login:
          final provider = params?['provider'] as String?;
          String? token;
          Map<String, dynamic>? backendResult;

          if (provider == 'kakao') {
            token = await nativeAuthService.loginWithKakao();
          } else if (provider == 'naver') {
            token = await nativeAuthService.loginWithNaver();
          } else if (provider == 'google') {
            token = await nativeAuthService.loginWithGoogle();
          } else if (provider == 'apple') {
            token = await nativeAuthService.loginWithApple();
          }

          if (token != null && provider != null) {
            backendResult = await nativeAuthService.verifyTokenWithBackend(
              provider,
              token,
            );
          }

          if (backendResult != null) {
            final newResult = Map<String, dynamic>.from(backendResult);

            // Store tokens in Flutter secure storage for native API calls (e.g. walk save)
            final accessToken = newResult['accessToken'] as String?;
            final refreshToken = newResult['refreshToken'] as String?;
            if (accessToken != null && refreshToken != null) {
              await ApiClient().setToken(accessToken, refreshToken);
            }

            // Determine type based on response content
            // If backend returns 'user', we need to check if it's a complete user or a new incomplete user
            // The user said: "2. If not in DB, save to DB -> 2-1. Move to Terms"
            // So even if we have a user, if they lack required info (like phoneNumber), it's a signup flow.
            if (newResult.containsKey('user')) {
              final user = newResult['user'] as Map<String, dynamic>;
              // 가입 완료 판정 = signupCompleted(온보딩 완료 단일 기준, PII 필드와 무관).
              // 신버전 서버는 명시 필드를 내려주고, 구버전 서버/캐시/스테이징 조합에선
              // 기존 name 기준으로 폴백해 이미 완료한 사용자가 false로 회귀하지 않게 한다.
              final hasCompletedSignup = user['signupCompleted'] as bool? ??
                  ((user['name'] as String?)?.isNotEmpty == true);

              if (hasCompletedSignup) {
                newResult['type'] = 'LOGIN_SUCCESS';
              } else {
                newResult['type'] = 'SIGNUP_REQUIRED';
                newResult['provider'] = provider;
              }
            } else {
              // Should not happen if backend always returns user on save, but fallback to signup
              newResult['type'] = 'SIGNUP_REQUIRED';
              newResult['provider'] = provider;
            }
            return newResult;
          }

          return {'success': false, 'message': 'Login failed'};

        // Auth 토큰 동기화 (React → Flutter secure storage)
        case BridgeProtocol.syncAuthToken:
          final accessToken = params?['accessToken'] as String?;
          final refreshToken = params?['refreshToken'] as String?;
          if (accessToken != null) {
            await ApiClient().setToken(accessToken, refreshToken ?? '');
            debugPrint('[NativeBridge] Auth tokens synced to secure storage');
          }
          return {'success': true};

        // FCM 토큰 등록
        case BridgeProtocol.registerFcmToken:
          final accessToken = params?['accessToken'] as String?;
          if (accessToken != null) {
            // Get stable deviceId before registering
            final deviceId = await pushNotificationService.getOrCreateDeviceId();
            pushNotificationService.registerTokenWithBackend(accessToken);
            return {'success': true, 'deviceId': deviceId};
          }
          return {'success': true};

        // 푸시 알림
        case BridgeProtocol.requestPushPermission:
          return await pushNotificationService.requestPermission();

        case BridgeProtocol.getPushToken:
          return await pushNotificationService.getToken();

        case BridgeProtocol.subscribeToTopic:
          if (params == null || params['topic'] == null) {
            throw Exception('Topic parameter is required');
          }
          await pushNotificationService.subscribeToTopic(
            params['topic'] as String,
          );
          return {'success': true};

        // 전체 권한 일괄 요청
        case BridgeProtocol.requestAllPermissions:
          final result = await permissionService.requestAllPermissions();
          // 알림 권한 부여 후 FCM 리스너 설정
          await pushNotificationService.setupAfterPermission();
          return result.toJson();

        // 위치
        case BridgeProtocol.requestLocationPermission:
          return await locationService.requestPermission();

        case BridgeProtocol.getCurrentLocation:
          return await locationService.getCurrentLocation();

        case BridgeProtocol.startLocationTracking:
          final interval = params?['interval'] as int? ?? 1000;
          await locationService.startTracking(interval: interval);
          return {'success': true};

        case BridgeProtocol.stopLocationTracking:
          await locationService.stopTracking();
          return {'success': true};

        // 카메라/사진
        case BridgeProtocol.requestCameraPermission:
          return await cameraService.requestCameraPermission();

        case BridgeProtocol.requestPhotoPermission:
          return await cameraService.requestPhotoPermission();

        case BridgeProtocol.openCamera:
          return await cameraService.pickImage(source: 'camera');

        case BridgeProtocol.openPhotoLibrary:
          return await cameraService.pickImage(source: 'gallery');

        case BridgeProtocol.pickImage:
          final source = params?['source'] as String? ?? 'gallery';
          return await cameraService.pickImage(source: source);

        // 앱 정보
        // 앱 정보
        case BridgeProtocol.getAppVersion:
          return {'version': '0.1.0'};

        case BridgeProtocol.getDeviceInfo:
          return {
            'platform': 'mobile',
            'os': 'android/ios', // 실제로는 Platform.isAndroid 등으로 확인
          };

        // 파일 다운로드
        case BridgeProtocol.downloadFile:
          final url = params?['url'] as String?;
          if (url == null) throw Exception('URL parameter is required');

          // SEC-007: 브리지 인자는 신뢰 경계 밖이다.
          //  - url: 우리 인프라의 https 자원만 허용(임의 호스트에서 내려받아 저장 금지)
          //  - fileName: 채팅 첨부의 originalFileName 은 업로더가 정하는 값이라 `../../` 로
          //    수신자 기기의 샌드박스 밖에 쓸 수 있었다 → basename 으로 강제한다.
          final env = EnvironmentConfig.fromEnv();
          if (!BridgeUrlGuard.isAllowedDownloadUrl(url, env)) {
            debugPrint('[Bridge] downloadFile rejected (host not allowed): $url');
            throw Exception('Download from this URL is not allowed');
          }
          final fileName = BridgeUrlGuard.safeFileName(
            params?['fileName'] as String? ?? Uri.tryParse(url)?.pathSegments.lastOrNull,
          );

          try {
            final tempDir = await getTemporaryDirectory();
            final tempPath = '${tempDir.path}/$fileName';
            await Dio().download(url, tempPath);
            debugPrint('[Bridge] File downloaded to: $tempPath');

            if (Platform.isAndroid) {
              // Android: Downloads 폴더에 복사
              final downloadsDir = Directory('/storage/emulated/0/Download');
              if (await downloadsDir.exists()) {
                final destPath = '${downloadsDir.path}/$fileName';
                await File(tempPath).copy(destPath);
                debugPrint('[Bridge] File saved to Downloads: $destPath');

                sendEventToReact('fileDownloaded', {
                  'fileName': fileName,
                  'path': destPath,
                  'message': '다운로드 폴더에 저장되었습니다',
                });
                return {'success': true, 'path': destPath};
              }
            }

            // iOS: 네이티브 저장 다이얼로그
            if (Platform.isIOS) {
              final savedPath = await FlutterFileDialog.saveFile(
                params: SaveFileDialogParams(sourceFilePath: tempPath),
              );
              if (savedPath != null) {
                sendEventToReact('fileDownloaded', {
                  'fileName': fileName,
                  'path': savedPath,
                  'message': '파일이 저장되었습니다',
                });
              }
              return {'success': true, 'path': savedPath ?? tempPath};
            }

            // Fallback: 공유 시트
            await SharePlus.instance.share(
              ShareParams(files: [XFile(tempPath)]),
            );
            return {'success': true, 'path': tempPath};
          } catch (e) {
            debugPrint('[Bridge] Download failed: $e');
            await launchUrl(Uri.parse(url), mode: LaunchMode.externalApplication);
            return {'success': true, 'fallback': true};
          }

        // 외부 URL 열기
        case BridgeProtocol.openExternalUrl:
          final url = params?['url'] as String?;
          if (url == null) throw Exception('URL parameter is required');
          // SEC-007: 커스텀 스킴(tel:, 앱 딥링크 등)을 그대로 launch 하면 페이지가 임의의
          // 앱 동작을 트리거할 수 있다. 웹 링크만 허용한다.
          if (!BridgeUrlGuard.isAllowedExternalUrl(url)) {
            debugPrint('[Bridge] openExternalUrl rejected (scheme not allowed): $url');
            throw Exception('This URL scheme is not allowed');
          }
          await launchUrl(Uri.parse(url), mode: LaunchMode.externalApplication);
          return {'success': true};

        // 산책 (Walk Feature)
        case BridgeProtocol.startWalk:
          // Singleton guard: don't push if WalkScreen already on stack
          if (WalkScreen.isOnStack) {
            return {'success': true, 'alreadyActive': true};
          }
          final isPublic = params?['isPublic'] as bool? ?? true;
          // CRITICAL: JS numbers are doubles, not ints. Must use (e as num).toInt()
          // Same pattern as startCourseWalk where (p[1] as num).toDouble() is used for coordinates
          final petIds = (params?['petIds'] as List?)
              ?.map((e) => (e as num).toInt())
              .toList();
          // Don't await - return immediately so bridge doesn't timeout
          rootNavigatorKey.currentState?.push(
            MaterialPageRoute(builder: (_) => WalkScreen(isPublic: isPublic, petIds: petIds)),
          );
          return {'success': true};

        // 코스 따라가기 (Course Walk Feature)
        case BridgeProtocol.startCourseWalk:
          // Singleton guard
          if (CourseWalkScreen.isOnStack || WalkScreen.isOnStack) {
            return {'success': true, 'alreadyActive': true};
          }
          final courseId = params?['courseId'] as int?;
          final pathRaw = params?['path'] as List?;
          final spotsRaw = params?['spots'] as List?;
          final courseTitle = params?['title'] as String?;

          if (courseId == null || pathRaw == null || pathRaw.isEmpty) {
            throw Exception('courseId and path are required');
          }

          final coursePath = pathRaw
              .map((p) => [((p as List)[0] as num).toDouble(), (p[1] as num).toDouble()])
              .toList();
          final courseSpots = (spotsRaw ?? [])
              .map((s) => CourseSpotData.fromJson(s as Map<String, dynamic>))
              .toList();

          rootNavigatorKey.currentState?.push(
            MaterialPageRoute(
              builder: (_) => CourseWalkScreen(
                courseId: courseId,
                coursePath: coursePath,
                courseSpots: courseSpots,
                courseTitle: courseTitle,
              ),
            ),
          );
          return {'success': true};

        // 로그아웃 시 Flutter 토큰 삭제
        case BridgeProtocol.clearAuthToken:
          debugPrint('[NativeBridge] clearAuthToken started');
          // 현재 토큰 캡처 후 디바이스 비활성화 (네트워크 호출)
          const storage = FlutterSecureStorage(
            iOptions: IOSOptions(accessibility: KeychainAccessibility.first_unlock),
          );
          final tokenBeforeDeactivation = await storage.read(key: StorageKeys.accessToken);
          if (tokenBeforeDeactivation != null) {
            await pushNotificationService.deactivateDevice(tokenBeforeDeactivation);
          }
          // Race condition guard: deactivateDevice 대기 중 새 로그인이 발생했는지 확인
          // 새 토큰이 저장되었으면 clearToken을 건너뛰어 새 세션을 보호
          final tokenAfterDeactivation = await storage.read(key: StorageKeys.accessToken);
          if (tokenAfterDeactivation == tokenBeforeDeactivation) {
            await ApiClient().clearToken();
            debugPrint('[NativeBridge] Auth tokens cleared + device deactivated');
          } else {
            debugPrint('[NativeBridge] Skipping clearToken - new login detected during deactivation');
          }
          pushNotificationService.setCurrentChatRoom(null);
          return {'success': true};

        // 현재 채팅방 설정 (푸시 알림 필터링용)
        case BridgeProtocol.setCurrentChatRoom:
          final chatRoomId = params?['chatRoomId'] as String?;
          pushNotificationService.setCurrentChatRoom(chatRoomId);
          return {'success': true};

        // 오버레이 색상 제어 (SafeArea 동기화)
        case BridgeProtocol.setOverlayColor:
          final colorHex = params?['color'] as String? ?? '#000000';
          final colorValue = int.parse(colorHex.replaceFirst('#', ''), radix: 16) | 0xFF000000;
          final bottomColorHex = params?['bottomColor'] as String?;
          final bottomColor = bottomColorHex != null
              ? Color(int.parse(bottomColorHex.replaceFirst('#', ''), radix: 16) | 0xFF000000)
              : Color(colorValue);
          onOverlayColorChange?.call(Color(colorValue), bottomColor);
          return {'success': true};

        case BridgeProtocol.clearOverlayColor:
          onOverlayColorChange?.call(null, null);
          return {'success': true};

        // 콘텐츠 공유 (OS 네이티브 공유 시트)
        case BridgeProtocol.shareContent:
          final imageBase64 = params?['imageBase64'] as String?;
          final text = params?['text'] as String? ?? '';
          final subject = params?['subject'] as String?;
          final fileName = params?['fileName'] as String? ??
              'goldpet_share_${DateTime.now().millisecondsSinceEpoch}.jpg';

          final xfiles = <XFile>[];
          if (imageBase64 != null && imageBase64.isNotEmpty) {
            // data URI prefix 제거 ("data:image/jpeg;base64,..." → raw base64)
            final rawBase64 = imageBase64.contains(',')
                ? imageBase64.split(',').last
                : imageBase64;

            // payload 크기 제한: base64 문자열 길이 > ~2MB ≈ 디코딩 후 1.5MB
            if (rawBase64.length > 2 * 1024 * 1024) {
              throw PlatformException(
                code: 'SHARE_INVALID_PARAMS',
                message: 'Image payload exceeds 1.5MB limit',
              );
            }

            final bytes = base64Decode(rawBase64);
            final tmpDir = await getTemporaryDirectory();
            final file = File('${tmpDir.path}/$fileName');
            await file.writeAsBytes(bytes);
            xfiles.add(XFile(file.path, mimeType: 'image/jpeg'));

            // 5분 후 임시 파일 자동 삭제
            Timer(const Duration(minutes: 5), () {
              file.delete().catchError((_) => file);
            });
          }

          if (xfiles.isEmpty && text.isEmpty) {
            throw PlatformException(
              code: 'SHARE_INVALID_PARAMS',
              message: 'Either imageBase64 or text is required',
            );
          }

          final shareParams = ShareParams(
            text: text.isNotEmpty ? text : null,
            subject: subject,
            files: xfiles.isEmpty ? null : xfiles,
            // iPad popover: 화면 우상단 기본값
            sharePositionOrigin: const Rect.fromLTWH(0, 0, 100, 40),
          );
          final shareResult = await SharePlus.instance.share(shareParams);
          // ShareResultStatus.success → 'shared' (plan 스펙 매핑)
          final statusStr = shareResult.status == ShareResultStatus.success
              ? 'shared'
              : shareResult.status.name;
          return {'status': statusStr, 'channel': shareResult.raw};

        default:
          throw PlatformException(
            code: 'METHOD_NOT_FOUND',
            message: 'Method $method not found',
          );
      }
    } catch (e) {
      if (e is PlatformException) rethrow;
      throw PlatformException(
        code: _getErrorCode(method, e),
        message: e.toString(),
        details: '${e.runtimeType}: ${e.toString()}',
      );
    }
  }

  String _getErrorCode(String method, dynamic error) {
    if (method.contains('location') ||
        method.contains('Location') ||
        method.contains('gps')) {
      return 'LOCATION_ERROR';
    }
    if (method.contains('camera') ||
        method.contains('Camera') ||
        method.contains('photo')) {
      return 'CAMERA_ERROR';
    }
    final errorStr = error.toString();
    if (errorStr.contains('permission') || errorStr.contains('Permission')) {
      return 'PERMISSION_ERROR';
    }
    if (errorStr.contains('network') ||
        errorStr.contains('timeout') ||
        errorStr.contains('SocketException')) {
      return 'NETWORK_ERROR';
    }
    return 'METHOD_ERROR';
  }
}
