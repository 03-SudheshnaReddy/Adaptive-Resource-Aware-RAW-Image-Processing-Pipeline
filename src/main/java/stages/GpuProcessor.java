package stages;

import java.awt.image.BufferedImage;

import org.jocl.*;
import static org.jocl.CL.*;

/**
 * Brightness/contrast on GPU using JOCL (OpenCL 1.x/2.0).
 * Falls back to CPU if no compatible GPU/OpenCL is available.
 *
 * brightness is given in [ -100 .. +100 ] (like your CLI),
 * contrast is given in [ -100 .. +100 ] and mapped to scale = 2^(contrast/50).
 */
public final class GpuProcessor {

    private GpuProcessor() {
    }

    private static final String KERNEL = """
                __kernel void brightnessContrast(
                    __global uchar4* pixels,
                    const float add,          // brightness in [ -1 .. +1 ]
                    const float scale)        // contrast scale, e.g. 2^(c/50)
                {
                    int i = get_global_id(0);
                    uchar4 p = pixels[i];

                    float r = ( (p.x / 255.0f) * scale + add );
                    float g = ( (p.y / 255.0f) * scale + add );
                    float b = ( (p.z / 255.0f) * scale + add );

                    r = clamp(r, 0.0f, 1.0f);
                    g = clamp(g, 0.0f, 1.0f);
                    b = clamp(b, 0.0f, 1.0f);

                    pixels[i] = (uchar4)((uchar)(r * 255.0f),
                                         (uchar)(g * 255.0f),
                                         (uchar)(b * 255.0f),
                                         p.w);
                }
            """;

    public static BufferedImage applyBrightnessContrast(BufferedImage src, int brightness, int contrast) {
        try {
            return runOnGpu(src, brightness, contrast);
        } catch (Throwable t) {
            System.err.println("[GPU] Falling back to CPU: " + t.getMessage());
            // Use your fast CPU path if GPU fails for any reason
            return stages.FiltersCPUFast.applyBrightnessContrast(src, brightness, contrast);
        }
    }

    // ---- JOCL implementation ----
    private static BufferedImage runOnGpu(BufferedImage src, int brightness, int contrast) {
        CL.setExceptionsEnabled(true);

        int w = src.getWidth();
        int h = src.getHeight();
        int n = w * h;

        // Pack ARGB → RGBA bytes
        byte[] bytes = new byte[n * 4];
        int idx = 0;
        for (int y = 0; y < h; y++) {
            for (int x = 0; x < w; x++) {
                int p = src.getRGB(x, y);
                bytes[idx++] = (byte) ((p >> 16) & 0xFF); // R
                bytes[idx++] = (byte) ((p >> 8) & 0xFF); // G
                bytes[idx++] = (byte) (p & 0xFF); // B
                bytes[idx++] = (byte) ((p >> 24) & 0xFF); // A
            }
        }

        // --- Platform & device ---
        int[] numPlatforms = new int[1];
        clGetPlatformIDs(0, null, numPlatforms);
        if (numPlatforms[0] == 0)
            throw new RuntimeException("No OpenCL platforms found");

        cl_platform_id[] platforms = new cl_platform_id[numPlatforms[0]];
        clGetPlatformIDs(platforms.length, platforms, null);
        cl_platform_id platform = platforms[0];

        int[] numDevices = new int[1];
        int err = clGetDeviceIDs(platform, CL_DEVICE_TYPE_GPU, 0, null, numDevices);
        if (err != CL_SUCCESS || numDevices[0] == 0) {
            throw new RuntimeException("No OpenCL GPU device found");
        }

        cl_device_id[] devices = new cl_device_id[numDevices[0]];
        clGetDeviceIDs(platform, CL_DEVICE_TYPE_GPU, devices.length, devices, null);
        cl_device_id device = devices[0];

        // --- Context & queue ---
        cl_context_properties props = new cl_context_properties();
        props.addProperty(CL_CONTEXT_PLATFORM, platform);

        cl_context context = clCreateContext(props, 1, new cl_device_id[] { device }, null, null, null);
        cl_queue_properties qprops = new cl_queue_properties();
        cl_command_queue queue = clCreateCommandQueueWithProperties(context, device, qprops, null);

        // --- Program & kernel ---
        cl_program program = clCreateProgramWithSource(context, 1, new String[] { KERNEL }, null, null);
        clBuildProgram(program, 0, null, null, null, null);
        cl_kernel kernel = clCreateKernel(program, "brightnessContrast", null);

        // --- Device buffer ---
        cl_mem mem = clCreateBuffer(context,
                CL_MEM_READ_WRITE | CL_MEM_COPY_HOST_PTR,
                Sizeof.cl_uchar * bytes.length, Pointer.to(bytes), null);

        // --- Kernel args ---
        float add = brightness / 100.0f; // [-1..1]
        float scale = (float) Math.pow(2.0, contrast / 50.0);// contrast scale
        clSetKernelArg(kernel, 0, Sizeof.cl_mem, Pointer.to(mem));
        clSetKernelArg(kernel, 1, Sizeof.cl_float, Pointer.to(new float[] { add }));
        clSetKernelArg(kernel, 2, Sizeof.cl_float, Pointer.to(new float[] { scale }));

        // --- Launch ---
        long[] global = new long[] { n };
        clEnqueueNDRangeKernel(queue, kernel, 1, null, global, null, 0, null, null);

        // --- Read back ---
        clEnqueueReadBuffer(queue, mem, CL_TRUE, 0,
                Sizeof.cl_uchar * bytes.length, Pointer.to(bytes), 0, null, null);

        // --- Cleanup ---
        clReleaseMemObject(mem);
        clReleaseKernel(kernel);
        clReleaseProgram(program);
        clReleaseCommandQueue(queue);
        clReleaseContext(context);

        // Unpack RGBA → ARGB
        BufferedImage out = new BufferedImage(w, h, BufferedImage.TYPE_INT_ARGB);
        idx = 0;
        for (int y = 0; y < h; y++) {
            for (int x = 0; x < w; x++) {
                int r = bytes[idx++] & 0xFF;
                int g = bytes[idx++] & 0xFF;
                int b = bytes[idx++] & 0xFF;
                int a = bytes[idx++] & 0xFF;
                out.setRGB(x, y, (a << 24) | (r << 16) | (g << 8) | b);
            }
        }
        return out;
    }
}
