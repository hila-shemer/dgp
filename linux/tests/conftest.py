import shutil
import subprocess
import pytest
from pathlib import Path
from dgp.service import serialize_services, new_service

linux_dir = Path(__file__).parent.parent


@pytest.fixture(scope="session")
def android_export_blob(tmp_path_factory):
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

    work_dir = tmp_path_factory.mktemp("android_export")
    plaintext_file = work_dir / "plaintext.txt"
    plaintext_file.write_text(plaintext, encoding="utf-8")
    blob_file = work_dir / "blob.txt"

    subprocess.run(
        [
            "java",
            "-cp",
            "build/javafixture",
            "AndroidExportFixture",
            "encrypt",
            "hunter2",
            str(plaintext_file),
            str(blob_file),
        ],
        check=True,
        cwd=linux_dir,
    )

    blob = blob_file.read_text(encoding="utf-8").strip()
    return {"blob": blob, "plaintext": plaintext, "pin": "hunter2"}
