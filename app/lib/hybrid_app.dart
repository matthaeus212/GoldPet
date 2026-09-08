import 'package:flutter/material.dart';
import 'package:flutter/services.dart';
import 'package:goldpet_app/core/webview/hybrid_webview.dart';
import 'package:flutter_native_splash/flutter_native_splash.dart';
import 'package:goldpet_app/core/constants/app_constants.dart';
import 'package:goldpet_app/core/constants/keys.dart';
import 'package:goldpet_app/core/api/api_client.dart';
import 'package:goldpet_app/features/walk/screens/walk_screen.dart';
import 'package:goldpet_app/features/walk/screens/course_walk_screen.dart';
import 'package:goldpet_app/core/services/app_version_service.dart';
import 'package:goldpet_app/core/widgets/force_update_dialog.dart';

/// Debug observer to log every Navigator push/pop/replace/remove event.
class _DebugNavigatorObserver extends NavigatorObserver {
  @override
  void didPush(Route route, Route? previousRoute) {
    debugPrint('[NAV] PUSH ${_routeName(route)} (prev: ${_routeName(previousRoute)})');
  }

  @override
  void didPop(Route route, Route? previousRoute) {
    debugPrint('[NAV] POP ${_routeName(route)} (showing: ${_routeName(previousRoute)})');
  }

  @override
  void didReplace({Route? newRoute, Route? oldRoute}) {
    debugPrint('[NAV] REPLACE ${_routeName(oldRoute)} → ${_routeName(newRoute)}');
  }

  @override
  void didRemove(Route route, Route? previousRoute) {
    debugPrint('[NAV] REMOVE ${_routeName(route)}');
  }

  static String _routeName(Route? route) {
    if (route == null) return 'null';
    return route.settings.name ?? route.runtimeType.toString();
  }
}

class HybridApp extends StatefulWidget {
  const HybridApp({super.key});

  @override
  State<HybridApp> createState() => _HybridAppState();
}

class _HybridAppState extends State<HybridApp> with WidgetsBindingObserver {
  static const _deepLinkChannel = MethodChannel('com.mannam.goldpet/deep_link');
  bool _isPushingWalkScreen = false;
  bool _versionCheckInProgress = false;
  // DAU: app-open active signal sent once per session (reset on cold start, i.e.
  // when this State is recreated). Stays false until a 2xx so a pre-login 401
  // is retried on the next resume.
  bool _sessionActiveRecorded = false;

  @override
  void initState() {
    super.initState();
    WidgetsBinding.instance.addObserver(this);
    _setupDeepLinkListener();
    // 앱 시작 시 버전 체크 (첫 프레임 이후 BuildContext 사용 가능)
    WidgetsBinding.instance.addPostFrameCallback((_) {
      _checkAppVersion();
      // 콜드 스타트(이미 resumed 상태로 시작)에서는 didChangeAppLifecycleState가
      // 발생하지 않을 수 있으므로 여기서도 DAU 신호를 시도한다.
      _recordSessionActive();
    });
  }

  @override
  void dispose() {
    WidgetsBinding.instance.removeObserver(this);
    super.dispose();
  }

  @override
  void didChangeAppLifecycleState(AppLifecycleState state) {
    debugPrint('[DL] Lifecycle: $state, isOnStack=${WalkScreen.isOnStack}, navHash=${rootNavigatorKey.currentState.hashCode}');
    if (state == AppLifecycleState.resumed) {
      // iOS에서 앱 복귀 시 토큰 사전 갱신 (WKWebView content process kill 대비)
      _refreshTokenOnResume();
      _checkAppVersion();
      _checkPendingDeepLink();
      _recordSessionActive();
    }
  }

  /// 앱 복귀 시 토큰을 사전 갱신하여 WKWebView 리로드 시 401 방지.
  /// 실패해도 앱 동작에 영향 없음 (non-blocking).
  Future<void> _refreshTokenOnResume() async {
    try {
      await ApiClient().refreshTokenIfNeeded();
      debugPrint('[TokenRefresh] App resume token refresh completed');
    } catch (e) {
      debugPrint('[TokenRefresh] App resume token refresh failed (non-blocking): $e');
    }
  }

