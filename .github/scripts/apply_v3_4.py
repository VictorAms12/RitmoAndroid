from pathlib import Path
import re


def read(path: str) -> str:
    return Path(path).read_text(encoding="utf-8")


def write(path: str, text: str) -> None:
    p = Path(path)
    p.parent.mkdir(parents=True, exist_ok=True)
    p.write_text(text, encoding="utf-8", newline="\n")


def replace_once(path: str, old: str, new: str, label: str) -> None:
    text = read(path)
    if new in text:
        print(f"already applied: {label}")
        return
    if old not in text:
        raise SystemExit(f"expected block not found: {label} in {path}")
    write(path, text.replace(old, new, 1))
    print(f"updated: {label}")


# Android notification data safety: preserve schema 8 + Smart Planner metadata
# whenever native notification actions rewrite the shared JSON.
store = "android/app/src/main/java/com/ritmo/mobile/Store.java"
replace_once(
    store,
    '            root.put("schemaVersion", 6);',
    '            root.put("schemaVersion", 8);',
    "native schema version",
)
replace_once(
    store,
    "        public boolean flexible;\n        public List<Subtask> subtasks = new ArrayList<>();",
    "        public boolean flexible, inbox;\n        public String energy, preferredPeriod;\n        public List<Subtask> subtasks = new ArrayList<>();",
    "native Smart Planner fields",
)
replace_once(
    store,
    "            this.flexible = flexible;\n        }",
    "            this.flexible = flexible;\n            this.inbox = false;\n            this.energy = \"medium\";\n            this.preferredPeriod = \"any\";\n        }",
    "native Smart Planner defaults",
)
replace_once(
    store,
    '            o.put("deadline", deadline); o.put("flexible", flexible);',
    '            o.put("deadline", deadline); o.put("flexible", flexible);\n            o.put("inbox", inbox); o.put("energy", energy); o.put("preferredPeriod", preferredPeriod);',
    "native Smart Planner json write",
)
replace_once(
    store,
    '                    o.optString("deadline", date), o.optBoolean("flexible", false));\n            JSONArray a = o.optJSONArray("subtasks");',
    '                    o.optString("deadline", date), o.optBoolean("flexible", false));\n            t.inbox = o.optBoolean("inbox", false);\n            t.energy = o.optString("energy", "medium");\n            t.preferredPeriod = o.optString("preferredPeriod", "any");\n            JSONArray a = o.optJSONArray("subtasks");',
    "native Smart Planner json read",
)
replace_once(
    store,
    "            if (t.subtasks == null) t.subtasks = new ArrayList<>();",
    "            if (t.energy == null || t.energy.trim().isEmpty()) t.energy = \"medium\";\n            if (t.preferredPeriod == null || t.preferredPeriod.trim().isEmpty()) t.preferredPeriod = \"any\";\n            if (t.subtasks == null) t.subtasks = new ArrayList<>();",
    "native Smart Planner sanitize",
)
text = read(store)
text = text.replace('r.accent = "green";', 'r.accent = "indigo";')
write(store, text)

# Desktop-adaptive shell. Wide layouts use NavigationRail; Android/mobile keeps
# the existing bottom navigation + centered FAB.
shell = "lib/screens/shell.dart"
text = read(shell)
start_marker = "    return AnnotatedRegion<SystemUiOverlayStyle>("
end_marker = "\n  }\n}\n\nclass _RitmoBottomBar"
start = text.index(start_marker)
end = text.index(end_marker, start)
replacement = r'''    final pageView = PageView(
      controller: _pages,
      onPageChanged: (value) {
        if (_tabTransitioning || value == _index) return;
        setState(() => _index = value);
      },
      children: pages,
    );
    final width = MediaQuery.sizeOf(context).width;
    final desktop = width >= 900;

    return AnnotatedRegion<SystemUiOverlayStyle>(
      value: overlayStyle,
      child: desktop
          ? Scaffold(
              body: SafeArea(
                child: Row(
                  children: [
                    NavigationRail(
                      selectedIndex: _index,
                      onDestinationSelected: _select,
                      extended: width >= 1180,
                      minWidth: 78,
                      minExtendedWidth: 210,
                      groupAlignment: -.72,
                      leading: Padding(
                        padding: const EdgeInsets.fromLTRB(10, 10, 10, 18),
                        child: FloatingActionButton.small(
                          tooltip: 'Adicionar',
                          onPressed: _showAddMenu,
                          child: const Icon(Icons.add_rounded),
                        ),
                      ),
                      destinations: const [
                        NavigationRailDestination(
                          icon: Icon(Icons.home_outlined),
                          selectedIcon: Icon(Icons.home_rounded),
                          label: Text('Hoje'),
                        ),
                        NavigationRailDestination(
                          icon: Icon(Icons.calendar_month_outlined),
                          selectedIcon: Icon(Icons.calendar_month_rounded),
                          label: Text('Agenda'),
                        ),
                        NavigationRailDestination(
                          icon: Icon(Icons.insights_outlined),
                          selectedIcon: Icon(Icons.insights_rounded),
                          label: Text('Progresso'),
                        ),
                        NavigationRailDestination(
                          icon: Icon(Icons.tune_outlined),
                          selectedIcon: Icon(Icons.tune_rounded),
                          label: Text('Ajustes'),
                        ),
                      ],
                    ),
                    VerticalDivider(
                      width: 1,
                      thickness: 1,
                      color: Theme.of(context).dividerColor,
                    ),
                    Expanded(
                      child: Center(
                        child: ConstrainedBox(
                          constraints: const BoxConstraints(maxWidth: 1500),
                          child: pageView,
                        ),
                      ),
                    ),
                  ],
                ),
              ),
            )
          : Scaffold(
              extendBody: true,
              body: SafeArea(
                top: true,
                bottom: false,
                child: pageView,
              ),
              floatingActionButtonLocation: FloatingActionButtonLocation.centerDocked,
              floatingActionButton: Semantics(
                button: true,
                label: 'Adicionar',
                child: FloatingActionButton(
                  elevation: 7,
                  onPressed: _showAddMenu,
                  child: const Icon(Icons.add_rounded, size: 30),
                ),
              ),
              bottomNavigationBar: _RitmoBottomBar(
                index: _index,
                onSelected: _select,
              ),
            ),
    );'''
