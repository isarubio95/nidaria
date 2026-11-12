#!/usr/bin/env python3
"""Maneja la app en el emulador (o en un móvil conectado) desde la terminal.

  tools/ui.py tap "Crear cuenta"     toca el elemento con ese texto
  tools/ui.py tap 360 900            toca en esas coordenadas (píxeles)
  tools/ui.py type "hola"            escribe en el campo con el foco
  tools/ui.py key tab|enter|back     pulsa una tecla
  tools/ui.py wait "Tienda" [seg]    espera a que aparezca un texto
  tools/ui.py texts                  lista los textos en pantalla
  tools/ui.py shot fichero.png       guarda una captura
  tools/ui.py burst prefijo 8 0.2    ocho capturas seguidas, para animaciones
  tools/ui.py swipe x1 y1 x2 y2 [ms] arrastra el dedo

Los elementos se buscan por su texto o por su descripción de
accesibilidad, así que los guiones no dependen de dónde caiga cada cosa.
El volcado de Android no ve la franja inferior de la pantalla (la de la
barra de navegación): lo que quede ahí hay que tocarlo por coordenadas.
"""
import os
import re
import shutil
import subprocess
import sys
import tempfile
import time
import xml.etree.ElementTree as ET

ADB = os.path.join(os.environ.get("ANDROID_HOME", os.path.expanduser("~/Android/Sdk")), "platform-tools", "adb")
KEYS = {"tab": "61", "enter": "66", "back": "4", "home": "3", "del": "67"}


def adb(*args, binary=False):
    out = subprocess.run([ADB, *args], capture_output=True, check=False).stdout
    return out if binary else out.decode("utf-8", "replace")


def nodes():
    """Elementos visibles: (texto, descripción, x, y) con el centro en píxeles."""
    for _ in range(4):
        raw = adb("exec-out", "uiautomator", "dump", "/dev/tty")
        start = raw.find("<?xml")
        end = raw.rfind("</hierarchy>")
        if start >= 0 and end > start:
            break
        time.sleep(1)
    else:
        return []
    found = []
    for node in ET.fromstring(raw[start:end + len("</hierarchy>")]).iter("node"):
        box = re.match(r"\[(\d+),(\d+)\]\[(\d+),(\d+)\]", node.get("bounds", ""))
        if not box:
            continue
        x1, y1, x2, y2 = map(int, box.groups())
        found.append((node.get("text", ""), node.get("content-desc", ""), (x1 + x2) // 2, (y1 + y2) // 2))
    return found


def shot(path):
    """Captura la pantalla. En el emulador la hace su consola, que tarda
    décimas; en un móvil real, `screencap`."""
    if "emulator-" in adb("devices"):
        with tempfile.TemporaryDirectory() as folder:
            adb("emu", "screenrecord", "screenshot", folder)
            files = os.listdir(folder)
            if files:
                shutil.move(os.path.join(folder, files[0]), path)
                return
    with open(path, "wb") as out:
        out.write(adb("exec-out", "screencap", "-p", binary=True))


def find(label):
    exact = [n for n in nodes() if label in (n[0], n[1])]
    if exact:
        return exact[0]
    loose = [n for n in nodes() if label.lower() in n[0].lower() or label.lower() in n[1].lower()]
    return loose[0] if loose else None


def main(argv):
    if not argv:
        print(__doc__)
        return 1
    command, rest = argv[0], argv[1:]
    if command == "tap":
        if len(rest) == 2 and all(part.isdigit() for part in rest):
            x, y = rest
        else:
            node = find(rest[0])
            if not node:
                print(f"No encuentro «{rest[0]}» en pantalla.", file=sys.stderr)
                return 2
            x, y = str(node[2]), str(node[3])
        adb("shell", "input", "tap", x, y)
    elif command == "type":
        adb("shell", "input", "text", rest[0].replace(" ", "%s"))
    elif command == "key":
        adb("shell", "input", "keyevent", KEYS.get(rest[0], rest[0]))
    elif command == "swipe":
        adb("shell", "input", "swipe", *rest)
    elif command == "wait":
        limit = time.time() + float(rest[1] if len(rest) > 1 else 30)
        while time.time() < limit:
            if find(rest[0]):
                return 0
            time.sleep(1)
        print(f"«{rest[0]}» no ha aparecido.", file=sys.stderr)
        return 2
    elif command == "texts":
        for text, desc, x, y in nodes():
            if text or desc:
                print(f"{x:5d} {y:5d}  {text or '(' + desc + ')'}")
    elif command == "shot":
        shot(rest[0])
    elif command == "burst":
        # burst prefijo cuántas [segundos entre capturas]: para ver animaciones.
        gap = float(rest[2]) if len(rest) > 2 else 0.2
        for index in range(int(rest[1])):
            shot(f"{rest[0]}{index + 1:02d}.png")
            time.sleep(gap)
    else:
        print(__doc__)
        return 1
    return 0


if __name__ == "__main__":
    sys.exit(main(sys.argv[1:]))
