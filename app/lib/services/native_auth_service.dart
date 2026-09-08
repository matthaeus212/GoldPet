import 'dart:io';
import 'package:flutter/services.dart';
import 'package:kakao_flutter_sdk_user/kakao_flutter_sdk_user.dart';
import 'package:flutter_naver_login/flutter_naver_login.dart';
import 'package:flutter_naver_login/interface/types/naver_login_result.dart';
import 'package:flutter_naver_login/interface/types/naver_token.dart';
import 'package:flutter_naver_login/interface/types/naver_login_status.dart';
import 'package:google_sign_in/google_sign_in.dart';
import 'package:flutter/foundation.dart';
import 'package:http/http.dart' as http;
import 'dart:convert';
import 'package:sign_in_with_apple/sign_in_with_apple.dart';
import 'package:goldpet_app/core/config/environment.dart';

class NativeAuthService {
  Future<String?> loginWithKakao() async {
    try {
      OAuthToken token;
      if (Platform.isIOS) {
        // iOS: 카카오톡 설치 시 네이티브 로그인, 미설치 시 웹 로그인
        bool isInstalled = await isKakaoTalkInstalled();
        debugPrint('KakaoTalk installed check: $isInstalled');
        if (isInstalled) {
          try {
            token = await UserApi.instance.loginWithKakaoTalk();
            debugPrint('Logged in with KakaoTalk');
          } catch (e) {
            debugPrint('KakaoTalk login failed: $e');
            if (e is PlatformException && e.code == 'CANCELED') {
              return null;
            }
            token = await UserApi.instance.loginWithKakaoAccount();
            debugPrint('Logged in with Kakao Account (Fallback)');
          }
        } else {
          token = await UserApi.instance.loginWithKakaoAccount();
          debugPrint('Logged in with Kakao Account');
        }
      } else {
        // Android: 카카오계정 웹 로그인 사용
        // (Android 14+ TalkAuthCodeActivity 결과 미반환 이슈 우회)
        token = await UserApi.instance.loginWithKakaoAccount();
        debugPrint('Logged in with Kakao Account (Android)');
      }
      return token.accessToken;
    } catch (e) {
      debugPrint('Kakao login failed: $e');
      return null;
    }
  }

  Future<String?> loginWithNaver() async {
    try {
      final NaverLoginResult result = await FlutterNaverLogin.logIn();
      debugPrint('Naver login result - status: ${result.status}, errorMessage: ${result.errorMessage}');
      if (result.status == NaverLoginStatus.loggedIn) {
        final NaverToken accessToken =
            await FlutterNaverLogin.getCurrentAccessToken();
        debugPrint('Logged in with Naver, token: ${accessToken.accessToken.substring(0, 10)}...');
        return accessToken.accessToken;
      }
    } catch (e) {
      debugPrint('Naver login failed: $e');
    }
    return null;
  }

  Future<String?> loginWithGoogle() async {
    try {
      // v7 Migration: Initialize singleton
      await GoogleSignIn.instance.initialize();

      // v7 Migration: Use authenticate() instead of signIn()
      final GoogleSignInAccount googleUser = await GoogleSignIn.instance
          .authenticate();

      final GoogleSignInAuthentication googleAuth = googleUser.authentication;
      debugPrint('Logged in with Google');
      // v7 Migration: Use idToken as accessToken is removed
      return googleAuth.idToken;
    } catch (e) {
      debugPrint('Google login failed: $e');
    }
    return null;
  }

  Future<String?> loginWithApple() async {
    try {
      final credential = await SignInWithApple.getAppleIDCredential(
        scopes: [
          AppleIDAuthorizationScopes.email,
          AppleIDAuthorizationScopes.fullName,
        ],
      );

      debugPrint('Logged in with Apple: ${credential.identityToken}');
      return credential.identityToken;
    } catch (e) {
      debugPrint('Apple login failed: $e');
      return null;
    }
  }

  Future<Map<String, dynamic>?> verifyTokenWithBackend(
    String provider,
    String accessToken,
  ) async {
    final config = EnvironmentConfig.fromEnv();
    final url = Uri.parse(
      '${config.apiBaseUrl}/api/v1/auth/login/$provider',
    );
    try {
      final response = await http.post(
        url,
        headers: {'Content-Type': 'application/json'},
        body: jsonEncode({'accessToken': accessToken}),
      );

      if (response.statusCode == 200) {
        final data = jsonDecode(response.body);
        debugPrint('Backend verification success: $data');
        return data;
      } else {
        debugPrint(
          'Backend verification failed: Status ${response.statusCode}, Body: ${response.body}',
        );
        return null;
      }
    } catch (e) {
      debugPrint('Backend connection failed: $e');
      return null;
    }
  }
}
