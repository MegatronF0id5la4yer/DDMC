import 'package:flutter/material.dart';
import 'package:video_player/video_player.dart';
import '../models/track.dart';
import '../services/youtube_music_service.dart';
import '../theme/colors.dart';

class VideoPlayerWidget extends StatefulWidget {
  final Track track;
  final VoidCallback? onClose;

  const VideoPlayerWidget({
    super.key,
    required this.track,
    this.onClose,
  });

  @override
  State<VideoPlayerWidget> createState() => _VideoPlayerWidgetState();
}

class _VideoPlayerWidgetState extends State<VideoPlayerWidget> {
  final YouTubeMusicService _ytService = YouTubeMusicService();
  VideoPlayerController? _controller;
  bool _isLoading = true;
  String? _error;

  @override
  void initState() {
    super.initState();
    _initVideo();
  }

  @override
  void didUpdateWidget(covariant VideoPlayerWidget oldWidget) {
    super.didUpdateWidget(oldWidget);
    if (oldWidget.track.videoId != widget.track.videoId) {
      _controller?.dispose();
      _controller = null;
      _initVideo();
    }
  }

  Future<void> _initVideo() async {
    final videoId = widget.track.videoId;
    if (videoId == null) {
      setState(() {
        _isLoading = false;
        _error = 'No video available for this track';
      });
      return;
    }

    setState(() {
      _isLoading = true;
      _error = null;
    });

    try {
      final videoUrl = await _ytService.getVideoStreamUrl(videoId);
      if (videoUrl == null) {
        throw Exception('Could not extract video stream');
      }

      _controller = VideoPlayerController.networkUrl(Uri.parse(videoUrl));
      await _controller!.initialize();
      await _controller!.play();
      _controller!.setLooping(true);

      if (mounted) {
        setState(() {
          _isLoading = false;
        });
      }
    } catch (e) {
      if (mounted) {
        setState(() {
          _isLoading = false;
          _error = 'Error loading video: $e';
        });
      }
    }
  }

  @override
  void dispose() {
    _controller?.dispose();
    _ytService.dispose();
    super.dispose();
  }

  @override
  Widget build(BuildContext context) {
    return Container(
      width: double.infinity,
      decoration: BoxDecoration(
        color: Colors.black,
        borderRadius: BorderRadius.circular(16.0),
        border: Border.all(color: SonoraColors.border),
      ),
      clipBehavior: Clip.antiAlias,
      child: AspectRatio(
        aspectRatio: 16 / 9,
        child: Stack(
          alignment: Alignment.center,
          children: [
            if (_controller != null && _controller!.value.isInitialized)
              GestureDetector(
                onTap: () {
                  setState(() {
                    if (_controller!.value.isPlaying) {
                      _controller!.pause();
                    } else {
                      _controller!.play();
                    }
                  });
                },
                child: Stack(
                  alignment: Alignment.center,
                  children: [
                    VideoPlayer(_controller!),
                    if (!_controller!.value.isPlaying)
                      Container(
                        padding: const EdgeInsets.all(12),
                        decoration: BoxDecoration(
                          color: Colors.black54,
                          shape: BoxShape.circle,
                        ),
                        child: const Icon(
                          Icons.play_arrow_rounded,
                          size: 40,
                          color: Colors.white,
                        ),
                      ),
                  ],
                ),
              )
            else if (_isLoading)
              const Center(
                child: Column(
                  mainAxisSize: MainAxisSize.min,
                  children: [
                    CircularProgressIndicator(color: SonoraColors.accent),
                    SizedBox(height: 12),
                    Text(
                      'Cargando videoclip en HD...',
                      style: TextStyle(color: SonoraColors.textSecondary, fontSize: 13),
                    ),
                  ],
                ),
              )
            else if (_error != null)
              Center(
                child: Padding(
                  padding: const EdgeInsets.all(16.0),
                  child: Column(
                    mainAxisSize: MainAxisSize.min,
                    children: [
                      const Icon(Icons.error_outline_rounded, color: SonoraColors.error, size: 36),
                      const SizedBox(height: 8),
                      Text(
                        _error!,
                        textAlign: TextAlign.center,
                        style: const TextStyle(color: SonoraColors.textSecondary, fontSize: 13),
                      ),
                      const SizedBox(height: 12),
                      TextButton.icon(
                        onPressed: _initVideo,
                        icon: const Icon(Icons.refresh, size: 16),
                        label: const Text('Reintentar'),
                      ),
                    ],
                  ),
                ),
              ),

            // Top control bar
            Positioned(
              top: 8,
              right: 8,
              child: IconButton(
                icon: const Icon(Icons.close, color: Colors.white70),
                onPressed: widget.onClose,
                tooltip: 'Cerrar vídeo',
              ),
            ),
          ],
        ),
      ),
    );
  }
}

