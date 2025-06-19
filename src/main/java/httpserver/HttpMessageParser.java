package httpserver;

import httpserver.model.Request;
import httpserver.model.Response;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.HashMap;
import java.util.Map;

public class HttpMessageParser {
    public static Request parse2request(InputStream reqStream) throws IOException {
        BufferedReader reader = new BufferedReader(new InputStreamReader(reqStream, "UTF-8"));
        Request request = new Request();

        decodeRequestLine(reader, request);
        decodeRequestHeader(reader, request);
        decodeRequestMessage(reader, request);
        
        return request;
    }
    
    private static void decodeRequestLine(BufferedReader reader, Request request) throws IOException {
        String line = reader.readLine();
        if (line == null) return;
        String[] strs = line.split(" ");
        if (strs.length >= 3) {
            request.setMethod(strs[0]);
            request.setUri(strs[1]);
            request.setVersion(strs[2]);
        }
    }
    
    private static void decodeRequestHeader(BufferedReader reader, Request request) throws IOException {
        Map<String, String> headers = new HashMap<>();
        String line;
        while ((line = reader.readLine()) != null && !line.isEmpty()) {
            int index = line.indexOf(':');
            if (index > 0) {
                String key = line.substring(0, index).trim();
                String value = line.substring(index + 1).trim();
                headers.put(key, value);
            }
        }
        request.setHeaders(headers);
    }
    
    private static void decodeRequestMessage(BufferedReader reader, Request request) throws IOException {
        Map<String, String> headers = request.getHeaders();
        int contentLen = Integer.parseInt(headers.getOrDefault("Content-Length", "0"));
        if (contentLen <= 0) return;
        
        char[] message = new char[contentLen];
        int readLen = reader.read(message);
        if (readLen > 0) {
            request.setMessage(new String(message, 0, readLen));
        }
    }
    
    public static String buildResponse(Request request, String responseBody) {
        Response response = new Response();
        response.setVersion(request.getVersion() != null ? request.getVersion() : "HTTP/1.1");
        response.setCode(200);
        response.setStatus("OK");
 
        Map<String, String> headers = new HashMap<>();
        headers.put("Content-Type", "text/plain; charset=UTF-8");
        headers.put("Content-Length", String.valueOf(responseBody.getBytes().length));
        response.setHeaders(headers);
        response.setMessage(responseBody);

        StringBuilder builder = new StringBuilder();
        builder.append(response.getVersion()).append(" ").append(response.getCode())
               .append(" ").append(response.getStatus()).append("\r\n");
        
        for (Map.Entry<String, String> entry : response.getHeaders().entrySet()) {
            builder.append(entry.getKey()).append(": ").append(entry.getValue()).append("\r\n");
        }
        builder.append("\r\n").append(response.getMessage());
        
        return builder.toString();
    }
}