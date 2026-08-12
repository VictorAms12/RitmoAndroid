# Ritmo 2.4.0 — Android nativo

Ritmo é um app offline-first para rotina, tarefas, hábitos, agenda, foco e planejamento pessoal. A 2.4 preserva a base da 2.3, mas melhora execução diária, confiabilidade e identidade visual.

## Destaques da 2.4.0

### Tema verde escuro renovado
- Tema escuro padrão para novas instalações.
- Fundo profundo `#071A14`, sem preto absoluto.
- Cards `#0D261E` e superfícies secundárias `#123328`.
- Verde menta como ação primária e sucesso.
- Tema claro continua disponível com tons neutros e acento verde.
- Barra de status, navegação, hero e widget atualizados para a mesma identidade.

### Menu + redesenhado
- Novo painel inferior de criação rápida.
- Nova tarefa em destaque.
- Atalhos visuais para compromisso, hábito, projeto e meta.
- Atalhos diretos para sessão de foco e planejamento de amanhã.
- Microanimação de entrada e feedback de pressão.

### Foco em background
- Sessões em andamento agora usam `FocusTimerService` como foreground service.
- Cronômetro continua ao bloquear a tela, trocar de app ou sair da Activity.
- Notificação persistente mostra contagem regressiva.
- Ao terminar em background, a notificação muda para “Sessão concluída”.
- Ao voltar ao Ritmo, a sessão é registrada normalmente no histórico.

### Fechamento do dia
- Novo card “Fechamento do dia” na Home.
- Nota de 1 a 5 sobre como foi o dia.
- Campo opcional de reflexão rápida.
- Resumo de tarefas concluídas, pendentes e tempo de foco.
- Opção de levar apenas tarefas flexíveis pendentes para amanhã.
- Compromissos fixos e recorrências nunca são movidos.
- Histórico de revisões diárias salvo localmente.

### Planejar amanhã
- Novo atalho no menu `+` e em Ajustes.
- Mostra o que já está previsto para amanhã.
- Pode recuperar pendências flexíveis de hoje.
- Permite criar uma tarefa diretamente no dia seguinte.
- Abre a Agenda já posicionada em amanhã.

### Histórico de foco
- Estatísticas agora exibem as sessões recentes.
- Cada sessão mostra tarefa, data, horário, modo, tempo real e percentual do alvo.

### Eficiência e estabilidade
- Replanejamento automático passa a rodar no máximo uma vez por dia, evitando reorganizações repetidas ao abrir o app.
- Reagendamento geral de alarmes também é limitado a uma vez por dia/versão; alterações individuais continuam atualizando seus próprios alarmes imediatamente.
- Tela de recuperação ganhou botão para copiar diagnóstico técnico.
- Limpeza de dados em modo de recuperação agora exige confirmação.
- Banco local atualizado para `schemaVersion 6` com migração compatível.

## Recursos preservados
- Tarefas, subtarefas, prioridades, recorrência, lembretes e projetos.
- Calendário mensal e semanal.
- Kanban com drag-and-drop.
- Hábitos com streak, frequência e lembretes.
- Metas e projetos.
- Planejador Inteligente.
- Estatísticas semanais/mensais.
- Heatmap de consistência.
- Widget Android.
- Tema Claro / Escuro / Sistema.
- Modo Foco 25/50/duração da tarefa.

## Versão Android

```gradle
versionCode 7
versionName '2.4.0'
```

## Build automática

O workflow em `.github/workflows/build-apk.yml` gera o APK assinado no GitHub Actions.

Artifact esperado: `Ritmo-v2.4.0-APK`  
Arquivo esperado: `Ritmo-v2.4.0.apk`

## Dados

Os dados continuam no armazenamento local do app. A atualização da 2.3 para a 2.4 mantém tarefas, projetos, metas, hábitos, histórico de conclusão e sessões de foco.

## Observação de build

O projeto foi revisado estruturalmente (Java/XML), mas a compilação Android final deve ser validada pelo GitHub Actions ou pelo Android Studio, pois este ambiente de geração não possui Android SDK instalado.
