package dev.stackbug.freechat.model;

import java.util.List;

public class ChatCompletionRequest {
    private List<MessageDTO> messages;
    private boolean          stream;


    public ChatCompletionRequest() {
    }

    public ChatCompletionRequest(List<MessageDTO> messages, boolean stream) {
        this.messages = messages;
        this.stream   = stream;
    }

    public List<MessageDTO> getMessages() {
        return messages;
    }

    public void setMessages(List<MessageDTO> messages) {
        this.messages = messages;
    }

    public boolean isStream() {
        return stream;
    }

    public void setStream(boolean stream) {
        this.stream = stream;
    }
}
