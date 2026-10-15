import 'package:flutter/material.dart';
import 'package:provider/provider.dart';
import 'package:cached_network_image/cached_network_image.dart';
import '../models/track.dart';
import '../providers/player_provider.dart';
import '../providers/library_provider.dart';
import '../providers/settings_provider.dart';
import '../theme/colors.dart';
import '../widgets/visualizer_widget.dart';
import '../widgets/video_player_widget.dart';
import '../widgets/lyrics_view.dart';
import '../widgets/glass_container.dart';

class NowPlayingScreen extends StatefulWidget {
  const NowPlayingScreen({super.key});

  @override
  State<NowPlayingScreen> createState() => _NowPlayingScreenState();
}

class _NowPlayingScreenState extends State<NowPlayingScreen> with SingleTickerProviderStateMixin {
  late TabController _tabController;
  int _selectedBottomTab = 0; // 0 = Player/Video, 1 = Lyrics, 2 = Queue

  @override
  void initState() {
    super.initState();
    _tabController = TabController(length: 3, vsync: this);
  }

  @override
  void dispose() {
    _tabController.dispose();
    super.dispose();
  }

  String _formatTime(Duration d) {
    final minutes = d.inMinutes;
    final seconds = d.inSeconds % 60;
    return '$minutes:${seconds.toString().padLeft(2, '0')}';
  }

