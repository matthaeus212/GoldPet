import 'dart:convert';
import 'package:flutter/foundation.dart';
import 'package:shared_preferences/shared_preferences.dart';
import 'walk_models.dart';

/// 산책 데이터 로컬 저장 서비스.
/// 인증 만료 또는 앱 종료 시 산책 데이터를 보존하기 위한 safety net.
class WalkLocalStorage {
  static const _key = 'pending_walk_data';

  /// Pre-cached instance for use in synchronous contexts (dispose).
  /// Must call [initialize] during WalkScreen.initState().
  static SharedPreferences? _prefs;

  /// Pre-cache SharedPreferences instance.
  /// Call this in WalkScreen.initState() so dispose() can use sync writes.
  static Future<void> initialize() async {
    _prefs = await SharedPreferences.getInstance();
  }

  /// Best-effort save using pre-cached instance (safe to call in dispose).
  /// The platform write is fire-and-forget — may not complete if engine is detaching.
  static void saveWalkDataBestEffort(CreateWalkRequest request) {
    try {
      _prefs?.setString(_key, jsonEncode(request.toJson()));
      debugPrint('[WalkLocalStorage] Best-effort save completed');
    } catch (e) {
      debugPrint('[WalkLocalStorage] Best-effort save failed: $e');
    }
  }

  /// Async save (for periodic saves during walk where we can await).
  static Future<void> saveWalkData(CreateWalkRequest request) async {
    final prefs = _prefs ?? await SharedPreferences.getInstance();
    await prefs.setString(_key, jsonEncode(request.toJson()));
  }

  /// Load pending walk data, or null if none exists.
  static Future<CreateWalkRequest?> loadWalkData() async {
    try {
      final prefs = _prefs ?? await SharedPreferences.getInstance();
      final json = prefs.getString(_key);
      if (json == null) return null;
      return CreateWalkRequest.fromJson(jsonDecode(json) as Map<String, dynamic>);
    } catch (e) {
      debugPrint('[WalkLocalStorage] Failed to load walk data: $e');
      return null;
    }
  }

  /// Remove persisted walk data.
  static Future<void> clearWalkData() async {
    final prefs = _prefs ?? await SharedPreferences.getInstance();
    await prefs.remove(_key);
  }

  /// Quick check for pending walk data.
  static Future<bool> hasPendingWalk() async {
    final prefs = _prefs ?? await SharedPreferences.getInstance();
    return prefs.containsKey(_key);
  }
}
