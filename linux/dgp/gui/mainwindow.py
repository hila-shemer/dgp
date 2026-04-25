from __future__ import annotations
from pathlib import Path
from PyQt6.QtWidgets import (
    QMainWindow, QWidget, QVBoxLayout, QHBoxLayout,
    QSplitter, QListWidget, QListWidgetItem, QLineEdit,
    QLabel, QPushButton, QAbstractItemView, QInputDialog,
    QApplication,
)
from PyQt6.QtCore import Qt

from dgp.service import DgpService
from dgp import store, engine


class MainWindow(QMainWindow):
    def __init__(self, seed_file=None, account_file=None, parent=None):
        super().__init__(parent)
        self.setWindowTitle("DGP")

        self._seed: str | None = None
        self._account: str = ""
        self._services: list[DgpService] = []
        self._show_archived = False

        # Load seed
        seed_p = Path(seed_file) if seed_file else store.seed_path()
        try:
            self._seed = store.read_text_file(seed_p).strip() or None
        except Exception:
            self._seed = None

        # Load account
        account_p = Path(account_file) if account_file else store.account_path()
        try:
            self._account = store.read_text_file(account_p).strip()
        except Exception:
            self._account = ""

        self._services = store.read_services()

        self._build_ui()
        self._refresh_list()

    def _build_ui(self):
        splitter = QSplitter(Qt.Orientation.Horizontal)
        self.setCentralWidget(splitter)

        # Left panel
        left = QWidget()
        left_layout = QVBoxLayout(left)

        self._search_box = QLineEdit()
        self._search_box.setPlaceholderText("Search…")
        self._search_box.textChanged.connect(self._on_search)
        left_layout.addWidget(self._search_box)

        self._list_widget = QListWidget()
        self._list_widget.setDragDropMode(QAbstractItemView.DragDropMode.InternalMove)
        self._list_widget.currentItemChanged.connect(self._on_item_changed)
        self._list_widget.model().rowsMoved.connect(self._on_reorder)
        left_layout.addWidget(self._list_widget)

        btn_row = QHBoxLayout()
        self._btn_settings = QPushButton("Settings")
        self._btn_settings.clicked.connect(self._open_settings)
        btn_row.addWidget(self._btn_settings)

        self._btn_archive = QPushButton("Archive")
        self._btn_archive.setCheckable(True)
        self._btn_archive.toggled.connect(self._on_archive_toggle)
        btn_row.addWidget(self._btn_archive)
        left_layout.addLayout(btn_row)

        splitter.addWidget(left)

        # Right panel
        right = QWidget()
        right_layout = QVBoxLayout(right)

        self._service_label = QLabel("")
        right_layout.addWidget(self._service_label)

        pw_row = QHBoxLayout()
        self._password_field = QLineEdit()
        self._password_field.setReadOnly(True)
        self._password_field.setEchoMode(QLineEdit.EchoMode.Password)
        pw_row.addWidget(self._password_field)

        self._btn_show = QPushButton("Show")
        self._btn_show.setCheckable(True)
        self._btn_show.toggled.connect(self._on_show_toggle)
        pw_row.addWidget(self._btn_show)
        right_layout.addLayout(pw_row)

        self._btn_copy = QPushButton("Copy")
        self._btn_copy.clicked.connect(self._copy_password)
        right_layout.addWidget(self._btn_copy)

        # Unlock row
        self._unlock_row = QWidget()
        unlock_layout = QHBoxLayout(self._unlock_row)
        unlock_layout.setContentsMargins(0, 0, 0, 0)
        self._seed_input = QLineEdit()
        self._seed_input.setPlaceholderText("Seed…")
        self._seed_input.setEchoMode(QLineEdit.EchoMode.Password)
        unlock_layout.addWidget(self._seed_input)
        self._btn_unlock = QPushButton("Unlock")
        self._btn_unlock.clicked.connect(self._unlock)
        unlock_layout.addWidget(self._btn_unlock)
        right_layout.addWidget(self._unlock_row)

        right_layout.addStretch()
        splitter.addWidget(right)

        if self._seed is not None:
            self._unlock_row.setVisible(False)

    def _refresh_list(self):
        self._list_widget.blockSignals(True)
        self._list_widget.clear()
        pinned = [s for s in self._services if s.pinned]
        unpinned = [s for s in self._services if not s.pinned]
        ordered = pinned + unpinned
        for svc in ordered:
            if svc.archived and not self._show_archived:
                continue
            item = QListWidgetItem(f"{svc.name} [{svc.type}]")
            item.setData(Qt.ItemDataRole.UserRole, svc)
            self._list_widget.addItem(item)
        self._list_widget.blockSignals(False)

    def _service_at_row(self, row: int) -> DgpService | None:
        item = self._list_widget.item(row)
        if item is None:
            return None
        return item.data(Qt.ItemDataRole.UserRole)

    def _on_item_changed(self, current, previous):
        if current is None:
            self._service_label.setText("")
            self._password_field.setText("")
            return
        svc: DgpService = current.data(Qt.ItemDataRole.UserRole)
        self._service_label.setText(svc.name)
        if self._seed is None:
            self._password_field.setText("[unlock required]")
        else:
            try:
                pw = engine.generate(self._seed, svc.name, svc.type, self._account)
                self._password_field.setText(pw)
            except Exception as e:
                self._password_field.setText(f"[error: {e}]")

    def _on_search(self, query: str):
        q = query.lower()
        for i in range(self._list_widget.count()):
            item = self._list_widget.item(i)
            item.setHidden(q not in item.text().lower())

    def _on_show_toggle(self, checked: bool):
        mode = QLineEdit.EchoMode.Normal if checked else QLineEdit.EchoMode.Password
        self._password_field.setEchoMode(mode)
        self._btn_show.setText("Hide" if checked else "Show")

    def _copy_password(self):
        QApplication.clipboard().setText(self._password_field.text())

    def _unlock(self):
        seed, ok = QInputDialog.getText(
            self, "Unlock", "Enter seed:", QLineEdit.EchoMode.Password
        )
        if ok and seed:
            self._seed = seed
            self._unlock_row.setVisible(False)
            self._on_item_changed(self._list_widget.currentItem(), None)

    def _on_archive_toggle(self, checked: bool):
        self._show_archived = checked
        self._refresh_list()

    def _on_reorder(self):
        new_order = []
        for i in range(self._list_widget.count()):
            item = self._list_widget.item(i)
            svc = item.data(Qt.ItemDataRole.UserRole)
            if svc is not None:
                new_order.append(svc)
        archived = [s for s in self._services if s.archived and not self._show_archived]
        store.write_services(new_order + archived)
        self._services = new_order + archived

    def _open_settings(self):
        from dgp.gui.settings import SettingsDialog
        dlg = SettingsDialog(self)
        dlg.exec()

    def reload_services(self):
        self._services = store.read_services()
        self._refresh_list()
