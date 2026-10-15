import 'package:youtube_explode_dart/youtube_explode_dart.dart';
import '../models/track.dart';
import '../models/playlist.dart';

class YouTubeMusicService {
  final YoutubeExplode _yt = YoutubeExplode();

  /// Search tracks and music videos on YouTube
  Future<List<Track>> search(String query) async {
    try {
      final searchList = await _yt.search.search(query);
      return searchList.map((video) {
        return Track(
          id: video.id.value,
          title: video.title,
          artist: video.author,
          duration: video.duration ?? Duration.zero,
          coverUrl: video.thumbnails.highResUrl,
          videoId: video.id.value,
        );
      }).toList();
    } catch (e) {
      print('YouTube search error: $e');
      return [];
    }
  }

  /// Get direct playable audio stream URL
  Future<String?> getAudioStreamUrl(String videoId) async {
    try {
      final manifest = await _yt.videos.streamsClient.getManifest(videoId);
      final audioStream = manifest.audioOnly.withHighestBitrate();
      return audioStream.url.toString();
    } catch (e) {
      print('Error getting audio stream: $e');
      return null;
    }
  }

  /// Get direct playable video stream URL (with video + audio or muxed)
  Future<String?> getVideoStreamUrl(String videoId) async {
    try {
      final manifest = await _yt.videos.streamsClient.getManifest(videoId);
      // Try muxed stream first for easy single player playback
      final muxed = manifest.muxed.bestQuality;
      return muxed.url.toString();
    } catch (e) {
      print('Error getting video stream: $e');
      return null;
    }
  }

  /// Explore / Trending Music
  Future<List<Track>> getTrendingMusic() async {
    try {
      // Query popular music
      final searchList = await _yt.search.search('Top Hits Music official audio video');
      return searchList.take(20).map((video) {
        return Track(
          id: video.id.value,
          title: video.title,
          artist: video.author,
          duration: video.duration ?? Duration.zero,
          coverUrl: video.thumbnails.highResUrl,
          videoId: video.id.value,
        );
      }).toList();
    } catch (e) {
      print('Error fetching trending music: $e');
      return [];
    }
  }

  /// Fetch playlist tracks
  Future<Playlist?> getPlaylist(String playlistId) async {
    try {
      final ytPlaylist = await _yt.playlists.get(playlistId);
      final videos = await _yt.playlists.getVideos(playlistId).toList();
      final tracks = videos.map((v) => Track(
        id: v.id.value,
        title: v.title,
        artist: v.author,
        duration: v.duration ?? Duration.zero,
        coverUrl: v.thumbnails.highResUrl,
        videoId: v.id.value,
      )).toList();

      return Playlist(
        id: ytPlaylist.id.value,
        title: ytPlaylist.title,
        description: ytPlaylist.description,
        coverUrl: ytPlaylist.thumbnails.highResUrl,
        author: ytPlaylist.author,
        trackCount: tracks.length,
        tracks: tracks,
      );
    } catch (e) {
      print('Error fetching playlist: $e');
      return null;
    }
  }

  void dispose() {
    _yt.close();
  }
}

