import 'package:flutter/cupertino.dart';
import 'package:flutter/material.dart';
import 'package:flutter/services.dart';

class RitmoColors {
  static const seed = Color(0xFF5C6EF8);
  static const primaryLight = Color(0xFF5260E8);
  static const primaryDark = Color(0xFF8B95FF);
  static const violet = Color(0xFFA78BFA);
  static const success = Color(0xFF63A6F5);
  static const mint = Color(0xFF63A6F5);
  static const amber = Color(0xFFF2B84B);
  static const danger = Color(0xFFEF6B73);
  static const info = Color(0xFF63A6F5);

  static const lightBg = Color(0xFFF7F8FA);
  static const lightSurface = Color(0xFFFFFFFF);
  static const lightSurfaceAlt = Color(0xFFF0F2F5);
  static const lightText = Color(0xFF17191F);
  static const lightMuted = Color(0xFF6B7280);

  static const darkBg = Color(0xFF0D0E11);
  static const darkSurface = Color(0xFF15171B);
  static const darkSurfaceAlt = Color(0xFF1C1F24);
  static const darkSurfaceHigh = Color(0xFF252931);
  static const darkText = Color(0xFFF4F5F7);
  static const darkMuted = Color(0xFFA2A8B3);
  static const darkNav = Color(0xFF111317);
}

ThemeData buildLightTheme() => _buildTheme(false);
ThemeData buildDarkTheme() => _buildTheme(true);

