import gradio as gr
from PIL import Image, ImageFilter, ImageEnhance
import io
import os
import base64
import cv2
import numpy as np

os.environ['U2NET_HOME'] = os.environ.get('U2NET_HOME', '/app/.u2net')
os.makedirs('.u2net', exist_ok=True)

session = None

def get_session():
    global session
    if session is None:
        from rembg import new_session
        session = new_session('u2net')
    return session

def refine_hair_alpha(img):
    r, g, b, a = img.split()
    a = a.filter(ImageFilter.GaussianBlur(radius=0.7))
    return Image.merge('RGBA', (r, g, b, a))

def apply_smoothing(img, smoothness):
    if smoothness <= 0: return img
    arr = np.array(img.convert('RGB'))
    d = max(3, (smoothness // 10) * 2 + 1)
    sigma = max(1, smoothness * 0.4)
    filtered = cv2.bilateralFilter(arr, d, sigma, sigma)
    result = Image.fromarray(filtered)
    if img.mode == 'RGBA': result.putalpha(img.split()[3])
    return result

def apply_brightness(img, brightness):
    if brightness == 100: return img
    enhancer = ImageEnhance.Brightness(img.convert('RGB'))
    result = enhancer.enhance(brightness / 100.0)
    if img.mode == 'RGBA': result.putalpha(img.split()[3])
    return result

def process_image(input_image, bg_color, smoothness, brightness):
    from rembg import remove

    img = input_image.convert('RGBA')
    if max(img.size) > 1000:
        img.thumbnail((1000, 1000), Image.Resampling.LANCZOS)

    img = apply_smoothing(img, smoothness)
    img = apply_brightness(img, brightness)

    sess = get_session()
    img_no_bg = remove(img, session=sess, alpha_matting=True,
                       alpha_matting_foreground_threshold=230,
                       alpha_matting_background_threshold=5,
                       alpha_matting_erode_size=5)
    img_no_bg = refine_hair_alpha(img_no_bg)

    bg_rgb = (3, 152, 252) if bg_color == 'blue' else (255, 255, 255)
    bg_image = Image.new('RGBA', img_no_bg.size, bg_rgb + (255,))
    final_img = Image.alpha_composite(bg_image, img_no_bg).convert('RGB')

    p_width, p_height = 330, 420
    passport_img = final_img.resize((p_width, p_height), Image.Resampling.LANCZOS)

    border_size = 2
    bordered_w = p_width + (2 * border_size)
    bordered_h = p_height + (2 * border_size)
    bordered_img = Image.new('RGB', (bordered_w, bordered_h), (0, 0, 0))
    bordered_img.paste(passport_img, (border_size, border_size))

    passport_ready = bordered_img.rotate(-90, expand=True)
    final_p_w, final_p_h = passport_ready.size

    layout = Image.new('RGB', (1200, 1800), (255, 255, 255))
    cols, rows, gap_x, gap_y = 2, 4, 70, 60
    total_grid_w = (cols * final_p_w) + ((cols - 1) * gap_x)
    total_grid_h = (rows * final_p_h) + ((rows - 1) * gap_y)
    start_x = (1200 - total_grid_w) // 2
    start_y = (1800 - total_grid_h) // 2

    for r in range(rows):
        for c in range(cols):
            x = start_x + c * (final_p_w + gap_x)
            y = start_y + r * (final_p_h + gap_y)
            layout.paste(passport_ready, (x, y))

    return layout

demo = gr.Interface(
    fn=process_image,
    inputs=[
        gr.Image(type="pil", label="Upload Photo", sources=["upload"]),
        gr.Radio(choices=["white", "blue"], value="white", label="Background Color"),
        gr.Slider(minimum=0, maximum=100, value=0, step=1, label="Smoothness"),
        gr.Slider(minimum=50, maximum=150, value=100, step=1, label="Brightness"),
    ],
    outputs=gr.Image(type="pil", label="Passport Photo Sheet (4x6)"),
    title="Passport Photo 4x6 Generator",
    description="Upload a photo, adjust smoothness/brightness, and get a print-ready 4x6 passport photo sheet.",
    allow_flagging="never",
)

if __name__ == "__main__":
    demo.launch(server_name="0.0.0.0", server_port=7860)
