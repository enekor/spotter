# Build APK release en Linux

Pasos completos desde una máquina Linux limpia (Ubuntu/Debian). En otras distros sustituye `apt` por su equivalente.

## 1) Dependencias del sistema

```bash
# JDK 17 (lo exige AGP 8.x)
sudo apt update
sudo apt install -y openjdk-17-jdk unzip curl git

# Verifica
java -version   # debe decir 17.x
```

## 2) Android SDK (cmdline-tools)

```bash
# Carpeta donde vivirá el SDK
export ANDROID_HOME="$HOME/Android/Sdk"
mkdir -p "$ANDROID_HOME/cmdline-tools"
cd "$ANDROID_HOME/cmdline-tools"

# Descarga las command-line tools (versión 11076708 = oct 2024, válida)
curl -O https://dl.google.com/android/repository/commandlinetools-linux-11076708_latest.zip
unzip -q commandlinetools-linux-11076708_latest.zip
mv cmdline-tools latest    # Google las espera bajo cmdline-tools/latest
rm commandlinetools-linux-11076708_latest.zip

# PATH (añádelo a ~/.bashrc o ~/.zshrc para que quede)
export PATH="$ANDROID_HOME/cmdline-tools/latest/bin:$ANDROID_HOME/platform-tools:$PATH"

# Acepta licencias e instala lo que pide el proyecto
yes | sdkmanager --licenses
sdkmanager "platform-tools" "platforms;android-35" "build-tools;35.0.0"
```

## 3) Gradle (para regenerar el wrapper que falta)

```bash
# La forma más fácil: SDKMAN
curl -s "https://get.sdkman.io" | bash
source "$HOME/.sdkman/bin/sdkman-init.sh"
sdk install gradle 8.10.2
gradle -v   # debe decir Gradle 8.10.2
```

## 4) El proyecto

```bash
# Sustituye por la ruta donde tengas la carpeta gp
cd /ruta/a/gp

# Genera el gradle-wrapper.jar (binario que no está en el repo)
gradle wrapper --gradle-version 8.10.2

# local.properties con sdk.dir y opcional GROQ_API_KEY
cp local.properties.example local.properties
sed -i "s|^sdk.dir=.*|sdk.dir=$HOME/Android/Sdk|" local.properties
# Edita y pega tu GROQ_API_KEY si quieres:
# nano local.properties
```

## 5) Keystore para firmar el release

```bash
keytool -genkeypair -v \
  -keystore spotter-release.keystore \
  -alias spotter \
  -keyalg RSA -keysize 2048 -validity 10000 \
  -storepass CAMBIAME -keypass CAMBIAME \
  -dname "CN=Spotter, OU=Dev, O=Personal, L=Local, S=NA, C=ES"

# Mete las contraseñas en local.properties (o edítalo a mano)
sed -i "s|^RELEASE_STORE_PASSWORD=.*|RELEASE_STORE_PASSWORD=CAMBIAME|" local.properties
sed -i "s|^RELEASE_KEY_PASSWORD=.*|RELEASE_KEY_PASSWORD=CAMBIAME|" local.properties

# Importante: anota la SHA-1 de este keystore — es la que tienes que dar de alta
# en Google Cloud Console para que el backup de Drive funcione en builds release:
keytool -list -v -keystore spotter-release.keystore -alias spotter -storepass CAMBIAME \
  | grep "SHA1:"
```

## 6) Compilar el APK release

```bash
./gradlew clean assembleRelease

# El APK queda en:
ls -lh app/build/outputs/apk/release/app-release.apk
```

## 7) Instalarlo en el móvil

```bash
# Activa "Depuración USB" en el móvil y conéctalo
adb devices                                  # debe aparecer tu dispositivo
adb install -r app/build/outputs/apk/release/app-release.apk
```

## SHA-1 de debug (si pruebas sin firmar release)

Si dejas los `RELEASE_*` vacíos en `local.properties`, el release se firma con la clave de debug. La SHA-1 de esa clave también tienes que registrarla en Google Cloud Console para que Drive funcione:

```bash
keytool -list -v -keystore ~/.android/debug.keystore -alias androiddebugkey \
  -storepass android -keypass android | grep SHA1:
```

## Notas

- Si `assembleRelease` falla por timeouts a Maven Central, vuelve a lanzarlo: Gradle cachea lo que ya bajó.
- El package de la app es `com.n3k0chan.spotter`. Es el que tienes que registrar en GCP junto a la SHA-1 para Drive y para Health Connect.
- Health Connect en builds debug funciona en Android 14+ sin nada extra; en Android 13 y anteriores la app pedirá instalar `com.google.android.apps.healthdata` desde Play Store.
