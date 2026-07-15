const CONFIG = {
    PASSPORT_RATIO: 35 / 45,
    CANVAS_WIDTH: 413,
    CANVAS_HEIGHT: 531,
    MAX_FILE_SIZE: 5 * 1024 * 1024,
    SUPPORTED_FORMATS: ['image/jpeg', 'image/png', 'image/webp'],
    MIN_CROP_SIZE: 80,
    COMPRESS_MAX_DIM: 1600,
    COMPRESS_QUALITY: 0.82,
    API_TIMEOUT: 120000,
    FACE_API: 'https://cdn.jsdelivr.net/npm/@mediapipe/face_detection@0.4.1646425229/'
};

const $ = id => document.getElementById(id);
const dom = {
    uploadArea: $('upload-area'),
    fileInput: $('file-input'),
    uploadSection: $('upload-section'),
    cropSection: $('crop-section'),
    previewSection: $('preview-section'),
    loadingSection: $('loading-section'),
    resultSection: $('result-section'),
    errorSection: $('error-section'),
    cropImage: $('crop-image'),
    cropBox: $('crop-box'),
    cropWrapper: $('crop-wrapper'),
    cropDoneBtn: $('crop-done-btn'),
    cropAutoBtn: $('crop-auto-btn'),
    cropCancelBtn: $('crop-cancel-btn'),
    adjustCropBtn: $('adjust-crop-btn'),
    resultImage: $('result-image'),
    generateBtn: $('generate-btn'),
    downloadBtn: $('download-btn'),
    changePhotoBtn: $('change-photo-btn'),
    newPhotoBtn: $('new-photo-btn'),
    tryAgainBtn: $('try-again-btn'),
    errorMessage: $('error-message'),
    previewCanvas: $('crop-preview-canvas'),
    progressFill: $('progress-fill'),
    progressText: $('progress-text'),
    progressLabel: $('progress-label'),
    smoothnessSlider: $('smoothness-slider'),
    smoothnessValue: $('smoothness-value'),
    brightnessSlider: $('brightness-slider'),
    brightnessValue: $('brightness-value'),
    previewContainer: document.querySelector('.preview-container')
};
dom.btnText = dom.generateBtn ? dom.generateBtn.querySelector('.btn-text') : null;
dom.btnSpinner = dom.generateBtn ? dom.generateBtn.querySelector('.spinner') : null;

let currentFile = null;
let generatedImage = null;
let originalImageData = null;
let savedCropData = null;
let cropper = null;
let previewSourceImg = null;

const sections = [
    dom.uploadSection, dom.cropSection, dom.previewSection,
    dom.loadingSection, dom.resultSection, dom.errorSection
];
function showSection(section) {
    sections.forEach(s => { if (s) s.classList.add('hidden'); });
    if (section) section.classList.remove('hidden');
}

const progress = {
    _interval: null,
    _target: 0,
    _current: 0,

    set(value, label) {
        this._target = Math.min(100, Math.max(0, value));
        if (!this._interval) this._animate();
        if (dom.progressLabel) dom.progressLabel.textContent = label || '';
    },

    _animate() {
        this._interval = setInterval(() => {
            const diff = this._target - this._current;
            if (Math.abs(diff) < 0.5) {
                this._current = this._target;
                this._render();
                clearInterval(this._interval);
                this._interval = null;
                return;
            }
            this._current += diff * 0.15;
            this._render();
        }, 50);
    },

    _render() {
        const val = Math.round(this._current);
        if (dom.progressFill) {
            dom.progressFill.style.width = `${val}%`;
            dom.progressFill.setAttribute('aria-valuenow', val);
        }
        if (dom.progressText) dom.progressText.textContent = `${val}%`;
    },

    reset() {
        clearInterval(this._interval);
        this._interval = null;
        this._current = 0;
        this._target = 0;
        this._render();
        if (dom.progressLabel) dom.progressLabel.textContent = '';
    }
};

