package edu.buffalo.cse.cse486586.simpledht;

import android.content.ContentValues;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedList;

/**
 * Created by rachna on 3/28/16.
 */
public class MessageObj implements Serializable {

    String msgType= null;
    String senderNodeID = null;
    ArrayList<String> ListOfAliveNodes = new ArrayList<String>();
    private HashMap mContentValues = new HashMap();
    String successorForSendingMsg = null;
    String predecessorForSendingMsg = null;
    String queryFile = null;
    String whoSentReplyAll = null;

    public String getMsgType() {
        return msgType;
    }

    public void setMsgType(String msgType) {
        this.msgType = msgType;
    }

    public String getSenderNodeID() {
        return senderNodeID;
    }

    public void setSenderNodeID(String senderNodeID) {
        this.senderNodeID = senderNodeID;
    }

    public ArrayList<String> getListOfAliveNodes() {
        return ListOfAliveNodes;
    }

    public void setListOfAliveNodes(ArrayList<String> listOfAliveNodes) {
        ListOfAliveNodes = listOfAliveNodes;
    }

    public String getSuccessorForSendingMsg() {
        return successorForSendingMsg;
    }

    public void setSuccessorForSendingMsg(String successorForSendingMsg) {
        this.successorForSendingMsg = successorForSendingMsg;
    }

    public String getPredecessorForSendingMsg() {
        return predecessorForSendingMsg;
    }

    public void setPredecessorForSendingMsg(String predecessorForSendingMsg) {
        this.predecessorForSendingMsg = predecessorForSendingMsg;
    }

    public HashMap getmContentValues() {
        return mContentValues;
    }
    public void setmContentValues(String key, String value) {
        mContentValues.put("key",key);
        mContentValues.put("value",value);
    }
    public void setmContentValues(HashMap<String,String > map) {
        mContentValues = map;
    }

    public String getWhoSentReplyAll() {
        return whoSentReplyAll;
    }

    public void setWhoSentReplyAll(String whoSentReplyAll) {
        this.whoSentReplyAll = whoSentReplyAll;
    }

    public String getQueryFile() {
        return queryFile;
    }

    public void setQueryFile(String queryFile) {
        this.queryFile = queryFile;
    }

    @Override
    public String toString() {
        return "MessageObj{" +
                "msgType='" + msgType + '\'' +
                ", senderNodeID='" + senderNodeID + '\'' +
                ", ListOfAliveNodes=" + ListOfAliveNodes +
                ", mContentValues=" + mContentValues +
                ", successorForSendingMsg='" + successorForSendingMsg + '\'' +
                ", predecessorForSendingMsg='" + predecessorForSendingMsg + '\'' +
                ", queryFile='" + queryFile + '\'' +
                '}';
    }
}
