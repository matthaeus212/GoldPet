import 'dart:async';
import 'dart:math' as math;
import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:google_maps_flutter/google_maps_flutter.dart';
import 'package:geolocator/geolocator.dart';
import 'package:flutter_svg/flutter_svg.dart';
import '../../../core/theme/app_theme.dart';
import '../utils/course_geometry.dart';
import '../../../core/webview/hybrid_webview.dart';
import '../../../core/widgets/goldpet_alert_dialog.dart';
import '../mixins/walk_tracking_mixin.dart';
import '../widgets/photo_memo_sheet.dart';
import '../widgets/walk_compass_indicator.dart';
// TODO: extract walkSpotActionController to walk_events.dart when a third walk screen type is added
// TODO: extract isAnyWalkActive() helper when third walk type added. Guard sites: api_client.dart (2), hybrid_webview.dart (1)
import 'walk_screen.dart' show walkSpotActionController;

/// Data model for a course spot passed from React WebView via bridge.
class CourseSpotData {
  final double latitude;
  final double longitude;
  final String type;
  final String? name;
  final String? description;

  const CourseSpotData({
    required this.latitude,
    required this.longitude,
    required this.type,
    this.name,
    this.description,
  });

  factory CourseSpotData.fromJson(Map<String, dynamic> json) {
    return CourseSpotData(
      latitude: (json['latitude'] as num).toDouble(),
      longitude: (json['longitude'] as num).toDouble(),
      type: json['type'] as String? ?? 'OTHER',
      name: json['name'] as String?,
      description: json['description'] as String?,
    );
  }
}

class CourseWalkScreen extends ConsumerStatefulWidget {
  final int courseId;
  final List<List<double>> coursePath; // [[lat,lng], ...]
  final List<CourseSpotData> courseSpots;
  final String? courseTitle;

  /// Singleton guard (same pattern as WalkScreen)
  static bool isOnStack = false;

  /// Set by HybridWebView when auth expires during an active course walk.
  /// The auth expiry dialog is deferred until the walk ends naturally.
  static bool authExpiredDuringWalk = false;

  const CourseWalkScreen({
    super.key,
    required this.courseId,
    required this.coursePath,
    this.courseSpots = const [],
    this.courseTitle,
  });

  @override
  ConsumerState<CourseWalkScreen> createState() => _CourseWalkScreenState();
}

