# Dicio A34

Versión de prueba basada en [Dicio](https://github.com/Stypox/dicio-android), con registro local de gastos por voz en español y selección conservadora de aplicaciones.

## Descargar

[Descargar APK (47 MiB)](https://github.com/JonathanMV0309/dicio-a34/releases/download/v4.1-a34.1/dicio-a34.apk) · [Versión y código fuente](https://github.com/JonathanMV0309/dicio-a34/releases/tag/v4.1-a34.1)

Descarga pública verificada; SHA-256: `c24658bd89fa5ceff3d50160c943b506078699609cd035e933763bbbc126492d`.

## Ejemplo

1. «Abre nota».
2. «En transporte agrega 3500».
3. «Agrega 2000».
4. «¿Cuál es el total de eso?» → 5500 pesos.

También admite total general, consultas de hoy o del mes, historial y deshacer el último gasto. Los datos están en el registro propio de Dicio A34, no en Samsung Notes.

## Instalación

Instala el APK, permite micrófono y notificaciones y activa «Hey Dicio». En ajustes puedes seleccionar «Reconocimiento de voz de Android». Desactiva la escucha de Dicio original si ambas versiones responden a la vez.

## Validación y límites

Compilación completa correcta, 44 pruebas unitarias de la app aprobadas y 31 comprobaciones adicionales del registro de gastos. Firma APK y bibliotecas ARM64 verificadas. Las pruebas físicas de micrófono, batería y pantalla bloqueada en Samsung A34 están pendientes. La app no tiene una integración de conversación con IA ni sincronización con SIAL.

Consulta [A34-DEVELOPMENT.md](A34-DEVELOPMENT.md) para instrucciones de compilación y pruebas. Los ejemplos originales de CI están en `ci-examples`. El flujo `Publish verified APK` publicó el APK después de verificar su SHA-256.

## Licencia

GPL-3.0; se conserva [LICENSE](LICENSE) y la documentación original en [README-UPSTREAM.md](README-UPSTREAM.md).
