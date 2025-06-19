package httpserver;

import httpserver.model.Request;  

import java.io.IOException;
import java.io.OutputStream;
import java.io.PrintWriter;
import java.net.Socket;

public class HttpTask implements Runnable {
    private Socket socket;
    
    public HttpTask(Socket socket) {
        this.socket = socket;
    }
    
    @Override
    public void run() {
        if (socket == null) {
            throw new IllegalArgumentException("Socket cannot be null");
        }
        
        try (Socket clientSocket = socket) {
            OutputStream output = clientSocket.getOutputStream();
            PrintWriter out = new PrintWriter(output);
            
            Request request = HttpMessageParser.parse2request(clientSocket.getInputStream());

            String responseBody;
            if (request.getUri().equals("/")) {
                responseBody = "Hello, Java Socket HTTP Server!";
            } else {
                responseBody = "404 Not Found";
            }

            String httpResponse = HttpMessageParser.buildResponse(request, responseBody);
            out.print(httpResponse);
            out.flush();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
}