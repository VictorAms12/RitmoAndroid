# Ritmo 2.3.0 — Android nativo

Ritmo é um aplicativo Android offline-first para rotina, tarefas, hábitos, agenda, planejamento e produtividade. A versão 2.3.0 mantém a base funcional da 2.2 e faz uma evolução ampla de experiência, foco, estatísticas e acessibilidade.

## Destaques da 2.3.0

### Redesign e Design System
- Paleta Light com fundo `#F8F9FA`, cards brancos e texto `#111827`.
- Paleta Dark baseada em `#0F172A` / `#1E293B`, sem preto absoluto como fundo principal.
- Índigo/violeta como ação primária, menta para sucesso e âmbar para alertas/foco.
- Cards com raio de aproximadamente 16dp, botões de 12dp e espaçamento baseado em grid de 8dp.
- Navegação inferior redesenhada para **Hoje / Calendário / Progresso / Ajustes**, com FAB central para criação rápida.
- Cabeçalho contextual com cumprimento, data, versão e atalho de tema.
- Integração edge-to-edge com barras de status/navegação e contraste de ícones conforme o tema.

### Home / Hoje
- Novo resumo diário com anel de progresso combinado de tarefas + hábitos.
- Streak e minutos de foco visíveis no resumo.
- Feed do dia dividido em **Manhã / Tarde / Noite**.
- Empty state amigável quando não há rotina no dia.
- Card para replanejar pendências flexíveis atrasadas.
- Atalho de modo Foco e resumo de hábitos/metas.

### Modo Foco / Pomodoro
- Nova `FocusActivity` dedicada.
- Sessões de 25 min, 50 min ou duração da tarefa.
- Pausar, continuar e finalizar sessão.
- Opção de concluir a tarefa ao terminar o foco.
- Sessão ativa persistida para retomada após sair e voltar ao app.
- Registro do tempo realmente focado para as estatísticas.

### Hábitos
- Horário, categoria, cor e lembrete por hábito.
- Frequências diária, dias úteis, semanal ou **dias específicos da semana**.
- Lembretes locais próprios para hábitos, com reagendamento após reiniciar o Android.
- Feedback tátil/visual na conclusão e cálculo de sequência.

### Progresso e estatísticas
- Taxa de conclusão semanal.
- Eficiência de execução real (tempo focado x planejado).
- Consistência de 30 dias em heatmap.
- Gráfico de sessões de foco.
- Gráfico semanal de produtividade e distribuição por categoria.
- Indicadores de atrasos, melhor dia e média de consistência.

### Configurações e acessibilidade
- Nome de exibição opcional.
- Tema **Sistema / Claro / Escuro**.
- Opção **Reduzir animações**.
- Opção para desativar feedback tátil.
- Replanejamento automático opcional de tarefas flexíveis atrasadas.
- Ação para reagendar lembretes de tarefas e hábitos.

### Fluidez e resiliência
- Skeleton animado na inicialização em vez de spinner.
- Transições curtas de fade/slide entre áreas.
- Estados `pressed` com redução sutil de escala.
- Conclusão de tarefas/hábitos com atualização visual imediata (optimistic UI), haptic e animação.
- Tela de recuperação em caso de exceção crítica.
- Backup do JSON bruto em caso de armazenamento local corrompido antes da recuperação.

### Mantido da 2.2
- Planejador inteligente de tarefas flexíveis.
- Capacidade produtiva e janela de disponibilidade.
- Preview e desfazer planejamento.
- Agenda mensal/semanal.
- Kanban drag-and-drop.
- Projetos, metas, subtarefas, pesquisa e filtros.
- Prioridade automática e notificações com ações rápidas.
- Widget da tela inicial, agora com streak e foco.

## Versão Android

- `versionCode 6`
- `versionName 2.3.0`
- `minSdk 24`
- `targetSdk 35`
- Java 17
- Schema local: v5

## Build na nuvem

O workflow `.github/workflows/build-apk.yml` gera um APK release assinado em `push` para `main/master`, execução manual ou tag `v*`.

Artifact esperado: `Ritmo-v2.3.0-APK`  
Arquivo esperado: `Ritmo-v2.3.0.apk`

## Estrutura principal

- `MainActivity.java` — shell, navegação, dashboard, agenda, CRUD e configurações.
- `FocusActivity.java` — modo foco/Pomodoro e persistência da sessão ativa.
- `Store.java` — persistência, modelos, migração e métricas.
- `SmartPlanner.java` — planejamento local de tarefas flexíveis.
- `ReminderScheduler.java` / `RoutineReminderScheduler.java` — alarmes locais.
- `ReminderReceiver.java` / `BootReceiver.java` — notificações, ações e restauração após boot.
- `ProgressRingView.java` — anel de progresso nativo.
- `MonthlyHeatmapView.java` — consistência de 30 dias.
- `WeeklyBarChart.java` — gráficos semanais/foco.
- `RitmoWidgetProvider.java` — widget da tela inicial.

## Privacidade

Tarefas, hábitos, sessões de foco, agenda e planejamento permanecem locais no aparelho. O app não depende de conta ou backend.

## Assinatura

A pasta `signing/` contém a chave usada nas builds 2.x. Preserve essa chave para que o Android aceite futuras versões como atualização do mesmo aplicativo. Mantenha o repositório privado e não publique a chave ou suas credenciais.
