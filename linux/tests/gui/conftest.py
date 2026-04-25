import pytest


@pytest.fixture(scope="session")
def qapp():
    """Singleton QApplication for the test session."""
    try:
        from PyQt6.QtWidgets import QApplication
    except ImportError:
        pytest.skip("PyQt6 not available")
    app = QApplication.instance() or QApplication([])
    yield app


@pytest.fixture
def dgp_config_dir(tmp_path, monkeypatch):
    """Isolated DGP config directory; sets DGP_CONFIG_DIR env var."""
    monkeypatch.setenv("DGP_CONFIG_DIR", str(tmp_path))
    yield tmp_path


@pytest.fixture
def seeded_services(dgp_config_dir):
    """Writes three services to the isolated config dir and yields the list.

    Services: github (alnum), email (xkcd, pinned=True), oldsvc (alnum, archived=True).
    """
    from dgp.service import new_service, serialize_services
    services = [
        new_service("github", type="alnum"),
        new_service("email",  type="xkcd",  pinned=True),
        new_service("oldsvc", type="alnum", archived=True),
    ]
    (dgp_config_dir / "services.json").write_text(serialize_services(services))
    yield services


class DialogStub:
    def __init__(self):
        self.calls: list = []
        self.input_text: tuple = ("", True)
        self.open_filename: tuple = ("", "")
        self.save_filename: tuple = ("", "")


@pytest.fixture
def stub_dialogs(monkeypatch):
    stub = DialogStub()

    def _get_text(parent, title, label, *args, **kwargs):
        stub.calls.append(("getText", title, label))
        return stub.input_text

    def _get_open(parent, caption, *args, **kwargs):
        stub.calls.append(("getOpenFileName", caption))
        return stub.open_filename

    def _get_save(parent, caption, *args, **kwargs):
        stub.calls.append(("getSaveFileName", caption))
        return stub.save_filename

    def _info(parent, title, text, *args, **kwargs):
        stub.calls.append(("information", title, text))

    def _warn(parent, title, text, *args, **kwargs):
        stub.calls.append(("warning", title, text))

    def _crit(parent, title, text, *args, **kwargs):
        stub.calls.append(("critical", title, text))

    monkeypatch.setattr("PyQt6.QtWidgets.QInputDialog.getText",        staticmethod(_get_text))
    monkeypatch.setattr("PyQt6.QtWidgets.QFileDialog.getOpenFileName", staticmethod(_get_open))
    monkeypatch.setattr("PyQt6.QtWidgets.QFileDialog.getSaveFileName", staticmethod(_get_save))
    monkeypatch.setattr("PyQt6.QtWidgets.QMessageBox.information",     staticmethod(_info))
    monkeypatch.setattr("PyQt6.QtWidgets.QMessageBox.warning",         staticmethod(_warn))
    monkeypatch.setattr("PyQt6.QtWidgets.QMessageBox.critical",        staticmethod(_crit))

    yield stub


class ClipboardStub:
    def __init__(self):
        self.calls: list = []
        self._paste_text: str | None = None
        self.fnf_commands: set = set()

    def set_paste(self, text: str) -> None:
        self._paste_text = text


@pytest.fixture
def stub_clipboard_subproc(monkeypatch):
    import subprocess
    stub = ClipboardStub()

    def _mock_run(cmd, *args, **kwargs):
        stub.calls.append({"cmd": list(cmd), "kwargs": kwargs})
        binary_name = cmd[0]
        if binary_name in stub.fnf_commands:
            raise FileNotFoundError(f"No such file or directory: '{binary_name}'")
        from unittest.mock import MagicMock
        result = MagicMock()
        if stub._paste_text is not None:
            result.stdout = stub._paste_text.encode("utf-8")
        else:
            result.stdout = b""
        return result

    monkeypatch.setattr(subprocess, "run", _mock_run)
    yield stub
