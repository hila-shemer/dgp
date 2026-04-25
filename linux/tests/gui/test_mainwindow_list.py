from __future__ import annotations
import re
import pytest
from PyQt6.QtWidgets import QApplication, QLineEdit
from PyQt6.QtTest import QTest
from PyQt6.QtCore import Qt

from dgp import engine as dgp_engine
from dgp.service import new_service, serialize_services
from tests.gui._helpers import make_window


def _visible_items(w):
    return [
        w._list_widget.item(i)
        for i in range(w._list_widget.count())
        if not w._list_widget.item(i).isHidden()
    ]


def test_empty_list_when_no_services_json(qapp, dgp_config_dir):
    w = make_window()
    assert w._list_widget.count() == 0


def test_pinned_first_ordering(qapp, seeded_services):
    w = make_window()
    visible = _visible_items(w)
    assert visible[0].data(Qt.ItemDataRole.UserRole).name == "email"


def test_archived_hidden_by_default(qapp, seeded_services):
    w = make_window()
    visible = _visible_items(w)
    names = [it.data(Qt.ItemDataRole.UserRole).name for it in visible]
    assert len(visible) == 2
    assert "oldsvc" not in names


def test_archive_toggle_reveals_archived(qapp, seeded_services):
    w = make_window()
    w._btn_archive.toggle()  # show archived
    assert len(_visible_items(w)) == 3
    w._btn_archive.toggle()  # hide archived
    assert len(_visible_items(w)) == 2


def test_search_filter_live(qapp, seeded_services):
    w = make_window()
    QTest.keyClicks(w._search_box, "git")
    visible = _visible_items(w)
    assert len(visible) == 1
    assert visible[0].data(Qt.ItemDataRole.UserRole).name == "github"


def test_search_filter_clears_on_empty(qapp, seeded_services):
    w = make_window()
    QTest.keyClicks(w._search_box, "git")
    w._search_box.clear()
    assert len(_visible_items(w)) == 2


def test_search_filter_survives_archive_toggle(qapp, seeded_services):
    """B5 regression — archive toggle must not reset the active search filter."""
    w = make_window()
    QTest.keyClicks(w._search_box, "git")
    w._btn_archive.toggle()
    visible = _visible_items(w)
    names = [it.data(Qt.ItemDataRole.UserRole).name for it in visible]
    assert names == ["github"]


def test_unlocked_state_generates_password(qapp, seeded_services):
    w = make_window(seed="testseed")
    w._list_widget.setCurrentRow(0)  # email / xkcd
    expected = dgp_engine.generate("testseed", "email", "xkcd", "")
    assert w._password_field.text() == expected


def test_locked_state_shows_unlock_placeholder(qapp, seeded_services):
    w = make_window()  # seed=None → locked
    w._list_widget.setCurrentRow(0)
    assert w._password_field.text() == "[unlock required]"


def test_engine_error_shown_inline(qapp, seeded_services, monkeypatch):
    def _bad(*args, **kwargs):
        raise RuntimeError("bad seed")
    monkeypatch.setattr(dgp_engine, "generate", _bad)
    w = make_window(seed="anything")
    w._list_widget.setCurrentRow(0)
    text = w._password_field.text()
    assert re.fullmatch(r"\[error: .*\]", text), repr(text)


def test_copy_password_writes_clipboard(qapp, seeded_services):
    QApplication.clipboard().clear()
    w = make_window(seed="testseed")
    w._list_widget.setCurrentRow(0)
    expected = w._password_field.text()
    assert not expected.startswith("[")  # sanity: real password
    w._btn_copy.click()
    assert QApplication.clipboard().text() == expected


def test_copy_password_skips_placeholder(qapp, seeded_services):
    """B6 fix — copying [unlock required] placeholder must be a no-op."""
    QApplication.clipboard().clear()
    w = make_window()  # locked
    w._list_widget.setCurrentRow(0)
    assert w._password_field.text() == "[unlock required]"
    w._btn_copy.click()
    assert QApplication.clipboard().text() == ""


def test_show_toggle_changes_echo_mode(qapp, seeded_services):
    w = make_window()
    assert w._password_field.echoMode() == QLineEdit.EchoMode.Password
    assert w._btn_show.text() == "Show"
    w._btn_show.click()
    assert w._password_field.echoMode() == QLineEdit.EchoMode.Normal
    assert w._btn_show.text() == "Hide"
    w._btn_show.click()
    assert w._password_field.echoMode() == QLineEdit.EchoMode.Password
    assert w._btn_show.text() == "Show"
