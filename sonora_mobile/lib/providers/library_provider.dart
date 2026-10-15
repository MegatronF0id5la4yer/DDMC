import 'package:flutter/foundation.dart';
import '../models/track.dart';
import '../models/playlist.dart';
import '../services/storage_service.dart';
import '../services/local_music_service.dart';

class LibraryProvider extends ChangeNotifier {
  final StorageService _storageService = StorageService();
  final LocalMusicService _localService = LocalMusicService();

  List<Track> _favorites = [];
  List<Track> _localTracks = [];
  List<Playlist> _playlists = [];
  bool _isLoadingLocal = false;

  List<Track> get favorites => _favorites;
  List<Track> get localTracks => _localTracks;
  List<Playlist> get playlists => _playlists;
  bool get isLoadingLocal => _isLoadingLocal;

  LibraryProvider() {
    loadLibrary();
  }

  Future<void> loadLibrary() async {
    _favorites = await _storageService.getFavorites();
    notifyListeners();
  }

  Future<void> scanLocalMusic() async {
    _isLoadingLocal = true;
    notifyListeners();

    _localTracks = await _localService.getLocalTracks();
    _isLoadingLocal = false;
    notifyListeners();
  }

  Future<void> toggleFavorite(Track track) async {
    final isFav = _favorites.any((t) => t.id == track.id);
    if (isFav) {
      await _storageService.removeFavorite(track.id);
      _favorites.removeWhere((t) => t.id == track.id);
    } else {
      await _storageService.saveFavorite(track);
      _favorites.insert(0, track);
    }
    notifyListeners();
  }

  bool isFavorite(String trackId) {
    return _favorites.any((t) => t.id == trackId);
  }
}

