import buffers.RequestProtos.*;
import buffers.ResponseProtos.*;

import java.io.*;
import java.net.Socket;


public class Client {
    public static void main (String[] args) throws Exception {
        Socket serverSock = null;
        OutputStream out = null;
        InputStream in = null;
        int port = 9099;
        String host = "localhost";

        try {
            host = args[0];
            port = Integer.parseInt(args[1]);
        } catch (NumberFormatException nfe) {
            System.exit(2);
        }

        ConnectionRequest.Builder op = IDRequest();
        ServerToClientResponse response;
        try {
            // connect to the server
            serverSock = new Socket(host, port);

            // write to the server
            out = serverSock.getOutputStream();
            in = serverSock.getInputStream();

            op.build().writeDelimitedTo(out);

            while (true) {
                // read from the server
                response = ServerToClientResponse.parseDelimitedFrom(in);

                ClientToServerRequest.Builder req = ClientToServerRequest.newBuilder();

                if (response != null) {
                    switch (response.getResponseType()) {
                        case GREETING:
                            System.out.println(response.getMessage());
                            req = chooseMenu(req, response);
                            break;
                        case STANDBY:
                            System.out.println(response.getMessage());
                            while (true) {
                                response = ServerToClientResponse.parseDelimitedFrom(in);
                                if (response != null) {
                                    if (response.getResponseType() == ServerToClientResponse.ResponseType.DECISION) {
                                        System.out.println(response.getMessage());
                                        req = chooseMenu(req, response);
                                        break;
                                    } else {
                                        break;
                                    }
                                }
                            }
                            break;
                        case DECISION:
                            System.out.println(response.getMessage());
                            req = chooseMenu(req, response);
                            break;
                        case BYE:
                            System.out.println(response.getMessage());
                            exitAndClose(in, out, serverSock);
                            break;
                    }
                    req.build().writeDelimitedTo(out);
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        } finally {
            exitAndClose(in, out, serverSock);
        }
    }

    static ConnectionRequest.Builder IDRequest() throws IOException {
        System.out.println("Please provide your client ID for the server.");
        BufferedReader stdin = new BufferedReader(new InputStreamReader(System.in));
        int id = 0;
        while (true) {
            try {
                id = Integer.parseInt(stdin.readLine());
                break;
            } catch (NumberFormatException nfe) {
                System.out.println("Client ID needs to be a number.\nPlease provide a valid client ID.");
            }
        }

        return ConnectionRequest.newBuilder()
                .setConnection(ConnectionRequest.Connection.CLIENT)
                .setId(id);
    }

    static ClientToServerRequest.Builder chooseMenu(ClientToServerRequest.Builder req, ServerToClientResponse response) throws IOException {
        while (true) {
            System.out.println(response.getMenuoptions());
            System.out.print("Enter a number 1-6: ");
            BufferedReader stdin = new BufferedReader(new InputStreamReader(System.in));
            String menu_select = stdin.readLine();

            switch (menu_select) {
                case "1":
                    req.setOperationType(ClientToServerRequest.OperationType.SUMMARY);
                    return req;
                case "2":
                    System.out.println("Please enter the amount that you want to request as a number (e.g 100 and 100.25 are both valid):");
                    double amount = 0.0;
                    while (true) {
                        try {
                            amount = Double.parseDouble(stdin.readLine());
                            break;
                        } catch (NumberFormatException nfe) {
                            System.out.println("That is not a valid input. Please provide a valid number:");
                        }
                    }
                    req.setAmount(amount);
                    req.setOperationType(ClientToServerRequest.OperationType.CREDIT);
                    return req;
                case "3":
                    System.out.println("Please enter the amount that you want to payback as a number (e.g 100 and 100.25 are both valid):");
                    double payback = 0.0;
                    while (true) {
                        try {
                            payback = Double.parseDouble(stdin.readLine());
                            break;
                        } catch (NumberFormatException nfe) {
                            System.out.println("That is not a valid input. Please provide a valid number:");
                        }
                    }
                    req.setAmount(payback);
                    req.setOperationType(ClientToServerRequest.OperationType.PAYBACK);
                    return req;
                case "4":
                    req.setOperationType(ClientToServerRequest.OperationType.CLIENTSUMMARY);
                    return req;
                case "5":
                    req.setOperationType(ClientToServerRequest.OperationType.NODESUMMARY);
                    return req;
                case "6":
                    req.setOperationType(ClientToServerRequest.OperationType.QUIT);
                    return req;
                default:
                    System.out.println("\nNot a valid choice, please choose again");
                    break;
            }
        }
    }

    static void exitAndClose(InputStream in, OutputStream out, Socket serverSock) throws IOException {
        if (in != null) in.close();
        if (out != null) out.close();
        if (serverSock != null) serverSock.close();
        System.exit(0);
    }
}
