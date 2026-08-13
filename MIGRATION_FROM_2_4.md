# Migração 2.4 → 3.0 Flutter

A versão 3.0 usa o mesmo package Android e a mesma assinatura da 2.4.

O projeto Flutter inclui uma ponte nativa em:

`android/app/src/main/java/com/ritmo/mobile/MainActivity.java`

Ela lê o JSON da versão antiga diretamente de:

`SharedPreferences("ritmo_prefs") / "ritmo_data"`

O formato dos modelos foi mantido compatível com `Store.java`, incluindo:

- tasks
- goals
- routines
- completions
- projects
- focusSessions
- dayReviews

Depois que o Flutter altera os dados, ele salva novamente no mesmo arquivo. Assim:

- lembretes nativos continuam entendendo os dados;
- o widget Android continua funcionando;
- ações "Concluir" das notificações continuam funcionando;
- o Flutter atualiza ao retornar ao primeiro plano.

Não desinstale a 2.4 antes de instalar a 3.0 se você deseja preservar os dados locais.
