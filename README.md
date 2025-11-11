# Adaptive-Resource-Aware-RAW-Image-Processing-Pipeline

## Objective:
The goal of this project is to optimize the image processing pipeline for RAW images captured by camera sensors, enhancing the end-user experience by reducing processing time, utilizing system resources efficiently, and adjusting dynamically based on battery life. The steps involved in this process include:

1. Loading the raw .ARW image  
2. Preprocessing the image by adjusting brightness, contrast, sharpening etc. Here we used GPU ensuring faster computation. If the system is on low battery, then we switched to CPU since GPU takes lots of power.  
3. Memory management: RAM is used in general but SSD is used temporarily to offload processing tasks, maintaining efficient resource utilization.  
4. We use multiple threading, according to battery level we dynamically change how many threads to use.  
5. We also do post processing and displaying preview and after doing post processing we do final saving of the final image.  

## Description or Process I Followed:
### 1. Loading the RAW Image:
We loaded .ARW image file provided by user. These images are straight from camera sensors is hard to handle these because these have more info than standard .jpg files.  
**What I did?**  
I attempted to load the embedded JPEG preview image within the RAW file. The JPEG preview provides an immediate, low-latency view of the image to the user while the RAW image is being processed in the background. This step ensures the user isn't left waiting unnecessarily for the RAW image to load.

### 2. Preprocessing the Image, Memory Management and Multi-threading:
I adjusted adjust brightness, sharpness and contrast to improve the quality of the image. I used GPU acceleration using OpenCL for doing these tasks. As I mentioned above, I used GPU only if battery is >= 30% or else use CPU to save power.  
Instead of loading and processing the entire image in one go, we processed smaller sections of the image, generally breaking into threads, which can be handled more efficiently by both the CPU and GPU.  
If the system's RAM was running low, I used an efficient memory management technique by offloading some of the image data to the SSD.

### 3. Post-Processing:
Then after preprocessing a preview is displayed then we go for post processing. I specifically just added simple filters like black & white image, edge detection filter and blurring filter and also can adjust contrast and brightness, sharpening.  
After doing all these we can save our preview as final output in .png format. The user can give file name and specific location to store it.

## Results:
### Without Improvement:
- **Processing Time:** Slower due to CPU-only processing.  
- **Memory Usage:** High, with potential for crashes when handling large images.  
- **Battery Usage:** If battery is low there is no separate process to use less power.  
- **User Experience:** Longer wait times and battery drain, leading to a frustrating user experience.  

### With Improvement:
- **Processing Time:** Significantly reduced by leveraging GPU acceleration and multi-threading.  
- **Memory Usage:** Optimized by offloading data to SSD when RAM was near capacity.  
- **Battery Usage:** Dynamic, GPU used only when battery > 30%, reducing energy consumption.  
- **User Experience:** Faster processing, smoother experience, and battery-conscious operation.  

## Conclusion:
This project successfully improved the performance and responsiveness of the RAW image processing pipeline by using hardware-aware optimizations like GPU acceleration, multi-threading, and memory spill management. The dynamic switching between CPU and GPU based on battery level ensures both performance and power efficiency, enhancing the overall user experience.
