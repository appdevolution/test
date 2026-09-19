from pathlib import Path
from urllib.request import urlretrieve
import math
import random
import struct
import wave

ROOT = Path(__file__).resolve().parents[1]
FONT_DIR = ROOT / "app/src/main/assets/fonts"
LICENSE_DIR = ROOT / "app/src/main/assets/licenses"
RAW_DIR = ROOT / "app/src/main/res/raw"

FONT_DIR.mkdir(parents=True, exist_ok=True)
LICENSE_DIR.mkdir(parents=True, exist_ok=True)
RAW_DIR.mkdir(parents=True, exist_ok=True)

downloads = {
    FONT_DIR / "CourierPrime-Regular.ttf":
        "https://raw.githubusercontent.com/google/fonts/main/ofl/courierprime/CourierPrime-Regular.ttf",
    FONT_DIR / "SpecialElite-Regular.ttf":
        "https://raw.githubusercontent.com/google/fonts/main/apache/specialelite/SpecialElite-Regular.ttf",
    FONT_DIR / "EBGaramond-Regular.ttf":
        "https://raw.githubusercontent.com/google/fonts/main/ofl/ebgaramond/EBGaramond%5Bwght%5D.ttf",
    FONT_DIR / "LibreBaskerville-Regular.ttf":
        "https://raw.githubusercontent.com/google/fonts/main/ofl/librebaskerville/LibreBaskerville%5Bwght%5D.ttf",
    LICENSE_DIR / "CourierPrime-OFL.txt":
        "https://raw.githubusercontent.com/google/fonts/main/ofl/courierprime/OFL.txt",
    LICENSE_DIR / "SpecialElite-LICENSE.txt":
        "https://raw.githubusercontent.com/google/fonts/main/apache/specialelite/LICENSE.txt",
    LICENSE_DIR / "EBGaramond-OFL.txt":
        "https://raw.githubusercontent.com/google/fonts/main/ofl/ebgaramond/OFL.txt",
    LICENSE_DIR / "LibreBaskerville-OFL.txt":
        "https://raw.githubusercontent.com/google/fonts/main/ofl/librebaskerville/OFL.txt",
}

for target, url in downloads.items():
    if not target.exists():
        print("Download:", target.name)
        urlretrieve(url, target)

SAMPLE_RATE = 22050

def write_wav(name, duration, generator):
    path = RAW_DIR / (name + ".wav")
    n = int(SAMPLE_RATE * duration)
    frames = bytearray()
    for i in range(n):
        t = i / SAMPLE_RATE
        sample = max(-1.0, min(1.0, generator(t, i, n)))
        frames += struct.pack("<h", int(sample * 32767))
    with wave.open(str(path), "wb") as wav:
        wav.setnchannels(1)
        wav.setsampwidth(2)
        wav.setframerate(SAMPLE_RATE)
        wav.writeframes(frames)

def click(seed, pitch=95.0, strength=0.82):
    rng = random.Random(seed)
    def gen(t, i, n):
        env = math.exp(-42.0 * t)
        noise = (rng.random() * 2.0 - 1.0) * 0.55
        body = math.sin(2 * math.pi * pitch * t) * 0.45
        metal = math.sin(2 * math.pi * (pitch * 4.1) * t) * 0.18
        return strength * env * (noise + body + metal)
    return gen

def space_sound(t, i, n):
    env = math.exp(-55.0 * t)
    return env * (math.sin(2 * math.pi * 70 * t) * 0.30)

def return_sound(t, i, n):
    env = math.exp(-6.5 * t)
    scrape = (random.Random(i * 19 + 7).random() * 2 - 1) * 0.13
    body = math.sin(2 * math.pi * (75 + 110 * t) * t) * 0.22
    return env * (scrape + body)

def bell_sound(t, i, n):
    env = math.exp(-4.5 * t)
    return env * (
        math.sin(2 * math.pi * 1280 * t) * 0.52 +
        math.sin(2 * math.pi * 1760 * t) * 0.20
    )

write_wav("key1", 0.075, click(11, 92, 0.83))
write_wav("key2", 0.078, click(17, 101, 0.78))
write_wav("key3", 0.072, click(23, 86, 0.86))
write_wav("backspace", 0.090, click(31, 68, 0.95))
write_wav("space", 0.060, space_sound)
write_wav("carriage_return", 0.36, return_sound)
write_wav("bell", 0.48, bell_sound)

print("Assets prepared.")
