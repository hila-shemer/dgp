from __future__ import annotations
from PyQt6.QtCore import Qt
from PyQt6.QtGui import QColor
from PyQt6.QtTest import QTest, QSignalSpy
from PyQt6.QtWidgets import QApplication, QPushButton

import pytest


def _make_view():
    from dgp.gui.vectorsview import VectorsView
    v = VectorsView()
    v.show()
    return v


def _run_btn(view) -> QPushButton:
    return next(b for b in view.findChildren(QPushButton) if b.text() == "Run vectors")


def _click_run(view):
    QTest.mouseClick(_run_btn(view), Qt.MouseButton.LeftButton)


def test_run_populates_table(qapp, dgp_config_dir):
    view = _make_view()
    _click_run(view)
    worker = view._worker
    spy = QSignalSpy(worker.finished)
    assert spy.wait(10000)
    QApplication.processEvents()
    assert view._table.rowCount() == 74


def test_all_pass_green(qapp, dgp_config_dir):
    view = _make_view()
    _click_run(view)
    worker = view._worker
    spy = QSignalSpy(worker.finished)
    assert spy.wait(10000)
    QApplication.processEvents()
    for row in range(74):
        item = view._table.item(row, 3)
        assert item.text() == "PASS"
        assert item.background().color() == QColor(200, 255, 200)


def test_run_disabled_during_execution(qapp, dgp_config_dir):
    view = _make_view()
    _click_run(view)
    worker = view._worker
    assert _run_btn(view).isEnabled() == False
    spy = QSignalSpy(worker.finished)
    assert spy.wait(10000)
    QApplication.processEvents()
    assert _run_btn(view).isEnabled() == True


def test_double_click_does_not_start_second_run(qapp, dgp_config_dir, monkeypatch):
    from dgp.gui import vectorsview as vv_mod
    count = {"n": 0}
    real_init = vv_mod._Worker.__init__
    def counting_init(self_w):
        count["n"] += 1
        real_init(self_w)
    monkeypatch.setattr(vv_mod._Worker, "__init__", counting_init)

    view = _make_view()
    _click_run(view)
    worker = view._worker
    _click_run(view)

    spy = QSignalSpy(worker.finished)
    assert spy.wait(10000)
    QApplication.processEvents()
    assert count["n"] == 1


def test_run_leaves_no_dangling_thread(qapp, dgp_config_dir):
    view = _make_view()
    _click_run(view)
    worker = view._worker
    spy = QSignalSpy(worker.finished)
    assert spy.wait(10000)
    QApplication.processEvents()
    assert view._thread is None
    assert view._worker is None
