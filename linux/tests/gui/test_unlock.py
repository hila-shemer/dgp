from __future__ import annotations
import pytest
from PyQt6.QtTest import QTest
from PyQt6.QtCore import Qt

from dgp import engine as dgp_engine
from tests.gui._helpers import make_window


def test_unlock_row_visible_when_no_seed_file(qapp, dgp_config_dir):
    """Unlock row should be visible when no seed file exists."""
    w = make_window()
    assert w._unlock_row.isVisible()


def test_unlock_row_hidden_when_seed_file_present(qapp, dgp_config_dir):
    """Unlock row hidden when seed loaded from file at startup."""
    seed_file = dgp_config_dir / "seed"
    seed_file.write_text("abc")
    w = make_window()
    assert not w._unlock_row.isVisible()
    assert w._seed == "abc"


def test_unlock_button_uses_inline_input(qapp, dgp_config_dir):
    """Clicking Unlock must read _seed_input, set _seed, hide row, and clear field."""
    w = make_window()
    QTest.keyClicks(w._seed_input, "hunter2")
    w._btn_unlock.click()
    assert w._seed == "hunter2"
    assert not w._unlock_row.isVisible()
    assert w._seed_input.text() == ""


def test_unlock_empty_input_is_noop(qapp, dgp_config_dir):
    """Clicking Unlock with an empty input must not change state."""
    w = make_window()
    assert w._seed is None
    w._btn_unlock.click()
    assert w._seed is None
    assert w._unlock_row.isVisible()


def test_unlock_via_return_key(qapp, dgp_config_dir):
    """Pressing Return in _seed_input must trigger unlock (returnPressed connection)."""
    w = make_window()
    QTest.keyClicks(w._seed_input, "pressenter")
    QTest.keyClick(w._seed_input, Qt.Key.Key_Return)
    assert w._seed == "pressenter"
    assert not w._unlock_row.isVisible()


def test_unlock_regenerates_for_selected_row(qapp, seeded_services):
    """Selecting a row while locked shows placeholder; unlocking shows the real password."""
    w = make_window()  # seed=None -> locked
    w._list_widget.setCurrentRow(0)  # email / xkcd
    assert w._password_field.text() == "[unlock required]"

    QTest.keyClicks(w._seed_input, "testseed")
    w._btn_unlock.click()

    expected = dgp_engine.generate("testseed", "email", "xkcd", "")
    assert w._password_field.text() == expected
