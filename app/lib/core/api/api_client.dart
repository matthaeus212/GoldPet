import 'dart:async';
import 'dart:io';
import 'package:dio/dio.dart';
import 'package:dio/io.dart';
import 'package:flutter/foundation.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:flutter_secure_storage/flutter_secure_storage.dart';
import '../constants/app_constants.dart';
import '../constants/keys.dart';
import '../services/push_notification_service.dart';
import '../../features/walk/data/walk_local_storage.dart';
import '../../features/walk/screens/walk_screen.dart';
import '../../features/walk/screens/course_walk_screen.dart';

final apiClientProvider = Provider<ApiClient>((ref) => ApiClient());

class ApiClient {
  static final ApiClient _instance = ApiClient._internal();
  factory ApiClient() => _instance;

  late final Dio _dio;
  final _storage = const FlutterSecureStorage(
    iOptions: IOSOptions(accessibility: KeychainAccessibility.first_unlock),
  );

  /// Auth expiry dedup guard -- prevents multiple dialogs on concurrent 401s
  static bool _isAuthExpired = false;

  /// Completer-based refresh mutex -- prevents concurrent token refresh races.
  /// If not null, a refresh is in progress; callers wait for it to complete.
  static Completer<void>? _refreshLock;

  /// Prevents concurrent _recoverPendingWalk executions
  /// (setToken is called multiple times during login: login handler + syncAuthToken)
  static bool _isRecoveringWalk = false;

  /// Callback invoked once when auth expires (refresh fails).
  /// Set by HybridWebView to show dialog + redirect to login.
  static VoidCallback? onAuthExpired;

  /// Callback invoked right after Flutter rotates the access/refresh token
  /// on its own initiative — either the 401 interceptor's reactive refresh
  /// or refreshTokenIfNeeded()'s proactive pre-walk refresh — i.e. any time
  /// the session is refreshed without React initiating it. Set by
  /// HybridWebView to push the new tokens to React via the 'tokenSync'
  /// event, so React's copy never goes stale and later retries a refresh
  /// with a dead token (server reuse detection → forced logout).
  /// NOT invoked for setToken() calls originating from the 'syncAuthToken'
  /// bridge call (React → Flutter) to avoid an echo loop.
  static void Function(String accessToken, String refreshToken)?
      onTokenRotatedNatively;

