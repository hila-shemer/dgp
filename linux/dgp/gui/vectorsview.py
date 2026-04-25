from __future__ import annotations

from PyQt6.QtWidgets import (
    QWidget, QVBoxLayout, QTableWidget, QTableWidgetItem,
    QPushButton,
)
from PyQt6.QtCore import Qt, QThread, QObject, pyqtSignal
from PyQt6.QtGui import QColor

from dgp import testvectors


class _Worker(QObject):
    finished = pyqtSignal(list)

    def run(self):
        results = testvectors.run_all()
        self.finished.emit(results)


class VectorsView(QWidget):
    def __init__(self, parent=None):
        super().__init__(parent)
        layout = QVBoxLayout(self)

        self._table = QTableWidget(0, 4)
        self._table.setHorizontalHeaderLabels(["Label", "Expected", "Actual", "Pass/Fail"])
        self._table.horizontalHeader().setStretchLastSection(True)
        layout.addWidget(self._table)

        btn = QPushButton("Run vectors")
        btn.clicked.connect(self._run)
        layout.addWidget(btn)

        self._thread: QThread | None = None
        self._worker: _Worker | None = None

    def _run(self):
        if self._thread and self._thread.isRunning():
            return
        self._table.setRowCount(0)
        self._thread = QThread()
        self._worker = _Worker()
        self._worker.moveToThread(self._thread)
        self._thread.started.connect(self._worker.run)
        self._worker.finished.connect(self._on_finished)
        self._worker.finished.connect(self._thread.quit)
        self._thread.start()

    def _on_finished(self, results: list):
        self._table.setRowCount(len(results))
        green = QColor(200, 255, 200)
        red = QColor(255, 200, 200)
        for row, r in enumerate(results):
            self._table.setItem(row, 0, QTableWidgetItem(r.label))
            self._table.setItem(row, 1, QTableWidgetItem(r.expected))
            self._table.setItem(row, 2, QTableWidgetItem(r.actual))
            pf = "PASS" if r.passed else "FAIL"
            pf_item = QTableWidgetItem(pf)
            pf_item.setBackground(green if r.passed else red)
            self._table.setItem(row, 3, pf_item)
