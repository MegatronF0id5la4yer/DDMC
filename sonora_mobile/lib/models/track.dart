class Track {
  final String id;
  final String title;
  final String artist;
  final String album;
  final Duration duration;
  final String? coverUrl;
  final String? videoId;
  final String? streamUrl;
  final bool isLocal;
  final bool isExplicit;
  final Map<String, dynamic>? extra;

  const Track({
    required this.id,
    required this.title,
    required this.artist,
    this.album = '',
    this.duration = Duration.zero,
    this.coverUrl,
    this.videoId,
    this.streamUrl,
    this.isLocal = false,
    this.isExplicit = false,
    this.extra,
  });

  /// Check if this track has an official YouTube video or playable video stream
  bool get hasVideo => videoId != null && videoId!.isNotEmpty;

  /// High quality video URL
  String get videoUrl => hasVideo 
      ? 'https://www.youtube.com/watch?v=$videoId' 
      : 'https://www.youtube.com/results?search_query=${Uri.encodeComponent('$artist $title')}';

  Track copyWith({
    String? id,
    String? title,
    String? artist,
    String? album,
    Duration? duration,
    String? coverUrl,
    String? videoId,
    String? streamUrl,
    bool? isLocal,
    bool? isExplicit,
    Map<String, dynamic>? extra,
  }) {
    return Track(
      id: id ?? this.id,
      title: title ?? this.title,
      artist: artist ?? this.artist,
      album: album ?? this.album,
      duration: duration ?? this.duration,
      coverUrl: coverUrl ?? this.coverUrl,
      videoId: videoId ?? this.videoId,
      streamUrl: streamUrl ?? this.streamUrl,
      isLocal: isLocal ?? this.isLocal,
      isExplicit: isExplicit ?? this.isExplicit,
      extra: extra ?? this.extra,
    );
  }

  Map<String, dynamic> toJson() {
    return {
      'id': id,
      'title': title,
      'artist': artist,
      'album': album,
      'durationMs': duration.inMilliseconds,
      'coverUrl': coverUrl,
      'videoId': videoId,
      'streamUrl': streamUrl,
      'isLocal': isLocal,
      'isExplicit': isExplicit,
    };
  }

  factory Track.fromJson(Map<String, dynamic> json) {
    return Track(
      id: json['id'] as String,
      title: json['title'] as String,
      artist: json['artist'] as String,
      album: json['album'] as String? ?? '',
      duration: Duration(milliseconds: json['durationMs'] as int? ?? 0),
      coverUrl: json['coverUrl'] as String?,
      videoId: json['videoId'] as String?,
      streamUrl: json['streamUrl'] as String?,
      isLocal: json['isLocal'] as bool? ?? false,
      isExplicit: json['isExplicit'] as bool? ?? false,
    );
  }
}
