package server;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Locale;

public class HttpParser {
    public static HttpRequest parse(InputStream raw) throws IOException {
        var req = new HttpRequest();
        var in = new BufferedInputStream(raw);

        String start = readLine(in, 8192); // "GET /status HTTP/1.1"
        if (start == null || start.isEmpty()) throw new IOException("empty request");
        String[] p = start.split(" ", 3);
        req.method  = p.length > 0 ? p[0] : "";
        req.path    = p.length > 1 ? p[1] : "/";
        req.version = p.length > 2 ? p[2] : "HTTP/1.1";

        String line;
        int contentLength = 0;
        while ((line = readLine(in, 32768)) != null && !line.isEmpty()) {
            int k = line.indexOf(':');
            if (k <= 0) continue;
            String key = line.substring(0,k).trim().toLowerCase(Locale.ROOT);
            String val = line.substring(k+1).trim();
            req.headers.put(key, val);
            if ("content-length".equals(key)) {
                try { contentLength = Integer.parseInt(val); } catch (NumberFormatException ignore) {}
            }
        }

        if (contentLength > 0) {
            req.body = readN(in, contentLength);
        } else {
            req.body = new byte[0];
        }
        return req;
    }

    private static String readLine(InputStream in, int max) throws IOException {
        ByteArrayOutputStream bos = new ByteArrayOutputStream();
        int prev = -1, cur;
        while ((cur = in.read()) != -1) {
            if (prev == '\r' && cur == '\n') break;
            bos.write(cur);
            if (bos.size() > max) throw new IOException("line too long");
            prev = cur;
        }
        if (cur == -1 && bos.size()==0) return null;
        byte[] arr = bos.toByteArray();
        if (arr.length>0 && arr[arr.length-1]=='\r') arr = Arrays.copyOf(arr, arr.length-1);
        return new String(arr, StandardCharsets.US_ASCII);
    }

    private static byte[] readN(InputStream in, int n) throws IOException {
        byte[] buf = new byte[n]; int off=0;
        while (off<n) {
            int r = in.read(buf, off, n-off);
            if (r<0) throw new IOException("unexpected eof");
            off += r;
        }
        return buf;
    }
}
