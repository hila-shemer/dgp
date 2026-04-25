from __future__ import annotations
import pytest
from PyQt6.QtTest import QTest
from PyQt6.QtCore import Qt

from dgp.service import new_service, serialize_services
from dgp.vault import encrypt_vault, decrypt_vault
from tests.gui._helpers import make_window

SEED = "testseed"
ACCOUNT = ""
SECRET = "topsecret"


@pytest.fixture
def vault_svc(dgp_config_dir):
    """One vault service pre-populated with an encrypted secret."""
    svc = new_service("myvault", type="vault")
    svc.encrypted_secret = encrypt_vault(SECRET, SEED, "myvault", ACCOUNT)
    (dgp_config_dir / "services.json").write_text(serialize_services([svc]))
    return svc


@pytest.fixture
def plain_vault_svc(dgp_config_dir):
    """One vault service with no encrypted secret."""
    svc = new_service("emptyvault", type="vault")
    (dgp_config_dir / "services.json").write_text(serialize_services([svc]))
    return svc


def test_vault_single_click_locked(qapp, plain_vault_svc):
    """Single-clicking a vault entry while locked shows [unlock required]."""
    w = make_window()
    w._list_widget.setCurrentRow(0)
    assert w._password_field.text() == "[unlock required]"


def test_vault_single_click_unlocked_shows_placeholder(qapp, plain_vault_svc):
    """Single-clicking a vault entry while unlocked shows the vault placeholder."""
    w = make_window(seed=SEED, account=ACCOUNT)
    w._list_widget.setCurrentRow(0)
    assert w._password_field.text() == "[vault entry — double-click to open]"


def test_vault_double_click_opens_dialog_with_decrypted_text(qapp, vault_svc, monkeypatch):
    """Double-clicking a vault entry opens VaultDialog with the decrypted text."""
    captured = []

    def fake_get_result(self):
        captured.append(self._text_edit.toPlainText())
        return None  # simulate cancel — don't write back

    monkeypatch.setattr("dgp.gui.vaultdialog.VaultDialog.get_result", fake_get_result)

    w = make_window(seed=SEED, account=ACCOUNT)
    item = w._list_widget.item(0)
    w._list_widget.itemDoubleClicked.emit(item)

    assert captured == [SECRET]


def test_vault_double_click_locked_does_nothing(qapp, plain_vault_svc, monkeypatch):
    """Double-clicking while locked must not open VaultDialog."""
    opened = []

    def fake_get_result(self):
        opened.append(True)
        return None

    monkeypatch.setattr("dgp.gui.vaultdialog.VaultDialog.get_result", fake_get_result)

    w = make_window()  # seed=None -> locked
    item = w._list_widget.item(0)
    w._list_widget.itemDoubleClicked.emit(item)

    assert opened == []


def test_vault_save_re_encrypts(qapp, vault_svc, monkeypatch):
    """After double-click with a new secret, on-disk blob decrypts to the new secret."""
    monkeypatch.setattr(
        "dgp.gui.vaultdialog.VaultDialog.get_result",
        lambda self: "newsecret",
    )

    w = make_window(seed=SEED, account=ACCOUNT)
    item = w._list_widget.item(0)
    w._list_widget.itemDoubleClicked.emit(item)

    from dgp import store as dgp_store
    services = dgp_store.read_services()
    assert len(services) == 1
    blob = services[0].encrypted_secret
    assert blob is not None
    plaintext = decrypt_vault(blob, SEED, "myvault", ACCOUNT)
    assert plaintext == "newsecret"


def test_vault_cancel_does_not_save(qapp, vault_svc, monkeypatch):
    """Cancelling VaultDialog (get_result returns None) leaves on-disk blob unchanged."""
    original_blob = vault_svc.encrypted_secret

    monkeypatch.setattr(
        "dgp.gui.vaultdialog.VaultDialog.get_result",
        lambda self: None,
    )

    w = make_window(seed=SEED, account=ACCOUNT)
    item = w._list_widget.item(0)
    w._list_widget.itemDoubleClicked.emit(item)

    from dgp import store as dgp_store
    services = dgp_store.read_services()
    assert services[0].encrypted_secret == original_blob


def test_vault_rename_invalidates_secret(qapp, vault_svc):
    """Documenting invariant: decrypting with a different name returns None (wrong key)."""
    # Encrypted under "myvault"; decrypting under "othervault" uses a different key.
    blob = vault_svc.encrypted_secret
    result = decrypt_vault(blob, SEED, "othervault", ACCOUNT)
    assert result is None
