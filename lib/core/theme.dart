import 'package:flutter/cupertino.dart';
import 'package:flutter/material.dart';
import 'package:flutter/services.dart';

class RitmoColors {
  static const seed = Color(0xFF5C6EF8);
  static const primaryLight = Color(0xFF5260E8);
  static const primaryDark = Color(0xFF8B95FF);
  static const violet = Color(0xFFA78BFA);

  static const success = Color(0xFF35C48D);
  static const mint = Color(0xFF35C48D);
  static const amber = Color(0xFFF2B84B);
  static const danger = Color(0xFFEF6B73);
  static const info = Color(0xFF63A6F5);

  static const lightBg = Color(0xFFF7F8FA);
  static const lightSurface = Color(0xFFFFFFFF);
  static const lightSurfaceAlt = Color(0xFFF0F2F5);
  static const lightSurfaceHigh = Color(0xFFE8EBF0);
  static const lightText = Color(0xFF17191F);
  static const lightMuted = Color(0xFF6B7280);
  static const lightNav = Color(0xFFFFFFFF);

  static const darkBg = Color(0xFF0D0E11);
  static const darkSurface = Color(0xFF15171B);
  static const darkSurfaceAlt = Color(0xFF1C1F24);
  static const darkSurfaceHigh = Color(0xFF252931);
  static const darkText = Color(0xFFF4F5F7);
  static const darkMuted = Color(0xFFA2A8B3);
  static const darkNav = Color(0xFF111317);
}

ThemeData buildLightTheme() {
  final scheme = ColorScheme.fromSeed(
    seedColor: RitmoColors.seed,
    brightness: Brightness.light,
    surface: RitmoColors.lightSurface,
  ).copyWith(
    primary: RitmoColors.primaryLight,
    onPrimary: Colors.white,
    primaryContainer: const Color(0xFFE8EAFF),
    onPrimaryContainer: const Color(0xFF252A70),
    secondary: const Color(0xFF7258D9),
    onSecondary: Colors.white,
    secondaryContainer: const Color(0xFFEEE9FF),
    onSecondaryContainer: const Color(0xFF302064),
    tertiary: RitmoColors.amber,
    error: const Color(0xFFD94F5C),
    surface: RitmoColors.lightSurface,
    surfaceContainerLow: RitmoColors.lightSurface,
    surfaceContainer: RitmoColors.lightSurfaceAlt,
    surfaceContainerHigh: RitmoColors.lightSurfaceHigh,
    outline: const Color(0xFFD3D7DE),
    outlineVariant: const Color(0xFFE5E8ED),
  );
  return _baseTheme(
    scheme: scheme,
    scaffold: RitmoColors.lightBg,
    text: RitmoColors.lightText,
    muted: RitmoColors.lightMuted,
    nav: RitmoColors.lightNav,
    dark: false,
  );
}

ThemeData buildDarkTheme() {
  final scheme = ColorScheme.fromSeed(
    seedColor: RitmoColors.seed,
    brightness: Brightness.dark,
    surface: RitmoColors.darkSurface,
  ).copyWith(
    primary: RitmoColors.primaryDark,
    onPrimary: const Color(0xFF11142F),
    primaryContainer: const Color(0xFF30366B),
    onPrimaryContainer: const Color(0xFFE8EAFF),
    secondary: const Color(0xFFB6A6FF),
    onSecondary: const Color(0xFF251D49),
    secondaryContainer: const Color(0xFF372E61),
    onSecondaryContainer: const Color(0xFFF0EBFF),
    tertiary: const Color(0xFFF4C15D),
    error: RitmoColors.danger,
    surface: RitmoColors.darkSurface,
    surfaceContainerLowest: RitmoColors.darkBg,
    surfaceContainerLow: RitmoColors.darkSurface,
    surfaceContainer: RitmoColors.darkSurfaceAlt,
    surfaceContainerHigh: RitmoColors.darkSurfaceHigh,
    surfaceContainerHighest: const Color(0xFF2D323B),
    outline: const Color(0xFF454A54),
    outlineVariant: const Color(0xFF2E3239),
  );
  return _baseTheme(
    scheme: scheme,
    scaffold: RitmoColors.darkBg,
    text: RitmoColors.darkText,
    muted: RitmoColors.darkMuted,
    nav: RitmoColors.darkNav,
    dark: true,
  );
}

