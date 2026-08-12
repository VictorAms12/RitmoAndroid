# Atualizar o Ritmo para 2.3.0 pelo Termux

Supondo que o ZIP esteja em Downloads e o repositório local em `~/storage/downloads/RitmoAndroid-src`:

```bash
cd ~/storage/downloads
rm -rf RitmoV23
mkdir RitmoV23
unzip "RitmoAndroid-v2.3.0.zip" -d RitmoV23

cd ~/storage/downloads/RitmoAndroid-src
cp -rf ~/storage/downloads/RitmoV23/. .

grep "versionName" app/build.gradle
git status
git add .
git commit -m "feat: Ritmo 2.3.0 redesign, foco e hábitos"
git push origin main
```

O `grep` deve mostrar `versionName '2.3.0'`.

No GitHub Actions, o artifact esperado é `Ritmo-v2.3.0-APK`.

## Teste antes da tag

1. Abrir a 2.3 sobre a 2.2 e confirmar a migração dos dados.
2. Testar Sistema / Claro / Escuro e barras do Android.
3. Concluir tarefa e hábito e observar animação/haptic.
4. Criar hábito com dias específicos e lembrete.
5. Iniciar foco, pausar, sair, voltar e retomar.
6. Conferir heatmap e métricas de foco.
7. Rodar o Planejador Inteligente e testar desfazer.
8. Conferir widget e notificações.

Após validar:

```bash
git tag -a v2.3.0 -m "Ritmo 2.3.0"
git push origin v2.3.0
```
