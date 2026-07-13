# Guia para editar interfaces

Este projeto foi publicado para permitir edicao real das interfaces do APK. A maior parte do visual do launcher, HUD Android e overlays WebView pode ser alterada sem mexer em offsets da `libGTASA.so`.

## Mapa rapido

```text
app/src/main/res/layout/hud.xml
HUD Android dentro do jogo: botoes laterais, frames das WebViews, painel de render, celular, inventario e roleta.

app/src/main/java/com/xyron/game/main/SAMP.java
Activity principal do jogo: abre/fecha overlays, registra cliques do HUD, faz ponte Java/JNI e envia dados para HTML.

app/src/main/assets/interfaces
Interfaces HTML/CSS/JS carregadas dentro do jogo por WebView.

app/src/main/res/drawable
Icones, fundos, imagens do HUD e botoes Android.

app/src/main/java/com/xyron/game/launcher
Launcher, servidor, download, editor, host e ferramentas.

app/src/main/res/layout/fragment_*.xml
Telas XML do launcher.

jni/jni
Source C/C++ da camada nativa SA-MP. Use quando a interface precisar de dados reais do jogo ou chamar funcoes nativas.
```

## O que da para editar sem rebuild nativo

Essas mudancas normalmente exigem apenas recompilar o APK pelo Gradle:

- Layout do HUD Android em `app/src/main/res/layout/hud.xml`.
- Icones e imagens em `app/src/main/res/drawable`.
- Inventario HTML em `app/src/main/assets/interfaces/inventario/index.html`.
- Imagens do inventario em `app/src/main/assets/interfaces/inventario/images`.
- Celular HTML em `app/src/main/assets/interfaces/celular/index.html`.
- Mapa HTML em `app/src/main/assets/interfaces/map/index.html`.
- Roleta de armas em `app/src/main/assets/interfaces/weapon_wheel/index.html`.
- Imagens da roleta em `app/src/main/assets/interfaces/weapon_wheel/images`.
- Telas do launcher em `app/src/main/res/layout`.
- Logica Java do launcher em `app/src/main/java/com/xyron/game/launcher`.

## O que exige rebuild da libSAMP.so

Essas mudancas entram na camada C/C++ e precisam de NDK, libs locais e recompilacao da `libSAMP.so`:

- Ler dados reais do player que ainda nao chegam no Java.
- Criar nova chamada `native` em Java.
- Alterar hooks, RakNet, pools, textdraws, radar, chat nativo ou memoria do GTA.
- Trocar comportamento que depende de `libGTASA.so`.
- Adicionar ponte JNI nova entre C++ e Java.

Nao invente offset. Se a mudanca precisar de endereco da `libGTASA.so`, use dump/logcat/build correta ou implemente scanner de assinatura.

## Como editar um botao do HUD

1. Abra `app/src/main/res/layout/hud.xml`.
2. Procure o painel ou botao existente, por exemplo `btn_weapon_wheel`.
3. Duplique o padrao visual que ja existe no HUD.
4. Adicione um `android:id` novo.
5. Abra `app/src/main/java/com/xyron/game/main/SAMP.java`.
6. Procure onde os botoes recebem `setOnClickListener`.
7. Ligue o novo botao a uma acao Java, WebView ou chamada `native`.

Exemplo de tipos de acao que ja existem:

```java
showInventoryOverlay();
showWeaponWheelOverlay();
showPhoneOverlay();
sendCommandV(commandBytes);
onClickButton(actionId);
```

Se a acao chamar `native`, confira se existe implementacao JNI correspondente em `jni/jni`.

## Como editar o inventario

Arquivos principais:

```text
app/src/main/assets/interfaces/inventario/index.html
app/src/main/assets/interfaces/inventario/images
```

O inventario e uma WebView offline carregada pelo APK. Pode mudar HTML, CSS, textos, posicoes, imagens e comportamento JS. Para adicionar icone novo, coloque a imagem em `images` e referencie no `index.html`.

Se quiser mostrar itens reais vindos do servidor ou do cliente nativo, crie uma ponte de dados no JavaScript e envie os dados por `SAMP.java` usando `evaluateJavascript`.

## Como editar a roleta de armas

Arquivos principais:

```text
app/src/main/assets/interfaces/weapon_wheel/index.html
app/src/main/assets/interfaces/weapon_wheel/images/weapon_ID.webp
```

A roleta recebe uma lista JSON pelo Java:

```text
[{ "id": 0, "ammo": 0, "current": true }]
```

O Java sincroniza isso por `UpdateWeaponWheel`, `syncWeaponWheelPayload` e `RuntimeOverlayBridge.selectWeapon` em `SAMP.java`. A troca real de arma chama:

```java
native void selectWeapon(int weaponId);
```

Entao:

- Visual da roleta: edite HTML/CSS/JS.
- Icones: troque `weapon_ID.webp`.
- Selecionar arma real no jogo: precisa da ponte nativa existente ou de ajuste C++ em `jni/jni`.

## Como adicionar uma nova interface WebView dentro do jogo

Use o padrao das interfaces existentes.

1. Crie uma pasta:

```text
app/src/main/assets/interfaces/minha_interface/index.html
```

2. Em `SAMP.java`, adicione uma URL:

```java
private static final String MY_OVERLAY_URL = "file:///android_asset/interfaces/minha_interface/index.html";
```

3. Em `hud.xml`, adicione um `FrameLayout` com uma `WebView`.
4. Em `initializeRuntimeOverlays`, busque os IDs novos com `findViewById`.
5. Em `ensureRuntimeOverlayConfigured`, crie o caso da nova overlay.
6. Crie metodos `showMinhaInterfaceOverlay` e `hideMinhaInterfaceOverlay`.
7. Se o HTML precisar chamar Java, adicione metodo em `RuntimeOverlayBridge` com `@JavascriptInterface`.

Dentro do HTML, o objeto Java aparece como:

```javascript
window.Android
```

Por exemplo:

```javascript
window.Android.closeInventory();
window.Android.openWeaponWheel();
window.Android.runCommand("/me abriu uma interface");
```

## Como editar o launcher

O launcher fica separado da tela do jogo.

```text
app/src/main/java/com/xyron/game/launcher
app/src/main/res/layout/fragment_home.xml
app/src/main/res/layout/fragment_servers.xml
app/src/main/res/layout/fragment_editor.xml
app/src/main/res/layout/fragment_host.xml
```

Para mudar visual, comece pelos XML em `res/layout`. Para mudar comportamento, edite os fragments Java correspondentes.

## Limites honestos

- O repositorio publico nao inclui `.so`, `.a`, `.aar`, `.jar`, APK, data full/lite ou assets proprietarios.
- Quem baixar consegue editar o source, mas para compilar APK jogavel precisa fornecer localmente os binarios e assets que tem direito de usar.
- HUD Android, launcher e WebViews sao as partes mais faceis de editar.
- Alteracoes de memoria, GTA internals, RenderWare e hooks precisam de C++/NDK e validacao por logcat.
- Arm64 ainda nao esta portado neste snapshot.

