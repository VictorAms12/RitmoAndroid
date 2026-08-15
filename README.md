# Ritmo

> **Planejamento pessoal, tarefas, hábitos e foco em um único aplicativo.**

O **Ritmo** é um aplicativo de produtividade desenvolvido em **Flutter** para organizar tarefas, compromissos, hábitos, metas, projetos e sessões de foco. O projeto segue uma abordagem **offline-first**, com processamento local e um **Smart Planner** capaz de redistribuir tarefas flexíveis de acordo com prazo, prioridade, duração, energia, horários disponíveis e carga diária.

A partir da versão **3.4.0**, o mesmo projeto possui builds oficiais para **Android** e **Windows**.

---

## Visão geral

O Ritmo foi criado para reduzir o trabalho de manter uma rotina organizada. Em vez de funcionar apenas como uma lista de tarefas, o aplicativo conecta planejamento, execução e acompanhamento de progresso.

**capturar → organizar → planejar → executar → revisar**

- **Capturar:** registre rapidamente algo na Caixa de Entrada.
- **Organizar:** defina prazo, projeto, duração, prioridade e contexto.
- **Planejar:** use o Smart Planner 2.0 para distribuir tarefas flexíveis.
- **Executar:** acompanhe o dia, hábitos e sessões de foco.
- **Revisar:** consulte progresso, histórico e fechamento diário.

---

## Plataformas

| Recurso | Android | Windows |
|---|:---:|:---:|
| Tarefas, projetos e metas | ✅ | ✅ |
| Hábitos e recorrências | ✅ | ✅ |
| Inbox / Caixa de Entrada | ✅ | ✅ |
| Smart Planner 2.0 | ✅ | ✅ |
| Agenda e Kanban | ✅ | ✅ |
| Estatísticas e histórico | ✅ | ✅ |
| Modo Foco | ✅ | ✅ |
| Tema claro / escuro / sistema | ✅ | ✅ |
| Lembretes nativos | ✅ | — |
| Ações **Concluir** e **Adiar 10 min** | ✅ | — |
| Widget | ✅ | — |
| Timer de foco em background | ✅ | — |

> No Windows, os dados são locais e independentes. A sincronização automática Android ↔ Windows ainda não faz parte desta versão.

---

## Funcionalidades

### Hoje
- resumo do dia e progresso combinado;
- tarefas agrupadas por período;
- hábitos do dia;
- tempo planejado e minutos de foco;
- tarefas atrasadas;
- fechamento diário;
- acesso à Central.

### Caixa de Entrada
A **Inbox** registra tarefas rapidamente sem exigir data, horário ou projeto. Depois, cada item pode ser enviado para hoje, amanhã ou aberto no editor completo.

### Smart Planner 2.0
O planejador considera:

- prioridade;
- prazo;
- duração prevista;
- histórico de foco;
- energia necessária;
- período preferido;
- capacidade diária;
- compromissos fixos;
- hábitos com horário;
- uso opcional de fins de semana.

Antes de aplicar mudanças, o Ritmo mostra uma **prévia** e explica o motivo de cada sugestão. É possível aceitar somente as mudanças desejadas e desfazer o último planejamento.

### Organização
- calendário mensal;
- timeline diária;
- Kanban com drag-and-drop;
- projetos e metas;
- subtarefas;
- recorrências;
- prioridade automática;
- busca global.

### Foco e progresso
- sessões Pomodoro e personalizadas;
- histórico de foco;
- estatísticas semanais;
- heatmap de consistência;
- distribuição por categoria;
- streak de hábitos;
- comparação entre planejamento e execução.

---

## Notificações Android

As notificações de tarefas possuem duas ações rápidas:

- **Concluir** — marca a tarefa como concluída, registra a conclusão e atualiza os dados locais;
- **Adiar 10 min** — agenda um novo lembrete sem alterar o horário original da tarefa.

Na versão **3.4.0**, a camada nativa Android foi alinhada ao **schema 8**. As ações da notificação passam a preservar os campos do Smart Planner: `inbox`, `energy` e `preferredPeriod`.

O Android pode flexibilizar alarmes em modo de economia de bateria, então o adiamento não deve ser tratado como um despertador de precisão absoluta.