function compressImage(file) {
    return new Promise((resolve) => {
        const img = new Image();
        img.onload = () => {
            let { width, height } = img;
            if (width > CONFIG.COMPRESS_MAX_DIM || height > CONFIG.COMPRESS_MAX_DIM) {
                const ratio = Math.min(CONFIG.COMPRESS_MAX_DIM / width, CONFIG.COMPRESS_MAX_DIM / height);
                width = Math.round(width * ratio);
                height = Math.round(height * ratio);
            }
            const canvas = document.createElement('canvas');
            canvas.width = width;
            canvas.height = height;
            const ctx = canvas.getContext('2d');
            ctx.drawImage(img, 0, 0, width, height);
            const mime = file.type === 'image/png' ? 'image/png' : 'image/jpeg';
            const quality = mime === 'image/png' ? undefined : CONFIG.COMPRESS_QUALITY;
            canvas.toBlob((blob) => {
                if (blob) {
                    resolve(new File([blob], file.name, { type: mime }));
                } else {
                    resolve(file);
                }
            }, mime, quality);
        };
        img.onerror = () => resolve(file);
        img.src = URL.createObjectURL(file);
    });
}

const faceDetector = {
    _instance: null,
    _loaded: false,
    _loading: false,

    async load() {
        if (this._loaded) return true;
        if (this._loading) {
            while (this._loading) await new Promise(r => setTimeout(r, 200));
            return this._loaded;
        }
        this._loading = true;
        if (typeof FaceDetection === 'undefined') {
            const loaded = await this._loadScript();
            if (!loaded) { this._loading = false; return false; }
        }
        try {
            this._instance = new FaceDetection({
                locateFile: (file) => `${CONFIG.FACE_API}${file}`
            });
            this._instance.setOptions({
                modelSelection: 0,
                minDetectionConfidence: 0.5
            });
            this._loaded = true;
        } catch (e) {
            console.warn('FaceDetection init failed:', e);
        }
        this._loading = false;
        return this._loaded;
    },

    _loadScript() {
        return new Promise((resolve) => {
            const s = document.createElement('script');
            s.src = `${CONFIG.FACE_API}face_detection.js`;
            s.crossOrigin = 'anonymous';
            const timeout = setTimeout(() => { if (s.parentNode) s.parentNode.removeChild(s); resolve(false); }, 10000);
            s.onload = () => { clearTimeout(timeout); resolve(true); };
            s.onerror = () => { clearTimeout(timeout); resolve(false); };
            document.head.appendChild(s);
        });
    },

    detect(image) {
        return new Promise((resolve) => {
            if (!this._instance || !this._loaded) { resolve(null); return; }
            const timeout = setTimeout(() => resolve(null), 15000);
            try {
                this._instance.onResults((results) => {
                    clearTimeout(timeout);
                    if (results.detections && results.detections.length > 0) {
                        resolve(results.detections[0].boundingBox);
                    } else {
                        resolve(null);
                    }
                });
                this._instance.send({ image });
            } catch (e) {
                clearTimeout(timeout);
                console.warn('Face detection send failed:', e);
                resolve(null);
            }
        });
    }
};

class Cropper {
    constructor(wrapper, image, options = {}) {
        this.wrapper = wrapper;
        this.image = image;
        this.box = wrapper.querySelector('.crop-box');
        this.overlay = wrapper.querySelector('.crop-overlay');
        this.ratio = options.ratio || CONFIG.PASSPORT_RATIO;
        this.minSize = options.minSize || CONFIG.MIN_CROP_SIZE;

        this.state = { x: 0, y: 0, width: 0, height: 0 };
        this.dragState = null;
        this._listeners = [];

        this._init();
        this._attachEvents();
    }

    _init() {
        if (!this.wrapper || !this.box) return;
        const wrapW = this.wrapper.clientWidth;
        const wrapH = this.wrapper.clientHeight;
        let boxH = wrapH * 0.5;
        let boxW = boxH * this.ratio;
        if (boxW > wrapW * 0.9) {
            boxW = wrapW * 0.9;
            boxH = boxW / this.ratio;
        }
        this.state.width = Math.round(boxW);
        this.state.height = Math.round(boxH);
        this.state.x = Math.round((wrapW - this.state.width) / 2);
        this.state.y = Math.round((wrapH - this.state.height) / 2);
        this._update();
    }

