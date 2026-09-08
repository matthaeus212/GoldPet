// 코스 이탈 거리·진행률 계산을 담당하는 순수 기하 모듈 (GPS tick 핫패스)
import 'dart:math' as math;

import 'package:geolocator/geolocator.dart';
import 'package:google_maps_flutter/google_maps_flutter.dart';

/// 진행률 계산 결과.
class CourseProgress {
  const CourseProgress({required this.waypointIndex, required this.progress});

  /// 현재 진행 중인 세그먼트 인덱스(뒤로 후퇴하지 않음).
  final int waypointIndex;

  /// 0.0 ~ 1.0
  final double progress;
}

/// 코스 경로에 대한 기하 계산.
///
/// PERF-010: 원래 이 계산은 CourseWalkScreen 안에 있었고, **GPS tick 마다 불변량을 다시 계산**했다.
///  - `_distanceToSegment` 가 세그먼트마다 `Geolocator.distanceBetween(a, b)` 로 세그먼트 길이를
///    구했다. 코스는 고정이므로 이 값은 상수다.
///  - `_updateProgress` 가 매 tick 코스 전체를 돌며 총 길이를 다시 합산했다. 이것도 상수다.
///  - 인접 세그먼트가 공유하는 꼭짓점까지의 거리(dpa/dpb)를 세그먼트마다 중복 계산했다.
///
/// 그래서 tick 당 Haversine 이 세그먼트 수의 약 4배 발생했다. 세그먼트 길이·누적 길이를 생성 시
/// 한 번만 계산하고, 꼭짓점까지의 거리를 tick 당 한 번만 계산하면 약 N+1 회로 줄어든다.
///
/// **판정은 1비트도 바꾸지 않는다.** 이탈 거리는 여전히 전체 세그먼트에 대한 전역 최소값이다.
/// (윈도우 검색으로 바꾸면 루프 코스에서 이탈 판정 자체가 달라진다 — 성능 개선이 아니라 동작 변경이므로
/// 하지 않는다. course_geometry_test.dart 가 기존 알고리즘 참조 구현과의 동일성을 고정한다.)
class CourseGeometry {
  CourseGeometry(List<LatLng> path)
      : points = List.unmodifiable(path),
        _segmentLengths = _computeSegmentLengths(path),
        _cumulativeLengths = List.filled(math.max(path.length, 1), 0.0) {
    for (var i = 0; i < _segmentLengths.length; i++) {
      _cumulativeLengths[i + 1] = _cumulativeLengths[i] + _segmentLengths[i];
    }
  }

  final List<LatLng> points;
  final List<double> _segmentLengths;
  final List<double> _cumulativeLengths;

  /// tick 당 한 번만 채우는 꼭짓점까지의 거리 버퍼(재사용해 할당을 피한다).
  final List<double> _vertexDistances = [];

  int get segmentCount => math.max(0, points.length - 1);

  /// 코스 총 길이(m). 생성 시 1회 계산.
  double get totalLength =>
      _cumulativeLengths.isEmpty ? 0.0 : _cumulativeLengths.last;

  bool get isUsable => points.length >= 2;

  static List<double> _computeSegmentLengths(List<LatLng> path) {
    if (path.length < 2) return const [];
    return List<double>.generate(
      path.length - 1,
      (i) => Geolocator.distanceBetween(
        path[i].latitude,
        path[i].longitude,
        path[i + 1].latitude,
        path[i + 1].longitude,
      ),
      growable: false,
    );
  }

  /// 현재 위치에서 각 꼭짓점까지의 거리를 tick 당 한 번 계산한다.
  /// 인접 세그먼트가 공유하는 값(세그먼트 i 의 끝점 = i+1 의 시작점)을 중복 계산하지 않게 한다.
  void _fillVertexDistances(double lat, double lng) {
    if (_vertexDistances.length != points.length) {
      _vertexDistances
        ..clear()
        ..addAll(List<double>.filled(points.length, 0.0));
    }
    for (var i = 0; i < points.length; i++) {
      _vertexDistances[i] = Geolocator.distanceBetween(
        lat,
        lng,
        points[i].latitude,
        points[i].longitude,
      );
    }
  }

