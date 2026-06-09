import os
from PIL import Image

def sanitize_folders(root_path):
    categories = os.listdir(root_path)
    
    for category in categories:
        folder_path = os.path.join(root_path, category)
        if not os.path.isdir(folder_path):
            continue
            
        print(f"Sanitizing category: {category}...")
        images = os.listdir(folder_path)
        
        for index, filename in enumerate(images, start=1):
            file_path = os.path.join(folder_path, filename)
            
            try:
                with Image.open(file_path) as img:
                    # Convert to RGB (removes alpha channel from webp/png)
                    rgb_img = img.convert('RGB')
                    # Create new standardized name
                    new_name = f"{category}_{index}.jpg"
                    save_path = os.path.join(folder_path, new_name)
                    
                    # Save as JPG
                    rgb_img.save(save_path, "JPEG")
                
                # Delete the original if it wasn't already the new name
                if filename != new_name:
                    os.remove(file_path)
                    
            except Exception as e:
                print(f"Could not process {filename}: {e}")

# Run it on your training data
sanitize_folders('VeriGov_backend/Data/train')
print("All images standardized to JPG and renamed!")