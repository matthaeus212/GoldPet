-- Lower minimum/latest app version to 0.1.0 so the force update dialog
-- does not block development builds (pubspec.yaml version: 0.1.0).
UPDATE system_settings SET setting_value = '0.1.0', updated_at = CURRENT_TIMESTAMP
WHERE setting_key IN (
    'app.version.android.minimum',
    'app.version.android.latest',
    'app.version.ios.minimum',
    'app.version.ios.latest'
);
