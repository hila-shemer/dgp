def test_import_dgp_gui_app():
    """dgp.gui.app must be importable even without libxkbcommon / a display."""
    import dgp.gui.app
    assert hasattr(dgp.gui.app, "main")
