import buffers.RequestProtos.*;
import buffers.ResponseProtos.*;
import buffers.ResponseProtos.Client;
import buffers.ResponseProtos.Node;

import java.io.*;
import java.net.ServerSocket;
import java.net.Socket;
import java.text.DecimalFormat;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public class Server extends Thread {
    static String entryFilename = "entries.bin";
    static String menuOptions = "\nWhat would you like to do? \n 1 - Get account summary \n 2 - Request line of credit " +
            "\n 3 - Make payment to exiting loan \n 4 - (DEBUG) see client list \n 5 - (DEBUG) see node list \n 6 - quit\n";

    static List<Server> connections = Collections.synchronizedList(new ArrayList<>());
    static List<Entry> entries = Collections.synchronizedList(new ArrayList<>());
    static List<Node> nodes = Collections.synchronizedList(new ArrayList<>());
    static List<Client> clients = Collections.synchronizedList(new ArrayList<>());

    static volatile Map<Node, Boolean> approvals = new ConcurrentHashMap<>();

    private Node node;
//    private static volatile Client client;
    private Client client;
    private ConnectionRequest.Connection type;

    InputStream in = null;
    OutputStream out = null;
    Socket clientSocket = null;
    int port = 9099; // default port for client
    private int id;
    
    public Server(Socket sock) {
        this.clientSocket = sock;
        try {
            in = clientSocket.getInputStream();
            out = clientSocket.getOutputStream();
        } catch (Exception e) {
            System.out.println("Error in constructor: " + e);
        }
    }

    public synchronized void connectToServer() throws IOException {
        try {
            // read the proto object and put into new objct
            ConnectionRequest op = ConnectionRequest.parseDelimitedFrom(in);

            switch(op.getConnection()) {
                case CLIENT:
                    connections.add(this);
                    this.type = ConnectionRequest.Connection.CLIENT;
                    this.id = op.getId();
                    clientConnect();
                    break;
                case NODE:
                    connections.add(this);
                    this.type = ConnectionRequest.Connection.NODE;
                    this.id = op.getId();
                    nodeConnect();
                    break;
            }

        } catch (Exception ex) {

        }
        finally {
            if (this.type == ConnectionRequest.Connection.CLIENT) {
                System.out.println("Client ID " + this.id + " disconnected");
            } else {
                System.out.println("Node ID " + this.id + " disconnected");
            }
            exitAndClose(this, in, out, clientSocket);
        }
    }

////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
///////////// CLIENT CONNECTION

    private synchronized void clientConnect() throws IOException {
        System.out.println("Client with ID " + this.id + " connected!");
        ServerToClientResponse.Builder response = sendClientGreeting();
        response.build().writeDelimitedTo(this.out);

        while (true) {
            ClientToServerRequest op = ClientToServerRequest.parseDelimitedFrom(in);
            boolean quit = false;

            switch (op.getOperationType()) {
                case SUMMARY:
                    StringBuilder summary = new StringBuilder();
                    if (this.client != null) {
                        if (this.client.getLoan() > 0) {
                            summary.append("\nAccount summary for client ").append(client.getId()).append("\n");
                            summary.append("\tTotal owed: ").append(client.getLoan()).append("\n");
                            summary.append("\tTotal paid: ").append(client.getPaid()).append("\n");
                        } else {
                            summary.append("\nSorry, no existing history found for this client.\n");
                        }
                    } else {
                        summary.append("\nSorry, no existing history found for this client.\n");
                    }
                    response.setResponseType(ServerToClientResponse.ResponseType.GREETING);
                    response.setMessage(summary.toString());
                    response.setMenuoptions(menuOptions);
                    break;
                case CREDIT:
                    response = creditRequest(op);
                    break;
                case PAYBACK:
                    response = paybackRequest(op);
                    break;
                case CLIENTSUMMARY:
                    getUpdatedEntries();
                    StringBuilder clientStr = new StringBuilder();
                    clientStr.append("\n");
                    for (Entry entry : entries) {
                        if (entry.hasClient()) clientStr.append(entry).append("\n");
                    }
                    response.setResponseType(ServerToClientResponse.ResponseType.GREETING);
                    response.setMessage(clientStr.toString());
                    response.setMenuoptions(menuOptions);
                    break;
                case NODESUMMARY:
                    getUpdatedEntries();
                    StringBuilder nodeStr = new StringBuilder();
                    nodeStr.append("\n");
                    for (Entry entry : entries) {
                        if (entry.hasNode()) nodeStr.append(entry).append("\n");
                    }
                    response.setResponseType(ServerToClientResponse.ResponseType.GREETING);
                    response.setMessage(nodeStr.toString());
                    response.setMenuoptions(menuOptions);
                    break;
                case QUIT:
                    response = quit();
                    quit = true;
                    break;
            }
            response.build().writeDelimitedTo(this.out);

            if (quit) {
                return;
            }
        }
    }

    private synchronized ServerToClientResponse.Builder sendClientGreeting() {
        getUpdatedEntries();

        boolean found = false;
        for (Client c : clients) {
            if (c.getId() == this.id) {
                this.client = c;
                found = true;
                break;
            }
        }

        if (!found) {
            this.client = Client.newBuilder().setId(this.id).build();
        }
        writeToEntry(this.client.toBuilder(), null);

        return ServerToClientResponse.newBuilder()
                .setResponseType(ServerToClientResponse.ResponseType.GREETING)
                .setMessage(found ? "Welcome back client " + this.id + "." : "Hello client " + this.id + " and welcome to our lending service.")
                .setMenuoptions(menuOptions);
    }

    private synchronized ServerToClientResponse.Builder creditRequest(ClientToServerRequest op) throws IOException {
        System.out.println("Client requested " + op.getAmount() + " as a loan.. Will communicate with nodes to get approval");

        sendCreditRequest(formatAmount(op.getAmount()));

        int connectedNodes = 0;
        for (Server s : connections) {
            if (s.node != null) {
                connectedNodes++;
            }
        }

        while (true) {
            if (approvals.size() == connectedNodes) break;
        }

        boolean decision = checkDecision();

        int approved = 0;
        int denied = 0;

        StringBuilder str = new StringBuilder();

        for (Map.Entry<Node, Boolean> entry : approvals.entrySet()) {
            if (entry.getValue()) {
                approved++;
            } else {
                denied++;
            }
            str.append("\t Node ID ").append(entry.getKey().getNodeID()).append(" : ").append(entry.getKey().getReason()).append("\n");
            entry.getKey().toBuilder().clearReason().build();
            for (int i = 0; i < nodes.size(); i++) {
                if (nodes.get(i).getNodeID() == entry.getKey().getNodeID()) {
                    nodes.set(i, entry.getKey().toBuilder().clearReason().build());
                }
            }
        }

        getUpdatedEntries();

        ServerToClientResponse.Builder res;

        if (decision) {
            res = approveRequest(approved, denied, op.getAmount());
        } else {
            res = ServerToClientResponse.newBuilder()
                    .setResponseType(ServerToClientResponse.ResponseType.DECISION)
                    .setMessage("\nThere were " + approved + " approved and " + denied + " denied. As a result your request has been denied.\n" + str)
                    .setMenuoptions(menuOptions);
        }
        approvals.clear();
        return res;
    }

    private synchronized ServerToClientResponse.Builder paybackRequest(ClientToServerRequest op) throws IOException {
        System.out.println("Client requested " + op.getAmount() + " to pay back");

        if (!this.client.hasLoan()) {
            ServerToClientResponse.Builder res = ServerToClientResponse.newBuilder()
                    .setResponseType(ServerToClientResponse.ResponseType.DECISION)
                    .setMessage("\nClient " + this.client.getId() + " has no loans to pay back.\n")
                    .setMenuoptions(menuOptions);
            return res;
        }

        double amountOwed =  this.client.getLoan() - this.client.getPaid();
        if (amountOwed < op.getAmount()) {
            ServerToClientResponse.Builder res = ServerToClientResponse.newBuilder()
                    .setResponseType(ServerToClientResponse.ResponseType.DECISION)
                    .setMessage("\nClient " + this.client.getId() + " has " + amountOwed + " owed. Please enter an amount up to " + amountOwed + "\n")
                    .setMenuoptions(menuOptions);

            return res;
        }

        int loans = 0;

        getUpdatedEntries();
        for (Node node : nodes) {
            List<Client> temp_client_list = node.getClientList();
            for (Client c : temp_client_list) {
                if (c.getId() == this.client.getId()) {
                    loans++;
                    break;
                }
            }
        }

        double amount = op.getAmount();
        double split = formatAmount(amount / (double)loans);
        double[] amount_per_node = new double[loans];
        Arrays.fill(amount_per_node, split);

        if (split * loans != amount) {
            if (split * loans < amount) {
                double difference = formatAmount(amount - (split * loans));
                amount_per_node[loans - 1] += difference;
            } else {
                double difference = formatAmount((split * loans) - amount);
                amount_per_node[loans - 1] -= difference;
            }
        }

        int i = 0;
        for (Node node : nodes) {
            List<Client> temp_client_list = node.getClientList();
            for (Client c : temp_client_list) {
                if (c.getId() == this.client.getId()) {
                    try {
                        ServerToNodeResponse.Builder response = ServerToNodeResponse.newBuilder()
                                .setResponseType(ServerToNodeResponse.ResponseType.PAYBACK)
                                .setMessage("\nClient requested " + amount_per_node[i] + " to payback for their existing loan.\n")
                                .setClient(this.client)
                                .setSplitAmount(amount_per_node[i])
                                .setNode(node);
                        i++;
                        for (Server s : connections) {
                            if (s.node != null && s.node.getNodeID() == node.getNodeID()) {
                                response.build().writeDelimitedTo(s.out);
                                break;
                            }
                        }

                    } catch (IOException ex) {

                    }
                    break;
                }
            }
        }

        String message = "";
        if (amount == amountOwed) {
            this.client = Client.newBuilder(this.client).clearLoan().clearPaid().build();
            message = "\nThank you for your payment of " + amount + ". You have paid the loan off!\n";
        }
        else {
            this.client = Client.newBuilder(this.client).setPaid(this.client.getPaid() + amount).build();
            message = "\nThank you for your payment of " + amount + ". \nYou now have " +
                    (this.client.getLoan() - this.client.getPaid()) + " remaining on your loan.\n";
        }

        writeToEntry(this.client.toBuilder(), null);

        ServerToClientResponse.Builder res = ServerToClientResponse.newBuilder()
                .setResponseType(ServerToClientResponse.ResponseType.DECISION)
                .setMessage(message)
                .setMenuoptions(menuOptions);

        return res;
    }

    private synchronized ServerToClientResponse.Builder quit() {
        return ServerToClientResponse.newBuilder()
                .setResponseType(ServerToClientResponse.ResponseType.BYE)
                .setMessage("Thank you for choosing our service! Goodbye.");
    }

////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
///////////// NODE CONNECTION

    private synchronized void nodeConnect() throws IOException {
        System.out.println("Node with ID " + this.id + " connected!");

        ServerToNodeResponse.Builder response = sendNodeGreeting();
        response.build().writeDelimitedTo(this.out);

        while (true) {
            NodeToServerRequest op = NodeToServerRequest.parseDelimitedFrom(in);
            response = ServerToNodeResponse.newBuilder();
            boolean quit = false;

            switch (op.getOperationType()) {
                case ID:
                    response = checkNode(op);
                    break;
                case ACCEPT:
                    this.node = op.getNode();
                    approvals.put(this.node, true);
                    response.setResponseType(ServerToNodeResponse.ResponseType.STANDBY);
                    response.setMessage("Your accept response has been recorded, Please standby.");
                    break;
                case DECLINE:
                    this.node = op.getNode();
                    approvals.put(this.node, false);
                    response.setResponseType(ServerToNodeResponse.ResponseType.STANDBY);
                    response.setMessage("Your decline response has been recorded, Please standby.");
                    break;
                case CONFIRM:
                    // This is sent to server after loan/payment is approved so just a message from node to confirm operations
                    // were completed as required
                    System.out.println("Node " + this.id + " has returned a confirmation");
                    this.node = op.getNode();
                    writeToEntry(null, this.node.toBuilder());
                    response.setResponseType(ServerToNodeResponse.ResponseType.STANDBY);
                    response.setMessage("Your confirmation has been received and updated, Please standby.");
                    break;
            }
            response.build().writeDelimitedTo(this.out);

            if (quit) {
                return;
            }
        }
    }

    private synchronized ServerToNodeResponse.Builder sendNodeGreeting() {
        getUpdatedEntries();
        String message = "";
        ServerToNodeResponse.ResponseType type = ServerToNodeResponse.ResponseType.STANDBY;

        for (Entry entry : entries) {
            if (entry.hasNode() && entry.getNode().getNodeID() == this.id) {
                if (entry.getNode().hasAmount()) {
                    message = "\nWelcome Node " + this.id + " and welcome to our lending service. You currently " +
                            "have " + entry.getNode().getAmount() + " available funds.\n Please standby for client " +
                            "request...\n";
                    this.node = entry.getNode();
                }
                break;
            }
        }

        if (message.isBlank()) {
            type = ServerToNodeResponse.ResponseType.GREETING;
            message = "\nLooks like this is your first time using this system. Please enter in the amount you wish " +
                    "you make available to the server.\n";
        }

        return ServerToNodeResponse.newBuilder()
                .setResponseType(type)
                .setMessage(message);
    }

    private synchronized ServerToNodeResponse.Builder checkNode(NodeToServerRequest op) {
        getUpdatedEntries();

        String message = "";
        ServerToNodeResponse.ResponseType type = ServerToNodeResponse.ResponseType.STANDBY;

        if (op.hasAmount()) {
            message = "\nWelcome Node " + this.id + " and welcome to our lending service. You currently " +
                    "have " + op.getAmount() + " available funds.\n Please standby for client " +
                    "request...\n";
            Node.Builder node = Node.newBuilder().setNodeID(this.id).setAmount(op.getAmount());
            writeToEntry(null, node);
            this.node = node.build();
        }

        return ServerToNodeResponse.newBuilder()
                .setResponseType(type)
                .setMessage(message);
    }

    private synchronized boolean checkDecision() throws IOException {
        getUpdatedEntries();

        int approved = 0;
        int denied = 0;

        for (Map.Entry<Node, Boolean> entry : approvals.entrySet()) {
            if (entry.getValue()) {
                approved++;
            } else {
                denied++;
            }
        }

        if (approved > denied) {
            return true;
        }
        else {
            return false;
        }
    }

    private synchronized void sendCreditRequest(double amount) {
        for (Server s : connections) {
            if (s.node != null && s.node != this.node) {
                try {
                    ServerToNodeResponse.Builder response = ServerToNodeResponse.newBuilder()
                            .setResponseType(ServerToNodeResponse.ResponseType.CREDIT)
                            .setMessage("\nClient requested " + amount + " as a credit.\n")
                            .setAmount(amount)
                            .setClient(this.client)
                            .setNode(s.node);

                    response.build().writeDelimitedTo(s.out);
                } catch (IOException ex) {

                }
            }
        }
    }

    private synchronized ServerToClientResponse.Builder approveRequest(int approved, int denied, double amount) throws IOException {
        this.client = Client.newBuilder(this.client).setLoan(this.client.getLoan() + amount).build();

        writeToEntry(this.client.toBuilder(), null);

        double split = amount / approved;
        DecimalFormat df_split = new DecimalFormat("#.##");
        split = Double.parseDouble(df_split.format(split));

        double[] amount_per_node = new double[approved];
        Arrays.fill(amount_per_node, split);

        if (split * approved != amount) {
            if (split * approved < amount) {
                double difference = formatAmount(amount - (split * approved));
                amount_per_node[approved - 1] += difference;
            } else {
                double difference = formatAmount((split * approved) - amount);
                amount_per_node[approved - 1] -= difference;
            }
        }

        int i = 0;
        for (Server s : connections) {
            if (s.node != null && approvals.containsKey(s.node) && approvals.get(s.node)) {
                try {
                    ServerToNodeResponse.Builder response = ServerToNodeResponse.newBuilder()
                            .setResponseType(ServerToNodeResponse.ResponseType.APPROVED)
                            .setClient(this.client)
                            .setNode(s.node)
                            .setSplitAmount(amount_per_node[i])
                            .setAmount(amount);

                    response.build().writeDelimitedTo(s.out);
                } catch (IOException ex) {

                }
                i++;
            }
        }

        StringBuilder str = new StringBuilder();
        str.append("\nCongratulations! Your request has been approved for a loan amount of " + amount);
        str.append("\nThere were " + approved + " approved and " + denied + " denied.").append("\n");

        for (Map.Entry<Node, Boolean> entry : approvals.entrySet()) {
            if (entry.getValue()) {
                str.append("\t Node ").append(entry.getKey().getNodeID()).append(" has split the amount by ").append(split).append("\n");
            }
        }

        ServerToClientResponse.Builder res = ServerToClientResponse.newBuilder()
                .setResponseType(ServerToClientResponse.ResponseType.DECISION)
                .setMessage(str.toString())
                .setMenuoptions(menuOptions);

        return res;
    }

    static synchronized void exitAndClose(Server server, InputStream in, OutputStream out, Socket serverSock) throws IOException {
        connections.remove(server);
        if (in != null)   in.close();
        if (out != null)  out.close();
        if (serverSock != null) serverSock.close();
    }

    /**
     * Writing a new entry to our entry if entry not found, else incrementing login count.
     */
    public static synchronized void writeToEntry(Client.Builder client, Node.Builder node) {
        try {
            Data.Builder res = readEntryFile();

            Map<Integer, Client> clientMap = new HashMap<>();
            Map<Integer, Node> nodeMap = new HashMap<>();

            for (Entry entry : res.getEntriesList()) {
                if (entry.hasClient()) {
                    clientMap.put(entry.getClient().getId(), entry.getClient());
                } else if (entry.hasNode()) {
                    nodeMap.put(entry.getNode().getNodeID(), entry.getNode());
                }
            }

            for (Node n : nodes) {
                nodeMap.put(n.getNodeID(), n);
            }

            for (Client c : clients) {
                clientMap.put(c.getId(), c);
            }

            if (node != null) {
                updateConnections(node.build());
                nodeMap.put(node.getNodeID(), node.build());
            }

            if (client != null) {
                clientMap.put(client.getId(), client.build());
            }

            res.clearEntries();

            for (Node n : nodeMap.values()) {
                Entry entry = Entry.newBuilder().setNode(n).build();
                res.addEntries(entry);
            }
            for (Client c : clientMap.values()) {
                Entry entry = Entry.newBuilder().setClient(c).build();
                res.addEntries(entry);
            }

            entries.clear();
            clients.clear();
            nodes.clear();

            entries.addAll(res.getEntriesList());
            clients.addAll(clientMap.values());
            nodes.addAll(nodeMap.values());

            if (entries.isEmpty()) return;

            FileOutputStream output = new FileOutputStream(entryFilename);
            res.build().writeTo(output);

        } catch(Exception e) {
            System.out.println("Issue while trying to save");
        }
    }

    /**
     * Reading the current entry file
     * @return Entry.Builder a builder of a entry from protobuf
     */
    public static synchronized Data.Builder readEntryFile() throws Exception {
        Data.Builder entries = Data.newBuilder();
        try {
            entries.mergeFrom(new FileInputStream(entryFilename));
            return entries;
        } catch (FileNotFoundException e) {
            return entries;
        }
    }

    public static synchronized void getUpdatedEntries() {
        writeToEntry(null, null);
    }

    public static void main (String[] args) throws Exception {
        if (args.length != 1) {
            System.out.println("Expected arguments: <port(int)>");
            System.exit(1);
        }
        int port = 9099; // default port
//        int sleepDelay = 1000; // default delay
        Socket clientSocket = null;
        ServerSocket socket = null;

        try {
            port = Integer.parseInt(args[0]);
//            sleepDelay = Integer.parseInt(args[1]);
        } catch (NumberFormatException nfe) {
            System.out.println("[Port|sleepDelay] must be an integer");
            System.exit(2);
        }
        try {
            socket = new ServerSocket(port);
            System.out.println("Server started..");
        } catch(Exception e) {
            e.printStackTrace();
            System.exit(2);
        }
        while (true) {
            try {
                clientSocket = socket.accept();
                System.out.println("Attempting connection");
                (new Server(clientSocket)).start();
            } catch (Exception e) {
                System.out.println("Error in accepting connection.");
            }
        }
    }

    public void run() {
        try {
            connectToServer();
            clientSocket.close();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public static void updateConnections(Node node) {
        for (Server s : connections) {
            if (s.node != null && s.node.getNodeID() == node.getNodeID()) {
                s.node = node;
            }
        }
    }

    public double formatAmount(double amount) {
        DecimalFormat df = new DecimalFormat("#.##");
        return Double.parseDouble(df.format(amount));
    }
}
