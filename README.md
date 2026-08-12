# Ritmo 2.0.1

Build de correção de estabilidade da v2.0.0.

# Ritmo — app Android nativo

Ritmo é um aplicativo Android de rotina e produtividade feito em Java nativo, sem WebView e sem depender de servidor para funcionar.

## O que já está implementado

- Dashboard **Hoje** com eficiência diária
- Tarefas com data, horário, prioridade, categoria e duração
- Marcar tarefa como concluída e reabrir
- Filtros: todas, hoje, pendentes e concluídas
- Agenda diária
- Kanban: **A fazer → Em andamento → Concluído**
- Metas com progresso
- Rotinas recorrentes
- Insights de carga de trabalho e organização
- Cadastro de tarefa, compromisso, meta e rotina
- Exclusão por toque longo
- Armazenamento 100% local usando SharedPreferences + JSON
- Sem login obrigatório
- Sem internet para o uso normal

## Estrutura

- `app/src/main/java/com/ritmo/mobile/MainActivity.java` — interface e regras do app
- `app/src/main/java/com/ritmo/mobile/Store.java` — persistência local
- `.github/workflows/build-apk.yml` — compilação automática do APK na nuvem

## Gerar o APK sem computador

O projeto já contém um workflow do GitHub Actions. Portanto, o APK pode ser compilado na nuvem:

1. Crie um repositório no GitHub.
2. Envie o conteúdo deste projeto para a raiz do repositório.
3. Vá em **Actions → Build Ritmo APK → Run workflow**.
4. Ao finalizar, abra a execução.
5. Em **Artifacts**, baixe `Ritmo-APK`.
6. Extraia o ZIP no celular e instale `app-debug.apk`.

O APK gerado é uma build de debug, adequada para uso pessoal e testes.
