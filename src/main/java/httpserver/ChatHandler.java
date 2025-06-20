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
                if (username == null || username.trim().isEmpty()) {
                    sendErrorResponse(out, 400, "用户名不能为空");
                    return;
                }
                
                username = username.trim();
                
                // 检查用户名是否已存在
                if (ChatServer.isUsernameExists(username)) {
                    sendErrorResponse(out, 409, "用户名已存在，请选择其他用户名");
                    return;
                }
                
                String userId = UUID.randomUUID().toString();
                // 注意：这里不再传递socket，因为HTTP是无状态的
                ChatUser user = new ChatUser(userId, username);
                ChatServer.addUser(user);
                
                String response = "{\"success\": true, \"userId\": \"" + userId + "\"}";
                sendJsonResponse(out, response);
                
            } catch (Exception e) {
                System.err.println("处理加入请求失败：" + e.getMessage());
                sendErrorResponse(out, 500, "加入聊天室失败");
            }
        } else if ("/api/send".equals(uri)) {
            // 发送消息
            try {
                ChatMessage message = objectMapper.readValue(request.getMessage(), ChatMessage.class);
                
                // 验证用户是否存在
                if (!ChatServer.isUserExists(message.getUserId())) {
                    sendErrorResponse(out, 401, "用户不存在或已离线");
                    return;
                }
                
                message.setTimestamp(System.currentTimeMillis());
                message.setType("user");
                
                ChatServer.broadcastMessage(message);
                
                String response = "{\"success\": true}";
                sendJsonResponse(out, response);
            } catch (Exception e) {
                System.err.println("处理发送消息失败：" + e.getMessage());
                sendErrorResponse(out, 500, "发送消息失败");
            }
        } else if ("/api/leave".equals(uri)) {
            // 用户离开聊天室
            try {
                String userIdJson = request.getMessage();
                String userId = extractUserIdFromRequest(userIdJson);
                if (userId != null) {
                    ChatServer.removeUser(userId);
                    String response = "{\"success\": true}";
                    sendJsonResponse(out, response);
                } else {
                    sendErrorResponse(out, 400, "无效的用户ID");
                }
            } catch (Exception e) {
                sendErrorResponse(out, 500, "离开聊天室失败");
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
    
    private String extractUserIdFromRequest(String requestBody) {
        try {
            if (requestBody != null && requestBody.contains("userId")) {
                int start = requestBody.indexOf("\"userId\":");
                if (start != -1) {
                    start = requestBody.indexOf("\"", start + 9);
                    int end = requestBody.indexOf("\"", start + 1);
                    if (start != -1 && end != -1) {
                        return requestBody.substring(start + 1, end);
                    }
                }
            }
        } catch (Exception e) {
            System.err.println("解析用户ID失败：" + e.getMessage());
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
               "        .header { background: #4CAF50; color: white; padding: 15px; text-align: center; position: relative; }\n" +
               "        .chat-area { flex: 1; display: flex; flex-direction: column; overflow: hidden; }\n" +
               "        .messages { flex: 1; padding: 20px; overflow-y: auto; background: #fafafa; }\n" +
               "        .message { margin-bottom: 15px; padding: 10px; border-radius: 8px; max-width: 70%; }\n" +
               "        .message.user { background: #e3f2fd; margin-left: auto; }\n" +
               "        .message.system { background: #fff3e0; margin: 0 auto; text-align: center; font-style: italic; max-width: 90%; }\n" +
               "        .message-header { font-size: 12px; color: #666; margin-bottom: 5px; }\n" +
               "        .input-area { padding: 20px; border-top: 1px solid #ddd; background: white; }\n" +
               "        .input-group { display: flex; gap: 10px; }\n" +
               "        input[type=\"text\"] { flex: 1; padding: 12px; border: 1px solid #ddd; border-radius: 4px; font-size: 14px; }\n" +
               "        button { padding: 12px 20px; background: #4CAF50; color: white; border: none; border-radius: 4px; cursor: pointer; font-size: 14px; }\n" +
               "        button:hover { background: #45a049; }\n" +
               "        button:disabled { background: #ccc; cursor: not-allowed; }\n" +
               "        .login-form { position: fixed; top: 0; left: 0; width: 100%; height: 100%; background: rgba(0,0,0,0.5); display: flex; align-items: center; justify-content: center; z-index: 1000; }\n" +
               "        .login-box { background: white; padding: 30px; border-radius: 8px; text-align: center; min-width: 300px; box-shadow: 0 4px 6px rgba(0,0,0,0.1); }\n" +
               "        .hidden { display: none; }\n" +
               "        .online-users { position: absolute; top: 60px; right: 20px; background: white; border: 1px solid #ddd; border-radius: 4px; padding: 15px; max-width: 200px; box-shadow: 0 2px 4px rgba(0,0,0,0.1); z-index: 100; }\n" +
               "        .users-toggle { position: absolute; top: 15px; right: 20px; background: rgba(255,255,255,0.2); border: 1px solid rgba(255,255,255,0.3); color: white; }\n" +
               "        .error-message { color: #f44336; margin-top: 10px; font-size: 14px; }\n" +
               "        .user-count { font-size: 12px; opacity: 0.8; }\n" +
               "    </style>\n" +
               "</head>\n" +
               "<body>\n" +
               "    <div class=\"container\">\n" +
               "        <div class=\"header\">\n" +
               "            <h1>本地聊天室</h1>\n" +
               "            <div class=\"user-count\" id=\"userCount\">在线用户: 0</div>\n" +
               "            <button class=\"users-toggle\" onclick=\"toggleUsers()\">在线用户</button>\n" +
               "        </div>\n" +
               "        <div class=\"chat-area\">\n" +
               "            <div class=\"messages\" id=\"messages\"></div>\n" +
               "            <div class=\"input-area\">\n" +
               "                <div class=\"input-group\">\n" +
               "                    <input type=\"text\" id=\"messageInput\" placeholder=\"输入消息...\" onkeypress=\"handleKeyPress(event)\" disabled>\n" +
               "                    <button onclick=\"sendMessage()\" id=\"sendButton\" disabled>发送</button>\n" +
               "                    <button onclick=\"leaveChat()\" id=\"leaveButton\" disabled>离开</button>\n" +
               "                </div>\n" +
               "            </div>\n" +
               "        </div>\n" +
               "    </div>\n" +
               "\n" +
               "    <div class=\"login-form\" id=\"loginForm\">\n" +
               "        <div class=\"login-box\">\n" +
               "            <h2>加入聊天室</h2>\n" +
               "            <br>\n" +
               "            <input type=\"text\" id=\"usernameInput\" placeholder=\"请输入您的昵称\" onkeypress=\"handleLoginKeyPress(event)\" maxlength=\"20\">\n" +
               "            <br><br>\n" +
               "            <button onclick=\"joinChat()\" id=\"joinButton\">加入</button>\n" +
               "            <div class=\"error-message\" id=\"errorMessage\"></div>\n" +
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
               "        let pollingInterval = null;\n" +
               "\n" +
               "        function showError(message) {\n" +
               "            const errorDiv = document.getElementById('errorMessage');\n" +
               "            errorDiv.textContent = message;\n" +
               "            setTimeout(() => {\n" +
               "                errorDiv.textContent = '';\n" +
               "            }, 5000);\n" +
               "        }\n" +
               "\n" +
               "        function joinChat() {\n" +
               "            const username = document.getElementById('usernameInput').value.trim();\n" +
               "            if (!username) {\n" +
               "                showError('请输入昵称');\n" +
               "                return;\n" +
               "            }\n" +
               "\n" +
               "            if (username.length > 20) {\n" +
               "                showError('昵称不能超过20个字符');\n" +
               "                return;\n" +
               "            }\n" +
               "\n" +
               "            const joinButton = document.getElementById('joinButton');\n" +
               "            joinButton.disabled = true;\n" +
               "            joinButton.textContent = '加入中...';\n" +
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
               "                    \n" +
               "                    // 启用聊天功能\n" +
               "                    document.getElementById('messageInput').disabled = false;\n" +
               "                    document.getElementById('sendButton').disabled = false;\n" +
               "                    document.getElementById('leaveButton').disabled = false;\n" +
               "                    \n" +
               "                    loadMessages();\n" +
               "                    startPolling();\n" +
               "                } else if (data.error) {\n" +
               "                    showError(data.error);\n" +
               "                } else {\n" +
               "                    showError('加入聊天室失败');\n" +
               "                }\n" +
               "            })\n" +
               "            .catch(error => {\n" +
               "                console.error('Error:', error);\n" +
               "                showError('连接服务器失败，请检查网络连接');\n" +
               "            })\n" +
               "            .finally(() => {\n" +
               "                joinButton.disabled = false;\n" +
               "                joinButton.textContent = '加入';\n" +
               "            });\n" +
               "        }\n" +
               "\n" +
               "        function leaveChat() {\n" +
               "            if (userId) {\n" +
               "                fetch('/api/leave', {\n" +
               "                    method: 'POST',\n" +
               "                    headers: { 'Content-Type': 'application/json' },\n" +
               "                    body: JSON.stringify({ userId: userId })\n" +
               "                })\n" +
               "                .catch(error => console.error('Leave error:', error));\n" +
               "            }\n" +
               "            \n" +
               "            // 重置状态\n" +
               "            currentUser = null;\n" +
               "            userId = null;\n" +
               "            if (pollingInterval) {\n" +
               "                clearInterval(pollingInterval);\n" +
               "                pollingInterval = null;\n" +
               "            }\n" +
               "            \n" +
               "            // 禁用聊天功能\n" +
               "            document.getElementById('messageInput').disabled = true;\n" +
               "            document.getElementById('sendButton').disabled = true;\n" +
               "            document.getElementById('leaveButton').disabled = true;\n" +
               "            document.getElementById('messageInput').value = '';\n" +
               "            \n" +
               "            // 显示登录表单\n" +
               "            document.getElementById('loginForm').classList.remove('hidden');\n" +
               "            document.getElementById('usernameInput').value = '';\n" +
               "            document.getElementById('onlineUsers').classList.add('hidden');\n" +
               "        }\n" +
               "\n" +
               "        function sendMessage() {\n" +
               "            const input = document.getElementById('messageInput');\n" +
               "            const message = input.value.trim();\n" +
               "            if (!message || !userId) return;\n" +
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
               "                } else if (data.error) {\n" +
               "                    if (data.error.includes('用户不存在')) {\n" +
               "                        alert('您已离线，请重新加入聊天室');\n" +
               "                        leaveChat();\n" +
               "                    } else {\n" +
               "                        alert('发送失败：' + data.error);\n" +
               "                    }\n" +
               "                }\n" +
               "            })\n" +
               "            .catch(error => {\n" +
               "                console.error('Error:', error);\n" +
               "                alert('发送消息失败');\n" +
               "            });\n" +
               "        }\n" +
               "\n" +
               "        function loadMessages() {\n" +
               "            fetch('/api/messages')\n" +
               "            .then(response => response.json())\n" +
               "            .then(messages => {\n" +
               "                displayMessages(messages);\n" +
               "            })\n" +
               "            .catch(error => console.error('Error loading messages:', error));\n" +
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
               "                        <div>${escapeHtml(message.content)}</div>\n" +
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
               "        function escapeHtml(text) {\n" +
               "            const div = document.createElement('div');\n" +
               "            div.textContent = text;\n" +
               "            return div.innerHTML;\n" +
               "        }\n" +
               "\n" +
               "        function startPolling() {\n" +
               "            if (pollingInterval) clearInterval(pollingInterval);\n" +
               "            pollingInterval = setInterval(() => {\n" +
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
               "                const userCount = document.getElementById('userCount');\n" +
               "                \n" +
               "                usersList.innerHTML = users.map(user => `<div>${escapeHtml(user)}</div>`).join('');\n" +
               "                userCount.textContent = `在线用户: ${users.length}`;\n" +
               "            })\n" +
               "            .catch(error => console.error('Error loading users:', error));\n" +
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
               "\n" +
               "        // 页面关闭时自动离开聊天室\n" +
               "        window.addEventListener('beforeunload', function() {\n" +
               "            if (userId) {\n" +
               "                navigator.sendBeacon('/api/leave', JSON.stringify({ userId: userId }));\n" +
               "            }\n" +
               "        });\n" +
               "    </script>\n" +
               "</body>\n" +
               "</html>";
    }
}