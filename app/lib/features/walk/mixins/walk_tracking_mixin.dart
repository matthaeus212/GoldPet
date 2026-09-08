import 'dart:async';
import 'dart:io';
import 'dart:ui' as ui;
import 'package:flutter/material.dart';
import 'package:flutter/services.dart';
import 'package:google_maps_flutter/google_maps_flutter.dart';
import 'package:geolocator/geolocator.dart';
import 'package:permission_handler/permission_handler.dart';
import 'package:image_picker/image_picker.dart';
import '../widgets/photo_crop_screen.dart';
import 'package:dio/dio.dart';
import 'package:http/http.dart' as http;
import '../../../core/theme/app_theme.dart';
import '../../../core/api/api_client.dart';
import '../data/walk_models.dart';
import '../data/walk_repository.dart';
import '../data/walk_local_storage.dart';
import 'package:shared_preferences/shared_preferences.dart';
import '../../../core/widgets/goldpet_alert_dialog.dart';

/// Mixin that provides common walk tracking functionality:
/// - GPS location tracking with 3-tier filtering (accuracy, distance, speed)
/// - Timer with pause/resume support
/// - Path accumulation and distance calculation
/// - Spot recording (pee, poop, photo with camera/crop/upload)
/// - Pet marker rendering at start position
/// - Periodic local storage backup
/// - Walk widget (Android foreground service / iOS Live Activity)
///
/// Consumers must:
/// - Mix into a `State<T>` with WidgetsBindingObserver
/// - Call [initTracking] in initState (after super)
/// - Call [disposeTracking] in dispose (before super)
/// - Override [onWalkSaved] for post-save navigation
/// - Provide [walkRef] for Riverpod access
mixin WalkTrackingMixin<T extends StatefulWidget> on State<T> {
  // --- State ---
  bool trackingIsWalking = false;
  bool trackingIsSaving = false;
  bool trackingIsInitializing = true;
  bool trackingIsPaused = false;

  GoogleMapController? trackingMapController;
  LatLng? trackingInitialPosition;

  /// Follow-mode state: when true, camera follows the user's location.
  /// Set to false when user manually drags the map.
  bool trackingIsFollowing = true;

  /// Compass mode state: map rotates to device heading when ON.
  /// Loaded from SharedPreferences in [startWalk]; default OFF.
  bool trackingCompassModeEnabled = false;
  /// Public bearing notifier for the custom compass indicator widget.
  /// 0.0 = north-up (first frame before any GPS tick).
  final ValueNotifier<double> trackingBearingNotifier = ValueNotifier<double>(0.0);

  /// Last smoothed bearing applied to the camera. `null` = unknown.
  double? _trackingLastBearing;

  /// Mirrors the GoogleMap widget's initial zoom in both walk screens.
  /// Kept in sync via [onCameraMoveHandler] so pinch-zoom is preserved
  /// across bearing-driven CameraPosition rebuilds.
  double _trackingLastZoom = 16.0;

  /// Guards against our own [animateCamera] calls masquerading as user pans.
  /// Cleared in [onCameraIdleHandler] and by a defensive 500 ms timeout.
  bool _trackingProgrammaticMove = false;
  Timer? _trackingProgrammaticMoveTimer;

  static const String _kPrefCompassMode = 'walk_compass_mode_enabled';

  DateTime? _trackingStartTime;
  Timer? _trackingTimer;
  DateTime? _trackingPauseStartTime;
  Duration _trackingTotalPauseDuration = Duration.zero;

  final ValueNotifier<int> trackingDurationNotifier = ValueNotifier(0);
  final ValueNotifier<double> trackingDistanceNotifier = ValueNotifier(0.0);

  List<LatLng> trackingPath = [];
  final List<WalkSpotDto> trackingSpots = [];

  /// Declarative marker/polyline sets for GoogleMap widget.
  Set<Marker> trackingMarkers = {};
  Set<Polyline> trackingPolylines = {};

  StreamSubscription<Position>? _trackingPositionSub;
  Position? _trackingLastAccepted;

  String trackingCurrentAddress = '';
  DateTime? _trackingLastGeocodingTime;
  String? _trackingPetImageUrl;
  List<int> _trackingPetIds = [];
  List<Map<String, dynamic>> _trackingPets = [];
  List<int>? _externalPetIds;
  Uint8List? _trackingPetMarkerBytes;
  final Map<String, BitmapDescriptor> _trackingSpotMarkerImages = {};

  bool _trackingIsPublic = true;

  // GPS filtering thresholds
  static const double _maxAccuracyMeters = 20.0;
  static const double _maxWalkSpeedMps = 6.9; // ~25 km/h
  static const double _minDistanceMeters = 5.0;

  // Platform channels
  static const _walkWidgetChannel = MethodChannel(
    'com.mannam.goldpet/walk_notification',
  );
  static const _liveActivityChannel = MethodChannel(
    'com.mannam.goldpet/walk_live_activity',
  );

  // --- Abstract / overridable ---

  /// Called after walk is successfully saved to server or locally.
  /// Implementors should handle navigation (e.g. Navigator.pop).
  void onWalkSaved();

  /// Called after a successful server save with the new walk ID.
  /// Default implementation falls back to [onWalkSaved]; screens that need
  /// the walk ID (e.g. to navigate React to a completion page) should override.
  void onWalkComplete(int walkId) => onWalkSaved();

  /// Provide the Riverpod WidgetRef for repository access.
  /// Return null if not using Riverpod (CourseWalkScreen uses ApiClient directly).
  dynamic get walkRef;

  /// Optional: called on each GPS update with the new position.
  /// CourseWalkScreen uses this for deviation detection.
  void onLocationUpdate(Position position, LatLng latLng) {}

  /// Optional: the courseId being followed (null for free walk).
  int? get followedCourseId => null;

  // --- Initialization ---

  void initTracking({bool isPublic = true, List<int>? petIds}) {
    _externalPetIds = petIds;
    _trackingIsPublic = isPublic;
    WalkLocalStorage.initialize();
    if (petIds != null && petIds.isNotEmpty) {
      _trackingPetIds = List<int>.from(petIds);
    }
    _initializeLocation();
  }

  // WALK-05: Overridable in tests to verify the dispose-path server save fires
  // without needing real platform channels or HTTP infrastructure.
  @visibleForTesting
  Future<void> Function(CreateWalkRequest)? serverSaveOnDisposeOverride;

  void disposeTracking() {
    _trackingTimer?.cancel();
    _trackingPositionSub?.cancel();
    _trackingProgrammaticMoveTimer?.cancel();
    _trackingProgrammaticMoveTimer = null;
    _trackingLastBearing = null;
    _stopWalkWidget();

    // WALK-05: Capture request BEFORE disposing notifiers so _buildWalkRequest
    // can safely read ValueNotifier.value. Must happen prior to .dispose() calls.
    CreateWalkRequest? emergencyRequest;
    final wasWalking = trackingIsWalking;
    if ((trackingIsWalking || trackingIsSaving) &&
        _trackingStartTime != null &&
        trackingPath.length >= 2) {
      try {
        emergencyRequest = _buildWalkRequest(DateTime.now());
      } catch (e) {
        debugPrint('[WalkTracking] Dispose request build failed: $e');
      }
    }

    trackingDurationNotifier.dispose();
    trackingDistanceNotifier.dispose();
    trackingBearingNotifier.dispose();

    if (emergencyRequest != null) {
      try {
        WalkLocalStorage.saveWalkDataBestEffort(emergencyRequest);
      } catch (e) {
        debugPrint('[WalkTracking] Dispose local save failed: $e');
      }
      // WALK-05: Fire-and-forget server save when walk was still in progress.
      // Skip when trackingIsSaving — stopWalk() already has an in-flight request.
      if (wasWalking) {
        unawaited(_stopWalkOnDispose(emergencyRequest));
      }
    }
  }

  // WALK-05: Server-side best-effort save invoked from disposeTracking().
  // Does NOT touch ValueNotifiers (already disposed), setState, or context.
  // The request is pre-built synchronously before this future is scheduled.
  Future<void> _stopWalkOnDispose(CreateWalkRequest request) async {
    final override = serverSaveOnDisposeOverride;
    if (override != null) {
      await override(request);
      return;
    }
    try {
      await ApiClient().dio.post('/api/v1/walks', data: request.toJson());
      await WalkLocalStorage.clearWalkData();
      debugPrint('[WalkTracking] WALK-05: dispose server save succeeded');
    } catch (e) {
      debugPrint('[WalkTracking] WALK-05: dispose server save failed (local copy preserved): $e');
    }
  }

  /// Test-only: initialises the minimal walk state required to exercise the
  /// dispose-guard code path without GPS, GoogleMaps, or real API calls.
  @visibleForTesting
  void testOnlyInitWalkState({
    required DateTime startTime,
    required List<LatLng> path,
    double distanceKm = 0.05,
  }) {
    _trackingStartTime = startTime;
    trackingPath = List<LatLng>.from(path);
    trackingIsWalking = true;
    trackingDistanceNotifier.value = distanceKm;
    trackingDurationNotifier.value =
        DateTime.now().difference(startTime).inSeconds;
  }

  // --- Location initialization ---

  Future<void> _initializeLocation() async {
    debugPrint('[WalkTracking] _initializeLocation started');

    bool serviceEnabled = await Geolocator.isLocationServiceEnabled();
    if (!serviceEnabled) {
      if (mounted) _showServiceDisabledDialog();
      return;
    }

    LocationPermission permission = await Geolocator.checkPermission();
    if (permission == LocationPermission.denied) {
      permission = await Geolocator.requestPermission();
      if (permission == LocationPermission.denied) {
        if (mounted) {
          setState(() => trackingIsInitializing = false);
          _showPermissionDialog();
        }
        return;
      }
    }

    if (permission == LocationPermission.deniedForever) {
      if (mounted) {
        setState(() => trackingIsInitializing = false);
        _showPermissionDialog();
      }
      return;
    }

    try {
      final position = await Geolocator.getCurrentPosition(
        locationSettings: const LocationSettings(
          timeLimit: Duration(seconds: 5),
        ),
      );
      trackingInitialPosition = LatLng(position.latitude, position.longitude);
    } catch (e) {
      debugPrint('[WalkTracking] Error getting location: $e');
    } finally {
      if (mounted) {
        setState(() => trackingIsInitializing = false);
      }
      if (mounted && trackingInitialPosition != null) {
        startWalk();
      } else if (mounted && trackingInitialPosition == null) {
        Navigator.of(context).pop();
      }
    }
  }

  // --- Walk lifecycle ---

  Future<void> startWalk() async {
    // Pre-refresh token
    try {
      await ApiClient().refreshTokenIfNeeded();
    } catch (_) {}
    if (!mounted) return;

    // 산책 최소 저장 조건 설정값 fetch + 캐시
    try {
      final res = await ApiClient().dio.get('/api/v1/settings/public');
      if (res.statusCode == 200 && res.data is Map) {
        final data = res.data as Map;
        final prefs = await SharedPreferences.getInstance();
        if (data.containsKey('walkMinDurationSeconds')) {
          await prefs.setInt(
            'walk_min_duration_seconds',
            (data['walkMinDurationSeconds'] as num).toInt(),
          );
        }
        if (data.containsKey('walkMinDistanceMeters')) {
          await prefs.setDouble(
            'walk_min_distance_meters',
            (data['walkMinDistanceMeters'] as num).toDouble(),
          );
        }
      }
    } catch (_) {
      // non-fatal: 캐시된 값 또는 하드코딩 기본값 사용
    }

    // Load persisted compass mode preference (default OFF).
    try {
      final prefs = await SharedPreferences.getInstance();
      trackingCompassModeEnabled =
          prefs.getBool(_kPrefCompassMode) ?? false;
    } catch (_) {
      // non-fatal: 기본값(OFF) 유지
    }
    if (!mounted) return;

    bool serviceEnabled = await Geolocator.isLocationServiceEnabled();
    if (!serviceEnabled) {
      if (mounted) _showServiceDisabledDialog();
      return;
    }

    LocationPermission permission = await Geolocator.checkPermission();
    if (permission == LocationPermission.denied) {
      permission = await Geolocator.requestPermission();
      if (permission == LocationPermission.denied) {
        if (mounted) _showPermissionDialog();
        return;
      }
    }
    if (permission == LocationPermission.deniedForever) {
      if (mounted) _showPermissionDialog();
      return;
    }

    // Request background permission
    if (permission == LocationPermission.whileInUse) {
      final shouldRequest = await _showBackgroundPermissionDialog();
      if (!mounted) return;
      if (shouldRequest) {
        await Geolocator.openAppSettings();
        if (!mounted) return;
        permission = await Geolocator.checkPermission();
      }
    }

    // Initialize state
    trackingDurationNotifier.value = 0;
    trackingDistanceNotifier.value = 0.0;
    trackingPath = [];
    trackingSpots.clear();
    trackingMarkers = {};
    trackingPolylines = {};
    _trackingLastAccepted = null;
    _trackingStartTime = DateTime.now();

    trackingIsPaused = false;
    _trackingPauseStartTime = null;
    _trackingTotalPauseDuration = Duration.zero;
    trackingIsFollowing = true;

    setState(() {
      trackingIsWalking = true;
    });

    _startWalkWidget();

    // Fetch pet data
    await _fetchPetData().timeout(const Duration(seconds: 3), onTimeout: () {
      debugPrint('[WalkTracking] _fetchPetData timed out after 3s — proceeding with bridge IDs only');
    });
    if (!mounted) return;

    await _buildPetMarkerImage();
    if (!mounted) return;
    await addPetStartMarker();

    // Timer
    _trackingTimer = Timer.periodic(const Duration(seconds: 1), (timer) {
      if (_trackingStartTime != null && !trackingIsPaused) {
        final elapsed = DateTime.now().difference(_trackingStartTime!);
        final elapsedSeconds =
            (elapsed - _trackingTotalPauseDuration).inSeconds;
        trackingDurationNotifier.value = elapsedSeconds;
        _updateWalkWidget(elapsedSeconds, trackingDistanceNotifier.value);

        if (elapsedSeconds > 0 &&
            elapsedSeconds % 60 == 0 &&
            trackingPath.length >= 2) {
          _saveWalkDataLocally();
        }
      }
    });

    // GPS stream
    late final LocationSettings locationSettings;
    if (Platform.isAndroid) {
      locationSettings = AndroidSettings(
        accuracy: LocationAccuracy.best,
        distanceFilter: 3,
      );
    } else if (Platform.isIOS) {
      locationSettings = AppleSettings(
        accuracy: LocationAccuracy.best,
        distanceFilter: 3,
        activityType: ActivityType.fitness,
        pauseLocationUpdatesAutomatically: false,
        allowBackgroundLocationUpdates: true,
        showBackgroundLocationIndicator: true,
      );
    } else {
      locationSettings = const LocationSettings(
        accuracy: LocationAccuracy.best,
        distanceFilter: 3,
      );
    }

    _trackingPositionSub = Geolocator.getPositionStream(
      locationSettings: locationSettings,
    ).listen((Position position) => _updateLocation(position));
  }

  void _updateLocation(Position position) {
    if (trackingIsPaused) return;

    if (position.accuracy > _maxAccuracyMeters) {
      debugPrint(
        '[Walk] Rejected: accuracy ${position.accuracy.toStringAsFixed(1)}m',
      );
      return;
    }

    final latLng = LatLng(position.latitude, position.longitude);

    if (trackingPath.isNotEmpty && _trackingLastAccepted != null) {
      final last = _trackingLastAccepted!;
      final dist = Geolocator.distanceBetween(
        last.latitude,
        last.longitude,
        position.latitude,
        position.longitude,
      );

      if (dist < _minDistanceMeters) return;

      final timeDiffSec =
          position.timestamp.difference(last.timestamp).inMilliseconds / 1000.0;
      if (timeDiffSec > 0) {
        final speedMps = dist / timeDiffSec;
        if (speedMps > _maxWalkSpeedMps) {
          debugPrint(
            '[Walk] Rejected: speed ${(speedMps * 3.6).toStringAsFixed(1)}km/h',
          );
          return;
        }
      }

      trackingDistanceNotifier.value += (dist / 1000.0);
      trackingPath.add(latLng);
      _trackingLastAccepted = position;
      updatePolyline();
    } else {
      trackingPath.add(latLng);
      _trackingLastAccepted = position;
      trackingInitialPosition = latLng;
      addPetStartMarker();
      updatePolyline();
    }

    // Follow mode: animate camera (with optional bearing in compass mode)
    if (trackingCompassModeEnabled && trackingIsFollowing) {
      // Compute target bearing; below speed threshold hold last known bearing.
      double targetBearing;
      if (position.heading >= 0 && position.speed >= 0.5) {
        targetBearing =
            _smoothBearing(_trackingLastBearing, position.heading);
      } else {
        targetBearing = _trackingLastBearing ?? 0.0;
      }
      _trackingLastBearing = targetBearing;
      trackingBearingNotifier.value = targetBearing;
      animateCameraGuarded(
        CameraUpdate.newCameraPosition(
          CameraPosition(
            target: latLng,
            zoom: _trackingLastZoom,
            bearing: targetBearing,
            tilt: 0,
          ),
        ),
      );
    } else if (trackingIsFollowing) {
      // Compass OFF: preserve zoom; bearing stays north-up.
      animateCameraGuarded(
        CameraUpdate.newCameraPosition(
          CameraPosition(
            target: latLng,
            zoom: _trackingLastZoom,
            bearing: 0.0,
            tilt: 0,
          ),
        ),
      );
    }
    _updateAddress(position.latitude, position.longitude);

    // Notify subclass (e.g. CourseWalkScreen for deviation detection)
    onLocationUpdate(position, latLng);
  }

  void updatePolyline({Color? color}) {
    if (trackingPath.length < 2) return;
    final polyline = Polyline(
      polylineId: const PolylineId('walk_path'),
      points: List<LatLng>.from(trackingPath),
      color: color ?? AppColors.primary,
      width: 5,
    );
    setState(() {
      trackingPolylines = {
        ...trackingPolylines.where((p) => p.polylineId.value != 'walk_path'),
        polyline,
      };
    });
  }

  /// Called when user manually drags the map. Disables follow mode.
  void onCameraMoveStartedByUser() {
    // Short-circuit: ignore camera moves that originated from our own
    // [animateCameraGuarded] calls so the bearing pipeline does not
    // spuriously disable follow mode on every GPS tick.
    if (_trackingProgrammaticMove) return;
    trackingIsFollowing = false;
    if (mounted) setState(() {});
  }

  /// Recenters camera to current position and re-enables follow mode.
  void recenterCamera() {
    trackingIsFollowing = true;
    if (mounted) setState(() {});
    if (trackingPath.isNotEmpty) {
      animateCameraGuarded(CameraUpdate.newLatLng(trackingPath.last));
    }
  }

  /// Wraps [GoogleMapController.animateCamera] with a flag that prevents
  /// [onCameraMoveStartedByUser] from treating the resulting camera move
  /// as a real user drag. Public so screens (e.g. course walk initial
  /// bounds-fit) can route their own animateCamera calls through the guard.
  void animateCameraGuarded(CameraUpdate update) {
    _trackingProgrammaticMove = true;
    _trackingProgrammaticMoveTimer?.cancel();
    // Defensive: if onCameraIdle fails to fire, clear the guard after 500 ms.
    // 500 ms (not 1 s) leaves room for a fast pan-and-release within ~0.5 s
    // of the previous GPS tick to still register as a user pan.
    _trackingProgrammaticMoveTimer = Timer(
      const Duration(milliseconds: 500),
      () {
        _trackingProgrammaticMove = false;
      },
    );
    trackingMapController?.animateCamera(update);
  }

  /// Wire to `GoogleMap.onCameraIdle`. Clears the programmatic-move guard.
  void onCameraIdleHandler() {
    _trackingProgrammaticMove = false;
    _trackingProgrammaticMoveTimer?.cancel();
    _trackingProgrammaticMoveTimer = null;
  }

  /// Wire to `GoogleMap.onCameraMove`. Tracks the current zoom so the
  /// bearing pipeline can rebuild a full [CameraPosition] without resetting
  /// the user's pinch-zoom level. Fires for both programmatic and user
  /// moves — always storing the latest zoom is intentional.
  void onCameraMoveHandler(CameraPosition position) {
    _trackingLastZoom = position.zoom;
  }

  /// Low-pass smoothed, shortest-arc bearing lerp. `alpha = 0.3` retains
  /// 70% of the previous bearing per GPS tick (~1 Hz), which suppresses
  /// heading-noise jitter while still tracking real direction changes
  /// within ~3 ticks.
  double _smoothBearing(double? prev, double next) {
    if (prev == null) return next;
    const alpha = 0.3;
    final delta = ((next - prev + 540) % 360) - 180;
    return (prev + alpha * delta + 360) % 360;
  }

  /// Flips compass mode, persists the new value, and (when turning OFF)
  /// animates the camera back to bearing 0 so the map snaps north-up.
  Future<void> toggleCompassMode() async {
    trackingCompassModeEnabled = !trackingCompassModeEnabled;
    try {
      final prefs = await SharedPreferences.getInstance();
      await prefs.setBool(_kPrefCompassMode, trackingCompassModeEnabled);
    } catch (e) {
      debugPrint('[WalkTracking] Failed to persist compass mode: $e');
    }
    if (!trackingCompassModeEnabled) {
      final target = trackingPath.isNotEmpty
          ? trackingPath.last
          : trackingInitialPosition;
      if (target != null) {
        animateCameraGuarded(
          CameraUpdate.newCameraPosition(
            CameraPosition(
              target: target,
              zoom: _trackingLastZoom,
              bearing: 0,
              tilt: 0,
            ),
          ),
        );
      }
      _trackingLastBearing = null;
      trackingBearingNotifier.value = 0.0;
    }
    if (mounted) setState(() {});
  }

  // --- Pause / Resume ---

  void togglePause() {
    setState(() {
      if (trackingIsPaused) {
        _trackingTotalPauseDuration += DateTime.now().difference(
          _trackingPauseStartTime!,
        );
        _trackingPauseStartTime = null;
        trackingIsPaused = false;
        _trackingPositionSub?.resume();
      } else {
        _trackingPauseStartTime = DateTime.now();
        trackingIsPaused = true;
        _trackingPositionSub?.pause();
      }
    });
  }

  // --- App lifecycle ---

  void handleAppResumed() {
    if (trackingIsWalking && _trackingStartTime != null && !trackingIsPaused) {
      final elapsed = DateTime.now().difference(_trackingStartTime!);
      trackingDurationNotifier.value =
          (elapsed - _trackingTotalPauseDuration).inSeconds;
    }
  }

  // --- Stop walk ---

  Future<void> stopWalk() async {
    if (!trackingIsWalking) return;

    final wasPaused = trackingIsPaused;
    final savedPauseStartTime = _trackingPauseStartTime;

    if (trackingIsPaused && _trackingPauseStartTime != null) {
      _trackingTotalPauseDuration += DateTime.now().difference(
        _trackingPauseStartTime!,
      );
      _trackingPauseStartTime = null;
      trackingIsPaused = false;
    }

    final endTime = DateTime.now();
    final startTime =
        _trackingStartTime ??
        endTime.subtract(Duration(seconds: trackingDurationNotifier.value));
    final durationSeconds =
        (endTime.difference(startTime) - _trackingTotalPauseDuration).inSeconds;

    if (durationSeconds < 1) {
      if (wasPaused) {
        trackingIsPaused = true;
        _trackingPauseStartTime = savedPauseStartTime;
        _trackingTotalPauseDuration -= DateTime.now().difference(
          savedPauseStartTime!,
        );
      }
      if (mounted) showTrackingSnackBar('산책 시간이 너무 짧습니다. 조금 더 걸어주세요!');
      return;
    }

    // 최소 저장 조건 체크: 시간 >= 30초 AND 거리 >= 10m
    final prefs = await SharedPreferences.getInstance();
    final minDuration = prefs.getInt('walk_min_duration_seconds') ?? 30;
    final minDistanceM = prefs.getDouble('walk_min_distance_meters') ?? 10.0;
    final distanceMeters = trackingDistanceNotifier.value * 1000.0;

    if (durationSeconds < minDuration || distanceMeters < minDistanceM) {
      if (!mounted) return;
      final keepWalking = await _showWalkSaveAlert(
        minDuration: minDuration,
        minDistance: minDistanceM.toInt(),
        currentDuration: durationSeconds,
        currentDistance: distanceMeters.toInt(),
      );
      if (keepWalking) {
        // "산책 계속하기" — 일시정지 상태 복원 후 재개
        if (wasPaused) {
          trackingIsPaused = true;
          _trackingPauseStartTime = savedPauseStartTime;
          _trackingTotalPauseDuration -= DateTime.now().difference(
            savedPauseStartTime!,
          );
        }
        return;
      }
      // "기록 삭제" — 산책 데이터 폐기
      _trackingTimer?.cancel();
      _trackingPositionSub?.cancel();
      _trackingProgrammaticMoveTimer?.cancel();
      _trackingProgrammaticMoveTimer = null;
      _trackingLastBearing = null;
      trackingBearingNotifier.value = 0.0;
      _stopWalkWidget();
      await WalkLocalStorage.clearWalkData();
      if (mounted) {
        setState(() {
          trackingIsWalking = false;
          trackingIsSaving = false;
        });
        showTrackingSnackBar('산책 기록이 삭제되었습니다.');
        await Future.delayed(const Duration(milliseconds: 500));
        if (mounted) onWalkSaved();
      }
      return;
    }

    _trackingTimer?.cancel();
    _trackingPositionSub?.cancel();
    _trackingProgrammaticMoveTimer?.cancel();
    _trackingProgrammaticMoveTimer = null;
    _trackingLastBearing = null;
    trackingBearingNotifier.value = 0.0;
    _stopWalkWidget();

    // Ensure path has at least 2 points
    if (trackingPath.length < 2) {
      try {
        final pos = await Geolocator.getCurrentPosition(
          locationSettings: const LocationSettings(
            timeLimit: Duration(seconds: 3),
          ),
        );
        final currentLatLng = LatLng(pos.latitude, pos.longitude);
        if (trackingPath.isEmpty) trackingPath.add(currentLatLng);
        if (trackingPath.length < 2) trackingPath.add(trackingPath.first);
      } catch (e) {
        if (trackingPath.isEmpty) {
          final fallback =
              trackingInitialPosition ?? const LatLng(37.5665, 126.9780);
          trackingPath.add(fallback);
          trackingPath.add(fallback);
        } else {
          trackingPath.add(trackingPath.first);
        }
      }
    }

    final request = _buildWalkRequest(
      endTime,
      durationOverride: durationSeconds,
    );

    setState(() {
      trackingIsWalking = false;
      trackingIsSaving = true;
    });

    try {
      int? savedWalkId;
      final repo = walkRef?.read(walkRepositoryProvider);
      if (repo != null) {
        final walk = await repo.createWalk(request);
        savedWalkId = walk.id as int?;
      } else {
        // Fallback: direct API call
        final response = await ApiClient().dio.post(
          '/api/v1/walks',
          data: request.toJson(),
        );
        final data = response.data;
        if (data is Map && data['id'] is num) {
          savedWalkId = (data['id'] as num).toInt();
        }
      }
      await WalkLocalStorage.clearWalkData();

      if (mounted) {
        setState(() => trackingIsSaving = false);
        showTrackingSnackBar('산책이 저장되었습니다!');
        await Future.delayed(const Duration(milliseconds: 500));
        if (!mounted) return;
        if (savedWalkId != null) {
          onWalkComplete(savedWalkId);
        } else {
          onWalkSaved();
        }
      }
    } catch (e) {
      debugPrint('[WalkTracking] Walk save error: $e');
      if (mounted) {
        setState(() => trackingIsSaving = false);
        if (e is DioException && e.response?.statusCode == 401) {
          await WalkLocalStorage.saveWalkData(request);
          showTrackingSnackBar('산책이 저장되었습니다! 다음 로그인 시 서버에 동기화됩니다.');
          await Future.delayed(const Duration(milliseconds: 500));
          if (mounted) onWalkSaved();
          return;
        }
        if (e is DioException && e.response?.statusCode == 400) {
          await WalkLocalStorage.clearWalkData();
          final prefs400 = await SharedPreferences.getInstance();
          final minDur = prefs400.getInt('walk_min_duration_seconds') ?? 30;
          final minDist =
              prefs400.getDouble('walk_min_distance_meters') ?? 10.0;
          showTrackingSnackBar(
            '산책 저장 조건 미충족: $minDur초 이상, ${minDist.toInt()}m 이상 필요',
          );
          if (mounted) onWalkSaved();
          return;
        }
        await _retrySaveWalk(request);
      }
    }
  }

  CreateWalkRequest _buildWalkRequest(
    DateTime endTime, {
    int? durationOverride,
  }) {
    assert(
      _trackingPetIds.isNotEmpty || _externalPetIds == null,
      'walk save with empty petIds when bridge supplied IDs',
    );
    final startTime =
        _trackingStartTime ??
        endTime.subtract(Duration(seconds: trackingDurationNotifier.value));
    final durationSeconds =
        durationOverride ??
        (endTime.difference(startTime) - _trackingTotalPauseDuration).inSeconds;
    return CreateWalkRequest(
      startTime: startTime,
      endTime: endTime,
      distanceKm: trackingDistanceNotifier.value,
      durationSeconds: durationSeconds,
      path: trackingPath.map((e) => [e.latitude, e.longitude]).toList(),
      spots: List<WalkSpotDto>.from(trackingSpots),
      caloriesBurned: trackingDistanceNotifier.value * 50.0,
      petIds: _trackingPetIds,
      isPublic: _trackingIsPublic,
      followedCourseId: followedCourseId,
    );
  }

  Future<void> _retrySaveWalk(CreateWalkRequest request) async {
    const maxAttempts = 3;
    for (var attempt = 1; attempt <= maxAttempts; attempt++) {
      if (!mounted) return;
      final retry = await showGoldPetConfirm(
        context,
        message: '산책 저장에 실패했습니다. 다시 시도하시겠습니까? ($attempt/$maxAttempts)',
        confirmText: '재시도',
        cancelText: '취소',
      );
      if (!retry || !mounted) break;

      setState(() => trackingIsSaving = true);
      try {
        final repo = walkRef?.read(walkRepositoryProvider);
        if (repo != null) {
          await repo.createWalk(request);
        } else {
          await ApiClient().dio.post('/api/v1/walks', data: request.toJson());
        }
        await WalkLocalStorage.clearWalkData();
        if (mounted) {
          setState(() => trackingIsSaving = false);
          showTrackingSnackBar('산책이 저장되었습니다!');
          await Future.delayed(const Duration(milliseconds: 500));
          if (mounted) onWalkSaved();
          return;
        }
      } catch (e) {
        debugPrint('[WalkTracking] Retry save error: $e');
        if (mounted) setState(() => trackingIsSaving = false);
        if (e is DioException && e.response?.statusCode == 401) {
          await WalkLocalStorage.saveWalkData(request);
          if (mounted) {
            showTrackingSnackBar('산책이 저장되었습니다! 다음 로그인 시 서버에 동기화됩니다.');
            await Future.delayed(const Duration(milliseconds: 500));
            if (mounted) onWalkSaved();
          }
          return;
        }
        if (e is DioException && e.response?.statusCode == 400) {
          await WalkLocalStorage.clearWalkData();
          if (mounted) {
            setState(() => trackingIsSaving = false);
            final prefs400 = await SharedPreferences.getInstance();
            final minDur = prefs400.getInt('walk_min_duration_seconds') ?? 30;
            final minDist =
                prefs400.getDouble('walk_min_distance_meters') ?? 10.0;
            showTrackingSnackBar(
              '산책 저장 조건 미충족: $minDur초 이상, ${minDist.toInt()}m 이상 필요',
            );
            await Future.delayed(const Duration(milliseconds: 500));
            if (mounted) onWalkSaved();
          }
          return;
        }
      }
    }
    await WalkLocalStorage.saveWalkData(request);
    if (mounted) {
      setState(() => trackingIsSaving = false);
      showTrackingSnackBar('산책이 저장되었습니다! 다음 접속 시 자동으로 동기화됩니다.');
      await Future.delayed(const Duration(milliseconds: 500));
      if (mounted) onWalkSaved();
    }
  }

  // --- Spot recording ---

  Future<void> addSpot(String type) async {
    if (!trackingIsWalking) return;

    if (type == 'PHOTO') {
      await _addPhotoSpot();
      return;
    }

    if (type == 'POOP') {
      await _addPoopSpot();
      return;
    }

    try {
      final position = await Geolocator.getCurrentPosition();
      final latLng = LatLng(position.latitude, position.longitude);

      final spot = WalkSpotDto(
        latitude: position.latitude,
        longitude: position.longitude,
        type: type,
        timestamp: DateTime.now(),
      );
      trackingSpots.add(spot);

      final spotIcon = await _getSpotMarkerImage(type);
      final marker = Marker(
        markerId: MarkerId('spot_${trackingSpots.length}'),
        position: latLng,
        icon: spotIcon,
        anchor: const Offset(0.5, 0.5),
      );
      setState(() {
        trackingMarkers = {...trackingMarkers, marker};
      });

      if (mounted) showTrackingSnackBar('${_getSpotLabel(type)} 스팟이 기록되었습니다!');
    } catch (e) {
      debugPrint('Error adding spot: $e');
    }
  }

  Future<void> _addPhotoSpot() async {
    try {
      final position = await Geolocator.getCurrentPosition();
      final picker = ImagePicker();
      late String finalCroppedPath;
      String? memoText;

      while (true) {
        final XFile? photo = await picker.pickImage(
          source: ImageSource.camera,
          imageQuality: 80,
          maxWidth: 1920,
          maxHeight: 1920,
        );
        if (photo == null) return;

        await Future.delayed(const Duration(milliseconds: 800));
        if (!mounted) return;
        setState(() {});

        final photoFile = File(photo.path);
        if (!await photoFile.exists()) {
          if (mounted) showTrackingSnackBar('사진 파일을 찾을 수 없습니다.');
          return;
        }

        if (!mounted) return;
        String? croppedPath;
        try {
          croppedPath = await PhotoCropScreen.show(context, photo.path);
        } catch (e) {
          debugPrint('PhotoCropScreen error: $e');
          if (mounted) showTrackingSnackBar('사진 편집 오류가 발생했습니다.');
          return;
        }
        // User cancelled crop — silent by design.
        if (croppedPath == null) return;

        if (!mounted) return;
        final result = await showPhotoMemoSheet(croppedPath);
        if (!mounted) return;

        if (result == null) {
          try {
            await File(croppedPath).delete();
          } catch (_) {}
          return;
        } else if (result['retake'] == true) {
          try {
            await File(croppedPath).delete();
          } catch (_) {}
          continue;
        } else {
          finalCroppedPath = croppedPath;
          memoText = result['memo'] as String?;
          break;
        }
      }

      String? imageUrl;
      try {
        final fileName = finalCroppedPath.split('/').last;
        final formData = FormData.fromMap({
          'file': await MultipartFile.fromFile(
            finalCroppedPath,
            filename: fileName,
          ),
        });
        final response = await ApiClient().dio.post(
          '/api/v1/files/upload',
          data: formData,
        );
        imageUrl = response.data['url'] as String?;
      } catch (e) {
        debugPrint('Photo upload failed: $e');
      } finally {
        try {
          await File(finalCroppedPath).delete();
        } catch (_) {}
      }

      if (!mounted) return;

      final latLng = LatLng(position.latitude, position.longitude);
      final spot = WalkSpotDto(
        latitude: position.latitude,
        longitude: position.longitude,
        type: 'PHOTO',
        timestamp: DateTime.now(),
        imageUrl: imageUrl,
        note: (memoText != null && memoText.isNotEmpty) ? memoText : null,
      );
      trackingSpots.add(spot);

      final spotIcon = await _getSpotMarkerImage('PHOTO');
      final marker = Marker(
        markerId: MarkerId('spot_${trackingSpots.length}'),
        position: latLng,
        icon: spotIcon,
        anchor: const Offset(0.5, 0.5),
      );
      setState(() {
        trackingMarkers = {...trackingMarkers, marker};
      });

      if (mounted) showTrackingSnackBar('사진이 저장되었습니다!');
    } catch (e) {
      debugPrint('Error adding photo spot: $e');
      if (mounted) {
        if (e is DioException && e.response?.statusCode == 401) {
          if (mounted) showTrackingSnackBar('인증이 만료되었습니다. 다시 로그인해 주세요.');
          return;
        }
        showTrackingSnackBar('사진 저장 실패: 네트워크 오류가 발생했습니다.');
      }
    }
  }

  /// Override in subclass to provide photo memo bottom sheet.
  /// Returns {'memo': String} or {'retake': true} or null.
  Future<Map<String, dynamic>?> showPhotoMemoSheet(String photoPath);

  // --- Pet marker ---

  Future<void> _fetchPetData() async {
    assert(
      _externalPetIds == null || _externalPetIds!.isEmpty || _trackingPetIds.isNotEmpty,
      'invariant: bridge IDs must already be seeded by initTracking',
    );
    try {
      final response = await ApiClient().dio.get('/api/v1/pets/my');
      final pets = response.data as List;
      if (_externalPetIds != null && _externalPetIds!.isNotEmpty) {
        // Use pet IDs selected by user in React WebView (IDs already seeded by initTracking)
        final selectedPets = pets
            .where((p) => _externalPetIds!.contains(p['id'] as int))
            .toList();
        if (selectedPets.isNotEmpty) {
          _trackingPetImageUrl =
              selectedPets.first['profileImageUrl'] as String?;
        } else if (pets.isNotEmpty) {
          _trackingPetImageUrl = pets.first['profileImageUrl'] as String?;
        }
        _trackingPets = selectedPets.cast<Map<String, dynamic>>();
      } else {
        // Backward compatible: no petIds provided → all pets (course walk, deep-link, etc.)
        if (pets.isNotEmpty) {
          _trackingPetImageUrl = pets.first['profileImageUrl'] as String?;
        }
        _trackingPetIds = pets.map<int>((p) => p['id'] as int).toList();
        _trackingPets = pets.cast<Map<String, dynamic>>();
      }
    } catch (e) {
      debugPrint('[WalkTracking] Failed to fetch pet data: $e');
    }
  }

  Future<void> _buildPetMarkerImage() async {
    ui.Image? petImage;
    if (_trackingPetImageUrl != null) {
      try {
        final imageUrl = _trackingPetImageUrl!.replaceFirst(
          'http://',
          'https://',
        );
        final response = await http.get(Uri.parse(imageUrl));
        if (response.statusCode == 200) {
          final codec = await ui.instantiateImageCodec(response.bodyBytes);
          final frame = await codec.getNextFrame();
          petImage = frame.image;
        }
      } catch (e) {
        debugPrint('[WalkTracking] Failed to download pet image: $e');
      }
    }

    final recorder = ui.PictureRecorder();
    final canvas = Canvas(recorder);
    const markerWidth = 180.0;
    const markerHeight = 240.0;
    const circleCenter = Offset(90, 90);
    const circleRadius = 54.0;
    const borderWidth = 6.0;

    final borderPaint = Paint()
      ..color = AppColors.primary
      ..style = PaintingStyle.fill;
    canvas.drawCircle(circleCenter, circleRadius + borderWidth, borderPaint);

    if (petImage != null) {
      canvas.save();
      canvas.clipPath(
        Path()..addOval(
          Rect.fromCircle(center: circleCenter, radius: circleRadius),
        ),
      );
      final srcRect = Rect.fromLTWH(
        0,
        0,
        petImage.width.toDouble(),
        petImage.height.toDouble(),
      );
      final dstRect = Rect.fromCircle(
        center: circleCenter,
        radius: circleRadius,
      );
      canvas.drawImageRect(petImage, srcRect, dstRect, Paint());
      canvas.restore();
    } else {
      canvas.drawCircle(
        circleCenter,
        circleRadius,
        Paint()..color = Colors.white,
      );
      canvas.drawCircle(
        circleCenter,
        24,
        Paint()..color = AppColors.primary.withValues(alpha: 0.3),
      );
    }

    final badgePaint = Paint()..color = AppColors.primary;
    canvas.drawRRect(
      RRect.fromRectAndRadius(
        const Rect.fromLTWH(48, 150, 84, 36),
        const Radius.circular(18),
      ),
      badgePaint,
    );
    final textPainter = TextPainter(
      text: const TextSpan(
        text: '출발',
        style: TextStyle(
          fontSize: 20,
          fontWeight: FontWeight.w600,
          color: Colors.white,
        ),
      ),
      textDirection: TextDirection.ltr,
    )..layout();
    textPainter.paint(
      canvas,
      Offset((markerWidth - textPainter.width) / 2, 157),
    );

    final linePaint = Paint()
      ..color = AppColors.primary
      ..strokeWidth = 3;
    canvas.drawLine(const Offset(90, 186), const Offset(90, 204), linePaint);

    const hereDotCenter = Offset(90, 216);
    canvas.drawCircle(hereDotCenter, 12, Paint()..color = Colors.white);
    canvas.drawCircle(
      hereDotCenter,
      10,
      Paint()..color = const Color(0xFFFF5005),
    );
    canvas.drawCircle(
      hereDotCenter,
      7,
      Paint()
        ..color = Colors.white
        ..style = PaintingStyle.stroke
        ..strokeWidth = 3,
    );

    final picture = recorder.endRecording();
    final img = await picture.toImage(
      markerWidth.toInt(),
      markerHeight.toInt(),
    );
    final byteData = await img.toByteData(format: ui.ImageByteFormat.png);
    _trackingPetMarkerBytes = byteData!.buffer.asUint8List();
  }

  Future<void> addPetStartMarker() async {
    if (_trackingPetMarkerBytes == null || trackingInitialPosition == null) {
      return;
    }
    try {
      final overlayImage = BitmapDescriptor.bytes(
        _trackingPetMarkerBytes!,
        width: 60,
        height: 80,
      );
      final marker = Marker(
        markerId: const MarkerId('pet_start'),
        position: trackingInitialPosition!,
        icon: overlayImage,
        anchor: const Offset(0.5, 1.0),
      );
      setState(() {
        trackingMarkers = {
          ...trackingMarkers.where((m) => m.markerId.value != 'pet_start'),
          marker,
        };
      });
    } catch (e) {
      debugPrint('[WalkTracking] Failed to add pet marker: $e');
    }
  }

  // --- Helpers ---

  Future<BitmapDescriptor> _getSpotMarkerImage(String type) async {
    if (_trackingSpotMarkerImages.containsKey(type)) {
      return _trackingSpotMarkerImages[type]!;
    }
    final pngAsset = switch (type) {
      'POOP' => 'assets/images/walk/marker_poop.png',
      'PEE' => 'assets/images/walk/marker_pee.png',
      'PHOTO' => 'assets/images/walk/marker_camera.png',
      _ => 'assets/images/walk/marker_poop.png',
    };
    final overlayImage = BitmapDescriptor.asset(
      const ImageConfiguration(size: Size(32, 32)),
      pngAsset,
    );
    final descriptor = await overlayImage;
    _trackingSpotMarkerImages[type] = descriptor;
    return descriptor;
  }

  Future<void> _updateAddress(double lat, double lng) async {
    final now = DateTime.now();
    if (_trackingLastGeocodingTime != null &&
        now.difference(_trackingLastGeocodingTime!).inSeconds < 10) {
      return;
    }
    _trackingLastGeocodingTime = now;

    try {
      final response = await ApiClient().dio.get(
        '/api/v1/maps/reverse-geocode',
        queryParameters: {'lat': lat, 'lng': lng},
      );
      if (mounted) {
        setState(
          () => trackingCurrentAddress =
              response.data['address'] as String? ?? '',
        );
      }
    } catch (e) {
      debugPrint('[WalkTracking] Reverse geocode failed: $e');
    }
  }

  Future<void> _saveWalkDataLocally() async {
    try {
      if (_trackingStartTime == null || trackingPath.length < 2) return;
      final request = _buildWalkRequest(DateTime.now());
      await WalkLocalStorage.saveWalkData(request);
    } catch (e) {
      debugPrint('[WalkTracking] Periodic local save failed: $e');
    }
  }

  String _getSpotLabel(String type) {
    switch (type) {
      case 'PEE':
        return '쉬';
      case 'POOP':
        return '응가';
      case 'PHOTO':
        return '사진';
      default:
        return '스팟';
    }
  }

  String formatDuration(int seconds) {
    final h = seconds ~/ 3600;
    final m = (seconds % 3600) ~/ 60;
    final s = seconds % 60;
    return '${h.toString().padLeft(2, '0')}:${m.toString().padLeft(2, '0')}:${s.toString().padLeft(2, '0')}';
  }

  // --- Walk widget (platform notification/live activity) ---

  String _formatWidgetDuration(int seconds) {
    final h = seconds ~/ 3600;
    final m = (seconds % 3600) ~/ 60;
    final s = seconds % 60;
    if (h > 0) {
      return '$h:${m.toString().padLeft(2, '0')}:${s.toString().padLeft(2, '0')}';
    }
    return '${m.toString().padLeft(2, '0')}:${s.toString().padLeft(2, '0')}';
  }

  void _updateWalkWidget(int seconds, double distanceKm) {
    final time = formatDuration(seconds);
    final widgetDuration = _formatWidgetDuration(seconds);
    final calories = (distanceKm * 50).toStringAsFixed(0);

    if (Platform.isAndroid) {
      _walkWidgetChannel.invokeMethod('show', {
        'time': time,
        'distance': distanceKm.toStringAsFixed(2),
        'duration': widgetDuration,
        'calories': calories,
      });
    } else if (Platform.isIOS) {
      _liveActivityChannel.invokeMethod('update', {
        'distanceKm': distanceKm,
        'durationSeconds': seconds,
        'caloriesBurned': distanceKm * 50.0,
      });
    }
  }

  void _startWalkWidget() {
    if (Platform.isAndroid) {
      _walkWidgetChannel.invokeMethod('startForeground');
    } else if (Platform.isIOS) {
      _liveActivityChannel.invokeMethod('start');
    }
  }

  void _stopWalkWidget() {
    try {
      if (Platform.isAndroid) {
        _walkWidgetChannel.invokeMethod('cancel');
      } else if (Platform.isIOS) {
        _liveActivityChannel.invokeMethod('end');
      }
    } catch (_) {}
  }

  // --- Dialogs ---

  void _showServiceDisabledDialog() async {
    if (!mounted) return;
    final confirmed = await showGoldPetConfirm(
      context,
      message: '원활한 산책 기록을 위해\n위치 서비스를 켜주세요.',
      confirmText: '설정',
      cancelText: '취소',
    );
    if (confirmed) {
      await Geolocator.openLocationSettings();
      if (mounted) _initializeLocation();
    } else {
      setState(() => trackingIsInitializing = false);
      if (mounted) Navigator.of(context).pop();
    }
  }

  void _showPermissionDialog() async {
    if (!mounted) return;
    final confirmed = await showGoldPetConfirm(
      context,
      message: '산책 경로를 기록하려면 위치 권한이 필요합니다.\n설정에서 "앱을 사용하는 동안 허용"으로 변경해주세요.',
      confirmText: '설정으로 이동',
      cancelText: '취소',
    );
    if (confirmed) {
      await openAppSettings();
      if (mounted) {
        setState(() => trackingIsInitializing = true);
        _initializeLocation();
      }
    } else {
      setState(() => trackingIsInitializing = false);
      if (mounted) Navigator.of(context).pop();
    }
  }

  Future<bool> _showBackgroundPermissionDialog() async {
    if (!mounted) return false;
    return await showGoldPetConfirm(
      context,
      message: '화면이 꺼진 상태에서도 산책 경로를 정확하게 기록하려면\n"항상 허용" 권한이 필요합니다.',
      confirmText: '허용하기',
      cancelText: '나중에',
    );
  }

  Future<bool> showStopWalkDialog() async {
    return showGoldPetConfirm(
      context,
      message: '산책을 종료하고 기록을 저장하시겠습니까?',
      confirmText: '종료',
      cancelText: '취소',
    );
  }

  void showTrackingSnackBar(String message) {
    ScaffoldMessenger.of(context).showSnackBar(
      SnackBar(
        content: Text(
          message,
          style: const TextStyle(
            fontFamily: 'Pretendard',
            fontWeight: FontWeight.w500,
            fontSize: 14,
            color: Colors.white,
            letterSpacing: -0.28,
            height: 1.4,
          ),
        ),
        backgroundColor: const Color(0xE6614108),
        shape: RoundedRectangleBorder(borderRadius: BorderRadius.circular(16)),
        behavior: SnackBarBehavior.floating,
        margin: const EdgeInsets.symmetric(horizontal: 16, vertical: 16),
        padding: const EdgeInsets.all(16),
        elevation: 8,
        duration: const Duration(seconds: 2),
      ),
    );
  }

  // --- POOP spot with optional health analysis ---

  Future<void> _addPoopSpot() async {
    try {
      final position = await Geolocator.getCurrentPosition();
      final latLng = LatLng(position.latitude, position.longitude);

      // Always record the POOP spot
      final spot = WalkSpotDto(
        latitude: position.latitude,
        longitude: position.longitude,
        type: 'POOP',
        timestamp: DateTime.now(),
      );
      trackingSpots.add(spot);

      final spotIcon = await _getSpotMarkerImage('POOP');
      final marker = Marker(
        markerId: MarkerId('spot_${trackingSpots.length}'),
        position: latLng,
        icon: spotIcon,
        anchor: const Offset(0.5, 0.5),
      );
      setState(() {
        trackingMarkers = {...trackingMarkers, marker};
      });
      if (mounted) showTrackingSnackBar('응가 스팟이 기록되었습니다!');

      // Pick pet for analysis — four-branch decision table
      if (!mounted) return;
      if (_trackingPetIds.isEmpty) {
        if (mounted) showTrackingSnackBar('등록된 반려동물 정보를 불러오지 못했습니다. 사진 촬영 없이 위치만 기록됩니다.');
        return;
      }
      if (_trackingPetIds.length > 1 && _trackingPets.isEmpty) {
        if (mounted) showTrackingSnackBar('반려동물 정보를 불러오지 못해 건강 분석을 건너뜁니다. 일반 사진은 사진 버튼으로 기록할 수 있어요.');
        return;
      }
      int? selectedPetId;
      if (_trackingPetIds.length == 1) {
        selectedPetId = _trackingPetIds.first;
      } else {
        selectedPetId = await _showPetSelectorSheet();
      }
      if (selectedPetId == null) return; // user-cancel: silent by design

      // Photo option
      if (!mounted) return;
      final takePhoto = await _showPoopPhotoSheet();
      if (takePhoto != true) return;

      // Take photo
      final picker = ImagePicker();
      final XFile? photo = await picker.pickImage(
        source: ImageSource.camera,
        imageQuality: 80,
        maxWidth: 1920,
        maxHeight: 1920,
      );
      if (photo == null) return;
      if (!mounted) return;

      // Crop/rotate photo (same as walk photo flow)
      String? croppedPath;
      try {
        croppedPath = await PhotoCropScreen.show(context, photo.path);
      } catch (e) {
        debugPrint('PhotoCropScreen error: $e');
        if (mounted) showTrackingSnackBar('사진 편집 오류가 발생했습니다.');
        return;
      }
      if (croppedPath == null) return;
      if (!mounted) return;

      // Upload photo
      String? imageUrl;
      try {
        final fileName = croppedPath.split('/').last;
        final formData = FormData.fromMap({
          'file': await MultipartFile.fromFile(croppedPath, filename: fileName),
          'category': 'pet',
        });
        final response = await ApiClient().dio.post(
          '/api/v1/files/upload',
          data: formData,
        );
        imageUrl = response.data['url'] as String?;
      } catch (e) {
        debugPrint('[WalkTracking] Stool photo upload failed: $e');
        if (mounted) showTrackingSnackBar('사진 업로드에 실패했습니다.');
        return;
      } finally {
        try {
          await File(croppedPath).delete();
        } catch (_) {}
        try {
          await File(photo.path).delete();
        } catch (_) {}
      }

      if (imageUrl == null || imageUrl.isEmpty) {
        if (mounted) showTrackingSnackBar('사진 업로드에 실패했습니다.');
        return;
      }

      if (!mounted) return;
      await _runStoolAnalysis(petId: selectedPetId, imageUrl: imageUrl);
    } catch (e) {
      debugPrint('[WalkTracking] Error in _addPoopSpot: $e');
    }
  }

  Future<int?> _showPetSelectorSheet() {
    return showModalBottomSheet<int>(
      context: context,
      backgroundColor: Colors.transparent,
      builder: (ctx) {
        final selectablePets = _trackingPets
            .where((p) => _trackingPetIds.contains(p['id'] as int))
            .toList();
        return Container(
          decoration: const BoxDecoration(
            color: Color(0xFFFFF7E6),
            borderRadius: BorderRadius.vertical(top: Radius.circular(20)),
          ),
          child: Column(
            mainAxisSize: MainAxisSize.min,
            children: [
              const SizedBox(height: 12),
              Container(
                width: 40,
                height: 4,
                decoration: BoxDecoration(
                  color: const Color(0xFF614108).withValues(alpha: 0.3),
                  borderRadius: BorderRadius.circular(2),
                ),
              ),
              const SizedBox(height: 20),
              const Text(
                '어떤 반려동물인가요?',
                style: TextStyle(
                  fontSize: 16,
                  fontWeight: FontWeight.w600,
                  color: Color(0xFF614108),
                ),
              ),
              const SizedBox(height: 8),
              ...selectablePets.map((pet) {
                final profileUrl = pet['profileImageUrl'] as String?;
                return ListTile(
                  leading: CircleAvatar(
                    radius: 24,
                    backgroundImage: profileUrl != null
                        ? NetworkImage(profileUrl)
                        : null,
                    backgroundColor: const Color(0xFFF5E6C8),
                    child: profileUrl == null
                        ? const Icon(Icons.pets, color: Color(0xFF614108))
                        : null,
                  ),
                  title: Text(
                    pet['name'] as String? ?? '반려동물',
                    style: const TextStyle(
                      fontWeight: FontWeight.w500,
                      color: Color(0xFF1A1A1A),
                    ),
                  ),
                  onTap: () => Navigator.of(ctx).pop(pet['id'] as int),
                );
              }),
              const SizedBox(height: 8),
              SafeArea(child: const SizedBox.shrink()),
            ],
          ),
        );
      },
    );
  }

  Future<bool?> _showPoopPhotoSheet() {
    return showModalBottomSheet<bool>(
      context: context,
      backgroundColor: Colors.transparent,
      builder: (ctx) {
        return Container(
          decoration: const BoxDecoration(
            color: Color(0xFFEFE1C4),
            borderRadius: BorderRadius.vertical(top: Radius.circular(16)),
          ),
          padding: const EdgeInsets.fromLTRB(16, 16, 16, 24),
          child: Column(
            mainAxisSize: MainAxisSize.min,
            children: [
              Container(
                width: 40,
                height: 4,
                decoration: BoxDecoration(
                  color: const Color(0xFFA58A54),
                  borderRadius: BorderRadius.circular(100),
                ),
              ),
              const SizedBox(height: 24),
              const Text(
                '건강 분석을 위해 사진 촬영하시겠습니까?',
                style: TextStyle(
                  fontSize: 14,
                  fontWeight: FontWeight.w500,
                  color: Color(0xFF505050),
                  letterSpacing: -0.28,
                  height: 1.4,
                ),
                textAlign: TextAlign.center,
              ),
              const SizedBox(height: 24),
              Row(
                children: [
                  Expanded(
                    child: SizedBox(
                      height: 48,
                      child: ElevatedButton(
                        onPressed: () => Navigator.of(ctx).pop(false),
                        style: ElevatedButton.styleFrom(
                          backgroundColor: const Color(0xFFFFF7E6),
                          foregroundColor: const Color(0xFF614108),
                          elevation: 0,
                          shape: RoundedRectangleBorder(
                            borderRadius: BorderRadius.circular(1000),
                          ),
                        ),
                        child: const Text(
                          '건너뛰기',
                          style: TextStyle(
                            fontSize: 16,
                            fontWeight: FontWeight.w500,
                            letterSpacing: -0.32,
                          ),
                        ),
                      ),
                    ),
                  ),
                  const SizedBox(width: 8),
                  Expanded(
                    child: SizedBox(
                      height: 48,
                      child: ElevatedButton(
                        onPressed: () => Navigator.of(ctx).pop(true),
                        style: ElevatedButton.styleFrom(
                          backgroundColor: const Color(0xFF614108),
                          foregroundColor: Colors.white,
                          elevation: 0,
                          shape: RoundedRectangleBorder(
                            borderRadius: BorderRadius.circular(1000),
                          ),
                        ),
                        child: const Text(
                          '사진 촬영',
                          style: TextStyle(
                            fontSize: 16,
                            fontWeight: FontWeight.w500,
                            letterSpacing: -0.32,
                          ),
                        ),
                      ),
                    ),
                  ),
                ],
              ),
              SafeArea(top: false, child: const SizedBox.shrink()),
            ],
          ),
        );
      },
    );
  }

  Future<bool> _showWalkSaveAlert({
    required int minDuration,
    required int minDistance,
    required int currentDuration,
    required int currentDistance,
  }) async {
    final result = await showDialog<bool>(
      context: context,
      barrierColor: Colors.black.withValues(alpha: 0.7),
      barrierDismissible: false,
      builder: (ctx) {
        return Dialog(
          backgroundColor: Colors.transparent,
          insetPadding: const EdgeInsets.symmetric(horizontal: 20),
          child: Container(
            width: 320,
            decoration: BoxDecoration(
              color: const Color(0xFFFFF7E6),
              borderRadius: BorderRadius.circular(16),
            ),
            padding: const EdgeInsets.fromLTRB(12, 40, 12, 16),
            child: Column(
              mainAxisSize: MainAxisSize.min,
              children: [
                Column(
                  mainAxisSize: MainAxisSize.min,
                  crossAxisAlignment: CrossAxisAlignment.center,
                  children: [
                    const Text(
                      '산책 기록을 저장할 수 없습니다.',
                      textAlign: TextAlign.center,
                      style: TextStyle(
                        fontSize: 14,
                        fontWeight: FontWeight.w500,
                        color: Color(0xFF505050),
                        letterSpacing: -0.28,
                        height: 1.4,
                      ),
                    ),
                    const SizedBox(height: 8),
                    Container(
                      width: 204,
                      padding: const EdgeInsets.all(16),
                      decoration: BoxDecoration(
                        color: const Color(0xFFF9ECD2),
                        borderRadius: BorderRadius.circular(8),
                      ),
                      child: Column(
                        crossAxisAlignment: CrossAxisAlignment.start,
                        children: [
                          Text(
                            '최소 조건 : $minDuration초이상, ${minDistance}m 이상',
                            style: const TextStyle(
                              fontSize: 14,
                              fontWeight: FontWeight.w500,
                              color: Color(0xFF505050),
                              letterSpacing: -0.28,
                              height: 1.4,
                            ),
                          ),
                          const SizedBox(height: 4),
                          Text(
                            '현재 기록 : $currentDuration초, ${currentDistance}m',
                            style: const TextStyle(
                              fontSize: 14,
                              fontWeight: FontWeight.w700,
                              color: Color(0xFF505050),
                              letterSpacing: -0.28,
                              height: 1.4,
                            ),
                          ),
                        ],
                      ),
                    ),
                    const SizedBox(height: 8),
                    const Text(
                      '산책을 계속하시겠습니까?',
                      textAlign: TextAlign.center,
                      style: TextStyle(
                        fontSize: 14,
                        fontWeight: FontWeight.w500,
                        color: Color(0xFF505050),
                        letterSpacing: -0.28,
                        height: 1.4,
                      ),
                    ),
                  ],
                ),
                const SizedBox(height: 40),
                Row(
                  children: [
                    Expanded(
                      child: SizedBox(
                        height: 48,
                        child: ElevatedButton(
                          onPressed: () => Navigator.of(ctx).pop(false),
                          style: ElevatedButton.styleFrom(
                            backgroundColor: const Color(0xFFEFE1C4),
                            foregroundColor: const Color(0xFF614108),
                            elevation: 0,
                            shape: RoundedRectangleBorder(
                              borderRadius: BorderRadius.circular(1000),
                            ),
                          ),
                          child: const Text(
                            '삭제',
                            style: TextStyle(
                              fontSize: 16,
                              fontWeight: FontWeight.w500,
                              letterSpacing: -0.32,
                            ),
                          ),
                        ),
                      ),
                    ),
                    const SizedBox(width: 8),
                    Expanded(
                      child: SizedBox(
                        height: 48,
                        child: ElevatedButton(
                          onPressed: () => Navigator.of(ctx).pop(true),
                          style: ElevatedButton.styleFrom(
                            backgroundColor: const Color(0xFF614108),
                            foregroundColor: Colors.white,
                            elevation: 0,
                            shape: RoundedRectangleBorder(
                              borderRadius: BorderRadius.circular(1000),
                            ),
                          ),
                          child: const Text(
                            '산책 계속',
                            style: TextStyle(
                              fontSize: 16,
                              fontWeight: FontWeight.w500,
                              letterSpacing: -0.32,
                            ),
                          ),
                        ),
                      ),
                    ),
                  ],
                ),
              ],
            ),
          ),
        );
      },
    );
    return result ?? false;
  }

  Future<void> _runStoolAnalysis({
    required int petId,
    required String imageUrl,
  }) async {
    if (!mounted) return;

    // Show analyzing overlay
    showDialog(
      context: context,
      barrierDismissible: false,
      builder: (_) => const _AnalyzingDialog(),
    );

    try {
      final response = await ApiClient().dio.post(
        '/api/v1/health/stool-analyses',
        data: {'petId': petId, 'imageUrl': imageUrl},
      );
      final analysisId = response.data['id'] as int;

      // Poll every 2 seconds, max 30 attempts (60s)
      Map<String, dynamic>? result;
      for (var i = 0; i < 30; i++) {
        await Future.delayed(const Duration(seconds: 2));
        if (!mounted) return;

        try {
          final poll = await ApiClient().dio.get(
            '/api/v1/health/stool-analyses/$analysisId',
          );
          final data = poll.data as Map<String, dynamic>;
          final status = data['status'] as String;
          if (status == 'COMPLETED') {
            result = data;
            break;
          } else if (status == 'FAILED') {
            break;
          }
        } catch (e) {
          debugPrint('[WalkTracking] Poll error: $e');
        }
      }

      if (!mounted) return;
      Navigator.of(
        context,
        rootNavigator: true,
      ).pop(); // close analyzing dialog

      if (result != null) {
        final overallScore = result['overallScore'] as int? ?? 0;
        if (overallScore == 0) {
          // AI determined the image is not poop
          if (mounted) {
            await showGoldPetAlert(
              context,
              message: '응가 이미지가 아닙니다.',
              confirmText: '확인',
            );
          }
        } else {
          if (mounted) {
            showDialog(
              context: context,
              barrierDismissible: true,
              builder: (_) => _AnalysisResultDialog(result: result!),
            );
          }
        }
      } else {
        if (mounted) showTrackingSnackBar('분석에 실패했습니다. 다시 시도해주세요.');
      }
    } catch (e) {
      debugPrint('[WalkTracking] Analysis error: $e');
      if (mounted) {
        Navigator.of(
          context,
          rootNavigator: true,
        ).pop(); // close analyzing dialog
        showTrackingSnackBar('분석 요청에 실패했습니다.');
      }
    }
  }
}

