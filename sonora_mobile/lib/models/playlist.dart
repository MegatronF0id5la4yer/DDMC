import 'track.dart';

class Playlist {
  final String id;
  final String title;
  final String? description;
  final String? coverUrl;
  final String author;
  final int trackCount;
  final List<Track> tracks;

  const Playlist({
    required this.id,
    required this.title,
    this.description,
    this.coverUrl,
    this.author = 'Sonora',
    this.trackCount = 0,
    this.tracks = const [],
  });

  Playlist copyWith({
    String? id,
    String? title,
    String? description,
    String? coverUrl,
    String? author,
    int? trackCount,
    List<Track>? tracks,
  }) {
    return Playlist(
      id: id ?? this.id,
      title: title ?? this.title,
      description: description ?? this.description,
      coverUrl: coverUrl ?? this.coverUrl,
      author: author ?? this.author,
      trackCount: trackCount ?? this.trackCount,
      tracks: tracks ?? this.tracks,
    );
  }
}
