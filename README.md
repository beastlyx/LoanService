# Author: Borys Banaszkiewicz, SER321 Assignment 5

## Description
This is a simple implementation of a program that allows multiple clients and multiple nodes to be connected to the server simultaneously.
The client(s) connect and have a menu option to either see any existing loans that they may have, they can request a loan, or
they can pay back an existing loan. (FOR GRADING PURPOSES I INCLUDED 2 MORE OPTIONS SO IT IS EASIER ON THE GRADER. Option 4 lets
you see the list of all clients and their data, option 5 lets you see the list of all nodes and their data).

The server class is responsible for accepting multiple connections from clients and nodes and has to maintain how each connected
thread communicates with the server (client and nodes have different communications between server). 

If client sends a request for a loan, the server asks the nodes to either approve or deny this loan. If approved by the 
majority of the nodes, the nodes that approved the loan will split the loan amount between themselves while the nodes that
denied the loan will remain unchanged. 

The client can also send a request to pay for an existing loan, which the server will then evaluate which nodes are 
connected to this loan and will evenly split the paid amount between those nodes.


## How to run the program
The proto file can be compiled using
``gradle generateProto``

This will also be done when building the project.

* Please run `gradle runServer -q --console=plain` and `gradle runClient -q --console=plain` and `gradle runNode -q --console=plain` together.
* NOTE: Nodes and clients can have multiple instances running on the server simultaneously, just run the commands for client
and node from above in a new terminal tab.
* Recommended that you include the flag `-q --console=plain` to get limited output
* Programs runs on hostIP
* Port and hostIP specification is optional.
* Can also be run by doing `gradle runServer -Pport=port` and `gradle runClient -Phost=hostIP -Pport=port` and 
`gradle runNode -Phost=hostIP -Pport=port` if you dont want default port and host.

## How to use the program
The nodes do not require any input from the user apart from an initial nodeID, and if the node is not recognized by the 
server then an amount will need to be provided as well. After that the nodes will be idle and mainly be printing 
messages to show the communication between the server and the nodes and their responses as well as what they are doing. 
The client requires that you provide the client ID on startup, then a welcome message will be shown along with a menu option
that has the option to view existing loan for the client, get a new loan, request to make payment on existing loan, as well as
additional options for viewing all nodes and clients on the server (not specific to 1 server instance, this will show
history of all nodes/clients that connected to the server over time). The client instance will be requesting that the 
user enter certain information such as loan amount of payment amount. 


