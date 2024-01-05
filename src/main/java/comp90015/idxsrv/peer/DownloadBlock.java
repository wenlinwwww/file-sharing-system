package comp90015.idxsrv.peer;

import comp90015.idxsrv.filemgr.FileMgr;
import comp90015.idxsrv.message.*;
import comp90015.idxsrv.server.IndexElement;
import comp90015.idxsrv.textgui.ISharerGUI;

import java.io.*;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Random;

public class DownloadBlock extends Thread {
    private DownloadTask downloadTask;
    private ArrayList<IndexElement> hosts;
    private FileMgr fileMgr;
    private Integer blockIdx;
    private ISharerGUI tgui;

    //each downloadBlock object representing a block of a file
    public DownloadBlock(DownloadTask downloadTask, Integer blockIdx, ISharerGUI tgui) {
        this.downloadTask=downloadTask;
        this.fileMgr=downloadTask.fileMgr;
        this.hosts = new ArrayList<IndexElement>();
        for (IndexElement hit : downloadTask.lookupReply.hits) {
            hosts.add(hit);
        }
        this.blockIdx = blockIdx;
        this.tgui = tgui;
    }

    public void run() {
        tgui.logInfo("start download block " + blockIdx);
        Random rand = new Random();
        while (!hosts.isEmpty()) {
            //if current idx is complete, break
            if (fileMgr.isComplete() || fileMgr.isBlockAvailable(blockIdx)) {
                break;
            }
            int hostIdx = rand.nextInt(hosts.size());
            IndexElement host = hosts.get(hostIdx);
            try {
                Socket socket = new Socket(host.ip, host.port);
                InputStream inputStream = socket.getInputStream();
                OutputStream outputStream = socket.getOutputStream();
                BufferedReader bufferedReader = new BufferedReader(new InputStreamReader(inputStream, StandardCharsets.UTF_8));
                BufferedWriter bufferedWriter = new BufferedWriter(new OutputStreamWriter(outputStream, StandardCharsets.UTF_8));

                BlockRequest blockRequest = new BlockRequest(host.filename, host.fileDescr.getFileMd5(), blockIdx);
                writeMsg(bufferedWriter, blockRequest);
                tgui.logInfo("Try to download \"" + host.filename + "\" block " + blockIdx + " from peer " + host.ip);
                Message peerMsg;
                peerMsg = readMsg(bufferedReader);//block reply

                if (peerMsg.getClass().getName().equals(BlockReply.class.getName())) {
                    BlockReply blockReply = (BlockReply) peerMsg;
                    String bytesString = blockReply.bytes;
                    //decode bytes by base 64
                    byte[] bytes = Base64.getDecoder().decode(bytesString);
                    //check if block hash is correct
                    if (fileMgr.checkBlockHash(blockRequest.blockIdx, bytes)) {
                        //write received and decoded bytes into random access file
                        //synchronized method for one download task (one file)
                        if (downloadTask.writeDownloadBlock(blockIdx,bytes)) {
                            tgui.logInfo("block " + blockIdx + "download complete");
                        }
                    }
                } else {
                    tgui.logWarn("Fail to download \"" + host.filename + "\" block " + blockIdx + " from peer " + host.ip);
                }
                this.hosts.remove(host);
                writeMsg(bufferedWriter, new GoodBye());
                socket.close();
            } catch (JsonSerializationException e) {
                tgui.logError("Invalid Message from peer");
            } catch (IOException e) {
                tgui.logError("Server received io exception on socket.");
            }
        }
        //warning message after trying all the hosts and fail to download
        if (!fileMgr.isBlockAvailable(blockIdx)) {
            tgui.logWarn("Fail to download block\"" + blockIdx + "\", please try again later!");
        }
    }


    private void writeMsg(BufferedWriter bufferedWriter, Message msg) throws IOException {
        //tgui.logInfo("sending: " + msg.toString());
        bufferedWriter.write(msg.toString());
        bufferedWriter.newLine();
        bufferedWriter.flush();
    }

    private Message readMsg(BufferedReader bufferedReader) throws IOException, JsonSerializationException {
        String jsonStr = bufferedReader.readLine();
        if (jsonStr != null) {
            Message msg = (Message) MessageFactory.deserialize(jsonStr);
            //tgui.logInfo("received: " + msg.toString());
            return msg;
        } else {
            throw new IOException();
        }
    }

}
