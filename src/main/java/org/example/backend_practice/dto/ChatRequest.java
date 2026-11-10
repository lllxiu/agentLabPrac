package org.example.backend_practice.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record ChatRequest(@NotBlank(message = "消息不能为空")
                          @Size(max=200,message ="消息长度不能超过200")
                          String message) {

}
