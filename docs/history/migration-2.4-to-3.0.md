# Migração 2.4 → 3.0 Flutter

Este documento registra a migração histórica do Ritmo Android nativo 2.4 para a base Flutter 3.0.

A versão 3.0 manteve o mesmo package Android e a mesma assinatura utilizada pela 2.4 para preservar a possibilidade de atualização sobre instalações existentes.

A ponte nativa fica em:

`android/app/src/main/java/com/ritmo/mobile/MainActivity.java`

Ela lê o JSON legado diretamente de:

`SharedPreferences("ritmo_prefs") / "ritmo_data"`

O formato dos modelos foi mantido compatível com a camada nativa, incluindo:

- tasks;
- goals;
- routines;
- completions;
- projects;
- focusSessions;
- dayReviews.

Depois que o Flutter altera os dados, eles continuam sendo persistidos na mesma base local. Isso permite que recursos nativos, como notificações, widget e serviços Android, compartilhem o mesmo estado da aplicação.

> Documento histórico. Não representa um procedimento de instalação ou atualização atual da versão 3.4+.
