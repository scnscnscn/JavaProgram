package httpserver;
import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

public class BasicHttpServer {
    private static final int PORT = 8999; 
    private static ExecutorService bootstrapExecutor = Executors.newSingleThreadExecutor();
    private static ExecutorService taskExecutor;
    
    public static void startHttpServer() {
        int nThreads = Runtime.getRuntime().availableProcessors();
        taskExecutor = new ThreadPoolExecutor(
            nThreads, nThreads, 0L, TimeUnit.MILLISECONDS,
            new LinkedBlockingQueue<>(100), new ThreadPoolExecutor.DiscardPolicy()
        );

        while (true) {
            try {
                ServerSocket serverSocket = new ServerSocket(PORT);
                System.out.println("HTTP服务器启动成功，监听端口：" + PORT);
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
                    System.out.println("接收到客户端连接：" + clientSocket.getInetAddress());
                    taskExecutor.submit(new HttpTask(clientSocket));
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
    
    public static void main(String[] args) {
        startHttpServer();
    }
}