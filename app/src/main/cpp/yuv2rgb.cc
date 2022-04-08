//
// Created by Mason on 3/17/2022.
//

#include "yuv2rgb.h"

// yuv2rgb.cc

#include "yuv2rgb.h"
#include <stdint.h>

namespace cameramotiontracker {

void Yuv2Rgb(int width, int height, const uint8_t* y_buffer, const uint8_t* u_buffer,
            const uint8_t* v_buffer, int y_pixel_stride, int uv_pixel_stride,
            int y_row_stride, int uv_row_stride, int* argb_output) {
  uint32_t a = (255u << 24);
  uint8_t r, g, b;
  int16_t y_val, u_val, v_val;

  for (int y = 0; y < height; ++y) {
    for (int x = 0; x < width; ++x) {
      // Y plane should have positive values belonging to [0...255]
      int y_idx = (y * y_row_stride) + (x * y_pixel_stride);
      y_val = static_cast<int16_t>(y_buffer[y_idx]);

      int uvx = x / 2;
      int uvy = y / 2;
      // U/V Values are sub-sampled i.e. each pixel in U/V channel in a
      // YUV_420 image act as chroma value for 4 neighbouring pixels
      int uv_idx = (uvy * uv_row_stride) +  (uvx * uv_pixel_stride);

      u_val = static_cast<int16_t>(u_buffer[uv_idx]) - 128;
      v_val = static_cast<int16_t>(v_buffer[uv_idx]) - 128;

      // Compute RGB values per formula above.
      r = y_val + 1.370705f * v_val;
      g = y_val - (0.698001f * v_val) - (0.337633f * u_val);
      b = y_val + 1.732446f * u_val;

      int argb_idx = y * width + x;
      argb_output[argb_idx] = a | r << 16 | g << 8 | b;
    }
  }
}

}