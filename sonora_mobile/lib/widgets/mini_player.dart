import 'package:flutter/material.dart';
import 'package:provider/provider.dart';
import 'package:cached_network_image/cached_network_image.dart';
import '../providers/player_provider.dart';
import '../providers/settings_provider.dart';
import '../screens/now_playing_screen.dart';
import '../theme/colors.dart';
import 'glass_container.dart';
import 'visualizer_widget.dart';

class MiniPlayer extends StatelessWidget {
  const MiniPlayer({super.key});

  @override
  Widget build(BuildContext context) {
    final player = context.watch<PlayerProvider>();
    final settings = context.watch<SettingsProvider>();
    final currentTrack = player.currentTrack;

    if (currentTrack == null) return const SizedBox.shrink();

    final progress = player.duration.inMilliseconds > 0
        ? player.position.inMilliseconds / player.duration.inMilliseconds
        : 0.0;

    return Padding(
      padding: const EdgeInsets.symmetric(horizontal: 12.0, vertical: 6.0),
      child: GestureDetector(
        onTap: () {
          Navigator.of(context).push(
            PageRouteBuilder(
              pageBuilder: (_, __, ___) => const NowPlayingScreen(),
              transitionsBuilder: (_, animation, __, child) {
                return SlideTransition(
                  position: Tween<Offset>(
                    begin: const Offset(0, 1),
                    end: Offset.zero,
                  ).animate(CurvedAnimation(parent: animation, curve: Curves.easeOutCubic)),
                  child: child,
                );
              },
            ),
          );
        },
        child: GlassContainer(
          borderRadius: BorderRadius.circular(16.0),
          padding: const EdgeInsets.symmetric(horizontal: 10.0, vertical: 8.0),
          child: Column(
            mainAxisSize: MainAxisSize.min,
            children: [
              Row(
                children: [
                  // Artwork
                  ClipRRect(
                    borderRadius: BorderRadius.circular(10.0),
                    child: SizedBox(
                      width: 44,
                      height: 44,
                      child: currentTrack.coverUrl != null
                          ? CachedNetworkImage(
                              imageUrl: currentTrack.coverUrl!,
                              fit: BoxFit.cover,
                            )
                          : Container(
                              color: SonoraColors.surface,
                              child: const Icon(Icons.music_note, color: SonoraColors.textMuted),
                            ),
                    ),
                  ),
                  const SizedBox(width: 12),

                  // Track Info
                  Expanded(
                    child: Column(
                      crossAxisAlignment: CrossAxisAlignment.start,
                      children: [
                        Text(
                          currentTrack.title,
                          maxLines: 1,
                          overflow: TextOverflow.ellipsis,
                          style: const TextStyle(
                            fontSize: 14,
                            fontWeight: FontWeight.w600,
                            color: SonoraColors.textPrimary,
                          ),
                        ),
                        Text(
                          currentTrack.artist,
                          maxLines: 1,
                          overflow: TextOverflow.ellipsis,
                          style: const TextStyle(
                            fontSize: 12,
                            color: SonoraColors.textSecondary,
                          ),
                        ),
                      ],
                    ),
                  ),

                  // Mini Visualizer Preview
                  if (player.isPlaying && settings.visualizerStyle != VisualizerStyleKind.none)
                    SizedBox(
                      width: 45,
                      height: 24,
                      child: VisualizerWidget(
                        levels: player.spectrum.take(12).toList(),
                        style: VisualizerStyleKind.bars,
                        height: 24,
                      ),
                    ),

                  const SizedBox(width: 8),

                  // Play / Pause
                  IconButton(
                    icon: Icon(
                      player.isPlaying ? Icons.pause_rounded : Icons.play_arrow_rounded,
                      color: SonoraColors.textPrimary,
                      size: 28,
                    ),
                    onPressed: player.togglePlayPause,
                  ),

                  // Next
                  IconButton(
                    icon: const Icon(
                      Icons.skip_next_rounded,
                      color: SonoraColors.textPrimary,
                      size: 24,
                    ),
                    onPressed: player.playNext,
                  ),
                ],
              ),
              const SizedBox(height: 6),
              // Slim progress bar
              ClipRRect(
                borderRadius: BorderRadius.circular(2.0),
                child: LinearProgressIndicator(
                  value: progress.clamp(0.0, 1.0),
                  minHeight: 2.5,
                  backgroundColor: SonoraColors.border,
                  valueColor: const AlwaysStoppedAnimation<Color>(SonoraColors.accent),
                ),
              ),
            ],
          ),
        ),
      ),
    );
  }
}

