import buffers.RequestProtos.*;
import buffers.ResponseProtos;
import buffers.ResponseProtos.*;

import java.io.*;
import java.net.Socket;
import java.util.ArrayList;
import java.util.List;

public class Node {
    public static void main (String[] args) throws Exception {
        Socket serverSock = null;
        OutputStream out = null;
        InputStream in = null;
        String host = args[0];
        int port = Integer.parseInt(args[1]);

        ConnectionRequest.Builder op = connectionRequest();
        ServerToNodeResponse response;

        try {
            // connect to the server
            serverSock = new Socket(host, port);

            // write to the server
            out = serverSock.getOutputStream();
            in = serverSock.getInputStream();

            op.build().writeDelimitedTo(out);

            while (true) {
                // read from the server
                response = ServerToNodeResponse.parseDelimitedFrom(in);

                NodeToServerRequest.Builder req = NodeToServerRequest.newBuilder();

                switch (response.getResponseType()) {
                    case GREETING:
                        System.out.println(response.getMessage());
                        req = getAmount(response);
                        break;
                    case CREDIT:
                        req = makeDecision(response);
                        break;
                    case PAYBACK:
                        req = payBack(response);
                        break;
                    case APPROVED:
                        req = approval(response);
                        break;
                    case STANDBY:
                        System.out.println(response.getMessage());
                        while (true) {
                            response = ServerToNodeResponse.parseDelimitedFrom(in);
                            if (response != null) {
                                if (response.getResponseType() == ServerToNodeResponse.ResponseType.CREDIT) {
                                    req = makeDecision(response);
                                    break;
                                } else if (response.getResponseType() == ServerToNodeResponse.ResponseType.STANDBY) {
                                    System.out.println(response.getMessage());
                                } else if (response.getResponseType() == ServerToNodeResponse.ResponseType.APPROVED) {
                                    req = approval(response);
                                    break;
                                } else if (response.getResponseType() == ServerToNodeResponse.ResponseType.PAYBACK) {
                                    System.out.println(response.getMessage());
                                    req = payBack(response);
                                    break;
                                } else {
                                    break;
                                }
                            }
                        }
                        break;
                }
                req.build().writeDelimitedTo(out);
            }
        } catch (Exception e) {
            e.printStackTrace();
        } finally {
            exitAndClose(in, out, serverSock);
        }
    }

    public static NodeToServerRequest.Builder getAmount(ServerToNodeResponse response) throws IOException {
        System.out.println("Please enter an amount:");
        BufferedReader stdin = new BufferedReader(new InputStreamReader(System.in));
        double amount = 0.0;

        while (true) {
            try {
                amount = Double.parseDouble(stdin.readLine());
                break;
            } catch (NumberFormatException nfe) {
                System.out.println("Amount needs to be a number.\n" + response.getMessage());
            }
        }

        return NodeToServerRequest.newBuilder()
                .setOperationType(NodeToServerRequest.OperationType.ID)
                .setAmount(amount);
    }

    public static NodeToServerRequest.Builder makeDecision(ServerToNodeResponse response) {
        System.out.println("\nA credit request has been sent here!");
        System.out.println(response.getMessage() + "\n");
        System.out.println("Evaluating request....\n");

        try {
            Thread.sleep(2000);
        } catch (InterruptedException e) {

        }

        NodeToServerRequest.Builder req = NodeToServerRequest.newBuilder();

        ResponseProtos.Node node = response.getNode();
        int nodeID = node.getNodeID();
        double amount = node.getAmount();
        List<ResponseProtos.Client> clients = new ArrayList<>(node.getClientList());

        ResponseProtos.Client client = response.getClient();
        int clientID = client.getId();
        double requested = response.getAmount();

        ResponseProtos.Node.Builder new_node = ResponseProtos.Node.newBuilder(node);

        boolean set = false;

        for (ResponseProtos.Client c : clients) {
            if (c.getId() == clientID) {
                req.setOperationType(NodeToServerRequest.OperationType.DECLINE);
                set = true;
            }
        }

        if ((requested * 1.50) < amount && !set) {
            //respond yes for have enough to fund and client not in client list
            req.setOperationType(NodeToServerRequest.OperationType.ACCEPT);
            new_node.setReason("Approved!");
            System.out.println("Approved!");
        } else if (!set) {
            // respond no for amount requested being too large
            req.setOperationType(NodeToServerRequest.OperationType.DECLINE);
            new_node.setReason("Requested amount exceeds the maximum allowed amount");
            System.out.println("Denied! - Requested amount exceeds the maximum allowed amount");
        } else {
            new_node.setReason("Client already has an existing loan with this node");
            System.out.println("Denied! - Client already has an existing loan with this node");
        }

        req.setNode(new_node.build());
        return req;
    }

