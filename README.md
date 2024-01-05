# file-sharing-system

## Structure
- **Index Server** : a process that acts as a server, to manage an index of files and IP addresses of file sharers
- **Peer** : a process that acts as both a client to an Index Server, and as a server for sharing file data with other Peers
- `comp90015.idxsrv.Filesharer.java` : main class for the Peer component 
- `comp90015.idxsrv.IdxSrv.java` : main class for the Index Server component 
- `comp90015.idxsrv.filemgr` : package providing file blocking and hashing management 
- `comp90015.idxsrv.message` : package providing a JSON annotation message factory 
- `comp90015.idxsrv.peer` : package providing the Peer implementation 
- `comp90015.idxsrv.server` : package providing the Index Server implementation 
- `comp90015.idxsrv.textgui` : package providing the terminal text GUI interface 

### Compiling and running the Server

    cd idxsrv
    mvn assembly:single
    java -cp target/idxsrv-0.0.1-SNAPSHOT-jar-with-dependencies.jar comp90015.idxsrv.IdxSrv

Use the `-h` option for help on options to change server configuration settings when running the server.

### Compiling and running the Peer

    cd idxsrv
    mvn assembly:single
    java -cp target/idxsrv-0.0.1-SNAPSHOT-jar-with-dependencies.jar comp90015.idxsrv.Filesharer