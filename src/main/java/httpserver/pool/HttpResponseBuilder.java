package httpserver.pool;

import java.util.HashMap;
import java.util.Map;

/**
 * 可池化的HTTP响应构建器，用于高效构建HTTP响应
 */
public class HttpResponseBuilder implements Poolable {
    private String version;
    private int statusCode;
    private String statusText;
    private Map<String, String> headers;
    private StringBuilder body;
    
    public HttpResponseBuilder() {
        this.headers = new HashMap<>();
        this.body = new StringBuilder(512);
        reset();
    }
    
    public HttpResponseBuilder setVersion(String version) {
        this.version = version;
        return this;
    }
    
    public HttpResponseBuilder setStatus(int code, String text) {
        this.statusCode = code;
        this.statusText = text;
        return this;
    }
    
    public HttpResponseBuilder addHeader(String name, String value) {
        headers.put(name, value);
        return this;
    }
    
    public HttpResponseBuilder setContentType(String contentType) {
        return addHeader("Content-Type", contentType);
    }
    
    public HttpResponseBuilder setContentLength(int length) {
        return addHeader("Content-Length", String.valueOf(length));
    }
    
    public HttpResponseBuilder appendBody(String content) {
        body.append(content);
        return this;
    }
    
    public HttpResponseBuilder setBody(String content) {
        body.setLength(0);
        body.append(content);
        return this;
    }
    
    /**
     * 构建完整的HTTP响应字符串
     */
    public String build() {
        PooledStringBuilder response = PoolManager.getInstance().getStringBuilder();
        try {
            // 状态行
            response.append(version).append(" ")
                   .append(statusCode).append(" ")
                   .append(statusText).append("\r\n");
            
            // 自动设置Content-Length
            String bodyStr = body.toString();
            if (!headers.containsKey("Content-Length")) {
                headers.put("Content-Length", String.valueOf(bodyStr.getBytes().length));
            }
            
            // 响应头
            for (Map.Entry<String, String> header : headers.entrySet()) {
                response.append(header.getKey()).append(": ")
                       .append(header.getValue()).append("\r\n");
            }
            
            // 空行分隔头和体
            response.append("\r\n");
            
            // 响应体
            response.append(bodyStr);
            
            return response.toString();
        } finally {
            PoolManager.getInstance().releaseStringBuilder(response);
        }
    }
    
    /**
     * 构建JSON响应
     */
    public String buildJsonResponse(String jsonContent) {
        return setContentType("application/json; charset=UTF-8")
               .addHeader("Access-Control-Allow-Origin", "*")
               .setBody(jsonContent)
               .build();
    }
    
    /**
     * 构建HTML响应
     */
    public String buildHtmlResponse(String htmlContent) {
        return setContentType("text/html; charset=UTF-8")
               .setBody(htmlContent)
               .build();
    }
    
    /**
     * 构建错误响应
     */
    public String buildErrorResponse(int statusCode, String message) {
        String statusText = getStatusText(statusCode);
        String errorJson = "{\"error\": \"" + message + "\"}";
        
        return setStatus(statusCode, statusText)
               .setContentType("application/json; charset=UTF-8")
               .setBody(errorJson)
               .build();
    }
    
    private String getStatusText(int statusCode) {
        switch (statusCode) {
            case 200: return "OK";
            case 400: return "Bad Request";
            case 401: return "Unauthorized";
            case 404: return "Not Found";
            case 405: return "Method Not Allowed";
            case 409: return "Conflict";
            case 500: return "Internal Server Error";
            default: return "Unknown";
        }
    }
    
    @Override
    public void reset() {
        this.version = "HTTP/1.1";
        this.statusCode = 200;
        this.statusText = "OK";
        this.headers.clear();
        this.body.setLength(0);
        
        // 设置默认头
        addHeader("Server", "ChatServer/1.0");
        addHeader("Connection", "close");
    }
}