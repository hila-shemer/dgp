import pytest
import sys


# Test 1: import smoke — must pass even without display
def test_import_dgp_gui_app():
    """import dgp.gui.app must succeed even without libxkbcommon."""
    import dgp.gui.app  # noqa: F401
    assert hasattr(dgp.gui.app, "main")


def _qt_available() -> bool:
    try:
        import PyQt6.QtWidgets  # noqa: F401
        return True
    except ImportError:
        return False


_skip_no_qt = pytest.mark.skipif(
    not _qt_available(),
    reason="PyQt6.QtWidgets not available (libxkbcommon.so.0 missing)",
)


@_skip_no_qt
def test_main_window_row_count(tmp_path, monkeypatch):
    """MainWindow shows correct row count for loaded services."""
    import os
    os.environ["QT_QPA_PLATFORM"] = "offscreen"
    from PyQt6.QtWidgets import QApplication
    from dgp.gui.mainwindow import MainWindow
    from dgp.service import new_service, serialize_services
    from dgp import store

    monkeypatch.setenv("DGP_CONFIG_DIR", str(tmp_path))
    services = [new_service("svc1"), new_service("svc2")]
    (tmp_path / "services.json").write_text(serialize_services(services))

    app = QApplication.instance() or QApplication([])
    w = MainWindow()
    assert w._list_widget.count() == 2


@_skip_no_qt
def test_generate_on_select(tmp_path, monkeypatch):
    """Clicking a service generates the correct password."""
    import os
    os.environ["QT_QPA_PLATFORM"] = "offscreen"
    from PyQt6.QtWidgets import QApplication
    from dgp.gui.mainwindow import MainWindow
    from dgp.service import new_service, serialize_services
    from dgp.engine import generate

    monkeypatch.setenv("DGP_CONFIG_DIR", str(tmp_path))
    svc = new_service("mysvc", type="alnum")
    (tmp_path / "services.json").write_text(serialize_services([svc]))

    app = QApplication.instance() or QApplication([])
    w = MainWindow()
    w._seed = "testseed"
    w._account = ""
    w._list_widget.setCurrentRow(0)

    expected = generate("testseed", "mysvc", "alnum", "")
    assert w._password_field.text() == expected
