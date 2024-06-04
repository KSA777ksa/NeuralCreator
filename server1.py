import os
import torch
from flask import Flask, request, jsonify, url_for, send_from_directory
from diffusers import StableDiffusionPipeline
from flask_cors import CORS
import logging

os.environ["HF_HUB_DISABLE_SYMLINKS_WARNING"] = "1"

app = Flask(__name__)
CORS(app)

local_model_path = "./stable_diffusion_model"


def download_and_save_model(model_id, local_path):
    pipeline = StableDiffusionPipeline.from_pretrained(model_id, torch_dtype=torch.bfloat16)
    pipeline.save_pretrained(local_path)
    return pipeline


def load_model(local_path):
    pipeline = StableDiffusionPipeline.from_pretrained(local_path, torch_dtype=torch.bfloat16)
    return pipeline


def generate_image(pipeline, prompt, device):
    pipeline = pipeline.to(device)
    with torch.no_grad():
        with torch.autocast("cuda"):
            image = pipeline(prompt).images[0]
    image_path = os.path.join("images", "generated_image.png")
    image.save(image_path)
    while not os.path.exists(image_path):
        pass
    return image_path


@app.route('/generate', methods=['POST'])
def generate():
    data = request.json
    prompt = data.get('prompt')
    if not prompt:
        return jsonify({"error": "No prompt provided"}), 400

    model_id = "CompVis/stable-diffusion-v1-4"
    device = "cuda" if torch.cuda.is_available() else "cpu"

    if not os.path.exists(local_model_path):
        pipeline = download_and_save_model(model_id, local_model_path)
    else:
        pipeline = load_model(local_model_path)

    image_path = generate_image(pipeline, prompt, device)
    image_url = url_for('get_image', filename="generated_image.png", _external=True)

    # Логирование URL изображения
    app.logger.info(f"Generated image URL: {image_url}")

    return jsonify({"image_path": image_url})


@app.route('/images/<path:filename>', methods=['GET'])
def get_image(filename):
    return send_from_directory('images', filename)


if __name__ == "__main__":
    if not os.path.exists("images"):
        os.makedirs("images")
    app.run(host='0.0.0.0', port=5036)
