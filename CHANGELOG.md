# Changelog

## 2.3.0 — 2026-08-12

### Redesign completo
- Novo Design System dual-mode com paleta Light `#F8F9FA` e Dark `#0F172A`.
- Índigo/violeta para ações primárias, menta para sucesso e âmbar para atenção/foco.
- Navegação inferior redesenhada para Hoje, Calendário, Progresso e Ajustes, com FAB central.
- Cabeçalho contextual, melhor thumb-zone, cards/controles compactos e barras do sistema adaptativas.
- Transições entre páginas, feedback `pressed`, haptics opcionais e redução de movimento.
- Skeleton animado de inicialização e empty states mais informativos.

### Home
- Novo anel de progresso combinado de tarefas e hábitos.
- Streak e foco diário no resumo.
- Feed organizado em Manhã, Tarde e Noite.
- Card de replanejamento para tarefas flexíveis atrasadas.

### Foco / Pomodoro
- Nova tela dedicada de foco.
- Sessões de 25 min, 50 min ou duração da tarefa.
- Pausar, retomar e finalizar.
- Persistência da sessão ativa.
- Registro do tempo executado e opção de concluir a tarefa ao finalizar.

### Hábitos
- Horário, categoria, cor e lembrete.
- Frequência por dias específicos da semana.
- Alarmes locais próprios para hábitos e restauração após reinicialização.
- Conclusão com optimistic UI, feedback tátil e animação.

### Estatísticas
- Eficiência de execução real.
- Consistência de 30 dias em heatmap.
- Estatísticas e gráfico de foco.
- Melhor dia, atrasos, tempo focado e distribuição por categoria.

### Configurações e resiliência
- Tema Sistema / Claro / Escuro.
- Nome de exibição opcional.
- Reduzir animações e ativar/desativar haptics.
- Replanejamento automático opcional de tarefas flexíveis atrasadas.
- Backup do JSON bruto quando os dados locais estiverem corrompidos.
- Schema local atualizado para v5.

## 2.2.0 — 2026-08-12

### Planejamento inteligente
- Nova aba **Planejador** em Organização.
- Distribuição automática das tarefas flexíveis pelos próximos sete dias.
- Algoritmo considera prioridade, prazo, duração e carga diária.
- Procura horários livres dentro da janela configurada.
- Hábitos pendentes passam a contar na carga produtiva do dia.
- Compromissos fixos e tarefas recorrentes não são movidos automaticamente.
- Prévia da redistribuição antes de aplicar.
- Desfazer último planejamento com restauração de data e horário.

### Disponibilidade
- Capacidade produtiva diária configurável de 2h a 10h.
- Janela de horário inicial/final configurável.
- Opção de incluir fins de semana ou trabalhar apenas com dias úteis.
- Visão da carga dos próximos sete dias com indicadores de sobrecarga.

### Tarefas
- Novo campo `deadline` / Prazo.
- Novo estado `flexible` para diferenciar tarefas movíveis de compromissos fixos.
- Prioridade automática passa a usar o prazo em vez de apenas a data planejada.
- Indicador visual de tarefa flexível nos cards e na agenda.
- Alternância rápido Fixo/Flexível no menu de ações.
- Tarefas recorrentes são protegidas contra redistribuição automática.

### Home
- Novo resumo de planejamento inteligente.
- Indicador de tarefas flexíveis, capacidade diária e dias sobrecarregados.

### Dados
- Schema local atualizado para v4.
- Migração automática: tarefas das versões anteriores continuam fixas por segurança e recebem o prazo igual à data atual da tarefa.

## 2.1.0
- Pesquisa, subtarefas, projetos, prioridade automática, Kanban drag-and-drop, agenda semanal, ações de notificação, widget e estatísticas avançadas.

## 2.0.1
- Correções de estabilidade de inicialização e modo de recuperação.

## 2.0.0
- Calendário mensal, agenda semanal, recorrência, lembretes, hábitos, metas e estatísticas iniciais.
