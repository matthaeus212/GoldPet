import 'dart:async';
import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:google_maps_flutter/google_maps_flutter.dart';
import 'package:flutter_svg/flutter_svg.dart';
import '../../../core/theme/app_theme.dart';
import '../../../core/webview/hybrid_webview.dart';
import '../mixins/walk_tracking_mixin.dart';
import '../widgets/photo_memo_sheet.dart';
import '../widgets/walk_compass_indicator.dart';

/// Global stream for delivering spot actions to an existing WalkScreen
/// without pushing a duplicate. Used by deep link handler in hybrid_app.dart.
final walkSpotActionController = StreamController<String>.broadcast();

class WalkScreen extends ConsumerStatefulWidget {
  final String? initialSpotAction;
  final bool isPublic;
  final List<int>? petIds;

  /// Singleton guard: true when a WalkScreen is on the Navigator stack.
  /// Set in initState(), cleared in dispose(). Used by hybrid_app and native_bridge
  /// to prevent duplicate WalkScreen pushes from deep links or bridge calls.
  static bool isOnStack = false;

  /// Set by HybridWebView when auth expires during an active walk.
  /// The auth expiry dialog is deferred until the walk ends naturally.
  static bool authExpiredDuringWalk = false;

  const WalkScreen({super.key, this.initialSpotAction, this.isPublic = true, this.petIds});

  @override
  ConsumerState<WalkScreen> createState() => _WalkScreenState();
}

