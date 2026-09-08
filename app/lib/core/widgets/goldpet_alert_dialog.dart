import 'package:flutter/material.dart';

/// GoldPet 공통 커스텀 알럿 다이얼로그
/// React AlertModal 디자인과 동일한 스타일 적용
class GoldPetAlertDialog extends StatelessWidget {
  final String message;
  final String confirmText;
  final String? cancelText;
  final VoidCallback onConfirm;
  final VoidCallback? onCancel;
  final TextAlign messageAlign;

  const GoldPetAlertDialog({
    super.key,
    required this.message,
    this.confirmText = '확인',
    this.cancelText,
    required this.onConfirm,
    this.onCancel,
    this.messageAlign = TextAlign.center,
  });

  @override
  Widget build(BuildContext context) {
    final hasCancelButton = cancelText != null && onCancel != null;

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
              // Message
              Padding(
                padding: const EdgeInsets.symmetric(horizontal: 4),
                child: Text(
                  message,
                  textAlign: messageAlign,
                  style: const TextStyle(
                    fontWeight: FontWeight.w500,
                    fontSize: 14,
                    color: Color(0xFF505050),
                    height: 1.5,
                  ),
                ),
              ),
              const SizedBox(height: 40),
              // Buttons
              if (hasCancelButton)
                Row(
                  children: [
                    // Cancel button (half-secondary)
                    Expanded(
                      child: SizedBox(
                        height: 48,
                        child: ElevatedButton(
                          onPressed: onCancel,
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
                          child: Text(cancelText!),
                        ),
                      ),
                    ),
                    const SizedBox(width: 8),
                    // Confirm button (half-primary)
                    Expanded(
                      child: SizedBox(
                        height: 48,
                        child: ElevatedButton(
                          onPressed: onConfirm,
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
                          child: Text(confirmText),
                        ),
                      ),
                    ),
                  ],
                )
              else
                // Full-width button (gp-btn-full)
                SizedBox(
                  width: double.infinity,
                  height: 48,
                  child: ElevatedButton(
                    onPressed: onConfirm,
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
                    child: Text(confirmText),
                  ),
                ),
            ],
          ),
        ),
      ),
    );
  }
}

/// 1-button alert (확인만)
Future<void> showGoldPetAlert(
  BuildContext context, {
  required String message,
  String confirmText = '확인',
  VoidCallback? onConfirm,
  bool barrierDismissible = false,
}) async {
  await showDialog(
    context: context,
    barrierDismissible: barrierDismissible,
    barrierColor: Colors.black.withValues(alpha: 0.7),
    builder: (context) => GoldPetAlertDialog(
      message: message,
      confirmText: confirmText,
      onConfirm: () {
        Navigator.pop(context);
        onConfirm?.call();
      },
    ),
  );
}

/// 2-button confirm (취소 + 확인) — returns true if confirmed, false if cancelled
Future<bool> showGoldPetConfirm(
  BuildContext context, {
  required String message,
  String confirmText = '확인',
  String cancelText = '취소',
  bool barrierDismissible = false,
  TextAlign messageAlign = TextAlign.center,
}) async {
  return await showDialog<bool>(
        context: context,
        barrierDismissible: barrierDismissible,
        barrierColor: Colors.black.withValues(alpha: 0.7),
        builder: (context) => GoldPetAlertDialog(
          message: message,
          confirmText: confirmText,
          cancelText: cancelText,
          messageAlign: messageAlign,
          onConfirm: () => Navigator.pop(context, true),
          onCancel: () => Navigator.pop(context, false),
        ),
      ) ??
      false;
}
