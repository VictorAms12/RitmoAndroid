# Atualizar o Ritmo para 2.3.0 no Windows

## 1. Preparar os arquivos

Extraia `RitmoAndroid-v2.3.0.zip`. Copie **o conteúdo de dentro da pasta extraída** para a pasta local já clonada do repositório `RitmoAndroid`.

Não apague nem substitua a pasta oculta `.git` do repositório local.

## 2. Conferir a versão

No Git Bash, dentro da pasta `RitmoAndroid`:

```bash
grep "versionName" app/build.gradle
git status
```

O primeiro comando deve mostrar:

```text
versionName '2.3.0'
```

No PowerShell, a conferência equivalente é:

```powershell
Select-String "versionName" app\build.gradle
```

## 3. Commit e push

```bash
git add .
git commit -m "feat: Ritmo 2.3.0 redesign, foco e hábitos"
git push origin main
```

O GitHub Actions deve iniciar automaticamente e gerar o artifact `Ritmo-v2.3.0-APK`.

## 4. Depois de testar o APK

```bash
git tag -a v2.3.0 -m "Ritmo 2.3.0"
git push origin v2.3.0
```

Não crie a tag antes de confirmar que a atualização abre, preserva os dados e que foco, hábitos, planner, notificações e temas estão estáveis.
