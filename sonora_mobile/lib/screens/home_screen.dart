import 'package:flutter/material.dart';
import 'package:provider/provider.dart';
import 'package:cached_network_image/cached_network_image.dart';
import '../models/track.dart';
import '../providers/player_provider.dart';
import '../services/youtube_music_service.dart';
import '../theme/colors.dart';
import '../widgets/track_tile.dart';

class HomeScreen extends StatefulWidget {
  const HomeScreen({super.key});

  @override
  State<HomeScreen> createState() => _HomeScreenState();
}

class _HomeScreenState extends State<HomeScreen> {
  final YouTubeMusicService _ytService = YouTubeMusicService();
  List<Track> _trendingTracks = [];
  bool _isLoading = true;

  @override
  void initState() {
    super.initState();
    _loadFeed();
  }

  Future<void> _loadFeed() async {
    setState(() => _isLoading = true);
    final tracks = await _ytService.getTrendingMusic();
    if (mounted) {
      setState(() {
        _trendingTracks = tracks;
        _isLoading = false;
      });
    }
  }

  @override
  Widget build(BuildContext context) {
    final player = context.read<PlayerProvider>();

    return Scaffold(
      backgroundColor: SonoraColors.background,
      appBar: AppBar(
        title: Row(
          children: [
            Container(
              padding: const EdgeInsets.all(6),
              decoration: BoxDecoration(
                color: SonoraColors.accent.withOpacity(0.15),
                borderRadius: BorderRadius.circular(10),
              ),
              child: const Icon(Icons.graphic_eq_rounded, color: SonoraColors.accent, size: 22),
            ),
            const SizedBox(width: 10),
            const Text(
              'Sonora',
              style: TextStyle(fontWeight: FontWeight.w800, fontSize: 22, letterSpacing: -0.6),
            ),
          ],
        ),
        actions: [
          IconButton(
            icon: const Icon(Icons.refresh_rounded),
            onPressed: _loadFeed,
            tooltip: 'Actualizar',
          ),
        ],
      ),
      body: _isLoading
          ? const Center(
              child: CircularProgressIndicator(color: SonoraColors.accent),
            )
          : RefreshIndicator(
              color: SonoraColors.accent,
              backgroundColor: SonoraColors.surfaceElevated,
              onRefresh: _loadFeed,
              child: ListView(
                padding: const EdgeInsets.only(bottom: 120.0),
                children: [
                  // Featured Music Videos Horizontal Carousel
                  Padding(
                    padding: const EdgeInsets.fromLTRB(16, 16, 16, 10),
                    child: Row(
                      mainAxisAlignment: MainAxisAlignment.spaceBetween,
                      children: [
                        const Text(
                          'Vídeos Musicales Destacados',
                          style: TextStyle(fontSize: 18, fontWeight: FontWeight.w700),
                        ),
                        Icon(Icons.smart_display_rounded, color: SonoraColors.accent.withOpacity(0.8), size: 20),
                      ],
                    ),
                  ),

                  SizedBox(
                    height: 180,
                    child: ListView.builder(
                      padding: const EdgeInsets.symmetric(horizontal: 12.0),
                      scrollDirection: Axis.horizontal,
                      itemCount: _trendingTracks.take(8).length,
                      itemBuilder: (context, index) {
                        final track = _trendingTracks[index];
                        return GestureDetector(
                          onTap: () {
                            player.playTrack(track, newQueue: _trendingTracks);
                            player.setVideoMode(true); // Open in video mode!
                          },
                          child: Container(
                            width: 220,
                            margin: const EdgeInsets.symmetric(horizontal: 6.0),
                            decoration: BoxDecoration(
                              color: SonoraColors.surface,
                              borderRadius: BorderRadius.circular(14.0),
                              border: Border.all(color: SonoraColors.border),
                            ),
                            clipBehavior: Clip.antiAlias,
                            child: Column(
                              crossAxisAlignment: CrossAxisAlignment.start,
                              children: [
                                // Thumbnail with Video badge
                                Expanded(
                                  child: Stack(
                                    fit: StackFit.expand,
                                    children: [
                                      if (track.coverUrl != null)
                                        CachedNetworkImage(
                                          imageUrl: track.coverUrl!,
                                          fit: BoxFit.cover,
                                        ),
                                      Container(
                                        color: Colors.black26,
                                        child: const Center(
                                          child: Icon(Icons.play_circle_fill_rounded, size: 38, color: Colors.white),
                                        ),
                                      ),
                                    ],
                                  ),
                                ),
                                Padding(
                                  padding: const EdgeInsets.all(8.0),
                                  child: Column(
                                    crossAxisAlignment: CrossAxisAlignment.start,
                                    children: [
                                      Text(
                                        track.title,
                                        maxLines: 1,
                                        overflow: TextOverflow.ellipsis,
                                        style: const TextStyle(fontWeight: FontWeight.w600, fontSize: 13),
                                      ),
                                      Text(
                                        track.artist,
                                        maxLines: 1,
                                        overflow: TextOverflow.ellipsis,
                                        style: const TextStyle(color: SonoraColors.textSecondary, fontSize: 11),
                                      ),
                                    ],
                                  ),
                                ),
                              ],
                            ),
                          ),
                        );
                      },
                    ),
                  ),

                  const SizedBox(height: 20),

                  // Top Trending Audio Tracks
                  const Padding(
                    padding: EdgeInsets.fromLTRB(16, 8, 16, 8),
                    child: Text(
                      'Éxitos Globales',
                      style: TextStyle(fontSize: 18, fontWeight: FontWeight.w700),
                    ),
                  ),

                  ..._trendingTracks.map((track) {
                    final isPlaying = player.currentTrack?.id == track.id && player.isPlaying;
                    return TrackTile(
                      track: track,
                      isPlaying: isPlaying,
                      onTap: () => player.playTrack(track, newQueue: _trendingTracks),
                    );
                  }),
                ],
              ),
            ),
    );
  }
}

