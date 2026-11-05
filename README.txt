MULTIPLAY — Android source project
=================================
This is a native Kotlin Android project for a two-phone, offline Bluetooth Classic word guessing game.

IMPORTANT BUILD / TEST STATUS
- This environment does not contain the Android SDK or Gradle, so an APK could not be compiled or device-tested here.
- Open this project in Android Studio on a computer with Android SDK 35 and JDK 17, then Build > Build Bundle(s) / APK(s) > Build APK(s).
- Install the resulting app-debug.apk on both Android phones.
- Pair the phones first in Android Settings > Bluetooth.
- Open MULTIPLAY on phone 1 and choose Create game.
- On phone 2 choose Join paired phone and select phone 1.

Gameplay
- Each phone submits two secret words (3–12 A–Z letters).
- Each player guesses the other player's two words, one letter per turn.
- Correct letters reveal all matching positions; wrong guesses increment the counter.
- The first player to reveal both words wins the round.

Current implementation notes
- Bluetooth Classic RFCOMM connection; no internet is used.
- Android 12+ Bluetooth permissions are requested at runtime.
- Host accepts one connection; guest connects to a previously paired device.
- This is an initial source build, not a verified production release. The round-reset / loss handling and robust reconnection need further testing and refinement before public use.


ONLINE APK BUILD
===============
This ZIP includes a GitHub Actions workflow at:
.github/workflows/build-apk.yml

To build the APK online:
1. Create a GitHub account and a NEW empty repository.
2. Upload/extract the contents of this ZIP into the repository's main (root) level.
   The folders should include app/, build.gradle, settings.gradle, and .github/.
3. Open the repository's Actions tab.
4. Select "Build MULTIPLAY APK" and click "Run workflow" (or push the files to main).
5. When the run finishes successfully, open the run and download the
   "MULTIPLAY-debug-apk" artifact.
6. Extract the downloaded artifact ZIP. It contains app-debug.apk.

Build note:
This workflow builds a debug APK. It does not certify that Bluetooth gameplay
works on every phone. Test pairing and a full game on both devices before relying
on it.
