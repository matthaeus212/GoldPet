/// 앱 실행 환경
enum Environment {
  local, // 로컬 개발 (localhost)
  dev,   // 개발 서버 (mannamsquare.com)
  prod,  // 상용 서버 (goldpet.com)
}

/// 환경별 설정 값
class EnvironmentConfig {
  final Environment environment;
  final String apiBaseUrl;
  final String appBaseUrl;
  final String adminBaseUrl;
  /// 파일/이미지가 서빙되는 S3(MinIO) 퍼블릭 엔드포인트.
  /// 브리지 다운로드 허용 호스트를 여기서 파생시킨다(BridgeUrlGuard).
  final String s3BaseUrl;

  const EnvironmentConfig._({
    required this.environment,
    required this.apiBaseUrl,
    required this.appBaseUrl,
    required this.adminBaseUrl,
    required this.s3BaseUrl,
  });

  /// 로컬 개발 환경 (같은 WiFi 네트워크에서 PC IP로 접근)
  static const local = EnvironmentConfig._(
    environment: Environment.local,
    apiBaseUrl: 'http://192.168.0.87:8081',
    appBaseUrl: 'http://192.168.0.87:5173',
    adminBaseUrl: 'http://192.168.0.87:5174',
    s3BaseUrl: 'http://192.168.0.87:9100',
  );

  /// 개발 서버 환경 (mannamsquare.com)
  static const dev = EnvironmentConfig._(
    environment: Environment.dev,
    apiBaseUrl: 'https://api.mannamsquare.com',
    appBaseUrl: 'https://app.mannamsquare.com',
    adminBaseUrl: 'https://admin.mannamsquare.com',
    s3BaseUrl: 'https://s3.mannamsquare.com',
  );

  /// 상용 서버 환경 (goldpet.com)
  static const prod = EnvironmentConfig._(
    environment: Environment.prod,
    apiBaseUrl: 'https://api.goldpet.com',
    appBaseUrl: 'https://app.goldpet.com',
    adminBaseUrl: 'https://admin.goldpet.com',
    s3BaseUrl: 'https://s3.goldpet.com',
  );

  /// dart-define으로 전달된 ENV 값에 따라 환경 설정 반환
  /// 사용법: flutter run --dart-define=ENV=local
  static EnvironmentConfig fromEnv() {
    const envName = String.fromEnvironment('ENV', defaultValue: 'dev');

    switch (envName) {
      case 'local':
        return local;
      case 'dev':
        return dev;
      case 'prod':
        return prod;
      default:
        return dev; // 기본값: 개발 서버
    }
  }

  bool get isLocal => environment == Environment.local;
  bool get isDev => environment == Environment.dev;
  bool get isProd => environment == Environment.prod;
}
