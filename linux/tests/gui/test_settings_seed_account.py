from __future__ import annotations
import stat
from PyQt6.QtWidgets import QPushButton, QLineEdit
from PyQt6.QtCore import Qt

from dgp import store as dgp_store
from tests.gui._helpers import make_window


def _make_settings(window):
    from dgp.gui.settings import SettingsDialog
    return SettingsDialog(parent=window)


def _find_btn(dlg, text: str) -> QPushButton:
    return next(b for b in dlg.findChildren(QPushButton) if b.text() == text)


# --- Seed tab ---

def test_save_seed_writes_to_disk_with_0600(qapp, dgp_config_dir, stub_dialogs):
    w = make_window()
    dlg = _make_settings(w)
    dlg._seed_edit.setText("myseed")
    _find_btn(dlg, "Save seed").click()

    path = dgp_store.seed_path()
    assert path.read_text() == "myseed"
    assert stat.S_IMODE(path.stat().st_mode) == 0o600


def test_save_seed_updates_parent_in_memory(qapp, dgp_config_dir, stub_dialogs):
    """B3: saving a seed must update MainWindow._seed immediately."""
    w = make_window()
    assert w._seed is None
    dlg = _make_settings(w)
    dlg._seed_edit.setText("newseed")
    _find_btn(dlg, "Save seed").click()

    assert w._seed == "newseed"
    assert not w._unlock_row.isVisible()


def test_save_seed_empty_rejected(qapp, dgp_config_dir, stub_dialogs):
    """B4: empty seed must not be written; warning dialog must appear."""
    w = make_window()
    dlg = _make_settings(w)
    dlg._seed_edit.setText("")
    _find_btn(dlg, "Save seed").click()

    assert any(c[0] == "warning" for c in stub_dialogs.calls)
    assert not dgp_store.seed_path().exists()
    assert w._seed is None


# --- Account tab ---

def test_save_account_writes_to_disk(qapp, dgp_config_dir, stub_dialogs):
    w = make_window()
    dlg = _make_settings(w)
    dlg._account_edit.setText("user@example.com")
    _find_btn(dlg, "Save account").click()

    path = dgp_store.account_path()
    assert path.read_text() == "user@example.com"
    assert stat.S_IMODE(path.stat().st_mode) == 0o600


def test_save_account_updates_parent_in_memory(
    qapp, seeded_services, dgp_config_dir, stub_dialogs
):
    """B3: saving an account must update MainWindow._account and regenerate the password."""
    from dgp import engine as dgp_engine
    w = make_window(seed="s", account="old")
    w._refresh_list()
    w._list_widget.setCurrentRow(0)  # row 0 = email (pinned, xkcd)
    svc = w._list_widget.item(0).data(Qt.ItemDataRole.UserRole)

    dlg = _make_settings(w)
    dlg._account_edit.setText("new@example.com")
    _find_btn(dlg, "Save account").click()

    assert w._account == "new@example.com"
    expected = dgp_engine.generate("s", svc.name, svc.type, "new@example.com")
    assert w._password_field.text() == expected


def test_save_account_empty_allowed(qapp, dgp_config_dir, stub_dialogs):
    """Empty account is a valid value (account-less mode)."""
    w = make_window()
    dlg = _make_settings(w)
    dlg._account_edit.setText("")
    _find_btn(dlg, "Save account").click()

    path = dgp_store.account_path()
    assert path.exists()
    assert path.read_text() == ""
    assert w._account == ""
    assert not any(c[0] == "warning" for c in stub_dialogs.calls)


# --- Show toggle ---

def test_seed_show_toggle(qapp, dgp_config_dir):
    """The Show button on the Seed tab toggles echo mode between Password and Normal."""
    from PyQt6.QtWidgets import QTabWidget
    from dgp.gui.settings import SettingsDialog
    dlg = SettingsDialog()

    assert dlg._seed_edit.echoMode() == QLineEdit.EchoMode.Password

    # Find the seed tab's Show button by searching within the seed tab widget (index 0)
    tabs = dlg.findChild(QTabWidget)
    seed_tab = tabs.widget(0)
    show_btns = [
        b for b in seed_tab.findChildren(QPushButton)
        if b.text() == "Show" and b.isCheckable()
    ]
    show_btn = show_btns[0]

    show_btn.setChecked(True)
    assert dlg._seed_edit.echoMode() == QLineEdit.EchoMode.Normal

    show_btn.setChecked(False)
    assert dlg._seed_edit.echoMode() == QLineEdit.EchoMode.Password