text = text[:start] + replacement + text[end:]
write(shell, text)

# Version 3.4.0 / build 12.
pub = read("pubspec.yaml")
pub = re.sub(r"^version:\s*.*$", "version: 3.4.0+12", pub, flags=re.M)
write("pubspec.yaml", pub)

gradle = read("android/app/build.gradle")
gradle = re.sub(
    r'flutterVersionCode = localProperties\.getProperty\("flutter\.versionCode"\) \?: "\d+"',
    'flutterVersionCode = localProperties.getProperty("flutter.versionCode") ?: "12"',
    gradle,
)
gradle = re.sub(
    r'flutterVersionName = localProperties\.getProperty\("flutter\.versionName"\) \?: "[^"]+"',
    'flutterVersionName = localProperties.getProperty("flutter.versionName") ?: "3.4.0"',
    gradle,
)
write("android/app/build.gradle", gradle)

apk = read(".github/workflows/build-apk.yml")
apk = re.sub(r"flutter\.versionCode=\d+", "flutter.versionCode=12", apk)
apk = re.sub(r"flutter\.versionName=[0-9.]+", "flutter.versionName=3.4.0", apk)
apk = re.sub(r"Ritmo-v[0-9.]+\.apk", "Ritmo-v3.4.0.apk", apk)
apk = re.sub(r"Ritmo-v[0-9.]+-APK", "Ritmo-v3.4.0-APK", apk)
write(".github/workflows/build-apk.yml", apk)

settings = read("lib/screens/settings_page.dart")
settings = settings.replace(
    "title: const Text('Ritmo 3.3.0'),",
    "title: const Text('Ritmo 3.4.0'),",
)
settings = settings.replace(
    "'Inbox, busca global, timeline e Smart Planner 2.0 · dados locais · sem conta obrigatória.',",
    "'Android + Windows · Inbox, busca global, timeline e Smart Planner 2.0 · dados locais.',",
)
write("lib/screens/settings_page.dart", settings)

# Windows runner polish.
main_cpp = Path("windows/runner/main.cpp")
if main_cpp.exists():
    w = read(str(main_cpp))
    w = w.replace("Win32Window::Size size(1280, 720);", "Win32Window::Size size(1280, 800);")
    w = w.replace('window.Create(L"ritmo"', 'window.Create(L"Ritmo"')
    write(str(main_cpp), w)

rc = Path("windows/runner/Runner.rc")
if rc.exists():
    w = read(str(rc)).replace('"ritmo"', '"Ritmo"')
    write(str(rc), w)

windows_workflow = r'''name: Build Ritmo Windows

on:
  workflow_dispatch:
  push:
    branches: [ "main", "master" ]
    tags: [ "v*" ]

jobs:
  build-windows:
    runs-on: windows-latest

    steps:
      - name: Checkout
        uses: actions/checkout@v4

      - name: Setup Flutter
        uses: subosito/flutter-action@v2
        with:
          flutter-version: "3.44.4"
          channel: "stable"
          cache: true

      - name: Enable Windows desktop
        run: flutter config --enable-windows-desktop

      - name: Fetch packages
        run: flutter pub get

      - name: Analyze Flutter source
        run: flutter analyze --no-fatal-infos --no-fatal-warnings

      - name: Build Windows release
        run: flutter build windows --release

      - name: Package portable Windows build
        shell: pwsh
        run: |
          $source = "build\windows\x64\runner\Release"
          if (-not (Test-Path $source)) { throw "Windows release folder not found: $source" }
          $dest = "dist\Ritmo-Windows-v3.4.0"
          New-Item -ItemType Directory -Force -Path $dest | Out-Null
          Copy-Item "$source\*" $dest -Recurse -Force
          Compress-Archive -Path "$dest\*" -DestinationPath "Ritmo-v3.4.0-Windows.zip" -Force

      - name: Upload Windows build
        uses: actions/upload-artifact@v4
        with:
          name: Ritmo-v3.4.0-Windows
          path: Ritmo-v3.4.0-Windows.zip
          if-no-files-found: error
          retention-days: 30
'''
write(".github/workflows/build-windows.yml", windows_workflow)

readme = r'''# Ritmo

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
'''
write("README.md", readme)

changelog_path = Path("CHANGELOG.md")
old = changelog_path.read_text(encoding="utf-8") if changelog_path.exists() else "# Changelog\n"
body = re.sub(r"^# Changelog\s*", "", old)
entry = r'''# Changelog

## 3.4.0

### Android
- Corrigida a persistência ao concluir tarefas pela notificação.
- A camada nativa agora preserva `inbox`, `energy` e `preferredPeriod`.
- `Store.java` alinhado ao schemaVersion 8.
- Ações `Concluir` e `Adiar 10 min` mantidas sem degradar os dados do Smart Planner.

### Windows
- Adicionado runner Flutter para Windows.
- Interface principal adaptada para telas largas com Navigation Rail.
- Adicionado build release automatizado no GitHub Actions.
- Artifact portátil em ZIP com executável e dependências.

### Documentação
- README refeito com visão geral, funcionalidades, arquitetura, plataformas, builds e roadmap.

'''
write("CHANGELOG.md", entry + body)

print("Ritmo 3.4 source migration complete")
