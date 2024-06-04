from flask import Flask, request, jsonify
from transformers import BlipProcessor, BlipForConditionalGeneration
from PIL import Image
import io
import os

app = Flask(__name__)

model_path = os.getenv("MODEL_PATH", "./blip_saved_model")
processor = BlipProcessor.from_pretrained(model_path)
model = BlipForConditionalGeneration.from_pretrained(model_path)


@app.route('/generate_caption', methods=['POST'])
def generate_caption():
    try:
        if 'image' not in request.files:
            return jsonify({"error": "No image provided"}), 400

        image_file = request.files['image']
        image = Image.open(io.BytesIO(image_file.read())).convert("RGB")
        inputs = processor(images=image, return_tensors="pt")

        output = model.generate(**inputs)
        caption = processor.decode(output[0], skip_special_tokens=True)

        return jsonify({"caption": caption})

    except Exception as e:
        return jsonify({"error": str(e)}), 500


if __name__ == '__main__':
    app.run(host='0.0.0.0', port=5032)
