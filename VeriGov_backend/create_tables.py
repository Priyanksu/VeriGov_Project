# VeriGov_backend/create_tables.py
from app import app, db

with app.app_context():
    db.create_all()
    print("🚀 Database tables successfully initialized inside PostgreSQL!")