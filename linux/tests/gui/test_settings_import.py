from __future__ import annotations
from PyQt6.QtWidgets import QPushButton
from dgp.exportcrypto import encrypt_export
from dgp.service import DgpService, new_service, serialize_services, parse_services
from dgp import store
from tests.gui._helpers import make_window


def _make_settings(window=None):
    from dgp.gui.settings import SettingsDialog
    return SettingsDialog(parent=window) if window else SettingsDialog()


def _find_btn(dlg, text):
    return next(b for b in dlg.findChildren(QPushButton) if b.text() == text)


def _make_blob(services, pin):
    return encrypt_export(serialize_services(services), pin)


def test_import_from_file_decrypts_and_merges(qapp, dgp_config_dir, stub_dialogs, tmp_path):
    svc_a = new_service("alpha", type="alnum")
    svc_b = new_service("beta", type="xkcd")
    blob = _make_blob([svc_a, svc_b], "pin")
    import_file = tmp_path / "import.txt"
    import_file.write_text(blob, encoding="utf-8")
    stub_dialogs.open_filename = (str(import_file), "")

    dlg = _make_settings()
    dlg._import_pin.setText("pin")
    _find_btn(dlg, "Import from file").click()

    names = {s.name for s in store.read_services()}
    assert "alpha" in names
    assert "beta" in names


def test_import_wrong_pin_shows_error(qapp, dgp_config_dir, stub_dialogs, tmp_path):
    svc = new_service("svc", type="alnum")
    blob = _make_blob([svc], "correct")
    import_file = tmp_path / "import.txt"
    import_file.write_text(blob, encoding="utf-8")
    stub_dialogs.open_filename = (str(import_file), "")

    dlg = _make_settings()
    dlg._import_pin.setText("wrong")
    _find_btn(dlg, "Import from file").click()

    assert any(c[0] == "critical" for c in stub_dialogs.calls)
    assert store.read_services() == []


def test_import_corrupt_blob_shows_error(qapp, dgp_config_dir, stub_dialogs, tmp_path):
    import_file = tmp_path / "import.txt"
    import_file.write_text("notbase64!!", encoding="utf-8")
    stub_dialogs.open_filename = (str(import_file), "")

    dlg = _make_settings()
    dlg._import_pin.setText("anypin")
    _find_btn(dlg, "Import from file").click()

    assert any(c[0] == "critical" for c in stub_dialogs.calls)


def test_import_merges_by_id_preserves_existing(qapp, dgp_config_dir, stub_dialogs, tmp_path):
    svc_a = new_service("svc_a", type="alnum")
    svc_b = new_service("svc_b", type="alnum")
    store.write_services([svc_a])

    blob = _make_blob([svc_b], "pin")
    import_file = tmp_path / "import.txt"
    import_file.write_text(blob, encoding="utf-8")
    stub_dialogs.open_filename = (str(import_file), "")

    dlg = _make_settings()
    dlg._import_pin.setText("pin")
    _find_btn(dlg, "Import from file").click()

    names = {s.name for s in store.read_services()}
    assert "svc_a" in names
    assert "svc_b" in names


def test_import_merges_by_id_overwrites_match(qapp, dgp_config_dir, stub_dialogs, tmp_path):
    svc_v1 = DgpService(id="fixed-id", name="v1", type="alnum")
    store.write_services([svc_v1])

    svc_v2 = DgpService(id="fixed-id", name="v2", type="alnum")
    blob = _make_blob([svc_v2], "pin")
    import_file = tmp_path / "import.txt"
    import_file.write_text(blob, encoding="utf-8")
    stub_dialogs.open_filename = (str(import_file), "")

    dlg = _make_settings()
    dlg._import_pin.setText("pin")
    _find_btn(dlg, "Import from file").click()

    services = store.read_services()
    assert len(services) == 1
    assert services[0].id == "fixed-id"
    assert services[0].name == "v2"


def test_import_from_clipboard_uses_wl_paste(
    qapp, dgp_config_dir, stub_dialogs, stub_clipboard_subproc, tmp_path
):
    svc = new_service("clipsvc", type="alnum")
    blob = _make_blob([svc], "pin")
    stub_clipboard_subproc.set_paste(blob)

    dlg = _make_settings()
    dlg._import_pin.setText("pin")
    _find_btn(dlg, "Import from clipboard").click()

    assert stub_clipboard_subproc.calls[0]["cmd"] == ["wl-paste"]
    names = {s.name for s in store.read_services()}
    assert "clipsvc" in names


def test_import_from_clipboard_no_tools_warns(
    qapp, dgp_config_dir, stub_dialogs, stub_clipboard_subproc
):
    stub_clipboard_subproc.fnf_commands = {"wl-paste", "xclip"}
    dlg = _make_settings()
    _find_btn(dlg, "Import from clipboard").click()

    assert any(c[0] == "warning" for c in stub_dialogs.calls)


def test_import_plaintext_json(qapp, dgp_config_dir, stub_dialogs, tmp_path):
    svc = new_service("plainsvc", type="alnum")
    plain_file = tmp_path / "plain.json"
    plain_file.write_text(serialize_services([svc]), encoding="utf-8")
    stub_dialogs.open_filename = (str(plain_file), "")

    dlg = _make_settings()
    _find_btn(dlg, "Import plaintext JSON").click()

    names = {s.name for s in store.read_services()}
    assert "plainsvc" in names


def test_import_plaintext_invalid_shows_error(qapp, dgp_config_dir, stub_dialogs, tmp_path):
    bad_file = tmp_path / "bad.json"
    bad_file.write_text("{not json", encoding="utf-8")
    stub_dialogs.open_filename = (str(bad_file), "")

    dlg = _make_settings()
    _find_btn(dlg, "Import plaintext JSON").click()

    assert any(c[0] == "critical" for c in stub_dialogs.calls)


def test_import_calls_parent_reload(
    qapp, seeded_services, dgp_config_dir, stub_dialogs, tmp_path
):
    # seeded_services: github + email (visible) + oldsvc (archived, hidden) = 2 visible rows
    window = make_window(seed="s")
    assert window._list_widget.count() == 2

    dlg = _make_settings(window)

    svc_new = new_service("new-svc", type="alnum")
    blob = _make_blob([svc_new], "pin")
    import_file = tmp_path / "import.txt"
    import_file.write_text(blob, encoding="utf-8")
    stub_dialogs.open_filename = (str(import_file), "")
    dlg._import_pin.setText("pin")
    _find_btn(dlg, "Import from file").click()

    # 2 original (github, email) + 1 new = 3 visible rows
    assert window._list_widget.count() == 3
