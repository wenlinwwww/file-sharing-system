package comp90015.idxsrv.peer;

import java.io.File;
import java.io.IOException;
import java.io.*;
import java.net.InetAddress;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.concurrent.LinkedBlockingDeque;

import comp90015.idxsrv.filemgr.FileMgr;
import comp90015.idxsrv.message.*;
import comp90015.idxsrv.server.IOThread;
import comp90015.idxsrv.textgui.ISharerGUI;
import comp90015.idxsrv.filemgr.FileDescr;



/**
 * Skeleton Peer class to be completed for Project 1.
 *
 * @author aaron
 */
public class Peer implements IPeer {

    private IOThread ioThread;

    private LinkedBlockingDeque<Socket> incomingConnections;

    private ISharerGUI tgui;

    private String basedir;

    private int timeout;

    private int port;
    private PeerServer peerServer;
    private LinkedBlockingDeque<DownloadTask> downloadQueue;

    private DownloadCenter downloadCenter;


    // New filename list
    private ArrayList<String> nameList;


    public Peer(int port, String basedir, int socketTimeout, ISharerGUI tgui) throws IOException {
        this.tgui = tgui;
        this.port = port;
        this.timeout = socketTimeout;
        this.basedir = new File(basedir).getCanonicalPath();
        this.basedir = new File(basedir).getCanonicalPath();
        this.incomingConnections = new LinkedBlockingDeque<Socket>();
        this.downloadQueue=new LinkedBlockingDeque<DownloadTask>();

        nameList = new ArrayList<String>();

        ioThread = new IOThread(port, incomingConnections, socketTimeout, tgui);
        ioThread.start();

        peerServer = new PeerServer(basedir, timeout, incomingConnections, tgui);
        peerServer.start();

        downloadCenter = new DownloadCenter(downloadQueue,tgui);
        downloadCenter.start();

    }

    public void shutdown() throws InterruptedException, IOException {
        ioThread.shutdown();
        ioThread.interrupt();
        ioThread.join();
        peerServer.interrupt();
        peerServer.join();
        downloadCenter.shutdown();
        downloadCenter.interrupt();
        downloadCenter.join();

    }

    /*
     * Students are to implement the interface below.
     */

    @Override
    public void shareFileWithIdxServer(File file, InetAddress idxAddress, int idxPort, String idxSecret,
                                       String shareSecret) {
        try (Socket socket = new Socket(idxAddress, idxPort)) {
            // Establish socket connection
            if (!isConnect(socket, idxSecret)) {
                //exit the method when serve secret authentication failed
                return;
            }

            // Share request part
            // Convert file to random access file (permission: read-write)
            RandomAccessFile randomFile = new RandomAccessFile(file, "rw");
            FileDescr desc = new FileDescr(randomFile);
            // share request message
            writeMsg(bufferedWrite(socket), new ShareRequest(desc, file.getName(), shareSecret, this.port));

            Message msg = readMsg(bufferedRead(socket));
            if (!msg.getClass().getName().equals(ShareReply.class.getName())) {
                if (msg.getClass().getName().equals(ErrorMsg.class.getName())) {
                    ErrorMsg e = (ErrorMsg) msg;
                    tgui.logWarn(e.msg);
                } else {
                    tgui.logWarn("Wrong message type. Unable to process this message from server");
                }
            }

            //share reply
            assert msg instanceof ShareReply;
            ShareReply sReply = (ShareReply) msg;

            // Check filename whether exist or not
            if (nameList.contains(file.getName())) {
                tgui.logInfo("The file has been shared!");
            } else {
                // Add filename to list
                nameList.add(file.getName());
                // tgui.logDebug(nameList.toString());

                // Add share records
                FileMgr mgr = new FileMgr(file.getName(), desc);
                Path basePath = Paths.get(basedir);
                Path relativePath = basePath.relativize(Paths.get(file.getCanonicalPath()));
                String relativeName = relativePath.toString();
                tgui.addShareRecord(relativeName, new ShareRecord(mgr, sReply.numSharers, "sharing",
                        idxAddress, idxPort, idxSecret, shareSecret));
                tgui.logInfo("Successful sharing file \"" + file.getName() + "\"!");
            }
        } catch (IOException | JsonSerializationException e) {
            tgui.logError("shareFileWithIdxServer received io exception on socket/Invalid message.");
        } catch (NoSuchAlgorithmException e) {
            tgui.logError("Error when getting file descriptor");
        }
    }