  /// 앱 오픈(DAU) 활성 신호를 서버에 1회/세션 기록한다.
  /// - 세션당 1회: 성공(2xx) 시에만 플래그를 세팅 → 로그인 전 401은 다음 resume에 재시도.
  /// - 비차단: fire-and-forget, 실패해도 앱 동작에 영향 없음.
  /// - 인증: ApiClient 인터셉터가 기존 액세스 토큰을 자동 첨부.
  /// - 순수 폴링 엔드포인트가 아니므로 서버에서 KST 일자 기준 1행으로 집계된다.
  Future<void> _recordSessionActive() async {
    if (_sessionActiveRecorded) return;
    try {
      final resp = await ApiClient().post('/api/v1/session/active');
      final code = resp.statusCode ?? 0;
      if (code >= 200 && code < 300) {
        _sessionActiveRecorded = true;
        debugPrint('[DAU] session/active recorded');
      }
    } catch (e) {
      // 미로그인(401)·네트워크 오류 등 — 플래그 유지(false)로 다음 resume에 재시도.
      debugPrint('[DAU] session/active skipped (non-blocking): $e');
    }
  }

  /// Check for deep links buffered on the native side.
  /// Covers the case where Activity was destroyed while backgrounded
  /// and onNewIntent() fired before the Dart handler was registered,
  /// so the invokeMethod("onDeepLink") was silently lost.
  Future<void> _checkPendingDeepLink() async {
    debugPrint('[DL] _checkPendingDeepLink called');
    try {
      final link = await _deepLinkChannel.invokeMethod<String>('getInitialLink');
      debugPrint('[DL] pending link from native: $link');
      if (link != null && mounted) {
        _handleDeepLink(link);
      }
    } catch (e) {
      debugPrint('[DL] _checkPendingDeepLink error: $e');
    }
  }

  Future<void> _checkAppVersion() async {
    if (_versionCheckInProgress) return;
    if (!mounted) return;
    _versionCheckInProgress = true;
    try {
      final info = await AppVersionService.checkVersion();
      if (info == null || !mounted) return;

      // Use rootNavigatorKey.currentContext (below MaterialApp) instead of
      // this widget's context (above MaterialApp) so showDialog can find
      // MaterialLocalizations.
      final dialogContext = rootNavigatorKey.currentContext;
      if (dialogContext == null || !dialogContext.mounted) return;

      if (info.forceUpdate) {
        await showForceUpdateDialog(
          dialogContext,
          updateUrl: info.updateUrl,
          updateMessage: info.updateMessage,
        );
      } else if (info.softUpdate) {
        final shouldShow = await AppVersionService.shouldShowSoftUpdate();
        if (shouldShow && dialogContext.mounted) {
          await showSoftUpdateDialog(
            dialogContext,
            updateUrl: info.updateUrl,
            updateMessage: info.updateMessage,
          );
        }
      }
    } finally {
      _versionCheckInProgress = false;
    }
  }

  Future<void> _setupDeepLinkListener() async {
    // Push handler: warm/hot restart deep links
    _deepLinkChannel.setMethodCallHandler((call) async {
      if (call.method == 'onDeepLink') {
        final uri = call.arguments as String?;
        debugPrint('[DL] onDeepLink push received: $uri');
        if (uri != null) _handleDeepLink(uri);
      }
    });

    // Pull handler: cold start deep link (buffered on native side)
    try {
      final initialLink = await _deepLinkChannel.invokeMethod<String>('getInitialLink');
      debugPrint('[DL] getInitialLink returned: $initialLink');
      if (initialLink != null) {
        // On cold start (Activity destroyed → recreated), the Navigator may
        // not be mounted yet.  Wait for the first frame, then poll until
        // rootNavigatorKey is available before delivering the deep link.
        WidgetsBinding.instance.addPostFrameCallback((_) {
          _handleColdStartDeepLink(initialLink);
        });
      }
    } catch (e) {
      debugPrint('[DL] getInitialLink error: $e');
    }
  }

  /// Retry deep-link delivery until the Navigator is ready (max ~3 s).
  void _handleColdStartDeepLink(String link, [int attempt = 0]) {
    debugPrint('[DL] _handleColdStartDeepLink attempt=$attempt, nav=${rootNavigatorKey.currentState}');
    if (!mounted || attempt > 10) return;
    if (rootNavigatorKey.currentState != null) {
      _handleDeepLink(link);
    } else {
      Future.delayed(const Duration(milliseconds: 300), () {
        _handleColdStartDeepLink(link, attempt + 1);
      });
    }
  }

