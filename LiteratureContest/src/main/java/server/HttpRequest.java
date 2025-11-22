package server;

import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

public class HttpRequest {
    public String method;
    public String path;
    public String version;
    public Map<String,String> headers = new HashMap<>();
    public byte[] body = new byte[0];

    public String header(String name) {
        return headers.get(name.toLowerCase(Locale.ROOT));
    }
    public String bodyAsString() { return new String(body, StandardCharsets.UTF_8); }
}
