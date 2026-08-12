# Atualizar o Ritmo para 2.2.0 pelo Termux

Supondo que o ZIP esteja em Downloads e o repositório local continue em `~/storage/downloads/RitmoAndroid-src`:

```bash
cd ~/storage/downloads
rm -rf RitmoV22
mkdir RitmoV22
unzip "RitmoAndroid-v2.2.0.zip" -d RitmoV22

cd ~/storage/downloads/RitmoAndroid-src
cp -rf ~/storage/downloads/RitmoV22/. .

grep "versionName" app/build.gradle
git status
git add .
git commit -m "feat: Ritmo 2.2.0 planejador inteligente"
git push origin main
```

O `grep` deve mostrar:

```text
versionName '2.2.0'
```

Depois abra GitHub → RitmoAndroid → Actions. Ao terminar em verde, baixe o artifact `Ritmo-v2.2.0-APK`.

## Teste recomendado antes da tag

1. Abrir o app e confirmar que os dados da 2.1 continuam presentes.
2. Editar uma tarefa e marcar como **Flexível**.
3. Definir uma data planejada e um prazo posterior ou igual.
4. Abrir **Organizar → Planejador**.
5. Configurar capacidade e horário disponível.
6. Executar **Distribuir semana automaticamente**.
7. Conferir a agenda semanal.
8. Testar **Desfazer último planejamento**.
9. Conferir notificações das tarefas que mudaram de horário.

## Criar a tag depois de validar o APK

```bash
git tag -a v2.2.0 -m "Ritmo 2.2.0"
git push origin v2.2.0
```

Não crie a tag antes de confirmar que a build abre e o planejamento está estável.
