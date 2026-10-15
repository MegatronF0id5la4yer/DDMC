import 'dart:convert';
import 'package:http/http.dart' as http;
import '../models/lyric_line.dart';
import '../models/track.dart';

class LyricsService {
  static const String _baseUrl = 'https://lrclib.net/api/get';

  /// Fetch synchronized lyrics for a track
  Future<List<LyricLine>> fetchLyrics(Track track) async {
    try {
      final queryParams = {
        'artist_name': track.artist,
        'track_name': track.title,
      };

      if (track.duration > Duration.zero) {
        queryParams['duration'] = track.duration.inSeconds.toString();
      }

      final uri = Uri.parse(_baseUrl).replace(queryParameters: queryParams);
      final response = await http.get(uri, headers: {
        'User-Agent': 'SonoraMobile/1.0 (https://github.com/sonorahq/sonora)',
      }).timeout(const Duration(seconds: 8));

      if (response.statusCode == 200) {
        final data = jsonDecode(response.body) as Map<String, dynamic>;
        final syncedLyrics = data['syncedLyrics'] as String?;

        if (syncedLyrics != null && syncedLyrics.isNotEmpty) {
          return _parseLrc(syncedLyrics);
        }

        // Fallback to plain lyrics if synced is not available
        final plainLyrics = data['plainLyrics'] as String?;
        if (plainLyrics != null && plainLyrics.isNotEmpty) {
          final lines = plainLyrics.split('\n');
          return lines.asMap().entries.map((entry) {
            return LyricLine(
              timestamp: Duration(seconds: entry.key * 4),
              text: entry.value.trim(),
            );
          }).where((l) => l.text.isNotEmpty).toList();
        }
      }
    } catch (e) {
      print('Error fetching lyrics: $e');
    }
    return [];
  }

  List<LyricLine> _parseLrc(String lrcContent) {
    final lines = lrcContent.split('\n');
    final result = <LyricLine>[];

    for (final line in lines) {
      final parsed = LyricLine.parse(line);
      if (parsed != null && parsed.text.isNotEmpty) {
        result.add(parsed);
      }
    }

    result.sort((a, b) => a.timestamp.compareTo(b.timestamp));
    return result;
  }
}

