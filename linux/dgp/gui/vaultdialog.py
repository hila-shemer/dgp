from __future__ import annotations

from PyQt6.QtWidgets import (
    QDialog, QVBoxLayout, QHBoxLayout, QTextEdit,
    QPushButton, QDialogButtonBox,
)

from dgp.service import DgpService


class VaultDialog(QDialog):
    def __init__(self, service: DgpService, seed: str, account: str, parent=None):
        super().__init__(parent)
        self.setWindowTitle(f"Vault — {service.name}")

        from dgp.vault import decrypt_vault
        plaintext = ""
        if service.encrypted_secret:
            plaintext = decrypt_vault(service.encrypted_secret, seed, service.name, account) or ""

        layout = QVBoxLayout(self)
        self._text_edit = QTextEdit()
        self._text_edit.setPlainText(plaintext)
        layout.addWidget(self._text_edit)

        buttons = QDialogButtonBox(
            QDialogButtonBox.StandardButton.Ok | QDialogButtonBox.StandardButton.Cancel
        )
        buttons.accepted.connect(self.accept)
        buttons.rejected.connect(self.reject)
        layout.addWidget(buttons)

    def get_result(self) -> str | None:
        if self.exec() == QDialog.DialogCode.Accepted:
            return self._text_edit.toPlainText()
        return None
