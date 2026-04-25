"""Phase 9 — CLI entrypoint tests for dgp.gui.app.main()"""
from __future__ import annotations
import sys
import pytest


def test_help_exits_zero_without_qt(monkeypatch, capsys):
    monkeypatch.setitem(sys.modules, "PyQt6.QtWidgets", None)
    with pytest.raises(SystemExit) as exc_info:
        from dgp.gui import app as app_mod
        app_mod.main(["--help"])
    assert exc_info.value.code == 0
    out = capsys.readouterr().out
    assert "usage:" in out


def test_unknown_arg_exits_two():
    from dgp.gui import app as app_mod
    with pytest.raises(SystemExit) as exc_info:
        app_mod.main(["--bogus"])
    assert exc_info.value.code == 2


def test_seed_file_passed_to_window(qapp, dgp_config_dir, monkeypatch):
    from dgp.gui import app as app_mod

    captured = {}

    class FakeMainWindow:
        def __init__(self, seed_file=None, account_file=None, parent=None):
            captured["seed_file"] = seed_file
            captured["account_file"] = account_file

        def show(self):
            pass

    class FakeQApp:
        def __init__(self, argv):
            pass

        def exec(self):
            return 0

    monkeypatch.setattr("PyQt6.QtWidgets.QApplication", FakeQApp)
    monkeypatch.setattr("dgp.gui.mainwindow.MainWindow", FakeMainWindow)

    result = app_mod.main(["--seed-file", "/tmp/x", "--account-file", "/tmp/y"])

    assert captured.get("seed_file") == "/tmp/x"
    assert captured.get("account_file") == "/tmp/y"
    assert result == 0
