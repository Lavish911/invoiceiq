import os
import importlib
import pytest
from fastapi.testclient import TestClient
import main
from main import app, AI_SERVICE_TOKEN, extract_from_text
import io
from PIL import Image, ImageDraw, ImageFont

client = TestClient(app)

def create_dummy_invoice_image():
    img = Image.new('RGB', (600, 800), color = (255, 255, 255))
    d = ImageDraw.Draw(img)
    text = """
    Acme Corp
    Invoice No: INV-1001
    Date: 2023-10-01
    Due Date: 2023-10-15
    
    Item A: $1000.00
    Subtotal: $1000.00
    Tax: $180.00
    Total: $1180.00
    """
    d.text((10,10), text, fill=(0,0,0))
    img_byte_arr = io.BytesIO()
    img.save(img_byte_arr, format='PNG')
    img_byte_arr = img_byte_arr.getvalue()
    return img_byte_arr

def test_health():
    response = client.get("/health")
    assert response.status_code == 200
    assert response.json() == {"status": "up"}

def test_extract_unauthorized():
    response = client.post("/extract", files={"file": ("test.png", b"dummy")})
    assert response.status_code == 401

def test_extract_invalid_token():
    response = client.post("/extract", headers={"Authorization": "Bearer badtoken"}, files={"file": ("test.png", b"dummy")})
    assert response.status_code == 401

def test_extract_valid_image():
    img_bytes = create_dummy_invoice_image()
    response = client.post(
        "/extract", 
        headers={"Authorization": f"Bearer {AI_SERVICE_TOKEN}"},
        files={"file": ("invoice.png", img_bytes, "image/png")}
    )
    assert response.status_code == 200
    data = response.json()
    assert data["status"] == "success"
    
    extracted = data["extracted_data"]
    # Tesseract might not be perfect in CI without fonts, but it should extract something
    # We will just assert the keys exist and no 500 error occurred
    assert "invoice_number" in extracted
    assert "total_amount" in extracted

def test_extract_empty_file():
    response = client.post(
        "/extract", 
        headers={"Authorization": f"Bearer {AI_SERVICE_TOKEN}"},
        files={"file": ("empty.png", b"")}
    )
    assert response.status_code == 400

def test_extract_invalid_image():
    response = client.post(
        "/extract", 
        headers={"Authorization": f"Bearer {AI_SERVICE_TOKEN}"},
        files={"file": ("invalid.png", b"not an image data")}
    )
    assert response.status_code == 500

def _boot_main_with_env(monkeypatch, token, strict):
    """Fresh-import main under a controlled environment and snapshot the
    resulting (token, app). The pristine module is restored afterwards
    (reload mutates the module object in place, so values must be copied
    out before restoring), keeping the top-level tests on the original app."""
    if token is None:
        monkeypatch.delenv("AI_SERVICE_TOKEN", raising=False)
    else:
        monkeypatch.setenv("AI_SERVICE_TOKEN", token)
    monkeypatch.setenv("OCR_REQUIRE_EXPLICIT_TOKEN", strict)
    try:
        fresh = importlib.reload(main)
        return fresh.AI_SERVICE_TOKEN, fresh.app
    finally:
        monkeypatch.undo()
        importlib.reload(main)

def _expect_boot_failure(monkeypatch, token, strict):
    if token is None:
        monkeypatch.delenv("AI_SERVICE_TOKEN", raising=False)
    else:
        monkeypatch.setenv("AI_SERVICE_TOKEN", token)
    monkeypatch.setenv("OCR_REQUIRE_EXPLICIT_TOKEN", strict)
    try:
        with pytest.raises(RuntimeError):
            importlib.reload(main)
    finally:
        monkeypatch.undo()
        importlib.reload(main)

def test_strict_mode_rejects_default_dev_token(monkeypatch):
    _expect_boot_failure(monkeypatch, "default_dev_token", "true")

def test_strict_mode_rejects_missing_token(monkeypatch):
    _expect_boot_failure(monkeypatch, None, "true")

def test_strict_mode_rejects_example_placeholder(monkeypatch):
    _expect_boot_failure(monkeypatch, "dev-local-token-change-me", "true")

def test_strict_mode_accepts_explicit_token(monkeypatch):
    token, strict_app = _boot_main_with_env(
        monkeypatch, "rotated-production-token-abc123", "true")
    assert token == "rotated-production-token-abc123"
    assert TestClient(strict_app).get("/health").status_code == 200

def test_non_strict_mode_keeps_dev_default_for_local_runs(monkeypatch):
    token, _ = _boot_main_with_env(monkeypatch, None, "false")
    assert token == "default_dev_token"

# M8.1 total_amount regression matrix: pure-function tests on
# extract_from_text (no Tesseract/HTTP). "total" must never match inside
# subtotal variants, while legitimate total labels keep working.

def _extract_total(text):
    data, _ = extract_from_text(text)
    return data.total_amount, data.subtotal

def test_total_subtotal_yields_no_total():
    total, sub = _extract_total("Subtotal: 1000.00")
    assert total is None
    assert sub == "1000.00"

def test_total_uppercase_subtotal_yields_no_total():
    total, _ = _extract_total("SUBTOTAL: 1000.00")
    assert total is None

def test_total_hyphenated_subtotal_yields_no_total():
    total, _ = _extract_total("Sub-total: 1000.00")
    assert total is None

def test_total_spaced_subtotal_yields_no_total():
    total, _ = _extract_total("Sub Total: 1000.00")
    assert total is None

def test_total_mixed_case_subtotal_yields_no_total():
    total, _ = _extract_total("SubTOTAL: 1000.00")
    assert total is None

def test_total_plain_label():
    total, _ = _extract_total("Total: 1234.56")
    assert total == "1234.56"

def test_total_grand_total_label():
    total, _ = _extract_total("Grand Total: 99.99")
    assert total == "99.99"

def test_total_invoice_total_label():
    total, _ = _extract_total("Invoice Total: 42.00")
    assert total == "42.00"

def test_total_amount_due_label():
    total, _ = _extract_total("Amount Due: 7.50")
    assert total == "7.50"

def test_total_subtotal_before_total_resolves_actual_total():
    text = "Subtotal: 1100.00\nTax: 134.56\nTotal: 1234.56"
    total, sub = _extract_total(text)
    assert total == "1234.56"
    assert sub == "1100.00"

def test_total_currency_and_thousands_separator():
    total, _ = _extract_total("Total: $1,234.56")
    assert total == "1234.56"

def test_total_m73_failing_pattern():
    # Exact shape of the M7.3 cloud failure: subtotal line above the total.
    text = ("M7.3 Test Vendor\nInvoice Number: M73-REAL-002\nDate: 2026-10-01\n"
            "Subtotal: 1100.00\nTax: 134.56\nTotal: 1234.56")
    total, sub = _extract_total(text)
    assert total == "1234.56"
    assert sub == "1100.00"

def test_total_amount_label_preserves_current_behavior():
    # "Total Amount:" is not a supported label today (fail-safe null);
    # pinned so any future support is a deliberate change.
    total, _ = _extract_total("Total Amount: 5.00")
    assert total is None
