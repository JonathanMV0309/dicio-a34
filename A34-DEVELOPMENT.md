# Dicio A34 — versión en desarrollo

Base: https://github.com/Stypox/dicio-android (GPL-3.0; se conserva LICENSE).

## Cambios implementados

- Alias de aplicaciones habituales de Samsung y Google. «notas» reconoce Samsung Notes y Keep.
- Se elimina la selección por distancia de edición que podía confundir «notas» con «fotos».
- Si hay varias aplicaciones compatibles, se muestran botones para elegir y una explicación por voz. También se puede repetir la petición con el nombre completo.
- Nuevo método «Reconocimiento de voz de Android», con el proveedor configurado en el teléfono. Mantiene Vosk como alternativa sin conexión.
- Las alternativas de reconocimiento se ordenan por confianza válida; los valores desconocidos conservan el orden del proveedor.
- Aplicación de desarrollo independiente (`org.stypox.dicio.a34`), llamada Dicio A34, para conservar la instalación original.

## Proyectos revisados

- Dicio: interfaz, habilidades, Vosk y OpenWakeWord existentes.
- https://github.com/woheller69/whisperIME (MIT): implementa RecognitionService y RecognizerIntent; se puede usar como proveedor con el nuevo adaptador de Android. No se copió su código ni se incluyó su modelo pesado en el APK. Necesita instalación separada y configuración como proveedor de voz para usarlo.
- https://github.com/Picovoice/porcupine: admite Android y español, pero requiere una clave y un modelo personalizado. No se integró sin una cuenta configurada.
- https://github.com/k2-fsa/sherpa-onnx: motores de reconocimiento y palabra de activación para Android. No se seleccionó un modelo nuevo sin medir precisión y latencia en el teléfono.

Esta versión no incluye un servicio de conversación con IA ni la integración de SIAL.

## Construcción

Requiere Java 21 y Java 17 (biblioteca dicio-numbers), Android SDK 36, Build Tools 35.0.0 y 36.0.0 y acceso a Google Maven, Maven Central, Gradle Plugin Portal y GitHub. Usa el wrapper oficial del proyecto, con SHA-256 de Gradle conservado.

```sh
./gradlew --no-daemon --max-workers=2 :app:testDebugUnitTest :app:assembleDebug
```

APK esperado: `app/build/outputs/apk/debug/app-debug.apk`.

También se incluye `.github/workflows/android-a34.yml`. No se ha ejecutado en GitHub y no sustituye la validación del APK.

## Evidencia local

Se compilaron las clases reales AppMatcher y RecognitionResults con Kotlin/JVM 2.2.0 y se ejecutó `tools/VoiceRegressionChecks.kt`: 13 comprobaciones aprobadas. Los mismos casos se incorporaron a la suite Kotest. La compilación completa `:app:assembleDebug` y la suite `:app:testDebugUnitTest` terminaron correctamente: 44 pruebas, 0 fallos, 0 errores y 0 omitidas. Se usó el espejo de Maven Central configurado para resolver los errores 429 de la instancia. El adaptador AndroidInputDevice también se compiló contra android.jar API 36, usando tipos mínimos para LocaleManager, UserSettings y Progress; es una comprobación de tipos/API, no una prueba de ejecución de Android. Los XML modificados se analizaron correctamente y `git diff --check` pasó.

Se descargaron Gradle y Android SDK desde destinos oficiales verificando los checksums publicados. No se desactivó TLS ni la verificación de artefactos.

## Comprobación obligatoria en Samsung A34

1. Instalar la compilación de desarrollo, dar permisos de micrófono y notificaciones; seleccionar español.
2. Seleccionar «Reconocimiento de voz de Android». Configurar Google/Samsung como proveedor del sistema, evitando elegir el propio Dicio (recursión). WhisperIME es opcional y requiere un modelo multilingüe.
3. Activar «Hey Dicio». En One UI, permitir batería sin restricciones si el sistema detiene el servicio.
4. Probar «abre notas» con Samsung Notes, con Samsung Notes + Keep y sin ninguna aplicación de notas. No debe abrir Fotos al reconocer «notas».
5. Probar botón de micrófono, silencio, cancelación, falta de internet, permisos denegados y cambio entre Vosk y Android.
6. Medir 20 activaciones en silencio y 20 con ruido, con pantalla encendida y bloqueada. Registrar transcripción, acción real y latencia. Comparar con Dicio original en las mismas condiciones.
7. Medir batería durante al menos una hora de escucha. No declarar mejora de precisión ni de batería sin estos resultados.

No hay un Samsung A34 ni un dispositivo Android conectado a este entorno. No se ha validado aún el uso real del micrófono, las restricciones de One UI, la convivencia con el servicio de palabra de activación ni la instalación del APK.

## Registro de gastos por voz

Nueva habilidad en español con datos locales privados (no modifica Samsung Notes):

- «Abre nota y en transporte agrega 3500».
- «En transporte agrega tres mil quinientos pesos».
- «Agrega 2000» usa la última categoría.
- «¿Cuál es el total de eso?» consulta la última categoría, incluso después de reiniciar.
- «Total de transporte hoy» y «total de transporte este mes» usan la fecha local del teléfono.
- «Total general», «muestra los gastos de transporte» y «deshaz el último gasto».

Los importes son pesos colombianos enteros. Se aceptan 3500 y 3.500; los decimales ambiguos se rechazan sin redondear. El registro se guarda con AtomicFile, sincronización a disco y comprobación de los bytes escritos. Un archivo dañado provoca un error y no se sobrescribe silenciosamente. Desinstalar la app elimina sus datos; esta versión todavía no tiene exportación o sincronización.

La comprobación Kotlin/JVM `tools/ExpenseRegressionChecks.kt` pasó 31 casos, incluidos reinicio con archivo real, categorías separadas, totales por fecha en America/Bogota, deshacer, importes incorrectos y fallos de escritura. También se incorporaron pruebas Kotest de la habilidad. La validación física en Samsung A34 sigue pendiente.

Después de «abre nota» y de guardar un gasto, el asistente usa el mecanismo de conversación de Dicio para volver a escuchar cuando termina de responder. La consulta de total termina ese turno. Esto permite abrir, agregar y consultar sin repetir el nombre en cada frase; todavía requiere comprobar los tiempos de micrófono y voz en el teléfono.

## APK generado y verificado

Archivo entregado: `dicio-a34.apk`, compilación de desarrollo firmada (48.8 MB), con bibliotecas ARM64. Se verificó la firma con `apksigner`, la estructura ZIP, el identificador `org.stypox.dicio.a34` y su SDK mínimo 21. La versión original de Dicio se conserva como otra aplicación. Desactiva la escucha de la original si ambas intentan activarse a la vez.

En el A34, da permisos de micrófono y notificaciones, activa «Hey Dicio» y selecciona el método «Reconocimiento de voz de Android» para probar Google/Samsung. Los gastos quedan en el registro propio de Dicio A34; no se escriben en Samsung Notes. No desinstales esta versión si necesitas conservar el registro local.

La suite de Android con dispositivo (`connectedDebugAndroidTest`) no se ejecutó: no hay un teléfono ni un emulador conectados. Precisión acústica, batería, permisos y escucha con pantalla bloqueada requieren pruebas en el teléfono.
