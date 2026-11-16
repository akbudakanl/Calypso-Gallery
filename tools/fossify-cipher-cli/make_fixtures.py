#!/usr/bin/env python3
"""
CLI ile uretilen .bin dosyalarini Android tarafi icin test fixture'i olarak uretir.

Amac: "CLI'da sifrele -> telefonda ac" akisini regresyon testiyle sabitlemek.
Android tarafi (CliFixtureCompatibilityTest.kt) bu dosyalari okuyup:
  - header' cozebiliyor,
  - Poly1305-AES MAC'i dogruluyor,
  - duz metni birebir elde ediyor
olmalidir.

Kullanim:
    cd tools/fossify-cipher-cli
    python make_fixtures.py

Cikti:
    app/src/test/resources/fixtures/cli/*.bin
    app/src/test/resources/fixtures/cli/manifest.json

NOT: Uretilen dosyalar deterministik DEGILDIR (IV ve dosya adi rastgeledir).
Bu yuzden bir kez uretilip repoya commit edilir; testler bu sabit
fixture'lari kullanir.
"""

import hashlib
import json
import shutil
import sys
from pathlib import Path

SCRIPT_DIR = Path(__file__).resolve().parent
REPO_ROOT = SCRIPT_DIR.parents[1]
OUT_DIR = REPO_ROOT / "app" / "src" / "test" / "resources" / "fixtures" / "cli"
TMP_DIR = SCRIPT_DIR / "_fixture_tmp"

# Test icin sabit master key. Android testi bu degeri manifest.json'dan okur.
MASTER_KEY_HEX = (
    "00112233445566778899aabbccddeeff"
    "102132435465768798a9bacbdcedfe0f"
)
MASTER_KEY = bytes.fromhex(MASTER_KEY_HEX)


def _payloads():
    """Kenar durumu payload'lari. Boyutlar Poly1305/streaming blok sinirlarina gore secilmistir."""
    return {
        # Poly1305 "hic blok yok" yolu (tag = s). En sik hata yeri.
        "empty": (b"", 0),
        # Kismi son blok
        "one_byte": (b"\x00", 1),
        # Tam blok siniri (16 bayt)
        "block_minus1": (bytes(range(15)), 15),
        "block_exact": (bytes(range(16)), 16),
        "block_plus1": (bytes(range(17)), 17),
        # Python chunk boyutu 64 KiB, Android tamponu 256 KiB
        "chunk_minus1": (bytes(i & 0xFF for i in range(65535)), 65535),
        "chunk_exact": (bytes(i & 0xFF for i in range(65536)), 65536),
        "chunk_plus1": (bytes(i & 0xFF for i in range(65537)), 65537),
        # Coklu chunk / streaming
        "multi_mb": (bytes((i * 31 + 7) & 0xFF for i in range(1_100_000)), 1_100_000),
    }


def _real_image_case():
    """Thumbnail yolunu (header icinde gomulu JPEG) test etmek icin gercek gorsel."""
    try:
        from PIL import Image
    except ImportError:
        print("  [i] Pillow yok, gercek gorsel fixture'i atlandi")
        return None

    import io

    buf = io.BytesIO()
    Image.new("RGB", (64, 48), (12, 200, 90)).save(buf, format="PNG")
    return buf.getvalue()


def main() -> int:
    sys.path.insert(0, str(SCRIPT_DIR))
    try:
        import cipher_vault as cv
    except ImportError as exc:
        print(f"[ERROR] cipher_vault import edilemedi: {exc}")
        print("Bu scripti tools/fossify-cipher-cli icinden calistirin.")
        return 1

    if len(MASTER_KEY) != 32:
        print("[ERROR] MASTER_KEY 32 bayt olmali")
        return 1

    shutil.rmtree(TMP_DIR, ignore_errors=True)
    TMP_DIR.mkdir(parents=True, exist_ok=True)
    OUT_DIR.mkdir(parents=True, exist_ok=True)

    cases = _payloads()
    real_image = _real_image_case()
    if real_image is not None:
        cases["real_image_with_thumbnail"] = (real_image, len(real_image))

    manifest = {
        "format": "FGVLTENC",
        "version": 3,
        "mac": "Poly1305-AES128 (restic / BouncyCastle Poly1305(AESEngine) uyumlu)",
        "master_key_hex": MASTER_KEY_HEX,
        "files": {},
    }

    for name, (payload, _expected_len) in cases.items():
        source_name = f"{name}.jpg"
        source = TMP_DIR / source_name
        source.write_bytes(payload)

        produced = cv.encrypt_file(source, TMP_DIR, MASTER_KEY)
        if produced is None:
            print(f"[ERROR] {name}: encrypt_file basarisiz")
            return 1

        data = produced.read_bytes()
        target = OUT_DIR / f"{name}.bin"
        target.write_bytes(data)

        manifest["files"][f"{name}.bin"] = {
            "source_name": source_name,
            "plaintext_sha256": hashlib.sha256(payload).hexdigest(),
            "plaintext_len": len(payload),
            "bin_sha256": hashlib.sha256(data).hexdigest(),
            "bin_len": len(data),
        }
        print(f"  [ok] {name}.bin  ({len(data)} bayt, duz metin {len(payload)} bayt)")

    (OUT_DIR / "manifest.json").write_text(
        json.dumps(manifest, indent=2, sort_keys=True) + "\n", encoding="utf-8"
    )

    shutil.rmtree(TMP_DIR, ignore_errors=True)
    print(f"\n{len(cases)} fixture uretildi -> {OUT_DIR}")
    print("Bu dosyalari repoya commit etmeyi unutma.")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
