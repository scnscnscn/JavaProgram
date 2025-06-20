package httpserver;

import httpserver.model.Request;
import httpserver.model.ChatMessage;
import httpserver.model.ChatUser;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.io.OutputStream;
import java.io.PrintWriter;
import java.net.Socket;
import java.util.UUID;

public class ChatHandler implements Runnable {
    private Socket socket;
    private static final ObjectMapper objectMapper = new ObjectMapper();
    
    public ChatHandler(Socket socket) {
        this.socket = socket;
    }
    
    @Override
    public void run() {
        if (socket == null) {
            throw new IllegalArgumentException("Socket不能为空");
        }
        
        try (Socket clientSocket = socket) {
            OutputStream output = clientSocket.getOutputStream();
            PrintWriter out = new PrintWriter(output);
            
            Request request = HttpMessageParser.parse2request(clientSocket.getInputStream());
            String method = request.getMethod();
            String uri = request.getUri();
            
            if ("GET".equals(method)) {
                handleGetRequest(uri, out);
            } else if ("POST".equals(method)) {
                handlePostRequest(uri, request, out);
            } else {
                sendErrorResponse(out, 405, "方法不被允许");
            }
            
        } catch (IOException e) {
            System.err.println("处理客户端请求时发生错误：" + e.getMessage());
        }
    }
    
    private void handleGetRequest(String uri, PrintWriter out) throws IOException {
        if ("/".equals(uri)) {
            // 返回聊天室主页面
            String htmlContent = getChatRoomHtml();
            sendHtmlResponse(out, htmlContent);
        } else if ("/api/messages".equals(uri)) {
            // 返回消息历史
            try {
                String messagesJson = objectMapper.writeValueAsString(ChatServer.getMessageHistory());
                sendJsonResponse(out, messagesJson);
            } catch (Exception e) {
                sendErrorResponse(out, 500, "获取消息历史失败");
            }
        } else if ("/api/users".equals(uri)) {
            // 返回在线用户列表
            try {
                String usersJson = objectMapper.writeValueAsString(ChatServer.getOnlineUsers());
                sendJsonResponse(out, usersJson);
            } catch (Exception e) {
                sendErrorResponse(out, 500, "获取用户列表失败");
            }
        } else {
            sendErrorResponse(out, 404, "页面未找到");
        }
    }
    
    private void handlePostRequest(String uri, Request request, PrintWriter out) throws IOException {
        if ("/api/join".equals(uri)) {
            // 用户加入聊天室
            try {
                String username = extractUsernameFromRequest(request.getMessage());
                if (username != null && !username.trim().isEmpty()) {
                    String userId = UUID.randomUUID().toString();
                    ChatUser user = new ChatUser(userId, username.trim(), socket);
                    ChatServer.addUser(user);
                    
                    String response = "{\"success\": true, \"userId\": \"" + userId + "\"}";
                    sendJsonResponse(out, response);
                } else {
                    sendErrorResponse(out, 400, "用户名不能为空");
                }
            } catch (Exception e) {
                sendErrorResponse(out, 500, "加入聊天室失败");
            }
        } else if ("/api/send".equals(uri)) {
            // 发送消息
            try {
                ChatMessage message = objectMapper.readValue(request.getMessage(), ChatMessage.class);
                message.setTimestamp(System.currentTimeMillis());
                message.setType("user");
                
                ChatServer.broadcastMessage(message);
                
                String response = "{\"success\": true}";
                sendJsonResponse(out, response);
            } catch (Exception e) {
                sendErrorResponse(out, 500, "发送消息失败");
            }
        } else {
            sendErrorResponse(out, 404, "API接口未找到");
        }
    }
    
    private String extractUsernameFromRequest(String requestBody) {
        try {
            // 简单的JSON解析，提取username字段
            if (requestBody != null && requestBody.contains("username")) {
                int start = requestBody.indexOf("\"username\":");
                if (start != -1) {
                    start = requestBody.indexOf("\"", start + 11);
                    int end = requestBody.indexOf("\"", start + 1);
                    if (start != -1 && end != -1) {
                        return requestBody.substring(start + 1, end);
                    }
                }
            }
        } catch (Exception e) {
            System.err.println("解析用户名失败：" + e.getMessage());
        }
        return null;
    }
    
    private void sendHtmlResponse(PrintWriter out, String content) {
        String response = "HTTP/1.1 200 OK\r\n" +
                         "Content-Type: text/html; charset=UTF-8\r\n" +
                         "Content-Length: " + content.getBytes().length + "\r\n" +
                         "\r\n" + content;
        out.print(response);
        out.flush();
    }
    
    private void sendJsonResponse(PrintWriter out, String jsonContent) {
        String response = "HTTP/1.1 200 OK\r\n" +
                         "Content-Type: application/json; charset=UTF-8\r\n" +
                         "Access-Control-Allow-Origin: *\r\n" +
                         "Content-Length: " + jsonContent.getBytes().length + "\r\n" +
                         "\r\n" + jsonContent;
        out.print(response);
        out.flush();
    }
    
