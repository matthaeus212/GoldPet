import 'dart:io';
import 'package:image_picker/image_picker.dart';
import 'package:permission_handler/permission_handler.dart';
import 'package:device_info_plus/device_info_plus.dart';
import 'dart:convert';

/// 카메라 및 사진 라이브러리 서비스
class CameraService {
  final ImagePicker _picker = ImagePicker();
  
  /// 카메라 권한 요청
  Future<Map<String, dynamic>> requestCameraPermission() async {
    try {
      final status = await Permission.camera.request();
      return {
        'granted': status.isGranted,
        'status': status.toString(),
        'isPermanentlyDenied': status.isPermanentlyDenied,
      };
    } catch (e) {
      return {
        'granted': false,
        'status': 'error',
        'error': e.toString(),
      };
    }
  }
  
  /// 사진 라이브러리 권한 요청
  /// Android 13 이상: Permission.photos
  /// Android 12 이하: Permission.storage
  /// iOS: Permission.photos
  Future<Map<String, dynamic>> requestPhotoPermission() async {
    try {
      Permission permission;
      
      if (Platform.isAndroid) {
        final androidInfo = await DeviceInfoPlugin().androidInfo;
        // Android 13 (API 33) 이상에서는 photos 권한 사용
        if (androidInfo.version.sdkInt >= 33) {
          permission = Permission.photos;
        } else {
          permission = Permission.storage;
        }
      } else {
        // iOS
        permission = Permission.photos;
      }
      
      final status = await permission.request();
      return {
        'granted': status.isGranted,
        'status': status.toString(),
        'isPermanentlyDenied': status.isPermanentlyDenied,
      };
    } catch (e) {
      return {
        'granted': false,
        'status': 'error',
        'error': e.toString(),
      };
    }
  }
  
  /// 이미지 선택
  Future<Map<String, dynamic>> pickImage({
    required String source,
    int maxWidth = 1920,
    int maxHeight = 1920,
    int quality = 85,
  }) async {
    ImageSource imageSource;
    if (source == 'camera') {
      imageSource = ImageSource.camera;
    } else {
      imageSource = ImageSource.gallery;
    }
    
    final XFile? image = await _picker.pickImage(
      source: imageSource,
      maxWidth: maxWidth.toDouble(),
      maxHeight: maxHeight.toDouble(),
      imageQuality: quality,
    );
    
    if (image == null) {
      throw Exception('No image selected');
    }
    
    final file = File(image.path);
    final bytes = await file.readAsBytes();
    
    return {
      'path': image.path,
      'name': image.name,
      'size': bytes.length,
      'mimeType': image.mimeType ?? 'image/jpeg',
      'base64': base64Encode(bytes),
    };
  }
  
  /// 여러 이미지 선택
  Future<List<Map<String, dynamic>>> pickMultipleImages({
    int maxImages = 10,
    int maxWidth = 1920,
    int maxHeight = 1920,
  }) async {
    final List<XFile> images = await _picker.pickMultiImage(
      maxWidth: maxWidth.toDouble(),
      maxHeight: maxHeight.toDouble(),
    );
    
    if (images.isEmpty) {
      return [];
    }
    
    return await Future.wait(
      images.take(maxImages).map((image) async {
        final file = File(image.path);
        final bytes = await file.readAsBytes();
        return {
          'path': image.path,
          'name': image.name,
          'size': bytes.length,
          'mimeType': image.mimeType ?? 'image/jpeg',
          'base64': base64Encode(bytes),
        };
      }),
    );
  }
}

