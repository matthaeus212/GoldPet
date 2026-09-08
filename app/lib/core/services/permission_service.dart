import 'dart:io';
import 'package:flutter/foundation.dart';
import 'package:permission_handler/permission_handler.dart';
import 'package:device_info_plus/device_info_plus.dart';

/// 개별 권한 결과
enum PermissionResult { granted, denied, permanentlyDenied, limited }

/// 전체 권한 결과 (permission_handler의 PermissionStatus와 이름 충돌 방지)
class AllPermissionsResult {
  final PermissionResult location;
  final PermissionResult camera;
  final PermissionResult photo;
  final PermissionResult notification;

  const AllPermissionsResult({
    required this.location,
    required this.camera,
    required this.photo,
    required this.notification,
  });

  Map<String, dynamic> toJson() => {
        'location': {
          'granted': location == PermissionResult.granted,
          'status': location.name,
        },
        'camera': {
          'granted': camera == PermissionResult.granted,
          'status': camera.name,
        },
        'photo': {
          'granted': photo == PermissionResult.granted ||
              photo == PermissionResult.limited,
          'status': photo.name,
        },
        'notification': {
          'granted': notification == PermissionResult.granted,
          'status': notification.name,
        },
      };
}

/// 중앙 집중화된 권한 관리 서비스
class PermissionService {
  /// permission_handler의 PermissionStatus를 PermissionResult로 변환
  PermissionResult _mapStatus(PermissionStatus status) {
    if (status.isGranted) return PermissionResult.granted;
    if (status.isPermanentlyDenied) return PermissionResult.permanentlyDenied;
    if (status.isLimited) return PermissionResult.limited;
    return PermissionResult.denied;
  }

  /// Android 사진 권한 (API 33+ photos, 이하 storage)
  Future<Permission> _getPhotoPermission() async {
    if (Platform.isAndroid) {
      final androidInfo = await DeviceInfoPlugin().androidInfo;
      if (androidInfo.version.sdkInt >= 33) {
        return Permission.photos;
      }
      return Permission.storage;
    }
    return Permission.photos;
  }

  /// 모든 권한 상태 확인 (OS 다이얼로그 표시 안 함)
  Future<AllPermissionsResult> checkAllPermissions() async {
    PermissionResult locationResult;
    PermissionResult cameraResult;
    PermissionResult photoResult;
    PermissionResult notificationResult;

    try {
      locationResult = _mapStatus(await Permission.location.status);
    } catch (_) {
      locationResult = PermissionResult.denied;
    }

    try {
      cameraResult = _mapStatus(await Permission.camera.status);
    } catch (_) {
      cameraResult = PermissionResult.denied;
    }

    try {
      final photoPermission = await _getPhotoPermission();
      photoResult = _mapStatus(await photoPermission.status);
    } catch (_) {
      photoResult = PermissionResult.denied;
    }

    try {
      notificationResult = _mapStatus(await Permission.notification.status);
    } catch (_) {
      notificationResult = PermissionResult.denied;
    }

    return AllPermissionsResult(
      location: locationResult,
      camera: cameraResult,
      photo: photoResult,
      notification: notificationResult,
    );
  }

  /// 모든 권한을 순차 요청 (이미 허용된 권한은 건너뜀)
  /// 각 권한은 try/catch로 감싸서 하나가 실패해도 나머지 진행
  Future<AllPermissionsResult> requestAllPermissions() async {
    PermissionResult locationResult;
    PermissionResult cameraResult;
    PermissionResult photoResult;
    PermissionResult notificationResult;

    // 1. Location
    try {
      final status = await Permission.location.status;
      if (status.isGranted) {
        locationResult = PermissionResult.granted;
      } else {
        locationResult = _mapStatus(await Permission.location.request());
      }
    } catch (e) {
      debugPrint('[PermissionService] Location permission error: $e');
      locationResult = PermissionResult.denied;
    }

    // 2. Camera
    try {
      final status = await Permission.camera.status;
      if (status.isGranted) {
        cameraResult = PermissionResult.granted;
      } else {
        cameraResult = _mapStatus(await Permission.camera.request());
      }
    } catch (e) {
      debugPrint('[PermissionService] Camera permission error: $e');
      cameraResult = PermissionResult.denied;
    }

    // 3. Photo
    try {
      final photoPermission = await _getPhotoPermission();
      final status = await photoPermission.status;
      if (status.isGranted || status.isLimited) {
        photoResult = _mapStatus(status);
      } else {
        photoResult = _mapStatus(await photoPermission.request());
      }
    } catch (e) {
      debugPrint('[PermissionService] Photo permission error: $e');
      photoResult = PermissionResult.denied;
    }

    // 4. Notification
    try {
      final status = await Permission.notification.status;
      if (status.isGranted) {
        notificationResult = PermissionResult.granted;
      } else {
        notificationResult = _mapStatus(await Permission.notification.request());
      }
    } catch (e) {
      debugPrint('[PermissionService] Notification permission error: $e');
      notificationResult = PermissionResult.denied;
    }

    return AllPermissionsResult(
      location: locationResult,
      camera: cameraResult,
      photo: photoResult,
      notification: notificationResult,
    );
  }

  /// 개별 위치 권한 요청
  Future<PermissionResult> requestLocationPermission() async {
    try {
      final status = await Permission.location.request();
      return _mapStatus(status);
    } catch (_) {
      return PermissionResult.denied;
    }
  }

  /// 개별 카메라 권한 요청
  Future<PermissionResult> requestCameraPermission() async {
    try {
      final status = await Permission.camera.request();
      return _mapStatus(status);
    } catch (_) {
      return PermissionResult.denied;
    }
  }

  /// 개별 사진 권한 요청
  Future<PermissionResult> requestPhotoPermission() async {
    try {
      final photoPermission = await _getPhotoPermission();
      final status = await photoPermission.request();
      return _mapStatus(status);
    } catch (_) {
      return PermissionResult.denied;
    }
  }

  /// 앱 설정 열기 (영구 거부된 권한 해결용)
  Future<void> openSettings() async {
    await openAppSettings();
  }
}
