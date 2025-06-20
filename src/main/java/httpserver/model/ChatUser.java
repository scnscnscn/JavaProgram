package httpserver.model;

import lombok.Data;

@Data
public class ChatUser {
    private String userId;      // 用户唯一ID
    private String username;    // 用户名
    private long lastActivity;  // 最后活动时间
    
    public ChatUser(String userId, String username) {
        this.userId = userId;
        this.username = username;
        this.lastActivity = System.currentTimeMillis();
    }
    
    // 更新最后活动时间
    public void updateActivity() {
        this.lastActivity = System.currentTimeMillis();
    }
    
    // 检查用户是否活跃（5分钟内有活动）
    public boolean isActive() {
        return System.currentTimeMillis() - lastActivity < 5 * 60 * 1000;
    }
}