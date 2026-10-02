import os
import re
from decimal import Decimal
from typing import Dict, Any, Optional
from fastapi import FastAPI, File, UploadFile, Header, HTTPException, status
from pydantic import BaseModel, ConfigDict
import pytesseract
from PIL import Image
import fitz  # PyMuPDF
import io

app = FastAPI(title="InvoiceIQ OCR Service")

AI_SERVICE_TOKEN = os.getenv("AI_SERVICE_TOKEN", "default_dev_token")

# Fail-fast production guard (mirrors the backend docker-profile validator):
# when OCR_REQUIRE_EXPLICIT_TOKEN is enabled (Docker Compose / Railway), booting
# with a missing or publicly known development token is refused instead of
# silently accepted. Plain local runs (uvicorn without the flag) keep the
# development default for convenience.
DEV_DEFAULT_TOKENS = frozenset({"", "default_dev_token", "dev-local-token-change-me"})
OCR_REQUIRE_EXPLICIT_TOKEN = (
    os.getenv("OCR_REQUIRE_EXPLICIT_TOKEN", "").strip().lower() in ("1", "true", "yes")
)

if OCR_REQUIRE_EXPLICIT_TOKEN and AI_SERVICE_TOKEN.strip() in DEV_DEFAULT_TOKENS:
    raise RuntimeError(
        "AI_SERVICE_TOKEN must be set to an explicit non-default value when "
        "OCR_REQUIRE_EXPLICIT_TOKEN is enabled; refusing to boot with a "
        "missing or publicly known development token."
    )

class ExtractionData(BaseModel):
    invoice_number: Optional[str]
    invoice_date: Optional[str]
    due_date: Optional[str]
    vendor_name: Optional[str]
    currency: Optional[str]
    subtotal: Optional[str]
    tax_amount: Optional[str]
    total_amount: Optional[str]

class ConfidenceScores(BaseModel):
    invoice_number: float = 0.0
    invoice_date: float = 0.0
    due_date: float = 0.0
    vendor_name: float = 0.0
    currency: float = 0.0
    subtotal: float = 0.0
    tax_amount: float = 0.0
    total_amount: float = 0.0

class ExtractionResponse(BaseModel):
    status: str
    extracted_data: ExtractionData
    confidence_scores: ConfidenceScores

def parse_money(text: str) -> Optional[str]:
    # Look for money patterns like 1000.00, 1,000.00, etc.
    match = re.search(r'[\d,]+\.\d{2}', text)
    if match:
        val_str = match.group().replace(',', '')
        try:
            return str(Decimal(val_str))
        except:
            return None
    return None

def extract_from_text(text: str) -> (ExtractionData, ConfidenceScores):
    data = ExtractionData(
        invoice_number=None,
        invoice_date=None,
        due_date=None,
        vendor_name=None,
        currency=None,
        subtotal=None,
        tax_amount=None,
        total_amount=None
    )
    conf = ConfidenceScores()
    
    # 1. Vendor Name (Heuristic: first line or near top)
    lines = [line.strip() for line in text.split('\n') if line.strip()]
    if lines:
        data.vendor_name = lines[0]
        conf.vendor_name = 0.8
        
    # 2. Invoice Number
    inv_match = re.search(r'(?i)(?:invoice\s*(?:no|number|#)?\s*[:\-]?\s*)([a-z0-9\-]+)', text)
    if inv_match:
        data.invoice_number = inv_match.group(1).strip()
        conf.invoice_number = 0.95
        
    # 3. Dates
    date_matches = re.finditer(r'(?i)(?:date\s*[:\-]?\s*)(\d{2,4}[-/]\d{1,2}[-/]\d{1,4})', text)
    dates = []
    for m in date_matches:
        dates.append(m.group(1).strip())
    if len(dates) >= 1:
        data.invoice_date = dates[0]
        conf.invoice_date = 0.9
    if len(dates) >= 2:
        data.due_date = dates[1]
        conf.due_date = 0.85
        
    # 4. Totals
    # Find Subtotal
    sub_match = re.search(r'(?i)(?:subtotal|sub-total)\s*[:\-]?\s*[\$€£]?\s*([\d,]+\.\d{2})', text)
    if sub_match:
        data.subtotal = parse_money(sub_match.group(1))
        conf.subtotal = 0.9

    # Find Tax
    tax_match = re.search(r'(?i)(?:tax|vat)\s*[:\-]?\s*[\$€£]?\s*([\d,]+\.\d{2})', text)
    if tax_match:
        data.tax_amount = parse_money(tax_match.group(1))
        conf.tax_amount = 0.9

    # Find Total
    # Guard: "total" must not match inside subtotal variants ("Subtotal",
    # "Sub-total", "Sub Total", incl. case variations and any whitespace
    # split Tesseract may emit). Normalize those variants to "subtotal" first
    # (this copy is used for the total search only), then reject any "total"
    # immediately preceded by "sub".
    total_text = re.sub(r'(?i)sub\s*-\s*total', 'subtotal', text)
    total_text = re.sub(r'(?i)sub\s+total', 'subtotal', total_text)
    total_match = re.search(r'(?i)(?:(?<!sub)total|amount due)\s*[:\-]?\s*[\$€£]?\s*([\d,]+\.\d{2})', total_text)
    if total_match:
        data.total_amount = parse_money(total_match.group(1))
        conf.total_amount = 0.95
        
    # Currency
    if '$' in text or 'USD' in text:
        data.currency = 'USD'
        conf.currency = 0.9
    elif '€' in text or 'EUR' in text:
        data.currency = 'EUR'
        conf.currency = 0.9
    elif '£' in text or 'GBP' in text:
        data.currency = 'GBP'
        conf.currency = 0.9

    return data, conf

@app.post("/extract", response_model=ExtractionResponse)
async def extract_invoice(
    file: UploadFile = File(...),
    authorization: str = Header(None)
):
    if not authorization or authorization != f"Bearer {AI_SERVICE_TOKEN}":
        raise HTTPException(status_code=status.HTTP_401_UNAUTHORIZED, detail="Unauthorized or missing token")
    
    contents = await file.read()
    if not contents:
        raise HTTPException(status_code=400, detail="Empty file")
        
    extracted_text = ""
    try:
        if file.filename.lower().endswith('.pdf') or file.content_type == 'application/pdf':
            doc = fitz.open(stream=contents, filetype="pdf")
            for page in doc:
                pix = page.get_pixmap()
                img = Image.open(io.BytesIO(pix.tobytes()))
                extracted_text += pytesseract.image_to_string(img) + "\n"
        else:
            img = Image.open(io.BytesIO(contents))
            extracted_text += pytesseract.image_to_string(img)
    except Exception as e:
        raise HTTPException(status_code=500, detail=f"Failed to process document: {str(e)}")

    if not extracted_text.strip():
        raise HTTPException(status_code=422, detail="No text could be extracted from document")

    data, conf = extract_from_text(extracted_text)
    
    return ExtractionResponse(
        status="success",
        extracted_data=data,
        confidence_scores=conf
    )

@app.get("/health")
def health_check():
    return {"status": "up"}
