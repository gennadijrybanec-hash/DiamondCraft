# DiamondCraft — upload key и GitHub Secrets

Для Google Play используйте отдельный upload key. Не кладите приватный ключ и пароли в репозиторий.

## GitHub Secrets

В репозитории: Settings → Secrets and variables → Actions → New repository secret.

Создайте:

- `DIAMONDCRAFT_UPLOAD_KEYSTORE_B64` — файл upload-keystore, закодированный Base64;
- `DIAMONDCRAFT_KEYSTORE_PASSWORD` — пароль хранилища;
- `DIAMONDCRAFT_KEY_ALIAS` — alias ключа;
- `DIAMONDCRAFT_KEY_PASSWORD` — пароль ключа.

После добавления секретов GitHub Actions автоматически соберёт артефакт:

`DiamondCraft-google-play-aab`

Именно подписанный AAB затем используется для загрузки в Play Console.

## Важно

- `app/diamondcraft-dev.keystore` предназначен только для наших тестовых APK.
- Для Google Play его не использовать.
- Upload key храните отдельно и сделайте резервную копию.
- App signing key рекомендуется доверить Google Play App Signing.