    _update() {
        if (!this.box) return;
        this.box.style.width = `${this.state.width}px`;
        this.box.style.height = `${this.state.height}px`;
        this.box.style.left = `${this.state.x}px`;
        this.box.style.top = `${this.state.y}px`;
    }

    _attachEvents() {
        this._on = (el, type, fn, opts) => {
            const handler = fn.bind(this);
            el.addEventListener(type, handler, opts);
            this._listeners.push(() => el.removeEventListener(type, handler, opts));
        };
        this._on(this.box, 'pointerdown', this._onPointerDown);
        this._on(document, 'pointermove', this._onPointerMove);
        this._on(document, 'pointerup', this._onPointerUp);
        this._on(document, 'pointercancel', this._onPointerUp);
        this._on(this.box, 'keydown', this._onKeyDown);
        this._on(this.box, 'focus', () => { this._keyboardActive = true; });
        this._on(this.box, 'blur', () => { this._keyboardActive = false; });
    }

    detach() {
        this._listeners.forEach(fn => fn());
        this._listeners = [];
    }

    _onPointerDown(e) {
        if (!this.wrapper) return;
        if (e.button !== undefined && e.button !== 0) return;
        const target = e.target;
        if (target.classList.contains('crop-handle')) {
            this.dragState = { type: 'resize', handle: target.dataset.handle };
        } else {
            this.dragState = { type: 'move' };
            this.box.style.cursor = 'grabbing';
        }
        this.dragState.startX = e.clientX;
        this.dragState.startY = e.clientY;
        this.dragState.startBoxX = this.state.x;
        this.dragState.startBoxY = this.state.y;
        this.dragState.startBoxW = this.state.width;
        this.dragState.startBoxH = this.state.height;
        this.box.setAttribute('aria-grabbed', 'true');
    }

    _onPointerMove(e) {
        if (!this.dragState) return;
        e.preventDefault();
        const dx = e.clientX - this.dragState.startX;
        const dy = e.clientY - this.dragState.startY;
        const wrapW = this.wrapper.clientWidth;
        const wrapH = this.wrapper.clientHeight;
        if (this.dragState.type === 'move') {
            this.state.x = Math.max(0, Math.min(this.dragState.startBoxX + dx, wrapW - this.state.width));
            this.state.y = Math.max(0, Math.min(this.dragState.startBoxY + dy, wrapH - this.state.height));
        } else if (this.dragState.type === 'resize') {
            this._resize(dx, dy, wrapW, wrapH);
        }
        this._update();
    }

    _onPointerUp() {
        if (this.dragState) {
            if (this.dragState.type === 'move' && this.box) {
                this.box.style.cursor = 'grab';
            }
            this.box.setAttribute('aria-grabbed', 'false');
            this.dragState = null;
        }
    }

