import 'dart:async';
import 'package:flutter/foundation.dart';
import 'package:just_audio/just_audio.dart';
import '../models/track.dart';
import '../models/lyric_line.dart';
import '../services/audio_player_service.dart';
import '../services/lyrics_service.dart';

class PlayerProvider extends ChangeNotifier {
  final AudioPlayerService _audioService = AudioPlayerService();
  final LyricsService _lyricsService = LyricsService();

  Track? _currentTrack;
  List<Track> _queue = [];
  int _currentIndex = -1;

  bool _isPlaying = false;
  Duration _position = Duration.zero;
  Duration _duration = Duration.zero;
  bool _isShuffle = false;
  LoopMode _loopMode = LoopMode.off;

  // Visualizer spectrum data (64 frequency bands)
  List<double> _spectrum = List.filled(64, 0.0);

  // Synced Lyrics
  List<LyricLine> _lyrics = [];
  bool _isLoadingLyrics = false;

  // Video Mode: allows watching the music video on mobile!
  bool _videoMode = false;

  // Stream Subscriptions
  StreamSubscription? _posSub;
  StreamSubscription? _durSub;
  StreamSubscription? _stateSub;
  StreamSubscription? _spectrumSub;

  // Getters
  Track? get currentTrack => _currentTrack;
  List<Track> get queue => _queue;
  int get currentIndex => _currentIndex;
  bool get isPlaying => _isPlaying;
  Duration get position => _position;
  Duration get duration => _duration;
  bool get isShuffle => _isShuffle;
  LoopMode get loopMode => _loopMode;
  List<double> get spectrum => _spectrum;
  List<LyricLine> get lyrics => _lyrics;
  bool get isLoadingLyrics => _isLoadingLyrics;
  bool get videoMode => _videoMode;

  PlayerProvider() {
    _initListeners();
  }

  void _initListeners() {
    _posSub = _audioService.positionStream.listen((pos) {
      _position = pos;
      notifyListeners();
    });

    _durSub = _audioService.durationStream.listen((dur) {
      if (dur != null) {
        _duration = dur;
        notifyListeners();
      }
    });

    _stateSub = _audioService.playerStateStream.listen((state) {
      _isPlaying = state.playing;
      if (state.processingState == ProcessingState.completed) {
        playNext();
      }
      notifyListeners();
    });

    _spectrumSub = _audioService.spectrumStream.listen((levels) {
      _spectrum = levels;
      notifyListeners();
    });
  }

  /// Toggle between Album Art (+ Visualizer) and Video Mode
  void toggleVideoMode() {
    _videoMode = !_videoMode;
    notifyListeners();
  }

  void setVideoMode(bool enabled) {
    _videoMode = enabled;
    notifyListeners();
  }

  /// Play a specific track or start a playlist
  Future<void> playTrack(Track track, {List<Track>? newQueue}) async {
    _currentTrack = track;
    if (newQueue != null && newQueue.isNotEmpty) {
      _queue = List.from(newQueue);
      _currentIndex = _queue.indexWhere((t) => t.id == track.id);
      if (_currentIndex == -1) {
        _queue.insert(0, track);
        _currentIndex = 0;
      }
    } else if (!_queue.any((t) => t.id == track.id)) {
      _queue.add(track);
      _currentIndex = _queue.length - 1;
    } else {
      _currentIndex = _queue.indexWhere((t) => t.id == track.id);
    }

    notifyListeners();

    // Fetch synced lyrics in background
    _loadLyrics(track);

    // Play track via audio service
    await _audioService.playTrack(track);
  }

  Future<void> _loadLyrics(Track track) async {
    _isLoadingLyrics = true;
    _lyrics = [];
    notifyListeners();

    final result = await _lyricsService.fetchLyrics(track);
    _lyrics = result;
    _isLoadingLyrics = false;
    notifyListeners();
  }

  Future<void> togglePlayPause() async {
    if (_isPlaying) {
      await _audioService.pause();
    } else {
      await _audioService.play();
    }
  }

  Future<void> seek(Duration pos) async {
    await _audioService.seek(pos);
  }

  Future<void> playNext() async {
    if (_queue.isEmpty) return;
    if (_currentIndex < _queue.length - 1) {
      _currentIndex++;
      await playTrack(_queue[_currentIndex]);
    } else if (_loopMode == LoopMode.all) {
      _currentIndex = 0;
      await playTrack(_queue[_currentIndex]);
    }
  }

  Future<void> playPrevious() async {
    if (_queue.isEmpty) return;
    if (_position.inSeconds > 3) {
      // If played for more than 3s, restart current track
      await seek(Duration.zero);
      return;
    }
    if (_currentIndex > 0) {
      _currentIndex--;
      await playTrack(_queue[_currentIndex]);
    } else {
      await seek(Duration.zero);
    }
  }

  Future<void> toggleShuffle() async {
    _isShuffle = !_isShuffle;
    await _audioService.setShuffleModeEnabled(_isShuffle);
    notifyListeners();
  }

  Future<void> toggleLoopMode() async {
    switch (_loopMode) {
      case LoopMode.off:
        _loopMode = LoopMode.all;
        break;
      case LoopMode.all:
        _loopMode = LoopMode.one;
        break;
      case LoopMode.one:
        _loopMode = LoopMode.off;
        break;
    }
    await _audioService.setLoopMode(_loopMode);
    notifyListeners();
  }

  @override
  void dispose() {
    _posSub?.cancel();
    _durSub?.cancel();
    _stateSub?.cancel();
    _spectrumSub?.cancel();
    _audioService.dispose();
    super.dispose();
  }
}

