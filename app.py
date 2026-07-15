from flask import Flask, request, jsonify, send_file, render_template
from flask_cors import CORS
from PIL import Image
import io
import os
import base64

os.environ['U2NET_HOME'] = "/app/.u2net"

app = Flask(__name__)
app.config['TEMPLATES_AUTO_RELOAD'] = True
CORS(app)

os.makedirs('temp', exist_ok=True)
os.makedirs(".u2net", exist_ok=True)

session = None

def get_session():
    global session
    if session is None:
        from rembg import new_session
        try:
            session = new_session('u2net')
        except Exception as e:
            print(f"Error loading model: {e}")
    return session

def refine_hair_alpha(img):
    from PIL import ImageFilter
    r, g, b, a = img.split()
    a = a.filter(ImageFilter.GaussianBlur(radius=0.7))
    return Image.merge('RGBA', (r, g, b, a))

def apply_smoothing(img, smoothness):
    if smoothness <= 0:
        return img
    import cv2, numpy as np
    arr = np.array(img.convert('RGB'))
    d = max(3, (smoothness // 10) * 2 + 1)
    sigma = max(1, smoothness * 0.4)
    filtered = cv2.bilateralFilter(arr, d, sigma, sigma)
    result = Image.fromarray(filtered)
    if img.mode == 'RGBA':
        result.putalpha(img.split()[3])
    return result

def apply_brightness(img, brightness):
    if brightness == 100:
        return img
    from PIL import ImageEnhance
    enhancer = ImageEnhance.Brightness(img.convert('RGB'))
    result = enhancer.enhance(brightness / 100.0)
    if img.mode == 'RGBA':
        result.putalpha(img.split()[3])
    return result

def process_passport_photo(input_image, bg_color):
    from rembg import remove
    if max(input_image.size) > 1000:
        input_image.thumbnail((1000, 1000), Image.Resampling.LANCZOS)

    sess = get_session()
    img_no_bg = remove(
        input_image,
        session=sess,
        alpha_matting=True,
        alpha_matting_foreground_threshold=230,
        alpha_matting_background_threshold=5,
        alpha_matting_erode_size=5
    )

    img_no_bg = refine_hair_alpha(img_no_bg)

    bg_rgb = (3, 152, 252) if bg_color == 'blue' else (255, 255, 255)
    bg_image = Image.new('RGBA', img_no_bg.size, bg_rgb + (255,))
    final_img = Image.alpha_composite(bg_image, img_no_bg).convert('RGB')

    p_width = 330
    p_height = 420

    passport_img = final_img.resize((p_width, p_height), Image.Resampling.LANCZOS)

    border_size = 2
    bordered_w = p_width + (2 * border_size)
    bordered_h = p_height + (2 * border_size)
    bordered_img = Image.new('RGB', (bordered_w, bordered_h), (0, 0, 0))
    bordered_img.paste(passport_img, (border_size, border_size))

    passport_ready = bordered_img.rotate(-90, expand=True)
    final_p_w, final_p_h = passport_ready.size

    layout = Image.new('RGB', (1200, 1800), (255, 255, 255))

    cols = 2
    rows = 4
    gap_x = 70
    gap_y = 60

    total_grid_w = (cols * final_p_w) + ((cols - 1) * gap_x)
    total_grid_h = (rows * final_p_h) + ((rows - 1) * gap_y)

    start_x = (1200 - total_grid_w) // 2
    start_y = (1800 - total_grid_h) // 2

    for r in range(rows):
        for c in range(cols):
            x = start_x + c * (final_p_w + gap_x)
            y = start_y + r * (final_p_h + gap_y)
            layout.paste(passport_ready, (x, y))

    buf = io.BytesIO()
    layout.save(buf, format='JPEG', quality=95)
    buf.seek(0)
    return buf

@app.route('/')
def index():
    return render_template('index.html')

@app.route('/upload', methods=['POST'])
def upload():
    try:
        file = request.files.get('image') or request.files.get('file')
        if not file:
            return jsonify({'error': 'No file provided'}), 400

        bg_color = request.form.get('bg_color', 'white')

        input_image = Image.open(io.BytesIO(file.read())).convert('RGBA')

        crop_x = request.form.get('crop_x', type=int)
        crop_y = request.form.get('crop_y', type=int)
        crop_w = request.form.get('crop_width', type=int)
        crop_h = request.form.get('crop_height', type=int)
        print(f"Received crop params: x={crop_x}, y={crop_y}, w={crop_w}, h={crop_h}", flush=True)
        print(f"Original image size: {input_image.size}", flush=True)
        if all(v is not None for v in (crop_x, crop_y, crop_w, crop_h)):
            crop_x = max(0, crop_x)
            crop_y = max(0, crop_y)
            crop_w = min(crop_w, input_image.width - crop_x)
            crop_h = min(crop_h, input_image.height - crop_y)
            if crop_w > 0 and crop_h > 0:
                input_image = input_image.crop((crop_x, crop_y, crop_x + crop_w, crop_y + crop_h))
                print(f"After crop size: {input_image.size}", flush=True)
            else:
                print("Crop dimensions invalid after clamping", flush=True)
        else:
            print("No crop params received (some are None)", flush=True)

        smoothness = request.form.get('smoothness', 0, type=int)
        brightness = request.form.get('brightness', 100, type=int)
        if smoothness > 0:
            input_image = apply_smoothing(input_image, smoothness)
        if brightness != 100:
            input_image = apply_brightness(input_image, brightness)

        buf = process_passport_photo(input_image, bg_color)

        img_base64 = base64.b64encode(buf.getvalue()).decode()
        return jsonify({'image': f'data:image/jpeg;base64,{img_base64}'})

    except Exception as e:
        return jsonify({'error': str(e)}), 500

@app.route('/generate_android', methods=['POST'])
def generate_android():
    try:
        if 'file' not in request.files:
            return jsonify({'error': 'No file provided'}), 400

        file = request.files['file']
        bg_color = request.form.get('bg_color', 'white')

        input_image = Image.open(io.BytesIO(file.read())).convert('RGBA')

        smoothness = request.form.get('smoothness', 0, type=int)
        brightness = request.form.get('brightness', 100, type=int)
        if smoothness > 0:
            input_image = apply_smoothing(input_image, smoothness)
        if brightness != 100:
            input_image = apply_brightness(input_image, brightness)

        buf = process_passport_photo(input_image, bg_color)

        return send_file(buf, mimetype='image/jpeg')

    except Exception as e:
        return jsonify({'error': str(e)}), 500

@app.route('/download', methods=['POST'])
def download():
    try:
        data = request.get_json()
        if not data or 'image' not in data:
            return jsonify({'error': 'No image data'}), 400

        img_data = data['image']
        if img_data.startswith('data:image'):
            img_data = img_data.split(',', 1)[1]

        buf = io.BytesIO(base64.b64decode(img_data))
        return send_file(buf, mimetype='image/jpeg', as_attachment=True, download_name='passport_photo_4x6.jpg')

    except Exception as e:
        return jsonify({'error': str(e)}), 500

if __name__ == '__main__':
    app.run(host='0.0.0.0', port=7860)
