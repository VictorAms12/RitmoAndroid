# Ritmo 2.1.0 — Android nativo

Ritmo é um app Android de rotina e produtividade feito em Java nativo, offline-first e sem WebView.

## Destaques da 2.1.0

- Exclusão visível dentro do editor de tarefa, meta, hábito e projeto.
- Editor de tarefa mais compacto, com campos em duas colunas quando há espaço.
- Pesquisa de tarefas por título, descrição, categoria ou projeto.
- Filtros por estado e categoria.
- Projetos completos com prazo, descrição, progresso e tarefas vinculadas.
- Subtarefas com marcação individual.
- Prioridade **Automática**, calculada pelo prazo da tarefa.
- Kanban com arrastar e soltar entre A fazer, Em andamento e Concluído.
- Planejamento semanal com arrastar tarefas de um dia para outro.
- Visão semanal redesenhada para leitura rápida da carga de cada dia.
- Estatísticas mais úteis: taxa de conclusão, atrasadas, melhor dia e tempo concluído.
- Notificações com ações rápidas: **Concluir** e **Adiar 10 min**.
- Widget de tela inicial com eficiência e pendências do dia.
- Top bar adaptativa: título/subtítulo mudam conforme a tela.
- Integração visual das barras de status/navegação com tema claro/escuro.
- Persistência offline e migração dos dados das versões 2.0.x.

## Versão Android

- `versionCode 4`
- `versionName 2.1.0`
- `minSdk 24`
- `targetSdk 35`
- Java 17

## Build na nuvem

O workflow `.github/workflows/build-apk.yml` gera um APK release assinado sempre que houver `push` na `main` ou uma tag `v*`.

Artifact esperado:

`Ritmo-v2.1.0-APK`

Arquivo:

`Ritmo-v2.1.0.apk`

## Widget

Após instalar a 2.1.0, abra o seletor de widgets do launcher Android e procure por **Ritmo**. O widget exibe a eficiência do dia, tarefas concluídas e pendentes.

## Estrutura principal

- `MainActivity.java` — interface, navegação e ações.
- `Store.java` — persistência, projetos, subtarefas e métricas.
- `ReminderReceiver.java` — notificações e ações rápidas.
- `ReminderScheduler.java` — agendamento de lembretes.
- `RitmoWidgetProvider.java` — widget da tela inicial.
- `WeeklyBarChart.java` — gráfico semanal nativo.

## Assinatura

A pasta `signing/` contém a chave usada nas builds 2.x. Preserve essa chave para que o Android aceite versões futuras como atualização do mesmo app. Mantenha o repositório privado e não publique a chave.
