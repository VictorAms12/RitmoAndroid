# Changelog

## 2.4.0 — 2026-08-12

### Design
- Retorno da identidade escura em verde profundo.
- Nova paleta de superfícies, bordas, cards, hero, navegação e widget.
- Tema escuro vira padrão apenas para novas instalações; preferências existentes são preservadas.
- Menu `+` substituído por um painel de criação rápida mais visual e orientado à thumb-zone.

### Execução diária
- Novo fechamento do dia com humor, nota e resumo real de execução.
- Novo fluxo “Planejar amanhã”.
- Recuperação opcional de tarefas flexíveis não concluídas.
- Registro persistente de revisões diárias.

### Foco
- Novo foreground service para manter cronômetro ativo em background.
- Notificação persistente com contagem regressiva.
- Notificação de conclusão quando o timer termina fora do app.
- Histórico recente de sessões dentro das estatísticas.

### Eficiência
- Replanejamento automático limitado a uma execução diária.
- Reagendamento global de lembretes reduzido para uma vez por dia/versão.
- Operações individuais continuam reagendando somente o item alterado.

### Resiliência
- Diagnóstico de crash agora pode ser copiado.
- Reset local exige confirmação explícita.
- Schema local atualizado para 6.

### Versão
- `versionCode 7`
- `versionName 2.4.0`