    public static NodeToServerRequest.Builder payBack(ServerToNodeResponse response) {
        System.out.println("\nA payment of " + response.getSplitAmount() + " has been sent here! ");

        System.out.println("Processing payment....");
        try {
            Thread.sleep(2000);
        } catch (InterruptedException e) {

        }

        NodeToServerRequest.Builder req = NodeToServerRequest.newBuilder();

        ResponseProtos.Node node = response.getNode();
        List<ResponseProtos.Client> clients = new ArrayList<>(node.getClientList());

        ResponseProtos.Client client = response.getClient();

        for (int i = 0; i < clients.size(); i++) {
            if (clients.get(i).getId() == client.getId()) {
                ResponseProtos.Client new_client = ResponseProtos.Client.newBuilder(clients.get(i))
                        .setPaid(clients.get(i).getPaid() + response.getSplitAmount())
                        .build();

                ResponseProtos.Node.Builder new_node = ResponseProtos.Node.newBuilder(node)
                        .setAmount(node.getAmount() + response.getSplitAmount())
                        .clearReason();

                if (new_client.getLoan() == new_client.getPaid()) {
                    new_node.removeClient(i);
                } else {
                    new_node.setClient(i, new_client);
                    req.setClient(new_client);
                }

                req.setNode(new_node.build());
                break;
            }
        }

        req.setOperationType(NodeToServerRequest.OperationType.CONFIRM);

        return req;
    }

    public static NodeToServerRequest.Builder approval(ServerToNodeResponse response) {
        System.out.println("\nClient decided to accept the approval that you have sent. The amount financed through you is " + response.getSplitAmount() + "\n");
        System.out.println("Processing loan....");
        try {
            Thread.sleep(2000);
        } catch (InterruptedException e) {

        }
        NodeToServerRequest.Builder req = NodeToServerRequest.newBuilder();

        ResponseProtos.Node node = response.getNode();
        ResponseProtos.Client client = response.getClient();

        // add updated client loan amount as well as individual split amount for this node
        client = client.toBuilder()
//                .setNodeSplit(response.getSplitAmount())
                .setLoan(response.getSplitAmount())
                .build();

        // update node's client list to add the new client object and set the amount to be (amount - split)
        ResponseProtos.Node new_node = ResponseProtos.Node.newBuilder(node)
                        .addClient(client)
                        .setAmount(node.getAmount() - response.getSplitAmount())
                        .build();

        req.setOperationType(NodeToServerRequest.OperationType.CONFIRM);
        req.setNode(new_node);

        return req;
    }

    public static ConnectionRequest.Builder connectionRequest() throws IOException {
        System.out.println("Please provide your Node ID for the server.");
        BufferedReader stdin = new BufferedReader(new InputStreamReader(System.in));
        int id;

        while (true) {
            try {
                id = Integer.parseInt(stdin.readLine());
                break;
            } catch (NumberFormatException nfe) {
                System.out.println("Node ID needs to be a number.\nPlease provide a valid Node ID.");
            }
        }

        return ConnectionRequest.newBuilder()
                .setConnection(ConnectionRequest.Connection.NODE)
                .setId(id);
    }

    public static void exitAndClose(InputStream in, OutputStream out, Socket serverSock) throws IOException {
        if (in != null)   in.close();
        if (out != null)  out.close();
        if (serverSock != null) serverSock.close();
        System.exit(0);
    }
}
