import shutil
import subprocess
import pytest
from pathlib import Path
from dgp.exportcrypto import decrypt_export, encrypt_export

pytestmark = pytest.mark.skipif(
    shutil.which("javac") is None, reason="JDK not available"
)

linux_dir = Path(__file__).parent.parent


def test_java_encrypt_python_decrypt(android_export_blob):
    result = decrypt_export(android_export_blob["blob"], android_export_blob["pin"])
    assert result == android_export_blob["plaintext"]


def test_python_encrypt_java_decrypt(android_export_blob, tmp_path):
    plaintext = android_export_blob["plaintext"]
    pin = android_export_blob["pin"]

    py_blob = encrypt_export(plaintext, pin)

    blob_file = tmp_path / "blob.txt"
    blob_file.write_text(py_blob, encoding="utf-8")
    out_file = tmp_path / "out.txt"

    subprocess.run(
        [
            "java",
            "-cp",
            "build/javafixture",
            "AndroidExportFixture",
            "decrypt",
            pin,
            str(blob_file),
            str(out_file),
        ],
        check=True,
        cwd=linux_dir,
    )

    result = out_file.read_text(encoding="utf-8")
    assert result == plaintext
