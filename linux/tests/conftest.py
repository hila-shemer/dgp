import shutil
import subprocess
import tempfile
import pytest
from pathlib import Path
from dgp.service import serialize_services, new_service

linux_dir = Path(__file__).parent.parent


@pytest.fixture(scope="session")
def android_export_blob():
    if shutil.which("javac") is None or shutil.which("java") is None:
        pytest.skip("JDK not available")

    Path(linux_dir / "build/javafixture").mkdir(parents=True, exist_ok=True)
    subprocess.run(
        [
            "javac",
            "-d",
            "build/javafixture",
            "tests/fixtures/AndroidExportFixture.java",
        ],
        check=True,
        cwd=linux_dir,
    )

    services = [
        new_service("github", type="alnum"),
        new_service("email", type="xkcd"),
    ]
    plaintext = serialize_services(services)

    with tempfile.NamedTemporaryFile(
        mode="w", suffix=".txt", delete=False, dir=linux_dir
    ) as pf:
        pf.write(plaintext)
        plaintext_file = pf.name

    with tempfile.NamedTemporaryFile(
        suffix=".txt", delete=False, dir=linux_dir
    ) as bf:
        blob_file = bf.name

    subprocess.run(
        [
            "java",
            "-cp",
            "build/javafixture",
            "AndroidExportFixture",
            "encrypt",
            "hunter2",
            plaintext_file,
            blob_file,
        ],
        check=True,
        cwd=linux_dir,
    )

    blob = Path(blob_file).read_text(encoding="utf-8").strip()
    return {"blob": blob, "plaintext": plaintext, "pin": "hunter2"}
