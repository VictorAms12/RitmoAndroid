# Atualizar o Ritmo para 2.4.0 pelo Termux

```bash
cd ~/storage/downloads
rm -rf RitmoV24
mkdir RitmoV24
unzip "RitmoAndroid-v2.4.0.zip" -d RitmoV24
```

Se o ZIP contiver diretamente os arquivos do projeto:

```bash
cp -rf ~/storage/downloads/RitmoV24/. ~/storage/downloads/RitmoAndroid-src/
cd ~/storage/downloads/RitmoAndroid-src
```

Confirme:

```bash
grep "versionName" app/build.gradle
git status
```

Depois:

```bash
git add .
git commit -m "feat: Ritmo 2.4.0 execucao diaria e tema verde"
git push origin main
```

Artifact esperado: `Ritmo-v2.4.0-APK`.