    @Override
    public void searchIdxServer(String[] keywords,
                                int maxhits,
                                InetAddress idxAddress,
                                int idxPort,
                                String idxSecret) {
        try (Socket socket = new Socket(idxAddress, idxPort)) {
            // Connect to server (include authenticate)
            if (!isConnect(socket, idxSecret)) {
                //exit the method when serve secret authentication failed
                return;
            }

            // Search request part
            // Before search keyword, clean the search first
            tgui.clearSearchHits();
            // Search request message
            writeMsg(bufferedWrite(socket), new SearchRequest(maxhits, keywords));

            Message msg = readMsg(bufferedRead(socket));
            if (!msg.getClass().getName().equals(SearchReply.class.getName())) {
                if (msg.getClass().getName().equals(ErrorMsg.class.getName())) {
                    ErrorMsg e = (ErrorMsg) msg;
                    tgui.logWarn(e.msg);
                } else {
                    tgui.logWarn("Wrong message type. Unable to process this message from server");
                }
            }

            assert msg instanceof SearchReply;
            SearchReply seReply = (SearchReply) msg;
            // lengths of hits and seedCounts is the same
            for (int i = 0; i < seReply.hits.length; i++) {
                // search record 6 param
                FileDescr fileDescr = seReply.hits[i].fileDescr;
                int numberShares = seReply.seedCounts[i];
                String shareSecret = seReply.hits[i].secret;
                String filename = seReply.hits[i].filename;
                // Add an entry to the search hits table
                tgui.addSearchHit(filename, new SearchRecord(fileDescr, numberShares, idxAddress,
                        idxPort, idxSecret, shareSecret));
            }
            int hitNumber = seReply.hits.length;
            if (hitNumber != 0) {
                tgui.logInfo("Successful retrieval and " + hitNumber + " hit(s) for search!");
            } else {
                tgui.logWarn("No hits for search!");
            }
        } catch (IOException | JsonSerializationException e) {
            tgui.logError("searchIdxServer received io exception on socket/Invalid message");
            e.printStackTrace(); // Print out error information
        }
    }

    @Override
    public boolean dropShareWithIdxServer(String relativePathname, ShareRecord shareRecord) {
        // Establish connection to server (include authenticate)
        int port = shareRecord.idxSrvPort;
        InetAddress address = shareRecord.idxSrvAddress;
        try (Socket socket = new Socket(address, port)) {
            String secret = shareRecord.idxSrvSecret;
            if (!isConnect(socket, secret)) {
                //return drop false when serve secret authentication failed
                return false;
            }

            // Drop file part
            // Request drop shared file - DropShareRequest 4 param (filename/md5/sSecrete/port)
            String filename = covertFilename(relativePathname);
            String fileMd5 = shareRecord.fileMgr.getFileDescr().getFileMd5();
            String sharingSecret = shareRecord.sharerSecret;
            writeMsg(bufferedWrite(socket), new DropShareRequest(filename, fileMd5, sharingSecret, this.port));

            Message msg = readMsg(bufferedRead(socket));
            if (!msg.getClass().getName().equals(DropShareReply.class.getName())) {
                if (msg.getClass().getName().equals(ErrorMsg.class.getName())) {
                    ErrorMsg e = (ErrorMsg) msg;
                    tgui.logWarn(e.msg);
                } else {
                    tgui.logWarn("Wrong message type. Unable to process this message from server");
                }
            }
            // Reply drop file
            assert msg instanceof DropShareReply;
            DropShareReply dReply = (DropShareReply) msg;
            // tgui.logDebug(dReply.toString());
            //
            if (dReply.success) {
                nameList.remove(filename);
            }
            // Return whether drop success
            return dReply.success;
        } catch (IOException | JsonSerializationException e) {
            tgui.logError("dropFileWithIdxServer received io exception on socket/Invalid message");
            e.printStackTrace(); // Print out error information
        } finally {
            tgui.logInfo("Successfully dropped file \"" + covertFilename(relativePathname) + "\"!");
        }
        return false;
    }

