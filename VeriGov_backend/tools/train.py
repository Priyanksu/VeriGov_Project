import os
import time
import torch
import torch.nn as nn
import torch.optim as optim
from torchvision import datasets, models, transforms
from torch.utils.data import DataLoader

def main():
    # 1. Base Paths Configuration
    # Adjust this path if your script is located inside a subfolder like 'tools/'
    DATA_DIR = '/home/priyanksu/Documents/6th semester project/VeriGov_project/VeriGov_backend/Data/train' 
    MODEL_SAVE_PATH = '/home/priyanksu/Documents/6th semester project/VeriGov_project/VeriGov_backend/models/verigov_resnet.pth'
    
    if not os.path.exists(DATA_DIR):
        print(f"❌ Error: The directory '{DATA_DIR}' does not exist.")
        print("Please ensure your dataset structure matches: Data/train/{clean, potholes, road_garbage, water_logging}")
        return

    # 2. Image Transformations (Optimized for ResNet50 input requirements)
    print("🎨 Setting up data transforms...")
    data_transforms = transforms.Compose([
        transforms.Resize((224, 224)),
        transforms.ToTensor(),
        # Standard normalization values for ImageNet models
        transforms.Normalize([0.485, 0.456, 0.406], [0.229, 0.224, 0.225])
    ])

    # 3. Load Dataset & DataLoader
    print("📦 Loading dataset from cargo bays...")
    dataset = datasets.ImageFolder(DATA_DIR, transform=data_transforms)
    
    # num_workers=0 is crucial for smooth CPU execution without multiprocess overhead
    dataloader = DataLoader(dataset, batch_size=32, shuffle=True, num_workers=0)
    
    class_names = dataset.classes
    print(f"✅ Detected {len(class_names)} Classes: {class_names}")
    print(f"📊 Total Training Images: {len(dataset)}")

    # 4. Initialize Pre-trained ResNet50
    print("🤖 Initializing Pre-trained ResNet50 Core...")
    model = models.resnet50(weights=models.ResNet50_Weights.DEFAULT)

    # ❄️ Freeze all base feature layers to save your CPU from massive calculations
    for param in model.parameters():
        param.requires_grad = False

    # 🛠️ Swap the final fully connected layer to match your 4 municipal classes
    num_ftrs = model.fc.in_features
    model.fc = nn.Linear(num_ftrs, len(class_names))

    # 5. Define Loss Function and Optimizer
    criterion = nn.CrossEntropyLoss()
    # Only optimize parameters of the final layer (model.fc)
    optimizer = optim.Adam(model.fc.parameters(), lr=0.001)

    # Force CPU configuration for stability
    device = torch.device("cpu")
    model = model.to(device)
    
    # 6. The Training Loop (15 Epochs)
    num_epochs = 15
    print(f"\n⚔️ Launching CPU Training Strike for {num_epochs} Epochs...")
    total_start_time = time.time()

    for epoch in range(num_epochs):
        epoch_start_time = time.time()
        model.train()
        
        running_loss = 0.0
        correct_preds = 0
        total_preds = 0
        
        for inputs, labels in dataloader:
            inputs, labels = inputs.to(device), labels.to(device)
            
            # Clear historical gradients
            optimizer.zero_grad()
            
            # Forward pass
            outputs = model(inputs)
            loss = criterion(outputs, labels)
            
            # Backward pass & weight updates
            loss.backward()
            optimizer.step()
            
            # Track training metrics
            running_loss += loss.item() * inputs.size(0)
            _, preds = torch.max(outputs, 1)
            correct_preds += torch.sum(preds == labels.data)
            total_preds += inputs.size(0)
            
        epoch_loss = running_loss / len(dataset)
        epoch_acc = correct_preds.double() / total_preds
        epoch_duration = time.time() - epoch_start_time
        
        print(f"🏴‍☠️ Epoch {epoch+1:02d}/{num_epochs} -> "
              f"Loss: {epoch_loss:.4f} | "
              f"Acc: {epoch_acc:.4f} | "
              f"Time: {epoch_duration:.1f}s")

    total_duration = time.time() - total_start_time
    print(f"\n🎉 Training complete in {total_duration/60:.2f} minutes!")

    # 7. Secure the Model Weights and metadata
    print(f"💾 Saving trained weights to '{MODEL_SAVE_PATH}'...")
    torch.save({
        'model_state_dict': model.state_dict(),
        'class_names': class_names
    }, MODEL_SAVE_PATH)
    print("⚓ Mission complete! The AI model is ready for Flask integration.")

if __name__ == '__main__':
    main()