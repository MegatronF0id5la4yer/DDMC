import 'package:flutter/material.dart';
import 'package:cached_network_image/cached_network_image.dart';
import '../models/track.dart';
import '../theme/colors.dart';

class TrackTile extends StatelessWidget {
  final Track track;
  final bool isPlaying;
  final VoidCallback onTap;
  final VoidCallback? onMoreOptions;

  const TrackTile({
    super.key,
    required this.track,
    this.isPlaying = false,
    required this.onTap,
    this.onMoreOptions,
  });

  String _formatDuration(Duration d) {
    final minutes = d.inMinutes;
    final seconds = d.inSeconds % 60;
    return '$minutes:${seconds.toString().padLeft(2, '0')}';
  }

  @override
  Widget build(BuildContext context) {
    return InkWell(
      onTap: onTap,
      borderRadius: BorderRadius.circular(12.0),
      child: Padding(
        padding: const EdgeInsets.symmetric(horizontal: 16.0, vertical: 8.0),
        child: Row(
          children: [
            // Artwork
            ClipRRect(
              borderRadius: BorderRadius.circular(8.0),
              child: SizedBox(
                width: 50,
                height: 50,
                child: track.coverUrl != null
                    ? CachedNetworkImage(
                        imageUrl: track.coverUrl!,
                        fit: BoxFit.cover,
                        placeholder: (_, __) => Container(
                          color: SonoraColors.surfaceElevated,
                          child: const Icon(Icons.music_note, color: SonoraColors.textMuted),
                        ),
                        errorWidget: (_, __, ___) => Container(
                          color: SonoraColors.surfaceElevated,
                          child: const Icon(Icons.music_note, color: SonoraColors.textMuted),
                        ),
                      )
                    : Container(
                        color: SonoraColors.surfaceElevated,
                        child: const Icon(Icons.music_note, color: SonoraColors.textMuted),
                      ),
              ),
            ),
            const SizedBox(width: 14),

            // Track Details
            Expanded(
              child: Column(
                crossAxisAlignment: CrossAxisAlignment.start,
                children: [
                  Text(
                    track.title,
                    maxLines: 1,
                    overflow: TextOverflow.ellipsis,
                    style: TextStyle(
                      fontSize: 15,
                      fontWeight: FontWeight.w600,
                      color: isPlaying ? SonoraColors.accent : SonoraColors.textPrimary,
                    ),
                  ),
                  const SizedBox(height: 3),
                  Row(
                    children: [
                      if (track.hasVideo)
                        Padding(
                          padding: const EdgeInsets.only(right: 6.0),
                          child: Icon(Icons.smart_display_rounded, size: 14, color: SonoraColors.accent.withOpacity(0.85)),
                        ),
                      Expanded(
                        child: Text(
                          track.artist,
                          maxLines: 1,
                          overflow: TextOverflow.ellipsis,
                          style: const TextStyle(
                            fontSize: 13,
                            color: SonoraColors.textSecondary,
                          ),
                        ),
                      ),
                    ],
                  ),
                ],
              ),
            ),

            if (track.duration > Duration.zero)
              Text(
                _formatDuration(track.duration),
                style: const TextStyle(fontSize: 12, color: SonoraColors.textMuted),
              ),

            if (onMoreOptions != null)
              IconButton(
                icon: const Icon(Icons.more_vert, color: SonoraColors.textMuted, size: 20),
                onPressed: onMoreOptions,
              ),
          ],
        ),
      ),
    );
  }
}
