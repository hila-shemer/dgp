from importlib.resources import files


def load_bip39_english() -> list[str]:
    text = files("dgp.data").joinpath("english.txt").read_text("utf-8")
    words = [w.strip() for w in text.split("\n") if w.strip()]
    assert len(words) == 2048, f"expected 2048 words, got {len(words)}"
    return words
