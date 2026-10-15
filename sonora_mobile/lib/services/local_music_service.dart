import 'package:on_audio_query/on_audio_query.dart';
import 'package:permission_handler/permission_handler.dart';
import '../models/track.dart';

class LocalMusicService {
  final OnAudioQuery _audioQuery = OnAudioQuery();

  Future<bool> requestPermissions() async {
    // Request permission based on Android SDK level
    if (await Permission.audio.request().isGranted) {
      return true;
    }
    if (await Permission.storage.request().isGranted) {
      return true;
    }
    return false;
  }

  /// Scan device for local tracks
  Future<List<Track>> getLocalTracks() async {
    try {
      final granted = await requestPermissions();
      if (!granted) return [];

      final songModels = await _audioQuery.querySongs(
        sortType: SongSortType.TITLE,
        orderType: OrderType.ASC_OR_SMALLER,
        uriType: UriType.EXTERNAL,
        ignoreTags: false,
      );

      return songModels.map((song) {
        return Track(
          id: 'local_${song.id}',
          title: song.title,
          artist: song.artist ?? 'Unknown Artist',
          album: song.album ?? 'Unknown Album',
          duration: Duration(milliseconds: song.duration ?? 0),
          coverUrl: null, // Artwork loaded via ArtworkWidget or uri
          streamUrl: song.uri,
          isLocal: true,
        );
      }).toList();
    } catch (e) {
      print('Error querying local songs: $e');
      return [];
    }
  }
}
