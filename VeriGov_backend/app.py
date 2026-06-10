import os
import io
import hashlib
import jwt
import datetime
from datetime import datetime, timezone, timedelta
from flask import Flask, request, jsonify
from flask_sqlalchemy import SQLAlchemy
from werkzeug.utils import secure_filename
from werkzeug.security import generate_password_hash, check_password_hash
from functools import wraps
from flask import send_from_directory
# --- PYTORCH & COMPUTER VISION IMPORTS ---
import torch
import torch.nn as nn
from torchvision import models, transforms
from PIL import Image
from sentence_transformers import SentenceTransformer, util
from flask_cors import CORS  

app = Flask(__name__)

# Enable Global Cross-Origin Requests specifically for local React development servers
CORS(app, resources={r"/*": {"origins": "*"}})

# 1. Configuration Core
app.config['SECRET_KEY'] = 'verigov_secure_cryptographic_jwt_key_2026'
# app.config['SQLALCHEMY_DATABASE_URI'] = 'postgresql://postgres:abcd1234@localhost:5432/verigov_db'
app.config['SQLALCHEMY_DATABASE_URI'] = os.getenv('DATABASE_URL')
app.config['UPLOAD_FOLDER'] = 'Data/uploads'
app.config['SQLALCHEMY_TRACK_MODIFICATIONS'] = False

db = SQLAlchemy(app)

# 1. Get the directory where app.py actually lives (verigov/backend)
BACKEND_DIR = os.path.dirname(os.path.abspath(__file__))

# 2. Go up one level to verigov/, then down into data/uploads
UPLOAD_FOLDER = os.path.abspath(os.path.join(BACKEND_DIR, '..', 'Data', 'uploads'))

print(f"[*] Flask Sentry: Mapping asset pipeline to: {UPLOAD_FOLDER}")

# --- 2. DATABASE MODELS LAYERS (With Relational Constraints) ---
class User(db.Model):
    __tablename__ = 'users'
    id = db.Column(db.Integer, primary_key=True)
    citizen_name = db.Column(db.String(100), nullable=False)
    email = db.Column(db.String(120), unique=True, nullable=False)
    password_hash = db.Column(db.String(255), nullable=False)
    
    # Cascade deletes to cleanly wipe logs if a profile drops out
    grievances = db.relationship('Grievance', backref='reporter', lazy=True, cascade="all, delete-orphan")

class Grievance(db.Model):
    __tablename__ = 'grievances'
    id = db.Column(db.Integer, primary_key=True)
    user_id = db.Column(db.Integer, db.ForeignKey('users.id', ondelete='CASCADE'), nullable=False) # Linked Anchor
    description = db.Column(db.Text)
    category = db.Column(db.String(50))      
    priority_score = db.Column(db.Integer)   
    confidence_level = db.Column(db.String(20)) 
    image_url = db.Column(db.Text)
    location_gps = db.Column(db.String(100))
    sha256_hash = db.Column(db.String(64))
    status = db.Column(db.String(50), default='Pending') # Broadened width bound to prevent truncation overflow
    created_at = db.Column(db.DateTime, default=lambda: datetime.now(timezone.utc))

    def to_dict(self):
        return {
            "id": self.id,
            "description": self.description,
            "category": self.category,
            "priority_score": self.priority_score,
            "location_gps": self.location_gps,
            "sha256_hash": self.sha256_hash,
            "confidence_level": self.confidence_level.upper() if self.confidence_level else "HIGH",
            "status": self.status,
            "created_at": self.created_at.strftime('%Y-%m-%d %H:%M:%S') if self.created_at else 'Just Now',
            "image_url": self.image_url if self.image_url else ""
        }

# --- 3. JWT SECURITY DECORATOR ---
def token_required(f):
    @wraps(f)
    def decorated(*args, **kwargs):
        token = None
        if 'Authorization' in request.headers:
            auth_header = request.headers['Authorization']
            if auth_header.startswith("Bearer "):
                token = auth_header.split(" ")[1]
        
        if not token:
            return jsonify({"error": "Access Token validation failed. Header missing."}), 401
        
        try:
            data = jwt.decode(token, app.config['SECRET_KEY'], algorithms=["HS256"])
            current_user = User.query.get(data['user_id'])
            if not current_user:
                return jsonify({"error": "User account contextual target not found"}), 401
        except jwt.ExpiredSignatureError:
            return jsonify({"error": "Session token has expired. Please authenticate again."}), 401
        except jwt.InvalidTokenError:
            return jsonify({"error": "Invalid token signature cryptographic block verification failed."}), 401
            
        return f(current_user, *args, **kwargs)
    return decorated

