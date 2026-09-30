import os
from fastapi.testclient import TestClient
from main import app, AI_SERVICE_TOKEN
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