class _CourseWalkScreenState extends ConsumerState<CourseWalkScreen>
    with WidgetsBindingObserver, WalkTrackingMixin {
  final Key _mapKey = UniqueKey();
  final DraggableScrollableController _sheetController = DraggableScrollableController();
  StreamSubscription<String>? _spotActionSubscription;
  String? _pendingSpotAction;

  // Course-specific state
  bool _isOffCourse = false;
  double _deviationDistance = 0.0;
  double _courseProgress = 0.0; // 0.0 ~ 1.0
  int _progressWaypointIndex = 0; // Sequential waypoint tracker for progress
  bool _completionShown = false;

  // Course path as LatLng for map overlay
  late final List<LatLng> _coursePathLatLng;
  /// PERF-010: 세그먼트 길이·누적 길이를 1회만 계산해 GPS tick 마다의 불변량 재계산을 없앤다.
  late final CourseGeometry _geometry;

  // Design colors
  static const Color _panelBg = Color(0xFFEFE1C4);
  static const Color _actionBarBg = Color(0xFFF9ECD2);
  static const Color _handleColor = Color(0xFFA58A54);
  static const Color _courseColor = Color(0xFF4A90D9); // Blue for course path
  static const double _headerToolbarHeight = 56.0;

  double get _bottomSafeArea => MediaQuery.of(context).padding.bottom;
  double get _headerTotalHeight => MediaQuery.of(context).padding.top + _headerToolbarHeight;
  double get _mapAreaHeight => MediaQuery.of(context).size.height - _headerTotalHeight;
  double get _collapsedSheetSize => (80.0 + _bottomSafeArea) / _mapAreaHeight;
  double get _expandedSheetSize => (260.0 + _bottomSafeArea) / _mapAreaHeight;

  // --- Mixin overrides ---

  @override
  dynamic get walkRef => ref;

  @override
  int? get followedCourseId => widget.courseId;

  @override
  void onWalkSaved() {
    // Handle deferred auth expiry (same pattern as WalkScreen)
    if (CourseWalkScreen.authExpiredDuringWalk) {
      CourseWalkScreen.authExpiredDuringWalk = false;
      Navigator.of(context).popUntil((route) => route.isFirst);
      return;
    }
    Navigator.of(context).pop();
    HybridWebView.ensureContentAlive?.call();
  }

  @override
  void onWalkComplete(int walkId) async {
    if (CourseWalkScreen.authExpiredDuringWalk) {
      onWalkSaved();
      return;
    }
    await HybridWebView.ensureContentAlive?.call();
    HybridWebView.sendEventToReact?.call('walkCompleted', {'walkId': walkId});
    if (!mounted) return;
    Navigator.of(context).pop();
  }

  @override
  Future<Map<String, dynamic>?> showPhotoMemoSheet(String photoPath) {
    return showModalBottomSheet<Map<String, dynamic>>(
      context: context,
      isScrollControlled: true,
      backgroundColor: Colors.transparent,
      builder: (ctx) => PhotoMemoSheet(photoPath: photoPath),
    );
  }

  @override
  void onLocationUpdate(Position position, LatLng latLng) {
    _checkDeviation(position);
    _updateProgress(latLng);
  }

  // --- Lifecycle ---

  @override
  void initState() {
    super.initState();
    CourseWalkScreen.isOnStack = true;
    WidgetsBinding.instance.addObserver(this);

    // Subscribe to global spot action stream (same pattern as WalkScreen)
    _spotActionSubscription = walkSpotActionController.stream.listen((type) {
      if (type == 'STOP') {
        if (trackingIsWalking) unawaited(stopWalk());
        return;
      }
      if (trackingIsWalking) {
        addSpot(type);
      } else {
        _pendingSpotAction = type;
      }
    });

    // Convert course path to LatLng
    _coursePathLatLng = widget.coursePath
        .map((p) => LatLng(p[0], p[1]))
        .toList();
    _geometry = CourseGeometry(_coursePathLatLng);

    initTracking(isPublic: true);
  }

  @override
  void dispose() {
    disposeTracking();
    CourseWalkScreen.isOnStack = false;
    CourseWalkScreen.authExpiredDuringWalk = false;
    WidgetsBinding.instance.removeObserver(this);
    _spotActionSubscription?.cancel();
    _sheetController.dispose();
    super.dispose();
  }

  @override
  void didChangeAppLifecycleState(AppLifecycleState state) {
    if (state == AppLifecycleState.resumed) handleAppResumed();
  }

  // --- Walk start: add course overlay after map is ready ---

  @override
  Future<void> startWalk() async {
    await super.startWalk();
    // Add course path overlay on map
    _addCourseOverlay();

    // Execute any pending spot action that arrived before walk started
    if (_pendingSpotAction != null) {
      final action = _pendingSpotAction!;
      _pendingSpotAction = null;
      Future.delayed(const Duration(seconds: 2), () {
        if (mounted && trackingIsWalking) addSpot(action);
      });
    }
  }

  void _addCourseOverlay() {
    if (trackingMapController == null || _coursePathLatLng.length < 2) return;

    // Course path polyline (blue, dashed)
    final coursePolyline = Polyline(
      polylineId: const PolylineId('course_path'),
      points: _coursePathLatLng,
      color: _courseColor.withValues(alpha: 0.6),
      width: 8,
      patterns: [PatternItem.dash(20), PatternItem.gap(10)],
    );

    // Course start marker
    final startMarker = Marker(
      markerId: const MarkerId('course_start'),
      position: _coursePathLatLng.first,
      icon: BitmapDescriptor.defaultMarkerWithHue(BitmapDescriptor.hueAzure),
      infoWindow: const InfoWindow(title: '출발'),
    );

    // Course end marker
    final endMarker = Marker(
      markerId: const MarkerId('course_end'),
      position: _coursePathLatLng.last,
      icon: BitmapDescriptor.defaultMarkerWithHue(BitmapDescriptor.hueRed),
      infoWindow: const InfoWindow(title: '도착'),
    );

    // Course spot markers
    final spotMarkers = <Marker>[];
    for (var i = 0; i < widget.courseSpots.length; i++) {
      final spot = widget.courseSpots[i];
      spotMarkers.add(Marker(
        markerId: MarkerId('course_spot_$i'),
        position: LatLng(spot.latitude, spot.longitude),
        icon: BitmapDescriptor.defaultMarkerWithHue(BitmapDescriptor.hueOrange),
        infoWindow: InfoWindow(title: spot.name ?? _getSpotEmoji(spot.type)),
      ));
    }

    setState(() {
      trackingPolylines = {...trackingPolylines, coursePolyline};
      trackingMarkers = {...trackingMarkers, startMarker, endMarker, ...spotMarkers};
    });

    // Fit camera to show course path
    if (_coursePathLatLng.length >= 2) {
      // Compute bounds manually
      double minLat = _coursePathLatLng.first.latitude;
      double maxLat = _coursePathLatLng.first.latitude;
      double minLng = _coursePathLatLng.first.longitude;
      double maxLng = _coursePathLatLng.first.longitude;
      for (final p in _coursePathLatLng) {
        if (p.latitude < minLat) minLat = p.latitude;
        if (p.latitude > maxLat) maxLat = p.latitude;
        if (p.longitude < minLng) minLng = p.longitude;
        if (p.longitude > maxLng) maxLng = p.longitude;
      }
      final bounds = LatLngBounds(
        southwest: LatLng(minLat, minLng),
        northeast: LatLng(maxLat, maxLng),
      );
      animateCameraGuarded(
        CameraUpdate.newLatLngBounds(bounds, 60),
      );
    }
  }

  String _getSpotEmoji(String type) {
    return switch (type) {
      'WATER_FOUNTAIN' => '💧',
      'REST_AREA' => '🪑',
      'TRASH_CAN' => '🗑️',
      'DANGER_ZONE' => '⚠️',
      'SCENIC_POINT' => '📸',
      'CONVENIENCE_STORE' => '🏪',
      'CAFE' => '☕',
      'PARK_ENTRANCE' => '🌳',
      'CROSSWALK' => '🚶',
      'PHOTO_SPOT' => '📷',
      _ => '📍',
    };
  }

  // --- Deviation detection ---

  void _checkDeviation(Position position) {
    if (!_geometry.isUsable) return;

    // PERF-010: 전역 최소 거리(기존 동작 그대로). 세그먼트 길이는 캐시돼 있고, 꼭짓점까지의
    // 거리는 이 호출에서 한 번만 계산해 바로 뒤의 _updateProgress 가 재사용한다.
    final minDist = _geometry.deviationDistance(
      position.latitude,
      position.longitude,
    );
    _deviationDistance = minDist;

    // Dynamic threshold: max(50m, accuracy * 2.5)
    final threshold = math.max(50.0, position.accuracy * 2.5);
    final wasOffCourse = _isOffCourse;
    _isOffCourse = minDist > threshold;

    if (_isOffCourse && !wasOffCourse && mounted) {
      // Just went off course - show alert
      showTrackingSnackBar('코스에서 벗어났습니다! (${minDist.toStringAsFixed(0)}m)');
    }

    if (mounted) setState(() {});
  }

  // --- Progress tracking (segment projection) ---

  void _updateProgress(LatLng latLng) {
    if (!_geometry.isUsable) return;

    // Don't update progress when significantly off course (>200m)
    if (_deviationDistance > 200) {
      if (mounted) setState(() {});
      return;
    }

    // PERF-010: 윈도우 검색 범위·후퇴 금지·비율 계산은 기존과 동일하다. 달라진 것은 매 tick
    // 코스 전체를 돌며 총 길이를 다시 합산하지 않는다는 점뿐이다(누적 길이 캐시).
    // _checkDeviation 이 방금 같은 좌표로 채운 꼭짓점 거리 버퍼를 재사용한다.
    final result = _geometry.updateProgress(
      latLng.latitude,
      latLng.longitude,
      currentWaypointIndex: _progressWaypointIndex,
      currentProgress: _courseProgress,
      reuseVertexDistances: true,
    );
    _progressWaypointIndex = result.waypointIndex;
    _courseProgress = result.progress;

    // Check completion: within 100m of course end point
    final distToEnd = Geolocator.distanceBetween(
      latLng.latitude, latLng.longitude,
      _coursePathLatLng.last.latitude, _coursePathLatLng.last.longitude,
    );

    if (distToEnd < 10 && !_completionShown && trackingIsWalking
        && _courseProgress >= 0.95
        && trackingDurationNotifier.value >= 60) {
      _completionShown = true;
      _showCompletionDialog();
    }

    if (mounted) setState(() {});
  }

  Future<void> _showCompletionDialog() async {
    if (!mounted) return;
    final shouldStop = await showGoldPetConfirm(
      context,
      message: '코스를 완주했습니다! 🎉\n산책을 종료하시겠습니까?',
      confirmText: '종료',
      cancelText: '계속 산책',
    );
    if (shouldStop && mounted) {
      await stopWalk();
    }
  }

  // --- Navigation ---

  Future<void> _handleBackNavigation() async {
    if (trackingIsSaving) return;
    if (trackingIsWalking) {
      final confirmed = await showStopWalkDialog();
      if (confirmed) await stopWalk();
    } else {
      Navigator.of(context).pop();
    }
  }

  // --- Build ---

  @override
  Widget build(BuildContext context) {
    return PopScope(
      canPop: !trackingIsWalking && !trackingIsSaving,
      onPopInvokedWithResult: (didPop, _) {
        if (!didPop) _handleBackNavigation();
      },
      child: Scaffold(
        resizeToAvoidBottomInset: false,
        body: Column(
          children: [
            _buildHeader(),
            Expanded(
              child: Stack(
                children: [
                  // Map
                  Positioned.fill(
                    child: trackingIsInitializing
                        ? const Center(child: CircularProgressIndicator())
                        : GoogleMap(
                            key: _mapKey,
                            initialCameraPosition: CameraPosition(
                              target: trackingInitialPosition ?? const LatLng(37.5665, 126.9780),
                              zoom: 16,
                            ),
                            myLocationEnabled: true,
                            myLocationButtonEnabled: false,
                            zoomControlsEnabled: false,
                            mapToolbarEnabled: false,
                            compassEnabled: false,
                            onMapCreated: (controller) {
                              trackingMapController = controller;
                              // Course overlay may have been skipped if startWalk()
                              // ran before the map was ready — add it now.
                              if (trackingIsWalking) {
                                _addCourseOverlay();
                              }
                            },
                            onCameraMoveStarted: () {
                              onCameraMoveStartedByUser();
                            },
                            onCameraMove: onCameraMoveHandler,
                            onCameraIdle: onCameraIdleHandler,
                            markers: trackingMarkers,
                            polylines: trackingPolylines,
                          ),
                  ),

                  // Deviation warning banner
                  if (trackingIsWalking && _isOffCourse)
                    Positioned(
                      top: 12,
                      left: 16,
                      right: 16,
                      child: _DeviationBanner(distance: _deviationDistance),
                    ),

                  // Compass indicator — hidden when off-course to avoid
                  // collision with the deviation banner (plan §9 Risk #4).
                  if (trackingIsWalking &&
                      trackingCompassModeEnabled &&
                      !_isOffCourse)
                    Positioned(
                      top: 12,
                      left: 16,
                      child: ValueListenableBuilder<double>(
                        valueListenable: trackingBearingNotifier,
                        builder: (context, bearing, _) =>
                            WalkCompassIndicator(bearing: bearing),
                      ),
                    ),

                  // Location chip
                  if (trackingIsWalking && trackingCurrentAddress.isNotEmpty && !_isOffCourse)
                    Positioned(
                      top: 12,
                      right: 16,
                      child: _CourseLocationChip(address: trackingCurrentAddress),
                    ),

                  // Info panel with progress bar
                  if (trackingIsWalking)
                    _buildInfoPanel(),

                  // Walk controls
                  if (trackingIsWalking)
                    _buildWalkControls(),

                  // Saving overlay
                  if (trackingIsSaving)
                    Positioned.fill(
                      child: Container(
                        color: Colors.black.withValues(alpha: 0.3),
                        child: const Center(
                          child: Column(
                            mainAxisSize: MainAxisSize.min,
                            children: [
                              CircularProgressIndicator(color: Colors.white),
                              SizedBox(height: 16),
                              Text(
                                '산책 기록 저장 중...',
                                style: TextStyle(color: Colors.white, fontSize: 16, fontWeight: FontWeight.w600),
                              ),
                            ],
                          ),
                        ),
                      ),
                    ),
                ],
              ),
            ),
          ],
        ),
      ),
    );
  }

  Widget _buildHeader() {
    return Container(
      color: AppColors.background,
      child: SafeArea(
        bottom: false,
        child: SizedBox(
          height: _headerToolbarHeight,
          child: Row(
            children: [
              GestureDetector(
                onTap: _handleBackNavigation,
                child: Padding(
                  padding: const EdgeInsets.only(left: 16),
                  child: Icon(Icons.arrow_back_ios_new, size: 20, color: AppColors.primary),
                ),
              ),
              const SizedBox(width: 12),
              Expanded(
                child: Text(
                  widget.courseTitle ?? '코스 따라가기',
                  style: const TextStyle(
                    fontSize: 16,
                    fontWeight: FontWeight.w600,
                    color: AppColors.primary,
                  ),
                  overflow: TextOverflow.ellipsis,
                ),
              ),
            ],
          ),
        ),
      ),
    );
  }

  Widget _buildInfoPanel() {
    return DraggableScrollableSheet(
      controller: _sheetController,
      initialChildSize: _expandedSheetSize,
      minChildSize: _collapsedSheetSize,
      maxChildSize: _expandedSheetSize,
      snap: true,
      snapSizes: [_collapsedSheetSize, _expandedSheetSize],
      builder: (context, scrollController) {
        return Container(
          decoration: const BoxDecoration(
            color: _panelBg,
            borderRadius: BorderRadius.vertical(top: Radius.circular(16)),
            boxShadow: [BoxShadow(color: Colors.black12, blurRadius: 8, offset: Offset(0, -2))],
          ),
          child: SingleChildScrollView(
            controller: scrollController,
            child: Column(
              children: [
                // Drag handle (tap to toggle)
                GestureDetector(
                  onTap: () {
                    final isExpanded = _sheetController.size >= _expandedSheetSize - 0.01;
                    _sheetController.animateTo(
                      isExpanded ? _collapsedSheetSize : _expandedSheetSize,
                      duration: const Duration(milliseconds: 300),
                      curve: Curves.easeInOut,
                    );
                  },
                  behavior: HitTestBehavior.opaque,
                  child: Container(
                    padding: const EdgeInsets.symmetric(vertical: 12),
                    child: Center(
                      child: Container(
                        width: 40, height: 4,
                        decoration: BoxDecoration(color: _handleColor, borderRadius: BorderRadius.circular(2)),
                      ),
                    ),
                  ),
                ),
                // Course progress bar
                Padding(
                  padding: const EdgeInsets.symmetric(horizontal: 24),
                  child: Column(
                    crossAxisAlignment: CrossAxisAlignment.start,
                    children: [
                      Row(
                        mainAxisAlignment: MainAxisAlignment.spaceBetween,
                        children: [
                          Text(
                            '코스 진행률',
                            style: TextStyle(fontSize: 11, color: _handleColor, fontWeight: FontWeight.w500),
                          ),
                          Text(
                            '${(_courseProgress * 100).toStringAsFixed(0)}%',
                            style: TextStyle(fontSize: 11, color: _courseColor, fontWeight: FontWeight.w700),
                          ),
                        ],
                      ),
                      const SizedBox(height: 6),
                      ClipRRect(
                        borderRadius: BorderRadius.circular(4),
                        child: LinearProgressIndicator(
                          value: _courseProgress,
                          backgroundColor: Colors.white.withValues(alpha: 0.5),
                          valueColor: const AlwaysStoppedAnimation<Color>(_courseColor),
                          minHeight: 6,
                        ),
                      ),
                    ],
                  ),
                ),
                const SizedBox(height: 12),
                // Stats row
                Padding(
                  padding: const EdgeInsets.symmetric(horizontal: 16),
                  child: Row(
                    children: [
                      Expanded(
                        child: ValueListenableBuilder<double>(
                          valueListenable: trackingDistanceNotifier,
                          builder: (_, distance, child) => _CourseStat(
                            value: distance.toStringAsFixed(2),
                            unit: 'km',
                            label: '거리',
                          ),
                        ),
                      ),
                      Expanded(
                        child: ValueListenableBuilder<int>(
                          valueListenable: trackingDurationNotifier,
                          builder: (_, seconds, child) => _CourseStat(
                            value: formatDuration(seconds),
                            label: '시간',
                          ),
                        ),
                      ),
                      Expanded(
                        child: ValueListenableBuilder<double>(
                          valueListenable: trackingDistanceNotifier,
                          builder: (_, distance, child) => _CourseStat(
                            value: (distance * 50).toStringAsFixed(0),
                            unit: 'Kcal',
                            label: '칼로리',
                          ),
                        ),
                      ),
                    ],
                  ),
                ),
                const SizedBox(height: 16),
                // Spot action buttons
                Padding(
                  padding: const EdgeInsets.symmetric(horizontal: 16),
                  child: Container(
                    padding: const EdgeInsets.symmetric(vertical: 12, horizontal: 8),
                    decoration: BoxDecoration(color: _actionBarBg, borderRadius: BorderRadius.circular(12)),
                    child: Row(
                      mainAxisAlignment: MainAxisAlignment.spaceEvenly,
                      children: [
                        _CourseSpotButton(label: '응가', svgAsset: 'assets/images/walk/icon_poop.svg', onTap: () => addSpot('POOP')),
                        _CourseSpotButton(label: '쉬', svgAsset: 'assets/images/walk/icon_pee.svg', onTap: () => addSpot('PEE')),
                        _CourseSpotButton(label: '사진', svgAsset: 'assets/images/walk/icon_camera.svg', onTap: () => addSpot('PHOTO')),
                      ],
                    ),
                  ),
                ),
                SizedBox(height: _bottomSafeArea),
              ],
            ),
          ),
        );
      },
    );
  }

  Widget _buildWalkControls() {
    return ListenableBuilder(
      listenable: _sheetController,
      builder: (context, child) {
        double sheetHeight;
        try {
          sheetHeight = _sheetController.size * _mapAreaHeight;
        } catch (_) {
          sheetHeight = _expandedSheetSize * _mapAreaHeight;
        }
        return Positioned(right: 16, bottom: sheetHeight + 16, child: child!);
      },
      child: Row(
        mainAxisSize: MainAxisSize.min,
        children: [
          // Stop button
          GestureDetector(
            onTap: () async {
              final confirmed = await showStopWalkDialog();
              if (confirmed) await stopWalk();
            },
            child: Container(
              width: 48, height: 48,
              decoration: BoxDecoration(
                color: AppColors.primary,
                shape: BoxShape.circle,
                boxShadow: [BoxShadow(color: const Color(0xFF353D4C).withValues(alpha: 0.1), blurRadius: 20, offset: const Offset(0, 12))],
              ),
              child: Center(
                child: Container(
                  width: 14, height: 14,
                  decoration: BoxDecoration(color: _handleColor, borderRadius: BorderRadius.circular(1)),
                ),
              ),
            ),
          ),
          const SizedBox(width: 10),
          // Compass button (3-state)
          GestureDetector(
            onTap: () async {
              final isOnPaused = trackingCompassModeEnabled && !trackingIsFollowing;
              if (isOnPaused) {
                recenterCamera();
              } else {
                await toggleCompassMode();
                if (trackingCompassModeEnabled && !trackingIsFollowing) {
                  recenterCamera();
                }
              }
            },
            child: Container(
              width: 48,
              height: 48,
              decoration: BoxDecoration(
                color: trackingCompassModeEnabled && !trackingIsFollowing
                    ? AppColors.primary.withValues(alpha: 0.5)
                    : AppColors.primary,
                shape: BoxShape.circle,
                boxShadow: [BoxShadow(color: const Color(0xFF353D4C).withValues(alpha: 0.1), blurRadius: 20, offset: const Offset(0, 12))],
              ),
              child: Center(
                child: Icon(
                  trackingCompassModeEnabled && trackingIsFollowing
                      ? Icons.explore
                      : Icons.explore_outlined,
                  size: 22,
                  color: trackingCompassModeEnabled && trackingIsFollowing
                      ? Colors.white
                      : _handleColor,
                ),
              ),
            ),
          ),
          const SizedBox(width: 10),
          // Pause button
          GestureDetector(
            onTap: togglePause,
            child: Container(
              width: 48, height: 48,
              decoration: BoxDecoration(
                color: AppColors.primary,
                shape: BoxShape.circle,
                boxShadow: [BoxShadow(color: const Color(0xFF353D4C).withValues(alpha: 0.1), blurRadius: 20, offset: const Offset(0, 12))],
              ),
              child: Center(
                child: trackingIsPaused
                    ? CustomPaint(size: const Size(14, 14), painter: _CoursePlayIconPainter(color: _handleColor))
                    : Row(
                        mainAxisSize: MainAxisSize.min,
                        children: [
                          Container(width: 6, height: 14, decoration: BoxDecoration(color: _handleColor, borderRadius: BorderRadius.circular(1))),
                          const SizedBox(width: 3),
                          Container(width: 6, height: 14, decoration: BoxDecoration(color: _handleColor, borderRadius: BorderRadius.circular(1))),
                        ],
                      ),
              ),
            ),
          ),
        ],
      ),
    );
  }
}

