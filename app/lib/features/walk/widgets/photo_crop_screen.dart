import 'dart:io';
import 'dart:math' as math;
import 'dart:ui' as ui;

import 'package:flutter/material.dart';
import 'package:path_provider/path_provider.dart';

import '../../../core/theme/app_theme.dart';

class PhotoCropScreen extends StatefulWidget {
  final String imagePath;

  const PhotoCropScreen({super.key, required this.imagePath});

  static Future<String?> show(BuildContext context, String imagePath) {
    return Navigator.of(context).push<String>(
      MaterialPageRoute(
        fullscreenDialog: true,
        builder: (_) => PhotoCropScreen(imagePath: imagePath),
      ),
    );
  }

  @override
  State<PhotoCropScreen> createState() => _PhotoCropScreenState();
}

enum _DragHandle { topLeft, topRight, bottomLeft, bottomRight, move, none }

class _PhotoCropScreenState extends State<PhotoCropScreen> {
  int _rotationQuarters = 0;
  ui.Size? _imageNaturalSize;
  Size _imageAreaSize = Size.zero;
  Rect _cropRect = Rect.zero;
  bool _isLoading = false;
  bool _cropInitialized = false;

  _DragHandle _activeHandle = _DragHandle.none;
  Offset _dragStart = Offset.zero;
  Rect _cropRectAtDragStart = Rect.zero;

  static const double _handleHitSize = 24.0;
  static const double _handleVisualSize = 20.0;
  static const double _minCropSize = 60.0;

  @override
  void initState() {
    super.initState();
    _loadImageSize();
  }

  Future<void> _loadImageSize() async {
    try {
      final bytes = await File(widget.imagePath).readAsBytes();
      final codec = await ui.instantiateImageCodec(bytes);
      final frame = await codec.getNextFrame();
      final img = frame.image;
      final size = ui.Size(img.width.toDouble(), img.height.toDouble());
      img.dispose();
      if (!mounted) return;
      setState(() => _imageNaturalSize = size);
    } catch (_) {}
  }

  /// The display rect for the (potentially rotated) image within the image area.
  Rect _displayRect() {
    final size = _imageNaturalSize;
    if (size == null || _imageAreaSize == Size.zero) return Rect.zero;
    final odd = _rotationQuarters % 2 == 1;
    final imgW = odd ? size.height : size.width;
    final imgH = odd ? size.width : size.height;
    final s = math.min(_imageAreaSize.width / imgW, _imageAreaSize.height / imgH);
    final w = imgW * s;
    final h = imgH * s;
    return Rect.fromLTWH(
      (_imageAreaSize.width - w) / 2,
      (_imageAreaSize.height - h) / 2,
      w,
      h,
    );
  }

  void _initCropRect() {
    final dr = _displayRect();
    if (dr.isEmpty) return;
    // Initial crop: inset by 16px or fit within display rect
    final inset = math.min(16.0, math.min(dr.width, dr.height) / 4);
    _cropRect = Rect.fromLTRB(
      dr.left + inset,
      dr.top + inset,
      dr.right - inset,
      dr.bottom - inset,
    );
    _cropInitialized = true;
  }

  _DragHandle _hitTest(Offset pos) {
    final r = _cropRect;
    final d = _handleHitSize;

    // Check corners first (priority over move)
    if ((pos - r.topLeft).distance < d) return _DragHandle.topLeft;
    if ((pos - r.topRight).distance < d) return _DragHandle.topRight;
    if ((pos - r.bottomLeft).distance < d) return _DragHandle.bottomLeft;
    if ((pos - r.bottomRight).distance < d) return _DragHandle.bottomRight;

    // Inside crop rect = move
    if (r.contains(pos)) return _DragHandle.move;

    return _DragHandle.none;
  }

  void _onPanStart(DragStartDetails details) {
    final pos = details.localPosition;
    _activeHandle = _hitTest(pos);
    _dragStart = pos;
    _cropRectAtDragStart = _cropRect;
  }

