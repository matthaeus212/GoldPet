// ignore_for_file: invalid_use_of_visible_for_testing_member

import 'dart:async';

import 'package:flutter/material.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:google_maps_flutter/google_maps_flutter.dart';
import 'package:goldpet_app/features/walk/mixins/walk_tracking_mixin.dart';

// ---------------------------------------------------------------------------
// Minimal test double that mixes in WalkTrackingMixin without heavy
// dependencies (GPS, GoogleMaps, Riverpod, real HTTP).
// ---------------------------------------------------------------------------

class _FakeWalkWidget extends StatefulWidget {
  const _FakeWalkWidget();

  @override
  State<_FakeWalkWidget> createState() => _FakeWalkState();
}

class _FakeWalkState extends State<_FakeWalkWidget>
    with WidgetsBindingObserver, WalkTrackingMixin {
  @override
  dynamic get walkRef => null; // no Riverpod — dispose path falls back to ApiClient

  @override
  void onWalkSaved() {}

  @override
  Future<Map<String, dynamic>?> showPhotoMemoSheet(String photoPath) async =>
      null;

  @override
  Widget build(BuildContext context) => const SizedBox();

  @override
  void initState() {
    super.initState();
    WidgetsBinding.instance.addObserver(this);
    // Intentionally skip initTracking() — no GPS needed for dispose tests.
  }

  @override
  void dispose() {
    WidgetsBinding.instance.removeObserver(this);
    disposeTracking();
    super.dispose();
  }
}

// ---------------------------------------------------------------------------
// Tests
// ---------------------------------------------------------------------------

void main() {
  group('WALK-05: WalkScreen dispose guard', () {
    testWidgets(
      'dispose() fires server save when trackingIsWalking=true and path >= 2',
      (tester) async {
        // Arrange
        final saveCompleter = Completer<bool>();

        await tester.pumpWidget(
          const MaterialApp(home: _FakeWalkWidget()),
        );

        final state =
            tester.state<_FakeWalkState>(find.byType(_FakeWalkWidget));

        // Install test hook — intercepts the server save without real HTTP.
        state.serverSaveOnDisposeOverride = (_) async {
          if (!saveCompleter.isCompleted) saveCompleter.complete(true);
        };

        // Simulate an active 5-minute walk with 2 GPS points.
        state.testOnlyInitWalkState(
          startTime: DateTime.now().subtract(const Duration(minutes: 5)),
          path: const [
            LatLng(37.5665, 126.9780),
            LatLng(37.5670, 126.9785),
          ],
          distanceKm: 0.05,
        );
        expect(state.trackingIsWalking, isTrue);

        // Act: replace widget tree → triggers dispose() on _FakeWalkState.
        await tester.pumpWidget(const MaterialApp(home: SizedBox()));
        // Drain microtasks so the unawaited(_stopWalkOnDispose) future runs.
        await tester.pump();

        // Assert
        expect(
          saveCompleter.isCompleted,
          isTrue,
          reason:
              'disposeTracking() must fire the server save when the walk was active',
        );
      },
    );

    testWidgets(
      'dispose() does NOT fire server save when trackingIsWalking=false',
      (tester) async {
        // Arrange
        var serverSaveFired = false;

        await tester.pumpWidget(
          const MaterialApp(home: _FakeWalkWidget()),
        );

        final state =
            tester.state<_FakeWalkState>(find.byType(_FakeWalkWidget));

        state.serverSaveOnDisposeOverride = (_) async {
          serverSaveFired = true;
        };

        // trackingIsWalking defaults to false — no testOnlyInitWalkState call.

        // Act
        await tester.pumpWidget(const MaterialApp(home: SizedBox()));
        await tester.pump();

        // Assert
        expect(
          serverSaveFired,
          isFalse,
          reason: 'no server save should fire when no walk was in progress',
        );
      },
    );

    testWidgets(
      'dispose() does NOT fire server save when path has fewer than 2 points',
      (tester) async {
        // Arrange
        var serverSaveFired = false;

        await tester.pumpWidget(
          const MaterialApp(home: _FakeWalkWidget()),
        );

        final state =
            tester.state<_FakeWalkState>(find.byType(_FakeWalkWidget));

        state.serverSaveOnDisposeOverride = (_) async {
          serverSaveFired = true;
        };

        // Walking=true but only 1 GPS point — minimum-path guard applies.
        state.testOnlyInitWalkState(
          startTime: DateTime.now().subtract(const Duration(seconds: 10)),
          path: const [LatLng(37.5665, 126.9780)], // single point
          distanceKm: 0.001,
        );

        // Act
        await tester.pumpWidget(const MaterialApp(home: SizedBox()));
        await tester.pump();

        // Assert
        expect(
          serverSaveFired,
          isFalse,
          reason: 'no server save should fire when path has fewer than 2 points',
        );
      },
    );
  });
}
