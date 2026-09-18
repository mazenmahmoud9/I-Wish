package iti.iwish.server;

import iti.iwish.shared.ApiRequest;
import iti.iwish.shared.ApiResponse;
import iti.iwish.shared.NetworkConfig;
import java.io.EOFException;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.net.ServerSocket;
import java.net.Socket;
import java.sql.SQLException;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/** Multi-client socket server. Each request is handled against the persistent JDBC repository. */
public final class WishServer implements AutoCloseable {
    public static final int PORT = NetworkConfig.port();
    private final WishDatabase database;
    private final ExecutorService workers = Executors.newCachedThreadPool();
    private volatile boolean running;
    private ServerSocket socket;

    public WishServer() throws SQLException, IOException { database = new WishDatabase(); }
    public synchronized void start() throws IOException {
        if (running) return;
        socket = new ServerSocket(PORT); running = true;
        workers.submit(() -> { while (running) try { Socket client = socket.accept(); workers.submit(() -> handle(client)); } catch (IOException ignored) { if (running) System.err.println("Server connection error: " + ignored.getMessage()); } });
        System.out.println("I-Wish server started on port " + PORT + ".");
    }
    public boolean isRunning() { return running; }
    private void handle(Socket client) {
        try (client; ObjectOutputStream output = new ObjectOutputStream(client.getOutputStream()); ObjectInputStream input = new ObjectInputStream(client.getInputStream())) {
            Object received = input.readObject();
            ApiResponse response = received instanceof ApiRequest request ? dispatch(request) : ApiResponse.fail("Invalid request.");
            output.writeObject(response); output.flush();
        } catch (EOFException ignored) { } catch (Exception exception) { System.err.println("Client request failed: " + exception.getMessage()); }
    }
    private ApiResponse dispatch(ApiRequest r) {
        try {
            Map<String,Object> d = r.data();
            return switch (r.action()) {
                case "REGISTER" -> ApiResponse.ok("Welcome to I-Wish!", database.register(text(d,"name"),text(d,"email"),text(d,"password")));
                case "LOGIN" -> ApiResponse.ok("Welcome back!", database.login(text(d,"email"),text(d,"password")));
                case "FRIENDS" -> ApiResponse.ok("", database.friends(id(d,"userId")));
                case "REQUEST_FRIEND" -> { database.requestFriend(id(d,"userId"),text(d,"email")); yield ApiResponse.ok("Friend request sent.",null); }
                case "REPLY_FRIEND" -> { database.replyFriend(id(d,"userId"),id(d,"relationshipId"),(Boolean)d.get("accept")); yield ApiResponse.ok("Friend request updated.",null); }
                case "REMOVE_FRIEND" -> { database.removeFriend(id(d,"userId"),id(d,"relationshipId")); yield ApiResponse.ok("Friend removed.",null); }
                case "MY_WISHES" -> ApiResponse.ok("",database.wishes(id(d,"userId")));
                case "FRIEND_WISHES" -> ApiResponse.ok("",database.friendWishes(id(d,"userId"),id(d,"friendId")));
                case "ADD_WISH" -> ApiResponse.ok("Wish added to your list.",database.addWish(id(d,"userId"),text(d,"title"),text(d,"note"),number(d,"price")));
                case "UPDATE_WISH" -> { database.updateWish(id(d,"userId"),id(d,"wishId"),text(d,"title"),text(d,"note"),number(d,"price")); yield ApiResponse.ok("Wish updated.",null); }
                case "DELETE_WISH" -> { database.deleteWish(id(d,"userId"),id(d,"wishId")); yield ApiResponse.ok("Wish deleted.",null); }
                case "CONTRIBUTE" -> { database.contribute(id(d,"userId"),id(d,"wishId"),number(d,"amount")); yield ApiResponse.ok("Your contribution was sent with love!",null); }
                case "NOTICES" -> ApiResponse.ok("",database.notices(id(d,"userId")));
                case "READ_NOTICES" -> { database.markNoticesRead(id(d,"userId")); yield ApiResponse.ok("",null); }
                case "CATALOG" -> ApiResponse.ok("",database.catalog());
                default -> ApiResponse.fail("Unknown request.");
            };
        } catch (IllegalArgumentException | SQLException exception) { return ApiResponse.fail(exception.getMessage()); }
    }
    private static long id(Map<String,Object>d,String key){return ((Number)d.get(key)).longValue();}
    private static double number(Map<String,Object>d,String key){return ((Number)d.get(key)).doubleValue();}
    private static String text(Map<String,Object>d,String key){return String.valueOf(d.getOrDefault(key,""));}
    @Override public synchronized void close() { running=false; try { if(socket!=null) socket.close(); } catch(IOException ignored){} workers.shutdownNow(); System.out.println("I-Wish server stopped."); }
}