  @override
  Widget build(BuildContext context) {
    final player = context.watch<PlayerProvider>();
    final library = context.watch<LibraryProvider>();
    final settings = context.watch<SettingsProvider>();
    final track = player.currentTrack;

    if (track == null) {
      return const Scaffold(
        body: Center(child: Text('No hay nada en reproducción')),
      );
    }

    final isFav = library.isFavorite(track.id);
    final size = MediaQuery.of(context).size;
    final artSize = size.width * 0.78;

    return Scaffold(
      backgroundColor: SonoraColors.background,
      body: SafeArea(
        child: Column(
          children: [
            // Top Navigation Bar
            Padding(
              padding: const EdgeInsets.symmetric(horizontal: 16.0, vertical: 8.0),
              child: Row(
                mainAxisAlignment: MainAxisAlignment.spaceBetween,
                children: [
                  IconButton(
                    icon: const Icon(Icons.keyboard_arrow_down_rounded, size: 32, color: SonoraColors.textPrimary),
                    onPressed: () => Navigator.of(context).pop(),
                  ),
                  
                  // Mode Switcher Pill (Artwork vs Video)
                  GlassContainer(
                    borderRadius: BorderRadius.circular(24.0),
                    padding: const EdgeInsets.symmetric(horizontal: 4.0, vertical: 3.0),
                    child: Row(
                      mainAxisSize: MainAxisSize.min,
                      children: [
                        _buildPillTab(
                          icon: Icons.album_rounded,
                          label: 'Portada',
                          isSelected: !player.videoMode,
                          onTap: () => player.setVideoMode(false),
                        ),
                        const SizedBox(width: 4),
                        _buildPillTab(
                          icon: Icons.smart_display_rounded,
                          label: 'Vídeo',
                          isSelected: player.videoMode,
                          onTap: () => player.setVideoMode(true),
                        ),
                      ],
                    ),
                  ),

                  IconButton(
                    icon: Icon(
                      isFav ? Icons.favorite_rounded : Icons.favorite_border_rounded,
                      color: isFav ? SonoraColors.accent : SonoraColors.textPrimary,
                    ),
                    onPressed: () => library.toggleFavorite(track),
                  ),
                ],
              ),
            ),

            // Main Content Area
            Expanded(
              child: IndexedStack(
                index: _selectedBottomTab,
                children: [
                  // Tab 0: Stage (Artwork / Visualizer OR Video Player)
                  _buildStage(artSize, track, player, settings),

                  // Tab 1: Synced Lyrics View
                  LyricsView(
                    lyrics: player.lyrics,
                    currentPosition: player.position,
                    onSeekTo: player.seek,
                  ),

                  // Tab 2: Queue List
                  _buildQueueView(player),
                ],
              ),
            ),

            // Track Info, Scrubber & Controls
            Padding(
              padding: const EdgeInsets.symmetric(horizontal: 24.0),
              child: Column(
                children: [
                  // Track Meta
                  Row(
                    mainAxisAlignment: MainAxisAlignment.spaceBetween,
                    children: [
                      Expanded(
                        child: Column(
                          crossAxisAlignment: CrossAxisAlignment.start,
                          children: [
                            Text(
                              track.title,
                              maxLines: 1,
                              overflow: TextOverflow.ellipsis,
                              style: const TextStyle(
                                fontSize: 20,
                                fontWeight: FontWeight.w700,
                                color: SonoraColors.textPrimary,
                                letterSpacing: -0.4,
                              ),
                            ),
                            const SizedBox(height: 4),
                            Text(
                              track.artist,
                              maxLines: 1,
                              overflow: TextOverflow.ellipsis,
                              style: const TextStyle(
                                fontSize: 15,
                                color: SonoraColors.textSecondary,
                              ),
                            ),
                          ],
                        ),
                      ),
                    ],
                  ),
                  const SizedBox(height: 14),

                  // Scrubber / Seek Slider
                  SliderTheme(
                    data: SliderTheme.of(context).copyWith(
                      trackHeight: 4.0,
                      thumbShape: const RoundSliderThumbShape(enabledThumbRadius: 6.0),
                      overlayShape: const RoundSliderOverlayShape(overlayRadius: 12.0),
                      activeTrackColor: SonoraColors.accent,
                      inactiveTrackColor: SonoraColors.border,
                      thumbColor: SonoraColors.textPrimary,
                    ),
                    child: Slider(
                      value: player.position.inMilliseconds.toDouble().clamp(
                        0.0,
                        player.duration.inMilliseconds.toDouble() > 0 
                            ? player.duration.inMilliseconds.toDouble() 
                            : 1.0,
                      ),
                      max: player.duration.inMilliseconds.toDouble() > 0 
                          ? player.duration.inMilliseconds.toDouble() 
                          : 1.0,
                      onChanged: (val) {
                        player.seek(Duration(milliseconds: val.toInt()));
                      },
                    ),
                  ),

                  // Timestamps
                  Padding(
                    padding: const EdgeInsets.symmetric(horizontal: 4.0),
                    child: Row(
                      mainAxisAlignment: MainAxisAlignment.spaceBetween,
                      children: [
                        Text(
                          _formatTime(player.position),
                          style: const TextStyle(fontSize: 12, color: SonoraColors.textMuted),
                        ),
                        Text(
                          _formatTime(player.duration),
                          style: const TextStyle(fontSize: 12, color: SonoraColors.textMuted),
                        ),
                      ],
                    ),
                  ),
                  const SizedBox(height: 8),

                  // Transport Playback Controls
                  Row(
                    mainAxisAlignment: MainAxisAlignment.spaceEvenly,
                    children: [
                      // Shuffle
                      IconButton(
                        icon: Icon(
                          Icons.shuffle_rounded,
                          color: player.isShuffle ? SonoraColors.accent : SonoraColors.textMuted,
                          size: 24,
                        ),
                        onPressed: player.toggleShuffle,
                      ),

                      // Previous
                      IconButton(
                        icon: const Icon(Icons.skip_previous_rounded, color: SonoraColors.textPrimary, size: 36),
                        onPressed: player.playPrevious,
                      ),

                      // Large Play/Pause
                      GestureDetector(
                        onTap: player.togglePlayPause,
                        child: Container(
                          width: 64,
                          height: 64,
                          decoration: BoxDecoration(
                            color: SonoraColors.textPrimary,
                            shape: BoxShape.circle,
                            boxShadow: [
                              BoxShadow(
                                color: SonoraColors.accent.withOpacity(0.3),
                                blurRadius: 16,
                                spreadRadius: 2,
                              ),
                            ],
                          ),
                          child: Icon(
                            player.isPlaying ? Icons.pause_rounded : Icons.play_arrow_rounded,
                            color: Colors.black,
                            size: 36,
                          ),
                        ),
                      ),

                      // Next
                      IconButton(
                        icon: const Icon(Icons.skip_next_rounded, color: SonoraColors.textPrimary, size: 36),
                        onPressed: player.playNext,
                      ),

                      // Loop / Repeat
                      IconButton(
                        icon: Icon(
                          player.loopMode == LoopMode.one 
                              ? Icons.repeat_one_rounded 
                              : Icons.repeat_rounded,
                          color: player.loopMode != LoopMode.off ? SonoraColors.accent : SonoraColors.textMuted,
                          size: 24,
                        ),
                        onPressed: player.toggleLoopMode,
                      ),
                    ],
                  ),
                  const SizedBox(height: 12),

                  // Bottom Tabs Pill (Player / Lyrics / Queue)
                  GlassContainer(
                    borderRadius: BorderRadius.circular(20.0),
                    padding: const EdgeInsets.symmetric(horizontal: 6.0, vertical: 4.0),
                    child: Row(
                      mainAxisAlignment: MainAxisAlignment.center,
                      mainAxisSize: MainAxisSize.min,
                      children: [
                        _buildBottomTabItem(Icons.music_note_rounded, 'Reproductor', 0),
                        const SizedBox(width: 8),
                        _buildBottomTabItem(Icons.lyrics_rounded, 'Letras', 1),
                        const SizedBox(width: 8),
                        _buildBottomTabItem(Icons.queue_music_rounded, 'Cola', 2),
                      ],
                    ),
                  ),
                  const SizedBox(height: 10),
                ],
              ),
            ),
          ],
        ),
      ),
    );
  }