// --- Analysis overlay dialogs ---

class _AnalyzingDialog extends StatelessWidget {
  const _AnalyzingDialog();

  @override
  Widget build(BuildContext context) {
    return const Dialog(
      backgroundColor: Colors.transparent,
      elevation: 0,
      child: Column(
        mainAxisSize: MainAxisSize.min,
        children: [
          CircularProgressIndicator(color: Color(0xFF614108)),
          SizedBox(height: 16),
          Text(
            '분석 중...',
            style: TextStyle(
              color: Colors.white,
              fontSize: 16,
              fontWeight: FontWeight.w600,
              decoration: TextDecoration.none,
            ),
          ),
        ],
      ),
    );
  }
}

class _AnalysisResultDialog extends StatefulWidget {
  final Map<String, dynamic> result;
  const _AnalysisResultDialog({required this.result});

  @override
  State<_AnalysisResultDialog> createState() => _AnalysisResultDialogState();
}

class _AnalysisResultDialogState extends State<_AnalysisResultDialog> {
  Color _barFillColor(int score) {
    if (score <= 2) return const Color(0xFFFF7F0F);
    if (score == 3) return const Color(0xFFFFE30F);
    if (score == 4) return const Color(0xFF0F97FF);
    return const Color(0xFF34C759);
  }

