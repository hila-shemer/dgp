import pytest

from dgp.testvectors import VECTORS, run_one


def _make_id(tv):
    seed_disp = f"{tv.seed[:4]}...({len(tv.seed)})" if len(tv.seed) > 16 else tv.seed
    name_disp = f"{tv.name[:4]}...({len(tv.name)})" if len(tv.name) > 16 else tv.name
    return f"{seed_disp}:{tv.account}:{name_disp}:{tv.type}"


@pytest.mark.parametrize(
    "idx",
    range(len(VECTORS)),
    ids=[_make_id(v) for v in VECTORS],
)
def test_vector(idx):
    result = run_one(idx)
    assert result.actual == result.expected, (
        f"Vector {result.label}: expected={result.expected!r} actual={result.actual!r}"
    )


def test_vector_count():
    assert len(VECTORS) == 74
