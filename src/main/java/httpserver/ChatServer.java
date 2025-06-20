package httpserver;

import httpserver.model.ChatMessage;
import httpserver.model.ChatUser;
import httpserver.pool.PoolManager;
import httpserver.pool.PooledStringBuilder;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.List;
import java.util.Map;

public class ChatServer {
    private static final int PORT = 8999;
    private static ExecutorService bootstrapExecutor = Executors.newSingleThreadExecutor();
    private static ExecutorService taskExecutor;
    
    // 存储所有连接的用户
    private static final Map<String, ChatUser> connectedUsers = new ConcurrentHashMap<>();
    // 存储聊天消息历史
    private static final List<ChatMessage> messageHistory = new CopyOnWriteArrayList<>();
    // JSON处理器
    private static final ObjectMapper objectMapper = new ObjectMapper();
    // 内存池管理器
    private static final PoolManager poolManager = PoolManager.getInstance();
    
    public static void startChatServer() {
        int nThreads = Runtime.getRuntime().availableProcessors();
        taskExecutor = new ThreadPoolExecutor(
            nThreads, nThreads, 0L, TimeUnit.MILLISECONDS,
            new LinkedBlockingQueue<>(100), new ThreadPoolExecutor.DiscardPolicy()
        );

        // 添加JVM关闭钩子，优雅关闭内存池
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            System.out.println("正在关闭聊天服务器...");
            poolManager.printPoolStats();
            poolManager.shutdown();
            if (taskExecutor != null) {
                taskExecutor.shutdown();
            }
            System.out.println("聊天服务器已关闭");
        }));

        while (true) {
            try {
                ServerSocket serverSocket = new ServerSocket(PORT);
                System.out.println("🚀 聊天室服务器启动成功，监听端口：" + PORT);
                System.out.println("💡 采用内存池技术，性能更优");
                System.out.println("🌐 请在浏览器中访问：http://localhost:" + PORT);
                
                // 打印初始内存池状态
                poolManager.printPoolStats();
                
                bootstrapExecutor.submit(new ServerThread(serverSocket));
                break;
            } catch (IOException e) {
                System.err.println("端口绑定失败，10秒后重试...");
                try {
                    TimeUnit.SECONDS.sleep(10);
                } catch (InterruptedException ie) {
                    Thread.currentThread().interrupt();
                }
            }
        }
        bootstrapExecutor.shutdown();
    }

    private static class ServerThread implements Runnable {
        private ServerSocket serverSocket;
        
        public ServerThread(ServerSocket serverSocket) {
            this.serverSocket = serverSocket;
        }
        
        @Override
        public void run() {
            try {
                while (!serverSocket.isClosed()) {
                    Socket clientSocket = serverSocket.accept();
                    System.out.println("新的客户端连接：" + clientSocket.getInetAddress());
                    taskExecutor.submit(new ChatHandler(clientSocket));
                }
            } catch (IOException e) {
                if (!serverSocket.isClosed()) {
                    System.err.println("服务器监听异常：" + e.getMessage());
                }
            } finally {
                try {
                    if (serverSocket != null && !serverSocket.isClosed()) {
                        serverSocket.close();
                    }
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
        }
    }
    
    // 检查用户名是否已存在
    public static boolean isUsernameExists(String username) {
        return connectedUsers.values().stream()
            .anyMatch(user -> user.getUsername().equals(username));
    }
    
    // 检查用户是否存在
    public static boolean isUserExists(String userId) {
        return connectedUsers.containsKey(userId);
    }
    
    // 添加用户到聊天室
    public static void addUser(ChatUser user) {
        connectedUsers.put(user.getUserId(), user);
        System.out.println("用户 " + user.getUsername() + " 加入聊天室，当前在线用户数：" + connectedUsers.size());
        
        // 使用内存池构建系统消息
        PooledStringBuilder messageBuilder = poolManager.getStringBuilder();
        try {
            String content = messageBuilder.append(user.getUsername())
                                         .append(" 加入了聊天室")
                                         .toString();
            
            ChatMessage joinMessage = new ChatMessage();
            joinMessage.setType("system");
            joinMessage.setContent(content);
            joinMessage.setTimestamp(System.currentTimeMillis());
            
            broadcastMessage(joinMessage);
        } finally {
            poolManager.releaseStringBuilder(messageBuilder);
        }
    }
    
    // 移除用户
    public static void removeUser(String userId) {
        ChatUser user = connectedUsers.remove(userId);
        if (user != null) {
            System.out.println("用户 " + user.getUsername() + " 离开聊天室，当前在线用户数：" + connectedUsers.size());
            
            // 使用内存池构建系统消息
            PooledStringBuilder messageBuilder = poolManager.getStringBuilder();
            try {
                String content = messageBuilder.append(user.getUsername())
                                             .append(" 离开了聊天室")
                                             .toString();
                
                ChatMessage leaveMessage = new ChatMessage();
                leaveMessage.setType("system");
                leaveMessage.setContent(content);
                leaveMessage.setTimestamp(System.currentTimeMillis());
                
                broadcastMessage(leaveMessage);
            } finally {
                poolManager.releaseStringBuilder(messageBuilder);
            }
        }
    }
    
    // 广播消息给所有用户
    public static void broadcastMessage(ChatMessage message) {
        messageHistory.add(message);
        
        // 只保留最近100条消息
        if (messageHistory.size() > 100) {
            messageHistory.remove(0);
        }
        
        // 使用内存池构建日志消息
        PooledStringBuilder logBuilder = poolManager.getStringBuilder();
        try {
            String logMessage = logBuilder.append("广播消息：[")
                                        .append(message.getType())
                                        .append("] ")
                                        .append(message.getUsername() != null ? message.getUsername() + ": " : "")
                                        .append(message.getContent())
                                        .toString();
            System.out.println(logMessage);
        } finally {
            poolManager.releaseStringBuilder(logBuilder);
        }
    }
    
    // 获取在线用户列表
    public static List<String> getOnlineUsers() {
        return connectedUsers.values().stream()
            .map(ChatUser::getUsername)
            .collect(java.util.stream.Collectors.toList());
    }
    
    // 获取消息历史
    public static List<ChatMessage> getMessageHistory() {
        return new java.util.ArrayList<>(messageHistory);
    }
    
    public static void main(String[] args) {
        startChatServer();
    }
}