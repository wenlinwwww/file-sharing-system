package comp90015.idxsrv.peer;
import comp90015.idxsrv.textgui.ISharerGUI;

import java.io.*;
import java.net.Socket;
import java.util.concurrent.LinkedBlockingDeque;

public class PeerServer extends Thread {

    private String dir;
    private ISharerGUI tgui;
    private int socketTimeout;
    private LinkedBlockingDeque<Socket> incomingConnections;

    //peer server assign a thread to a new connection with a peer
    public PeerServer(String dir, int socketTimeout, LinkedBlockingDeque<Socket> incomingConnections, ISharerGUI tgui) {
        this.dir = dir;
        this.tgui = tgui;
        this.socketTimeout = socketTimeout;
        this.incomingConnections = incomingConnections;
    }

    @Override
    public void run() {
        tgui.logInfo("Server thread running.");
        while (!isInterrupted()) {
            try {
                Socket socket = incomingConnections.take();
                socket.setSoTimeout(this.socketTimeout);
                PeerConnection peerConnection = new PeerConnection(socket,this.dir);
                peerConnection.start();

            } catch (IOException e) {
                tgui.logError("Server received io exception on socket.");
            } catch (InterruptedException e) {
                tgui.logError("connection interrupted");
            } catch (NullPointerException e) {
                tgui.logInfo("All incoming connection is processed");
            }
        }
    }




}


