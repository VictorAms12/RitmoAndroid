# Versionamento do Ritmo

O projeto usa versionamento semântico:

- PATCH: correções pequenas, ex. `2.1.0 → 2.1.1`.
- MINOR: novos recursos compatíveis, ex. `2.1.1 → 2.2.0`.
- MAJOR: mudanças grandes ou incompatíveis, ex. `2.x → 3.0.0`.

A 2.1.0 usa:

```gradle
versionCode 4
versionName '2.1.0'
```

Em toda nova build instalável, aumente o `versionCode` pelo menos em 1.

Fluxo recomendado:

```bash
git add .
git commit -m "fix: descrição"   # ou feat:, refactor:, ui:
git push origin main
```

Após validar a build:

```bash
git tag -a v2.1.0 -m "Ritmo 2.1.0"
git push origin v2.1.0
```
