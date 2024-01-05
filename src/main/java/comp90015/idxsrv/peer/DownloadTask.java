package comp90015.idxsrv.peer;

import comp90015.idxsrv.filemgr.FileDescr;
import comp90015.idxsrv.filemgr.FileMgr;
import comp90015.idxsrv.message.LookupReply;

import java.io.IOException;
import java.security.NoSuchAlgorithmException;

public class DownloadTask {
    public String fileName;
    public LookupReply lookupReply;

    public FileDescr fileDescr;
    public FileMgr fileMgr;

    //each DownloadTask object describe a download job created by user
    public DownloadTask(String fileName, LookupReply lookupReply, FileDescr fileDescr) throws IOException, NoSuchAlgorithmException {
        this.fileName = fileName;
        this.lookupReply = lookupReply;
        this.fileDescr = fileDescr;
        this.fileMgr = new FileMgr(fileName, fileDescr);
    }

    public synchronized Boolean writeDownloadBlock(int blockIdx,byte[] bytes) throws IOException {
        return this.fileMgr.writeBlock(blockIdx,bytes);
    }
}
