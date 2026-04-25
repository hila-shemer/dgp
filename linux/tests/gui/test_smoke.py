from dgp.service import new_service, serialize_services
from dgp.engine import generate
from dgp.gui.mainwindow import MainWindow


def test_main_window_row_count(qapp, dgp_config_dir):
    services = [new_service("svc1"), new_service("svc2")]
    (dgp_config_dir / "services.json").write_text(serialize_services(services))
    w = MainWindow()
    assert w._list_widget.count() == 2


def test_generate_on_select(qapp, dgp_config_dir):
    svc = new_service("mysvc", type="alnum")
    (dgp_config_dir / "services.json").write_text(serialize_services([svc]))
    w = MainWindow()
    w._seed = "testseed"
    w._account = ""
    w._list_widget.setCurrentRow(0)
    expected = generate("testseed", "mysvc", "alnum", "")
    assert w._password_field.text() == expected
