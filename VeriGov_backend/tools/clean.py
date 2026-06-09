import os
import shutil

# 1. Define your paths
source_folder = 'VeriGov_backend/Data/train/Pothole Dataset'
destination_folder = 'VeriGov_backend/Data/train/potholes'

# 2. Create the destination if it doesn't exist
os.makedirs(destination_folder, exist_ok=True)

# 3. Define allowed image extensions
image_extensions = ('.jpg', '.jpeg', '.png', '.webp')

print("Starting data extraction...")

count = 0
for filename in os.listdir(source_folder):
    # Check if the file is an image
    if filename.lower().endswith(image_extensions):
        source_path = os.path.join(source_folder, filename)
        dest_path = os.path.join(destination_folder, filename)
        
        # Copy the file to the new folder
        shutil.copy2(source_path, dest_path)
        count += 1

print(f"Success! Moved {count} images to {destination_folder}.")
print("Text files were ignored.")
count = 0
for filename in os.listdir(source_folder):
    # Check if the file is an image
    if filename.lower().endswith(image_extensions):
        source_path = os.path.join(source_folder, filename)
        dest_path = os.path.join(destination_folder, filename)
        
        # Copy the file to the new folder
        shutil.copy2(source_path, dest_path)
        count += 1

print(f"Success! Moved {count} images to {destination_folder}.")
print("Text files were ignored.")