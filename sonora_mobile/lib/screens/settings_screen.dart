import 'package:flutter/material.dart';
import 'package:provider/provider.dart';
import '../providers/settings_provider.dart';
import '../theme/colors.dart';

class SettingsScreen extends StatelessWidget {
  const SettingsScreen({super.key});

  @override
  Widget build(BuildContext context) {
    final settings = context.watch<SettingsProvider>();

    return Scaffold(
      backgroundColor: SonoraColors.background,
      appBar: AppBar(
        title: const Text(
          'Ajustes',
          style: TextStyle(fontWeight: FontWeight.w700, fontSize: 22, letterSpacing: -0.5),
        ),
      ),
      body: ListView(
        padding: const EdgeInsets.symmetric(horizontal: 16.0, vertical: 12.0),
        children: [
          _buildSectionHeader('Visualizador de Audio'),
          Card(
            color: SonoraColors.surface,
            shape: RoundedRectangleBorder(
              borderRadius: BorderRadius.circular(14.0),
              side: const BorderSide(color: SonoraColors.border),
            ),
            child: Column(
              children: [
                RadioListTile<VisualizerStyleKind>(
                  title: const Text('Barras y Onda (Recomendado)'),
                  subtitle: const Text('Muestra el espectro completo con barras y línea de onda brillante'),
                  value: VisualizerStyleKind.both,
                  groupValue: settings.visualizerStyle,
                  activeColor: SonoraColors.accent,
                  onChanged: (val) {
                    if (val != null) settings.setVisualizerStyle(val);
                  },
                ),
                const Divider(color: SonoraColors.borderSubtle, height: 1),
                RadioListTile<VisualizerStyleKind>(
                  title: const Text('Solo Barras'),
                  subtitle: const Text('Barras de frecuencia reactivas clásicas'),
                  value: VisualizerStyleKind.bars,
                  groupValue: settings.visualizerStyle,
                  activeColor: SonoraColors.accent,
                  onChanged: (val) {
                    if (val != null) settings.setVisualizerStyle(val);
                  },
                ),
                const Divider(color: SonoraColors.borderSubtle, height: 1),
                RadioListTile<VisualizerStyleKind>(
                  title: const Text('Solo Onda'),
                  subtitle: const Text('Curva de onda continua suave con efecto glow'),
                  value: VisualizerStyleKind.wave,
                  groupValue: settings.visualizerStyle,
                  activeColor: SonoraColors.accent,
                  onChanged: (val) {
                    if (val != null) settings.setVisualizerStyle(val);
                  },
                ),
                const Divider(color: SonoraColors.borderSubtle, height: 1),
                RadioListTile<VisualizerStyleKind>(
                  title: const Text('Desactivado'),
                  subtitle: const Text('Ocultar el visualizador en la pantalla de reproducción'),
                  value: VisualizerStyleKind.none,
                  groupValue: settings.visualizerStyle,
                  activeColor: SonoraColors.accent,
                  onChanged: (val) {
                    if (val != null) settings.setVisualizerStyle(val);
                  },
                ),
              ],
            ),
          ),

          const SizedBox(height: 24),
          _buildSectionHeader('Reproducción y Vídeo'),
          Card(
            color: SonoraColors.surface,
            shape: RoundedRectangleBorder(
              borderRadius: BorderRadius.circular(14.0),
              side: const BorderSide(color: SonoraColors.border),
            ),
            child: Column(
              children: [
                ListTile(
                  title: const Text('Calidad de Streaming'),
                  subtitle: const Text('Alta (256 kbps AAC / Opus)'),
                  trailing: const Icon(Icons.arrow_forward_ios_rounded, size: 16, color: SonoraColors.textMuted),
                  onTap: () {},
                ),
                const Divider(color: SonoraColors.borderSubtle, height: 1),
                SwitchListTile(
                  title: const Text('Carga de Vídeos en HD'),
                  subtitle: const Text('Priorizar resolución 1080p/720p en conexiones Wi-Fi'),
                  value: true,
                  activeColor: SonoraColors.accent,
                  onChanged: (val) {},
                ),
                const Divider(color: SonoraColors.borderSubtle, height: 1),
                SwitchListTile(
                  title: const Text('Normalización de Volumen'),
                  subtitle: const Text('Ajusta el volumen para evitar saltos bruscos entre pistas'),
                  value: true,
                  activeColor: SonoraColors.accent,
                  onChanged: (val) {},
                ),
              ],
            ),
          ),

          const SizedBox(height: 24),
          _buildSectionHeader('Acerca de'),
          Card(
            color: SonoraColors.surface,
            shape: RoundedRectangleBorder(
              borderRadius: BorderRadius.circular(14.0),
              side: const BorderSide(color: SonoraColors.border),
            ),
            child: const Column(
              children: [
                ListTile(
                  title: Text('Sonora Mobile'),
                  subtitle: Text('Versión 1.0.0 (Port Android con soporte de vídeo y visualizador)'),
                ),
                Divider(color: SonoraColors.borderSubtle, height: 1),
                ListTile(
                  title: Text('Licencia'),
                  subtitle: Text('GPL-3.0 - Código Abierto'),
                ),
              ],
            ),
          ),
          const SizedBox(height: 120),
        ],
      ),
    );
  }

  Widget _buildSectionHeader(String title) {
    return Padding(
      padding: const EdgeInsets.only(left: 6.0, bottom: 8.0),
      child: Text(
        title,
        style: const TextStyle(
          fontSize: 14,
          fontWeight: FontWeight.w600,
          color: SonoraColors.accent,
          letterSpacing: 0.5,
        ),
      ),
    );
  }
}
