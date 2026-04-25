"""Phase 10 — Edge case tests."""
from __future__ import annotations
import os
import pytest
from PyQt6.QtCore import Qt


def test_corrupted_services_json_shows_empty_list(qapp, dgp_config_dir):
    from tests.gui._helpers import make_window
    (dgp_config_dir / "services.json").write_text("{not json")
    w = make_window()
    assert w._list_widget.count() == 0


def test_missing_seed_file_keeps_unlock_row_visible(qapp, dgp_config_dir):
    from tests.gui._helpers import make_window
    w = make_window()
    assert w._seed is None
    assert w._unlock_row.isVisible() is True


def test_unicode_service_name(qapp, dgp_config_dir):
    from dgp.service import new_service, serialize_services
    from dgp import engine
    from tests.gui._helpers import make_window

    svc = new_service("café-账号", type="alnum")
    (dgp_config_dir / "services.json").write_text(serialize_services([svc]))

    w = make_window(seed="abc")
    w._list_widget.setCurrentRow(0)
    w._on_item_changed(w._list_widget.item(0), None)

    expected = engine.generate("abc", "café-账号", "alnum", "")
    assert w._password_field.text() == expected


def test_very_long_service_name(qapp, dgp_config_dir):
    from dgp.service import new_service, serialize_services
    from tests.gui._helpers import make_window

    long_name = "x" * 256
    svc = new_service(long_name, type="alnum")
    (dgp_config_dir / "services.json").write_text(serialize_services([svc]))

    w = make_window(seed="abc")
    assert w._list_widget.count() == 1
    w._list_widget.setCurrentRow(0)
    w._on_item_changed(w._list_widget.item(0), None)

    pw = w._password_field.text()
    assert pw
    assert not pw.startswith("[error")


def test_seed_file_with_trailing_newline(qapp, dgp_config_dir):
    from tests.gui._helpers import make_window
    (dgp_config_dir / "seed").write_text("abc\n")
    w = make_window()
    assert w._seed == "abc"


def test_account_file_unreadable(qapp, dgp_config_dir):
    from tests.gui._helpers import make_window
    acct_path = dgp_config_dir / "account"
    acct_path.write_text("secret")
    acct_path.chmod(0o000)
    try:
        if not os.access(acct_path, os.R_OK):
            w = make_window()
            assert w._account == ""
        else:
            pytest.skip("filesystem does not honor chmod 000 for this user")
    finally:
        acct_path.chmod(0o600)


def test_export_with_zero_services(qapp, dgp_config_dir):
    from dgp import exportcrypto
    from dgp.service import serialize_services
    blob = exportcrypto.encrypt_export(serialize_services([]), "hunter2")
    assert blob
    result = exportcrypto.decrypt_export(blob, "hunter2")
    assert result == "[]"


def test_search_with_regex_metachars(qapp, dgp_config_dir, seeded_services):
    from tests.gui._helpers import make_window
    from PyQt6.QtTest import QTest

    w = make_window()

    QTest.keyClicks(w._search_box, ".*")
    visible = [w._list_widget.item(i) for i in range(w._list_widget.count())
               if not w._list_widget.item(i).isHidden()]
    assert len(visible) == 0

    w._search_box.clear()
    QTest.keyClicks(w._search_box, "git")
    visible = [w._list_widget.item(i) for i in range(w._list_widget.count())
               if not w._list_widget.item(i).isHidden()]
    assert len(visible) == 1
    assert visible[0].data(Qt.ItemDataRole.UserRole).name == "github"
