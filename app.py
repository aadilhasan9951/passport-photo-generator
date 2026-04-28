from flask import Flask, request, jsonify, send_file, render_template
from flask_cors import CORS
from PIL import Image, ImageDraw
import io
import os
import uuid
import base64
from rembg import remove
import numpy as np
import cv2

app = Flask(__name__)
CORS(app)

UPLOAD_FOLDER = 'temp'
ALLOWED_EXTENSIONS = {'png', 'jpg', 'jpeg', 'webp'}

os.makedirs(UPLOAD_FOLDER, exist_ok=True)

# Preload rembg model at startup to prevent timeout during requests
print("Preloading rembg model...")
try:
    from rembg import new_session
    session = new_session('u2net')
    print("Rembg model loaded successfully")
except Exception as e:
    print(f"Error loading rembg model: {e}")
    session = None

app.config['MAX_CONTENT_LENGTH'] = 5 * 1024 * 1024  # 5MB max file size

def allowed_file(filename):
    return '.' in filename and filename.rsplit('.', 1)[1].lower() in ALLOWED_EXTENSIONS

def smart_crop_to_passport(img):
    """Crop image to passport ratio (35:45) with face detection for proper centering."""
    target_ratio = 35 / 45  # width / height
    current_ratio = img.width / img.height
    
    # Convert to numpy array for OpenCV
    img_array = np.array(img)
    
    # Try to detect face
    face_cascade = cv2.CascadeClassifier(cv2.data.haarcascades + 'haarcascade_frontalface_default.xml')
    gray = cv2.cvtColor(img_array, cv2.COLOR_RGB2GRAY)
    faces = face_cascade.detectMultiScale(gray, 1.1, 4)
    
    # Get face center if detected
    if len(faces) > 0:
        # Use the largest face
        largest_face = max(faces, key=lambda f: f[2] * f[3])
        fx, fy, fw, fh = largest_face
        face_center_x = fx + fw // 2
        face_center_y = fy + fh // 2
    else:
        # No face detected, use image center
        face_center_x = img.width // 2
        face_center_y = img.height // 3
    
    # Calculate target dimensions based on image ratio
    if current_ratio > target_ratio:
        # Image is too wide, crop width
        new_width = int(img.height * target_ratio)
        new_height = img.height
        # Center crop on face horizontally
        left = max(0, min(face_center_x - new_width // 2, img.width - new_width))
        top = 0
    else:
        # Image is too tall, crop height
        new_width = img.width
        new_height = int(img.width / target_ratio)
        left = 0
        # Position crop to include face in upper portion
        # Face should be about 25% from top of passport photo
        desired_face_y = int(new_height * 0.25)
        top = max(0, min(face_center_y - desired_face_y, img.height - new_height))
    
    # Ensure crop is within bounds
    left = max(0, left)
    top = max(0, top)
    new_width = min(new_width, img.width - left)
    new_height = min(new_height, img.height - top)
    
    cropped = img.crop((left, top, left + new_width, top + new_height))
    
    # Resize to exact passport dimensions
    return cropped.resize((413, 531), Image.Resampling.LANCZOS)

@app.route('/')
def index():
    return render_template('index.html')

@app.route('/upload', methods=['POST'])
def upload_image():
    try:
        if 'image' not in request.files:
            return jsonify({'error': 'No image file provided'}), 400
        
        file = request.files['image']
        if file.filename == '':
            return jsonify({'error': 'No file selected'}), 400
        
        if not allowed_file(file.filename):
            return jsonify({'error': 'Invalid file type. Only JPG, PNG, WEBP allowed'}), 400
        
        # Read image
        image_bytes = file.read()
        input_image = Image.open(io.BytesIO(image_bytes))
        
        # Convert to RGBA if necessary
        if input_image.mode != 'RGBA':
            input_image = input_image.convert('RGBA')
        
        # Get background color (default: white)
        bg_color = request.form.get('bg_color', 'white')
        
        # Step 1: Crop to passport size (BEFORE background removal for accurate coordinates)
        # Check for manual crop coordinates first
        crop_x = request.form.get('crop_x', type=int)
        crop_y = request.form.get('crop_y', type=int)
        crop_width = request.form.get('crop_width', type=int)
        crop_height = request.form.get('crop_height', type=int)
        
        print(f"DEBUG: Crop coordinates - x:{crop_x}, y:{crop_y}, w:{crop_width}, h:{crop_height}")
        
        if all(v is not None and v > 0 for v in [crop_x, crop_y, crop_width, crop_height]):
            # Use manual crop coordinates on original image
            print(f"DEBUG: Using manual crop on original image")
            cropped_img = input_image.crop((crop_x, crop_y, crop_x + crop_width, crop_y + crop_height))
            # Resize to exact passport size
            cropped_img = cropped_img.resize((413, 531), Image.Resampling.LANCZOS)
        else:
            # Fall back to smart crop with face detection
            print(f"DEBUG: Using smart crop fallback")
            cropped_img = smart_crop_to_passport(input_image)
        
        # Step 2: Remove background from cropped image
        if cropped_img.mode != 'RGBA':
            cropped_img = cropped_img.convert('RGBA')
        if session:
            img_no_bg = remove(cropped_img, session=session)
        else:
            img_no_bg = remove(cropped_img)
        
        # Step 3: Apply solid background
        if bg_color == 'blue':
            bg_rgb = (3, 152, 252)  # Light blue
        elif bg_color == 'green':
            bg_rgb = (190, 252, 3)  # Green
        elif bg_color == 'purple':
            bg_rgb = (242, 114, 223)  # Purple
        elif bg_color == 'red':
            bg_rgb = (230, 96, 96)  # Red
        else:
            bg_rgb = (255, 255, 255)  # White (default)
        
        # Create background image
        bg_image = Image.new('RGBA', img_no_bg.size, bg_rgb + (255,))
        
        # Composite background with foreground
        final_img = Image.alpha_composite(bg_image, img_no_bg)
        
        # Convert to RGB for JPEG
        final_img = final_img.convert('RGB')
        
        # Step 4: Create 4x6 layout (portrait) for vertical photo arrangement
        # 4x6 inch at 300 DPI = 1200 x 1800 pixels (rotated 90 degrees)
        # Passport photo at 300 DPI: 35mm = 413px, 45mm = 531px
        
        layout_width = 1200  # 4 inches * 300 DPI (now width)
        layout_height = 1800  # 6 inches * 300 DPI (now height)
        
        passport_width = 300  # Much smaller to ensure visible gaps
        passport_height = 400  # Much smaller to ensure visible gaps
        
        # Resize passport photo to correct size
        passport_img = final_img.resize((passport_width, passport_height), Image.Resampling.LANCZOS)
        
        # Rotate passport photo 90 degrees to the right (clockwise)
        passport_img = passport_img.rotate(-90, expand=True)
        
        # After rotation, dimensions are swapped
        passport_width, passport_height = passport_img.size
        
        # Add 2px border around the passport photo
        border_size = 2
        bordered_width = passport_width + (2 * border_size)
        bordered_height = passport_height + (2 * border_size)
        bordered_img = Image.new('RGB', (bordered_width, bordered_height), (0, 0, 0))  # Black border
        bordered_img.paste(passport_img, (border_size, border_size))
        passport_img = bordered_img
        
        # Update dimensions for layout calculation
        passport_width = bordered_width
        passport_height = bordered_height
        
        # Create white canvas
        layout = Image.new('RGB', (layout_width, layout_height), (255, 255, 255))
        
        # Calculate grid layout
        # Arrange in 2 columns x 4 rows = 8 photos (vertical layout)
        cols = 2
        rows = 4
        
        # Calculate spacing with extra gap between photos
        # Add minimum 20px gap between photos (border to border)
        min_gap = 20
        
        # Calculate available space for gaps
        total_photo_width = cols * passport_width
        total_photo_height = rows * passport_height
        
        available_width = layout_width - total_photo_width
        available_height = layout_height - total_photo_height
        
        # Distribute gaps evenly between photos and edges
        margin_x = available_width // (cols + 1)
        margin_y = available_height // (rows + 1)
        
        # Ensure minimum gap
        margin_x = max(min_gap, margin_x)
        margin_y = max(min_gap, margin_y)
        
        # Paste photos in grid
        for row in range(rows):
            for col in range(cols):
                x = margin_x + col * (passport_width + margin_x)
                y = margin_y + row * (passport_height + margin_y)
                layout.paste(passport_img, (x, y))
        
        # Save to bytes
        output_buffer = io.BytesIO()
        layout.save(output_buffer, format='JPEG', quality=95, dpi=(300, 300))
        output_buffer.seek(0)
        
        # Convert to base64 for response
        img_base64 = base64.b64encode(output_buffer.getvalue()).decode('utf-8')
        
        return jsonify({
            'success': True,
            'image': f'data:image/jpeg;base64,{img_base64}',
            'message': 'Passport photo generated successfully'
        })
        
    except Exception as e:
        return jsonify({'error': str(e)}), 500

@app.route('/generate', methods=['POST'])
def generate():
    try:
        if 'file' not in request.files:
            return jsonify({'error': 'No file provided'}), 400
        
        file = request.files['file']
        if file.filename == '':
            return jsonify({'error': 'No file selected'}), 400
        
        if not allowed_file(file.filename):
            return jsonify({'error': 'Invalid file type'}), 400
        
        # Get background color (default: white)
        bg_color = request.form.get('bg_color', 'white')
        
        # Step 1: Crop to passport size (BEFORE background removal for accurate coordinates)
        
        # Read image
        image_bytes = file.read()
        input_image = Image.open(io.BytesIO(image_bytes))
        
        # Convert to RGBA if necessary
        if input_image.mode != 'RGBA':
            input_image = input_image.convert('RGBA')
        
        # Step 1: Remove background
        if session:
            img_no_bg = remove(input_image, session=session)
        else:
            img_no_bg = remove(input_image)
        
        # Step 2: Apply solid background
        if bg_color == 'blue':
            bg_rgb = (3, 152, 252)  # Light blue
        elif bg_color == 'gray':
            bg_rgb = (220, 220, 220)  # Light gray
        elif bg_color == 'cream':
            bg_rgb = (255, 248, 220)  # Cream/beige
        elif bg_color == 'pink':
            bg_rgb = (255, 228, 225)  # Light pink
        elif bg_color == 'green':
            bg_rgb = (230, 255, 240)  # Mint green
        else:
            bg_rgb = (255, 255, 255)  # White (default)
        
        # Create background image
        bg_image = Image.new('RGBA', img_no_bg.size, bg_rgb + (255,))
        
        # Composite background with foreground
        final_img = Image.alpha_composite(bg_image, img_no_bg)
        
        # Convert to RGB for JPEG
        final_img = final_img.convert('RGB')
        
        # Step 3: Create 4x6 layout (portrait)
        layout_width = 1200
        layout_height = 1800
        
        passport_width = 300
        passport_height = 400
        
        # Resize passport photo to correct size
        passport_img = final_img.resize((passport_width, passport_height), Image.Resampling.LANCZOS)
        
        # Rotate passport photo 90 degrees to the right (clockwise)
        passport_img = passport_img.rotate(-90, expand=True)
        
        # After rotation, dimensions are swapped
        passport_width, passport_height = passport_img.size
        
        # Add 2px border
        border_size = 2
        bordered_width = passport_width + (2 * border_size)
        bordered_height = passport_height + (2 * border_size)
        bordered_img = Image.new('RGB', (bordered_width, bordered_height), (0, 0, 0))
        bordered_img.paste(passport_img, (border_size, border_size))
        passport_img = bordered_img
        
        passport_width = bordered_width
        passport_height = bordered_height
        
        # Create white canvas
        layout = Image.new('RGB', (layout_width, layout_height), (255, 255, 255))
        
        # Calculate grid layout (2 columns x 4 rows)
        cols = 2
        rows = 4
        
        # Calculate spacing with minimum gap between photos
        min_gap = 20
        total_photo_width = cols * passport_width
        total_photo_height = rows * passport_height
        available_width = layout_width - total_photo_width
        available_height = layout_height - total_photo_height
        margin_x = available_width // (cols + 1)
        margin_y = available_height // (rows + 1)
        margin_x = max(min_gap, margin_x)
        margin_y = max(min_gap, margin_y)
        
        # Paste photos in grid
        for row in range(rows):
            for col in range(cols):
                x = margin_x + col * (passport_width + margin_x)
                y = margin_y + row * (passport_height + margin_y)
                layout.paste(passport_img, (x, y))
        
        # Save to bytes
        output_buffer = io.BytesIO()
        layout.save(output_buffer, format='JPEG', quality=95, dpi=(300, 300))
        output_buffer.seek(0)
        
        return send_file(
            output_buffer,
            mimetype='image/jpeg',
            as_attachment=False,
            download_name='passport_photo.jpg'
        )
        
    except Exception as e:
        print(f"Error: {e}")
        return jsonify({'error': str(e)}), 500

@app.route('/generate_android', methods=['POST'])
def generate_android():
    """API endpoint for Android app - accepts cropped image and returns generated layout"""
    try:
        if 'file' not in request.files:
            return jsonify({'error': 'No file provided'}), 400
        
        file = request.files['file']
        if file.filename == '':
            return jsonify({'error': 'No file selected'}), 400
        
        if not allowed_file(file.filename):
            return jsonify({'error': 'Invalid file type'}), 400
        
        # Get background color (default: white)
        bg_color = request.form.get('bg_color', 'white')
        
        # Read image
        image_bytes = file.read()
        input_image = Image.open(io.BytesIO(image_bytes))
        
        # Convert to RGBA if necessary
        if input_image.mode != 'RGBA':
            input_image = input_image.convert('RGBA')
        
        # Step 1: Remove background
        if session:
            img_no_bg = remove(input_image, session=session)
        else:
            img_no_bg = remove(input_image)
        
        # Step 2: Apply solid background
        if bg_color == 'blue':
            bg_rgb = (3, 152, 252)  # Light blue
        elif bg_color == 'gray':
            bg_rgb = (220, 220, 220)  # Light gray
        elif bg_color == 'cream':
            bg_rgb = (255, 248, 220)  # Cream/beige
        elif bg_color == 'pink':
            bg_rgb = (255, 228, 225)  # Light pink
        elif bg_color == 'green':
            bg_rgb = (230, 255, 240)  # Mint green
        else:
            bg_rgb = (255, 255, 255)  # White (default)
        
        # Create background image
        bg_image = Image.new('RGBA', img_no_bg.size, bg_rgb + (255,))
        
        # Composite background with foreground
        final_img = Image.alpha_composite(bg_image, img_no_bg)
        
        # Convert to RGB for JPEG
        final_img = final_img.convert('RGB')
        
        # Step 3: Create 4x6 layout (portrait)
        layout_width = 1200
        layout_height = 1800
        
        passport_width = 300
        passport_height = 400
        
        # Resize passport photo to correct size
        passport_img = final_img.resize((passport_width, passport_height), Image.Resampling.LANCZOS)
        
        # Rotate passport photo 90 degrees to the right (clockwise)
        passport_img = passport_img.rotate(-90, expand=True)
        
        # After rotation, dimensions are swapped
        passport_width, passport_height = passport_img.size
        
        # Add 2px border
        border_size = 2
        bordered_width = passport_width + (2 * border_size)
        bordered_height = passport_height + (2 * border_size)
        bordered_img = Image.new('RGB', (bordered_width, bordered_height), (0, 0, 0))
        bordered_img.paste(passport_img, (border_size, border_size))
        passport_img = bordered_img
        
        passport_width = bordered_width
        passport_height = bordered_height
        
        # Create white canvas
        layout = Image.new('RGB', (layout_width, layout_height), (255, 255, 255))
        
        # Calculate grid layout (2 columns x 4 rows)
        cols = 2
        rows = 4
        
        # Calculate spacing with minimum gap between photos
        min_gap = 20
        total_photo_width = cols * passport_width
        total_photo_height = rows * passport_height
        available_width = layout_width - total_photo_width
        available_height = layout_height - total_photo_height
        margin_x = available_width // (cols + 1)
        margin_y = available_height // (rows + 1)
        margin_x = max(min_gap, margin_x)
        margin_y = max(min_gap, margin_y)
        
        # Paste photos in grid
        for row in range(rows):
            for col in range(cols):
                x = margin_x + col * (passport_width + margin_x)
                y = margin_y + row * (passport_height + margin_y)
                layout.paste(passport_img, (x, y))
        
        # Save to bytes
        output_buffer = io.BytesIO()
        layout.save(output_buffer, format='JPEG', quality=95, dpi=(300, 300))
        output_buffer.seek(0)
        
        return send_file(
            output_buffer,
            mimetype='image/jpeg',
            as_attachment=False,
            download_name='passport_photo.jpg'
        )
        
    except Exception as e:
        print(f"Error: {e}")
        return jsonify({'error': str(e)}), 500

@app.route('/download', methods=['POST'])
def download_image():
    try:
        data = request.get_json()
        img_data = data.get('image', '').replace('data:image/jpeg;base64,', '')
        
        if not img_data:
            return jsonify({'error': 'No image data provided'}), 400
        
        img_bytes = base64.b64decode(img_data)
        output_buffer = io.BytesIO(img_bytes)
        output_buffer.seek(0)
        
        return send_file(
            output_buffer,
            mimetype='image/jpeg',
            as_attachment=True,
            download_name='passport_photo_4x6.jpg'
        )
        
    except Exception as e:
        return jsonify({'error': str(e)}), 500

if __name__ == '__main__':
    port = int(os.environ.get('PORT', 5000))
    app.run(debug=False, host='0.0.0.0', port=port)
