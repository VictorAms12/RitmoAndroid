# Ritmo 2.0.0 — Android nativo

App de rotina e produtividade feito em Java/Android nativo. Não usa WebView e funciona offline.

## Recursos da v2

- Dashboard Hoje com eficiência e resumo de carga.
- Tarefas: criar, editar, concluir, filtrar, excluir e categorizar.
- Descrição, prioridade, duração, recorrência e lembretes.
- Calendário mensal interativo.
- Planejamento semanal.
- Agenda diária.
- Kanban A fazer → Em andamento → Concluído.
- Metas com prazo e progresso.
- Hábitos com frequência e streak.
- Estatísticas dos últimos 7 dias e distribuição por categoria.
- Tema claro/escuro.
- Barras de sistema integradas ao tema.
- Interface adaptada a celulares e tablets.
- Armazenamento local com migração dos dados da v1.

## Build na nuvem

O workflow `.github/workflows/build-apk.yml` gera um APK **release assinado**. Ao enviar alterações para `main`, a build inicia automaticamente.

Resultado esperado em **Actions → Artifacts**:

`Ritmo-v2.0.0-APK` → `Ritmo-v2.0.0.apk`

## Atualizações futuras

Preserve `signing/ritmo.keystore`. Ela é a identidade criptográfica do aplicativo e permite que o Android aceite APKs futuros como atualização do mesmo Ritmo.

Consulte `VERSIONING.md` e `UPDATE_FROM_TERMUX.md`.