# --- 4. PRE-TRAINED MODELS LIFECYCLE INITIALIZATION ---
device = torch.device("cpu")
class_names = ['clean', 'potholes', 'road_garbage', 'water_logging']

print("🤖 Loading pre-trained ResNet50 Core into memory...")
model = models.resnet50()
num_ftrs = model.fc.in_features
model.fc = nn.Linear(num_ftrs, len(class_names))

MODEL_PATH = '/home/priyanksu/Documents/6th semester project/VeriGov_project/VeriGov_backend/models/verigov_resnet.pth'

if os.path.exists(MODEL_PATH):
    checkpoint = torch.load(MODEL_PATH, map_location=device)
    if isinstance(checkpoint, dict) and 'model_state_dict' in checkpoint:
        model.load_state_dict(checkpoint['model_state_dict'])
    else:
        model.load_state_dict(checkpoint)
    print(f"🎉 ResNet50 weights loaded successfully from: {MODEL_PATH}")
else:
    print(f"❌ WARNING: Model weights not found at {MODEL_PATH}. Inference will fail.")

model.to(device)
model.eval()

inference_transforms = transforms.Compose([
    transforms.Resize((224, 224)),
    transforms.ToTensor(),
    transforms.Normalize([0.485, 0.456, 0.406], [0.229, 0.224, 0.225])
])

priority_mapping = {"potholes": 3, "water_logging": 5, "road_garbage": 2, "clean": 0}

print("🧠 Loading Self-Contained Local NLP Embedding Model (MiniLM)...")
nlp_embedder = SentenceTransformer('/home/priyanksu/Documents/6th semester project/VeriGov_project/VeriGov_backend/models/all-MiniLM-L6-v2')
nlp_anchors = {
    "potholes": "pothole broken road cracked asphalt crater street damage street cavity",
    "water_logging": "water logging flooding rain water puddle blocked drainage sewer overflow",
    "road_garbage": "road garbage trash pile waste dumping public litter refuse street dump"
}

def auto_verify_text_semantics(user_description, ai_category):
    if not user_description.strip() or ai_category not in nlp_anchors:
        return "Verified", 1.0
    target_concept = nlp_anchors[ai_category]
    embedding_target = nlp_embedder.encode(target_concept, convert_to_tensor=True)
    embedding_user = nlp_embedder.encode(user_description, convert_to_tensor=True)
    similarity_score = float(util.cos_sim(embedding_target, embedding_user)[0][0])
    print(f"🧠 NLP Semantic Similarity Score: {similarity_score:.4f}")
    status = "Verified" if similarity_score >= 0.40 else "Text Mismatch Warning"
    return status, similarity_score

# --- 5. IMMUTABLE CITIZEN CREDENTIAL TRACKING ROUTER ---
@app.route('/api/auth/register', methods=['POST'])
def register():
    data = request.get_json() or {}
    if not data.get('email') or not data.get('password') or not data.get('name'):
        return jsonify({"error": "Missing registration criteria "}), 400
        
    if User.query.filter_by(email=data['email']).first():
        return jsonify({"error": "This citizen email is already registered."}), 400
        
    hashed_password = generate_password_hash(data['password'], method='pbkdf2:sha256')
    new_citizen = User(citizen_name=data['name'], email=data['email'], password_hash=hashed_password)
    
    db.session.add(new_citizen)
    db.session.commit()
    return jsonify({"message": "Citizen identity created successfully!"}), 201

@app.route('/api/auth/login', methods=['POST'])
def login():
    data = request.get_json() or {}
    user = User.query.filter_by(email=data.get('email')).first()
    
    if not user or not check_password_hash(user.password_hash, data.get('password')):
        return jsonify({"error": "Invalid signature credentials mismatch."}), 401
        
    token = jwt.encode({
        'user_id': user.id,
        'exp': datetime.now(timezone.utc) + timedelta(hours=24)
    }, app.config['SECRET_KEY'], algorithm="HS256")
    
    return jsonify({"token": token, "name": user.citizen_name}), 200