---

## Design

O Ritmo utiliza **Material 3** e um design system próprio.

### Tema escuro
- preto e grafite como base;
- superfícies em diferentes níveis de cinza;
- índigo como cor principal;
- cores semânticas apenas para estado e atenção.

### Tema claro
- branco e cinza frio;
- contraste elevado para métricas;
- índigo como identidade visual principal.

Em telas largas, o app troca automaticamente a navegação inferior por uma **Navigation Rail lateral**, deixando a versão de PC mais adequada para notebook e desktop.

---

## Tecnologias

| Tecnologia | Aplicação |
|---|---|
| **Flutter / Dart** | UI e regras de negócio multiplataforma |
| **Material 3** | componentes e design system |
| **Shared Preferences** | persistência local |
| **Java / Android SDK** | notificações, alarmes, widget e timer em background |
| **MethodChannel** | comunicação Flutter ↔ Android |
| **GitHub Actions** | CI e builds Android/Windows |
| **Gradle** | build Android |
| **CMake / Visual Studio Build Tools** | build Windows |

---

## Estrutura do projeto

```text
Ritmo
├── lib/
│   ├── core/          # estado, tema e bridge nativa
│   ├── models/        # modelos e serialização
│   ├── screens/       # telas principais
│   ├── services/      # Smart Planner e regras
│   ├── sheets/        # editores
│   └── widgets/       # componentes reutilizáveis
├── android/
│   └── app/src/main/
│       ├── java/      # notificações, alarmes, widget e serviços
│       └── res/       # recursos Android
├── windows/           # runner Flutter para Windows
└── .github/workflows/
    ├── build-apk.yml
    └── build-windows.yml
```

A maior parte da aplicação é compartilhada. Recursos dependentes do sistema operacional ficam isolados na camada nativa.

---

## Dados e compatibilidade

### Android

```text
package: com.ritmo.mobile
SharedPreferences: ritmo_prefs
chave: ritmo_data
schemaVersion: 8
```

O package e a assinatura da série 2.x foram preservados para manter compatibilidade com instalações anteriores.

### Windows

O Windows usa a implementação desktop do `shared_preferences`. Os dados ficam no perfil local do usuário e são independentes do Android nesta versão.

---

## Build automatizado

### Android

Workflow:

```text
.github/workflows/build-apk.yml
```

Artifact:

```text
Ritmo-v3.4.0-APK
└── Ritmo-v3.4.0.apk
```

### Windows

Workflow:

```text
.github/workflows/build-windows.yml
```

Artifact:

```text
Ritmo-v3.4.0-Windows
└── Ritmo-v3.4.0-Windows.zip
```

O ZIP é portátil e contém o executável junto das DLLs necessárias. Extraia a pasta completa antes de executar.

---

## Executando localmente

Pré-requisitos:

- Flutter 3.44.4 ou compatível;
- Dart compatível;
- Android SDK para Android;
- Visual Studio com **Desktop development with C++** para Windows.

Dependências:

```bash
flutter pub get
```

Android:

```bash
flutter run
```

Windows:

```bash
flutter config --enable-windows-desktop
flutter run -d windows
```

Build Windows:

```bash
flutter build windows --release
```

---

## Regras de segurança do Smart Planner

O planejador não move automaticamente:

- tarefas concluídas;
- tarefas recorrentes;
- compromissos fixos;
- itens ainda na Inbox.

Somente tarefas marcadas como **flexíveis** participam da distribuição automática.

---

## Roadmap

- sincronização Android ↔ Windows;
- backup e restauração em arquivo;
- notificações nativas no Windows;
- recorrências avançadas;
- dashboard personalizável;
- dependências entre tarefas;
- atalhos de teclado;
- refinamento contínuo de acessibilidade e desempenho.

---

## Versão atual

```text
Ritmo 3.4.0
versionCode: 12
schemaVersion: 8
Flutter: 3.44.4
Android + Windows
```

---

## Autor

Desenvolvido por **Victor Alexandre** como projeto de produtividade pessoal e exploração prática de desenvolvimento multiplataforma, UX, automação e arquitetura offline-first.
