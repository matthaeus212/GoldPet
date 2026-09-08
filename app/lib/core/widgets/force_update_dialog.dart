import 'package:flutter/material.dart';
import 'package:url_launcher/url_launcher.dart';
import '../services/app_version_service.dart';

/// 강제 업데이트 다이얼로그 (닫기 불가)
/// PopScope(canPop: false)로 뒤로가기/스와이프 차단
class ForceUpdateDialog extends StatelessWidget {
  final String updateUrl;
  final String? updateMessage;

  const ForceUpdateDialog({
    super.key,
    required this.updateUrl,
    this.updateMessage,
  });

  Future<void> _openStore() async {
    final uri = Uri.tryParse(updateUrl);
    if (uri == null) return;
    if (await canLaunchUrl(uri)) {
      await launchUrl(uri, mode: LaunchMode.externalApplication);
    }
  }

  @override
  Widget build(BuildContext context) {
    return PopScope(
      canPop: false,
      child: Center(
        child: Material(
          color: Colors.transparent,
          child: Container(
            width: 320,
            padding: const EdgeInsets.fromLTRB(12, 40, 12, 16),
            decoration: BoxDecoration(
              color: const Color(0xFFFFF7E6),
              borderRadius: BorderRadius.circular(16),
            ),
            child: Column(
              mainAxisSize: MainAxisSize.min,
              children: [
                Padding(
                  padding: const EdgeInsets.symmetric(horizontal: 4),
                  child: Text(
                    updateMessage ?? '더 나은 서비스를 위해\n업데이트가 필요합니다.',
                    textAlign: TextAlign.center,
                    style: const TextStyle(
                      fontWeight: FontWeight.w500,
                      fontSize: 14,
                      color: Color(0xFF505050),
                      height: 1.5,
                    ),
                  ),
                ),
                const SizedBox(height: 40),
                SizedBox(
                  width: double.infinity,
                  height: 48,
                  child: ElevatedButton(
                    onPressed: _openStore,
                    style: ElevatedButton.styleFrom(
                      backgroundColor: const Color(0xFF614108),
                      foregroundColor: Colors.white,
                      elevation: 0,
                      shape: RoundedRectangleBorder(
                        borderRadius: BorderRadius.circular(1000),
                      ),
                      textStyle: const TextStyle(
                        fontSize: 16,
                        fontWeight: FontWeight.w600,
                      ),
                    ),
                    child: const Text('업데이트'),
                  ),
                ),
              ],
            ),
          ),
        ),
      ),
    );
  }
}

/// 소프트 업데이트 다이얼로그 (닫기 가능, 24h 쿨다운)
class SoftUpdateDialog extends StatelessWidget {
  final String updateUrl;
  final String? updateMessage;

  const SoftUpdateDialog({
    super.key,
    required this.updateUrl,
    this.updateMessage,
  });

  Future<void> _openStore() async {
    final uri = Uri.tryParse(updateUrl);
    if (uri == null) return;
    if (await canLaunchUrl(uri)) {
      await launchUrl(uri, mode: LaunchMode.externalApplication);
    }
  }

  @override
  Widget build(BuildContext context) {
    return Center(
      child: Material(
        color: Colors.transparent,
        child: Container(
          width: 320,
          padding: const EdgeInsets.fromLTRB(12, 40, 12, 16),
          decoration: BoxDecoration(
            color: const Color(0xFFFFF7E6),
            borderRadius: BorderRadius.circular(16),
          ),
          child: Column(
            mainAxisSize: MainAxisSize.min,
            children: [
              Padding(
                padding: const EdgeInsets.symmetric(horizontal: 4),
                child: Text(
                  updateMessage ?? '새로운 버전이 출시되었습니다.\n지금 업데이트하시겠어요?',
                  textAlign: TextAlign.center,
                  style: const TextStyle(
                    fontWeight: FontWeight.w500,
                    fontSize: 14,
                    color: Color(0xFF505050),
                    height: 1.5,
                  ),
                ),
              ),
              const SizedBox(height: 40),
              Row(
                children: [
                  Expanded(
                    child: SizedBox(
                      height: 48,
                      child: ElevatedButton(
                        onPressed: () async {
                          await AppVersionService.dismissSoftUpdate();
                          if (context.mounted) Navigator.pop(context);
                        },
                        style: ElevatedButton.styleFrom(
                          backgroundColor: const Color(0xFFEFE1C4),
                          foregroundColor: const Color(0xFF614108),
                          elevation: 0,
                          shape: RoundedRectangleBorder(
                            borderRadius: BorderRadius.circular(1000),
                          ),
                          textStyle: const TextStyle(
                            fontSize: 16,
                            fontWeight: FontWeight.w600,
                          ),
                        ),
                        child: const Text('나중에'),
                      ),
                    ),
                  ),
                  const SizedBox(width: 8),
                  Expanded(
                    child: SizedBox(
                      height: 48,
                      child: ElevatedButton(
                        onPressed: _openStore,
                        style: ElevatedButton.styleFrom(
                          backgroundColor: const Color(0xFF614108),
                          foregroundColor: Colors.white,
                          elevation: 0,
                          shape: RoundedRectangleBorder(
                            borderRadius: BorderRadius.circular(1000),
                          ),
                          textStyle: const TextStyle(
                            fontSize: 16,
                            fontWeight: FontWeight.w600,
                          ),
                        ),
                        child: const Text('업데이트'),
                      ),
                    ),
                  ),
                ],
              ),
            ],
          ),
        ),
      ),
    );
  }
}

/// 강제 업데이트 다이얼로그 표시 (barrierDismissible: false, PopScope canPop: false)
Future<void> showForceUpdateDialog(
  BuildContext context, {
  required String updateUrl,
  String? updateMessage,
}) async {
  await showDialog(
    context: context,
    barrierDismissible: false,
    barrierColor: Colors.black.withValues(alpha: 0.7),
    builder: (_) => ForceUpdateDialog(
      updateUrl: updateUrl,
      updateMessage: updateMessage,
    ),
  );
}

/// 소프트 업데이트 다이얼로그 표시 (barrierDismissible: true)
Future<void> showSoftUpdateDialog(
  BuildContext context, {
  required String updateUrl,
  String? updateMessage,
}) async {
  await showDialog(
    context: context,
    barrierDismissible: true,
    barrierColor: Colors.black.withValues(alpha: 0.7),
    builder: (_) => SoftUpdateDialog(
      updateUrl: updateUrl,
      updateMessage: updateMessage,
    ),
  );
}
