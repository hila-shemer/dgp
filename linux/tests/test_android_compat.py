import shutil
import subprocess
import tempfile
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


def test_python_encrypt_java_decrypt(android_export_blob):
    plaintext = android_export_blob["plaintext"]
    pin = android_export_blob["pin"]

    py_blob = encrypt_export(plaintext, pin)

    with tempfile.NamedTemporaryFile(
        mode="w", suffix=".txt", delete=False, dir=linux_dir
    ) as bf:
        bf.write(py_blob)
        blob_file = bf.name

    with tempfile.NamedTemporaryFile(
        suffix=".txt", delete=False, dir=linux_dir
    ) as of:
        out_file = of.name

    subprocess.run(
        [
            "java",
            "-cp",
            "build/javafixture",
            "AndroidExportFixture",
            "decrypt",
            pin,
            blob_file,
            out_file,
        ],
        check=True,
        cwd=linux_dir,
    )

    result = Path(out_file).read_text(encoding="utf-8")
    assert result == plaintext
