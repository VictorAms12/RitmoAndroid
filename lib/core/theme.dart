import 'package:flutter/material.dart';
import 'package:flutter/services.dart';

class RitmoColors {
  static const seed = Color(0xFF2ECF98);
  static const mint = Color(0xFF58E2B2);
  static const amber = Color(0xFFF6B84B);
  static const danger = Color(0xFFEF6B73);
  static const info = Color(0xFF73B6FF);

  static const lightBg = Color(0xFFF5F8F6);
  static const lightSurface = Color(0xFFFFFFFF);
  static const lightSurfaceAlt = Color(0xFFEAF2EE);
  static const lightText = Color(0xFF14211C);

  static const darkBg = Color(0xFF071A14);
  static const darkSurface = Color(0xFF0D261E);
  static const darkSurfaceAlt = Color(0xFF123328);
  static const darkSurfaceHigh = Color(0xFF173E31);
  static const darkText = Color(0xFFF1FBF7);
}

ThemeData buildLightTheme() {
  final scheme = ColorScheme.fromSeed(
    seedColor: RitmoColors.seed,
    brightness: Brightness.light,
    surface: RitmoColors.lightSurface,
  ).copyWith(
    primary: const Color(0xFF136B50),
    secondary: const Color(0xFF1A8C69),
    tertiary: RitmoColors.amber,
    error: const Color(0xFFC8404C),
  );
  return _baseTheme(scheme, RitmoColors.lightBg, false);
}

ThemeData buildDarkTheme() {
  final scheme = ColorScheme.fromSeed(
    seedColor: RitmoColors.seed,
    brightness: Brightness.dark,
    surface: RitmoColors.darkSurface,
  ).copyWith(
    primary: RitmoColors.mint,
    onPrimary: const Color(0xFF042016),
    secondary: const Color(0xFF7CE6BF),
    tertiary: RitmoColors.amber,
    error: RitmoColors.danger,
    surfaceContainerLow: RitmoColors.darkSurface,
    surfaceContainer: RitmoColors.darkSurfaceAlt,
    surfaceContainerHigh: RitmoColors.darkSurfaceHigh,
  );
  return _baseTheme(scheme, RitmoColors.darkBg, true);
}