ThemeData _baseTheme({
  required ColorScheme scheme,
  required Color scaffold,
  required Color text,
  required Color muted,
  required Color nav,
  required bool dark,
}) {
  final base = ThemeData(
    useMaterial3: true,
    brightness: scheme.brightness,
    colorScheme: scheme,
    scaffoldBackgroundColor: scaffold,
    canvasColor: scaffold,
    fontFamily: 'Roboto',
    visualDensity: VisualDensity.standard,
  );

  final borderColor = dark
      ? Colors.white.withValues(alpha: .075)
      : Colors.black.withValues(alpha: .055);

  return base.copyWith(
    textTheme: base.textTheme.copyWith(
      headlineLarge: TextStyle(
        fontSize: 30,
        height: 1.08,
        fontWeight: FontWeight.w800,
        letterSpacing: -1.0,
        color: text,
      ),
      headlineMedium: TextStyle(
        fontSize: 24,
        height: 1.12,
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
        color: muted,
      ),
      labelLarge: TextStyle(fontWeight: FontWeight.w800, color: text),
    ),
    iconTheme: IconThemeData(
      color: dark ? const Color(0xFFD9DCE2) : const Color(0xFF4C515A),
    ),
    cardTheme: CardThemeData(
      margin: EdgeInsets.zero,
      elevation: dark ? 0 : .8,
      shadowColor: Colors.black.withValues(alpha: dark ? .18 : .07),
      surfaceTintColor: Colors.transparent,
      color: dark ? RitmoColors.darkSurface : RitmoColors.lightSurface,
      shape: RoundedRectangleBorder(
        borderRadius: BorderRadius.circular(18),
        side: BorderSide(color: borderColor),
      ),
    ),
    dividerTheme: DividerThemeData(
      color: dark
          ? Colors.white.withValues(alpha: .07)
          : Colors.black.withValues(alpha: .065),
      thickness: 1,
      space: 1,
    ),
    inputDecorationTheme: InputDecorationTheme(
      filled: true,
      fillColor: dark ? RitmoColors.darkSurfaceAlt : RitmoColors.lightSurfaceAlt,
      hintStyle: TextStyle(color: muted.withValues(alpha: .86)),
      labelStyle: TextStyle(color: muted),
      border: OutlineInputBorder(
        borderRadius: BorderRadius.circular(14),
        borderSide: BorderSide.none,
      ),
      enabledBorder: OutlineInputBorder(
        borderRadius: BorderRadius.circular(14),
        borderSide: BorderSide(color: borderColor),
      ),
      focusedBorder: OutlineInputBorder(
        borderRadius: BorderRadius.circular(14),
        borderSide: BorderSide(color: scheme.primary, width: 1.6),
      ),
      errorBorder: OutlineInputBorder(
        borderRadius: BorderRadius.circular(14),
        borderSide: BorderSide(color: scheme.error),
      ),
      contentPadding: const EdgeInsets.symmetric(horizontal: 14, vertical: 13),
    ),
    chipTheme: base.chipTheme.copyWith(
      backgroundColor:
          dark ? RitmoColors.darkSurfaceAlt : RitmoColors.lightSurfaceAlt,
      selectedColor: scheme.primary.withValues(alpha: dark ? .22 : .12),
      checkmarkColor: scheme.primary,
      labelStyle: TextStyle(color: text, fontWeight: FontWeight.w600),
      padding: const EdgeInsets.symmetric(horizontal: 8),
      side: BorderSide(color: borderColor),
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
        foregroundColor: scheme.primary,
        side: BorderSide(color: borderColor),
        shape: RoundedRectangleBorder(borderRadius: BorderRadius.circular(14)),
      ),
    ),
    textButtonTheme: TextButtonThemeData(
      style: TextButton.styleFrom(
        foregroundColor: scheme.primary,
        textStyle: const TextStyle(fontWeight: FontWeight.w700),
      ),
    ),
    floatingActionButtonTheme: FloatingActionButtonThemeData(
      elevation: dark ? 3 : 2,
      highlightElevation: 5,
      backgroundColor: scheme.primary,
      foregroundColor: scheme.onPrimary,
      shape: RoundedRectangleBorder(borderRadius: BorderRadius.circular(18)),
    ),
    navigationBarTheme: NavigationBarThemeData(
      height: 72,
      elevation: 0,
      backgroundColor: nav,
      surfaceTintColor: Colors.transparent,
      indicatorColor: scheme.primary.withValues(alpha: dark ? .18 : .11),
      indicatorShape: RoundedRectangleBorder(borderRadius: BorderRadius.circular(14)),
      iconTheme: WidgetStateProperty.resolveWith((states) {
        final selected = states.contains(WidgetState.selected);
        return IconThemeData(
          size: selected ? 24 : 23,
          color: selected ? scheme.primary : muted,
        );
      }),
      labelTextStyle: WidgetStateProperty.resolveWith((states) {
        final selected = states.contains(WidgetState.selected);
        return TextStyle(
          fontSize: 11,
          color: selected ? scheme.primary : muted,
          fontWeight: selected ? FontWeight.w800 : FontWeight.w600,
        );
      }),
    ),
    bottomSheetTheme: BottomSheetThemeData(
      backgroundColor: dark ? RitmoColors.darkSurface : Colors.white,
      modalBackgroundColor: dark ? RitmoColors.darkSurface : Colors.white,
      surfaceTintColor: Colors.transparent,
      dragHandleColor: dark
          ? Colors.white.withValues(alpha: .22)
          : Colors.black.withValues(alpha: .18),
      shape: const RoundedRectangleBorder(
        borderRadius: BorderRadius.vertical(top: Radius.circular(28)),
      ),
      showDragHandle: true,
    ),
    dialogTheme: DialogThemeData(
      elevation: dark ? 4 : 2,
      backgroundColor: dark ? RitmoColors.darkSurface : Colors.white,
      surfaceTintColor: Colors.transparent,
      shape: RoundedRectangleBorder(borderRadius: BorderRadius.circular(22)),
    ),
    snackBarTheme: SnackBarThemeData(
      behavior: SnackBarBehavior.floating,
      elevation: 3,
      backgroundColor:
          dark ? RitmoColors.darkSurfaceHigh : const Color(0xFF252830),
      contentTextStyle: const TextStyle(
        color: Color(0xFFF8F9FB),
        fontWeight: FontWeight.w600,
      ),
      actionTextColor: dark ? RitmoColors.primaryDark : const Color(0xFFAEB5FF),
      shape: RoundedRectangleBorder(borderRadius: BorderRadius.circular(14)),
    ),
    progressIndicatorTheme: ProgressIndicatorThemeData(
      color: scheme.primary,
      linearTrackColor: scheme.primary.withValues(alpha: dark ? .15 : .10),
      circularTrackColor: scheme.primary.withValues(alpha: dark ? .15 : .10),
    ),
    checkboxTheme: CheckboxThemeData(
      shape: RoundedRectangleBorder(borderRadius: BorderRadius.circular(5)),
      side: BorderSide(
        color: dark ? const Color(0xFF5B606A) : const Color(0xFFB7BDC7),
        width: 1.5,
      ),
      fillColor: WidgetStateProperty.resolveWith((states) {
        if (states.contains(WidgetState.selected)) return scheme.primary;
        return Colors.transparent;
      }),
      checkColor: WidgetStatePropertyAll(scheme.onPrimary),
    ),
    switchTheme: SwitchThemeData(
      thumbColor: WidgetStateProperty.resolveWith((states) {
        return states.contains(WidgetState.selected)
            ? scheme.onPrimary
            : dark
                ? const Color(0xFFC7CBD2)
                : Colors.white;
      }),
      trackColor: WidgetStateProperty.resolveWith((states) {
        return states.contains(WidgetState.selected)
            ? scheme.primary
            : dark
                ? const Color(0xFF3A3E46)
                : const Color(0xFFCDD2DA);
      }),
      trackOutlineColor: const WidgetStatePropertyAll(Colors.transparent),
    ),
    appBarTheme: AppBarTheme(
      elevation: 0,
      scrolledUnderElevation: 0,
      backgroundColor: Colors.transparent,
      surfaceTintColor: Colors.transparent,
      foregroundColor: text,
      titleTextStyle: TextStyle(
        color: text,
        fontSize: 20,
        fontWeight: FontWeight.w800,
        letterSpacing: -.3,
      ),
      systemOverlayStyle: dark
          ? SystemUiOverlayStyle.light.copyWith(
              statusBarColor: Colors.transparent,
              systemNavigationBarColor: RitmoColors.darkNav,
            )
          : SystemUiOverlayStyle.dark.copyWith(
              statusBarColor: Colors.transparent,
              systemNavigationBarColor: RitmoColors.lightNav,
            ),
    ),
    pageTransitionsTheme: const PageTransitionsTheme(
      builders: {
        TargetPlatform.android: FadeForwardsPageTransitionsBuilder(),
        TargetPlatform.iOS: CupertinoPageTransitionsBuilder(),
      },
    ),
  );
}
