# Passport Photo 4×6 Generator

A simple web application to generate print-ready 4×6 inch passport photo sheets from uploaded images.

## Features

- 📤 Upload JPG/PNG/WEBP images (max 5MB)
- 🎨 Automatic background removal
- 🖼️ White or Light Blue background options
- 📐 Standard passport size: 35mm × 45mm
- 📄 4×6 inch print layout with 8 photos
- ⬇️ Download print-ready JPEG (300 DPI)

## Requirements

- Python 3.8+
- pip

## Installation

1. Install dependencies:
```bash
pip install -r requirements.txt
```

## Usage

1. Run the Flask application:
```bash
python app.py
```

2. Open your browser and go to `http://localhost:5000`

3. Upload your photo, select background color, and generate your passport photo sheet

## How It Works

1. User uploads an image
2. Background is automatically removed using `rembg`
3. Selected background color (white/blue) is applied
4. Image is cropped to passport size ratio (35:45)
5. 4×6 inch layout is created with 8 passport photos arranged in a grid
6. Output is 300 DPI JPEG ready for printing

## API Endpoints

- `POST /upload` - Upload image and generate passport photo
  - Input: Image file + bg_color (white/blue)
  - Output: Base64 encoded JPEG

- `POST /download` - Download generated image
  - Input: Base64 image data
  - Output: JPEG file attachment

## Print Instructions

1. Download the generated image
2. Print at 4×6 inch size (10×15 cm)
3. Select "Actual Size" or "100%" in print settings
4. Use photo paper for best results
5. Cut along the edges of each photo

## Notes

- First-time processing may take longer as the AI model downloads
- Face centering is automatic based on image center
- Internet connection required for background removal processing
