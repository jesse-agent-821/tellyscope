# <img src="docs/logo/icon.svg" alt="" width="64" height="64" align="middle"> Tellyscope

Debloat your Android TV, install apps, and browse and transfer files on it, all from your phone. No root, no PC.

<p>
  <img src="https://img.shields.io/badge/size-under%203%20MB-00696B" alt="Under 3 MB">
  <img src="https://img.shields.io/badge/tracking-none-00696B" alt="No tracking">
  <img src="https://img.shields.io/badge/root-not%20needed-00696B" alt="No root">
  <img src="https://img.shields.io/badge/license-GPL--3.0-00696B" alt="GPL-3.0">
  <a href="../../releases/latest"><img src="https://img.shields.io/github/v/release/man15h/tellyscope?color=00696B&label=release" alt="Latest release"></a>
</p>

<a href="../../releases/latest"><img src="docs/badges/get-it-on-github.png" alt="Get the signed APK on GitHub" height="60"></a>
<a href="https://apps.obtainium.imranr.dev/redirect?r=obtainium://add/https://github.com/man15h/tellyscope"><img src="docs/badges/get-it-on-obtainium.png" alt="Get it on Obtainium" height="60"></a>
<a href="https://rookieenough.github.io/Orion-Data/redirect.html?id=tellyscope&icon=https%3A%2F%2Fraw.githubusercontent.com%2Fman15h%2Ftellyscope%2Fmain%2Fdocs%2Flogo%2Ficon.svg"><img src="docs/badges/get-it-on-orion.png" alt="Get it on Orion Store" height="60"></a>

