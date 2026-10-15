import 'package:flutter/material.dart';
import 'package:google_fonts/google_fonts.dart';
import 'colors.dart';

class SonoraTheme {
  static ThemeData get darkTheme {
    return ThemeData(
      brightness: Brightness.dark,
      scaffoldBackgroundColor: SonoraColors.background,
      primaryColor: SonoraColors.accent,
      colorScheme: const ColorScheme.dark(
        primary: SonoraColors.accent,
        secondary: SonoraColors.visualizerBar,
        surface: SonoraColors.surface,
        background: SonoraColors.background,
        error: SonoraColors.error,
        onPrimary: Colors.white,
        onSurface: SonoraColors.textPrimary,
        onBackground: SonoraColors.textPrimary,
      ),
      textTheme: GoogleFonts.interTextTheme(
        ThemeData.dark().textTheme,
      ).copyWith(
        titleLarge: GoogleFonts.inter(
          fontSize: 22,
          fontWeight: FontWeight.w700,
          color: SonoraColors.textPrimary,
          letterSpacing: -0.5,
        ),
        titleMedium: GoogleFonts.inter(
          fontSize: 16,
          fontWeight: FontWeight.w600,
          color: SonoraColors.textPrimary,
          letterSpacing: -0.2,
        ),
        bodyLarge: GoogleFonts.inter(
          fontSize: 15,
          fontWeight: FontWeight.w400,
          color: SonoraColors.textPrimary,
        ),
        bodyMedium: GoogleFonts.inter(
          fontSize: 13,
          fontWeight: FontWeight.w400,
          color: SonoraColors.textSecondary,
        ),
        labelSmall: GoogleFonts.inter(
          fontSize: 11,
          fontWeight: FontWeight.w500,
          color: SonoraColors.textMuted,
        ),
      ),
      bottomNavigationBarTheme: const BottomNavigationBarThemeData(
        backgroundColor: SonoraColors.surfaceElevated,
        selectedItemColor: SonoraColors.textPrimary,
        unselectedItemColor: SonoraColors.textMuted,
        type: BottomNavigationBarType.fixed,
        elevation: 0,
      ),
      appBarTheme: const AppBarTheme(
        backgroundColor: Colors.transparent,
        elevation: 0,
        centerTitle: false,
        iconTheme: IconThemeData(color: SonoraColors.textPrimary),
      ),
    );
  }
}