// --- Course-specific widgets ---

class _DeviationBanner extends StatelessWidget {
  final double distance;
  const _DeviationBanner({required this.distance});

  @override
  Widget build(BuildContext context) {
    return Container(
      padding: const EdgeInsets.symmetric(horizontal: 16, vertical: 10),
      decoration: BoxDecoration(
        color: const Color(0xFFFFF3CD),
        borderRadius: BorderRadius.circular(12),
        border: Border.all(color: const Color(0xFFFFD700)),
        boxShadow: const [BoxShadow(color: Colors.black12, blurRadius: 4)],
      ),
      child: Row(
        children: [
          const Icon(Icons.warning_amber_rounded, color: Color(0xFFFF8C00), size: 20),
          const SizedBox(width: 8),
          Expanded(
            child: Text(
              '코스에서 ${distance.toStringAsFixed(0)}m 벗어났습니다',
              style: const TextStyle(fontSize: 13, fontWeight: FontWeight.w600, color: Color(0xFF856404)),
            ),
          ),
        ],
      ),
    );
  }
}

class _CourseLocationChip extends StatelessWidget {
  final String address;
  const _CourseLocationChip({required this.address});

  @override
  Widget build(BuildContext context) {
    return Container(
      padding: const EdgeInsets.symmetric(horizontal: 12, vertical: 8),
      decoration: BoxDecoration(
        color: Colors.white,
        borderRadius: BorderRadius.circular(20),
        boxShadow: const [BoxShadow(color: Colors.black12, blurRadius: 4)],
      ),
      child: Row(
        mainAxisSize: MainAxisSize.min,
        children: [
          const Icon(Icons.location_on, size: 16, color: AppColors.primary),
          const SizedBox(width: 4),
          ConstrainedBox(
            constraints: const BoxConstraints(maxWidth: 200),
            child: Text(address, style: const TextStyle(fontSize: 12, color: AppColors.textPrimary), overflow: TextOverflow.ellipsis),
          ),
        ],
      ),
    );
  }
}

