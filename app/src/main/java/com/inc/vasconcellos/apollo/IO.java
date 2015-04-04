package com.inc.vasconcellos.apollo;


import android.content.Context;
import android.util.Log;

import com.github.nkzawa.socketio.client.Url;
import com.github.nkzawa.socketio.parser.Parser;

import java.net.MalformedURLException;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;
import java.util.concurrent.ConcurrentHashMap;

import javax.net.ssl.SSLContext;


public class IO {

    private static final String TAG = IO.class.getSimpleName();

    private static final ConcurrentHashMap<String, SocketManager> managers = new ConcurrentHashMap<>();

    /**
     * Protocol version.
     */
    public static int protocol = Parser.protocol;

    public static void setDefaultSSLContext(SSLContext sslContext) {
        SocketManager.defaultSSLContext = sslContext;
    }

    private IO() {}

    public static Socket socket(Context context, String uri) throws URISyntaxException {
        return socket(context, uri, null);
    }

    public static Socket socket(Context context, String uri, Options opts) throws URISyntaxException {
        return socket(context, new URI(uri), opts);
    }

    public static Socket socket(Context context, URI uri) throws URISyntaxException {
        return socket(context, uri, null);
    }

    /**
     * Initializes a {@link com.github.nkzawa.socketio.client.Socket} from an existing {@link SocketManager} for multiplexing.
     *
     * @param uri uri to connect.
     * @param opts options for socket.
     * @return {@link com.github.nkzawa.socketio.client.Socket} instance.
     * @throws URISyntaxException
     */
    public static Socket socket(Context context, URI uri, Options opts) throws URISyntaxException {
        if (opts == null) {
            opts = new Options();
        }

        URL parsed;
        try {
            parsed = Url.parse(uri);
        } catch (MalformedURLException e) {
            throw new URISyntaxException(uri.toString(), e.getMessage());
        }
        URI source = parsed.toURI();
        SocketManager io;

        if (opts.forceNew || !opts.multiplex) {
            Log.v(TAG, "Ignoring socket cache for " + source);

            io = new SocketManager(context, source, opts);
        } else {
            String id = Url.extractId(parsed);
            if (!managers.containsKey(id)) {
                Log.v(TAG, "new io instance for " + source);

                managers.putIfAbsent(id, new SocketManager(context, source, opts));
            }
            io = managers.get(id);
        }

        return io.socket(parsed.getPath());
    }


    public static class Options extends SocketManager.Options {

        public boolean forceNew;

        /**
         * Whether to enable multiplexing. Default is true.
         */
        public boolean multiplex = true;
    }
}
