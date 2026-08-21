# Final Gradle fix RC10

Исправлено:
- добавлен Gradle wrapper;
- gradle-wrapper.properties использует Gradle 8.13;
- GitHub Actions теперь использует ./gradlew вместо установленного Gradle runner.

Причина предыдущей ошибки:
workflow запускал глобальный Gradle 8.9 и игнорировал wrapper.
