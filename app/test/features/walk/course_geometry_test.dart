// PERF-010 — 최적화된 CourseGeometry 가 기존(순진한) 알고리즘과 완전히 동일한 판정을 내는지 고정
import 'dart:math' as math;

import 'package:flutter_test/flutter_test.dart';
import 'package:geolocator/geolocator.dart';
import 'package:goldpet_app/features/walk/utils/course_geometry.dart';
import 'package:google_maps_flutter/google_maps_flutter.dart';

/// ── 참조 구현 ───────────────────────────────────────────────────────────────
/// CourseWalkScreen 에 있던 **원래 코드 그대로**. 최적화가 판정을 바꾸지 않았음을 증명하는 기준선이다.
/// (불변량 캐싱만 했으므로 결과는 부동소수점 수준까지 동일해야 한다.)

double _refDistanceToSegment(
  double px, double py,
  double ax, double ay,
  double bx, double by,
) {
  final segLen = Geolocator.distanceBetween(ax, ay, bx, by);
  if (segLen < 0.001) return Geolocator.distanceBetween(px, py, ax, ay);

  final dpa = Geolocator.distanceBetween(px, py, ax, ay);
  final dpb = Geolocator.distanceBetween(px, py, bx, by);

  final dab = segLen;
  if (dpa * dpa > dpb * dpb + dab * dab) return dpb;
  if (dpb * dpb > dpa * dpa + dab * dab) return dpa;

  final s = (dpa + dpb + dab) / 2;
  final area = math.sqrt(math.max(0, s * (s - dpa) * (s - dpb) * (s - dab)));
  return 2 * area / dab;
}

double _refDeviation(List<LatLng> path, double lat, double lng) {
  var minDist = double.infinity;
  for (var i = 0; i < path.length - 1; i++) {
    final d = _refDistanceToSegment(
      lat, lng,
      path[i].latitude, path[i].longitude,
      path[i + 1].latitude, path[i + 1].longitude,
    );
    if (d < minDist) minDist = d;
  }
  return minDist;
}

({int index, double progress}) _refProgress(
  List<LatLng> path,
  double lat,
  double lng, {
  required int waypointIndex,
  required double currentProgress,
}) {
  final totalSegments = path.length - 1;

  final searchStart = math.max(0, waypointIndex - 2);
  final maxLookahead = math.max(5, totalSegments ~/ 3);
  final searchEnd = math.min(waypointIndex + maxLookahead, totalSegments);
  var minDist = double.infinity;
  var nearestIdx = waypointIndex;

  for (var i = searchStart; i < searchEnd; i++) {
    final d = _refDistanceToSegment(
      lat, lng,
      path[i].latitude, path[i].longitude,
      path[i + 1].latitude, path[i + 1].longitude,
    );
    if (d < minDist) {
      minDist = d;
      nearestIdx = i;
    }
  }

  nearestIdx = math.max(nearestIdx, waypointIndex);

  double totalLength = 0;
  double progressLength = 0;
  for (var i = 0; i < path.length - 1; i++) {
    final segLen = Geolocator.distanceBetween(
      path[i].latitude, path[i].longitude,
      path[i + 1].latitude, path[i + 1].longitude,
    );
    totalLength += segLen;
    if (i < nearestIdx) {
      progressLength += segLen;
    } else if (i == nearestIdx) {
      final dToA = Geolocator.distanceBetween(
        lat, lng, path[i].latitude, path[i].longitude);
      final dToB = Geolocator.distanceBetween(
        lat, lng, path[i + 1].latitude, path[i + 1].longitude);
      final fraction = segLen > 0.001
          ? ((dToA * dToA + segLen * segLen - dToB * dToB) / (2 * segLen * segLen))
              .clamp(0.0, 1.0)
          : 0.0;
      progressLength += segLen * fraction;
    }
  }

  final newProgress =
      totalLength > 0 ? (progressLength / totalLength).clamp(0.0, 1.0) : 0.0;
  return (index: nearestIdx, progress: math.max(currentProgress, newProgress));
}

/// ── 테스트용 코스 생성 ──────────────────────────────────────────────────────

List<LatLng> _lineCourse(int n) => List<LatLng>.generate(
      n,
      (i) => LatLng(37.5000 + i * 0.0006, 127.0000 + i * 0.0004),
    );

