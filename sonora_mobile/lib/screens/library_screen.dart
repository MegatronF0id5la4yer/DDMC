import 'package:flutter/material.dart';
import 'package:provider/provider.dart';
import '../providers/library_provider.dart';
import '../providers/player_provider.dart';
import '../theme/colors.dart';
import '../widgets/track_tile.dart';

class LibraryScreen extends StatefulWidget {
  const LibraryScreen({super.key});

  @override
  State<LibraryScreen> createState() => _LibraryScreenState();
}

class _LibraryScreenState extends State<LibraryScreen> with SingleTickerProviderStateMixin {
  late TabController _tabController;

  @override
  void initState() {
    super.initState();
    _tabController = TabController(length: 2, vsync: this);
  }

  @override
  void dispose() {
    _tabController.dispose();
    super.dispose();
  }

  @override
  Widget build(BuildContext context) {
    final library = context.watch<LibraryProvider>();
    final player = context.read<PlayerProvider>();

    return Scaffold(
      backgroundColor: SonoraColors.background,
      appBar: AppBar(
        title: const Text(
          'Tu Biblioteca',
          style: TextStyle(fontWeight: FontWeight.w700, fontSize: 22, letterSpacing: -0.5),
        ),
        bottom: TabBar(
          controller: _tabController,
          indicatorColor: SonoraColors.accent,
          indicatorWeight: 3.0,
          labelColor: SonoraColors.textPrimary,
          unselectedLabelColor: SonoraColors.textMuted,
          tabs: const [
            Tab(text: 'Favoritas'),
            Tab(text: 'Archivos Locales'),
          ],
        ),
      ),
      body: TabBarView(
        controller: _tabController,
        children: [
          // Favorites Tab
          library.favorites.isEmpty
              ? const Center(
                  child: Text(
                    'Aún no tienes canciones favoritas',
                    style: TextStyle(color: SonoraColors.textMuted, fontSize: 14),
                  ),
                )
              : ListView.builder(
                  padding: const EdgeInsets.only(bottom: 120.0, top: 8.0),
                  itemCount: library.favorites.length,
                  itemBuilder: (context, index) {
                    final track = library.favorites[index];
                    final isPlaying = player.currentTrack?.id == track.id && player.isPlaying;
                    return TrackTile(
                      track: track,
                      isPlaying: isPlaying,
                      onTap: () => player.playTrack(track, newQueue: library.favorites),
                    );
                  },
                ),

          // Local Device Tracks Tab
          library.isLoadingLocal
              ? const Center(child: CircularProgressIndicator(color: SonoraColors.accent))
              : library.localTracks.isEmpty
                  ? Center(
                      child: Column(
                        mainAxisSize: MainAxisSize.min,
                        children: [
                          const Icon(Icons.folder_open_rounded, size: 54, color: SonoraColors.textMuted),
                          const SizedBox(height: 12),
                          const Text(
                            'Escanea las canciones guardadas en tu celular',
                            style: TextStyle(color: SonoraColors.textSecondary, fontSize: 14),
                          ),
                          const SizedBox(height: 16),
                          ElevatedButton.icon(
                            style: ElevatedButton.styleFrom(
                              backgroundColor: SonoraColors.accent,
                              foregroundColor: Colors.white,
                              shape: RoundedRectangleBorder(borderRadius: BorderRadius.circular(12)),
                            ),
                            onPressed: () => library.scanLocalMusic(),
                            icon: const Icon(Icons.search, size: 18),
                            label: const Text('Escanear Dispositivo'),
                          ),
                        ],
                      ),
                    )
                  : ListView.builder(
                      padding: const EdgeInsets.only(bottom: 120.0, top: 8.0),
                      itemCount: library.localTracks.length,
                      itemBuilder: (context, index) {
                        final track = library.localTracks[index];
                        final isPlaying = player.currentTrack?.id == track.id && player.isPlaying;
                        return TrackTile(
                          track: track,
                          isPlaying: isPlaying,
                          onTap: () => player.playTrack(track, newQueue: library.localTracks),
                        );
                      },
                    ),
        ],
      ),
    );
  }
}