class _CourseStat extends StatelessWidget {
  final String value;
  final String? unit;
  final String label;
  const _CourseStat({required this.value, this.unit, required this.label});

  @override
  Widget build(BuildContext context) {
    return Column(
      children: [
        Row(
          mainAxisAlignment: MainAxisAlignment.center,
          crossAxisAlignment: CrossAxisAlignment.baseline,
          textBaseline: TextBaseline.alphabetic,
          children: [
            Flexible(
              child: FittedBox(
                fit: BoxFit.scaleDown,
                child: Text(value, style: const TextStyle(fontSize: 18, fontWeight: FontWeight.bold, color: AppColors.primary)),
              ),
            ),
            if (unit != null) ...[
              const SizedBox(width: 2),
              Text(unit!, style: const TextStyle(fontSize: 11, fontWeight: FontWeight.w500, color: AppColors.primary)),
            ],
          ],
        ),
        const SizedBox(height: 4),
        Text(label, style: const TextStyle(fontSize: 10, color: Color(0xFF727272))),
      ],
    );
  }
}

class _CourseSpotButton extends StatelessWidget {
  final String label;
  final String svgAsset;
  final VoidCallback onTap;
  const _CourseSpotButton({required this.label, required this.svgAsset, required this.onTap});

  @override
  Widget build(BuildContext context) {
    return InkWell(
      onTap: onTap,
      borderRadius: BorderRadius.circular(12),
      child: Padding(
        padding: const EdgeInsets.symmetric(horizontal: 16, vertical: 4),
        child: Column(
          children: [
            SizedBox(width: 40, height: 40, child: SvgPicture.asset(svgAsset)),
            const SizedBox(height: 8),
            Text(label, style: const TextStyle(fontSize: 10, fontWeight: FontWeight.w500, color: Color(0xFF727272))),
          ],
        ),
      ),
    );
  }
}

class _CoursePlayIconPainter extends CustomPainter {
  final Color color;
  _CoursePlayIconPainter({required this.color});

  @override
  void paint(Canvas canvas, Size size) {
    final paint = Paint()..color = color..style = PaintingStyle.fill;
    final path = Path()
      ..moveTo(0, 0)
      ..lineTo(size.width, size.height / 2)
      ..lineTo(0, size.height)
      ..close();
    canvas.drawPath(path, paint);
  }

  @override
  bool shouldRepaint(covariant CustomPainter oldDelegate) => false;
}
