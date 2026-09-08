import 'dart:convert';
import 'dart:io';
import 'package:flutter/foundation.dart';
import 'package:http/http.dart' as http;
import 'package:package_info_plus/package_info_plus.dart';
import 'package:shared_preferences/shared_preferences.dart';
import '../constants/app_constants.dart';

class AppVersionInfo {
  final bool forceUpdate;
  final bool softUpdate;
  final String minimumVersion;
  final String latestVersion;
  final String updateUrl;
  final String? updateMessage;

  AppVersionInfo({
    required this.forceUpdate,
    required this.softUpdate,
    required this.minimumVersion,
    required this.latestVersion,
    required this.updateUrl,
    this.updateMessage,
  });

  factory AppVersionInfo.fromJson(Map<String, dynamic> json) {
    return AppVersionInfo(
      forceUpdate: json['forceUpdate'] as bool? ?? false,
      softUpdate: json['softUpdate'] as bool? ?? false,
      minimumVersion: json['minimumVersion'] as String? ?? '1.0.0',
      latestVersion: json['latestVersion'] as String? ?? '1.0.0',
      updateUrl: json['updateUrl'] as String? ?? '',
      updateMessage: json['updateMessage'] as String?,
    );
  }
}

class AppVersionService {
  static const String _softUpdateDismissedKey = 'soft_update_dismissed_at';
  static const Duration _softUpdateCooldown = Duration(hours: 24);

  /// 서버에서 버전 정보를 조회합니다.
  /// 네트워크 오류나 파싱 오류 시 null을 반환합니다.
  static Future<AppVersionInfo?> checkVersion() async {
    try {
      final packageInfo = await PackageInfo.fromPlatform();
      final currentVersion = packageInfo.version;
      final platform = Platform.isAndroid ? 'android' : 'ios';

      final uri = Uri.parse(
        '${ApiConstants.baseUrl}/api/v1/app/version'
        '?platform=$platform&currentVersion=$currentVersion',
      );

      final response = await http
          .get(uri, headers: {'Accept': 'application/json'})
          .timeout(const Duration(seconds: 10));

      if (response.statusCode == 200) {
        final json = jsonDecode(response.body) as Map<String, dynamic>;
        return AppVersionInfo.fromJson(json);
      } else {
        debugPrint('[AppVersion] Server returned ${response.statusCode}');
        return null;
      }
    } catch (e) {
      debugPrint('[AppVersion] Version check failed: $e');
      return null;
    }
  }

  /// 소프트 업데이트 다이얼로그를 보여줘야 하는지 확인합니다.
  /// 24시간 이내에 닫은 경우 false를 반환합니다.
  static Future<bool> shouldShowSoftUpdate() async {
    try {
      final prefs = await SharedPreferences.getInstance();
      final dismissedAt = prefs.getInt(_softUpdateDismissedKey);
      if (dismissedAt == null) return true;

      final dismissedTime = DateTime.fromMillisecondsSinceEpoch(dismissedAt);
      final elapsed = DateTime.now().difference(dismissedTime);
      return elapsed >= _softUpdateCooldown;
    } catch (e) {
      debugPrint('[AppVersion] shouldShowSoftUpdate error: $e');
      return true;
    }
  }

  /// 소프트 업데이트 다이얼로그를 닫은 시각을 저장합니다.
  static Future<void> dismissSoftUpdate() async {
    try {
      final prefs = await SharedPreferences.getInstance();
      await prefs.setInt(
        _softUpdateDismissedKey,
        DateTime.now().millisecondsSinceEpoch,
      );
    } catch (e) {
      debugPrint('[AppVersion] dismissSoftUpdate error: $e');
    }
  }
}