    @Override
    public void downloadFromPeers(String relativePathname, SearchRecord searchRecord) {
        try (Socket socket = new Socket(searchRecord.idxSrvAddress, searchRecord.idxSrvPort)) {
            String secret = searchRecord.idxSrvSecret;
            if (!isConnect(socket, secret)) {
                //exit the method when serve secret authentication failed
                return;
            }

            //get information about the selecting file
            Path path = Paths.get(relativePathname);
            String fileName = String.valueOf(path.getFileName());
            String fileMd5 = searchRecord.fileDescr.getFileMd5();

            //send look up request to server
            writeMsg(bufferedWrite(socket), new LookupRequest(fileName, fileMd5));
            //get lookup reply from server
            Message msg = readMsg(bufferedRead(socket));
            if (!msg.getClass().getName().equals(LookupReply.class.getName())) {
                if (msg.getClass().getName().equals(ErrorMsg.class.getName())) {
                    ErrorMsg e = (ErrorMsg) msg;
                    tgui.logWarn(e.msg);
                } else {
                    tgui.logWarn("Wrong message type. Unable to process this message from server");
                }
            }
            assert msg instanceof LookupReply;
            LookupReply lookupReply = (LookupReply) msg;
            socket.close();

            //create a download task and add to download queue
            DownloadTask downloadTask = new DownloadTask(fileName, lookupReply, searchRecord.fileDescr);
            if (!downloadQueue.offer(downloadTask)) {
                tgui.logError("Download queue is fulled. Please try again later");
            }
        } catch (JsonSerializationException jse) {
            tgui.logError("Invalid message");
        } catch (IOException e) {
            tgui.logError("unable to connect");
        } catch (NoSuchAlgorithmException e) {
            tgui.logError("Error when getting file descriptor");
        }
    }


    // Copy from Server.java (line 240-245)
    private void writeMsg(BufferedWriter bufferedWriter, Message msg) throws IOException {
        // tgui.logDebug("sending: " + msg.toString());
        bufferedWriter.write(msg.toString());
        bufferedWriter.newLine();
        bufferedWriter.flush();
    }

    // Copy from Server.java (line 247-256)
    private Message readMsg(BufferedReader bufferedReader) throws IOException, JsonSerializationException {
        String jsonStr = bufferedReader.readLine();
        if (jsonStr != null) {
            // tgui.logDebug("received: " + msg.toString());
            return (Message) MessageFactory.deserialize(jsonStr);
        } else {
            throw new IOException();
        }
    }


    // Buffer reader
    private BufferedReader bufferedRead(Socket socket) throws IOException {
        // Input Stream
        InputStream input = socket.getInputStream();
        return new BufferedReader(new InputStreamReader(input, StandardCharsets.UTF_8));
    }

    // Buffer writer
    private BufferedWriter bufferedWrite(Socket socket) throws IOException {
        // Output Stream
        OutputStream output = socket.getOutputStream();
        return new BufferedWriter(new OutputStreamWriter(output, StandardCharsets.UTF_8));
    }

    // Authenticate part - check it is an authenticate request
    private boolean isConnect(Socket socket, String idxSecret) throws IOException, JsonSerializationException {
        // Welcome message
        socket.setSoTimeout(timeout);
        Message msg = readMsg(bufferedRead(socket));
        //verify type of message incoming
        if (!msg.getClass().getName().equals(WelcomeMsg.class.getName())) {
            if (msg.getClass().getName().equals(ErrorMsg.class.getName())) {
                ErrorMsg e = (ErrorMsg) msg;
                tgui.logWarn(e.msg);
            } else {
                tgui.logWarn("Wrong message type. Unable to process this message from server");
            }
            return false;
        }
        WelcomeMsg welcomeMsg = (WelcomeMsg) msg;
        String welcome = welcomeMsg.msg;
        tgui.logInfo(welcome);
        // Authenticate request secrete
        writeMsg(bufferedWrite(socket), new AuthenticateRequest(idxSecret));
        msg = readMsg(bufferedRead(socket));
        //verify type of message incoming
        if (!msg.getClass().getName().equals(AuthenticateReply.class.getName())) {
            if (msg.getClass().getName().equals(ErrorMsg.class.getName())) {
                ErrorMsg e = (ErrorMsg) msg;
                tgui.logWarn(e.msg);
            } else {
                tgui.logWarn("Wrong message type. Unable to process this message from server");
            }
            return false;
        }

        // Authenticate reply
        AuthenticateReply aReply = (AuthenticateReply) msg;
        if (aReply.success) {
            tgui.logInfo("The access is authorized");
        } else {
            tgui.logWarn("The index server secret is wrong, please try again!");
        }
        return aReply.success;
    }


    // Get filename according to relative path name
    private String covertFilename(String relativeName) {
        Path path = Paths.get(relativeName);
        return String.valueOf(path.getFileName());
    }
}