  void _onPanUpdate(DragUpdateDetails details) {
    if (_activeHandle == _DragHandle.none) return;
    final delta = details.localPosition - _dragStart;
    final dr = _displayRect();
    if (dr.isEmpty) return;

    final orig = _cropRectAtDragStart;
    Rect newRect;

    switch (_activeHandle) {
      case _DragHandle.topLeft:
        newRect = Rect.fromLTRB(
          orig.left + delta.dx,
          orig.top + delta.dy,
          orig.right,
          orig.bottom,
        );
      case _DragHandle.topRight:
        newRect = Rect.fromLTRB(
          orig.left,
          orig.top + delta.dy,
          orig.right + delta.dx,
          orig.bottom,
        );
      case _DragHandle.bottomLeft:
        newRect = Rect.fromLTRB(
          orig.left + delta.dx,
          orig.top,
          orig.right,
          orig.bottom + delta.dy,
        );
      case _DragHandle.bottomRight:
        newRect = Rect.fromLTRB(
          orig.left,
          orig.top,
          orig.right + delta.dx,
          orig.bottom + delta.dy,
        );
      case _DragHandle.move:
        newRect = orig.shift(delta);
        // Clamp move within display rect
        double dx = 0, dy = 0;
        if (newRect.left < dr.left) dx = dr.left - newRect.left;
        if (newRect.right > dr.right) dx = dr.right - newRect.right;
        if (newRect.top < dr.top) dy = dr.top - newRect.top;
        if (newRect.bottom > dr.bottom) dy = dr.bottom - newRect.bottom;
        newRect = newRect.shift(Offset(dx, dy));
      case _DragHandle.none:
        return;
    }

    // Enforce minimum size
    if (_activeHandle != _DragHandle.move) {
      if (newRect.width < _minCropSize) {
        if (_activeHandle == _DragHandle.topLeft || _activeHandle == _DragHandle.bottomLeft) {
          newRect = Rect.fromLTRB(newRect.right - _minCropSize, newRect.top, newRect.right, newRect.bottom);
        } else {
          newRect = Rect.fromLTRB(newRect.left, newRect.top, newRect.left + _minCropSize, newRect.bottom);
        }
      }
      if (newRect.height < _minCropSize) {
        if (_activeHandle == _DragHandle.topLeft || _activeHandle == _DragHandle.topRight) {
          newRect = Rect.fromLTRB(newRect.left, newRect.bottom - _minCropSize, newRect.right, newRect.bottom);
        } else {
          newRect = Rect.fromLTRB(newRect.left, newRect.top, newRect.right, newRect.top + _minCropSize);
        }
      }

      // Clamp to display rect bounds
      newRect = Rect.fromLTRB(
        newRect.left.clamp(dr.left, dr.right - _minCropSize),
        newRect.top.clamp(dr.top, dr.bottom - _minCropSize),
        newRect.right.clamp(dr.left + _minCropSize, dr.right),
        newRect.bottom.clamp(dr.top + _minCropSize, dr.bottom),
      );
    }

    setState(() => _cropRect = newRect);
  }

  void _onPanEnd(DragEndDetails details) {
    _activeHandle = _DragHandle.none;
  }

  void _rotate() {
    setState(() {
      _rotationQuarters = (_rotationQuarters + 1) % 4;
      _cropInitialized = false;
    });
  }

  Future<void> _onSave() async {
    if (_isLoading) return;
    setState(() => _isLoading = true);
    try {
      final path = await _cropAndSave();
      if (!mounted) return;
      if (path != null) {
        Navigator.of(context).pop<String>(path);
      } else {
        setState(() => _isLoading = false);
      }
    } catch (_) {
      if (mounted) {
        setState(() => _isLoading = false);
        ScaffoldMessenger.of(context).showSnackBar(
          const SnackBar(content: Text('사진 저장에 실패했습니다.')),
        );
      }
    }
  }

