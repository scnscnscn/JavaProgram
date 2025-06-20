package httpserver.model;

import lombok.Data;
import java.net.Socket;
import java.io.PrintWriter;
import java.io.IOException;

@Data
public class ChatUser {
    private String userId;      // 用户唯一ID
    private String username;    // 用户名
    private Socket socket;      // 用户的Socket连接
    private PrintWriter writer; // 用于发送消息的输出流
    private long lastActivity;  // 最后活动时间
    
    public ChatUser(String userId, String username, Socket socket) {
        this.userId = userId;
        this.username = username;
        this.socket = socket;
        this.lastActivity = System.currentTimeMillis();
        
        try {
            this.writer = new PrintWriter(socket.getOutputStream(), true);
        } catch (IOException e) {
            System.err.println("创建用户输出流失败：" + e.getMessage());
        }
    }
    
    // 向用户发送消息
    public void sendMessage(String message) throws IOException {
        if (writer != null && !socket.isClosed()) {
            writer.println(message);
            this.lastActivity = System.currentTimeMillis();
        }
    }
    
    // 检查连接是否有效
    public boolean isConnected() {
        return socket != null && !socket.isClosed() && socket.isConnected();
    }
    
    // 关闭连接
    public void disconnect() {
        try {
            if (writer != null) {
                writer.close();
            }
            if (socket != null && !socket.isClosed()) {
                socket.close();
            }
        } catch (IOException e) {
            System.err.println("关闭用户连接失败：" + e.getMessage());
        }
    }
}