## Screencast
[Activity 2 screencast](https://drive.google.com/file/d/12Jg1KZhsuE2viOk8I5e27VKfpy_Lqul-/view?usp=sharing)

## Requirements fulfilled (All requirements from document are complete)
 - [x] You will need a README.md which contains the following (most goes to the protocol):
   - [x] (4 points) A short screencast where you show your project in action and explain
      everything we need to know about it. (if you do not include a screencast you
      might loose more points if we cannot see some of your features)
   - [x] (3 points) Explain your project and which requirements you were able to fulfill.
   - [x] (3 points) Explain how to start your project and which Gradle commands to
      use
   - [x] (6 points) Explain your protocol.


- [x] (4 points) Project is well structured and easy to understand.


- [x] (5 points) We can run the project as specified in the Readme, we only need to copy
   and paste the commands and things connect correctly.


- [x] (4 points) It is possible to start one Client, one Leader and at least 2 Nodes. All of
   these are connected correctly.


- [x] (3 points) It is possible to start up to 5 Nodes.


- [x] (3 points) Leader will ask the Client for their clientID, which should be provided
   by the client. You can just use a number if you like and you can assume the number
   entered is correct. This id will be sent to the Leader.


- [x] (3 points) Client will then have the choice between requesting a line of credit or
   paying part of an existing loan. This should be a Client-side choice that is followed
   by input for the amount of "credit" or "payback".


- [x] (3 points) The leader receives the request ("credit", "payback" – or however you
   want to call this) with the amount. See below for details on each operation.


- [x] Credit:
    - [x] (4 points) If the client wants credit, then the leader sends all known Nodes
   the information that a specific client wants a credit of that amount (clientID,
   amount).
  - [x] (4 points) Nodes will check if that client already has credit with them and if
   the node (each representing a bank) has enough money available. If the client
   with that ID does not have credit with them yet and the bank has at least
   150% of what the client wants available (yes we want the bank to have more
   than what the client wants), they respond with a "yes". If that is not the case,
   they respond with a "no".
  - [x] (4 points) If the majority of nodes (so if two nodes are connected, both have
   to answer yes) say "yes", then the credit is granted to the client.
  - [x] (4 points) The leader will split up the amount as evenly as possible between
   the nodes and notify them that this client now has this amount of credit with
   them.
  - [x] (4 points) Nodes and the Leader should store the value of the credit given to
   a specific Client. The Nodes will have to persistently save that amount and
   clientID. The Client is informed that they get the credit and the leader stores
   the amount persistently as well.
  - [x] (3 points) Nodes will decrease their available money based on how much they
   gave to the Client.
  - [x] (2 points) If the majority vote "no", the Client will be informed that they do
   not get any credit and the Nodes will not decrease their available money.
  - [x] (6 points) The Leader asking for votes should be threaded. As an example
   of why this is necessary, let’s assume a Node takes a while to respond with a
   yes/no, such as a delay of 10 seconds. If there are 4 Nodes then it would take
   at least 30 seconds (majority) until we can actually count if we got a majority
   vote. This is how it would work in a single-threaded program where the Leader
   asks one Node at a time. We don’t want to wait that long. Come up with a way
   that the Nodes can actually "work" in parallel so in this hypothetical situation
   we would not be more than 30 seconds but just about 10 seconds instead.


- [x] Pay back:
    - [x] (3 points) If the client wants to pay money back, then the Leader informs the
    Nodes that this client wants to pay money back.
    - [x] (3 points) Nodes will check how much the client owes and return how much
    that clients owes to them to the Leader.
    - [x] (3 points) If the client wants to pay back more than is owed, they will just get
    an error message which the leader will send to the client.
    - [x] (6 points) If the client pays back partially or all of their existing credit, the
    Leader will split up the amount to each Node. You can split it up 2/3 1/3
    if you like. You should of course not pay back more than is owed to a Node.
    The Nodes will update their records, the Leader will update its records and the
    Client will be informed about how much debt is still owed.


- [x] (5 points) You should make sure that when a node crashes the whole system does
    not go down. If the Leader crashes then of course a restart might be needed but the
    data should be persistent.


- [x] (5 points) If a restart is needed, the first thing the leader should do when a node connects
    to it is check in with the node and verify their records, e.g. client=1 owes=100
    based on leader, node1 says client=1 owes=50, node2 says client=1 owes=20. In this
    case something went wrong and you might want to check what happened. Maybe
    have the leader keep track of all transactions so you can roll back.


- [x] (5 points) This gets interesting if more than one client can interact with the leader
    and make requests. The system will need to make sure it handles them correctly
    (like preventing the common multi-threading issues that we learned about) and the
    order of transactions is still correct.


## Protocol Elaboration

*Note:* **CL == client, SV == server, ND == node**

### Initial handshake
CL connection
```
ConnectionRequest: CLIENT
Required Fields: Id number as an integer
```
ND connection
```
ConnectionRequest: NODE
Required Fields: Id number as an integer
```
### Logging in
CL Request  
Client sends over their client ID
```
OperationType: ID
Required Fields: Id number as an integer
```
ND Request  
Node sends over their node ID
```
OperationType: ID
Required Fields: Id number as an integer
``` 

SV Response to CL  
Server responds with a greeting message
```
ResponseType: GREETING
Require Fields: message -- this is just a greeting message to the client, 
				menuoptions -- these are the menu options the client has, 
```
SV Response to ND (Node logging in for the first time)  
Server responds with a greeting message and a request for the amount that the node wishes to make available
```
ResponseType: GREETING
Require Fields: message -- this is just a greeting message to the node with request for amount, 
				
```
SV Response to ND (Node exists in server data)  
Server responds with a standby message and a request for the node to await any client requests
```
ResponseType: STANDBY
Require Fields: message -- this is just a message to the node with request to standby, 
				
```
### Get Account Summary
CL Request  
Clients wants their account summary
```
RequestType: SUMMARY
Required Fields: *none*
```
SV Response  
Server responds with clients outstanding loans (if any)
```
ResponseType: GREETING
Required Fields: message -- this is a message to the client with the information containing any outstanding loans
				menuoptions -- these are the menu options the client has, 
				next = MENU
```
### Request line of credit
CL Request  
Client wants to request a line of credit
```
RequestType: CREDIT
Required Fields: amount -- amount the client is requesting
```
SV Response to ND  
Server sends the amount to each connected node and waits for a response before sending anything back to client.
```
ResponseType: CREDIT
Required Fields: amount -- amount the client is requesting
```
ND Response to SV  
Each node will send one of 2 responses below depending on their decision.
```
RequestType: ACCEPT
Required Fields: message -- message with "Accept" 

RequestType: DECLINE
Required Fields: message -- message with reason for declining the request.
```
SV Response to ND  
each node that responds will be put on idle while the server is evaluating the responses received and then communicating 
the information back to the client. After evaluation the server will respond with 1 of 2 messages to each node:
```
ResponseType: APPROVED
Required Fields: message -- includes how much is split between each node and assigns that amount to the node.

ResponseType: STANDBY 
Required Fields: message -- if not approved the nodes go back to idle mode to wait for client request
```
ND request to SV  
Only if the node was one of the ones that approved the loan and if the loan was distributed, node responds with a confirmation
that the loan was processed on their end.
```
RequestType: CONFIRM
Required Fields: message -- Includes confirmation from node of new loan
```
SV Response to CL  
Client is updated about the decision made by the nodes
```
ResponseType: DECISION
Required Fields: message -- either approval message (which then creates the loan for the client) or a decline message followed
                            by an explanation of why they were denied.
```
### Request to payback a loan from client
CL Request to SV  
```
RequestType: PAYBACK
Required Fields: amount -- amount the client wants to payback
```
SV Response to CL  
```
ResponseType: DECISION
Required Fields: message -- updated client on if the amount was approved or not and the reason why (client will need to 
                            select the option from menu again).
                 menuoptions -- these are the menu options the client has, 
```
SV Response to ND  
only if the payment was valid (existing loan and payment not more than owed)
```
RequestType: PAYBACK
Required Fields: amount -- amount the client wants to payback split per node that has client under its active loans
```

### Exit (from main menu)
1. CL Request  
```
RequestType: QUIT
Required Fields: *none*
```
2. SV Response  
```
ResponseType: BYE
Required Fields: message -- a goodbye message from the server
```