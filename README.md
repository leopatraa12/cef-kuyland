# News RP Android SA-MP

Source do APK Android do News RP/SA-MP Mobile, com launcher, HUD, overlays WebView, ferramentas de host local, editor Pawn, downloader de Data Lite/Full e camada nativa C/C++.

## O que tem neste projeto

- Launcher Android em Java.
- HUD e telas in-game em Java/XML.
- Interfaces WebView em HTML/CSS/JS.
- Codigo nativo SA-MP em C/C++ via NDK.
- Downloader de Data Lite/Full via `update_sources.json`.
- Editor/compilador Pawn e ferramentas de host local.

## Estrutura principal

```text
app/src/main/java/com/xyron/game/launcher   Launcher, abas, download e configuracoes
app/src/main/java/com/xyron/game/main       Activity do jogo, HUD, overlays e ponte Java/native
app/src/main/res                            Layouts XML, icones, temas e imagens Android
app/src/main/assets/interfaces              Celular, inventario, mapa e runtime WebView
app/src/main/assets/update_sources.json     Fontes de download da Data Lite/Full
app/src/main/jniLibs/armeabi-v7a            Bibliotecas nativas usadas no APK
jni/jni                                     Source C/C++ da libSAMP
jni/compile.cmd                             Script Windows para compilar a lib nativa
prdownloader                                Modulo local do downloader
server                                     Arquivos auxiliares do host/editor
```

## Requisitos

- Windows com Android Studio instalado.
- Android SDK Platform 33.
- Android Build Tools instalado pelo Android Studio.
- NDK com `ndk-build.cmd` instalado. O script procura por NDK 27, 26, 25 ou 21.
- Celular Android com depuracao USB/Wireless ADB ativada para instalar e testar.

Se o Gradle nao achar Java no terminal, configure `JAVA_HOME` ou edite localmente `gradle.properties` e aponte `org.gradle.java.home` para o JBR do Android Studio.

## Como compilar o APK

1. Abra a pasta raiz no Android Studio ou PowerShell.
2. Configure o SDK/NDK pelo Android Studio.
3. Compile o APK debug:

```powershell
$env:ANDROID_HOME="$env:LOCALAPPDATA\Android\Sdk"
$env:ANDROID_SDK_ROOT="$env:LOCALAPPDATA\Android\Sdk"
.\gradlew.bat :app:assembleDebug --no-daemon
```

O APK sai em:

```text
app/build/outputs/apk/debug/app-debug.apk
```

Para instalar via ADB:

```powershell
adb devices
adb install -r -d -g app/build/outputs/apk/debug/app-debug.apk
```

## Como compilar a lib nativa

O source C/C++ fica em `jni/jni`.

```powershell
cd jni
.\compile.cmd
```

Depois copie a lib gerada para o APK:

```powershell
copy jni\libs\armeabi-v7a\libSAMP.so ..\app\src\main\jniLibs\armeabi-v7a\libSAMP.so
```

## Data Lite e Data Full

As fontes ficam em:

```text
app/src/main/assets/update_sources.json
```

O APK baixa os arquivos do jogo para:

```text
/sdcard/Android/data/com.xyron.game/files
```

Para evitar crash nativo no boot, a Data Lite precisa conter arquivos criticos como:

- `texdb/txd/txd.*`
- `texdb/samp/samp.*`
- `texdb/samp.img`
- `texdb/gta3.img`
- `texdb/gta_int.img`
- `SAMP/main.scm`

Se o jogo crashar em `libGTASA.so CCustomRoadsignMgr::Initialise`, normalmente a Data Lite esta incompleta. Rebaixe os dados pelo launcher ou confira se `texdb/txd` e `texdb/samp` existem.

## Onde editar

- Nome/icone do app: `app/build.gradle`, `app/src/main/res/mipmap-*`, `app/src/main/res/drawable-nodpi`.
- Tela inicial/launcher: `app/src/main/res/layout/fragment_home.xml`.
- Downloader: `app/src/main/java/com/xyron/game/launcher/UpdateService.java`.
- Verificador da data: `app/src/main/java/com/xyron/game/launcher/util/GameDataVerifier.java`.
- Inventario/mochila: `app/src/main/assets/interfaces/inventario/index.html`.
- Imagens dos itens da mochila: `app/src/main/assets/interfaces/inventario/images`.
- Celular: `app/src/main/assets/interfaces/celular/index.html`.
- Hooks/native SA-MP: `jni/jni/game`, `jni/jni/net`, `jni/jni/main.cpp`.

## Cuidados antes de publicar

Nao coloque no ZIP publico:

- `app/build/`
- `prdownloader/build/`
- `.gradle/`
- `.idea/`
- `.vscode/`
- `local.properties`
- APKs gerados
- logs, screenshots e arquivos temporarios
- chaves `.jks` ou `.keystore`

`app/google-services.json` esta com dados placeholder. Quem for usar Firebase deve trocar pelo proprio arquivo.

`app/src/main/jniLibs` e `app/libs` podem conter bibliotecas prebuilt necessarias para montar um APK executavel. Elas nao substituem o source C/C++ em `jni/jni`. Se voce quiser publicar uma release estritamente source-only, remova esses binarios e explique no README como restaurar as dependencias locais.

## Debug de crash

Sempre analise com logcat:

```powershell
adb logcat -c
adb logcat -v time | Select-String "FATAL EXCEPTION|Fatal signal|SIGSEGV|SIGABRT|libGTASA|libSAMP|libsamp"
```

Para crash nativo, procure:

- `Build fingerprint`
- `pid`
- `tid`
- `signal`
- `fault addr`
- `backtrace`
- biblioteca e offset, por exemplo `libGTASA.so pc 005a576a`.

Nao chute offset. Use o logcat/tombstone e compare com a lib correta.

## Estado atual

- Nome do app: News RP.
- Package Android: `com.xyron.game`.
- ABI principal: `armeabi-v7a`.
- Data padrao: Lite.
- Inventario com imagens locais corrigidas em `assets/interfaces/inventario/images`.
- Verificacao de Data Lite reforcada para nao iniciar o jogo com arquivos incompletos.