class _WalkScreenState extends ConsumerState<WalkScreen>
    with WidgetsBindingObserver, WalkTrackingMixin {
  final Key _mapKey = UniqueKey();

  StreamSubscription<String>? _spotActionSubscription;
  String? _pendingSpotAction;

  final DraggableScrollableController _sheetController = DraggableScrollableController();

  // Design colors (from Figma)
  static const Color _panelBg = Color(0xFFEFE1C4);
  static const Color _actionBarBg = Color(0xFFF9ECD2);
  static const Color _handleColor = Color(0xFFA58A54);

  // Header constants
  static const double _headerToolbarHeight = 56.0;

  // Sheet size helpers
  double get _bottomSafeArea => MediaQuery.of(context).padding.bottom;
  double get _headerTotalHeight => MediaQuery.of(context).padding.top + _headerToolbarHeight;
  double get _mapAreaHeight => MediaQuery.of(context).size.height - _headerTotalHeight;
  double get _collapsedSheetSize => (60.0 + _bottomSafeArea) / _mapAreaHeight;
  double get _expandedSheetSize => (240.0 + _bottomSafeArea) / _mapAreaHeight;

  // --- Mixin overrides ---

  @override
  dynamic get walkRef => ref;

  @override
  void onWalkSaved() {
    // Handle deferred auth expiry
    if (WalkScreen.authExpiredDuringWalk) {
      WalkScreen.authExpiredDuringWalk = false;
      Navigator.of(context).popUntil((route) => route.isFirst);
      WidgetsBinding.instance.addPostFrameCallback((_) {
        // ignore: deprecated_member_use_from_same_package
        // ApiClient.onAuthExpired?.call();
      });
      return;
    }
    Navigator.of(context).pop();
    HybridWebView.ensureContentAlive?.call();
  }

  @override
  void onWalkComplete(int walkId) async {
    if (WalkScreen.authExpiredDuringWalk) {
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

  // --- Lifecycle ---

  @override
  void initState() {
    super.initState();
    WalkScreen.isOnStack = true;
    WidgetsBinding.instance.addObserver(this);

    // Subscribe to global spot action stream
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

    initTracking(isPublic: widget.isPublic, petIds: widget.petIds);

    // Handle initial spot action from deep link
    if (widget.initialSpotAction != null) {
      Future.delayed(const Duration(seconds: 2), () {
        if (mounted && trackingIsWalking) {
          addSpot(widget.initialSpotAction!);
        }
      });
    }
  }

  @override
  void dispose() {
    debugPrint('[WalkScreen] dispose() called, isWalking=$trackingIsWalking');
    disposeTracking();
    WalkScreen.isOnStack = false;
    WalkScreen.authExpiredDuringWalk = false;
    WidgetsBinding.instance.removeObserver(this);
    _spotActionSubscription?.cancel();
    _sheetController.dispose();
    super.dispose();
  }

  @override
  void didChangeAppLifecycleState(AppLifecycleState state) {
    if (state == AppLifecycleState.resumed) {
      handleAppResumed();
    }
  }

  // --- Walk start override to handle pending spot action ---

  @override
  Future<void> startWalk() async {
    await super.startWalk();

    // Execute any pending spot action that arrived before walk started
    if (_pendingSpotAction != null) {
      final action = _pendingSpotAction!;
      _pendingSpotAction = null;
      Future.delayed(const Duration(seconds: 2), () {
        if (mounted && trackingIsWalking) addSpot(action);
      });
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

  // --- Build methods ---

  Widget _buildHeader() {
    return Container(
      color: AppColors.background,
      child: SafeArea(
        bottom: false,
        child: SizedBox(
          height: _headerToolbarHeight,
          child: Align(
            alignment: Alignment.centerLeft,
            child: GestureDetector(
              onTap: _handleBackNavigation,
              child: Padding(
                padding: const EdgeInsets.only(left: 16),
                child: Icon(
                  Icons.arrow_back_ios_new,
                  size: 20,
                  color: AppColors.primary,
                ),
              ),
            ),
          ),
        ),
      ),
    );
  }

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

                if (trackingIsWalking && trackingCurrentAddress.isNotEmpty)
                  Positioned(
                    top: 12,
                    right: 16,
                    child: _LocationChip(address: trackingCurrentAddress),
                  ),

                if (trackingIsWalking && trackingCompassModeEnabled)
                  Positioned(
                    top: 12,
                    left: 16,
                    child: ValueListenableBuilder<double>(
                      valueListenable: trackingBearingNotifier,
                      builder: (context, bearing, _) =>
                          WalkCompassIndicator(bearing: bearing),
                    ),
                  ),

                if (trackingIsWalking)
                  _buildInfoPanel(),

                if (trackingIsWalking)
                  _buildWalkControls(),

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
                              style: TextStyle(
                                color: Colors.white,
                                fontSize: 16,
                                fontWeight: FontWeight.w600,
                              ),
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
            boxShadow: [
              BoxShadow(color: Colors.black12, blurRadius: 8, offset: Offset(0, -2)),
            ],
          ),
          child: SingleChildScrollView(
            controller: scrollController,
            child: Column(
              children: [
                _buildDragHandle(),
                _buildStatsRow(),
                const SizedBox(height: 16),
                _buildActionButtonsRow(),
                SizedBox(height: _bottomSafeArea),
              ],
            ),
          ),
        );
      },
    );
  }

  void _toggleSheet() {
    final isExpanded = _sheetController.size >= _expandedSheetSize - 0.01;
    _sheetController.animateTo(
      isExpanded ? _collapsedSheetSize : _expandedSheetSize,
      duration: const Duration(milliseconds: 300),
      curve: Curves.easeInOut,
    );
  }

  Widget _buildDragHandle() {
    return GestureDetector(
      onTap: _toggleSheet,
      behavior: HitTestBehavior.opaque,
      child: Container(
        padding: const EdgeInsets.symmetric(vertical: 12),
        child: Center(
          child: Container(
            width: 40,
            height: 4,
            decoration: BoxDecoration(
              color: _handleColor,
              borderRadius: BorderRadius.circular(2),
            ),
          ),
        ),
      ),
    );
  }

  Widget _buildStatsRow() {
    return Padding(
      padding: const EdgeInsets.symmetric(horizontal: 16),
      child: Row(
        children: [
          Expanded(
            child: ValueListenableBuilder<double>(
              valueListenable: trackingDistanceNotifier,
              builder: (context, distance, _) {
                return _WalkStat(
                  value: distance.toStringAsFixed(2),
                  unit: 'km',
                  label: '거리',
                );
              },
            ),
          ),
          Expanded(
            child: ValueListenableBuilder<int>(
              valueListenable: trackingDurationNotifier,
              builder: (context, seconds, _) {
                return _WalkStat(
                  value: formatDuration(seconds),
                  label: '시간',
                );
              },
            ),
          ),
          Expanded(
            child: ValueListenableBuilder<double>(
              valueListenable: trackingDistanceNotifier,
              builder: (context, distance, _) {
                return _WalkStat(
                  value: (distance * 50).toStringAsFixed(0),
                  unit: 'Kcal',
                  label: '칼로리',
                );
              },
            ),
          ),
        ],
      ),
    );
  }

  Widget _buildActionButtonsRow() {
    return Padding(
      padding: const EdgeInsets.symmetric(horizontal: 16),
      child: Container(
        padding: const EdgeInsets.symmetric(vertical: 12, horizontal: 8),
        decoration: BoxDecoration(
          color: _actionBarBg,
          borderRadius: BorderRadius.circular(12),
        ),
        child: Row(
          mainAxisAlignment: MainAxisAlignment.spaceEvenly,
          children: [
            _SpotButton(
              label: '응가',
              svgAsset: 'assets/images/walk/icon_poop.svg',
              onTap: () => addSpot('POOP'),
            ),
            _SpotButton(
              label: '쉬',
              svgAsset: 'assets/images/walk/icon_pee.svg',
              onTap: () => addSpot('PEE'),
            ),
            _SpotButton(
              label: '사진',
              svgAsset: 'assets/images/walk/icon_camera.svg',
              onTap: () => addSpot('PHOTO'),
            ),
          ],
        ),
      ),
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
        return Positioned(
          right: 16,
          bottom: sheetHeight + 16,
          child: child!,
        );
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
              width: 48,
              height: 48,
              decoration: BoxDecoration(
                color: AppColors.primary,
                shape: BoxShape.circle,
                boxShadow: [
                  BoxShadow(
                    color: const Color(0xFF353D4C).withValues(alpha: 0.1),
                    blurRadius: 20,
                    offset: const Offset(0, 12),
                  ),
                ],
              ),
              child: Center(
                child: Container(
                  width: 14,
                  height: 14,
                  decoration: BoxDecoration(
                    color: _handleColor,
                    borderRadius: BorderRadius.circular(1),
                  ),
                ),
              ),
            ),
          ),
          const SizedBox(width: 10),
          // Compass mode button (3-state)
          GestureDetector(
            onTap: () async {
              if (trackingCompassModeEnabled && !trackingIsFollowing) {
                // ON-paused: re-engage follow without toggling compass off
                recenterCamera();
              } else {
                await toggleCompassMode();
                // If compass just turned ON while panned, re-engage follow
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
                boxShadow: [
                  BoxShadow(
                    color: const Color(0xFF353D4C).withValues(alpha: 0.1),
                    blurRadius: 20,
                    offset: const Offset(0, 12),
                  ),
                ],
              ),
              child: Center(
                child: Icon(
                  trackingCompassModeEnabled && trackingIsFollowing
                      ? Icons.explore
                      : Icons.explore_outlined,
                  size: 24,
                  color: trackingCompassModeEnabled && trackingIsFollowing
                      ? Colors.white
                      : _handleColor,
                ),
              ),
            ),
          ),
          const SizedBox(width: 10),
          // Play/Pause button
          GestureDetector(
            onTap: togglePause,
            child: Container(
              width: 48,
              height: 48,
              decoration: BoxDecoration(
                color: AppColors.primary,
                shape: BoxShape.circle,
                boxShadow: [
                  BoxShadow(
                    color: const Color(0xFF353D4C).withValues(alpha: 0.1),
                    blurRadius: 20,
                    offset: const Offset(0, 12),
                  ),
                ],
              ),
              child: Center(
                child: trackingIsPaused
                    ? CustomPaint(
                        size: const Size(14, 14),
                        painter: _PlayIconPainter(color: _handleColor),
                      )
                    : Row(
                        mainAxisSize: MainAxisSize.min,
                        children: [
                          Container(
                            width: 6,
                            height: 14,
                            decoration: BoxDecoration(
                              color: _handleColor,
                              borderRadius: BorderRadius.circular(1),
                            ),
                          ),
                          const SizedBox(width: 3),
                          Container(
                            width: 6,
                            height: 14,
                            decoration: BoxDecoration(
                              color: _handleColor,
                              borderRadius: BorderRadius.circular(1),
                            ),
                          ),
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

// --- Extracted widgets ---

class _WalkStat extends StatelessWidget {
  final String value;
  final String? unit;
  final String label;

  const _WalkStat({required this.value, this.unit, required this.label});

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
                child: Text(
                  value,
                  style: const TextStyle(
                    fontSize: 18,
                    fontWeight: FontWeight.bold,
                    color: AppColors.primary,
                  ),
                ),
              ),
            ),
            if (unit != null) ...[
              const SizedBox(width: 2),
              Text(
                unit!,
                style: const TextStyle(
                  fontSize: 11,
                  fontWeight: FontWeight.w500,
                  color: AppColors.primary,
                ),
              ),
            ],
          ],
        ),
        const SizedBox(height: 4),
        Text(
          label,
          style: const TextStyle(fontSize: 10, color: Color(0xFF727272)),
        ),
      ],
    );
  }
}