  // Allowed Universal Link paths for the React SPA.
  // Anything outside this list is silently ignored.
  static final _universalLinkPathPattern = RegExp(
    r'^/(w|c|pet|health|post)/[a-zA-Z0-9_\-]+(/[a-zA-Z0-9_\-]+)?$',
  );

  void _handleDeepLink(String uriString) {
    debugPrint('[DL] _handleDeepLink: $uriString');
    final uri = Uri.tryParse(uriString);
    if (uri == null) return;

    // Universal Link (https://app.mannamsquare.com/… or https://app.goldpet.com/…)
    if (uri.scheme == 'https' &&
        (uri.host == 'app.mannamsquare.com' || uri.host == 'app.goldpet.com')) {
      final path = uri.path;
      if (_universalLinkPathPattern.hasMatch(path)) {
        final fullPath = path + (uri.query.isNotEmpty ? '?${uri.query}' : '');
        debugPrint('[DL] Universal Link → WebView path: $fullPath');
        HybridWebView.navigateTo?.call(fullPath);
      } else {
        debugPrint('[DL] Universal Link path not in allowlist, ignoring: $path');
      }
      return;
    }

    if (uri.scheme != 'goldpet') return;

    final host = uri.host;
    String? spotAction;

    if (host == 'spot' && uri.pathSegments.isNotEmpty) {
      spotAction = uri.pathSegments.first.toUpperCase(); // pee → PEE
    }

    // Handle walk/stop deep link — stop the walk if in progress
    if (host == 'walk' && uri.pathSegments.isNotEmpty && uri.pathSegments.first == 'stop') {
      if (WalkScreen.isOnStack || CourseWalkScreen.isOnStack) {
        walkSpotActionController.add('STOP');
      }
      return;
    }

    if (host == 'walk' || host == 'spot') {
      debugPrint('[DL] walkOnStack=${WalkScreen.isOnStack}, courseOnStack=${CourseWalkScreen.isOnStack}, isPushing=$_isPushingWalkScreen');
      if (CourseWalkScreen.isOnStack) {
        // CourseWalkScreen is active — deliver spot action via stream, don't push
        debugPrint('[DL] CourseWalkScreen on stack, spotAction=$spotAction');
        if (spotAction != null) {
          walkSpotActionController.add(spotAction);
        }
      } else if (WalkScreen.isOnStack) {
        // WalkScreen was pushed and dispose() hasn't run — deliver spot action
        debugPrint('[DL] WalkScreen on stack, spotAction=$spotAction');
        if (spotAction != null) {
          walkSpotActionController.add(spotAction);
        }
        // If no spotAction (stats/body click), PendingIntent already brought
        // the app to foreground with WalkScreen visible — nothing more needed.
      } else if (!_isPushingWalkScreen) {
        // Neither walk screen on stack — push new WalkScreen
        final nav = rootNavigatorKey.currentState;
        debugPrint('[DL] Pushing WalkScreen, nav=$nav');
        if (nav == null) {
          debugPrint('[DL] FAILED: navigator is null');
          return;
        }
        _isPushingWalkScreen = true;
        nav.push(
          MaterialPageRoute(
            builder: (_) => WalkScreen(initialSpotAction: spotAction),
          ),
        );
        // Clear the pushing guard after the push animation (~300ms).
        // nav.push().then() only fires when the route is POPPED, which
        // would block all subsequent deep links while WalkScreen is showing.
        Future.delayed(const Duration(milliseconds: 500), () {
          _isPushingWalkScreen = false;
        });
      } else {
        debugPrint('[DL] BLOCKED: push in progress');
      }
    }
  }

  @override
  Widget build(BuildContext context) {
    // React 앱 URL - 환경 설정에 따라 결정
    final reactAppUrl = currentEnv.appBaseUrl;

    return MaterialApp(
      navigatorKey: rootNavigatorKey,
      navigatorObservers: [_DebugNavigatorObserver()],
      title: 'GoldPet',
      debugShowCheckedModeBanner: false,
      theme: ThemeData(primarySwatch: Colors.brown, useMaterial3: true),
      home: HybridWebView(
        initialUrl: reactAppUrl,
        onAppLoaded: () {
          // WebView 로딩이 완료되면 스플래시 화면 제거
          FlutterNativeSplash.remove();
        },
      ),
    );
  }
}
