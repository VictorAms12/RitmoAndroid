# Versionamento do Ritmo

O projeto usa dois números no arquivo `app/build.gradle`:

- `versionCode`: número inteiro interno do Android. Precisa aumentar a cada APK publicado.
- `versionName`: versão visível, usando SemVer (`MAJOR.MINOR.PATCH`).

## Regra recomendada

- PATCH: correção de bug. Ex.: `2.0.0` → `2.0.1`.
- MINOR: recurso novo compatível. Ex.: `2.0.1` → `2.1.0`.
- MAJOR: mudança grande no produto. Ex.: `2.1.0` → `3.0.0`.

Para qualquer nova versão, aumente também `versionCode`:

```gradle
versionCode 3
versionName '2.0.1'
```

## Tags Git

Depois de enviar uma versão estável:

```bash
git tag -a v2.0.0 -m "Ritmo 2.0.0"
git push origin v2.0.0
```

Isso cria um marco fácil de recuperar no histórico.

## Assinatura

A pasta `signing/` contém a chave usada para assinar o APK. **Não apague nem troque essa chave.**

Como a chave está dentro do projeto, mantenha o repositório **privado**. Se ela for perdida, um APK futuro não poderá atualizar o app instalado com a assinatura anterior.
