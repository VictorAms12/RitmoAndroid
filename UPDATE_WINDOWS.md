# Atualizar o repositório para Ritmo 3.0 Flutter — Windows

A 3.0 é uma migração estrutural. Não copie apenas a pasta `lib` ou `android/app`: substitua os arquivos rastreados da versão nativa pelo novo projeto Flutter, preservando somente a pasta `.git` do repositório.

## 1. Garanta que a 2.4 está salva no GitHub

Dentro do repositório atual:

```bash
git status
git pull origin main
git tag -a v2.4.0 -m "Ritmo 2.4.0 nativo"
git push origin v2.4.0
```

Se a tag `v2.4.0` já existir, não a recrie.

## 2. Remova os arquivos rastreados antigos

Ainda dentro do repositório:

```bash
git rm -r .
```

Esse comando remove os arquivos rastreados, mas preserva a pasta `.git` e o histórico.

## 3. Copie a versão Flutter

Extraia `RitmoFlutter-v3.0.0.zip` e copie todo o conteúdo da pasta `RitmoFlutter-v3.0.0` para a raiz do repositório.

A raiz deve conter:

```text
lib/
android/
signing/
.github/
pubspec.yaml
analysis_options.yaml
README.md
```

## 4. Versione

```bash
git add -A
git status
git commit -m "feat: Ritmo 3.0.0 migracao para Flutter"
git push origin main
```

O GitHub Actions gerará o artifact `Ritmo-v3.0.0-APK`.

## 5. Instale como atualização

Se você quer preservar os dados locais da 2.4, não desinstale o app. Instale o APK 3.0 por cima da 2.4.

A 3.0 mantém `com.ritmo.mobile`, a chave de assinatura e o SharedPreferences nativo usados pela 2.4.

## Se quiser desenvolver localmente

Com Flutter instalado, na raiz do projeto:

```bash
flutter pub get
flutter analyze
flutter run
```

Para gerar APK localmente, use o build Android normalmente pelo Flutter/Android Studio. O GitHub Actions continua sendo o caminho automatizado já configurado no projeto.
