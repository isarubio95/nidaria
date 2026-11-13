#!/usr/bin/env bash
# Emulador de Android para probar Nidaria sin ventana y sin aceleración
# por hardware (sirve aunque la virtualización esté apagada en la BIOS).
#
#   tools/emulator.sh create        crea el dispositivo virtual «nidaria»
#   tools/emulator.sh start         lo arranca y espera a que esté listo
#   tools/emulator.sh install       instala el APK de depuración y lo abre
#   tools/emulator.sh shot f.png    guarda una captura de pantalla
#   tools/emulator.sh stop          lo apaga
#
# Con la virtualización activada (/dev/kvm) arranca en segundos; sin ella
# tarda unos cuatro minutos y la app va despacio, pero funciona.
set -euo pipefail

export ANDROID_HOME="${ANDROID_HOME:-$HOME/Android/Sdk}"
if [ -z "${JAVA_HOME:-}" ]; then
  # El JDK 21 de ~/.jdks o, si no está, el que Gradle descarga solo.
  JAVA_HOME="$(ls -d "$HOME"/.jdks/jdk-21* "$HOME"/.gradle/jdks/*-21-* 2>/dev/null | grep -v '\.lock$' | head -1 || true)"
  export JAVA_HOME
fi
export PATH="$ANDROID_HOME/platform-tools:$ANDROID_HOME/emulator:$ANDROID_HOME/cmdline-tools/latest/bin:${JAVA_HOME:+$JAVA_HOME/bin:}$PATH"

AVD=nidaria
IMAGE="system-images;android-30;default;x86_64"
HERE="$(cd "$(dirname "$0")" && pwd)"
APK="$HERE/../app/build/outputs/apk/debug/app-debug.apk"
LOG="${TMPDIR:-/tmp}/nidaria-emulator.log"

set_ini() {
  local file="$HOME/.android/avd/$AVD.avd/config.ini" key="$1" value="$2"
  if grep -q "^$key *=" "$file"; then sed -i "s|^$key *=.*|$key=$value|" "$file"; else echo "$key=$value" >> "$file"; fi
}

case "${1:-start}" in
  create)
    sdkmanager "platform-tools" "emulator" "$IMAGE"
    echo no | avdmanager create avd -n "$AVD" -k "$IMAGE" -d pixel_5 --force
    # Pantalla pequeña y dibujo por software: lo más ligero que sigue
    # pareciendo un móvil.
    set_ini hw.lcd.width 720
    set_ini hw.lcd.height 1560
    set_ini hw.lcd.density 320
    set_ini hw.ramSize 4096
    set_ini hw.cpu.ncore 6
    set_ini hw.gpu.enabled yes
    set_ini hw.gpu.mode swiftshader_indirect
    set_ini hw.keyboard yes
    set_ini disk.dataPartition.size 4G
    ;;
  start)
    if adb devices | grep -q '^emulator-'; then
      echo "Ya hay un emulador en marcha."
    else
      accel=()
      [ -e /dev/kvm ] || accel=(-accel off)
      nohup emulator -avd "$AVD" -no-window -no-audio -no-boot-anim -no-snapshot \
        -gpu swiftshader_indirect -netdelay none -netspeed full "${accel[@]}" > "$LOG" 2>&1 &
      echo "Arrancando (registro en $LOG)…"
    fi
    adb wait-for-device
    until [ "$(adb shell getprop sys.boot_completed 2>/dev/null | tr -d '\r')" = "1" ]; do sleep 5; done
    # Sin animaciones del sistema ni avisos de «no responde»: en un
    # emulador lento estorban más que ayudan.
    adb shell settings put global window_animation_scale 0
    adb shell settings put global transition_animation_scale 0
    adb shell settings put global hide_error_dialogs 1
    adb shell settings put system screen_off_timeout 2147483647
    adb shell svc power stayon true
    adb shell input keyevent 82
    echo "Emulador listo."
    ;;
  install)
    adb install -r "$APK"
    # Compilar la app por adelantado la hace bastante más ágil sin KVM.
    adb shell cmd package compile -m speed -f com.nidaria.app > /dev/null
    adb shell am start -n com.nidaria.app/.MainActivity
    ;;
  shot)
    "$HERE/ui.py" shot "${2:?Falta el fichero de salida}"
    ;;
  stop)
    adb emu kill || true
    ;;
  *)
    sed -n '2,13p' "$0"
    exit 1
    ;;
esac
