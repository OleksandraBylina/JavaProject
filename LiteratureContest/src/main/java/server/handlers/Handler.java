package server.handlers;

import server.HttpRequest;
import java.io.IOException;
import java.io.OutputStream;

public interface Handler {
    void handle(HttpRequest req, OutputStream out) throws IOException;
}
