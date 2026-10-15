import 'package:flutter/foundation.dart';
import '../services/storage_service.dart';

enum VisualizerStyleKind {
  none,
  bars,
  wave,
  both,
}

class SettingsProvider extends ChangeNotifier {
  final StorageService _storageService = StorageService();

  VisualizerStyleKind _visualizerStyle = VisualizerStyleKind.both;
  String _audioQuality = 'high'; // low, medium, high

  VisualizerStyleKind get visualizerStyle => _visualizerStyle;
  String get audioQuality => _audioQuality;

  SettingsProvider() {
    _loadSettings();
  }

  Future<void> _loadSettings() async {
    final style = await _storageService.getVisualizerStyle();
    _visualizerStyle = _parseStyle(style);
    notifyListeners();
  }

  VisualizerStyleKind _parseStyle(String style) {
    switch (style) {
      case 'none':
        return VisualizerStyleKind.none;
      case 'bars':
        return VisualizerStyleKind.bars;
      case 'wave':
        return VisualizerStyleKind.wave;
      case 'both':
      default:
        return VisualizerStyleKind.both;
    }
  }

  Future<void> setVisualizerStyle(VisualizerStyleKind style) async {
    _visualizerStyle = style;
    await _storageService.setVisualizerStyle(style.name);
    notifyListeners();
  }

  void setAudioQuality(String quality) {
    _audioQuality = quality;
    notifyListeners();
  }
}
