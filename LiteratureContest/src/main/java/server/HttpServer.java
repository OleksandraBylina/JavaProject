// file: src/main/java/server/HttpServer.java
package server;

import server.handlers.GetHandler;
import server.handlers.PostHandler;
import server.handlers.PutHandler;
import server.storage.Storage;

import java.io.*;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.concurrent.*;

public class HttpServer {
    static final int PORT = 8080;
    static final int READ_TIMEOUT_MS = 15_000;

    public static void main(String[] args) throws Exception {
        Storage.ensure();
        var getHandler  = new GetHandler();
        var postHandler = new PostHandler();
        var putHandler  = new PutHandler();
        var router = new Router()
                .registerGET("/status", getHandler)
                .registerGET("/assignments", getHandler)
                .registerGET("/assignments.xlsx", getHandler)
                .registerGET("/assignments.zip", getHandler)
                .registerGET("/results", getHandler)
                .registerPOST("/submit", postHandler)
                .registerPOST("/mail", postHandler)
                .registerPUT("/submission", putHandler)
                .registerPUT("/reviews",    putHandler);

        var queue = new ArrayBlockingQueue<Runnable>(200);
        var pool = new ThreadPoolExecutor(32, 64, 60, TimeUnit.SECONDS, queue,
                new ThreadPoolExecutor.AbortPolicy());

        try (ServerSocket server = new ServerSocket(PORT, 500)) {
            System.out.println("Server started on :" + PORT);
            while (true) {
                Socket s = server.accept();
                s.setSoTimeout(READ_TIMEOUT_MS);
                try {
                    pool.execute(() -> handleOne(router, s));
                } catch (RejectedExecutionException ex) {
                    send503AndClose(s);
                }
            }
        }
    }

    static void handleOne(Router router, Socket s) {
        try (s; InputStream in = s.getInputStream(); OutputStream out = s.getOutputStream()) {
            HttpRequest req = HttpParser.parse(in);
            var h = router.resolve(req.method, req.path);
            if (h != null) h.handle(req, out);
            else HttpResponses.text(out, 404, "Not Found");
        } catch (Exception e) {
            try { HttpResponses.text(s.getOutputStream(), 500, "Internal Server Error"); } catch (Exception ignore) {}
        }
    }

    static void send503AndClose(Socket s) {
        try (var out = s.getOutputStream()) { HttpResponses.text(out, 503, "Service Unavailable"); }
        catch (Exception ignore) {}
        try { s.close(); } catch (Exception ignore) {}
    }
}
