package httpserver.model;

import java.util.Map;
import lombok.Data;

@Data
public class Request {
    private String method;      
    private String uri;          
    private String version;   
    private Map<String, String> headers;  
    private String message;      
}