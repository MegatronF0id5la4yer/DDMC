import 'dart:math';
import 'package:flutter/material.dart';
import '../providers/settings_provider.dart';
import '../theme/colors.dart';

class VisualizerWidget extends StatelessWidget {
  final List<double> levels;
  final VisualizerStyleKind style;
  final double height;
  final Color? barColor;
  final Color? peakColor;

  const VisualizerWidget({
    super.key,
    required this.levels,
    required this.style,
    this.height = 120.0,
    this.barColor,
    this.peakColor,
  });

  @override
  Widget build(BuildContext context) {
    if (style == VisualizerStyleKind.none || levels.isEmpty) {
      return const SizedBox.shrink();
    }

    return SizedBox(
      height: height,
      width: double.infinity,
      child: CustomPaint(
        painter: _VisualizerPainter(
          levels: levels,
          style: style,
          barColor: barColor ?? SonoraColors.visualizerBar,
          peakColor: peakColor ?? SonoraColors.visualizerPeak,
        ),
      ),
    );
  }
}

class _VisualizerPainter extends CustomPainter {
  final List<double> levels;
  final VisualizerStyleKind style;
  final Color barColor;
  final Color peakColor;

  _VisualizerPainter({
    required this.levels,
    required this.style,
    required this.barColor,
    required this.peakColor,
  });

  @override
  void paint(Canvas canvas, Size size) {
    if (levels.isEmpty) return;

    final width = size.width;
    final height = size.height;
    final count = min(levels.length, 48); // Draw up to 48 distinct frequency bands

    if (style == VisualizerStyleKind.bars || style == VisualizerStyleKind.both) {
      _drawBars(canvas, size, count, width, height);
    }

    if (style == VisualizerStyleKind.wave || style == VisualizerStyleKind.both) {
      _drawWave(canvas, size, count, width, height);
    }
  }

  void _drawBars(Canvas canvas, Size size, int count, double width, double height) {
    final gap = 2.5;
    final totalGap = gap * (count - 1);
    final barWidth = (width - totalGap) / count;

    final paint = Paint()
      ..shader = LinearGradient(
        begin: Alignment.bottomCenter,
        end: Alignment.topCenter,
        colors: [
          barColor.withOpacity(0.3),
          barColor,
          peakColor,
        ],
      ).createShader(Rect.fromLTWH(0, 0, width, height))
      ..style = PaintingStyle.fill;

    for (int i = 0; i < count; i++) {
      final level = levels[i].clamp(0.02, 1.0);
      final barHeight = level * height * 0.9;
      final x = i * (barWidth + gap);
      final y = height - barHeight;

      final rrect = RRect.fromRectAndRadius(
        Rect.fromLTWH(x, y, barWidth, barHeight),
        const Radius.circular(3.0),
      );

      canvas.drawRRect(rrect, paint);
    }
  }

  void _drawWave(Canvas canvas, Size size, int count, double width, double height) {
    final wavePaint = Paint()
      ..color = peakColor.withOpacity(0.85)
      ..strokeWidth = 2.5
      ..style = PaintingStyle.stroke
      ..strokeCap = StrokeCap.round;

    final glowPaint = Paint()
      ..color = peakColor.withOpacity(0.35)
      ..strokeWidth = 6.0
      ..style = PaintingStyle.stroke
      ..maskFilter = const MaskFilter.blur(BlurStyle.normal, 6.0);

    final path = Path();
    final step = width / (count - 1);

    for (int i = 0; i < count; i++) {
      final level = levels[i].clamp(0.05, 1.0);
      final x = i * step;
      final y = height - (level * height * 0.85);

      if (i == 0) {
        path.moveTo(x, y);
      } else {
        final prevX = (i - 1) * step;
        final prevLevel = levels[i - 1].clamp(0.05, 1.0);
        final prevY = height - (prevLevel * height * 0.85);
        final midX = (prevX + x) / 2;
        path.quadraticBezierTo(prevX, prevY, midX, (prevY + y) / 2);
      }
    }

    // Draw ambient glow first, then the core wave
    canvas.drawPath(path, glowPaint);
    canvas.drawPath(path, wavePaint);
  }

  @override
  bool shouldRepaint(covariant _VisualizerPainter oldDelegate) {
    return true; // Continuously animates with incoming audio frames
  }
}

