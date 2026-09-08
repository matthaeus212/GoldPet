import '../config/environment.dart';

/// 현재 환경 설정 (dart-define으로 설정)
/// 사용법:
///   로컬: flutter run --dart-define=ENV=local
///   개발: flutter run --dart-define=ENV=dev
///   상용: flutter run --dart-define=ENV=prod
final EnvironmentConfig currentEnv = EnvironmentConfig.fromEnv();

class ApiConstants {
  // 환경에 따른 Base URL
  static String get baseUrl => currentEnv.apiBaseUrl;

  // Timeouts
  static const Duration connectTimeout = Duration(seconds: 10);
  static const Duration receiveTimeout = Duration(seconds: 10);
}
