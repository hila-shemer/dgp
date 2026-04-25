from __future__ import annotations


def make_window(seed=None, account="", seed_file=None, account_file=None):
    """Construct a MainWindow.  DGP_CONFIG_DIR must already be set by the caller."""
    from dgp.gui.mainwindow import MainWindow
    w = MainWindow(seed_file=seed_file, account_file=account_file)
    if seed is not None:
        w._seed = seed
        w._unlock_row.setVisible(False)
    w._account = account
    return w


def wait_for_signal(signal, timeout_ms: int = 5000) -> bool:
    """Wait up to timeout_ms for signal to fire. Returns True if received."""
    from PyQt6.QtTest import QSignalSpy
    spy = QSignalSpy(signal)
    return spy.wait(timeout_ms)
