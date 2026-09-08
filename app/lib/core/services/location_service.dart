import 'dart:async';
import 'package:geolocator/geolocator.dart';
import 'package:permission_handler/permission_handler.dart';
import '../bridge/native_bridge.dart';

/// 위치 서비스
class LocationService {
  NativeBridge? bridge;
  StreamSubscription<Position>? _positionStream;
  
  LocationService();
  
  /// 위치 권한 요청
  Future<Map<String, dynamic>> requestPermission() async {
    final status = await Permission.location.request();
    return {
      'granted': status.isGranted,
      'status': status.toString(),
    };
  }
  
  /// 현재 위치 가져오기
  Future<Map<String, dynamic>> getCurrentLocation() async {
    bool serviceEnabled = await Geolocator.isLocationServiceEnabled();
    if (!serviceEnabled) {
      throw Exception('Location services are disabled');
    }
    
    LocationPermission permission = await Geolocator.checkPermission();
    if (permission == LocationPermission.denied) {
      permission = await Geolocator.requestPermission();
      if (permission == LocationPermission.denied) {
        throw Exception('Location permissions are denied');
      }
    }
    
    if (permission == LocationPermission.deniedForever) {
      throw Exception('Location permissions are permanently denied');
    }
    
    final position = await Geolocator.getCurrentPosition(
      locationSettings: const LocationSettings(accuracy: LocationAccuracy.high),
    );
    
    return {
      'latitude': position.latitude,
      'longitude': position.longitude,
      'accuracy': position.accuracy,
      'altitude': position.altitude,
      'speed': position.speed,
      'timestamp': position.timestamp.millisecondsSinceEpoch,
    };
  }
  
  /// 위치 추적 시작.
  ///
  /// PERF-016: [interval] 은 받아만 놓고 **무시되고 있었다**. React 가 interval:1000 을 요청해도
  /// distanceFilter(10m)만 걸린 채 모든 GPS 이벤트가 브리지로 넘어갔고, 이벤트 1건마다
  /// `runJavaScript` 문자열 평가가 WebView 메인 스레드에서 돌았다. 이제 요청한 주기대로 스로틀한다.
  /// (첫 이벤트는 지연 없이 즉시 전달한다.)
  ///
  /// [source]/[now]/[emit] 은 테스트 주입용이다.
  Future<void> startTracking({
    int interval = 1000,
    Stream<Position>? source,
    DateTime Function()? now,
    void Function(Map<String, dynamic> payload)? emit,
  }) async {
    if (_positionStream != null) {
      return; // 이미 추적 중
    }

    final clock = now ?? DateTime.now;
    final send = emit ??
        (Map<String, dynamic> payload) =>
            bridge?.sendEventToReact('locationUpdated', payload);
    DateTime? lastEmittedAt;

    final stream = source ??
        Geolocator.getPositionStream(
          locationSettings: const LocationSettings(
            accuracy: LocationAccuracy.high,
            distanceFilter: 10, // 10m 이상 이동 시 업데이트
          ),
        );

    _positionStream = stream.listen((position) {
      final at = clock();
      if (lastEmittedAt != null &&
          at.difference(lastEmittedAt!).inMilliseconds < interval) {
        return; // 요청 주기보다 촘촘한 이벤트는 버린다
      }
      lastEmittedAt = at;

      send({
        'latitude': position.latitude,
        'longitude': position.longitude,
        'accuracy': position.accuracy,
        'altitude': position.altitude,
        'speed': position.speed,
        'timestamp': position.timestamp.millisecondsSinceEpoch,
      });
    });
  }
  
  /// 위치 추적 중지
  Future<void> stopTracking() async {
    await _positionStream?.cancel();
    _positionStream = null;
  }
  
  void dispose() {
    stopTracking();
  }
}

