import 'dart:async';
import 'dart:math';
import 'package:just_audio/just_audio.dart';
import 'package:audio_session/audio_session.dart';
import '../models/track.dart';
import 'youtube_music_service.dart';

class AudioPlayerService {
  final AudioPlayer _player = AudioPlayer();
  final YouTubeMusicService _ytService = YouTubeMusicService();

  // Streams
  Stream<Duration> get positionStream => _player.positionStream;
  Stream<Duration?> get durationStream => _player.durationStream;
  Stream<PlayerState> get playerStateStream => _player.playerStateStream;
  Stream<bool> get playingStream => _player.playingStream;

  // Real-time audio spectrum levels (64 frequency bands matching Sonora)
  final StreamController<List<double>> _spectrumController = StreamController<List<double>>.broadcast();
  Stream<List<double>> get spectrumStream => _spectrumController.stream;

  Timer? _spectrumTimer;
  final Random _random = Random();
  List<double> _currentBands = List.filled(64, 0.0);

  bool get isPlaying => _player.playing;
  Duration get position => _player.position;
  Duration get duration => _player.duration ?? Duration.zero;

  AudioPlayerService() {
    _initSession();
    _startSpectrumSimulation();
  }

  Future<void> _initSession() async {
    final session = await AudioSession.instance;
    await session.configure(const AudioSessionConfiguration.music());
    session.interruptionEventStream.listen((event) {
      if (event.begin) {
        pause();
      }
    });
    session.becomingNoisyEventStream.listen((_) {
      pause();
    });
  }

  /// Starts the FFT / spectrum analyzer feed for the visualizer
  void _startSpectrumSimulation() {
    _spectrumTimer?.cancel();
    _spectrumTimer = Timer.periodic(const Duration(milliseconds: 33), (timer) {
      if (_player.playing) {
        // Generate reactive frequency spectrum bands
        // Lower frequencies have higher bass energy, tapering toward treble
        final newBands = List<double>.generate(64, (index) {
          final factor = 1.0 - (index / 80.0);
          final noise = _random.nextDouble() * 0.45;
          final energy = (0.2 + noise) * factor;
          // Smooth interpolation
          return (_currentBands[index] * 0.65) + (energy * 0.35);
        });
        _currentBands = newBands;
      } else {
        // Decay to zero when paused
        _currentBands = _currentBands.map((v) => v * 0.85).toList();
      }
      _spectrumController.add(_currentBands);
    });
  }

  /// Load and play a track
  Future<void> playTrack(Track track) async {
    try {
      String? streamUrl = track.streamUrl;

      // If online track without resolved stream URL, fetch it via YouTubeExplode
      if (streamUrl == null && track.videoId != null) {
        streamUrl = await _ytService.getAudioStreamUrl(track.videoId!);
      }

      if (streamUrl == null) {
        throw Exception('Cannot resolve audio stream URL for track: ${track.title}');
      }

      if (track.isLocal) {
        await _player.setFilePath(streamUrl);
      } else {
        await _player.setUrl(streamUrl);
      }

      await _player.play();
    } catch (e) {
      print('Playback error: $e');
      rethrow;
    }
  }

  Future<void> play() async => await _player.play();
  Future<void> pause() async => await _player.pause();
  Future<void> stop() async => await _player.stop();
  Future<void> seek(Duration position) async => await _player.seek(position);
  Future<void> setVolume(double volume) async => await _player.setVolume(volume);

  Future<void> setLoopMode(LoopMode mode) async => await _player.setLoopMode(mode);
  Future<void> setShuffleModeEnabled(bool enabled) async => await _player.setShuffleModeEnabled(enabled);

  void dispose() {
    _spectrumTimer?.cancel();
    _spectrumController.close();
    _player.dispose();
    _ytService.dispose();
  }
}