    _resize(dx, dy, wrapW, wrapH) {
        const { handle } = this.dragState;
        let newX = this.dragState.startBoxX;
        let newY = this.dragState.startBoxY;
        let newW = this.dragState.startBoxW;
        let newH = this.dragState.startBoxH;
        const R = this.ratio;
        const MIN = this.minSize;
        switch (handle) {
            case 'br': newW = Math.max(MIN, this.dragState.startBoxW + dx); newH = newW / R; break;
            case 'bl':
                newW = Math.max(MIN, this.dragState.startBoxW - dx); newH = newW / R;
                newX = this.dragState.startBoxX + this.dragState.startBoxW - newW; break;
            case 'tr':
                newW = Math.max(MIN, this.dragState.startBoxW + dx); newH = newW / R;
                newY = this.dragState.startBoxY + this.dragState.startBoxH - newH; break;
            case 'tl':
                newW = Math.max(MIN, this.dragState.startBoxW - dx); newH = newW / R;
                newX = this.dragState.startBoxX + this.dragState.startBoxW - newW;
                newY = this.dragState.startBoxY + this.dragState.startBoxH - newH; break;
            case 'r': newW = Math.max(MIN, this.dragState.startBoxW + dx); newH = newW / R; break;
            case 'l':
                newW = Math.max(MIN, this.dragState.startBoxW - dx); newH = newW / R;
                newX = this.dragState.startBoxX + this.dragState.startBoxW - newW; break;
            case 'b': newH = Math.max(MIN, this.dragState.startBoxH + dy); newW = newH * R; break;
            case 't':
                newH = Math.max(MIN, this.dragState.startBoxH - dy); newW = newH * R;
                newY = this.dragState.startBoxY + this.dragState.startBoxH - newH; break;
        }
        if (newX < 0) { newW += newX; newH = newW / R; newX = 0; }
        if (newY < 0) { newH += newY; newW = newH * R; newY = 0; }
        if (newX + newW > wrapW) { newW = wrapW - newX; newH = newW / R; }
        if (newY + newH > wrapH) { newH = wrapH - newY; newW = newH * R; }
        if (newW >= MIN && newH >= MIN) {
            this.state.x = Math.round(newX);
            this.state.y = Math.round(newY);
            this.state.width = Math.round(newW);
            this.state.height = Math.round(newH);
        }
    }

    _onKeyDown(e) {
        const step = e.shiftKey ? 10 : 1;
        const wrapW = this.wrapper.clientWidth;
        const wrapH = this.wrapper.clientHeight;
        switch (e.key) {
            case 'ArrowUp': e.preventDefault(); this.state.y = Math.max(0, this.state.y - step); break;
            case 'ArrowDown': e.preventDefault(); this.state.y = Math.min(wrapH - this.state.height, this.state.y + step); break;
            case 'ArrowLeft': e.preventDefault(); this.state.x = Math.max(0, this.state.x - step); break;
            case 'ArrowRight': e.preventDefault(); this.state.x = Math.min(wrapW - this.state.width, this.state.x + step); break;
            default: return;
        }
        this._update();
    }

    getCropData() {
        if (!this.wrapper || !this.image || !this.image.naturalWidth) {
            return { x: 0, y: 0, width: 0, height: 0 };
        }
        return {
            x: Math.round(this.state.x * (this.image.naturalWidth / this.wrapper.clientWidth)),
            y: Math.round(this.state.y * (this.image.naturalHeight / this.wrapper.clientHeight)),
            width: Math.round(this.state.width * (this.image.naturalWidth / this.wrapper.clientWidth)),
            height: Math.round(this.state.height * (this.image.naturalHeight / this.wrapper.clientHeight))
        };
    }

    setFromFaceBBox(bbox) {
        const imgW = this.image.naturalWidth;
        const imgH = this.image.naturalHeight;
        const wrapW = this.wrapper.clientWidth;
        const wrapH = this.wrapper.clientHeight;
        const faceH = bbox.height * imgH;
        const faceCX = bbox.xCenter * imgW;
        const faceCY = bbox.yCenter * imgH;
        let cropH = Math.round(faceH / 0.55);
        let cropW = Math.round(cropH * this.ratio);
        if (cropW > imgW) { cropW = imgW; cropH = Math.round(cropW / this.ratio); }
        if (cropH > imgH) { cropH = imgH; cropW = Math.round(cropH * this.ratio); }
        let cropX = Math.round(faceCX - cropW / 2);
        let cropY = Math.round(faceCY - cropH * 0.38);
        cropX = Math.max(0, Math.min(cropX, imgW - cropW));
        cropY = Math.max(0, Math.min(cropY, imgH - cropH));
        const scale = wrapW / imgW;
        this.state.x = Math.round(cropX * scale);
        this.state.y = Math.round(cropY * scale);
        this.state.width = Math.round(cropW * scale);
        this.state.height = Math.round(cropH * scale);
        this.state.x = Math.max(0, Math.min(this.state.x, wrapW - this.state.width));
        this.state.y = Math.max(0, Math.min(this.state.y, wrapH - this.state.height));
        this._update();
    }

