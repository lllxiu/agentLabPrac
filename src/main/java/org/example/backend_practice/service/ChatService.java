package org.example.backend_practice.service;

import org.springframework.stereotype.Service;

@Service
public class ChatService {

    public String reply(String message){
        return "收到"+message+"这是本地的模拟回答";
    }
}