  @override
  Widget build(BuildContext context) {
    final colorScore = widget.result['colorScore'] as int? ?? 0;
    final consistencyScore = widget.result['consistencyScore'] as int? ?? 0;
    final coatingScore = widget.result['coatingScore'] as int? ?? 0;
    final contentsScore = widget.result['contentsScore'] as int? ?? 0;
    final colorAssessment = widget.result['colorAssessment'] as String? ?? '';
    final consistencyAssessment =
        widget.result['consistencyAssessment'] as String? ?? '';
    final coatingAssessment =
        widget.result['coatingAssessment'] as String? ?? '';
    final contentsAssessment =
        widget.result['contentsAssessment'] as String? ?? '';

    return Dialog(
      shape: RoundedRectangleBorder(borderRadius: BorderRadius.circular(16)),
      backgroundColor: const Color(0xFFFFF7E6),
      insetPadding: const EdgeInsets.symmetric(horizontal: 20),
      child: SingleChildScrollView(
        child: Padding(
          padding: const EdgeInsets.symmetric(horizontal: 12, vertical: 24),
          child: Column(
            mainAxisSize: MainAxisSize.min,
            crossAxisAlignment: CrossAxisAlignment.stretch,
            children: [
              Align(
                alignment: Alignment.centerRight,
                child: GestureDetector(
                  onTap: () => Navigator.of(context, rootNavigator: true).pop(),
                  child: const SizedBox(
                    width: 24,
                    height: 24,
                    child: Icon(
                      Icons.close,
                      size: 20,
                      color: Color(0xFF614108),
                    ),
                  ),
                ),
              ),
              const SizedBox(height: 16),
              const Padding(
                padding: EdgeInsets.symmetric(horizontal: 0),
                child: Text(
                  '건강 분석 결과',
                  style: TextStyle(
                    fontSize: 16,
                    fontWeight: FontWeight.w700,
                    color: Color(0xFF614108),
                    letterSpacing: -0.32,
                    height: 1.4,
                  ),
                ),
              ),
              const SizedBox(height: 25),
              IntrinsicHeight(
                child: Row(
                  crossAxisAlignment: CrossAxisAlignment.stretch,
                  children: [
                    Expanded(
                      child: _buildCategoryCard(
                        '색상(Color)',
                        colorScore,
                        colorAssessment,
                      ),
                    ),
                    const SizedBox(width: 16),
                    Expanded(
                      child: _buildCategoryCard(
                        '농도(Consistency)',
                        consistencyScore,
                        consistencyAssessment,
                      ),
                    ),
                  ],
                ),
              ),
              const SizedBox(height: 8),
              IntrinsicHeight(
                child: Row(
                  crossAxisAlignment: CrossAxisAlignment.stretch,
                  children: [
                    Expanded(
                      child: _buildCategoryCard(
                        '코팅(Coating)',
                        coatingScore,
                        coatingAssessment,
                      ),
                    ),
                    const SizedBox(width: 16),
                    Expanded(
                      child: _buildCategoryCard(
                        '내용물(Contents)',
                        contentsScore,
                        contentsAssessment,
                      ),
                    ),
                  ],
                ),
              ),
            ],
          ),
        ),
      ),
    );
  }

