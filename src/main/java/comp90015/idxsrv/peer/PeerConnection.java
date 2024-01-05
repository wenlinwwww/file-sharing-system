package comp90015.idxsrv.peer;

import comp90015.idxsrv.filemgr.BlockUnavailableException;
import comp90015.idxsrv.filemgr.FileDescr;
import comp90015.idxsrv.filemgr.FileMgr;
import comp90015.idxsrv.message.*;

import java.io.*;
import java.net.Socket;
import java.net.SocketException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.security.NoSuchAlgorithmException;
import java.util.Base64;

public class PeerConnection extends Thread {
    private Socket socket;
    private String dir;


    public PeerConnection(Socket socket, String dir) throws SocketException {
        this.socket = socket;
        this.dir=dir;

    }

    public void run() {
        try {
            processRequest(socket);
            socket.close();
        } catch (IOException e) {
            throw new RuntimeException(e);
        }

    }



    private void processRequest(Socket socket) throws IOException {
        //tgui.logDebug("Server processing request on connection " + socket.getInetAddress().getHostAddress());
        InputStream inputStream = socket.getInputStream();
        OutputStream outputStream = socket.getOutputStream();
        BufferedReader bufferedReader = new BufferedReader(new InputStreamReader(inputStream, StandardCharsets.UTF_8));
        BufferedWriter bufferedWriter = new BufferedWriter(new OutputStreamWriter(outputStream, StandardCharsets.UTF_8));
        // get a message
        Message msg;
        try {
            msg = readMsg(bufferedReader);
        } catch (JsonSerializationException e1) {
            writeMsg(bufferedWriter, new ErrorMsg("Invalid message"));
            return;
        }

        if (msg.getClass().getName() .equals(BlockRequest.class.getName()) ) {
            processBlockRequest(bufferedWriter, (BlockRequest) msg);
        } else {
            // close the streams
            writeMsg(bufferedWriter, new ErrorMsg("Expecting a request message"));
        }

        bufferedReader.close();
        bufferedWriter.close();
    }

    // Methods to process block requests. Will only be called if incoming request is verified as block request
    private void processBlockRequest(BufferedWriter bufferedWriter, BlockRequest br) throws IOException {
        String filename = br.filename;
        String fileMd5 = br.fileMd5;
        Integer blockIdx = br.blockIdx;
        Path basePath = Paths.get(dir);
        String filePath = basePath.resolve(filename).toString();
        try {
            File file = new File(filePath);
            RandomAccessFile randomAccessFile = new RandomAccessFile(file, "r");
            FileDescr fileDescr = new FileDescr(randomAccessFile);
            FileMgr fileMgr = new FileMgr(filePath, fileDescr);
            if (fileMd5.equals(fileDescr.getFileMd5())) {
                byte[] blockContent = fileMgr.readBlock(blockIdx);
                String bytes = Base64.getEncoder().encodeToString(blockContent);
                writeMsg(bufferedWriter, new BlockReply(filename, fileMd5, blockIdx, bytes));
            }
        } catch (NoSuchAlgorithmException e) {
            writeMsg(bufferedWriter, new ErrorMsg("Algorithm issue with server"));
        } catch (BlockUnavailableException e) {
            writeMsg(bufferedWriter, new ErrorMsg("Requested block is not available at this peer"));
        }
    }

    private void writeMsg(BufferedWriter bufferedWriter, Message msg) throws IOException {
        //tgui.logDebug("sending: " + msg.toString());
        bufferedWriter.write(msg.toString());
        bufferedWriter.newLine();
        bufferedWriter.flush();
    }

    private Message readMsg(BufferedReader bufferedReader) throws IOException, JsonSerializationException {
        String jsonStr = bufferedReader.readLine();
        if (jsonStr != null) {
            Message msg = (Message) MessageFactory.deserialize(jsonStr);
            //tgui.logDebug("received: " + msg.toString());
            return msg;
        } else {
            throw new IOException();
        }
    }

}
