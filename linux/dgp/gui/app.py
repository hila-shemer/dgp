from __future__ import annotations
import argparse
import sys


def main(argv=None) -> int:
    parser = argparse.ArgumentParser(
        prog="dgp-gui",
        description="Deterministically Generated Passwords — GUI",
    )
    parser.add_argument(
        "--seed-file", metavar="PATH",
        help="Read seed from file instead of ~/.config/dgp/seed",
    )
    parser.add_argument(
        "--account-file", metavar="PATH",
        help="Read account from file instead of ~/.config/dgp/account",
    )
    # Parse here so --help exits before any Qt import
    args = parser.parse_args(argv)

    # All Qt imports deferred past argparse so --help works without libxkbcommon
    import os
    os.environ.setdefault("QT_QPA_PLATFORM", "offscreen")
    from PyQt6.QtWidgets import QApplication
    from dgp.gui.mainwindow import MainWindow

    app = QApplication(sys.argv if argv is None else [sys.argv[0]])
    window = MainWindow(seed_file=args.seed_file, account_file=args.account_file)
    window.show()
    return app.exec()


if __name__ == "__main__":
    sys.exit(main())
