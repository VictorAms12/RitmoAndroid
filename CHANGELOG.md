# Changelog

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