  Future<String?> _cropAndSave() async {
    ui.Image srcImage;
    try {
      final bytes = await File(widget.imagePath).readAsBytes();
      final codec = await ui.instantiateImageCodec(bytes);
      final frame = await codec.getNextFrame();
      srcImage = frame.image;
    } catch (_) {
      if (mounted) {
        ScaffoldMessenger.of(context).showSnackBar(
          const SnackBar(content: Text('사진을 불러올 수 없습니다.')),
        );
      }
      return null;
    }

    try {
      final srcW = srcImage.width.toDouble();
      final srcH = srcImage.height.toDouble();
      final dr = _displayRect();
      if (dr.isEmpty) {
        srcImage.dispose();
        return null;
      }

      // Map crop rect (screen coords) → source pixel coords
      final odd = _rotationQuarters % 2 == 1;
      final effW = odd ? srcH : srcW;
      final effH = odd ? srcW : srcH;
      final displayScale = dr.width / effW; // == dr.height / effH

      // Crop rect in rotated-image pixel coords
      final rxMin = (_cropRect.left - dr.left) / displayScale;
      final ryMin = (_cropRect.top - dr.top) / displayScale;
      final rxMax = (_cropRect.right - dr.left) / displayScale;
      final ryMax = (_cropRect.bottom - dr.top) / displayScale;

      // Map rotated-image coords → source coords
      double sxMin, syMin, sxMax, syMax;
      switch (_rotationQuarters) {
        case 0:
          sxMin = rxMin; syMin = ryMin; sxMax = rxMax; syMax = ryMax;
        case 1: // 90° CW: rotated(rx,ry) → source(ry, effW-rx)
          sxMin = ryMin; syMin = effW - rxMax; sxMax = ryMax; syMax = effW - rxMin;
        case 2: // 180°: rotated(rx,ry) → source(effW-rx, effH-ry)
          sxMin = effW - rxMax; syMin = effH - ryMax; sxMax = effW - rxMin; syMax = effH - ryMin;
        case 3: // 270° CW: rotated(rx,ry) → source(effH-ry, rx)
          sxMin = effH - ryMax; syMin = rxMin; sxMax = effH - ryMin; syMax = rxMax;
        default:
          sxMin = rxMin; syMin = ryMin; sxMax = rxMax; syMax = ryMax;
      }

      // Clamp
      sxMin = sxMin.clamp(0.0, srcW);
      syMin = syMin.clamp(0.0, srcH);
      sxMax = sxMax.clamp(0.0, srcW);
      syMax = syMax.clamp(0.0, srcH);

      final cropW = (sxMax - sxMin).round();
      final cropH = (syMax - syMin).round();
      if (cropW <= 0 || cropH <= 0) {
        srcImage.dispose();
        return null;
      }

      // Determine output dimensions (rotated)
      final int outputW, outputH;
      if (_rotationQuarters % 2 == 0) {
        outputW = cropW;
        outputH = cropH;
      } else {
        outputW = cropH;
        outputH = cropW;
      }

      // Render
      final recorder = ui.PictureRecorder();
      final canvas = Canvas(
        recorder,
        Rect.fromLTWH(0, 0, outputW.toDouble(), outputH.toDouble()),
      );

      // Apply rotation + crop translation
      switch (_rotationQuarters) {
        case 0:
          canvas.translate(-sxMin, -syMin);
        case 1:
          canvas.translate(syMax - syMin, 0);
          canvas.rotate(math.pi / 2);
          canvas.translate(-sxMin, -(srcH - syMax));
        case 2:
          canvas.translate(sxMax - sxMin, syMax - syMin);
          canvas.rotate(math.pi);
          canvas.translate(-sxMin, -syMin);
        case 3:
          canvas.translate(0, sxMax - sxMin);
          canvas.rotate(-math.pi / 2);
          canvas.translate(-(srcW - sxMax), -syMin);
      }

      canvas.drawImage(srcImage, Offset.zero, Paint());
      srcImage.dispose();

      final picture = recorder.endRecording();
      final outputImage = await picture.toImage(outputW, outputH);
      picture.dispose();

      final byteData = await outputImage.toByteData(format: ui.ImageByteFormat.png);
      outputImage.dispose();
      if (byteData == null) return null;

      final tempDir = await getTemporaryDirectory();
      final ts = DateTime.now().millisecondsSinceEpoch;
      final file = File('${tempDir.path}/crop_$ts.png');
      await file.writeAsBytes(byteData.buffer.asUint8List());
      return file.path;
    } catch (e) {
      srcImage.dispose();
      rethrow;
    }
  }