    async autoDetect() {
        if (!this.image || !this.image.naturalWidth) return;
        try {
            const loaded = await faceDetector.load();
            if (!loaded) return;
            const bbox = await faceDetector.detect(this.image);
            if (bbox) this.setFromFaceBBox(bbox);
        } catch (e) {
            console.warn('Auto detect failed:', e);
        }
    }

    reset() {
        this._init();
    }
}

function validateFile(file) {
    if (!CONFIG.SUPPORTED_FORMATS.includes(file.type)) {
        return 'Invalid file type. Please upload JPG, PNG, or WEBP.';
    }
    if (file.size > CONFIG.MAX_FILE_SIZE) {
        return 'File too large. Maximum size is 5MB.';
    }
    return null;
}

function showError(message) {
    if (dom.errorMessage) dom.errorMessage.textContent = message;
    showSection(dom.errorSection);
}

async function handleFile(file) {
    const error = validateFile(file);
    if (error) { showError(error); return; }
    currentFile = file;
    const reader = new FileReader();
    reader.onload = (e) => {
        originalImageData = e.target.result;
        if (!dom.cropImage) { showSection(dom.previewSection); return; }
        dom.cropImage.src = originalImageData;
        dom.cropImage.onload = () => {
            if (dom.cropWrapper && dom.cropBox) {
                showSection(dom.cropSection);
                if (cropper) { cropper.detach(); cropper = null; }
                setTimeout(() => {
                    cropper = new Cropper(dom.cropWrapper, dom.cropImage);
                    setTimeout(() => dom.cropBox && dom.cropBox.focus(), 100);
                }, 50);
            } else {
                showSection(dom.previewSection);
            }
        };
        dom.cropImage.onerror = () => showSection(dom.previewSection);
    };
    reader.onerror = () => showError('Failed to read file. Please try again.');
    reader.readAsDataURL(file);
}

async function generatePassportPhoto() {
    if (!currentFile) return;
    if (dom.generateBtn) dom.generateBtn.disabled = true;
    if (dom.btnText) dom.btnText.textContent = 'Processing...';
    if (dom.btnSpinner) dom.btnSpinner.classList.remove('hidden');
    showSection(dom.loadingSection);
    progress.reset();
    progress.set(2, 'Preparing...');

    const loadingSubText = dom.loadingSection ? dom.loadingSection.querySelector('.sub-text') : null;
    const originalSubText = loadingSubText ? loadingSubText.textContent : '';
    const firstTimeMsg = 'Downloading AI model (~180MB)... This may take 1-2 minutes on first use';

    const msgTimeout = setTimeout(() => {
        if (dom.loadingSection && !dom.loadingSection.classList.contains('hidden') && loadingSubText) {
            loadingSubText.textContent = firstTimeMsg;
        }
    }, 3000);

    try {
        progress.set(10, 'Compressing image...');
        const compressedFile = await compressImage(currentFile);
        progress.set(25, 'Image compressed');

        const bgColorEl = document.querySelector('input[name="bg-color"]:checked');
        const bgColor = bgColorEl ? bgColorEl.value : 'white';

        const formData = new FormData();
        formData.append('image', compressedFile);
        formData.append('bg_color', bgColor);

        const smoothness = dom.smoothnessSlider ? dom.smoothnessSlider.value : 0;
        const brightness = dom.brightnessSlider ? dom.brightnessSlider.value : 100;
        formData.append('smoothness', smoothness);
        formData.append('brightness', brightness);

        const cropData = savedCropData || (cropper ? cropper.getCropData() : { x: 0, y: 0, width: 0, height: 0 });
        formData.append('crop_x', cropData.x);
        formData.append('crop_y', cropData.y);
        formData.append('crop_width', cropData.width);
        formData.append('crop_height', cropData.height);

        const data = await _uploadWithFetch(formData, msgTimeout, loadingSubText, originalSubText);
        clearTimeout(msgTimeout);

        generatedImage = data.image;
        if (dom.resultImage) dom.resultImage.src = generatedImage;
        progress.set(100, 'Done!');
        setTimeout(() => showSection(dom.resultSection), 400);

    } catch (error) {
        clearTimeout(msgTimeout);
        if (loadingSubText) loadingSubText.textContent = originalSubText;
        progress.reset();
        if (error.name === 'AbortError') {
            showError('Request timed out. The first processing may take 1-2 minutes. Please try again.');
        } else {
            showError(error.message || 'Failed to process image. Please try again.');
        }
    } finally {
        if (dom.generateBtn) dom.generateBtn.disabled = false;
        if (dom.btnText) dom.btnText.textContent = 'Generate Passport Photo';
        if (dom.btnSpinner) dom.btnSpinner.classList.add('hidden');
    }
}