  Widget _buildCategoryCard(String label, int score, String description) {
    return Column(
      crossAxisAlignment: CrossAxisAlignment.start,
      mainAxisSize: MainAxisSize.min,
      children: [
        Padding(
          padding: const EdgeInsets.symmetric(vertical: 8),
          child: Text(
            label,
            style: const TextStyle(
              fontSize: 14,
              fontWeight: FontWeight.w500,
              color: Color(0xFF614108),
              letterSpacing: -0.28,
              height: 1.0,
            ),
          ),
        ),
        const SizedBox(height: 8),
        Container(
          padding: const EdgeInsets.fromLTRB(16, 12, 16, 12),
          decoration: BoxDecoration(
            color: Colors.white,
            borderRadius: BorderRadius.circular(16),
            border: Border.all(color: const Color(0xFFF1F1F1), width: 2),
          ),
          child: Column(
            crossAxisAlignment: CrossAxisAlignment.center,
            mainAxisSize: MainAxisSize.min,
            children: [
              SizedBox(
                height: 34,
                child: Center(
                  child: Text.rich(
                    TextSpan(
                      children: [
                        TextSpan(
                          text: '$score',
                          style: const TextStyle(
                            fontSize: 20,
                            fontWeight: FontWeight.w900,
                            color: Color(0xFF614108),
                            letterSpacing: -0.32,
                            height: 1.0,
                          ),
                        ),
                        const TextSpan(
                          text: '/5',
                          style: TextStyle(
                            fontSize: 12,
                            fontWeight: FontWeight.w500,
                            color: Color(0xFF614108),
                            letterSpacing: -0.32,
                            height: 1.0,
                          ),
                        ),
                      ],
                    ),
                  ),
                ),
              ),
              const SizedBox(height: 10),
              SizedBox(
                width: 50,
                height: 8,
                child: Stack(
                  children: [
                    Container(
                      decoration: BoxDecoration(
                        color: const Color(0xFFAAAAAA),
                        borderRadius: BorderRadius.circular(1000),
                      ),
                    ),
                    FractionallySizedBox(
                      alignment: Alignment.centerLeft,
                      widthFactor: (score.clamp(0, 5)) / 5.0,
                      child: Container(
                        decoration: BoxDecoration(
                          color: _barFillColor(score),
                          borderRadius: BorderRadius.circular(1000),
                        ),
                      ),
                    ),
                  ],
                ),
              ),
              const SizedBox(height: 12),
              Text(
                description,
                maxLines: 4,
                overflow: TextOverflow.ellipsis,
                style: const TextStyle(
                  fontSize: 12,
                  fontWeight: FontWeight.w500,
                  color: Color(0xFF505050),
                  letterSpacing: -0.24,
                  height: 1.4,
                ),
              ),
            ],
          ),
        ),
      ],
    );
  }
}
