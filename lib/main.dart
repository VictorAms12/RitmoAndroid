import 'package:flutter/material.dart';
import 'package:flutter/services.dart';
import 'package:intl/date_symbol_data_local.dart';

import 'core/app_state.dart';
import 'core/theme.dart';
import 'screens/shell.dart';
import 'widgets/common.dart';

Future<void> main() async {
  WidgetsFlutterBinding.ensureInitialized();
  await initializeDateFormatting('pt_BR');
  await SystemChrome.setEnabledSystemUIMode(SystemUiMode.edgeToEdge);
  final state = AppState();
  runApp(RitmoApp(state: state));
  state.initialize();
}

class RitmoApp extends StatefulWidget {
  final AppState state;
  const RitmoApp({super.key, required this.state});

  @override
  State<RitmoApp> createState() => _RitmoAppState();
}

class _RitmoAppState extends State<RitmoApp> with WidgetsBindingObserver {
  late bool _loading;
  String? _errorMessage;
  late RitmoThemeMode _themeMode;

  @override
  void initState() {
    super.initState();
    _captureAppSnapshot();
    widget.state.addListener(_onAppStateChanged);
    WidgetsBinding.instance.addObserver(this);
  }

  @override
  void didUpdateWidget(covariant RitmoApp oldWidget) {
    super.didUpdateWidget(oldWidget);
    if (!identical(oldWidget.state, widget.state)) {
      oldWidget.state.removeListener(_onAppStateChanged);
      _captureAppSnapshot();
      widget.state.addListener(_onAppStateChanged);
    }
  }

  void _captureAppSnapshot() {
    _loading = widget.state.loading;
    _errorMessage = widget.state.errorMessage;
    _themeMode = widget.state.themeMode;
  }

  void _onAppStateChanged() {
    final state = widget.state;
    final needsRebuild =
        _loading != state.loading ||
        _errorMessage != state.errorMessage ||
        _themeMode != state.themeMode;
    if (!needsRebuild || !mounted) return;
    setState(_captureAppSnapshot);
  }

  @override
  void dispose() {
    widget.state.removeListener(_onAppStateChanged);
    WidgetsBinding.instance.removeObserver(this);
    super.dispose();
  }

  @override
  void didChangeAppLifecycleState(AppLifecycleState lifecycleState) {
    if (lifecycleState == AppLifecycleState.resumed) {
      widget.state.refreshFromNative();
    }
  }

  @override
  Widget build(BuildContext context) {
    final state = widget.state;
    return MaterialApp(
      title: 'Ritmo',
      debugShowCheckedModeBanner: false,
      theme: buildLightTheme(),
      darkTheme: buildDarkTheme(),
      themeMode: state.materialThemeMode,
      home: _loading
          ? const _LoadingScreen()
          : _errorMessage != null
          ? _ErrorScreen(error: _errorMessage!, onRetry: state.initialize)
          : RitmoShell(state: state),
    );
  }
}

class _LoadingScreen extends StatelessWidget {
  const _LoadingScreen();

  @override
  Widget build(BuildContext context) {
    return Scaffold(
      body: SafeArea(
        child: ListView(
          padding: const EdgeInsets.all(16),
          children: [
            const SizedBox(height: 14),
            Row(
              children: [
                Expanded(
                  child: Container(
                    height: 34,
                    width: 180,
                    alignment: Alignment.centerLeft,
                    child: Text(
                      'Ritmo',
                      style: Theme.of(context).textTheme.headlineMedium,
                    ),
                  ),
                ),
                const CircleAvatar(child: Icon(Icons.hourglass_top_rounded)),
              ],
            ),
            const SizedBox(height: 22),
            const SkeletonCard(height: 170),
            const SizedBox(height: 14),
            Row(
              children: const [
                Expanded(child: SkeletonCard(height: 105)),
                SizedBox(width: 10),
                Expanded(child: SkeletonCard(height: 105)),
                SizedBox(width: 10),
                Expanded(child: SkeletonCard(height: 105)),
              ],
            ),
            const SizedBox(height: 22),
            const SkeletonCard(),
            const SizedBox(height: 10),
            const SkeletonCard(),
            const SizedBox(height: 10),
            const SkeletonCard(),
          ],
        ),
      ),
    );
  }
}

class _ErrorScreen extends StatelessWidget {
  final String error;
  final Future<void> Function() onRetry;
  const _ErrorScreen({required this.error, required this.onRetry});

  @override
  Widget build(BuildContext context) {
    return Scaffold(
      body: SafeArea(
        child: Center(
          child: SingleChildScrollView(
            padding: const EdgeInsets.all(22),
            child: ConstrainedBox(
              constraints: const BoxConstraints(maxWidth: 520),
              child: Card(
                child: Padding(
                  padding: const EdgeInsets.all(20),
                  child: Column(
                    children: [
                      Icon(
                        Icons.error_outline_rounded,
                        size: 50,
                        color: Theme.of(context).colorScheme.error,
                      ),
                      const SizedBox(height: 14),
                      Text(
                        'O Ritmo encontrou um problema',
                        textAlign: TextAlign.center,
                        style: Theme.of(context).textTheme.headlineMedium,
                      ),
                      const SizedBox(height: 8),
                      Text(
                        'Seus dados não serão apagados automaticamente.',
                        textAlign: TextAlign.center,
                        style: Theme.of(context).textTheme.bodySmall,
                      ),
                      const SizedBox(height: 16),
                      Container(
                        width: double.infinity,
                        constraints: const BoxConstraints(maxHeight: 180),
                        padding: const EdgeInsets.all(12),
                        decoration: BoxDecoration(
                          color: Theme.of(context).colorScheme.surfaceContainer,
                          borderRadius: BorderRadius.circular(14),
                        ),
                        child: SingleChildScrollView(
                          child: SelectableText(
                            error,
                            style: Theme.of(context).textTheme.bodySmall,
                          ),
                        ),
                      ),
                      const SizedBox(height: 14),
                      FilledButton.icon(
                        onPressed: onRetry,
                        icon: const Icon(Icons.refresh_rounded),
                        label: const Text('Tentar novamente'),
                      ),
                      const SizedBox(height: 6),
                      TextButton.icon(
                        onPressed: () async {
                          await Clipboard.setData(ClipboardData(text: error));
                          if (context.mounted) {
                            ScaffoldMessenger.of(context).showSnackBar(
                              const SnackBar(
                                content: Text('Diagnóstico copiado.'),
                              ),
                            );
                          }
                        },
                        icon: const Icon(Icons.copy_rounded),
                        label: const Text('Copiar diagnóstico'),
                      ),
                    ],
                  ),
                ),
              ),
            ),
          ),
        ),
      ),
    );
  }
}
