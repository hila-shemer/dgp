from __future__ import annotations
import subprocess

from PyQt6.QtWidgets import (
    QDialog, QTabWidget, QWidget, QVBoxLayout, QHBoxLayout,
    QLineEdit, QPushButton, QLabel, QFileDialog, QMessageBox,
)
from PyQt6.QtCore import Qt

from dgp import store
from dgp.service import parse_services, serialize_services
from dgp.exportcrypto import encrypt_export, decrypt_export
from dgp.gui.vectorsview import VectorsView


class SettingsDialog(QDialog):
    def __init__(self, parent=None):
        super().__init__(parent)
        self.setWindowTitle("Settings")
        self.resize(500, 350)

        layout = QVBoxLayout(self)
        tabs = QTabWidget()
        layout.addWidget(tabs)

        tabs.addTab(self._build_seed_tab(), "Seed")
        tabs.addTab(self._build_account_tab(), "Account")
        tabs.addTab(self._build_export_tab(), "Export")
        tabs.addTab(self._build_import_tab(), "Import")
        tabs.addTab(VectorsView(), "Test Vectors")

    # --- Seed tab ---

    def _build_seed_tab(self) -> QWidget:
        w = QWidget()
        layout = QVBoxLayout(w)
        layout.addWidget(QLabel("Seed:"))

        row = QHBoxLayout()
        self._seed_edit = QLineEdit()
        self._seed_edit.setEchoMode(QLineEdit.EchoMode.Password)
        row.addWidget(self._seed_edit)
        btn_show = QPushButton("Show")
        btn_show.setCheckable(True)
        btn_show.toggled.connect(
            lambda c: self._seed_edit.setEchoMode(
                QLineEdit.EchoMode.Normal if c else QLineEdit.EchoMode.Password
            )
        )
        row.addWidget(btn_show)
        layout.addLayout(row)

        btn_save = QPushButton("Save seed")
        btn_save.clicked.connect(self._save_seed)
        layout.addWidget(btn_save)
        layout.addStretch()
        return w

    def _save_seed(self):
        try:
            store.write_text_file(store.seed_path(), self._seed_edit.text())
            QMessageBox.information(self, "Saved", "Seed saved.")
        except Exception as e:
            QMessageBox.critical(self, "Error", str(e))

    # --- Account tab ---

    def _build_account_tab(self) -> QWidget:
        w = QWidget()
        layout = QVBoxLayout(w)
        layout.addWidget(QLabel("Account:"))

        row = QHBoxLayout()
        self._account_edit = QLineEdit()
        self._account_edit.setEchoMode(QLineEdit.EchoMode.Password)
        row.addWidget(self._account_edit)
        btn_show = QPushButton("Show")
        btn_show.setCheckable(True)
        btn_show.toggled.connect(
            lambda c: self._account_edit.setEchoMode(
                QLineEdit.EchoMode.Normal if c else QLineEdit.EchoMode.Password
            )
        )
        row.addWidget(btn_show)
        layout.addLayout(row)

        btn_save = QPushButton("Save account")
        btn_save.clicked.connect(self._save_account)
        layout.addWidget(btn_save)
        layout.addStretch()
        return w

    def _save_account(self):
        try:
            store.write_text_file(store.account_path(), self._account_edit.text())
            QMessageBox.information(self, "Saved", "Account saved.")
        except Exception as e:
            QMessageBox.critical(self, "Error", str(e))

    # --- Export tab ---

    def _build_export_tab(self) -> QWidget:
        w = QWidget()
        layout = QVBoxLayout(w)
        layout.addWidget(QLabel("PIN:"))
        self._export_pin = QLineEdit()
        self._export_pin.setEchoMode(QLineEdit.EchoMode.Password)
        layout.addWidget(self._export_pin)

        btn_file = QPushButton("Export to file")
        btn_file.clicked.connect(self._export_to_file)
        layout.addWidget(btn_file)

        btn_clip = QPushButton("Copy to clipboard")
        btn_clip.clicked.connect(self._export_to_clipboard)
        layout.addWidget(btn_clip)
        layout.addStretch()
        return w

    def _make_export_blob(self) -> str | None:
        pin = self._export_pin.text()
        if not pin:
            QMessageBox.warning(self, "Error", "PIN is required.")
            return None
        services = store.read_services()
        plaintext = serialize_services(services)
        return encrypt_export(plaintext, pin)

    def _export_to_file(self):
        blob = self._make_export_blob()
        if blob is None:
            return
        path, _ = QFileDialog.getSaveFileName(self, "Save export", "", "All files (*)")
        if path:
            try:
                with open(path, "w", encoding="utf-8") as f:
                    f.write(blob)
                QMessageBox.information(self, "Done", "Exported.")
            except Exception as e:
                QMessageBox.critical(self, "Error", str(e))

    def _export_to_clipboard(self):
        blob = self._make_export_blob()
        if blob is None:
            return
        data = blob.encode("utf-8")
        for cmd in (["wl-copy"], ["xclip", "-selection", "clipboard", "-i"]):
            try:
                subprocess.run(cmd, input=data, check=True)
                return
            except (FileNotFoundError, subprocess.CalledProcessError):
                continue
        QMessageBox.warning(self, "Error", "Neither wl-copy nor xclip found.")

    # --- Import tab ---

    def _build_import_tab(self) -> QWidget:
        w = QWidget()
        layout = QVBoxLayout(w)
        layout.addWidget(QLabel("PIN (for encrypted import):"))
        self._import_pin = QLineEdit()
        self._import_pin.setEchoMode(QLineEdit.EchoMode.Password)
        layout.addWidget(self._import_pin)

        btn_file = QPushButton("Import from file")
        btn_file.clicked.connect(self._import_from_file)
        layout.addWidget(btn_file)

        btn_clip = QPushButton("Import from clipboard")
        btn_clip.clicked.connect(self._import_from_clipboard)
        layout.addWidget(btn_clip)

        btn_plain = QPushButton("Import plaintext JSON")
        btn_plain.clicked.connect(self._import_plaintext)
        layout.addWidget(btn_plain)
        layout.addStretch()
        return w

    def _merge_services(self, new_services):
        existing = store.read_services()
        by_id = {s.id: s for s in existing}
        for svc in new_services:
            by_id[svc.id] = svc
        merged = list(by_id.values())
        store.write_services(merged)
        if self.parent() and hasattr(self.parent(), "reload_services"):
            self.parent().reload_services()

    def _decrypt_blob(self, blob: str) -> list | None:
        pin = self._import_pin.text()
        plaintext = decrypt_export(blob, pin)
        if plaintext is None:
            QMessageBox.critical(self, "Error", "Decryption failed. Wrong PIN?")
            return None
        services = parse_services(plaintext)
        if not services:
            QMessageBox.critical(self, "Error", "No valid services found.")
            return None
        return services

    def _import_from_file(self):
        path, _ = QFileDialog.getOpenFileName(self, "Open export", "", "All files (*)")
        if not path:
            return
        try:
            blob = open(path, encoding="utf-8").read().strip()
        except Exception as e:
            QMessageBox.critical(self, "Error", str(e))
            return
        services = self._decrypt_blob(blob)
        if services:
            self._merge_services(services)
            QMessageBox.information(self, "Done", f"Imported {len(services)} service(s).")

    def _import_from_clipboard(self):
        blob = None
        for cmd in (["wl-paste"], ["xclip", "-o"]):
            try:
                result = subprocess.run(cmd, capture_output=True, check=True)
                blob = result.stdout.decode("utf-8").strip()
                break
            except (FileNotFoundError, subprocess.CalledProcessError):
                continue
        if blob is None:
            QMessageBox.warning(self, "Error", "Neither wl-paste nor xclip found.")
            return
        services = self._decrypt_blob(blob)
        if services:
            self._merge_services(services)
            QMessageBox.information(self, "Done", f"Imported {len(services)} service(s).")

    def _import_plaintext(self):
        path, _ = QFileDialog.getOpenFileName(self, "Open JSON", "", "JSON files (*.json)")
        if not path:
            return
        try:
            text = open(path, encoding="utf-8").read()
        except Exception as e:
            QMessageBox.critical(self, "Error", str(e))
            return
        services = parse_services(text)
        if not services:
            QMessageBox.critical(self, "Error", "No valid services found in file.")
            return
        self._merge_services(services)
        QMessageBox.information(self, "Done", f"Imported {len(services)} service(s).")
