# Versionamento do Ritmo

O projeto segue Semantic Versioning de forma prática:

- PATCH: correções e estabilidade, ex. `2.4.0 → 2.4.1`.
- MINOR: novos recursos compatíveis, ex. `2.4.1 → 2.5.0`.
- MAJOR: mudança ampla de arquitetura/comportamento, ex. `2.x → 3.0.0`.

A 2.4.0 usa:

```gradle
versionCode 7
versionName '2.4.0'
```

Sempre aumente `versionCode` em cada APK distribuído.

Após validar uma versão estável:

```bash
git tag -a v2.4.0 -m "Ritmo 2.4.0"
git push origin v2.4.0
```

Mantenha `signing/ritmo.keystore` preservado para que o Android reconheça as próximas builds como atualizações do mesmo app.
