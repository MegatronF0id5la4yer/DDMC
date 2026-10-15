import 'dart:convert';
import 'package:shared_preferences/shared_preferences.dart';
import '../models/track.dart';

class StorageService {
  static const String _keyFavorites = 'sonora_favorites';
  static const String _keyHistory = 'sonora_history';
  static const String _keyVisualizerStyle = 'sonora_visualizer_style';

  Future<List<Track>> getFavorites() async {
    final prefs = await SharedPreferences.getInstance();
    final jsonList = prefs.getStringList(_keyFavorites) ?? [];
    return jsonList.map((item) => Track.fromJson(jsonDecode(item))).toList();
  }

  Future<void> saveFavorite(Track track) async {
    final prefs = await SharedPreferences.getInstance();
    final list = await getFavorites();
    if (!list.any((t) => t.id == track.id)) {
      list.insert(0, track);
      final encoded = list.map((t) => jsonEncode(t.toJson())).toList();
      await prefs.setStringList(_keyFavorites, encoded);
    }
  }

  Future<void> removeFavorite(String trackId) async {
    final prefs = await SharedPreferences.getInstance();
    final list = await getFavorites();
    list.removeWhere((t) => t.id == trackId);
    final encoded = list.map((t) => jsonEncode(t.toJson())).toList();
    await prefs.setStringList(_keyFavorites, encoded);
  }

  Future<bool> isFavorite(String trackId) async {
    final list = await getFavorites();
    return list.any((t) => t.id == trackId);
  }

  Future<String> getVisualizerStyle() async {
    final prefs = await SharedPreferences.getInstance();
    return prefs.getString(_keyVisualizerStyle) ?? 'both'; // none, bars, wave, both
  }

  Future<void> setVisualizerStyle(String style) async {
    final prefs = await SharedPreferences.getInstance();
    await prefs.setString(_keyVisualizerStyle, style);
  }
}