class _SpotButton extends StatelessWidget {
  final String label;
  final String svgAsset;
  final VoidCallback onTap;

  const _SpotButton({
    required this.label,
    required this.svgAsset,
    required this.onTap,
  });

  @override
  Widget build(BuildContext context) {
    return InkWell(
      onTap: onTap,
      borderRadius: BorderRadius.circular(12),
      child: Padding(
        padding: const EdgeInsets.symmetric(horizontal: 16, vertical: 4),
        child: Column(
          children: [
            SizedBox(
              width: 40,
              height: 40,
              child: SvgPicture.asset(svgAsset),
            ),
            const SizedBox(height: 8),
            Text(
              label,
              style: const TextStyle(
                fontSize: 10,
                fontWeight: FontWeight.w500,
                color: Color(0xFF727272),
              ),
            ),
          ],
        ),
      ),
    );
  }
}

class _LocationChip extends StatelessWidget {
  final String address;
  const _LocationChip({required this.address});

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
            child: Text(
              address,
              style: const TextStyle(fontSize: 12, color: AppColors.textPrimary),
              overflow: TextOverflow.ellipsis,
            ),
          ),
        ],
      ),
    );
  }
}

class _PlayIconPainter extends CustomPainter {
  final Color color;
  _PlayIconPainter({required this.color});

  @override
  void paint(Canvas canvas, Size size) {
    final paint = Paint()
      ..color = color
      ..style = PaintingStyle.fill;
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
