// WebView 브리지로 들어온 URL·파일명을 신뢰하지 않고 검증하는 가드 (SEC-007)
import '../config/environment.dart';

/// React(WebView)가 브리지로 넘긴 값은 **신뢰 경계 밖**이다.
/// 채팅 파일의 `originalFileName` 은 업로드한 사용자가 정하는 값이고, `url` 도 페이지가
/// 침해되면 임의 값이 들어올 수 있다. 그대로 쓰면 경로 이탈 쓰기와 임의 스킴 실행이 된다.
class BridgeUrlGuard {
  BridgeUrlGuard._();

  /// 앱이 다운로드를 허용하는 호스트 — 환경 설정에서 파생한다(하드코딩 금지).
  static Set<String> allowedDownloadHosts(EnvironmentConfig env) => {
        Uri.parse(env.apiBaseUrl).host,
        Uri.parse(env.appBaseUrl).host,
        Uri.parse(env.adminBaseUrl).host,
        Uri.parse(env.s3BaseUrl).host,
      }..removeWhere((h) => h.isEmpty);

  /// 다운로드 대상 URL 검증. 우리 인프라의 https 자원만 허용한다.
  /// (local 환경은 평문 http 로 뜨므로 그때만 http 를 허용한다.)
  static bool isAllowedDownloadUrl(String? url, EnvironmentConfig env) {
    if (url == null || url.isEmpty) return false;
    final uri = Uri.tryParse(url);
    if (uri == null || !uri.isAbsolute) return false;

    final httpAllowed = env.environment == Environment.local;
    final schemeOk = uri.scheme == 'https' || (httpAllowed && uri.scheme == 'http');
    if (!schemeOk) return false;

    return allowedDownloadHosts(env).contains(uri.host);
  }

  /// 외부로 열 URL 검증. 웹 링크만 허용하고 커스텀 스킴(앱 실행)은 막는다.
  static bool isAllowedExternalUrl(String? url) {
    if (url == null || url.isEmpty) return false;
    final uri = Uri.tryParse(url);
    if (uri == null || !uri.isAbsolute) return false;
    if (uri.host.isEmpty) return false;
    return uri.scheme == 'https' || uri.scheme == 'http';
  }

  /// 저장 파일명을 basename 으로 강제한다.
  ///
  /// `../../x`, `dir/x`, 윈도 구분자, 널바이트를 모두 제거해 앱 샌드박스를 벗어나지 못하게 한다.
  /// 남는 게 없으면(`..`, `.`, 빈 문자열) 안전한 기본값으로 떨어진다.
  static String safeFileName(String? raw, {String fallback = 'download'}) {
    if (raw == null) return fallback;
    // 널바이트/제어문자 제거 — 경로 truncation 회피 차단.
    var name = raw.replaceAll(RegExp(r'[\x00-\x1f\x7f]'), '');
    // 마지막 경로 구분자 뒤만 취한다(POSIX/윈도 양쪽).
    final lastSeparator = name.lastIndexOf(RegExp(r'[/\\]'));
    if (lastSeparator >= 0) name = name.substring(lastSeparator + 1);
    name = name.trim();
    if (name.isEmpty || name == '.' || name == '..') return fallback;
    return name;
  }
}