async function _uploadWithFetch(formData, msgTimeout, loadingSubText, originalSubText) {
    progress.set(30, 'Uploading...');

    const controller = new AbortController();
    const timeoutId = setTimeout(() => controller.abort(), CONFIG.API_TIMEOUT);

    // Progress simulation
    const simInterval = setInterval(() => {
        progress.set(55, 'Processing on server...');
        clearInterval(simInterval);
    }, 1500);
    const simInterval2 = setInterval(() => {
        progress.set(75, 'Almost done...');
        clearInterval(simInterval2);
    }, 5000);

    try {
        const response = await fetch('/upload', {
            method: 'POST',
            body: formData,
            signal: controller.signal
        });
        clearTimeout(timeoutId);
        clearInterval(simInterval);
        clearInterval(simInterval2);
        clearTimeout(msgTimeout);
        if (loadingSubText) loadingSubText.textContent = originalSubText;

        progress.set(85, 'Processing complete');

        const data = await response.json();
        if (!response.ok) {
            throw new Error(data.error || 'Processing failed');
        }
        return data;
    } catch (error) {
        clearTimeout(timeoutId);
        clearInterval(simInterval);
        clearInterval(simInterval2);
        clearTimeout(msgTimeout);
        if (loadingSubText) loadingSubText.textContent = originalSubText;
        throw error;
    }
}

if (dom.uploadArea && dom.fileInput) {
    dom.uploadArea.addEventListener('click', () => dom.fileInput.click());
    dom.uploadArea.addEventListener('dragover', (e) => { e.preventDefault(); dom.uploadArea.classList.add('dragover'); });
    dom.uploadArea.addEventListener('dragleave', () => { dom.uploadArea.classList.remove('dragover'); });
    dom.uploadArea.addEventListener('drop', (e) => {
        e.preventDefault();
        dom.uploadArea.classList.remove('dragover');
        if (e.dataTransfer.files.length > 0) handleFile(e.dataTransfer.files[0]);
    });
    dom.fileInput.addEventListener('change', (e) => {
        if (e.target.files.length > 0) handleFile(e.target.files[0]);
    });
}

function drawPreview() {
    const canvas = dom.previewCanvas;
    if (!canvas || !previewSourceImg || !previewSourceImg.naturalWidth) return;
    const ctx = canvas.getContext('2d');
    const b = dom.brightnessSlider ? Number(dom.brightnessSlider.value) : 100;
    canvas.width = CONFIG.CANVAS_WIDTH;
    canvas.height = CONFIG.CANVAS_HEIGHT;
    ctx.filter = `brightness(${b / 100})`;
    ctx.fillStyle = '#fff';
    ctx.fillRect(0, 0, canvas.width, canvas.height);
    ctx.drawImage(previewSourceImg, savedCropData.x, savedCropData.y, savedCropData.width, savedCropData.height, 0, 0, canvas.width, canvas.height);
    ctx.filter = 'none';
}