  ApiClient._internal() {
    _dio = Dio(
      BaseOptions(
        baseUrl: ApiConstants.baseUrl,
        connectTimeout: ApiConstants.connectTimeout,
        receiveTimeout: ApiConstants.receiveTimeout,
        headers: {
          'Content-Type': 'application/json',
          'Accept': 'application/json',
        },
      ),
    );

    _setupCertificatePinning(_dio);

    _dio.interceptors.add(
      InterceptorsWrapper(
        onRequest: (options, handler) async {
          final token = await _storage.read(key: StorageKeys.accessToken);
          if (token != null) {
            options.headers['Authorization'] = 'Bearer $token';
          }
          final deviceId = await PushNotificationService().getOrCreateDeviceId();
          options.headers['X-Device-Id'] = deviceId;
          return handler.next(options);
        },
        onError: (error, handler) async {
          if (error.response?.statusCode == 401) {
            final refreshToken = await _storage.read(key: StorageKeys.refreshToken);
            // Refresh 시도가 401(재사용 감지/유효하지 않은 refresh token)로 거부됐는지 여부.
            // true인 경우에만 죽은 토큰을 폐기한다 — 네트워크 오류 등 일시적 실패에서는
            // 세션(저장된 토큰)을 보존해 오프라인 순간에 로그아웃되지 않도록 한다.
            var refreshRejectedAsInvalid = false;
            if (refreshToken != null && refreshToken.isNotEmpty) {
              try {
                if (_refreshLock != null) {
                  // Another refresh is in progress — wait for it, then retry with stored token
                  debugPrint('[AuthExpiry] Waiting for in-progress token refresh...');
                  await _refreshLock!.future.timeout(const Duration(seconds: 10));
                  final newToken = await _storage.read(key: StorageKeys.accessToken);
                  if (newToken != null) {
                    final opts = error.requestOptions;
                    opts.headers['Authorization'] = 'Bearer $newToken';
                    final retryDio = Dio(BaseOptions(
                      baseUrl: ApiConstants.baseUrl,
                      headers: {'Content-Type': 'application/json'},
                    ));
                    try {
                      final retryResponse = await retryDio.fetch(opts);
                      return handler.resolve(retryResponse);
                    } catch (_) {
                      return handler.next(error);
                    }
                  }
                } else {
                  // No refresh in progress — acquire lock and refresh
                  _refreshLock = Completer<void>();
                  try {
                    // Use a separate Dio instance to avoid interceptor loops
                    final deviceId = await PushNotificationService().getOrCreateDeviceId();
                    final refreshDio = Dio(BaseOptions(
                      baseUrl: ApiConstants.baseUrl,
                      headers: {
                        'Content-Type': 'application/json',
                        'X-Device-Id': deviceId,
                      },
                    ));
                    final response = await refreshDio.post(
                      '/api/v1/auth/refresh',
                      data: {'refreshToken': refreshToken},
                    );
                    if (response.statusCode == 200) {
                      final newAccessToken = response.data['accessToken'] as String;
                      final newRefreshToken = response.data['refreshToken'] as String;
                      await setToken(newAccessToken, newRefreshToken);
                      // 네이티브가 자체적으로 회전시킨 토큰을 React에 즉시 역동기화
                      // (React zustand 사본이 구세대로 남는 것을 방지)
                      onTokenRotatedNatively?.call(newAccessToken, newRefreshToken);
                      _refreshLock!.complete();
                      _refreshLock = null;

                      // Retry the original request with new token
                      final opts = error.requestOptions;
                      opts.headers['Authorization'] = 'Bearer $newAccessToken';
                      try {
                        final retryResponse = await refreshDio.fetch(opts);
                        return handler.resolve(retryResponse);
                      } catch (retryError) {
                        debugPrint('[AuthExpiry] Retry after token refresh failed: $retryError');
                        // 토큰 갱신은 성공했으나 원래 요청 재시도 실패 — 원본 에러 전파
                        return handler.next(error);
                      }
                    }
                  } catch (refreshError) {
                    debugPrint('[AuthExpiry] Token refresh failed: $refreshError');
                    if (refreshError is DioException && refreshError.response?.statusCode == 401) {
                      // Refresh token 자체가 서버에서 거부됨(재사용 감지/만료) — 죽은 토큰
                      refreshRejectedAsInvalid = true;
                    }
                    _refreshLock?.completeError(refreshError);
                    _refreshLock = null;
                  }
                }
              } catch (refreshError) {
                debugPrint('[AuthExpiry] Token refresh failed: $refreshError');
                if (refreshError is DioException && refreshError.response?.statusCode == 401) {
                  refreshRejectedAsInvalid = true;
                }
                _refreshLock?.completeError(refreshError);
                _refreshLock = null;
              }
            }
            // Trigger auth expiry callback (dedup: only once).
            // refreshToken 이 애초에 없으면 "로그인한 적 없는" 정상 상태이므로
            // 만료 알럿을 띄우지 않는다(신규 설치 첫 실행 시 오알럿 방지).
            // Set flag before async clearToken() to prevent race on concurrent 401s
            final hadSession = refreshToken != null && refreshToken.isNotEmpty;
            if (!_isAuthExpired && hadSession) {
              _isAuthExpired = true;
              debugPrint('[AuthExpiry] Token refresh failed, showing expiry dialog');
              onAuthExpired?.call();
            }
            // 세션이 애초에 없었거나(hadSession false, 정리할 잔여 토큰 대상)
            // refresh token이 명시적으로 거부된 경우에만 토큰을 삭제.
            // 네트워크 오류 등으로 refresh 자체를 시도하지 못한 경우는 세션 보존.
            final shouldClearTokens = !hadSession || refreshRejectedAsInvalid;
            if (shouldClearTokens && !WalkScreen.isOnStack && !CourseWalkScreen.isOnStack) {
              await clearToken();
            }
          }
          return handler.next(error);
        },
      ),
    );
  }

