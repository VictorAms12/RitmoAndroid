# Atualizar o Ritmo pelo Termux

Este procedimento substitui os arquivos da versão anterior sem apagar o histórico Git (`.git`).

Supondo que seu repositório esteja em `~/RitmoAndroid` e o ZIP novo esteja em Downloads:

```bash
cd ~/storage/downloads
rm -rf RitmoAndroid-v2-files
mkdir RitmoAndroid-v2-files
unzip RitmoAndroid-v2.0.0.zip -d RitmoAndroid-v2-files

cd ~/RitmoAndroid
cp -rf ~/storage/downloads/RitmoAndroid-v2-files/. .

git status
git add .
git commit -m "feat: Ritmo 2.0.0"
git push origin main

git tag -a v2.0.0 -m "Ritmo 2.0.0"
git push origin v2.0.0
```

Se seu repositório está em outro local, altere apenas `~/RitmoAndroid` no comando `cd`.

Depois do `git push`, o GitHub Actions inicia automaticamente. Abra **Actions → Build Ritmo APK** e baixe o artifact `Ritmo-v2.0.0-APK` quando ficar verde.
