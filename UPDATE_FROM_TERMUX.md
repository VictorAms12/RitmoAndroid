# Atualizar o Ritmo para 2.1.0 pelo Termux

Supondo que o ZIP esteja em Downloads e o repositório local continue em `~/storage/downloads/RitmoAndroid-src`:

```bash
cd ~/storage/downloads
rm -rf RitmoV21
mkdir RitmoV21
unzip "RitmoAndroid-v2.1.0.zip" -d RitmoV21

cd ~/storage/downloads/RitmoAndroid-src
cp -rf ~/storage/downloads/RitmoV21/. .

grep "versionName" app/build.gradle
git status
git add .
git commit -m "feat: Ritmo 2.1.0"
git push origin main
```

O `grep` deve mostrar:

```text
versionName '2.1.0'
```

Depois abra GitHub → RitmoAndroid → Actions. Ao terminar em verde, baixe o artifact `Ritmo-v2.1.0-APK`.

## Criar a tag depois de validar o APK

```bash
git tag -a v2.1.0 -m "Ritmo 2.1.0"
git push origin v2.1.0
```

Não crie a tag antes de confirmar que a build abre e as funções principais estão estáveis.
