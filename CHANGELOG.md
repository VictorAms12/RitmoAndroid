# Changelog

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
