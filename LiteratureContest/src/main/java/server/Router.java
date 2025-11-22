// file: src/main/java/server/Router.java
package server;

import server.handlers.Handler;
import java.util.HashMap;
import java.util.Map;

public class Router {
    private final Map<String, Handler> get  = new HashMap<>();
    private final Map<String, Handler> post = new HashMap<>();
    private final Map<String, Handler> put  = new HashMap<>();

    public Router registerGET (String path, Handler h){ get.put(path, h);  return this; }
    public Router registerPOST(String path, Handler h){ post.put(path, h); return this; }
    public Router registerPUT (String path, Handler h){ put.put(path, h);  return this; }

    public Handler resolve(String method, String path) {
        return switch (method) {
            case "GET"  -> get.get(path);
            case "POST" -> post.get(path);
            case "PUT"  -> put.get(path);
            default     -> null;
        };
    }
}
