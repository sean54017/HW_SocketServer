package com.example.hw_socketserver;

import android.os.Bundle;
import android.util.Log;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;
import androidx.appcompat.app.AppCompatActivity;
import org.json.JSONException;
import org.json.JSONObject;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;

public class ServerActivity extends AppCompatActivity {

    private static final int SERVER_PORT = 6100;
    private ServerSocket serverSocket;
    private List<ClientHandler> clients = new ArrayList<>();
    private TextView tvMessages, tvWelcomeMessage;
    private EditText etMessage;
    private String serverName = "Server";
    private boolean isRunning = true;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_server);

        tvMessages = findViewById(R.id.tvMessages);
        tvWelcomeMessage = findViewById(R.id.tvWelcomeMessage);
        etMessage = findViewById(R.id.etMessage);
        Button btnSend = findViewById(R.id.btnSend);
        Button btnLeave = findViewById(R.id.btnLeave);

        String name = getIntent().getStringExtra("name");
        if (name != null) {
            serverName = name;
            tvWelcomeMessage.setText("Hi, " + name);
        }

        new Thread(() -> {
            try {
                serverSocket = new ServerSocket(SERVER_PORT);
                String serverIP = InetAddress.getLocalHost().getHostAddress();
                String serverStartMessage = "Server Started[" + serverIP + ":" + SERVER_PORT + "]";
                runOnUiThread(() -> tvMessages.append(serverStartMessage + "\n"));
                Log.d("SocketServer", serverStartMessage);
                new Thread(new ServerThread()).start();
            } catch (IOException e) {
                Log.e("SocketServer", "Error starting server: " + e.getMessage());
            }
        }).start();

        btnSend.setOnClickListener(v -> {
            String message = etMessage.getText().toString().trim();
            if (!message.isEmpty()) {
                new Thread(() -> sendMessage("Me", message)).start();
            }
        });

        btnLeave.setOnClickListener(v -> {
            new Thread(() -> {
                try {
                    String shutdownMessage = "Server SHUTDOWN. Please press leave button.";
                    broadcastMessage(shutdownMessage, null);
                    runOnUiThread(() -> tvMessages.append(shutdownMessage + "\n"));
                    isRunning = false;
                    closeConnections();
                    finish();
                } catch (IOException e) {
                    Log.e("SocketServer", "Error closing server: " + e.getMessage());
                }
            }).start();
        });
    }

    private void sendMessage(String senderName, String message) {
        if (!message.isEmpty()) {
            runOnUiThread(() -> {
                etMessage.setText("");
                tvMessages.append("Me[" + getCurrentTime() + "]: " + message + "\n");
            });

            JSONObject json = new JSONObject();
            try {
                json.put("name", serverName);
                json.put("msg", message);
                String formattedMessage = json.toString();

                if (!clients.isEmpty()) {
                    broadcastMessage(formattedMessage, null);
                } else {
                    Log.d("SocketServer", "No clients connected. Message not broadcasted.");
                }
            } catch (JSONException e) {
                Log.e("SocketServer", "Error creating JSON message: " + e.getMessage());
            }
        }
    }

    private void broadcastMessage(String message, ClientHandler excludeClient) {
        List<ClientHandler> failedClients = new ArrayList<>();
        for (ClientHandler client : clients) {
            if (client != excludeClient) {
                new Thread(() -> {
                    try {
                        client.sendMessage(message);
                    } catch (IOException e) {
                        Log.e("SocketServer", "Failed to send message to client: " + e.getMessage());
                        failedClients.add(client);
                    }
                }).start();
            }
        }
        clients.removeAll(failedClients);
    }

    private class ServerThread implements Runnable {
        @Override
        public void run() {
            try {
                Log.d("SocketServer", "Server started on port: " + SERVER_PORT);

                while (isRunning) {
                    Socket clientSocket = serverSocket.accept();
                    ClientHandler clientHandler = new ClientHandler(clientSocket);
                    clients.add(clientHandler);
                    new Thread(clientHandler).start();
                }
            } catch (IOException e) {
                if (isRunning) {
                    Log.e("SocketServer", "Error in server thread: " + e.getMessage());
                }
            }
        }
    }

    private class ClientHandler implements Runnable {
        private Socket clientSocket;
        private BufferedReader reader;
        private OutputStreamWriter writer;
        private String clientName;

        public ClientHandler(Socket socket) {
            this.clientSocket = socket;
            try {
                this.writer = new OutputStreamWriter(clientSocket.getOutputStream());
            } catch (IOException e) {
                Log.e("SocketServer", "Error initializing writer: " + e.getMessage());
            }
        }

        @Override
        public void run() {
            try {
                reader = new BufferedReader(new InputStreamReader(clientSocket.getInputStream()));

                clientName = reader.readLine();
                String clientIP = clientSocket.getInetAddress().getHostAddress();
                int clientPort = clientSocket.getPort();
                String connectMessage = clientName + "[" + clientIP + ":" + clientPort + "]: connect to server";

                runOnUiThread(() -> tvMessages.append(connectMessage + "\n"));
                broadcastMessage(connectMessage, null);

                String tmp;
                while ((tmp = reader.readLine()) != null) {
                    try {
                        JSONObject jsonObj = new JSONObject(tmp);
                        String senderName = jsonObj.getString("name");
                        String msg = jsonObj.getString("msg");

                        String formattedMessage = senderName + "[" + getCurrentTime() + "]: " + msg;
                        runOnUiThread(() -> tvMessages.append(formattedMessage + "\n"));

                        broadcastMessage(jsonObj.toString(), this);
                    } catch (JSONException e) {
                        Log.e("SocketServer", "Error parsing JSON: " + e.getMessage());
                    }
                }
            } catch (IOException e) {
                Log.e("SocketServer", "ClientHandler error: " + e.getMessage());
            } finally {
                close();
            }
        }

        public void sendMessage(String message) throws IOException {
            if (writer != null) {
                writer.write(message + "\n");
                writer.flush();
            } else {
                Log.e("SocketServer", "Writer is null, message not sent");
            }
        }

        private void close() {
            try {
                clients.remove(this);

                String leaveMessage = clientName + "[" + getCurrentTime() + "]: has left";
                broadcastMessage(leaveMessage, null);
                runOnUiThread(() -> tvMessages.append(leaveMessage + "\n"));

                if (clientSocket != null && !clientSocket.isClosed()) {
                    clientSocket.close();
                }
            } catch (IOException e) {
                Log.e("SocketServer", "Error closing client connection: " + e.getMessage());
            }
        }
    }

    private void closeConnections() throws IOException {
        isRunning = false;
        if (serverSocket != null && !serverSocket.isClosed()) {
            serverSocket.close();
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        try {
            closeConnections();
        } catch (IOException e) {
            Log.e("SocketServer", "Error during onDestroy: " + e.getMessage());
        }
    }

    private String getCurrentTime() {
        SimpleDateFormat sdf = new SimpleDateFormat("HH:mm:ss");
        return sdf.format(new Date());
    }
}
