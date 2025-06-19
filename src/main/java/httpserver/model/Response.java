package httpserver.model;

import java.util.Map;
import lombok.Data;

@Data
public class Response {
    private String version;      
    private int code;            
    private String status;       
    private Map<String, String> headers;  
    private String message;      
}