if (dom.cropDoneBtn) {
    dom.cropDoneBtn.addEventListener('click', () => {
        savedCropData = cropper ? cropper.getCropData() : { x: 0, y: 0, width: 0, height: 0 };
        if (!dom.previewCanvas || !dom.cropImage) { showSection(dom.previewSection); return; }
        previewSourceImg = new Image();
        previewSourceImg.onload = () => {
            drawPreview();
            showSection(dom.previewSection);
        };
        previewSourceImg.onerror = () => showSection(dom.previewSection);
        previewSourceImg.src = originalImageData;
    });
}

if (dom.cropAutoBtn) {
    dom.cropAutoBtn.addEventListener('click', async () => {
        if (cropper) {
            dom.cropAutoBtn.disabled = true;
            dom.cropAutoBtn.textContent = 'Detecting...';
            await cropper.autoDetect();
            dom.cropAutoBtn.disabled = false;
            dom.cropAutoBtn.textContent = 'Auto Detect';
        }
    });
}

if (dom.cropCancelBtn) {
    dom.cropCancelBtn.addEventListener('click', () => {
        currentFile = null;
        if (dom.fileInput) dom.fileInput.value = '';
        if (cropper) { cropper.detach(); cropper = null; }
        showSection(dom.uploadSection);
    });
}

if (dom.adjustCropBtn) {
    dom.adjustCropBtn.addEventListener('click', () => {
        showSection(dom.cropSection);
        if (cropper) setTimeout(() => dom.cropBox && dom.cropBox.focus(), 100);
    });
}

if (dom.generateBtn) {
    dom.generateBtn.addEventListener('click', generatePassportPhoto);
}

if (dom.downloadBtn) {
    dom.downloadBtn.addEventListener('click', async () => {
        if (!generatedImage) return;
        try {
            const response = await fetch('/download', {
                method: 'POST',
                headers: { 'Content-Type': 'application/json' },
                body: JSON.stringify({ image: generatedImage })
            });
            if (!response.ok) throw new Error('Download failed');
            const blob = await response.blob();
            const url = window.URL.createObjectURL(blob);
            const a = document.createElement('a');
            a.href = url;
            a.download = 'passport_photo_4x6.jpg';
            document.body.appendChild(a);
            a.click();
            document.body.removeChild(a);
            window.URL.revokeObjectURL(url);
        } catch {
            const a = document.createElement('a');
            a.href = generatedImage;
            a.download = 'passport_photo_4x6.jpg';
            a.click();
        }
    });
}

if (dom.changePhotoBtn) {
    dom.changePhotoBtn.addEventListener('click', () => {
        currentFile = null;
        if (dom.fileInput) dom.fileInput.value = '';
        if (cropper) { cropper.detach(); cropper = null; }
        showSection(dom.uploadSection);
    });
}

if (dom.newPhotoBtn) {
    dom.newPhotoBtn.addEventListener('click', () => {
        currentFile = null;
        generatedImage = null;
        if (dom.fileInput) dom.fileInput.value = '';
        if (dom.resultImage) dom.resultImage.src = '';
        if (cropper) { cropper.detach(); cropper = null; }
        showSection(dom.uploadSection);
    });
}

if (dom.tryAgainBtn) {
    dom.tryAgainBtn.addEventListener('click', () => {
        showSection(dom.uploadSection);
    });
}

if (dom.smoothnessSlider && dom.smoothnessValue) {
    dom.smoothnessSlider.addEventListener('input', () => {
        dom.smoothnessValue.textContent = dom.smoothnessSlider.value;
    });
}

if (dom.brightnessSlider && dom.brightnessValue) {
    dom.brightnessSlider.addEventListener('input', () => {
        dom.brightnessValue.textContent = dom.brightnessSlider.value;
        drawPreview();
    });
}

document.addEventListener('keydown', (e) => {
    if (e.key === 'Escape') {
        if (dom.resultSection && !dom.resultSection.classList.contains('hidden')) {
            if (dom.newPhotoBtn) dom.newPhotoBtn.click();
        } else if (dom.previewSection && !dom.previewSection.classList.contains('hidden')) {
            if (dom.changePhotoBtn) dom.changePhotoBtn.click();
        }
    }
});
