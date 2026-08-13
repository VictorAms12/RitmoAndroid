# Changelog

## 3.0.0

### Arquitetura
- Reescrita da interface em Flutter.
- Mantido `com.ritmo.mobile`.
- Mantida a assinatura Android da série 2.x.
- Camada nativa Android preservada apenas onde agrega valor: lembretes, widget, migração de dados e timer foreground.

### UI/UX
- Material 3.
- Novo design system verde profundo.
- Nova barra inferior com FAB central.
- Novo menu `+` em bottom sheet.
- Animações de conclusão, progresso e navegação.
- Swipe para concluir/excluir.
- Empty states, skeleton loading e error recovery.
- Layouts adaptativos e melhor thumb-zone.

### Rotina
- Dashboard Hoje redesenhado.
- Hábitos integrados ao progresso do dia.
- Streak destacado.
- Fechamento do dia.
- Filtros rápidos.

### Planejamento
- Calendário mensal.
- Kanban drag-and-drop.
- Planejador inteligente em Dart.
- Preview e desfazer planejamento.
- Tarefas fixas e flexíveis preservadas.

### Foco
- Tela de foco reconstruída.
- 25 min, 50 min e duração da tarefa.
- Pausar, retomar e registrar.
- Foreground service Android mantido.
- Histórico e gráficos de foco.

### Dados
- Leitura direta dos dados da 2.4.
- schemaVersion 7.
- Backup Flutter adicional via shared_preferences.
