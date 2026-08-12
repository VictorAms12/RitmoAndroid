# Atualizar o Ritmo para 2.4.0 no Windows

1. Baixe e extraia `RitmoAndroid-v2.4.0.zip`.
2. Abra a pasta extraída e copie **o conteúdo dela** para dentro do seu repositório local `RitmoAndroid`.
3. Não apague a pasta `.git` do repositório local.
4. Abra o Git Bash dentro de `RitmoAndroid`.

Confira:

```bash
git status
grep "versionName" app/build.gradle
```

O `grep` deve mostrar:

```text
versionName '2.4.0'
```

Depois:

```bash
git add .
git commit -m "feat: Ritmo 2.4.0 execucao diaria e tema verde"
git push origin main
```

O GitHub Actions deverá gerar `Ritmo-v2.4.0-APK`.

Depois de testar a versão:

```bash
git tag -a v2.4.0 -m "Ritmo 2.4.0"
git push origin v2.4.0
```