ThemeData _baseTheme(ColorScheme scheme, Color scaffold, bool dark) {
  final text = dark ? RitmoColors.darkText : RitmoColors.lightText;
  final theme = ThemeData(
    useMaterial3: true,
    brightness: scheme.brightness,
    colorScheme: scheme,
    scaffoldBackgroundColor: scaffold,
    fontFamily: 'Roboto',
    visualDensity: VisualDensity.standard,
  );

  return theme.copyWith(
    textTheme: theme.textTheme.copyWith(
      headlineLarge: TextStyle(
        fontSize: 30,
        height: 1.1,
        fontWeight: FontWeight.w800,
        letterSpacing: -1.0,
        color: text,
      ),
      headlineMedium: TextStyle(
        fontSize: 24,
        height: 1.15,
        fontWeight: FontWeight.w800,
        letterSpacing: -.7,
        color: text,
      ),
      titleLarge: TextStyle(
        fontSize: 19,
        fontWeight: FontWeight.w800,
        letterSpacing: -.35,
        color: text,
      ),
      titleMedium: TextStyle(
        fontSize: 15,
        fontWeight: FontWeight.w700,
        color: text,
      ),
      bodyLarge: TextStyle(fontSize: 15, height: 1.45, color: text),
      bodyMedium: TextStyle(fontSize: 13, height: 1.4, color: text),
      bodySmall: TextStyle(
        fontSize: 11.5,
        height: 1.35,
        color: text.withValues(alpha: .66),
      ),
      labelLarge: const TextStyle(fontWeight: FontWeight.w800),
    ),
    cardTheme: CardThemeData(
      margin: EdgeInsets.zero,
      elevation: dark ? 0 : 1,
      shadowColor: Colors.black.withValues(alpha: .08),
      color: dark ? RitmoColors.darkSurface : RitmoColors.lightSurface,
      shape: RoundedRectangleBorder(
        borderRadius: BorderRadius.circular(20),
        side: BorderSide(
          color: dark
              ? Colors.white.withValues(alpha: .065)
              : Colors.black.withValues(alpha: .045),
        ),
      ),
    ),
    dividerTheme: DividerThemeData(
      color: dark
          ? Colors.white.withValues(alpha: .075)
          : Colors.black.withValues(alpha: .07),
      thickness: 1,
      space: 1,
    ),
    inputDecorationTheme: InputDecorationTheme(
      filled: true,
      fillColor: dark ? RitmoColors.darkSurfaceAlt : RitmoColors.lightSurfaceAlt,
      border: OutlineInputBorder(
        borderRadius: BorderRadius.circular(14),
        borderSide: BorderSide.none,
      ),
      enabledBorder: OutlineInputBorder(
        borderRadius: BorderRadius.circular(14),
        borderSide: BorderSide(
          color: dark
              ? Colors.white.withValues(alpha: .06)
              : Colors.black.withValues(alpha: .04),
        ),
      ),
      focusedBorder: OutlineInputBorder(
        borderRadius: BorderRadius.circular(14),
        borderSide: BorderSide(color: scheme.primary, width: 1.5),
      ),
      contentPadding: const EdgeInsets.symmetric(horizontal: 14, vertical: 13),
    ),
    chipTheme: theme.chipTheme.copyWith(
      padding: const EdgeInsets.symmetric(horizontal: 8),
      side: BorderSide(
        color: dark
            ? Colors.white.withValues(alpha: .08)
            : Colors.black.withValues(alpha: .07),
      ),
      shape: RoundedRectangleBorder(borderRadius: BorderRadius.circular(999)),
    ),
    filledButtonTheme: FilledButtonThemeData(
      style: FilledButton.styleFrom(
        minimumSize: const Size(48, 48),
        shape: RoundedRectangleBorder(borderRadius: BorderRadius.circular(14)),
        textStyle: const TextStyle(fontWeight: FontWeight.w800),
      ),
    ),
    outlinedButtonTheme: OutlinedButtonThemeData(
      style: OutlinedButton.styleFrom(
        minimumSize: const Size(48, 48),
        shape: RoundedRectangleBorder(borderRadius: BorderRadius.circular(14)),
      ),
    ),
    navigationBarTheme: NavigationBarThemeData(
      height: 72,
      elevation: 0,
      backgroundColor: dark ? const Color(0xFF0A211A) : Colors.white,
      indicatorColor: scheme.primary.withValues(alpha: .16),
      labelTextStyle: WidgetStateProperty.resolveWith((states) {
        return TextStyle(
          fontSize: 11,
          fontWeight:
              states.contains(WidgetState.selected) ? FontWeight.w800 : FontWeight.w600,
        );
      }),
    ),
    bottomSheetTheme: BottomSheetThemeData(
      backgroundColor: dark ? RitmoColors.darkSurface : Colors.white,
      modalBackgroundColor: dark ? RitmoColors.darkSurface : Colors.white,
      shape: const RoundedRectangleBorder(
        borderRadius: BorderRadius.vertical(top: Radius.circular(28)),
      ),
      showDragHandle: true,
    ),
    dialogTheme: DialogThemeData(
      backgroundColor: dark ? RitmoColors.darkSurface : Colors.white,
      shape: RoundedRectangleBorder(borderRadius: BorderRadius.circular(24)),
    ),
    pageTransitionsTheme: const PageTransitionsTheme(
      builders: {
        TargetPlatform.android: FadeForwardsPageTransitionsBuilder(),
        TargetPlatform.iOS: CupertinoPageTransitionsBuilder(),
      },
    ),
    appBarTheme: AppBarTheme(
      elevation: 0,
      scrolledUnderElevation: 0,
      backgroundColor: Colors.transparent,
      foregroundColor: text,
      systemOverlayStyle: dark
          ? SystemUiOverlayStyle.light.copyWith(
              statusBarColor: Colors.transparent,
              systemNavigationBarColor: const Color(0xFF0A211A),
            )
          : SystemUiOverlayStyle.dark.copyWith(
              statusBarColor: Colors.transparent,
              systemNavigationBarColor: Colors.white,
            ),
    ),
  );
}
