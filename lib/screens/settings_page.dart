import 'package:flutter/foundation.dart';
import 'package:flutter/material.dart';
import 'package:flutter/services.dart';

import '../core/app_state.dart';
import '../core/native_bridge.dart';

class SettingsPage extends StatefulWidget {
  final AppState state;
  final VoidCallback onOpenPlanner;
  const SettingsPage({
    super.key,
    required this.state,
    required this.onOpenPlanner,
  });

  @override
  State<SettingsPage> createState() => _SettingsPageState();
}

class _SettingsPageState extends State<SettingsPage> {
  late final TextEditingController _name;
  late final TextEditingController _capacity;
  late final TextEditingController _startHour;
  late final TextEditingController _endHour;

  @override
  void initState() {
    super.initState();
    final s = widget.state;
    _name = TextEditingController(text: s.userName);
    _capacity = TextEditingController(text: '${s.plannerCapacityMinutes ~/ 60}');
    _startHour = TextEditingController(text: '${s.plannerStartHour}');
    _endHour = TextEditingController(text: '${s.plannerEndHour}');
  }

  @override
  void dispose() {
    _name.dispose();
    _capacity.dispose();
    _startHour.dispose();
    _endHour.dispose();
    super.dispose();
  }

  Future<void> _savePlanner() async {
    final capacityHours = int.tryParse(_capacity.text) ?? 6;
    final start = (int.tryParse(_startHour.text) ?? 8).clamp(0, 23).toInt();
    final end = (int.tryParse(_endHour.text) ?? 22).clamp(start + 1, 24).toInt();
    final capacity = capacityHours.clamp(1, 16).toInt();
    await widget.state.setPlannerSettings(
      startHour: start,
      endHour: end,
      capacityMinutes: capacity * 60,
      includeWeekend: widget.state.plannerIncludeWeekend,
    );
    _capacity.text = '$capacity';
    _startHour.text = '$start';
    _endHour.text = '$end';
    if (mounted) {
      ScaffoldMessenger.of(context).showSnackBar(
        const SnackBar(
          content: Text('Preferências de planejamento salvas.'),
          behavior: SnackBarBehavior.floating,
        ),
      );
    }
  }

