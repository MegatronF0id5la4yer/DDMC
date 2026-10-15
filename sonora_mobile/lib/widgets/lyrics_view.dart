import 'package:flutter/material.dart';
import '../models/lyric_line.dart';
import '../theme/colors.dart';

class LyricsView extends StatefulWidget {
  final List<LyricLine> lyrics;
  final Duration currentPosition;
  final ValueChanged<Duration> onSeekTo;

  const LyricsView({
    super.key,
    required this.lyrics,
    required this.currentPosition,
    required this.onSeekTo,
  });

  @override
  State<LyricsView> createState() => _LyricsViewState();
}

class _LyricsViewState extends State<LyricsView> {
  final ScrollController _scrollController = ScrollController();
  int _lastActiveIndex = -1;

  @override
  void didUpdateWidget(covariant LyricsView oldWidget) {
    super.didUpdateWidget(oldWidget);
    final activeIndex = _findActiveIndex();
    if (activeIndex != _lastActiveIndex && activeIndex != -1) {
      _lastActiveIndex = activeIndex;
      _scrollToActive(activeIndex);
    }
  }

  int _findActiveIndex() {
    for (int i = widget.lyrics.length - 1; i >= 0; i--) {
      if (widget.currentPosition >= widget.lyrics[i].timestamp) {
        return i;
      }
    }
    return 0;
  }

  void _scrollToActive(int index) {
    if (!_scrollController.hasClients) return;
    const itemHeight = 60.0;
    final targetOffset = (index * itemHeight) - 120.0;
    _scrollController.animateTo(
      targetOffset.clamp(0.0, _scrollController.position.maxScrollExtent),
      duration: const Duration(milliseconds: 350),
      curve: Curves.easeOutCubic,
    );
  }

  @override
  void dispose() {
    _scrollController.dispose();
    super.dispose();
  }

  @override
  Widget build(BuildContext context) {
    if (widget.lyrics.isEmpty) {
      return const Center(
        child: Text(
          'Letras no disponibles para esta canción',
          style: TextStyle(color: SonoraColors.textMuted, fontSize: 14),
        ),
      );
    }

    final activeIndex = _findActiveIndex();

    return ListView.builder(
      controller: _scrollController,
      padding: const EdgeInsets.symmetric(vertical: 80.0, horizontal: 24.0),
      itemCount: widget.lyrics.length,
      itemBuilder: (context, index) {
        final line = widget.lyrics[index];
        final isActive = index == activeIndex;

        return GestureDetector(
          onTap: () => widget.onSeekTo(line.timestamp),
          child: Padding(
            padding: const EdgeInsets.symmetric(vertical: 10.0),
            child: AnimatedDefaultTextStyle(
              duration: const Duration(milliseconds: 250),
              style: TextStyle(
                fontSize: isActive ? 24.0 : 18.0,
                fontWeight: isActive ? FontWeight.w700 : FontWeight.w500,
                color: isActive 
                    ? SonoraColors.textPrimary 
                    : SonoraColors.textMuted.withOpacity(0.55),
                letterSpacing: -0.3,
                height: 1.3,
              ),
              child: Text(line.text),
            ),
          ),
        );
      },
    );
  }
}
