import 'package:flutter/material.dart';
import 'package:provider/provider.dart';
import '../models/track.dart';
import '../providers/player_provider.dart';
import '../services/youtube_music_service.dart';
import '../theme/colors.dart';
import '../widgets/track_tile.dart';

class SearchScreen extends StatefulWidget {
  const SearchScreen({super.key});

  @override
  State<SearchScreen> createState() => _SearchScreenState();
}

class _SearchScreenState extends State<SearchScreen> {
  final TextEditingController _searchController = TextEditingController();
  final YouTubeMusicService _ytService = YouTubeMusicService();

  List<Track> _searchResults = [];
  bool _isSearching = false;
  String _activeQuery = '';

  Future<void> _performSearch(String query) async {
    if (query.trim().isEmpty) return;

    setState(() {
      _isSearching = true;
      _activeQuery = query;
    });

    final results = await _ytService.search(query);

    if (mounted) {
      setState(() {
        _searchResults = results;
        _isSearching = false;
      });
    }
  }

  @override
  void dispose() {
    _searchController.dispose();
    super.dispose();
  }

  @override
  Widget build(BuildContext context) {
    final player = context.read<PlayerProvider>();

    return Scaffold(
      backgroundColor: SonoraColors.background,
      appBar: AppBar(
        title: Container(
          height: 44,
          decoration: BoxDecoration(
            color: SonoraColors.surface,
            borderRadius: BorderRadius.circular(12.0),
            border: Border.all(color: SonoraColors.border),
          ),
          child: TextField(
            controller: _searchController,
            textInputAction: TextInputAction.search,
            onSubmitted: _performSearch,
            style: const TextStyle(fontSize: 15, color: SonoraColors.textPrimary),
            decoration: InputDecoration(
              hintText: 'Buscar canciones, artistas, vídeos...',
              hintStyle: const TextStyle(color: SonoraColors.textMuted, fontSize: 14),
              prefixIcon: const Icon(Icons.search_rounded, color: SonoraColors.textMuted),
              suffixIcon: _searchController.text.isNotEmpty
                  ? IconButton(
                      icon: const Icon(Icons.clear_rounded, color: SonoraColors.textMuted),
                      onPressed: () {
                        _searchController.clear();
                        setState(() {
                          _searchResults = [];
                          _activeQuery = '';
                        });
                      },
                    )
                  : null,
              border: InputBorder.none,
              contentPadding: const EdgeInsets.symmetric(vertical: 10),
            ),
          ),
        ),
      ),
      body: _isSearching
          ? const Center(child: CircularProgressIndicator(color: SonoraColors.accent))
          : _searchResults.isEmpty
              ? Center(
                  child: Column(
                    mainAxisSize: MainAxisSize.min,
                    children: [
                      Icon(Icons.search_rounded, size: 64, color: SonoraColors.textMuted.withOpacity(0.4)),
                      const SizedBox(height: 12),
                      Text(
                        _activeQuery.isEmpty
                            ? 'Busca en millones de canciones y vídeos'
                            : 'No se encontraron resultados para "$_activeQuery"',
                        style: const TextStyle(color: SonoraColors.textSecondary, fontSize: 14),
                      ),
                    ],
                  ),
                )
              : ListView.builder(
                  padding: const EdgeInsets.only(bottom: 120.0, top: 8.0),
                  itemCount: _searchResults.length,
                  itemBuilder: (context, index) {
                    final track = _searchResults[index];
                    final isPlaying = player.currentTrack?.id == track.id && player.isPlaying;
                    return TrackTile(
                      track: track,
                      isPlaying: isPlaying,
                      onTap: () => player.playTrack(track, newQueue: _searchResults),
                    );
                  },
                ),
    );
  }
}