  @override
  Widget build(BuildContext context) {
    return Scaffold(
      backgroundColor: AppColors.background,
      body: SafeArea(
        bottom: false,
        child: Column(
          children: [
            // ── Header
            Align(
              alignment: Alignment.centerRight,
              child: IconButton(
                onPressed: () => Navigator.of(context).pop<String>(null),
                icon: const Icon(Icons.close, color: Color(0xFF333333)),
              ),
            ),

            // ── Image area
            Expanded(
              child: LayoutBuilder(
                builder: (context, constraints) {
                  final containerSize = Size(constraints.maxWidth, constraints.maxHeight);
                  if (_imageAreaSize != containerSize) {
                    _imageAreaSize = containerSize;
                    _cropInitialized = false;
                  }

                  // Initialize crop rect once we know both image and container sizes
                  if (!_cropInitialized && _imageNaturalSize != null) {
                    WidgetsBinding.instance.addPostFrameCallback((_) {
                      if (mounted && !_cropInitialized) {
                        setState(() => _initCropRect());
                      }
                    });
                  }

                  return GestureDetector(
                    onPanStart: _onPanStart,
                    onPanUpdate: _onPanUpdate,
                    onPanEnd: _onPanEnd,
                    child: Stack(
                      fit: StackFit.expand,
                      children: [
                        // Image (rotated, non-interactive)
                        Transform.rotate(
                          angle: _rotationQuarters * math.pi / 2,
                          child: Image.file(
                            File(widget.imagePath),
                            fit: BoxFit.contain,
                            width: double.infinity,
                            height: double.infinity,
                          ),
                        ),

                        // Overlay + crop frame + handles
                        if (_cropRect != Rect.zero)
                          CustomPaint(
                            painter: CropOverlayPainter(cropRect: _cropRect),
                          ),

                        // Corner handles
                        if (_cropRect != Rect.zero) ..._buildHandles(),

                        // Rotate button
                        Positioned(
                          top: 16,
                          right: 16,
                          child: GestureDetector(
                            onTap: _rotate,
                            child: Container(
                              width: 48,
                              height: 48,
                              decoration: const BoxDecoration(
                                color: Color(0xFF26282B),
                                shape: BoxShape.circle,
                              ),
                              child: const Icon(Icons.crop_rotate, color: Colors.white, size: 24),
                            ),
                          ),
                        ),
                      ],
                    ),
                  );
                },
              ),
            ),

            // ── Save button
            Padding(
              padding: const EdgeInsets.fromLTRB(16, 16, 16, 40),
              child: SizedBox(
                width: double.infinity,
                height: 52,
                child: ElevatedButton(
                  onPressed: _isLoading ? null : _onSave,
                  style: ElevatedButton.styleFrom(
                    backgroundColor: AppColors.primary,
                    foregroundColor: Colors.white,
                    disabledBackgroundColor: AppColors.primary.withValues(alpha: 0.6),
                    shape: RoundedRectangleBorder(
                      borderRadius: BorderRadius.circular(1000),
                    ),
                    elevation: 0,
                  ),
                  child: _isLoading
                      ? const SizedBox(
                          width: 22,
                          height: 22,
                          child: CircularProgressIndicator(color: Colors.white, strokeWidth: 2.5),
                        )
                      : const Text(
                          '저장',
                          style: TextStyle(
                            fontFamily: 'Pretendard',
                            fontSize: 16,
                            fontWeight: FontWeight.w600,
                          ),
                        ),
                ),
              ),
            ),
          ],
        ),
      ),
    );
  }

  List<Widget> _buildHandles() {
    final r = _cropRect;
    const size = _handleVisualSize;
    const half = size / 2;

    Widget handle(Offset center) {
      return Positioned(
        left: center.dx - half,
        top: center.dy - half,
        child: IgnorePointer(
          child: Container(
            width: size,
            height: size,
            decoration: BoxDecoration(
              color: Colors.white,
              shape: BoxShape.circle,
              border: Border.all(color: AppColors.primary, width: 2),
              boxShadow: const [
                BoxShadow(color: Color(0x40000000), blurRadius: 4, offset: Offset(0, 2)),
              ],
            ),
          ),
        ),
      );
    }

    return [
      handle(r.topLeft),
      handle(r.topRight),
      handle(r.bottomLeft),
      handle(r.bottomRight),
    ];
  }
}

// ── CropOverlayPainter ──────────────────────────────────────────────────────

class CropOverlayPainter extends CustomPainter {
  final Rect cropRect;

  const CropOverlayPainter({required this.cropRect});

  @override
  void paint(Canvas canvas, Size size) {
    final outerPath = Path()..addRect(Rect.fromLTWH(0, 0, size.width, size.height));
    final innerPath = Path()..addRRect(RRect.fromRectAndRadius(cropRect, const Radius.circular(16)));

    // Dark overlay
    canvas.drawPath(
      Path.combine(PathOperation.difference, outerPath, innerPath),
      Paint()..color = const Color(0xB3000000),
    );

    // Crop frame border
    canvas.drawRRect(
      RRect.fromRectAndRadius(cropRect, const Radius.circular(16)),
      Paint()
        ..color = AppColors.background
        ..style = PaintingStyle.stroke
        ..strokeWidth = 3,
    );

    // Grid lines (rule of thirds)
    final thirdW = cropRect.width / 3;
    final thirdH = cropRect.height / 3;
    final gridPaint = Paint()
      ..color = const Color(0x40FFFFFF)
      ..strokeWidth = 0.5;

    for (var i = 1; i <= 2; i++) {
      canvas.drawLine(
        Offset(cropRect.left + thirdW * i, cropRect.top),
        Offset(cropRect.left + thirdW * i, cropRect.bottom),
        gridPaint,
      );
      canvas.drawLine(
        Offset(cropRect.left, cropRect.top + thirdH * i),
        Offset(cropRect.right, cropRect.top + thirdH * i),
        gridPaint,
      );
    }
  }

  @override
  bool shouldRepaint(CropOverlayPainter old) => old.cropRect != cropRect;
}
