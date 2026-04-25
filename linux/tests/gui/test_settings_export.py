from __future__ import annotations
import subprocess
from PyQt6.QtWidgets import QPushButton
from dgp.exportcrypto import decrypt_export
from dgp.service import serialize_services, parse_services
from dgp import store
from tests.gui._helpers import make_window


def _make_settings(window=None):
    from dgp.gui.settings import SettingsDialog
    return SettingsDialog(parent=window) if window else SettingsDialog()


def _find_btn(dlg, text):
    return next(b for b in dlg.findChildren(QPushButton) if b.text() == text)


def test_export_to_file_round_trip(qapp, seeded_services, dgp_config_dir, stub_dialogs, tmp_path):
    out_path = tmp_path / "out.txt"
    stub_dialogs.save_filename = (str(out_path), "")
    window = make_window(seed="s")
    dlg = _make_settings(window)
    dlg._export_pin.setText("p")
    _find_btn(dlg, "Export to file").click()

    blob = out_path.read_text(encoding="utf-8").strip()
    plaintext = decrypt_export(blob, "p")
    assert plaintext is not None
    services = parse_services(plaintext)
    exported_names = {s.name for s in services}
    expected_names = {s.name for s in seeded_services}
    assert exported_names == expected_names


def test_export_empty_pin_rejected(qapp, dgp_config_dir, seeded_services, stub_dialogs):
    dlg = _make_settings()
    dlg._export_pin.setText("")
    _find_btn(dlg, "Export to file").click()

    assert any(c[0] == "warning" for c in stub_dialogs.calls)
    # getSaveFileName should never have been called
    assert not any(c[0] == "getSaveFileName" for c in stub_dialogs.calls)


def test_export_to_clipboard_uses_wl_copy(
    qapp, seeded_services, dgp_config_dir, stub_dialogs, stub_clipboard_subproc
):
    dlg = _make_settings()
    dlg._export_pin.setText("pin")
    _find_btn(dlg, "Copy to clipboard").click()

    assert stub_clipboard_subproc.calls[0]["cmd"] == ["wl-copy"]
    assert isinstance(stub_clipboard_subproc.calls[0]["kwargs"]["input"], bytes)
    assert not any(c[0] == "warning" for c in stub_dialogs.calls)


def test_export_to_clipboard_falls_back_to_xclip(
    qapp, seeded_services, dgp_config_dir, stub_dialogs, stub_clipboard_subproc
):
    stub_clipboard_subproc.fnf_commands = {"wl-copy"}
    dlg = _make_settings()
    dlg._export_pin.setText("pin")
    _find_btn(dlg, "Copy to clipboard").click()

    # First call was wl-copy (which failed), second is xclip
    assert stub_clipboard_subproc.calls[1]["cmd"][0] == "xclip"
    assert not any(c[0] == "warning" for c in stub_dialogs.calls)


def test_export_to_clipboard_no_tools_warns(
    qapp, seeded_services, dgp_config_dir, stub_dialogs, stub_clipboard_subproc
):
    stub_clipboard_subproc.fnf_commands = {"wl-copy", "xclip"}
    dlg = _make_settings()
    dlg._export_pin.setText("pin")
    _find_btn(dlg, "Copy to clipboard").click()

    assert any(c[0] == "warning" for c in stub_dialogs.calls)
