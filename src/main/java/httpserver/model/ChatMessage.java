package httpserver.model;

import lombok.Data;

@Data
public class ChatMessage {
    private String type;        // "user" 或 "system"
    private String username;    // 发送者用户名
    private String content;     // 消息内容
    private long timestamp;     // 时间戳
    private String userId;      // 用户ID
}