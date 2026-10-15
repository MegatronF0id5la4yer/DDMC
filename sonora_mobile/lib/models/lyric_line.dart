class LyricLine {
  final Duration timestamp;
  final String text;

  const LyricLine({
    required this.timestamp,
    required this.text,
  });

  /// Parse standard LRC format [mm:ss.xx] line
  static LyricLine? parse(String line) {
    final match = RegExp(r'\[(\d{2}):(\d{2})\.?(\d{2,3})?\](.*)').firstMatch(line.trim());
    if (match == null) return null;

    final minutes = int.tryParse(match.group(1) ?? '0') ?? 0;
    final seconds = int.tryParse(match.group(2) ?? '0') ?? 0;
    final millisRaw = match.group(3) ?? '0';
    final millis = millisRaw.length == 2 
        ? (int.tryParse(millisRaw) ?? 0) * 10 
        : (int.tryParse(millisRaw) ?? 0);

    final duration = Duration(
      minutes: minutes,
      seconds: seconds,
      milliseconds: millis,
    );

    final text = (match.group(4) ?? '').trim();
    return LyricLine(timestamp: duration, text: text);
  }
}