  /// 점-세그먼트 거리(m). 꼭짓점까지의 거리와 세그먼트 길이가 이미 계산돼 있다고 가정한다.
  /// 공식은 기존 `_distanceToSegment` 와 동일하다(둔각이면 꼭짓점, 아니면 헤론 공식 수선 거리).
  double _segmentDistance(int i) {
    final segLen = _segmentLengths[i];
    final dpa = _vertexDistances[i];
    final dpb = _vertexDistances[i + 1];

    if (segLen < 0.001) return dpa;
    if (dpa * dpa > dpb * dpb + segLen * segLen) return dpb; // B 쪽 둔각
    if (dpb * dpb > dpa * dpa + segLen * segLen) return dpa; // A 쪽 둔각

    final s = (dpa + dpb + segLen) / 2;
    final area = math.sqrt(math.max(0, s * (s - dpa) * (s - dpb) * (s - segLen)));
    return 2 * area / segLen;
  }

  /// 코스에서 벗어난 거리(m) — 전체 세그먼트에 대한 **전역** 최소값(기존 동작 유지).
  ///
  /// 호출 후 [updateProgress] 를 같은 좌표로 이어서 부르면 꼭짓점 거리 버퍼를 재사용한다.
  double deviationDistance(double lat, double lng) {
    if (!isUsable) return 0.0;
    _fillVertexDistances(lat, lng);

    var minDist = double.infinity;
    for (var i = 0; i < segmentCount; i++) {
      final d = _segmentDistance(i);
      if (d < minDist) minDist = d;
    }
    return minDist;
  }

  /// 진행률 계산. 탐색 범위는 기존과 동일한 윈도우(뒤 2, 앞 max(5, N/3))다.
  ///
  /// [reuseVertexDistances] 가 true 면 직전 [deviationDistance] 호출이 채운 버퍼를 재사용한다
  /// (같은 tick·같은 좌표로 연속 호출되는 경로 최적화).
  CourseProgress updateProgress(
    double lat,
    double lng, {
    required int currentWaypointIndex,
    required double currentProgress,
    bool reuseVertexDistances = false,
  }) {
    if (!isUsable) {
      return CourseProgress(
        waypointIndex: currentWaypointIndex,
        progress: currentProgress,
      );
    }
    if (!reuseVertexDistances || _vertexDistances.length != points.length) {
      _fillVertexDistances(lat, lng);
    }

    // 루프 코스에서 시작점 근처가 끝 세그먼트와 매칭되는 것을 막는 윈도우 검색(기존 로직 그대로).
    final searchStart = math.max(0, currentWaypointIndex - 2);
    final maxLookahead = math.max(5, segmentCount ~/ 3);
    final searchEnd = math.min(currentWaypointIndex + maxLookahead, segmentCount);

    var minDist = double.infinity;
    var nearestIdx = currentWaypointIndex;
    for (var i = searchStart; i < searchEnd; i++) {
      final d = _segmentDistance(i);
      if (d < minDist) {
        minDist = d;
        nearestIdx = i;
      }
    }

    // 후퇴 금지(기존 동작).
    nearestIdx = math.max(nearestIdx, currentWaypointIndex);

    // 완료 세그먼트 합 + 현재 세그먼트 내 비율. 누적 길이가 있으니 전체를 다시 돌지 않는다.
    final segLen = _segmentLengths[nearestIdx];
    final dToA = _vertexDistances[nearestIdx];
    final dToB = _vertexDistances[nearestIdx + 1];
    final fraction = segLen > 0.001
        ? ((dToA * dToA + segLen * segLen - dToB * dToB) / (2 * segLen * segLen))
            .clamp(0.0, 1.0)
        : 0.0;
    final progressLength = _cumulativeLengths[nearestIdx] + segLen * fraction;

    final newProgress =
        totalLength > 0 ? (progressLength / totalLength).clamp(0.0, 1.0) : 0.0;

    return CourseProgress(
      waypointIndex: nearestIdx,
      // 진행률은 후퇴하지 않는다(기존 동작).
      progress: math.max(currentProgress, newProgress),
    );
  }
}