  void _setupCertificatePinning(Dio dio) {
    // Skip certificate pinning in debug and profile mode for local development.
    if (kDebugMode || kProfileMode) return;

    (dio.httpClientAdapter as IOHttpClientAdapter).createHttpClient = () {
      final client = HttpClient();
      client.badCertificateCallback = (X509Certificate cert, String host, int port) {
        // Only allow connections to our known hosts
        const allowedHosts = [
          'api.goldpet.com',
          'api.mannamsquare.com',
        ];

        if (!allowedHosts.contains(host)) {
          return false;
        }

        // 인증서 피닝(선택): 핀이 채워져 있으면 그 핀과 일치할 때만 허용한다.
        //
        // STYLE-009 회귀: 예전에는 핀이 비어 있으면 여기서 `return true` 를 했다.
        // 이 콜백은 **정상 검증에 실패한 인증서**일 때만 호출되므로, 그건 곧 우리 호스트로 오는
        // 잘못된 인증서(자체 서명·만료·MITM)를 무조건 신뢰한다는 뜻이었다. fail-open 취약점이다.
        // 이제 핀이 없으면 거부한다(fail-closed). 정상 인증서는 애초에 이 콜백을 타지 않으므로
        // 정상 트래픽에는 영향이 없다.
        //
        // 핀 추가 방법:
        //   openssl s_client -connect api.mannamsquare.com:443 | openssl x509 -pubkey -noout \
        //     | openssl pkey -pubin -outform der | openssl dgst -sha256 -binary | openssl enc -base64
        const allowedFingerprints = <String>[];

        if (allowedFingerprints.isEmpty) {
          return false;
        }

        // Reject by default once fingerprints are configured.
        return false;
      };
      return client;
    };
  }

  Dio get dio => _dio;

  Future<Response<T>> get<T>(
    String path, {
    Map<String, dynamic>? queryParameters,
  }) {
    return _dio.get<T>(path, queryParameters: queryParameters);
  }

  Future<Response<T>> post<T>(String path, {dynamic data}) {
    return _dio.post<T>(path, data: data);
  }

  Future<Response<T>> put<T>(String path, {dynamic data}) {
    return _dio.put<T>(path, data: data);
  }

  Future<Response<T>> delete<T>(String path) {
    return _dio.delete<T>(path);
  }

  Future<void> setToken(String accessToken, String refreshToken) async {
    await _storage.write(key: StorageKeys.accessToken, value: accessToken);
    await _storage.write(key: StorageKeys.refreshToken, value: refreshToken);
    // Reset auth expiry flag on re-login
    _isAuthExpired = false;
    // 재로그인 후 보류된 산책 데이터 자동 복구 시도
    _recoverPendingWalk();
  }