    private void sendErrorResponse(PrintWriter out, int statusCode, String message) {
        String content = "{\"error\": \"" + message + "\"}";
        String response = "HTTP/1.1 " + statusCode + " Error\r\n" +
                         "Content-Type: application/json; charset=UTF-8\r\n" +
                         "Content-Length: " + content.getBytes().length + "\r\n" +
                         "\r\n" + content;
        out.print(response);
        out.flush();
    }
    
    private String getChatRoomHtml() {
        return "<!DOCTYPE html>\n" +
               "<html lang=\"zh-CN\">\n" +
               "<head>\n" +
               "    <meta charset=\"UTF-8\">\n" +
               "    <meta name=\"viewport\" content=\"width=device-width, initial-scale=1.0\">\n" +
               "    <title>本地聊天室</title>\n" +
               "    <style>\n" +
               "        * { margin: 0; padding: 0; box-sizing: border-box; }\n" +
               "        body { font-family: 'Microsoft YaHei', Arial, sans-serif; background: #f5f5f5; }\n" +
               "        .container { max-width: 800px; margin: 0 auto; background: white; height: 100vh; display: flex; flex-direction: column; }\n" +
               "        .header { background: #4CAF50; color: white; padding: 15px; text-align: center; }\n" +
               "        .chat-area { flex: 1; display: flex; flex-direction: column; overflow: hidden; }\n" +
               "        .messages { flex: 1; padding: 20px; overflow-y: auto; background: #fafafa; }\n" +
               "        .message { margin-bottom: 15px; padding: 10px; border-radius: 8px; max-width: 70%; }\n" +
               "        .message.user { background: #e3f2fd; margin-left: auto; }\n" +
               "        .message.system { background: #fff3e0; margin: 0 auto; text-align: center; font-style: italic; }\n" +
               "        .message-header { font-size: 12px; color: #666; margin-bottom: 5px; }\n" +
               "        .input-area { padding: 20px; border-top: 1px solid #ddd; background: white; }\n" +
               "        .input-group { display: flex; gap: 10px; }\n" +
               "        input[type=\"text\"] { flex: 1; padding: 12px; border: 1px solid #ddd; border-radius: 4px; font-size: 14px; }\n" +
               "        button { padding: 12px 20px; background: #4CAF50; color: white; border: none; border-radius: 4px; cursor: pointer; font-size: 14px; }\n" +
               "        button:hover { background: #45a049; }\n" +
               "        button:disabled { background: #ccc; cursor: not-allowed; }\n" +
               "        .login-form { position: fixed; top: 0; left: 0; width: 100%; height: 100%; background: rgba(0,0,0,0.5); display: flex; align-items: center; justify-content: center; }\n" +
               "        .login-box { background: white; padding: 30px; border-radius: 8px; text-align: center; min-width: 300px; }\n" +
               "        .hidden { display: none; }\n" +
               "        .online-users { position: absolute; top: 60px; right: 20px; background: white; border: 1px solid #ddd; border-radius: 4px; padding: 10px; max-width: 200px; }\n" +
               "        .users-toggle { position: absolute; top: 15px; right: 20px; background: rgba(255,255,255,0.2); border: 1px solid rgba(255,255,255,0.3); color: white; }\n" +
               "    </style>\n" +
               "</head>\n" +
               "<body>\n" +
               "    <div class=\"container\">\n" +
               "        <div class=\"header\">\n" +
               "            <h1>本地聊天室</h1>\n" +
               "            <button class=\"users-toggle\" onclick=\"toggleUsers()\">在线用户</button>\n" +
               "        </div>\n" +
               "        <div class=\"chat-area\">\n" +
               "            <div class=\"messages\" id=\"messages\"></div>\n" +
               "            <div class=\"input-area\">\n" +
               "                <div class=\"input-group\">\n" +
               "                    <input type=\"text\" id=\"messageInput\" placeholder=\"输入消息...\" onkeypress=\"handleKeyPress(event)\">\n" +
               "                    <button onclick=\"sendMessage()\">发送</button>\n" +
               "                </div>\n" +
               "            </div>\n" +
               "        </div>\n" +
               "    </div>\n" +
               "\n" +
               "    <div class=\"login-form\" id=\"loginForm\">\n" +
               "        <div class=\"login-box\">\n" +
               "            <h2>加入聊天室</h2>\n" +
               "            <br>\n" +
               "            <input type=\"text\" id=\"usernameInput\" placeholder=\"请输入您的昵称\" onkeypress=\"handleLoginKeyPress(event)\">\n" +
               "            <br><br>\n" +
               "            <button onclick=\"joinChat()\">加入</button>\n" +
               "        </div>\n" +
               "    </div>\n" +
               "\n" +
               "    <div class=\"online-users hidden\" id=\"onlineUsers\">\n" +
               "        <h4>在线用户</h4>\n" +
               "        <div id=\"usersList\"></div>\n" +
               "    </div>\n" +
               "\n" +
               "    <script>\n" +
               "        let currentUser = null;\n" +
               "        let userId = null;\n" +
               "\n" +
               "        function joinChat() {\n" +
               "            const username = document.getElementById('usernameInput').value.trim();\n" +
               "            if (!username) {\n" +
               "                alert('请输入昵称');\n" +
               "                return;\n" +
               "            }\n" +
               "\n" +
               "            fetch('/api/join', {\n" +
               "                method: 'POST',\n" +
               "                headers: { 'Content-Type': 'application/json' },\n" +
               "                body: JSON.stringify({ username: username })\n" +
               "            })\n" +
               "            .then(response => response.json())\n" +
               "            .then(data => {\n" +
               "                if (data.success) {\n" +
               "                    currentUser = username;\n" +
               "                    userId = data.userId;\n" +
               "                    document.getElementById('loginForm').classList.add('hidden');\n" +
               "                    loadMessages();\n" +
               "                    startPolling();\n" +
               "                } else {\n" +
               "                    alert('加入聊天室失败');\n" +
               "                }\n" +
               "            })\n" +
               "            .catch(error => {\n" +
               "                console.error('Error:', error);\n" +
               "                alert('连接服务器失败');\n" +
               "            });\n" +
               "        }\n" +
               "\n" +
               "        function sendMessage() {\n" +
               "            const input = document.getElementById('messageInput');\n" +
               "            const message = input.value.trim();\n" +
               "            if (!message) return;\n" +
               "\n" +
               "            const messageData = {\n" +
               "                username: currentUser,\n" +
               "                content: message,\n" +
               "                userId: userId\n" +
               "            };\n" +
               "\n" +
               "            fetch('/api/send', {\n" +
               "                method: 'POST',\n" +
               "                headers: { 'Content-Type': 'application/json' },\n" +
               "                body: JSON.stringify(messageData)\n" +
               "            })\n" +
               "            .then(response => response.json())\n" +
               "            .then(data => {\n" +
               "                if (data.success) {\n" +
               "                    input.value = '';\n" +
               "                }\n" +
               "            })\n" +
               "            .catch(error => console.error('Error:', error));\n" +
               "        }\n" +
               "\n" +
               "        function loadMessages() {\n" +
               "            fetch('/api/messages')\n" +
               "            .then(response => response.json())\n" +
               "            .then(messages => {\n" +
               "                displayMessages(messages);\n" +
               "            })\n" +
               "            .catch(error => console.error('Error:', error));\n" +
               "        }\n" +
               "\n" +
               "        function displayMessages(messages) {\n" +
               "            const messagesDiv = document.getElementById('messages');\n" +
               "            messagesDiv.innerHTML = '';\n" +
               "\n" +
               "            messages.forEach(message => {\n" +
               "                const messageDiv = document.createElement('div');\n" +
               "                messageDiv.className = 'message ' + message.type;\n" +
               "\n" +
               "                if (message.type === 'user') {\n" +
               "                    const time = new Date(message.timestamp).toLocaleTimeString();\n" +
               "                    messageDiv.innerHTML = `\n" +
               "                        <div class=\"message-header\">${message.username} - ${time}</div>\n" +
               "                        <div>${message.content}</div>\n" +
               "                    `;\n" +
               "                } else {\n" +
               "                    messageDiv.textContent = message.content;\n" +
               "                }\n" +
               "\n" +
               "                messagesDiv.appendChild(messageDiv);\n" +
               "            });\n" +
               "\n" +
               "            messagesDiv.scrollTop = messagesDiv.scrollHeight;\n" +
               "        }\n" +
               "\n" +
               "        function startPolling() {\n" +
               "            setInterval(() => {\n" +
               "                loadMessages();\n" +
               "                loadOnlineUsers();\n" +
               "            }, 2000);\n" +
               "        }\n" +
               "\n" +
               "        function loadOnlineUsers() {\n" +
               "            fetch('/api/users')\n" +
               "            .then(response => response.json())\n" +
               "            .then(users => {\n" +
               "                const usersList = document.getElementById('usersList');\n" +
               "                usersList.innerHTML = users.map(user => `<div>${user}</div>`).join('');\n" +
               "            })\n" +
               "            .catch(error => console.error('Error:', error));\n" +
               "        }\n" +
               "\n" +
               "        function toggleUsers() {\n" +
               "            const usersDiv = document.getElementById('onlineUsers');\n" +
               "            usersDiv.classList.toggle('hidden');\n" +
               "        }\n" +
               "\n" +
               "        function handleKeyPress(event) {\n" +
               "            if (event.key === 'Enter') {\n" +
               "                sendMessage();\n" +
               "            }\n" +
               "        }\n" +
               "\n" +
               "        function handleLoginKeyPress(event) {\n" +
               "            if (event.key === 'Enter') {\n" +
               "                joinChat();\n" +
               "            }\n" +
               "        }\n" +
               "    </script>\n" +
               "</body>\n" +
               "</html>";
    }
}