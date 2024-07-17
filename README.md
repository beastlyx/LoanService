# LoanService - Borys Banaszkiewicz

## Description
This is a simple implementation of a program that allows multiple clients and multiple nodes to be connected to the server simultaneously.
The client(s) connect and have a menu option to either see any existing loans that they may have, they can request a loan, or
they can pay back an existing loan.

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
