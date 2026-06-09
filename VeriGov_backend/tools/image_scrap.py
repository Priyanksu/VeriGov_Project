import os
import time
import requests
from duckduckgo_search import DDGS
from random import randint

def get_high_res_data(query, limit, folder):
    save_path = os.path.abspath(os.path.join(folder, query.replace(" ", "_")))
    os.makedirs(save_path, exist_ok=True)
    
    print(f"🚀 Stealth Mode: {query}")
    
    # 1. Use a fresh instance with custom headers to avoid detection
    with DDGS() as ddgs:
        try:
            # 2. Add a small delay before the request
            time.sleep(randint(2, 5)) 
            results = ddgs.images(query, max_results=limit)
            
            count = 0
            for i, res in enumerate(results):
                url = res['image']
                try:
                    # Randomize delay between downloads
                    time.sleep(randint(1, 2))
                    
                    response = requests.get(url, timeout=10, headers={'User-Agent': 'Mozilla/5.0'})
                    if response.status_code == 200:
                        extension = url.split('.')[-1].split('?')[0][:4]
                        if extension.lower() not in ['jpg', 'jpeg', 'png']: extension = 'jpg'
                        
                        file_name = f"bsl_{i}.{extension}"
                        with open(os.path.join(save_path, file_name), "wb") as f:
                            f.write(response.content)
                        count += 1
                        print(f"✅ Saved {count}/{limit}")
                except Exception:
                    continue
        except Exception as e:
            print(f"⚠️ DDG still blocking? Error: {e}")
            print("💡 Suggestion: Wait 10 mins or change your IP (toggle Wifi/Mobile Data).")

get_high_res_data("indian", 50, "VeriGov_backend/Data/train")