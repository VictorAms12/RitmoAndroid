# 2.0.1

- Corrige inicialização instável em alguns aparelhos.
- Inicialização tolerante a falhas: permissões e reagendamento de lembretes são adiados até a UI abrir.
- Tratamento mais seguro de barras do sistema no Android 15+.
- Sanitização de dados migrados da v1.
- Tela de recuperação em vez de fechar silenciosamente quando ocorre erro durante a montagem da interface.
- Registro local do último crash para diagnóstico.

# Changelog

## 2.0.0 — 2026-08-12

### Interface
- UI refeita para navegação rápida em uma mão.
- Ícones vetoriais nativos no menu principal.
- Tema claro e escuro persistentes.
- Barra de status e barra de navegação integradas ao tema do app.
- Layout centralizado em tablets e adaptado a telas maiores.
- Cards, chips e hierarquia visual mais compactos.

### Agenda e planejamento
- Calendário mensal interativo.
- Planejamento semanal com carga por dia.
- Seleção de dia e criação rápida de tarefa naquele dia.
- Agenda diária integrada às tarefas.

### Tarefas
- Edição completa de tarefas.
- Descrição/observações.
- Recorrência diária, dias úteis, semanal ou mensal.
- Lembretes: na hora, 10 min, 30 min, 1 h ou 1 dia antes.
- Lembretes reaplicados após reiniciar o aparelho.
- Mudança de status pelo Kanban.
- Histórico de conclusão preservado ao limpar concluídas.

### Hábitos e metas
- Hábitos com frequência e duração.
- Marcação diária de hábito.
- Sequência (streak) calculada automaticamente.
- Edição de hábitos.
- Metas com prazo, progresso e edição.

### Estatísticas
- Gráfico nativo dos últimos 7 dias.
- Tempo concluído na semana.
- Distribuição de tempo por categoria.
- Melhor sequência de hábito.
- Insights de carga, prioridades e fila pendente.

### Distribuição
- APK de release assinado com uma chave estável do projeto.
- O mesmo APK pode ser atualizado por versões futuras sem reinstalar, desde que a chave de `signing/ritmo.keystore` seja preservada.
