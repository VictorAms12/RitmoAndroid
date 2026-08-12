# Ritmo 2.2.0 — Android nativo

Ritmo é um app Android de rotina e produtividade feito em Java nativo, offline-first e sem WebView.

## Destaques da 2.2.0

A 2.2.0 introduz o **Planejamento Inteligente local**. O app agora consegue distribuir tarefas flexíveis pelos próximos sete dias levando em conta prazo, prioridade, duração, carga já existente, hábitos e horários disponíveis.

### Planejador inteligente
- Nova aba **Organizar → Planejador**.
- Configuração de capacidade produtiva diária.
- Configuração do horário disponível para execução de tarefas.
- Opção de planejar de segunda a domingo ou somente dias úteis.
- Indicador de carga para cada um dos próximos sete dias.
- Estados visuais: Leve, Equilibrado, Cheio e Sobrecarga.
- Distribuição automática de tarefas flexíveis.
- Busca do primeiro horário livre dentro da janela configurada.
- Prioridade e prazo usados para ordenar o que deve ser encaixado primeiro.
- Hábitos pendentes contam na capacidade diária.
- Tarefas recorrentes e compromissos fixos não são movidos automaticamente.
- Prévia antes de aplicar a reorganização.
- **Desfazer último planejamento** restaura dia e horário anteriores.

### Tarefas flexíveis
- Novo campo **Prazo** separado da data planejada.
- Novo campo **Planejamento**: Flexível ou Fixo.
- Tarefas flexíveis exibem indicador próprio nos cards e na agenda.
- Atalho no menu `⋮` para alternar rapidamente entre tarefa fixa e flexível.
- Prioridade automática agora considera o prazo real da tarefa.
- Tarefas recorrentes permanecem fixas para preservar a repetição.

### Home
- Novo cartão de planejamento mostra tarefas flexíveis, capacidade diária e dias sobrecarregados.
- Atalho direto para organizar a semana.

## Recursos herdados da 2.1

- Tarefas, pesquisa, filtros e subtarefas.
- Projetos e metas.
- Hábitos e sequências.
- Kanban com drag-and-drop.
- Agenda mensal e semanal.
- Reagendamento manual por arrastar.
- Prioridade automática.
- Notificações com Concluir e Adiar 10 min.
- Widget da tela inicial.
- Estatísticas semanais.
- Tema claro/escuro e integração com barras do sistema.

## Versão Android

- `versionCode 5`
- `versionName 2.2.0`
- `minSdk 24`
- `targetSdk 35`
- Java 17
- Schema local: v4

## Build na nuvem

O workflow `.github/workflows/build-apk.yml` gera um APK release assinado sempre que houver `push` na `main` ou uma tag `v*`.

Artifact esperado:

`Ritmo-v2.2.0-APK`

Arquivo:

`Ritmo-v2.2.0.apk`

## Estrutura principal

- `MainActivity.java` — interface, navegação, formulários e ações.
- `Store.java` — persistência, modelos e métricas.
- `SmartPlanner.java` — algoritmo local de distribuição e rollback do planejamento.
- `ReminderReceiver.java` — notificações e ações rápidas.
- `ReminderScheduler.java` — agendamento de lembretes.
- `RitmoWidgetProvider.java` — widget da tela inicial.
- `WeeklyBarChart.java` — gráfico semanal nativo.

## Privacidade

O Planejador Inteligente roda totalmente no aparelho. Nenhuma tarefa, horário ou rotina é enviada para serviços externos.

## Assinatura

A pasta `signing/` contém a chave usada nas builds 2.x. Preserve essa chave para que o Android aceite versões futuras como atualização do mesmo app. Mantenha o repositório privado e não publique a chave.
