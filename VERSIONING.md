# Versionamento do Ritmo

O projeto usa versionamento semântico:

- PATCH: correções pequenas, ex. `2.3.0 → 2.3.1`.
- MINOR: novos recursos compatíveis, ex. `2.3.1 → 2.4.0`.
- MAJOR: mudanças grandes ou incompatíveis, ex. `2.x → 3.0.0`.

A 2.3.0 usa:

```gradle
versionCode 6
versionName '2.3.0'
```

Toda build instalável futura deve aumentar `versionCode` pelo menos em 1.

Fluxo recomendado:

```bash
git status
git add .
git commit -m "feat: Ritmo 2.3.0 redesign, foco e hábitos"
git push origin main
```

Somente depois de validar o APK:

```bash
git tag -a v2.3.0 -m "Ritmo 2.3.0"
git push origin v2.3.0
```
