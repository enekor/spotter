# Spotter

App Android privada para entrenar en el gimnasio: tracking de pesos, repeticiones y tiempos, racha de días y un compañero IA opcional vía Groq.

## Stack

- Kotlin + Jetpack Compose (Material 3, paleta fija slate + naranja, sin dynamic color)
- Tipografía Inter/Geist-style (numéricos en monospace para datos)
- Room (SQLite) — todo local, sin cuentas
- EncryptedSharedPreferences para la API key
- Foreground Service para el temporizador de descanso
- Retrofit + OkHttp + kotlinx-serialization para Groq

## Cómo abrirlo

1. Abre la carpeta `gp` con Android Studio (Iguana o superior).
2. Android Studio detecta que falta el `gradle-wrapper.jar` y te ofrece regenerarlo. Si no, ejecuta una vez:
   ```
   gradle wrapper --gradle-version 8.10.2
   ```
   (necesitas tener Gradle instalado de sistema, o lanzarlo desde Android Studio: *File → Sync Project with Gradle Files*).
3. Copia `local.properties.example` a `local.properties` y rellena `sdk.dir`. Si quieres, pega ahí tu `GROQ_API_KEY`.
4. Ejecuta sobre dispositivo o emulador (minSdk 29).

## Backup en Google Drive

La app puede sincronizar `spotter.db` a una **carpeta privada de la app** en tu Drive (`appDataFolder`). Esa carpeta es **invisible** en drive.google.com — solo nuestra app, autenticada con el mismo OAuth client (package + SHA-1), puede leer/escribir ahí.

### Configuración en Google Cloud Console

Una sola vez:
1. Crea un proyecto en https://console.cloud.google.com.
2. **APIs & Services → Library** → habilita **Google Drive API**.
3. **APIs & Services → OAuth consent screen** → tipo **External**, rellena los datos básicos. En *Scopes*, añade `https://www.googleapis.com/auth/drive.appdata` (no requiere verificación porque solo usamos el `appDataFolder`).
4. **APIs & Services → Credentials → Create credentials → OAuth client ID**:
   - Tipo: **Android**
   - Package name: `com.n3k0chan.spotter`
   - SHA-1: el de tu certificado de debug y/o release.
5. (Mientras la app está en testing) añade tu cuenta de Google como *Test user* en la pantalla de OAuth consent.

No hace falta meter ningún Client ID en `local.properties` — la librería usa el package + SHA-1 implícitamente.

### Comportamiento

- **Backup automático**: tras finalizar cada entreno, si la cuenta está enlazada y "Subir tras cada entreno" está activado, la base de datos se sube en background.
- **Backup manual**: Ajustes → "Subir ahora".
- **Restaurar**: Ajustes → "Restaurar de Drive". Pide confirmación, sobrescribe la BD local con la copia de Drive y reinicia la app.
- La primera operación pedirá un consentimiento OAuth dentro de la app; después es silenciosa.

## API key de Groq

- Si la pones en `local.properties`, viaja al `BuildConfig` y la app la usa por defecto.
- Si la pegas dentro de la app (Ajustes → API key), se guarda cifrada y **sobrescribe** la del BuildConfig.
- Para volver al modo "BuildConfig", borra la clave en Ajustes.

Consigue una key gratis en https://console.groq.com/keys.

## Decisiones que ya hay tomadas

- Catálogo de ejercicios vacío al inicio: tú lo vas creando.
- Plantillas reutilizables + posibilidad de entrenos libres sin plantilla.
- Solo `kg`. Si en algún momento quieres `lb`, hay que tocar `Workout.weightKg` y la UI de input.
- Mensajes motivacionales secos por defecto (sin "tú puedes" ni emojis); con API key, los genera Groq.
- Modelo Groq por defecto: `llama-3.3-70b-versatile` (configurable en Ajustes).

## Estructura

```
app/src/main/java/com/erebollo/spotter/
├── ai/                  # Cliente Groq + prompts
├── data/
│   ├── db/              # Room (entidades, DAOs)
│   ├── prefs/           # EncryptedSharedPreferences (API key, ajustes)
│   └── repository/      # Repositorios sobre los DAOs
├── di/                  # ServiceLocator (sin Hilt)
├── motivation/          # Frases motivacionales offline
├── timer/               # Foreground Service del temporizador
├── ui/                  # Compose: pantallas + nav + theme
└── util/                # Cálculo de rachas
```

## Permisos

- `INTERNET`: llamadas a Groq.
- `POST_NOTIFICATIONS` (Android 13+): mostrar el descanso en la barra de estado.
- `FOREGROUND_SERVICE` + `FOREGROUND_SERVICE_SPECIAL_USE`: que el descanso siga corriendo aunque cierres la app.
- `VIBRATE`: aviso al terminar el descanso.

## Próximos pasos (no implementados aún)

- Gráficas de progreso por ejercicio.
- Reordenar ejercicios dentro del entreno con drag & drop.
- Importar/exportar histórico (JSON).
- TTS para guiar el entreno con voz.
