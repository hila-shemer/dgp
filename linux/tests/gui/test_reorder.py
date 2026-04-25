from __future__ import annotations
import pytest
from PyQt6.QtCore import QModelIndex

from dgp.service import new_service, serialize_services
from dgp import store as dgp_store
from tests.gui._helpers import make_window


def test_reorder_persists_to_services_json(qapp, seeded_services, dgp_config_dir):
    """Moving row 0 after row 1 writes the new order to services.json."""
    w = make_window(seed="s", account="")
    # Visible list after _refresh_list: [email(0, pinned), github(1)]
    model = w._list_widget.model()
    # destRow=2 inserts before end-of-list, placing moved item at position 1
    # _on_reorder() called directly because programmatic moveRow does not fire rowsMoved
    model.moveRow(QModelIndex(), 0, QModelIndex(), 2)
    w._on_reorder()

    services = dgp_store.read_services()
    assert services[0].name == "github"
    assert services[1].name == "email"
    assert services[2].name == "oldsvc"


def test_reorder_preserves_archived_at_end(qapp, seeded_services, dgp_config_dir):
    """Archived services remain present in JSON after a reorder, appended at the end."""
    w = make_window(seed="s", account="")
    model = w._list_widget.model()
    # _on_reorder() called directly because programmatic moveRow does not fire rowsMoved
    model.moveRow(QModelIndex(), 0, QModelIndex(), 2)
    w._on_reorder()

    services = dgp_store.read_services()
    assert services[2].name == "oldsvc"
    assert services[2].archived is True


def test_pinned_first_invariant_after_refresh(qapp, seeded_services, dgp_config_dir):
    """After a reorder that moved email away from row 0, _refresh_list re-floats it (pinned).

    Documents an existing UX quirk: pinned items always float to the top on list rebuild.
    Do NOT add code to "fix" this behavior.
    """
    w = make_window(seed="s", account="")
    model = w._list_widget.model()
    # _on_reorder() called directly because programmatic moveRow does not fire rowsMoved
    model.moveRow(QModelIndex(), 0, QModelIndex(), 2)
    w._on_reorder()
    # On-disk and in-memory order is now [github, email, oldsvc]
    # Re-building the list re-floats the pinned item (email) back to row 0
    w._refresh_list()

    assert "email" in w._list_widget.item(0).text()
    assert "github" in w._list_widget.item(1).text()


def test_reload_services_after_external_change(qapp, seeded_services, dgp_config_dir):
    """reload_services() picks up an externally-modified services.json."""
    w = make_window(seed="s", account="")
    assert w._list_widget.count() == 2  # email + github (oldsvc hidden)

    new_svcs = [new_service("alpha", type="hex"), new_service("beta", type="base58")]
    (dgp_config_dir / "services.json").write_text(serialize_services(new_svcs))

    w.reload_services()

    assert w._list_widget.count() == 2
    assert "alpha" in w._list_widget.item(0).text()
    assert "beta" in w._list_widget.item(1).text()