  @override
  Widget build(BuildContext context) {
    final state = widget.state;
    return ListView(
      padding: const EdgeInsets.fromLTRB(16, 18, 16, 110),
      children: [
        Text('Ajustes', style: Theme.of(context).textTheme.headlineMedium),
        const SizedBox(height: 4),
        Text(
          'Personalize o Ritmo para sua rotina e seu jeito de trabalhar.',
          style: Theme.of(context).textTheme.bodySmall,
        ),
        const SizedBox(height: 20),
        _SettingsCard(
          title: 'Perfil',
          icon: Icons.person_outline_rounded,
          children: [
            TextField(
              controller: _name,
              textCapitalization: TextCapitalization.words,
              decoration: const InputDecoration(
                labelText: 'Seu nome',
                hintText: 'Usado apenas no cumprimento',
                prefixIcon: Icon(Icons.badge_outlined),
              ),
              onSubmitted: state.setUserName,
            ),
            const SizedBox(height: 10),
            FilledButton.tonal(
              onPressed: () => state.setUserName(_name.text),
              child: const Text('Salvar nome'),
            ),
          ],
        ),
        const SizedBox(height: 12),
        _SettingsCard(
          title: 'Aparência',
          icon: Icons.palette_outlined,
          children: [
            SegmentedButton<RitmoThemeMode>(
              segments: const [
                ButtonSegment(
                  value: RitmoThemeMode.system,
                  label: Text('Sistema'),
                  icon: Icon(Icons.phone_android_rounded),
                ),
                ButtonSegment(
                  value: RitmoThemeMode.light,
                  label: Text('Claro'),
                  icon: Icon(Icons.light_mode_rounded),
                ),
                ButtonSegment(
                  value: RitmoThemeMode.dark,
                  label: Text('Escuro'),
                  icon: Icon(Icons.dark_mode_rounded),
                ),
              ],
              selected: {state.themeMode},
              onSelectionChanged: (v) => state.setTheme(v.first),
            ),
            const SizedBox(height: 8),
            SwitchListTile.adaptive(
              value: state.reduceMotion,
              onChanged: state.setReduceMotion,
              contentPadding: EdgeInsets.zero,
              title: const Text('Reduzir animações'),
              subtitle: const Text(
                'Diminui transições e movimentos para uma experiência mais confortável.',
              ),
              secondary: const Icon(Icons.motion_photos_off_outlined),
            ),
            SwitchListTile.adaptive(
              value: state.haptics,
              onChanged: state.setHaptics,
              contentPadding: EdgeInsets.zero,
              title: const Text('Feedback tátil'),
              subtitle: const Text('Vibração leve ao concluir e executar ações importantes.'),
              secondary: const Icon(Icons.vibration_rounded),
            ),
          ],
        ),
        const SizedBox(height: 12),
        _SettingsCard(
          title: 'Planejador',
          icon: Icons.auto_awesome_rounded,
          children: [
            Row(
              children: [
                Expanded(
                  child: TextField(
                    controller: _capacity,
                    keyboardType: TextInputType.number,
                    inputFormatters: [FilteringTextInputFormatter.digitsOnly],
                    decoration: const InputDecoration(
                      labelText: 'Capacidade (h/dia)',
                      prefixIcon: Icon(Icons.battery_5_bar_rounded),
                    ),
                  ),
                ),
                const SizedBox(width: 10),
                Expanded(
                  child: TextField(
                    controller: _startHour,
                    keyboardType: TextInputType.number,
                    inputFormatters: [FilteringTextInputFormatter.digitsOnly],
                    decoration: const InputDecoration(
                      labelText: 'Começa às',
                      suffixText: 'h',
                    ),
                  ),
                ),
                const SizedBox(width: 10),
                Expanded(
                  child: TextField(
                    controller: _endHour,
                    keyboardType: TextInputType.number,
                    inputFormatters: [FilteringTextInputFormatter.digitsOnly],
                    decoration: const InputDecoration(
                      labelText: 'Termina às',
                      suffixText: 'h',
                    ),
                  ),
                ),
              ],
            ),
            const SizedBox(height: 6),
            SwitchListTile.adaptive(
              value: state.plannerIncludeWeekend,
              onChanged: (v) async {
                await state.setPlannerSettings(
                  startHour: state.plannerStartHour,
                  endHour: state.plannerEndHour,
                  capacityMinutes: state.plannerCapacityMinutes,
                  includeWeekend: v,
                );
                if (mounted) setState(() {});
              },
              contentPadding: EdgeInsets.zero,
              title: const Text('Usar fins de semana'),
              secondary: const Icon(Icons.weekend_outlined),
            ),
            SwitchListTile.adaptive(
              value: state.autoReplanOverdue,
              onChanged: state.setAutoReplan,
              contentPadding: EdgeInsets.zero,
              title: const Text('Replanejar flexíveis atrasadas'),
              subtitle: const Text(
                'No máximo uma vez por dia, sem mover compromissos fixos.',
              ),
              secondary: const Icon(Icons.update_rounded),
            ),
            const SizedBox(height: 8),
            Row(
              children: [
                Expanded(
                  child: OutlinedButton.icon(
                    onPressed: widget.onOpenPlanner,
                    icon: const Icon(Icons.calendar_month_rounded),
                    label: const Text('Abrir planejador'),
                  ),
                ),
                const SizedBox(width: 10),
                Expanded(
                  child: FilledButton.icon(
                    onPressed: _savePlanner,
                    icon: const Icon(Icons.save_outlined),
                    label: const Text('Salvar'),
                  ),
                ),
              ],
            ),
          ],
        ),
        const SizedBox(height: 12),
        if (defaultTargetPlatform == TargetPlatform.android)
          _SettingsCard(
            title: 'Notificações',
            icon: Icons.notifications_none_rounded,
            children: [
              ListTile(
                contentPadding: EdgeInsets.zero,
                leading: const Icon(Icons.notifications_active_outlined),
                title: const Text('Permissão de notificações'),
                subtitle: const Text(
                  'Necessária para lembretes e o cronômetro de foco em background.',
                ),
                trailing: const Icon(Icons.chevron_right_rounded),
                onTap: () async {
                  await NativeBridge.requestNotificationPermission();
                  if (context.mounted) {
                    ScaffoldMessenger.of(context).showSnackBar(
                      const SnackBar(
                        content: Text('Solicitação de permissão enviada.'),
                        behavior: SnackBarBehavior.floating,
                      ),
                    );
                  }
                },
              ),
            ],
          )
        else
          const _SettingsCard(
            title: 'Notificações',
            icon: Icons.notifications_none_rounded,
            children: [
              ListTile(
                contentPadding: EdgeInsets.zero,
                leading: Icon(Icons.desktop_windows_outlined),
                title: Text('Lembretes no Windows'),
                subtitle: Text(
                  'A versão portátil mantém os dados e recursos de produtividade, mas os lembretes nativos ainda são exclusivos do Android.',
                ),
              ),
            ],
          ),
        const SizedBox(height: 12),
        _SettingsCard(
          title: 'Sobre',
          icon: Icons.info_outline_rounded,
          children: [
            ListTile(
              contentPadding: EdgeInsets.zero,
              leading: const Icon(Icons.flutter_dash_rounded),
              title: const Text('Ritmo 3.4.2'),
              subtitle: const Text(
                'Android + Windows · Inbox, busca global, timeline e Smart Planner 2.0 · dados locais.',
              ),
            ),
            const Divider(),
            ListTile(
              contentPadding: EdgeInsets.zero,
              leading: const Icon(Icons.verified_user_outlined),
              title: const Text('Dados preservados'),
              subtitle: const Text(
                'No Android, o app mantém o mesmo package e lê o armazenamento da versão nativa 2.4.',
              ),
            ),
          ],
        ),
      ],
    );
  }
}

class _SettingsCard extends StatelessWidget {
  final String title;
  final IconData icon;
  final List<Widget> children;
  const _SettingsCard({
    required this.title,
    required this.icon,
    required this.children,
  });

  @override
  Widget build(BuildContext context) => Card(
        child: Padding(
          padding: const EdgeInsets.fromLTRB(16, 14, 16, 16),
          child: Column(
            crossAxisAlignment: CrossAxisAlignment.stretch,
            children: [
              Row(
                children: [
                  Icon(icon, size: 19, color: Theme.of(context).colorScheme.primary),
                  const SizedBox(width: 8),
                  Text(title, style: Theme.of(context).textTheme.titleMedium),
                ],
              ),
              const SizedBox(height: 14),
              ...children,
            ],
          ),
        ),
      );
}
