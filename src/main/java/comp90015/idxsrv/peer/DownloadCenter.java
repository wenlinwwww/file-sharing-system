package comp90015.idxsrv.peer;

import comp90015.idxsrv.textgui.ISharerGUI;

import java.io.FileNotFoundException;
import java.util.ArrayList;
import java.util.concurrent.LinkedBlockingDeque;

public class DownloadCenter extends Thread{

    private LinkedBlockingDeque<DownloadTask> downloadQueue;
    private static ArrayList<String> downloadingFileMd5;
    private ISharerGUI tgui;
    private static ArrayList<DownloadFile> downloadingFile;

    public DownloadCenter(LinkedBlockingDeque<DownloadTask> downloadQueue, ISharerGUI tgui){
        this.downloadQueue=downloadQueue;
        downloadingFileMd5=new ArrayList<String>();
        downloadingFile=new ArrayList<DownloadFile>();
        this.tgui=tgui;
    }
    public void run() {
        while (!interrupted()) {
            try {
                DownloadTask downloadTask = downloadQueue.take();
                //check if this download task have peer(s) to download from
                String fileMd5 = downloadTask.fileDescr.getFileMd5();
                if(downloadTask.lookupReply.hits.length==0) {
                    tgui.logWarn("No peer is currently sharing this file!");
                    continue;
                }
                //check if this download task is already in the download queue
                if (!downloadingFileMd5.contains(fileMd5)) {
                    DownloadFile downloadFile = new DownloadFile(downloadTask, tgui);
                    downloadFile.start();
                    addMd5(fileMd5);
                    addDownloadTask(downloadFile);
                } else {
                    tgui.logWarn("This file is currently downloading, please try again later");
                }

            } catch (InterruptedException e) {
                tgui.logError("connection interrupted");
            } catch (FileNotFoundException e) {
                throw new RuntimeException(e);
            }
        }
    }

    public static synchronized void addMd5(String fileMd5){
        downloadingFileMd5.add(fileMd5);
    }
    public static synchronized void removeMd5(String fileMd5){
        downloadingFileMd5.remove(fileMd5);
    }
    public  static synchronized  void addDownloadTask(DownloadFile downloadFile){
        downloadingFile.add(downloadFile);
    }
    public static  synchronized void removeDownloadTask(DownloadFile downloadFile){
        downloadingFile.remove(downloadFile);
    }

    public void shutdown() throws InterruptedException {
        for(DownloadFile downloadFile : downloadingFile){
            downloadFile.shutdown();
            downloadFile.interrupt();
            downloadFile.join();
        }
    }
}
