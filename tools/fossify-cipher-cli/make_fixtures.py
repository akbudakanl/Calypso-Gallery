#!/usr/bin/env python3
"""
CLI ile uretilen .bin dosyalarini Android tarafi icin test fixture'i olarak uretir.

Amac: "CLI'da sifrele -> telefonda ac" akisini regresyon testiyle sabitlemek.
Uretilen konteynerler kanonik FGVLTENC v1 formatindadir:
  - KEK'ten turetilen anahtarla AES-256-GCM sifreli header (AAD = cleartext prefix),
  - Tink AES256_GCM_HKDF uyumlu akis (AAD = fileId, 256 KiB segment).

Kullanim:
    cd tools/fossify-cipher-cli
    python make_fixtures.py

Cikti:
    app/src/test/resources/fixtures/cli/*.bin
    app/src/test/resources/fixtures/cli/manifest.json

NOT: Uretilen dosyalar deterministik DEGILDIR (IV, fileId, salt, noncePrefix ve dosya adi
rastgeledir). Bu yuzden bir kez uretilip repoya commit edilir; testler bu sabit
fixture'lari kullanir. manifest.json icindeki kek_hex, konteyner header'larini cozmek
icin kullanilan anahtari kaydeder.
"""

import hashlib
import json
import shutil
import struct
import sys
from pathlib import Path

SCRIPT_DIR = Path(__file__).resolve().parent
REPO_ROOT = SCRIPT_DIR.parents[1]
OUT_DIR = REPO_ROOT / "app" / "src" / "test" / "resources" / "fixtures" / "cli"
TMP_DIR = SCRIPT_DIR / "_fixture_tmp"

# Test icin sabit KEK. Android testi bu degeri manifest.json'dan okur.
KEK_HEX = (
    "00112233445566778899aabbccddeeff"
    "102132435465768798a9bacbdcedfe0f"
)
KEK = bytes.fromhex(KEK_HEX)


def _payloads():
    """Kenar durumu payload'lari: Tink akis segment sinirlarini kapsar."""
    return {
        # Tink bos duz metin yolu: 40B stream header + 16B tag = 56 bayt.
        "empty": (b"", 0),
        "one_byte": (b"\x00", 1),
        "block_minus1": (bytes(range(15)), 15),
        "block_exact": (bytes(range(16)), 16),
        "block_plus1": (bytes(range(17)), 17),
        # Ilk segment kapasitesi: 262144 - 40 - 16 = 262088 bayt.
        "first_segment_minus1": (bytes(i & 0xFF for i in range(262087)), 262087),
        "first_segment_exact": (bytes(i & 0xFF for i in range(262088)), 262088),
        "first_segment_plus1": (bytes(i & 0xFF for i in range(262089)), 262089),
        # Ikinci ve sonraki segment kapasitesi: 262144 - 16 = 262128 bayt.
        "second_segment_exact": (bytes((i * 7) & 0xFF for i in range(262128)), 262128),
        "two_full_segments": (bytes((i * 3 + 1) & 0xFF for i in range(524216)), 524216),
        # Coklu segment / streaming
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

    if len(KEK) != 32:
        print("[ERROR] KEK 32 bayt olmali")
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
        "version": 1,
        "algorithm": "AES-256-GCM header + Tink AES256_GCM_HKDF stream (cryptography)",
        "segment_size": 262144,
        "kek_hex": KEK_HEX,
        "files": {},
    }

    for name, (payload, _expected_len) in cases.items():
        source_name = f"{name}.jpg"
        source = TMP_DIR / source_name
        source.write_bytes(payload)

        produced = cv.encrypt_file(source, TMP_DIR, KEK)
        if produced is None:
            print(f"[ERROR] {name}: encrypt_file basarisiz")
            return 1

        data = produced.read_bytes()
        expected_stream = cv.expected_ciphertext_size(len(payload))
        actual_stream = len(data) - cv.FIXED_PREFIX_LENGTH - struct.unpack(">I", data[39:43])[0]
        if actual_stream != expected_stream:
            print(f"[ERROR] {name}: stream boyutu {actual_stream}, beklenen {expected_stream}")
            return 1

        target = OUT_DIR / f"{name}.bin"
        target.write_bytes(data)

        manifest["files"][f"{name}.bin"] = {
            "source_name": source_name,
            "plaintext_sha256": hashlib.sha256(payload).hexdigest(),
            "plaintext_len": len(payload),
            "expected_stream_len": expected_stream,
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