  /// 로컬에 저장된 보류 산책 데이터를 서버에 전송.
  /// setToken()에서 호출되며, 보류 데이터가 없으면 no-op.
  /// 산책 화면이 활성 중이면 복구를 건너뜀 (새 산책과의 충돌 방지).
  /// 인터셉터가 없는 별도 Dio를 사용하여 401 재진입 루프를 방지.
  Future<void> _recoverPendingWalk() async {
    // 동시 실행 방지: setToken이 login + syncAuthToken에서 2회 호출되므로
    // 첫 번째 호출만 실행하고 두 번째는 건너뜀
    if (_isRecoveringWalk) {
      debugPrint('[WalkRecovery] Already recovering, skipping duplicate');
      return;
    }
    _isRecoveringWalk = true;
    try {
      // 산책 화면이 활성 중이면 복구 건너뜀
      if (WalkScreen.isOnStack || CourseWalkScreen.isOnStack) return;

      final walkData = await WalkLocalStorage.loadWalkData();
      if (walkData == null) return;

      // 인터셉터 없는 별도 Dio로 전송 (401 재진입 방지)
      final token = await _storage.read(key: StorageKeys.accessToken);
      if (token == null) return;
      final recoveryDio = Dio(BaseOptions(
        baseUrl: ApiConstants.baseUrl,
        headers: {
          'Content-Type': 'application/json',
          'Authorization': 'Bearer $token',
        },
      ));
      final response = await recoveryDio.post('/api/v1/walks', data: walkData.toJson());
      if (response.statusCode == 200 || response.statusCode == 201) {
        await WalkLocalStorage.clearWalkData();
        debugPrint('[WalkRecovery] Pending walk saved successfully');
      }
    } catch (e) {
      debugPrint('[WalkRecovery] Failed to recover pending walk: $e');
      if (e is DioException && e.response?.statusCode == 400) {
        // 400 = 데이터 유효성 실패 → 재시도해도 동일하므로 로컬 데이터 삭제
        await WalkLocalStorage.clearWalkData();
        debugPrint('[WalkRecovery] Cleared invalid walk data (400)');
      }
      // 그 외 에러(네트워크, 5xx 등)는 로컬 데이터 유지 → 다음 setToken() 호출 시 재시도
    } finally {
      _isRecoveringWalk = false;
    }
  }

  Future<void> clearToken() async {
    await _storage.delete(key: StorageKeys.accessToken);
    await _storage.delete(key: StorageKeys.refreshToken);
    _isAuthExpired = false;
  }

  /// 산책 시작 전 토큰을 사전 갱신하여 만료 확률을 최소화.
  /// 실패해도 산책은 진행됨 (non-blocking).
  Future<void> refreshTokenIfNeeded() async {
    final refreshToken = await _storage.read(key: StorageKeys.refreshToken);
    if (refreshToken == null || refreshToken.isEmpty) return;
    await _performTokenRefresh(refreshToken);
  }

  /// 토큰 갱신 공통 로직. 인터셉터와 refreshTokenIfNeeded()에서 공유.
  Future<void> _performTokenRefresh(String refreshToken) async {
    if (_refreshLock != null) {
      // A refresh is already in progress — wait for it (10s timeout to prevent deadlock)
      debugPrint('[TokenRefresh] Waiting for in-progress refresh...');
      await _refreshLock!.future.timeout(const Duration(seconds: 10));
      return;
    }
    _refreshLock = Completer<void>();
    try {
      final deviceId = await PushNotificationService().getOrCreateDeviceId();
      final refreshDio = Dio(BaseOptions(
        baseUrl: ApiConstants.baseUrl,
        headers: {
          'Content-Type': 'application/json',
          'X-Device-Id': deviceId,
        },
      ));
      final response = await refreshDio.post(
        '/api/v1/auth/refresh',
        data: {'refreshToken': refreshToken},
      );
      if (response.statusCode == 200) {
        final newAccessToken = response.data['accessToken'] as String;
        final newRefreshToken = response.data['refreshToken'] as String;
        await setToken(newAccessToken, newRefreshToken);
        // 네이티브가 자체적으로 회전시킨 토큰을 React에 즉시 역동기화
        // (React zustand 사본이 구세대로 남는 것을 방지)
        onTokenRotatedNatively?.call(newAccessToken, newRefreshToken);
      }
      _refreshLock!.complete();
      _refreshLock = null;
    } catch (e) {
      _refreshLock?.completeError(e);
      _refreshLock = null;
      rethrow;
    }
  }
}