[![Watch the demo](docs/site/demo-thumb.webp)](https://youtu.be/N_PeDw5GiCc)

<table>
  <tr>
    <td width="25%"><img src="docs/screenshots/tv-page.png" alt="A TV's page: the TV at a glance, its vitals, and the Install, Debloat and Send buttons"></td>
    <td width="25%"><img src="docs/screenshots/remote.png" alt="The Remote: Screen off in the top bar, a D-pad, icon keys for Back, Home, Menu, mute and volume, a dock of four TV apps, and typing on the TV with a button to clear its text box"></td>
    <td width="25%"><img src="docs/screenshots/debloat.png" alt="Debloat: the TV's model on top, then the apps it will turn off, each with its package name"></td>
    <td width="25%"><img src="docs/screenshots/install.png" alt="Updating Netflix on the TV from an APK on the phone"></td>
  </tr>
  <tr>
    <td align="center"><sub>Your TV at a glance</sub></td>
    <td align="center"><sub>Control the TV, dock your apps</sub></td>
    <td align="center"><sub>Review every app Debloat turns off</sub></td>
    <td align="center"><sub>Install apps</sub></td>
  </tr>
  <tr>
    <td><img src="docs/screenshots/send.png" alt="Sending a video from the phone to the TV's Movies folder"></td>
    <td><img src="docs/screenshots/tweaks.png" alt="TV tweaks: ad-blocking Private DNS on, faster animations, clear caches, screenshot, and Another home screen to install a launcher"></td>
    <td><img src="docs/screenshots/home.png" alt="Making Projectivy Launcher the home screen: the confirm names both launchers"></td>
    <td><img src="docs/screenshots/advanced.png" alt="Advanced: CPU and RAM by app, the TV's network connection, and the apps' open connections"></td>
  </tr>
  <tr>
    <td align="center"><sub>Send files to the TV</sub></td>
    <td align="center"><sub>Block ads, tweak the TV, add a launcher</sub></td>
    <td align="center"><sub>Set default launcher</sub></td>
    <td align="center"><sub>Advanced: what is using the CPU, RAM and network</sub></td>
  </tr>
</table>

<sub>Rendered from the app's screenshot tests with sample data. Remote and the Tweaks launcher row are drawn to match the app, and Advanced joins parts of its longer page.</sub>

## Get it

- **GitHub Releases.** Download `Tellyscope-<version>.apk` from the [latest release](../../releases/latest) and open it on your phone. You may need to allow installs from your browser or file manager first. Pre-releases marked `-alpha` are test builds: they install as a separate app next to the regular one, with the same name and icon.
- **Obtainium.** To get updates automatically, tap the Obtainium badge above on your phone, or add this repository's GitHub address in [Obtainium](https://github.com/ImranR98/Obtainium). Turn on **Include prereleases** only if you want the alpha builds.
- **Orion Store.** Tellyscope is in the [Orion Store](https://github.com/RookieEnough/Orion-Store) catalog: tap the Orion Store badge above on your phone, or search for Tellyscope in the app. Orion installs it from this project's GitHub releases and tells you when there's an update.

Tellyscope needs a phone on Android 8.0 or newer, on the same Wi-Fi as the TV, and a TV with developer options turned on ([how](#setting-up-your-tv)).

## Is it safe?

- **Small and private.** Under 3 MB. No ads, no tracking, no accounts. It makes two internet requests: one fetches the debloat lists from this project's GitHub, or from your own fork if you point it there; the other asks GitHub every 30 minutes whether a newer version is out, and you can turn it off in Settings. Everything else stays on your Wi-Fi, except one lookup: the scan asks your phone's DNS server for the name of each TV it finds, which leaves your Wi-Fi only if that server is outside it, such as Private DNS on the phone. Turning on Private DNS changes where the *TV* looks up addresses, not what the app sends.
- **Reversible.** Apps that came with the TV are only ever disabled, never uninstalled, so nothing is deleted. Apps you installed yourself can also be uninstalled, after a confirm that says it can't be undone. Every change goes into History, all but Uninstall with an Undo, and **Restore everything** turns back on all it turned off.
- **Protected list.** Apps the TV needs to start, update, stay on Wi-Fi, show its home screen or take input can't be stopped or disabled. They sit in their own **System** section under Apps. There is no override. Apps a TV's list only says to keep can still be changed, after a warning in the confirm.
- **You see it first.** Debloat shows every app it will turn off, and the confirm names each one before anything runs.

The full rules are under [Safety rules](#safety-rules).

## What it does

Tellyscope is a phone app that talks to your Android TV, Google TV or Fire TV over the network using ADB, the same debugging protocol developers use.

- **Find and connect.** It looks for TVs on your Wi-Fi, or you add one by IP address. Classic network debugging (port 5555) and Android 11+ wireless debugging with a 6-digit pairing code both work. **Add your TV** walks through turning debugging on one step at a time, with the menu names for Google TV, Android TV, Fire TV, Xiaomi or XGIMI, and general steps for any other TV, then lists the TVs it found. Each TV opens its own screen: the pairing code, or the Allow box with this phone's key to check against the one the TV shows. The first launch opens a short welcome with the three setup steps, and on Android 17 or newer asks for the local-network permission it needs to reach the TV.
- **Remembers your TVs.** They reconnect on their own after a restart of the app or the TV. You can rename a TV (the name stays on your phone) or forget it. The **⋮** menu shows which TV it's for on top, then this TV's items and the app's in two blocks.
- **How it's doing.** Every tab of a TV's page opens with the TV itself: its name, maker and model, and a one-line summary such as "Running fine" or "Storage almost full". Scroll, and its icon and name shrink and slide up into the top bar, where a Connected or Offline badge joins them. On Overview, below that is a chip per TV to switch between them, then a **+ Device** chip. Then comes free space as a bar, with CPU, RAM, temperature and network speed. When a TV is offline, it shows the storage from the last time Tellyscope saw it. Its address and how long it's been on are under Advanced.
- **The main jobs up front.** Big **Install app**, **Debloat** and **Send file** tiles sit right under the vitals. Debloat shows a count while the TV's list still has apps on, or while apps Tellyscope turned off are back on, often after a TV update. When there's nothing left, it opens Apps instead. Below the tiles, one line says how many apps on the TV's list are off. If any came back on, a red line says so. Below that are the TV's Android version, when it last updated, and **Check for updates**, which opens the TV's own update screen. The Apps tab has an **Install app** button, and Files has **Upload files**.
- **Advanced.** Every number in one place: memory, storage, CPU, temperature, uptime and screen state, CPU and RAM by app, processes, network and data used, and the TV's address.
- **Apps.** Every app on the TV with its real name and icon, under three buttons: **Running** (what's using memory now), **Installed** (only the apps you put on the TV) and **Built-in** (what came with it). The buttons stay pinned under the bar as you scroll. Stop, disable or turn back on any app that isn't protected; each button says what it will do and asks first. Apps you installed yourself also offer **Uninstall**, which asks in a dialog that names the app and says it can't be undone. On Running and Built-in, the protected apps sit last under **System**, which folds away with a tap and stays folded until you open it again. **Search apps** sits under the buttons and pins with them. Tap it and the field moves to the top, the TV and the buttons step aside, and every matching app shows in one list. ← or Back brings them back.
- **Another home screen.** On a TV with the Play Store whose Home is still its own launcher, Apps shows a card that offers a better home screen, and Tweaks keeps **Choose one** after **Not now** puts the card away for that TV. Neither shows once Home opens a launcher you added. Tellyscope isn't affiliated with any of the launchers it recommends. Pick one of four recommended launchers (Projectivy, AT4K, Dispatch or the open-source FLauncher), and Tellyscope opens its Play Store page on the TV, waits for the install and then offers to make it Home. If the Store doesn't open, it says so and offers another launcher or a retry. With a second launcher such as Projectivy installed, its row in Apps offers **Set as default launcher**. Tellyscope makes it Home, checks that Home really opens it, and only then disables the old launcher, so the TV can't switch back by itself. The one exception: a stock launcher that outranks the rest, e.g. Android TV Home, is turned off first and turned back on if Home still doesn't open the new one. One Undo in History turns the old launcher back on and makes it Home again. Later, the new launcher's row offers **Switch home back to** the one it replaced, or the TV's own if there was no switch: it's turned back on if it's off and made Home, and the added launcher stays, free to uninstall. For a launcher that keeps Home with its own accessibility service, such as Projectivy (the ones marked in [`launchers.json`](app/src/main/assets/launchers.json)), Set as default then asks separately whether to turn that service on, for TVs whose Home button opens their own launcher whatever the default is: **Turn it on** or **Switch without it**. Without it, the TV's page says Home may still open the TV's own launcher, with **Got it** and **Turn on**, and the launcher's row keeps **Turn on**. Undo and Switch home back turn it off again, and any other accessibility services stay as they were. The added launcher on Home also offers **Uninstall**: its confirm lists the steps, then Tellyscope switches Home back (turning the service off) and uninstalls it only if that worked. Only the launcher on Home is protected; another one you installed, such as FLauncher while Projectivy is Home, can be disabled or uninstalled. Settings and other apps that only answer Home as a fallback are never offered as launchers. On Google TV, the Apps row and the Live tab are part of the old launcher and go with it.
- **Debloat.** It finds your TV among 300+ tested clean-up lists (adapted from [cleanyourtv](https://github.com/Gathin23/cleanyourtv)) and opens on every app it will turn off, with the model on top. With no list for the model, it shows apps from the maker's other lists, none ticked. With none for the maker either, you tick the TV's own apps by hand; the ones it needs aren't shown. **Not this TV?** searches every list. Your pick is remembered, and the TV's page then says how many of the list's apps are still on. Apps are pre-ticked only when the TV runs the Android version the list was tested on; untick anything you use, and you confirm before anything is disabled. If you'd rather not trust our lists, point the app at your own fork of them under **Advanced** in Settings (see [CONTRIBUTING](CONTRIBUTING.md#using-your-own-lists)).

  <img src="docs/screenshots/clean-home.jpg" alt="A TV home screen after debloating: the inputs and the user's own apps, with no ads or suggestions" width="100%">

  <sub>A TV after Tellyscope.</sub>

- **History.** Every change is listed with an Undo, and **Restore everything** turns back on what Tellyscope turned off. Undoing a **Turn on** asks first, since it disables the app again. History is kept per TV, across restarts.
- **Install apps.** Pick an APK, several split APKs or an `.apks`/`.xapk`/`.apkm` bundle on your phone. Before installing, Tellyscope shows the app's name, version and size and says whether it's new, an update, a reinstall or an older version. It won't install an older version over a newer one unless you tick the box. If the TV refuses, you get the reason in plain words.
- **TV tweaks.** **Block ads with Private DNS** sends the TV's address lookups to an ad-blocking DNS provider (AdGuard by default, or any other you type in). Tellyscope then checks the TV can still look addresses up, and puts the old setting back if not. It needs Android 9 or newer. **Faster animations** makes menus move at twice the speed. Both land in History with an Undo. **Clear caches** frees space by clearing every app's temporary files; app data and sign-ins stay. **Take screenshot** saves what the TV shows to your phone.
- **Who has access.** ADB lets any allowed computer do everything Tellyscope can, so the TV's page warns, above its stats, when a computer other than this phone is connected, with **It's mine** for your own. **Who has access** in the **⋮** menu lists who is connected now and every computer the TV allows, with its key fingerprint and roughly when it was last seen. The TV doesn't say which key is behind which connection, so Tellyscope never pairs them up. **Remove all other access** clears the TV's allowed list, then reconnects so the TV asks to allow this phone again; on TVs that won't take the request, it opens Developer options and walks you through **Revoke USB debugging authorisations**. A computer that is already connected keeps its connection until it disconnects or the TV restarts, so if one is still there Tellyscope says so and offers **Check again** and **Restart the TV**, which waits for the TV to come back and checks once more. It says "Only this phone has access" only after checking the TV again.
- **Connected devices.** The Overview lists what's paired with or plugged into the TV: Bluetooth remotes and headphones, USB keyboards and drives with their free space, HDMI-CEC devices such as a soundbar, and where the sound goes now.
- **Connection requests.** When another device asks to debug the TV, Tellyscope shows the TV's "Allow debugging?" prompt on your phone, with the device's key fingerprint, so you can **Allow** or **Deny** it without the remote. **Always allow this device** appears when the TV's own prompt offers it.
- **Files.** Send a file from your phone into the TV's Download or Movies folder, or save one from there to your phone. Two buttons under the TV pick the folder, and stay pinned under the bar as you scroll. An empty folder says so in the middle of the page. A file with the same name gets a " (1)" instead of being overwritten, and a cancelled copy leaves nothing half-written behind.
- **Remote.** A D-pad and icon keys for Back, Home, Menu, mute and volume, laid out like Google TV's phone remote, plus typing on the TV. The backspace button next to Send empties the TV's text box and the one on your phone. Each key gives a short tick; **Vibrate on key press** in Settings turns it off. The D-pad's centre pauses a playing video. **Add to dock** puts up to four of the TV's apps under the volume keys, each one tap from opening on the TV. Long-press one to change or remove it, or keep holding and move it to reorder. An app uninstalled from the TV leaves the dock by itself, and a snackbar says so. The restart button in the top bar asks first, then waits for the TV to come back: **Hide** keeps the wait in the top bar, and after a minute **Retry** waits another one. **Screen off** in the top bar turns off the TV's picture while Tellyscope stays connected, say to listen to music; any key or closing the Remote turns it back on. It needs Android 13 or older.

## Setting up your TV

1. On the TV, open **About** in Settings and press OK on **Build** (Fire TV: your device's name) 7 times, until it says you're a developer.
2. In **Developer options**, turn on **USB debugging** (Fire TV: **ADB debugging**), and **Network debugging** if your TV lists it. Newer TVs may show **Wireless debugging** instead; turn that on too.
3. Put your phone on the same Wi-Fi as the TV and open Tellyscope. Pick your TV's brand under **Add your TV**, and tap **Connect** when your TV shows up under **Find your TV**.
4. The TV asks "Allow USB debugging?". Check its key matches the one Tellyscope shows, tick **Always allow from this computer**, then press **Allow**. With wireless debugging, Tellyscope asks for the 6-digit pairing code the TV shows instead.

The menu paths differ by brand; the app shows the exact steps for yours.

## Safety rules

Debloating a TV can break it if done carelessly. Tellyscope is built around these rules, and contributions must keep them:

1. **Disable what came with the TV, never uninstall it.** Tellyscope uses `pm disable-user`, which is reversible from the app or with `pm enable`. Uninstall is offered only for apps `pm list packages -3` lists as installed by the user, and runs plain `pm uninstall` after a confirm that says it can't be undone. Tellyscope never runs `pm uninstall --user 0`, the form that removes a TV's own apps.
2. **Record before changing.** Before the first change on a TV, Tellyscope notes which apps were already off, so **Restore everything** turns back on only what Tellyscope turned off. Every change goes into the TV's history with an Undo. The history lives on the phone and is deleted if you uninstall Tellyscope; a disabled app still shows in the Apps list with **Turn on**.
3. **Protected list.** Packages the TV needs to boot, install its updates, stay on Wi-Fi, show web pages, show the home screen or accept input are on a protected list. Tellyscope refuses to stop or disable them, and there is no override in the app. A TV's list can add keeps: those only warn in the confirm, and can be overridden. If the TV doesn't say which app is its home screen, Tellyscope refuses to stop or disable anything. The one exception is switching home screens: the old launcher is disabled only after another launcher has become Home and Home has been checked to open it; a stock launcher that outranks the rest, such as Android TV Home, is turned off first and turned back on if Home still doesn't open the new one. The current home screen itself never can be stopped or disabled; one you installed can be uninstalled, after Home has gone back to the TV's own. A TV's suggested list never protects fewer apps; its keeps add a warning, not a lock.
4. **No root, no exploits.** Only what ADB allows on a stock TV.
5. **Nothing left on the TV.** Tellyscope copies two small helpers to `/data/local/tmp`. To show real app names and icons, the label helper runs once and is deleted; it only reads through PackageManager: no writes, no permissions, no network. While the Remote is open, the key helper keeps running to send keys and text, empty the TV's text box, and turn the screen off and on; it is deleted when the Remote closes.

## Project layout

| Module | What it is |
|---|---|
| `:app` | The Android phone app. Kotlin, Jetpack Compose, Material 3 Expressive. Package `app.tellyscope`. |
| `:adb` | Pure Kotlin/JVM library on top of [Kadb](https://github.com/flyfishxu/Kadb): connecting, pairing and running commands on a TV. Unit-testable without a device. |
| `app/src/helper` | The label helper: plain Java, built into a dex in the app's assets and run on the TV with `app_process`. |
| `:cli` | Desktop smoke test for `:adb` against a real TV (`./gradlew :cli:run --args="stats <tv-ip>"`). Not shipped. |

## Building

Requirements:

- **JDK 25.** Kadb ships Java 25 bytecode, so `:adb` tests need a Java 25 runtime. `:adb` itself still compiles to Java 17 bytecode so the app can use it.
- **Android SDK** with platform 37. Set `ANDROID_HOME` or put `sdk.dir=...` in `local.properties`.

```sh
./gradlew build          # assemble, unit tests and lint
./gradlew :app:installDebug
```

The app supports phones on Android 8.0 (API 26) and newer.

## Contributing

See [CONTRIBUTING.md](CONTRIBUTING.md). If you have an Android TV, a [device report](../../issues/new?template=device_report.yml) helps even if nothing worked.

## Why the name?

Tellyscope is named after Wallace's [Tellyscope](https://wallaceandgromit.fandom.com/wiki/The_Tellyscope), a contraption for changing the channel without getting up. This one is a bit safer.

## License

Tellyscope is licensed under the [GNU General Public License v3.0](LICENSE). Third-party credits are in [NOTICE](NOTICE).
