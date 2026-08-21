# RC10 Gradle fix

Исправлено:
- добавлен gradle/wrapper/gradle-wrapper.properties
- wrapper переведён на Gradle 8.13

Причина ошибки GitHub Actions:
проект требовал Gradle 8.13, но wrapper отсутствовал.
