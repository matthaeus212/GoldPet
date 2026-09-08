// PERF-016 — startTracking 의 interval 파라미터가 실제로 브리지 전달을 스로틀하는지 검증
import 'dart:async';

import 'package:flutter_test/flutter_test.dart';
import 'package:geolocator/geolocator.dart';
import 'package:goldpet_app/core/services/location_service.dart';

Position _pos(double lat) => Position(
      latitude: lat,
      longitude: 127.0,
      timestamp: DateTime.fromMillisecondsSinceEpoch(0),
      accuracy: 5,
      altitude: 0,
      altitudeAccuracy: 0,
      heading: 0,
      headingAccuracy: 0,
      speed: 0,
      speedAccuracy: 0,
    );

void main() {
  group('LocationService.startTracking — interval 스로틀 (PERF-016)', () {
    late LocationService service;
    late StreamController<Position> source;
    late List<Map<String, dynamic>> emitted;
    late DateTime now;

    setUp(() {
      service = LocationService();
      source = StreamController<Position>();
      emitted = [];
      now = DateTime.fromMillisecondsSinceEpoch(0);
    });

    tearDown(() async {
      await service.stopTracking();
      await source.close();
    });

    Future<void> pump() => Future<void>.delayed(Duration.zero);

    // 회귀: interval 은 받아만 놓고 무시됐다. React 가 interval:1000 을 요청해도 모든 GPS
    // 이벤트가 브리지(runJavaScript 문자열 평가)로 넘어가 WebView 메인 스레드를 때렸다.
    test('요청 주기보다 촘촘한 이벤트는 전달하지 않는다', () async {
      await service.startTracking(
        interval: 1000,
        source: source.stream,
        now: () => now,
        emit: emitted.add,
      );

      source.add(_pos(37.5000)); // t=0 → 첫 이벤트는 즉시 전달
      await pump();

      now = DateTime.fromMillisecondsSinceEpoch(500);
      source.add(_pos(37.5001)); // 주기 미달 → 버려짐
      await pump();

      now = DateTime.fromMillisecondsSinceEpoch(900);
      source.add(_pos(37.5002)); // 여전히 미달 → 버려짐
      await pump();

      now = DateTime.fromMillisecondsSinceEpoch(1500);
      source.add(_pos(37.5003)); // 1000ms 경과 → 전달
      await pump();

      expect(emitted.length, 2);
      expect(emitted.first['latitude'], 37.5000);
      expect(emitted.last['latitude'], 37.5003);
    });

    test('첫 이벤트는 지연 없이 즉시 전달한다', () async {
      await service.startTracking(
        interval: 5000,
        source: source.stream,
        now: () => now,
        emit: emitted.add,
      );

      source.add(_pos(37.5));
      await pump();

      expect(emitted, hasLength(1));
    });

    test('interval 0 이면 모든 이벤트를 전달한다(스로틀 비활성)', () async {
      await service.startTracking(
        interval: 0,
        source: source.stream,
        now: () => now,
        emit: emitted.add,
      );

      source.add(_pos(37.5000));
      source.add(_pos(37.5001));
      source.add(_pos(37.5002));
      await pump();

      expect(emitted, hasLength(3));
    });

    test('전달되는 payload 형태는 기존과 같다', () async {
      await service.startTracking(
        interval: 1000,
        source: source.stream,
        now: () => now,
        emit: emitted.add,
      );
      source.add(_pos(37.5));
      await pump();

      expect(
        emitted.single.keys.toSet(),
        {'latitude', 'longitude', 'accuracy', 'altitude', 'speed', 'timestamp'},
      );
    });

    test('이미 추적 중이면 중복 구독하지 않는다', () async {
      await service.startTracking(
          interval: 1000, source: source.stream, now: () => now, emit: emitted.add);

      final second = StreamController<Position>();
      await service.startTracking(
          interval: 1000, source: second.stream, now: () => now, emit: emitted.add);

      expect(second.hasListener, isFalse);
      // 리스너가 없는 컨트롤러의 close() 는 완료되지 않으므로 await 하지 않는다.
      unawaited(second.close());
    });
  });
}
