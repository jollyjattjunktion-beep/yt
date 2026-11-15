from app.renderer import safe_name

def test_safe_name():
    assert safe_name("India vs Australia 1st ODI") == "india-vs-australia-1st-odi"