/// 루프(원형) 코스 — 시작점 ≈ 끝점. 윈도우 검색이 존재하는 이유이기도 하다.
List<LatLng> _loopCourse(int n) => List<LatLng>.generate(n, (i) {
      final t = 2 * math.pi * i / (n - 1);
      return LatLng(37.5 + 0.003 * math.sin(t), 127.0 + 0.003 * math.cos(t));
    });

void main() {
  group('CourseGeometry — 기존 알고리즘과 동일한 판정 (PERF-010)', () {
    test('이탈 거리가 참조 구현과 정확히 일치한다 (직선 코스)', () {
      final path = _lineCourse(60);
      final geo = CourseGeometry(path);
      final rnd = math.Random(7);

      for (var k = 0; k < 200; k++) {
        // 코스 근처부터 한참 벗어난 지점까지 골고루
        final lat = 37.5 + rnd.nextDouble() * 0.04 - 0.005;
        final lng = 127.0 + rnd.nextDouble() * 0.03 - 0.005;
        expect(
          geo.deviationDistance(lat, lng),
          closeTo(_refDeviation(path, lat, lng), 1e-9),
          reason: '($lat, $lng) 에서 이탈 거리가 달라졌다',
        );
      }
    });

    test('이탈 거리가 참조 구현과 정확히 일치한다 (루프 코스)', () {
      final path = _loopCourse(80);
      final geo = CourseGeometry(path);
      final rnd = math.Random(11);

      for (var k = 0; k < 200; k++) {
        final lat = 37.5 + rnd.nextDouble() * 0.01 - 0.005;
        final lng = 127.0 + rnd.nextDouble() * 0.01 - 0.005;
        expect(
          geo.deviationDistance(lat, lng),
          closeTo(_refDeviation(path, lat, lng), 1e-9),
        );
      }
    });

    test('코스를 따라 걷는 전 구간에서 진행률·웨이포인트가 참조 구현과 일치한다', () {
      final path = _lineCourse(40);
      final geo = CourseGeometry(path);

      var refIdx = 0;
      var refProg = 0.0;
      var idx = 0;
      var prog = 0.0;

      // 코스 위를 세그먼트마다 3등분해 이동하며 GPS tick 을 흉내낸다.
      for (var i = 0; i < path.length - 1; i++) {
        for (var f = 0; f < 3; f++) {
          final t = f / 3;
          final lat = path[i].latitude + (path[i + 1].latitude - path[i].latitude) * t;
          final lng = path[i].longitude + (path[i + 1].longitude - path[i].longitude) * t;

          final ref = _refProgress(path, lat, lng,
              waypointIndex: refIdx, currentProgress: refProg);
          refIdx = ref.index;
          refProg = ref.progress;

          geo.deviationDistance(lat, lng); // 실제 tick 순서 (144 → 145행)
          final got = geo.updateProgress(lat, lng,
              currentWaypointIndex: idx,
              currentProgress: prog,
              reuseVertexDistances: true);
          idx = got.waypointIndex;
          prog = got.progress;

          expect(idx, refIdx, reason: 'waypointIndex 가 달라졌다 (i=$i, f=$f)');
          expect(prog, closeTo(refProg, 1e-9), reason: '진행률이 달라졌다 (i=$i, f=$f)');
        }
      }

      // 끝까지 걸으면 진행률이 100%에 가까워야 한다(계산이 살아있다는 최소 확인).
      expect(prog, greaterThan(0.9));
      expect(idx, greaterThan(30));
    });

    test('총 길이는 세그먼트 길이의 합과 같다 (매 tick 재계산 대신 1회 캐싱)', () {
      final path = _lineCourse(25);
      final geo = CourseGeometry(path);

      var expected = 0.0;
      for (var i = 0; i < path.length - 1; i++) {
        expected += Geolocator.distanceBetween(
          path[i].latitude, path[i].longitude,
          path[i + 1].latitude, path[i + 1].longitude,
        );
      }
      expect(geo.totalLength, closeTo(expected, 1e-9));
    });

    test('세그먼트가 2개 미만이면 안전하게 무시한다', () {
      final geo = CourseGeometry([const LatLng(37.5, 127.0)]);
      expect(geo.isUsable, isFalse);
      expect(geo.deviationDistance(37.5, 127.0), 0.0);

      final p = geo.updateProgress(37.5, 127.0,
          currentWaypointIndex: 3, currentProgress: 0.4);
      expect(p.waypointIndex, 3);
      expect(p.progress, 0.4);
    });
  });
}
