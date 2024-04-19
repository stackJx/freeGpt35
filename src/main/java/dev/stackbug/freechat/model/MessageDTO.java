package dev.stackbug.freechat.model;

public class MessageDTO {
    private String role;
    private String content;
    public MessageDTO() {}

    public MessageDTO(String role, String content) {
        this.role = role;
        this.content = content;
    }

    // Getters and Setters
    public String getRole() {
        return role;
    }

    public void setRole(String role) {
        this.role = role;
    }

    public String getContent() {
        return content;
    }

    public void setContent(String content) {
        this.content = content;
    }
}