# --- 6. AUTH PROTECTED SECURE ACTION SUBMIT ROUTE ---
@app.route('/submit_grievance', methods=['POST'])
@token_required
def submit_grievance(current_user): 
    if 'image' not in request.files:
        return jsonify({"error": "Proof of Work image required"}), 400
    
    image_file = request.files['image']
    description = request.form.get('description', '')
    location = request.form.get('location_gps', '0.0, 0.0')

    img_bytes = image_file.read()

    try:
        image = Image.open(io.BytesIO(img_bytes)).convert('RGB')
        tensor = inference_transforms(image).unsqueeze(0).to(device)
        
        with torch.no_grad():
            outputs = model(tensor)
            probabilities = torch.nn.functional.softmax(outputs, dim=1)[0]
            highest_idx = torch.argmax(probabilities).item()
            ai_category = class_names[highest_idx]
            vision_confidence = probabilities[highest_idx].item()
        
        print(f"🎯 AI Vision Detection: '{ai_category}' | Confidence: {vision_confidence:.4f}")
    except Exception as e:
        return jsonify({"error": f"AI Processing Engine Failure: {str(e)}"}), 500

    if ai_category == "clean":
        return jsonify({"error": "Submission Rejected: AI evaluated this infrastructure as clean."}), 400

    if vision_confidence < 0.50:
        return jsonify({"error": "Rejected: Image definition is too poor or ambiguous for categorization."}), 400

    if vision_confidence >= 0.70:
        confidence_level = "HIGH"
        ai_priority = priority_mapping.get(ai_category, 1)
    else:
        confidence_level = "LOW"
        ai_priority = 1  

    text_validation_status, nlp_score = auto_verify_text_semantics(description, ai_category)
    
    user_desc_lower = description.lower()
    indoor_anomalies = ['room', 'bed', 'studying', 'kitchen', 'table', 'chair', 'bedroom', 'sofa']
    has_indoor_mismatch = any(anomaly in user_desc_lower for anomaly in indoor_anomalies)

    if (has_indoor_mismatch and ai_category in ['potholes', 'water_logging']) or (text_validation_status == "Text Mismatch Warning" and nlp_score > 0.60):
        return jsonify({"error": "Rejected: Description text details conflict with structural environment!"}), 400

    final_description_entry = f"[{text_validation_status}] {description}".strip()

    timestamp_now = datetime.now(timezone.utc)
    filename = secure_filename(f"{int(timestamp_now.timestamp())}_{image_file.filename}")
    image_path = os.path.join(app.config['UPLOAD_FOLDER'], filename)
    os.makedirs(app.config['UPLOAD_FOLDER'], exist_ok=True)
    
    with open(image_path, 'wb') as f:
        f.write(img_bytes)

    timestamp_str = timestamp_now.isoformat()
    raw_data = f"{final_description_entry}{location}{image_path}{timestamp_str}"
    report_hash = hashlib.sha256(raw_data.encode()).hexdigest()

    try:
        new_report = Grievance(
            user_id=current_user.id, 
            description=final_description_entry,
            location_gps=location,
            image_url=image_path,
            category=ai_category,
            priority_score=ai_priority,
            confidence_level=confidence_level,  
            sha256_hash=report_hash,
            status="Pending Review" if confidence_level == "LOW" else "Pending"
        )
        db.session.add(new_report)
        db.session.commit()
    except Exception as e:
        db.session.rollback()
        return jsonify({"error": "Failed to safely commit ledger to relational store"}), 500

    return jsonify({
        "message": "Grievance securely verified and logged by AI",
        "category_detected": ai_category,
        "priority_assigned": ai_priority,
        "hash": report_hash,
        "status": new_report.status
    }), 201

# --- 7. SECURE CITIZEN SPECIFIC HISTORY ACCESS POINT ---
@app.route('/api/history', methods=['GET'])
@token_required
def get_user_history(current_user):
    try:
        reports = Grievance.query.filter_by(user_id=current_user.id).order_by(Grievance.created_at.desc()).all()
        results = []
        for r in reports:
            results.append({
                "id": r.id, "description": r.description, "category": r.category,
                "priority_score": r.priority_score, "confidence_level": r.confidence_level,
                "location_gps": r.location_gps, "sha256_hash": r.sha256_hash, "status": r.status,
                "created_at": r.created_at.strftime('%Y-%m-%d %H:%M:%S')
            })
        return jsonify(results), 200
    except Exception as e:
        return jsonify({"error": str(e)}), 500

