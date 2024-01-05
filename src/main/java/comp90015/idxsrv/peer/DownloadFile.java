package comp90015.idxsrv.peer;

import comp90015.idxsrv.textgui.ISharerGUI;

import java.io.*;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Random;


public class DownloadFile extends Thread {

    private DownloadTask downloadTask;
    private ISharerGUI tgui;
    private ArrayList<DownloadBlock> downloadingBlocks;//blocks that currently downloading in a thread
    private ArrayList<Integer> blocksRequired;//blocks needs to be downloaded


    //each downloadFile object represent a downloading file
    public DownloadFile(DownloadTask downloadTask, ISharerGUI tgui) throws FileNotFoundException {

        this.downloadTask=downloadTask;
        this.tgui = tgui;
        this.downloadingBlocks= new ArrayList<DownloadBlock>();
        this.blocksRequired = new ArrayList<Integer>();

    }

    public void run() {

        try {
            //check if this file is already downloaded
            if (downloadTask.fileMgr.isComplete() && downloadTask.fileMgr.checkFileHash()) {
                tgui.logWarn("\"" + downloadTask.fileName + "\" already existed!");
                DownloadCenter.removeMd5(downloadTask.fileMgr.getFileDescr().getFileMd5());
                DownloadCenter.removeDownloadTask(this);
                return;
            }
            //for each block, create a connection with a peer and requesting for download

            boolean[] blockMap = downloadTask.fileMgr.getBlockAvailability();
            for (int i = 0; i < blockMap.length; i++) {
                if (!blockMap[i]) {
                    blocksRequired.add(i);
                }
            }
            Random rand = new Random();
            //assign a thread for downloading each block
            while (!blocksRequired.isEmpty()) {
                int blockIdx = rand.nextInt(blocksRequired.size());
                DownloadBlock downloadBlock = new DownloadBlock(downloadTask, blockIdx, tgui);
                downloadBlock.start();
                downloadingBlocks.add(downloadBlock);
                blocksRequired.remove(blocksRequired.indexOf(blockIdx));
            }
            for (DownloadBlock downloadBlock : downloadingBlocks) {
                downloadBlock.join();
            }
            if (downloadTask.fileMgr.checkFileHash() && downloadTask.fileMgr.isComplete()) {
                String fileMd5=downloadTask.fileDescr.getFileMd5();
                DownloadCenter.removeMd5(fileMd5);
                DownloadCenter.removeDownloadTask(this);
                tgui.logInfo("\"" + downloadTask.fileName + "\" download Complete!");
            } else {
                tgui.logWarn("Unable to download \"" + downloadTask.fileName + "\" from any known peer, please try again later!");
            }
        } catch (IOException ex) {
            tgui.logError("Server received io exception on socket.");
        } catch (NoSuchAlgorithmException ex) {
            tgui.logError("Error occurred while accessing the file");
        } catch (InterruptedException e) {
            tgui.logError("connection interrupted");
        }
    }

    public void shutdown() throws InterruptedException {
        for (DownloadBlock downloadBlock: downloadingBlocks){
            downloadBlock.interrupt();
            downloadBlock.join();
        }
    }



}