ThemeData _buildTheme(bool dark) {
  final primary = dark ? RitmoColors.primaryDark : RitmoColors.primaryLight;
  final bg = dark ? RitmoColors.darkBg : RitmoColors.lightBg;
  final surface = dark ? RitmoColors.darkSurface : RitmoColors.lightSurface;
  final surfaceAlt = dark ? RitmoColors.darkSurfaceAlt : RitmoColors.lightSurfaceAlt;
  final text = dark ? RitmoColors.darkText : RitmoColors.lightText;
  final muted = dark ? RitmoColors.darkMuted : RitmoColors.lightMuted;
  final border = dark
      ? Colors.white.withValues(alpha: .075)
      : Colors.black.withValues(alpha: .055);

  final scheme = ColorScheme.fromSeed(
    seedColor: RitmoColors.seed,
    brightness: dark ? Brightness.dark : Brightness.light,
    surface: surface,
  ).copyWith(
    primary: primary,
    onPrimary: dark ? const Color(0xFF11142F) : Colors.white,
    primaryContainer: dark ? const Color(0xFF30366B) : const Color(0xFFE8EAFF),
    onPrimaryContainer: dark ? const Color(0xFFE8EAFF) : const Color(0xFF252A70),
    secondary: dark ? const Color(0xFFB6A6FF) : const Color(0xFF7258D9),
    tertiary: RitmoColors.amber,
    error: dark ? RitmoColors.danger : const Color(0xFFD94F5C),
    surfaceContainerLow: surface,
    surfaceContainer: surfaceAlt,
    surfaceContainerHigh: dark ? RitmoColors.darkSurfaceHigh : const Color(0xFFE8EBF0),
    outline: dark ? const Color(0xFF454A54) : const Color(0xFFD3D7DE),
    outlineVariant: dark ? const Color(0xFF2E3239) : const Color(0xFFE5E8ED),
  );

  final base = ThemeData(
    useMaterial3: true,
    brightness: scheme.brightness,
    colorScheme: scheme,
    scaffoldBackgroundColor: bg,
    canvasColor: bg,
    fontFamily: 'Roboto',
  );

  return base.copyWith(
    textTheme: base.textTheme.copyWith(
      headlineLarge: TextStyle(fontSize: 30, height: 1.08, fontWeight: FontWeight.w800, letterSpacing: -1, color: text),
      headlineMedium: TextStyle(fontSize: 24, height: 1.12, fontWeight: FontWeight.w800, letterSpacing: -.7, color: text),
      titleLarge: TextStyle(fontSize: 19, fontWeight: FontWeight.w800, letterSpacing: -.35, color: text),
      titleMedium: TextStyle(fontSize: 15, fontWeight: FontWeight.w700, color: text),
      bodyLarge: TextStyle(fontSize: 15, height: 1.45, color: text),
      bodyMedium: TextStyle(fontSize: 13, height: 1.4, color: text),
      bodySmall: TextStyle(fontSize: 11.5, height: 1.35, color: muted),
      labelLarge: TextStyle(fontWeight: FontWeight.w800, color: text),
    ),
    cardTheme: CardThemeData(
      margin: EdgeInsets.zero,
      elevation: dark ? 0 : .8,
      surfaceTintColor: Colors.transparent,
      color: surface,
      shape: RoundedRectangleBorder(
        borderRadius: BorderRadius.circular(18),
        side: BorderSide(color: border),
      ),
    ),
    dividerTheme: DividerThemeData(color: border, thickness: 1, space: 1),
    inputDecorationTheme: InputDecorationTheme(
      filled: true,
      fillColor: surfaceAlt,
      hintStyle: TextStyle(color: muted.withValues(alpha: .86)),
      labelStyle: TextStyle(color: muted),
      border: OutlineInputBorder(borderRadius: BorderRadius.circular(14), borderSide: BorderSide.none),
      enabledBorder: OutlineInputBorder(borderRadius: BorderRadius.circular(14), borderSide: BorderSide(color: border)),
      focusedBorder: OutlineInputBorder(borderRadius: BorderRadius.circular(14), borderSide: BorderSide(color: primary, width: 1.6)),
      contentPadding: const EdgeInsets.symmetric(horizontal: 14, vertical: 13),
    ),
    chipTheme: base.chipTheme.copyWith(
      backgroundColor: surfaceAlt,
      selectedColor: primary.withValues(alpha: dark ? .22 : .12),
      checkmarkColor: primary,
      labelStyle: TextStyle(color: text, fontWeight: FontWeight.w600),
      side: BorderSide(color: border),
      shape: RoundedRectangleBorder(borderRadius: BorderRadius.circular(999)),
    ),
    filledButtonTheme: FilledButtonThemeData(
      style: FilledButton.styleFrom(
        minimumSize: const Size(48, 48),
        elevation: 0,
        shape: RoundedRectangleBorder(borderRadius: BorderRadius.circular(14)),
        textStyle: const TextStyle(fontWeight: FontWeight.w800),
      ),
    ),
    outlinedButtonTheme: OutlinedButtonThemeData(
      style: OutlinedButton.styleFrom(
        minimumSize: const Size(48, 48),
        foregroundColor: primary,
        side: BorderSide(color: border),
        shape: RoundedRectangleBorder(borderRadius: BorderRadius.circular(14)),
      ),
    ),
    floatingActionButtonTheme: FloatingActionButtonThemeData(
      elevation: dark ? 3 : 2,
      backgroundColor: primary,
      foregroundColor: scheme.onPrimary,
      shape: RoundedRectangleBorder(borderRadius: BorderRadius.circular(18)),
    ),
    bottomSheetTheme: BottomSheetThemeData(
      backgroundColor: surface,
      modalBackgroundColor: surface,
      surfaceTintColor: Colors.transparent,
      shape: const RoundedRectangleBorder(borderRadius: BorderRadius.vertical(top: Radius.circular(28))),
      showDragHandle: true,
    ),
    dialogTheme: DialogThemeData(
      backgroundColor: surface,
      surfaceTintColor: Colors.transparent,
      shape: RoundedRectangleBorder(borderRadius: BorderRadius.circular(22)),
    ),
    progressIndicatorTheme: ProgressIndicatorThemeData(
      color: primary,
      linearTrackColor: primary.withValues(alpha: .12),
      circularTrackColor: primary.withValues(alpha: .12),
    ),
    checkboxTheme: CheckboxThemeData(
      shape: RoundedRectangleBorder(borderRadius: BorderRadius.circular(5)),
      fillColor: WidgetStateProperty.resolveWith((states) =>
          states.contains(WidgetState.selected) ? primary : Colors.transparent),
      checkColor: WidgetStatePropertyAll(scheme.onPrimary),
    ),
    switchTheme: SwitchThemeData(
      trackColor: WidgetStateProperty.resolveWith((states) =>
          states.contains(WidgetState.selected)
              ? primary
              : dark
                  ? const Color(0xFF3A3E46)
                  : const Color(0xFFCDD2DA)),
      trackOutlineColor: const WidgetStatePropertyAll(Colors.transparent),
    ),
    appBarTheme: AppBarTheme(
      elevation: 0,
      scrolledUnderElevation: 0,
      backgroundColor: Colors.transparent,
      surfaceTintColor: Colors.transparent,
      foregroundColor: text,
      systemOverlayStyle: dark
          ? SystemUiOverlayStyle.light.copyWith(statusBarColor: Colors.transparent, systemNavigationBarColor: RitmoColors.darkNav)
          : SystemUiOverlayStyle.dark.copyWith(statusBarColor: Colors.transparent, systemNavigationBarColor: Colors.white),
    ),
    pageTransitionsTheme: const PageTransitionsTheme(
      builders: {
        TargetPlatform.android: FadeForwardsPageTransitionsBuilder(),
        TargetPlatform.iOS: CupertinoPageTransitionsBuilder(),
      },
    ),
  );
}
