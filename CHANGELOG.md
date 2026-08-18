# Changelog

## 3.4.1 — Stability & Performance Polish

### Correções
- Hábitos respeitam corretamente a data de início no Flutter e no Android nativo.
- Tarefas da Inbox não entram em métricas diárias, Kanban, atraso ou alarmes.
- Alarmes antigos não exibem notificações de tarefas já concluídas ou movidas para a Inbox.
- Recorrência mensal nativa foi alinhada à regra do Flutter para fins de mês.
- Sessões de foco concluídas em background são recuperadas e registradas ao retornar ao app.
- Subtarefas preservam identidade e estado ao remover/reordenar itens e agora podem ser marcadas no editor.
- Lembretes exigem horário e hábitos personalizados exigem pelo menos um dia.
- Estado vazio da tela Hoje e próxima ação da Timeline foram corrigidos.

### Desempenho
- Persistência serializada para evitar escritas concorrentes.
- Sincronização de lembretes passou a ser direcionada para tarefas/hábitos alterados.
- Smart Planner indexa histórico de foco em uma única passagem.
- Timer de foco Flutter atualiza uma vez por segundo e pausa o ticker quando necessário.
- Foreground service Android deixa o cronômetro do sistema atualizar a notificação sem reconstruí-la a cada segundo.
- Animações decorativas desnecessárias foram reduzidas.

### Design
- Novo ícone do Ritmo: base grafite, anel índigo e pulso branco.
- Ícone adaptativo no Android e ICO multirresolução no Windows.

### Qualidade
- Testes unitários adicionados para modelos, recorrências, Inbox e Smart Planner.
- Builds Android e Windows passam a executar `flutter test` antes do release.

## 3.4.0

### Android
- Corrigida a persistência ao concluir tarefas pela notificação.
- A camada nativa agora preserva `inbox`, `energy` e `preferredPeriod`.
- `Store.java` alinhado ao schemaVersion 8.
- Ações `Concluir` e `Adiar 10 min` mantidas sem degradar os dados do Smart Planner.

### Windows
- Adicionado runner Flutter para Windows.
- Interface principal adaptada para telas largas com Navigation Rail.
- Adicionado build release automatizado no GitHub Actions.
- Artifact portátil em ZIP com executável e dependências.

### Documentação
- README refeito com visão geral, funcionalidades, arquitetura, plataformas, builds e roadmap.

## 3.0.1

### Visual
- Tema claro reconstruído em branco, cinza frio e superfícies neutras.
- Tema escuro reconstruído em preto, grafite e cinza, removendo o verde do fundo e dos cards.
- Índigo/azul passa a ser a identidade principal das ações.
- Verde fica reservado principalmente para sucesso e conclusão.
- Contraste revisado em cards, inputs, chips, botões, diálogos, snackbars e barra inferior.
- Widget, splash, barras do Android e ícone atualizados para a nova identidade.

### Build
- versionName 3.0.1
- versionCode 9

## 3.0.0

- Migração da interface para Flutter com Material 3.
- Dashboard, agenda, progresso, ajustes, hábitos, metas, projetos, Kanban, foco e planejador inteligente.
- Persistência/migração dos dados da linha 2.x e integrações Android nativas.
