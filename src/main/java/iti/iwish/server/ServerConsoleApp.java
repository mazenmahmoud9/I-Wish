package iti.iwish.server;

import java.io.BufferedReader;
import java.io.InputStreamReader;

/** Admin entry point: Start the server and type stop (or press Ctrl+C) to stop it. */
public final class ServerConsoleApp {
    public static void main(String[] args) throws Exception {
        try (WishServer server = new WishServer(); BufferedReader terminal = new BufferedReader(new InputStreamReader(System.in))) {
            server.start();
            System.out.println("Database connected. Type 'stop' to shut down.");
            String command;
            while (server.isRunning() && (command = terminal.readLine()) != null) {
                if ("stop".equalsIgnoreCase(command.trim())) server.close();
            }
        }
    }
}