  Widget _buildStage(double artSize, Track track, PlayerProvider player, SettingsProvider settings) {
    if (player.videoMode) {
      return Padding(
        padding: const EdgeInsets.all(20.0),
        child: Center(
          child: VideoPlayerWidget(
            track: track,
            onClose: () => player.setVideoMode(false),
          ),
        ),
      );
    }

    return Center(
      child: Stack(
        alignment: Alignment.center,
        children: [
          // Audio Spectrum Visualizer (rendered behind/underneath the artwork!)
          if (settings.visualizerStyle != VisualizerStyleKind.none)
            Positioned(
              bottom: 10,
              left: 20,
              right: 20,
              child: VisualizerWidget(
                levels: player.spectrum,
                style: settings.visualizerStyle,
                height: 130,
              ),
            ),

          // High Resolution Album Artwork
          Hero(
            tag: 'artwork_${track.id}',
            child: Container(
              width: artSize,
              height: artSize,
              decoration: BoxDecoration(
                borderRadius: BorderRadius.circular(20.0),
                border: Border.all(color: SonoraColors.border.withOpacity(0.5)),
                boxShadow: [
                  BoxShadow(
                    color: Colors.black.withOpacity(0.5),
                    blurRadius: 28,
                    spreadRadius: -4,
                  ),
                ],
              ),
              clipBehavior: Clip.antiAlias,
              child: track.coverUrl != null
                  ? CachedNetworkImage(
                      imageUrl: track.coverUrl!,
                      fit: BoxFit.cover,
                      placeholder: (_, __) => Container(
                        color: SonoraColors.surfaceElevated,
                        child: const Icon(Icons.music_note, color: SonoraColors.textMuted, size: 64),
                      ),
                      errorWidget: (_, __, ___) => Container(
                        color: SonoraColors.surfaceElevated,
                        child: const Icon(Icons.music_note, color: SonoraColors.textMuted, size: 64),
                      ),
                    )
                  : Container(
                      color: SonoraColors.surfaceElevated,
                      child: const Icon(Icons.music_note, color: SonoraColors.textMuted, size: 64),
                    ),
            ),
          ),
        ],
      ),
    );
  }

  Widget _buildPillTab({
    required IconData icon,
    required String label,
    required bool isSelected,
    required VoidCallback onTap,
  }) {
    return GestureDetector(
      onTap: onTap,
      child: Container(
        padding: const EdgeInsets.symmetric(horizontal: 12.0, vertical: 6.0),
        decoration: BoxDecoration(
          color: isSelected ? SonoraColors.surfaceElevated : Colors.transparent,
          borderRadius: BorderRadius.circular(16.0),
          border: isSelected ? Border.all(color: SonoraColors.border) : null,
        ),
        child: Row(
          mainAxisSize: MainAxisSize.min,
          children: [
            Icon(
              icon,
              size: 16,
              color: isSelected ? SonoraColors.textPrimary : SonoraColors.textMuted,
            ),
            const SizedBox(width: 6),
            Text(
              label,
              style: TextStyle(
                fontSize: 12,
                fontWeight: isSelected ? FontWeight.w600 : FontWeight.w400,
                color: isSelected ? SonoraColors.textPrimary : SonoraColors.textMuted,
              ),
            ),
          ],
        ),
      ),
    );
  }

  Widget _buildBottomTabItem(IconData icon, String label, int index) {
    final isSelected = _selectedBottomTab == index;
    return GestureDetector(
      onTap: () => setState(() => _selectedBottomTab = index),
      child: Container(
        padding: const EdgeInsets.symmetric(horizontal: 14.0, vertical: 6.0),
        decoration: BoxDecoration(
          color: isSelected ? SonoraColors.surface : Colors.transparent,
          borderRadius: BorderRadius.circular(14.0),
        ),
        child: Row(
          mainAxisSize: MainAxisSize.min,
          children: [
            Icon(
              icon,
              size: 16,
              color: isSelected ? SonoraColors.accent : SonoraColors.textMuted,
            ),
            const SizedBox(width: 6),
            Text(
              label,
              style: TextStyle(
                fontSize: 12,
                fontWeight: isSelected ? FontWeight.w600 : FontWeight.w400,
                color: isSelected ? SonoraColors.textPrimary : SonoraColors.textMuted,
              ),
            ),
          ],
        ),
      ),
    );
  }

  Widget _buildQueueView(PlayerProvider player) {
    return ListView.builder(
      padding: const EdgeInsets.all(16.0),
      itemCount: player.queue.length,
      itemBuilder: (context, index) {
        final track = player.queue[index];
        final isCurrent = index == player.currentIndex;

        return ListTile(
          leading: Text(
            '${index + 1}',
            style: TextStyle(
              color: isCurrent ? SonoraColors.accent : SonoraColors.textMuted,
              fontWeight: isCurrent ? FontWeight.w700 : FontWeight.w400,
            ),
          ),
          title: Text(
            track.title,
            maxLines: 1,
            overflow: TextOverflow.ellipsis,
            style: TextStyle(
              color: isCurrent ? SonoraColors.accent : SonoraColors.textPrimary,
              fontWeight: isCurrent ? FontWeight.w600 : FontWeight.w400,
            ),
          ),
          subtitle: Text(
            track.artist,
            maxLines: 1,
            overflow: TextOverflow.ellipsis,
            style: const TextStyle(color: SonoraColors.textSecondary, fontSize: 12),
          ),
          trailing: isCurrent 
              ? const Icon(Icons.equalizer_rounded, color: SonoraColors.accent)
              : null,
          onTap: () => player.playTrack(track),
        );
      },
    );
  }
}
