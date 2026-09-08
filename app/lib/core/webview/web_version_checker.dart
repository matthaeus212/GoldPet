import 'dart:async';
import 'dart:convert';
import 'package:flutter/foundation.dart';
import 'package:http/http.dart' as http;
import 'package:shared_preferences/shared_preferences.dart';
import 'package:webview_flutter/webview_flutter.dart';

/// WebView에 로드하는 React 앱의 버전을 추적하고, 새 배포 감지 시
/// WebView HTTP 캐시를 비워 브라우저 캐시된 구버전 JS가 계속 실행되는
/// 문제를 방지한다. 로그인/프로필 등은 localStorage에 있으므로 그대로 유지.
class WebVersionChecker {
  // v2: 이 키를 bump하면 재설치/업데이트 후 반드시 1회 clearCache 실행됨.
  static const String _prefsKey = 'web_app_version_v2';
  static const Duration _timeout = Duration(seconds: 3);

  /// 새 버전이 감지되어 캐시를 비운 경우 true.
  static Future<bool> checkAndMaybeClearCache({
    required String baseUrl,
    required WebViewController controller,
  }) async {
    try {
      final uri = Uri.parse('$baseUrl/version.json');
      final res = await http
          .get(uri, headers: const {'Cache-Control': 'no-cache'})
          .timeout(_timeout);
      if (res.statusCode != 200) return false;

      final decoded = jsonDecode(res.body);
      if (decoded is! Map) return false;
      final newVersion = decoded['version'];
      if (newVersion is! String || newVersion.isEmpty) return false;

      final prefs = await SharedPreferences.getInstance();
      final oldVersion = prefs.getString(_prefsKey);

      // oldVersion == null: 이 앱 바이너리가 처음 실행됐거나 prefs 초기화됨.
      // 재설치 직후 WebView HTTP 캐시에 이전 설치의 구버전 해시 청크가
      // 남아있을 수 있으므로 방어적으로 clearCache.
      final needClear = oldVersion == null || oldVersion != newVersion;
      if (needClear) {
        debugPrint('[WebVersionChecker] ${oldVersion ?? "(null)"} -> $newVersion, clearing WebView cache');
        try {
          await controller.clearCache();
        } catch (e) {
          debugPrint('[WebVersionChecker] clearCache failed: $e');
        }
        await prefs.setString(_prefsKey, newVersion);
        return true;
      }
      return false;
    } catch (e) {
      debugPrint('[WebVersionChecker] check skipped: $e');
      return false;
    }
  }
}
