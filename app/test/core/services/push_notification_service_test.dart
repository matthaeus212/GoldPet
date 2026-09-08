// ignore_for_file: invalid_use_of_visible_for_testing_member

import 'package:flutter_secure_storage/flutter_secure_storage.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:goldpet_app/core/services/push_notification_service.dart';

void main() {
  // FlutterSecureStorage mock requires the Flutter binding to be initialized.
  TestWidgetsFlutterBinding.ensureInitialized();

  group('PushNotificationService.handleFcmTokenRefresh', () {
    setUp(() {
      // Reset the singleton's test override before each test.
      PushNotificationService().testRegisterDeviceOverride = null;
    });

    test(
      'reads CURRENT access token from storage — not the stale closure value',
      () async {
        // Seed storage with a fresh JWT (simulates rotation after initial login).
        FlutterSecureStorage.setMockInitialValues({'ACCESS_TOKEN': 'fresh-jwt-xyz'});

        final calls = <({String fcmToken, String accessToken})>[];
        PushNotificationService().testRegisterDeviceOverride =
            (fcmToken, accessToken) async {
          calls.add((fcmToken: fcmToken, accessToken: accessToken));
        };

        await PushNotificationService().handleFcmTokenRefresh('new-fcm-token');

        expect(calls.length, 1, reason: '_registerDevice must be called once');
        expect(calls.first.fcmToken, 'new-fcm-token');
        expect(
          calls.first.accessToken,
          'fresh-jwt-xyz',
          reason: 'must use the current token from storage, not a stale closure value',
        );
      },
    );

    test(
      'skips registration when no access token is present in storage',
      () async {
        // Empty storage — user logged out or token never written.
        FlutterSecureStorage.setMockInitialValues({});

        var called = false;
        PushNotificationService().testRegisterDeviceOverride =
            (_, _) async => called = true;

        await PushNotificationService().handleFcmTokenRefresh('new-fcm-token');

        expect(
          called,
          isFalse,
          reason: 'must not attempt registration when no JWT is available',
        );
      },
    );
  });
}
