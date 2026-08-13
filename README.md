# Ritmo 3.0.0 — Flutter Edition

Ritmo foi reconstruído em Flutter para melhorar fluidez, animações, consistência visual e manutenção sem abandonar os recursos existentes da versão Android nativa.

## Destaques

- Material 3 com identidade verde profunda
- Light / Dark / System
- Dashboard diário com progresso combinado
- Tarefas, subtarefas, recorrência, lembretes, prioridade automática e projetos
- Hábitos com frequência, dias customizados, lembrete e streak
- Calendário mensal
- Swipe para concluir/excluir
- Kanban com drag-and-drop
- Planejador inteligente de tarefas flexíveis
- Metas e projetos
- Modo Foco com cronômetro em background no Android
- Estatísticas semanais, heatmap de 30 dias e histórico de foco
- Fechamento do dia
- Empty/loading/error states
- Feedback tátil opcional
- Redução de movimento
- Widget Android preservado
- Migração direta dos dados do Ritmo 2.4 no Android

## Compatibilidade com a 2.4

O applicationId continua:

`com.ritmo.mobile`

A mesma chave `signing/ritmo.keystore` também foi preservada. Por isso, o APK 3.0.0 foi preparado para instalar como atualização da 2.4.0.

Os dados continuam no SharedPreferences nativo:

- arquivo: `ritmo_prefs`
- chave: `ritmo_data`

O Flutter lê e salva no mesmo local através de um MethodChannel. Isso evita perder tarefas, projetos, metas, hábitos, histórico e revisões do dia na migração.

## Build no GitHub Actions

Ao fazer push na `main`, o workflow `.github/workflows/build-apk.yml`:

1. instala Java 17;
2. prepara Android SDK API 35;
3. instala Flutter 3.44.4;
4. executa `flutter pub get`;
5. executa `flutter analyze`;
6. gera APK release assinado;
7. publica `Ritmo-v3.0.0-APK`.

## Atualização no Windows

Use esta versão como uma substituição estrutural do repositório, não apenas da pasta `app/`, porque o projeto agora é Flutter.

Faça backup/commit da 2.4 antes da troca.

Depois de copiar o conteúdo da v3 para o repositório:

```bash
git status
git add -A
git commit -m "feat: Ritmo 3.0.0 migracao para Flutter"
git push origin main
```

Se o Actions ficar verde, baixe o artifact `Ritmo-v3.0.0-APK`.

## Versão

- versionName: 3.0.0
- versionCode: 8
- schemaVersion: 7