# --- 8. CENTRALIZED SYSTEM PANELS / ADMINISTRATIVE DISCOVERY DASHBOARD ---
@app.route('/dashboard', methods=['GET'])
def get_dashboard_data():
    try:
        view_filter = request.args.get('view', 'all')
        
        if view_filter == 'high':
            records = Grievance.query.filter_by(confidence_level='HIGH').order_by(Grievance.priority_score.desc()).all()
        elif view_filter == 'low':
            records = Grievance.query.filter_by(confidence_level='LOW').order_by(Grievance.created_at.desc()).all()
        else:
            records = Grievance.query.order_by(Grievance.confidence_level.desc(), Grievance.priority_score.desc()).all()
            
        results = []
        for r in records:
            results.append({
                "id": r.id, 
                "description": r.description, 
                "category": r.category,
                "priority_score": r.priority_score, 
                "confidence_level": r.confidence_level.upper() if r.confidence_level else "HIGH",
                "location_gps": r.location_gps, 
                "sha256_hash": r.sha256_hash, 
                "status": r.status if r.status else "Pending Review",
                "created_at": r.created_at.strftime('%Y-%m-%d %H:%M:%S') if r.created_at else 'Just Now',
                "image_url": r.image_url if r.image_url else "" 
            })
            
        return jsonify(results), 200
        
    except Exception as e:
        print(f"CRITICAL DASHBOARD DATA SYNC ERROR: {str(e)}")
        return jsonify({"error": "Internal ledger pipeline crash", "details": str(e)}), 500

# --- 9. ADMINISTRATIVE CONTROL PIPELINE: CLAIM JOB NODE ---
@app.route('/api/grid_task/<int:grievance_id>/claim', methods=['POST'])
def claim_grievance(grievance_id):
    try:
        report = Grievance.query.get(grievance_id)
        if not report:
            return jsonify({"error": "Target incident ledger record not found"}), 404
            
        # Update the structural field parameter to change task processing state
        report.status = "Under Investigation"
        db.session.commit()
        
        return jsonify({
            "message": "Task assigned to field official unit successfully", 
            "current_status": report.status
        }), 200
    except Exception as e:
        db.session.rollback()
        return jsonify({"error": "Database update mutation rejected", "details": str(e)}), 500


# Change the route path matching rule to include /Data/uploads/
@app.route('/Data/uploads/<filename>', methods=['GET'])
def serve_uploaded_file(filename):
    """Safely intercepts the exact path requested by the React Engine."""
    try:
        file_path = os.path.join(UPLOAD_FOLDER, filename)
        
        if not os.path.exists(file_path):
            print(f"[!] Target Not Found: {file_path}")
            return {"error": "Image asset not found on disk"}, 404
            
        response = send_from_directory(UPLOAD_FOLDER, filename)
        response.headers['Access-Control-Allow-Origin'] = '*'
        return response
        
    except Exception as e:
        return {"error": f"Internal file server exception: {str(e)}"}, 500
    
# --- 10. ADMINISTRATIVE CONTROL PIPELINE: RESOLVE JOB NODE ---
@app.route('/api/report/update_status/<int:incident_id>', methods=['POST'])
def update_incident_status(incident_id):
    """Transitions a grievance record's status to 'Resolved' inside the PostgreSQL system."""
    try:
        # Query the database using your existing Grievance model context
        report = Grievance.query.get(incident_id)
        if not report:
            return jsonify({"error": "Target incident record not found"}), 404
            
        data = request.get_json() or {}
        new_status = data.get('status', 'Resolved')
        
        # Mutation: update the column data field
        report.status = new_status
        db.session.commit()
        
        print(f"[*] Lifecycle Transition: Report #{incident_id} successfully updated to '{new_status}'")
        
        response = jsonify({
            "status": "Success", 
            "message": f"Incident #{incident_id} state updated to {new_status}"
        })
        # The global CORS setup will inherit this, but explicit definition guarantees safe passage
        response.headers['Access-Control-Allow-Origin'] = '*'
        return response, 200
        
    except Exception as e:
        db.session.rollback()
        print(f"[!] Update Status Exception: {str(e)}")
        return jsonify({"error": "Database update mutation rejected", "details": str(e)}), 500
if __name__ == '__main__':
    with app.app_context():
        db.create_all() 
        print("✅ Database synchronization complete!")
        
    port = int(os.environ.get('PORT', 5000))
    app.run(host='0.0.0.0', port=port)