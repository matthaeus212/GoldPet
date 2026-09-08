import 'dart:math' as math;

import 'package:flutter/material.dart';

import '../../../core/theme/app_theme.dart';

/// Custom compass rose indicator for the walk map.
///
/// Always visible while walking with compass mode ON. The needle always points
/// to true north, so when the map rotates (bearing != 0) the needle appears to
/// rotate accordingly — this is achieved by counter-rotating the needle by
/// -bearing.
///
/// Ships because `google_maps_flutter` v2.10.0 does not render the native
/// Google Maps compass rose on iOS in all configurations — verified by the
/// Step 0 spike (heading arrives, bearing applies, map rotates, but no native
/// rose renders on iPhone 16 iOS 26.3). On Android we suppress the native rose
/// via `compassEnabled: false` on the `GoogleMap` widget so this custom
/// indicator is the single source of truth.
class WalkCompassIndicator extends StatelessWidget {
  const WalkCompassIndicator({
    required this.bearing,
    super.key,
  });

  /// Map camera bearing in degrees (0 = north-up).
  final double bearing;

  @override
  Widget build(BuildContext context) {
    return Container(
      width: 44,
      height: 44,
      decoration: BoxDecoration(
        color: Colors.white,
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
        child: Transform.rotate(
          // Counter-rotate the needle so it always points to true north
          // regardless of the map's current bearing.
          angle: -bearing * math.pi / 180,
          child: const Icon(
            Icons.navigation,
            size: 22,
            color: AppColors.primary,
          ),
        ),
      ),
    );
  }
}
