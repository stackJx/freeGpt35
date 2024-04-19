package dev.stackbug.freechat.controller;


import dev.stackbug.freechat.model.ChatCompletionRequest;
import dev.stackbug.freechat.service.ChatService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;


/**
 * @author yinshen
 */
@CrossOrigin(origins = "*", allowedHeaders = "*")
@RestController
@RequestMapping("/v1/chat/completions")
public class ChatController {

    @Autowired
    private ChatService chatService;

    @PostMapping
    public SseEmitter handleChatCompletion(@RequestBody ChatCompletionRequest request) {
        return chatService.handleChatCompletion(request);
    }
}
