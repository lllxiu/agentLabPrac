package org.example.backend_practice.ai;

import org.example.backend_practice.dto.MessageResponse;
import org.springframework.stereotype.Service;

import java.util.List;

public class DemoChatGateway  implements ChatGateway{

    @Override
    public String reply(
            String message,
            List<MessageResponse> history){
        return "收到"+message+"这是本地模拟回复";
    }
}
