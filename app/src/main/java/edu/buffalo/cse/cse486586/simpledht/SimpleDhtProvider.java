package edu.buffalo.cse.cse486586.simpledht;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.UnknownHostException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.Formatter;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;

import android.content.ContentProvider;
import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.MatrixCursor;
import android.net.Uri;
import android.os.AsyncTask;
import android.telephony.TelephonyManager;
import android.util.Log;

public class SimpleDhtProvider extends ContentProvider {

    static final String TAG = SimpleDhtProvider.class.getSimpleName();
    static final int SERVER_PORT = 10000;
    private static final String[] REMOTE_PORTS = {"11108", "11112", "11116", "11120", "11124"};
    private static final String[] EMULATOR_ID = {"5554", "5556", "5558", "5560", "5562"};

    static String myNodeId = null;
    static String myPort = null;
    static String myNodeIdGenHash = null;
    static String predecessor = null;
    static String sucessor= null;

    private static final int firstNode = 11108;
    private static final String JOIN_MESSAGE = "JOIN_MESSAGE";
    private static final String UPDATE_LIVE_NODE = "UPDATE_LIVE_NODE";
    private static final String INSERT_MSG = "INSERT_MSG";
    private static final String DELETE_MSG = "DELETE_MSG";
    private static final String QUERY_MSG= "QUERY_MSG";
    private static final String QUERY_REPLY= "QUERY_REPLY";
    private static final String QUERY_ALL_MSG= "QUERY_ALL_MSG";
    private static final String QUERY_ALL_REPLY= "QUERY_ALL_REPLY";
    private static final String DELETE_ALL_MSG = "DELETE_ALL_MSG";
    private static final String KEY_FIELD = "key";
    private static final String VALUE_FIELD = "value";
    private static ArrayList<String> ListOfAliveNodes = new ArrayList<String>();
    private HashMap<String,String> SenderGenHashMap = new HashMap();
    private HashMap<String, String> queryMap= new HashMap<String, String>();
    private HashMap<String,HashMap<String,String>> queryAllMap = new HashMap<String,HashMap<String,String>>();
    private Integer queryAllCount = 1;
    private Integer NoOfRepliesExpected = 0;

    public Uri getProviderUri() {
        return providerUri;
    }

    public Uri providerUri = null;
    /**
     * buildUri() demonstrates how to build a URI for a
     * @param authority
     * @return the URI
     */
    private Uri buildUri(String scheme, String authority) {
        Uri.Builder uriBuilder = new Uri.Builder();
        uriBuilder.authority(authority);
        uriBuilder.scheme(scheme);
        return uriBuilder.build();
    }


    @Override
    public int delete(Uri uri, String selection, String[] selectionArgs) {

        // selection can be a key directly, @ or *

        if( selection.equals("@") ) { // delete all key value pairs from local AVD

            Log.e(TAG, "Entered Delete File, delete all the files in my local : (selection ) :  "+selection);
            try {
                String files[] = getContext().fileList();
                File dir = getContext().getFilesDir();
                for(int i = 0 ; i<files.length ; i++) {
                    File file = new File(dir, files[i]);
                    boolean deleted = file.delete();
                    Log.e(TAG, "Deleting file :"+i+" filename : "+files[i] + " deleteflag : "+deleted);

                }

            } catch (Exception e) {
                Log.e(TAG, "File delete failed");
                e.printStackTrace();
            }
            // run 1 when there is only one active node
            if(ListOfAliveNodes.size() ==0 || (ListOfAliveNodes.size() ==1 && myNodeId.equals("5554"))) {
                Log.e(TAG, "Only 1 avd running in delete @ ");
                return 1;
            }

            String msgType = null;
            if(selectionArgs !=null) {
                msgType = selectionArgs[0];
            } else {
                Log.e(TAG, "Just Delete my files Dont fwd");
                return 1;
            }

            Log.v(TAG,"In delete(@) : Multiple AVDs running  ");
            Log.v(TAG,"In delete(@) : Got delete @ request when msgtype :   "+msgType);
            String originalQuerySender = selectionArgs[1];
            if(msgType.equals(DELETE_ALL_MSG) && !SenderGenHashMap.get(sucessor).equals(originalQuerySender)) {
                // deleted ur local files but fwd to ur successor too if its not the original sender of query *
                MessageObj msgObj = new MessageObj();
                msgObj.setMsgType(DELETE_ALL_MSG);
                msgObj.setSenderNodeID(originalQuerySender); // set the original sender ID
                String succ = SenderGenHashMap.get(sucessor);
                msgObj.setSuccessorForSendingMsg(succ); // node id not hashed node id
                Log.e(TAG, "Fwding To Client Task Delete ALL File Request (successor) "+succ);
                new ClientTask().executeOnExecutor(AsyncTask.SERIAL_EXECUTOR, msgObj);

            }

            Log.v(TAG," : In delete(@) Completed !! ");
            return 1;

        } else if (selection.equals("*")) { // return all key value pairs from all the live AVDs

            return 1; // returns the final matrixCursor

        } else {
            // when the selection gives the filename directly
            String fileName = selection;
            String fileNameHash = null;

            try {
                fileNameHash = genHash(fileName);

            } catch(NoSuchAlgorithmException e ) {
                Log.e(TAG, "In Delete() For filename : No Such Algorithm exception caught ");
                e.printStackTrace();
            }

            Log.e(TAG, "Entered Delete File, check if the file is stored in my avd ");


            // case when there is only one node , the file shud  be in my avd only
            if(ListOfAliveNodes.size() ==0 || (ListOfAliveNodes.size() ==1 && myNodeId.equals("5554"))) {
                   try {
                    File dir = getContext().getFilesDir();
                    File file = new File(dir, fileName);
                    boolean deleted = file.delete();
                    Log.v(TAG, "file deleted successfully..."+fileName + "deleteFlag : "+deleted);
                    return 1;

                } catch (Exception e) {
                    Log.e(TAG, "File delete failed");
                       e.printStackTrace();
                }


            }

            if(((fileNameHash.compareTo(predecessor)>0) && (fileNameHash.compareTo(myNodeIdGenHash)<=0)) || ((fileNameHash.compareTo(predecessor)>0) && ListOfAliveNodes.indexOf(myNodeIdGenHash)==0) || ((fileNameHash.compareTo(myNodeIdGenHash)<=0) && ListOfAliveNodes.indexOf(myNodeIdGenHash)==0)) {
                Log.e(TAG, "Multiple Avds but the file is stored with me, Delete in progress...");
                try {
                    File dir = getContext().getFilesDir();
                    File file = new File(dir, fileName);
                    boolean deleted = file.delete();
                    Log.v(TAG, "Multiple Avds case : file deleted successfully..."+fileName+ "deleteFlag : "+deleted);
                    return 1;

                } catch (Exception e) {
                    Log.e(TAG, "File delete failed");
                    e.printStackTrace();
                }
            } else {
                Log.e(TAG, "Oops..file not with me..fwding request... ");
                // pass the key value pair to the successor node
                MessageObj msgObj = new MessageObj();
                msgObj.setMsgType(DELETE_MSG);
                msgObj.setQueryFile(fileName);
                msgObj.setSenderNodeID(myNodeId); // node id not hashed node id
                String succ = SenderGenHashMap.get(sucessor);
                msgObj.setSuccessorForSendingMsg(succ); // node id not hashed node id
                Log.e(TAG, "Fwding To Client Task Delete File Request DELETE_MSG to (successor) "+succ);
                new ClientTask().executeOnExecutor(AsyncTask.SERIAL_EXECUTOR, msgObj);
            }

            return 1;
        }
    }

    @Override
    public String getType(Uri uri) {
        // TODO Auto-generated method stub
        return null;
    }

    public void createSenderGenHashMap () {
        for ( int i = 0 ; i<EMULATOR_ID.length ; i++) {

            try {
                String genHashVal = genHash(EMULATOR_ID[i]);
                SenderGenHashMap.put(genHashVal, EMULATOR_ID[i]);

            } catch(NoSuchAlgorithmException e ) {
                Log.e(TAG, "In Insert() : No Such Algorithm exception caught ");
                e.printStackTrace();
            }
        }
    }

    @Override
    public Uri insert(Uri uri, ContentValues values) {
        String key = (String)values.get("key");
        String value = (String)values.get("value");
        String hashOfKey = null;
        Log.e(TAG, " Entered Insert() ....  key: "+key+" value: "+value);

        // Now you have the key and value - hash the key to find the correct location of key
        try {
           hashOfKey  = genHash(key);

        } catch(NoSuchAlgorithmException e ) {
            Log.e(TAG, "In Insert() : No Such Algorithm exception caught ");
            e.printStackTrace();
        }

        Log.e(TAG, "In Insert() : ListOfAliveNodes "+ListOfAliveNodes.size()+"list : "+ListOfAliveNodes);
        // case when there is only one node
        if(ListOfAliveNodes.size() ==0 || (ListOfAliveNodes.size() ==1 && myNodeId.equals("5554"))) {
            // i need to store this key value pair in my local file system
            FileOutputStream outputStream;
            try {
                outputStream = getContext().openFileOutput(key, Context.MODE_PRIVATE); // check the value of key & Value to be inserted is correct
                outputStream.write(value.getBytes());
                outputStream.flush();
                outputStream.close();


            } catch (Exception e) {
                Log.e(TAG, "File write failed");
            }
            Log.v("Provider msg inserted", values.toString());
            return uri;

        }

        if(((hashOfKey.compareTo(predecessor)>0) && (hashOfKey.compareTo(myNodeIdGenHash)<=0)) || ((hashOfKey.compareTo(predecessor)>0) && ListOfAliveNodes.indexOf(myNodeIdGenHash)==0) || ((hashOfKey.compareTo(myNodeIdGenHash)<=0) && ListOfAliveNodes.indexOf(myNodeIdGenHash)==0)) {
            // i need to store this key value pair in my local file system
            FileOutputStream outputStream;
            try {
                outputStream = getContext().openFileOutput(key, Context.MODE_PRIVATE); // check the value of key & Value to be inserted is correct
                outputStream.write(value.getBytes());
                outputStream.flush();
                outputStream.close();


            } catch (Exception e) {
                Log.e(TAG, "File write failed");
            }
            Log.v("Provider msg inserted", values.toString());
            return uri;
        } else {
            // pass the key value pair to the successor node
            MessageObj msgObj = new MessageObj();
            msgObj.setMsgType(INSERT_MSG);
            msgObj.setmContentValues(key, value);
            msgObj.setSenderNodeID(myNodeId); // node id not hashed node id
            String succ = SenderGenHashMap.get(sucessor);
            String predec = SenderGenHashMap.get(predecessor);


            Log.e(TAG, "Fwding insert Request INSERT_MSG : succ : "+succ + "predec : "+predec);
            msgObj.setSuccessorForSendingMsg(succ); // node id not hashed node id
            msgObj.setPredecessorForSendingMsg(predec); // node id not hashed node id


            new ClientTask().executeOnExecutor(AsyncTask.SERIAL_EXECUTOR, msgObj);
            Log.e(TAG, "INSERT send to Client task with msg"+msgObj.toString());
        }

        return uri;

    }

    @Override
    public boolean onCreate() {
        TelephonyManager tel = (TelephonyManager) this.getContext().getSystemService(Context.TELEPHONY_SERVICE);
        String portStr = tel.getLine1Number().substring(tel.getLine1Number().length() - 4);

        providerUri = buildUri("content", "edu.buffalo.cse.cse486586.simpledht.provider");

        myNodeId = portStr; // eg 5554
        myPort = String.valueOf((Integer.parseInt(portStr) * 2)); ///eg 11108
        try {
            myNodeIdGenHash = genHash(portStr);
        } catch (NoSuchAlgorithmException e) {
            Log.e(TAG, "On create : No Such Algorithm exception caught ");
            e.printStackTrace();
        }

        try {
            ServerSocket serverSocket = new ServerSocket(SERVER_PORT);
            new ServerTask().executeOnExecutor(AsyncTask.THREAD_POOL_EXECUTOR, serverSocket);
        } catch (IOException e) {

            Log.e(TAG, "Can't create a ServerSocket");
            return  false;
        }

        createSenderGenHashMap();


        if(!myNodeId.equals("5554")){ //Firse Node to Join
            Log.d(TAG, "JOIN request from : Node id" + myNodeId + "myPort :"+myPort);
            MessageObj msg = new MessageObj();
            msg.setMsgType(JOIN_MESSAGE);
            msg.setSenderNodeID(myNodeId);
            new ClientTask().executeOnExecutor(AsyncTask.SERIAL_EXECUTOR, msg);
        }
        else{
            Log.d(TAG, "I am first Node : Node id" + myNodeId + "myPort :" + myPort + "myNodeIdgenHash : " + myNodeIdGenHash);
            ListOfAliveNodes.add(myNodeIdGenHash);
            int myNodeIndex = ListOfAliveNodes.indexOf(myNodeIdGenHash);
            Log.d(TAG, "My Node Index on create : "+myNodeIndex);
            predecessor = myNodeIdGenHash;
            sucessor = myNodeIdGenHash;
        }

        return true;
    }

    @Override
    public Cursor query(Uri uri, String[] projection, String selection, String[] selectionArgs,
            String sortOrder) {
         // selection can be a key directly, @ or *

        if( selection.equals("@") ) { // return all key value pairs from local AVD
            String fileName = null;
            FileInputStream fis;
            MatrixCursor mc = new MatrixCursor(new String[] { "key", "value"});
            StringBuffer fileContent = new StringBuffer("");
            int n=0;
            String fileNamesList[] = getContext().fileList();
            int noOfFiles = fileNamesList.length;
            for ( int counter = 0 ; counter < noOfFiles ; counter++) {
                fileName = fileNamesList[counter];
                 n=0;
                fileContent = new StringBuffer("");

                try {
                    fis = getContext().openFileInput(fileName);
                    byte[] buffer = new byte[1024];
                    while ((n=fis.read(buffer)) != -1)
                    {
                        fileContent.append(new String(buffer, 0, n));
                    }
                } catch (FileNotFoundException e) {
                    Log.e(TAG,"File Not Found To Read for iteration : "+counter);
                } catch (IOException e) {
                    Log.e(TAG, "Error Reading File for iteration : "+counter);
                }

                //read one file now store its corresponding values as a new row in matrix cursor
                mc.addRow(new String[] {fileName, fileContent.toString() });
                Log.v(counter+ " : In query(Sel) : ",selection);
                Log.v(counter+" : In query...", "Filename : "+fileName+" & File Content : "+fileContent.toString());

            }

            // run 1 when there is only one active node
            if(ListOfAliveNodes.size() ==0 || (ListOfAliveNodes.size() ==1 && myNodeId.equals("5554"))) {
                return mc;
            }

            String msgType = null;
            String senderNodeId = null;

            if(projection!=null) {
                msgType = projection[0];
                senderNodeId = projection[1];
                boolean c=false,d=false,f = false;
                c = senderNodeId!=null;
                d = msgType.equals(QUERY_ALL_MSG);
                f = SenderGenHashMap.get(sucessor)!=senderNodeId;
                Log.v(TAG, "SenderGenHashMap.get(sucessor)" +SenderGenHashMap.get(sucessor) + "senderNodeId : "+senderNodeId );


                Log.v(TAG, "senderNodeId!=null" +c );
                Log.v(TAG, "msgType.equals(QUERY_ALL_MSG)" + d );
                Log.v(TAG, "SenderGenHashMap.get(sucessor)!=senderNodeId" + f );
            }


            if(senderNodeId!=null && msgType.equals(QUERY_ALL_MSG) && !SenderGenHashMap.get(sucessor).equals(senderNodeId)) {
               //I received @ request as a part of * query request from other avd
                // so return ur cursor and fwd this request to ur successors
                //handle the case the original sender shudnt be fwded twice the same query msg

                MessageObj msgObj = new MessageObj();
                msgObj.setMsgType(QUERY_ALL_MSG);
                msgObj.setWhoSentReplyAll(myNodeId);
                msgObj.setSenderNodeID(senderNodeId);
                msgObj.setSuccessorForSendingMsg(SenderGenHashMap.get(sucessor));
                msgObj.setQueryFile("@");
                new ClientTask().executeOnExecutor(AsyncTask.SERIAL_EXECUTOR, msgObj);
                Log.e(TAG, "QUERY_ALL_MSG send to Client task with msg" + msgObj.toString());


            }

            Log.v(TAG," : In query(@) replying with cursor count : "+mc.getCount());
            return mc;

        } else if (selection.equals("*")) { // return all key value pairs from all the live AVDs
            String fileName = null;
            FileInputStream fis;
            MatrixCursor mc = new MatrixCursor(new String[] { "key", "value"});
            StringBuffer fileContent;
            int n=0;
            String fileNamesList[] = getContext().fileList();
            int noOfFiles = fileNamesList.length;
            Log.e(TAG,"Only original * query reciever  comes here : : noOffiles I have : "+noOfFiles);

            for ( int counter = 0 ; counter < noOfFiles ; counter++) {
                fileName = fileNamesList[counter];
                n=0;
                fileContent = new StringBuffer("");

                try {
                    fis = getContext().openFileInput(fileName);
                    byte[] buffer = new byte[1024];
                    while ((n=fis.read(buffer)) != -1)
                    {
                        fileContent.append(new String(buffer, 0, n));
                    }
                } catch (FileNotFoundException e) {
                    Log.e(TAG,"File Not Found To Read for iteration : "+counter);
                } catch (IOException e) {
                    Log.e(TAG, "Error Reading File for iteration : "+counter);
                }

                //read one file now store its corresponding values as a new row in matrix cursor
                mc.addRow(new String[] {fileName, fileContent.toString() });
                Log.v(counter+ " : In query(Sel) : ",selection);
                Log.v(counter+" : In query...", "Filename : "+fileName+" & File Content : "+fileContent.toString());

            }
            // run 1 when there is only one active node
            if(ListOfAliveNodes.size() ==0 || (ListOfAliveNodes.size() ==1 && myNodeId.equals("5554"))) {
                Log.e(TAG,"Only when one node with select * is true ");
                return mc;
            }


            //Now you got ur local files, but wait till u get the files stored in all other alive AVD nodes.
            // Send a request to all other avds except urs and wait for their responses, then concatenate all the responses into a matrix and return

            //forward the query request to the successor
            String senderNodeId = null;
            if(projection!=null) {
                senderNodeId = projection[0];
            }


            if(senderNodeId == null ) {
                //means I am the first node to get query for all
                for(int i=0;i<EMULATOR_ID.length;i++) {
                    if(myNodeId.equals(EMULATOR_ID[i])) {
                        continue;
                    }
                    queryAllMap.put(EMULATOR_ID[i],null);
                }
            }

            MessageObj msgObj = new MessageObj();
            msgObj.setMsgType(QUERY_ALL_MSG);
            if(senderNodeId  == null) {
                msgObj.setSenderNodeID(myNodeId);
            } else {
                msgObj.setSenderNodeID(senderNodeId);
            }
            //msgObj.setWhoSentReplyAll(myNodeId);
            msgObj.setSuccessorForSendingMsg(SenderGenHashMap.get(sucessor));
            msgObj.setQueryFile("@");
            new ClientTask().executeOnExecutor(AsyncTask.SERIAL_EXECUTOR, msgObj);
            Log.e(TAG, "QUERY_ALL_MSG send to Client task with msg" + msgObj.toString());

            NoOfRepliesExpected = ListOfAliveNodes.size();

            if(senderNodeId==null) {

                while(queryAllCount < NoOfRepliesExpected ){
                    // only original sender waits to receive the reply from all the nodes to return all their local file content
                    //  Log.e(TAG, "Query FileName : Waiting Till File Content Not Received ........................");
                }

                Log.e(TAG, "While loop exited : queryAllCount : " + queryAllCount + "NoOfRepliesExpected :" +NoOfRepliesExpected );

                Iterator it = queryAllMap.entrySet().iterator();
                while (it.hasNext()) {
                    Map.Entry pair = (Map.Entry)it.next();
                    String emulatorID = (String)pair.getKey();
                    HashMap fileMap = (HashMap)pair.getValue();
                        if(fileMap!=null) {
                            Iterator it2 = fileMap.entrySet().iterator();
                            int counter = 0;
                            while(it2.hasNext()) {
                                Map.Entry fileMapValues = (Map.Entry)it2.next();
                                String fileKey = (String)fileMapValues.getKey();
                                String fileValue = (String)fileMapValues.getValue();
                                mc.addRow(new String[]{fileKey, fileValue});
                                Log.v(TAG,counter+ " : In query(All while loop) : "+selection);
                                Log.v(TAG,counter+" : In query(All while loop)...Filename : "+fileKey+" & File Content : "+fileValue);

                            }
                        }
                }
            }
            return mc; // returns the final matrixCursor

        } else {
            // when the selection gives the filename directly
            String fileName = selection;
            String fileNameHash = null;

            try {
                fileNameHash = genHash(fileName);

            } catch(NoSuchAlgorithmException e ) {
                Log.e(TAG, "In Insert() : No Such Algorithm exception caught ");
                e.printStackTrace();
            }

            FileInputStream fis;
            StringBuffer fileContent = new StringBuffer("");
            int n=0;

            //case with only one AVD Running ( run 1)
            if(ListOfAliveNodes.size() ==0 || (ListOfAliveNodes.size() ==1 && myNodeId.equals("5554"))) {
                try {
                    fis = getContext().openFileInput(fileName);
                    byte[] buffer = new byte[1024];
                    while ((n=fis.read(buffer)) != -1)
                    {
                        fileContent.append(new String(buffer, 0, n));
                    }
                } catch (FileNotFoundException e) {
                    Log.e(TAG,"File Not Found To Read");

                } catch (IOException e) {
                    Log.e(TAG, "Error Reading File");
                }

                MatrixCursor mc = new MatrixCursor(new String[] { "key", "value"});
                mc.addRow(new String[] {fileName, fileContent.toString() });
                Log.v("In query...Selection : ",selection);
                Log.v("In query...", "Filename : "+fileName+" & File Content : "+fileContent.toString());
                return mc;
            }



            if(((fileNameHash.compareTo(predecessor)>0) && (fileNameHash.compareTo(myNodeIdGenHash)<=0)) || ((fileNameHash.compareTo(predecessor)>0) && ListOfAliveNodes.indexOf(myNodeIdGenHash)==0) || ((fileNameHash.compareTo(myNodeIdGenHash)<=0) && ListOfAliveNodes.indexOf(myNodeIdGenHash)==0)) {
                //this case wud have been stored in my internal file storage
                try {
                    fis = getContext().openFileInput(fileName);
                    byte[] buffer = new byte[1024];
                    while ((n=fis.read(buffer)) != -1)
                    {
                        fileContent.append(new String(buffer, 0, n));
                    }
                } catch (FileNotFoundException e) {
                    Log.e(TAG,"File Not Found To Read");

                } catch (IOException e) {
                    Log.e(TAG, "Error Reading File");
                }

            } else {
                //forward the query request to the successor
                String senderNodeId = null;
                if(projection!=null) {
                    senderNodeId = projection[0];
                }


                if(senderNodeId == null ) {
                    queryMap.put(fileName, null);
                }

                MessageObj msgObj = new MessageObj();
                msgObj.setMsgType(QUERY_MSG);
                if(senderNodeId == null) {
                    msgObj.setSenderNodeID(myNodeId);
                } else {
                    msgObj.setSenderNodeID(senderNodeId);
                }

                msgObj.setSuccessorForSendingMsg(SenderGenHashMap.get(sucessor));
                msgObj.setQueryFile(fileName);
                new ClientTask().executeOnExecutor(AsyncTask.SERIAL_EXECUTOR, msgObj);
                Log.e(TAG, "QUERY_MSG send to Client task with msg" + msgObj.toString());

                if(senderNodeId==null) {
                    while(queryMap.get(fileName)==null){
                        // only original sender waits to receive the reply from any node with the file content
                      //  Log.e(TAG, "Query FileName : Waiting Till File Content Not Received ........................");
                    }

                    fileContent.append(queryMap.get(fileName));
                }
            }

            MatrixCursor mc = new MatrixCursor(new String[] { "key", "value"});
            mc.addRow(new String[] {fileName, fileContent.toString() });
            Log.v("In query...Selection : ",selection);
            Log.v("In query...", "Filename : "+fileName+" & File Content : "+fileContent.toString());
            return mc;

        }

    }

    @Override
    public int update(Uri uri, ContentValues values, String selection, String[] selectionArgs) {
        // TODO Auto-generated method stub
        return 0;
    }

    /***
     * ServerTask is an AsyncTask that should handle incoming messages. It is created by
     * ServerTask.executeOnExecutor() call in SimpleMessengerActivity.
     * <p/>
     * Please make sure you understand how AsyncTask works by reading
     * http://developer.android.com/reference/android/os/AsyncTask.html
     *
     * @author stevko
     */
    private class ServerTask extends AsyncTask<ServerSocket, String, Void> {

        @Override
        protected Void doInBackground(ServerSocket... sockets) {
            ServerSocket serverSocket = sockets[0];

            while (true) {
                Socket socket = null;
                ObjectInputStream ois = null;

                MessageObj messageRecvd;
                try {
                    socket = serverSocket.accept();
                    ois = new ObjectInputStream(socket.getInputStream());
                    messageRecvd = (MessageObj) ois.readObject();
                    String msgRecvdType = messageRecvd.getMsgType();
                    ois.close();
                    socket.close();

                    if (msgRecvdType.equalsIgnoreCase(JOIN_MESSAGE)) {
                        // Msg Type... SenderNodeID
                        String[] joinMessage = new String[2];
                        joinMessage[0] = msgRecvdType;
                        joinMessage[1] = messageRecvd.getSenderNodeID();
                        Log.e(TAG, "Executed JOIN MESSAGE 1 Block in ServerTask......... : msgType" + joinMessage[0]);
                        Log.e(TAG, "Executed JOIN MESSAGE 2 Block in ServerTask......... SenderNodeId: " + joinMessage[1]);
                        publishProgress(joinMessage);

                    } else if (msgRecvdType.equalsIgnoreCase(UPDATE_LIVE_NODE)) {
                        String[] updateNodeMsg = new String[2];
                        ArrayList<String> AliveNodes = new ArrayList<String>();

                        AliveNodes = messageRecvd.getListOfAliveNodes();
                        ListOfAliveNodes = AliveNodes;

                        updateNodeMsg[0] = msgRecvdType;
                        Log.e(TAG, "Executed UPDATE_LIVE NODE Block in ServerTask.........MsgType : " + updateNodeMsg[0]);
                        publishProgress(updateNodeMsg);
                    } else if (msgRecvdType.equalsIgnoreCase(INSERT_MSG)) {
                        String[] insertMsg = new String[4];
                        insertMsg[0] = msgRecvdType;
                        insertMsg[1] = messageRecvd.getSenderNodeID();
                        insertMsg[2] = (String)messageRecvd.getmContentValues().get(KEY_FIELD);
                        insertMsg[3] = (String)messageRecvd.getmContentValues().get(VALUE_FIELD);

                        Log.e(TAG, "Executed INSERT_MSG NODE Block in ServerTask...MsgType : " + insertMsg[0] + "senderNodeId : "+insertMsg[1]);
                        Log.e(TAG, "Executed INSERT_MSG NODE Block in ServerTask...Key : " + insertMsg[2]+" value : "+insertMsg[3]);
                        publishProgress(insertMsg);
                    } else if (msgRecvdType.equalsIgnoreCase(QUERY_MSG)) {
                        String[] queryMsg = new String[3];
                        queryMsg[0] = msgRecvdType;
                        queryMsg[1] = messageRecvd.getSenderNodeID();
                        queryMsg[2] = messageRecvd.getQueryFile();

                        Log.e(TAG, "Executed QUERY_MSG NODE Block in ServerTask...MsgType : " + queryMsg[0] + "senderNodeId : "+queryMsg[1]);
                        Log.e(TAG, "Executed QUERY_MSG NODE Block in ServerTask...QueryFile : " + queryMsg[2]);
                        publishProgress(queryMsg);
                    } else if (msgRecvdType.equalsIgnoreCase(QUERY_REPLY)) {
                        String[] queryReplyMsg = new String[3];
                        queryReplyMsg[0] = msgRecvdType;
                        queryReplyMsg[1] = (String)messageRecvd.getmContentValues().get(KEY_FIELD);
                        queryReplyMsg[2] = (String)messageRecvd.getmContentValues().get(VALUE_FIELD);

                        Log.e(TAG, "Executed QUERY_REPLY NODE Block in ServerTask...MsgType : " + queryReplyMsg[0] + "QueryFile : "+queryReplyMsg[1]);
                        Log.e(TAG, "Executed QUERY_REPLY NODE Block in ServerTask...Query File Content : " + queryReplyMsg[2]);
                        publishProgress(queryReplyMsg);
                    }else if (msgRecvdType.equalsIgnoreCase(QUERY_ALL_MSG)) {
                        // 0 - msgType, 1 - senderNodeID , 2- selection Param
                        String[] queryAllMsg = new String[3];
                        queryAllMsg[0] = msgRecvdType;
                        queryAllMsg[1] = messageRecvd.getSenderNodeID();;
                        queryAllMsg[2] = messageRecvd.getQueryFile();

                        Log.e(TAG, "Executed QUERY_ALL_MSG NODE Block in ServerTask...MsgType : " + queryAllMsg[0] + "Selection Param : "+queryAllMsg[2]);
                        Log.e(TAG, "Executed QUERY_ALL_MSG NODE Block in ServerTask...Original Sender : " + queryAllMsg[1]);
                        publishProgress(queryAllMsg);
                    } else if (msgRecvdType.equalsIgnoreCase(QUERY_ALL_REPLY)) {

                        HashMap<String,String> map = (HashMap)messageRecvd.getmContentValues();
                        String whoSentReplyAllMsg = messageRecvd.getWhoSentReplyAll();

                        if(map==null) {
                            //this avd who replied doesnt have any keys stored so dont count it
                           // NoOfRepliesExpected--;
                            Log.e(TAG, "IF map == null QUERY_ALL_REPLY NODE Block in ServerTask...I am here : " + NoOfRepliesExpected);

                        }

                        queryAllMap.put(whoSentReplyAllMsg,map);
                        queryAllCount++;
                        Log.e(TAG, "Executed QUERY_ALL_REPLY NODE Block in ServerTask...MsgType : " + msgRecvdType);
                        Log.e(TAG, "Executed QUERY_ALL_REPLY NODE Block in ServerTask...whoSentReplyAllMsg : " + whoSentReplyAllMsg);
                        Log.e(TAG, "Executed QUERY_ALL_REPLY NODE Block in ServerTask...map : " + map);
                        Log.e(TAG, "Executed QUERY_ALL_REPLY NODE Block in ServerTask...NoOfRepliesExpected : " + NoOfRepliesExpected);

                    }else if (msgRecvdType.equalsIgnoreCase(DELETE_MSG)) {
                        //msgtype..senderNodeId..filetobedeleted
                        String[] deleteMsg = new String[3];
                        deleteMsg[0] = msgRecvdType;
                        deleteMsg[1] = messageRecvd.getSenderNodeID();
                        deleteMsg[2] = messageRecvd.getQueryFile();
                        Log.e(TAG, "In DELETE_MSG NODE Block (ServerTask) ...MsgType : " + deleteMsg[0] + "Original senderNodeId : "+deleteMsg[1] +"QueryFile : " + deleteMsg[2]);
                        publishProgress(deleteMsg);
                    } else if (msgRecvdType.equalsIgnoreCase(DELETE_ALL_MSG)) {
                        //msgtype..senderNodeId
                        String[] deleteAllMsg = new String[2];
                        deleteAllMsg[0] = msgRecvdType;
                        deleteAllMsg[1] = messageRecvd.getSenderNodeID();
                        Log.e(TAG, "In DELETE_ALL_MSG NODE Block (ServerTask) ...MsgType : " + deleteAllMsg[0] + "Original senderNodeId : "+deleteAllMsg[1]);
                        publishProgress(deleteAllMsg);
                    }



                } catch (IOException e) {
                    e.printStackTrace();

                } catch (ClassNotFoundException e) {
                    e.printStackTrace();
                }
            }
        }
        protected void onProgressUpdate(String... strings) {
            String msgTypeReceived = strings[0];


            if (msgTypeReceived.equalsIgnoreCase(JOIN_MESSAGE)) {
                // that means you are the firstNode = 5554
                String senderNodeID = strings[1];
                String senderNodeIDGenHash= null;
                try {
                    senderNodeIDGenHash = genHash(senderNodeID);
                } catch (NoSuchAlgorithmException e) {
                    Log.e(TAG, "No Such Algorithm exception caught ");
                    e.printStackTrace();
                }

                ListOfAliveNodes.add(senderNodeIDGenHash);

                // Sort the list of Gen Hashed Node Ids before sending
                Collections.sort(ListOfAliveNodes, new Comparator<String>() {
                    @Override
                    public int compare(String s1, String s2) {
                        return s1.compareToIgnoreCase(s2);
                    }
                });
                //Now you need to broadcast this list to other alive nodes including u

                MessageObj msgObj = new MessageObj();
                msgObj.setMsgType(UPDATE_LIVE_NODE);
                msgObj.setListOfAliveNodes(ListOfAliveNodes);
                new ClientTask().executeOnExecutor(AsyncTask.SERIAL_EXECUTOR, msgObj);
                Log.e(TAG, "UPDATE_LIVE_NODE send to Client task with msg"+msgObj.toString());

            } else if (msgTypeReceived.equalsIgnoreCase(UPDATE_LIVE_NODE)) {
                Log.e(TAG, "UPDATE_LIVE_NODE :ListOfAliveNodes : " + ListOfAliveNodes);


                // find the sucessor and predecessor
                Log.e(TAG, "UPDATE_LIVE_NODE:MyNodeIdGenHash :"+myNodeIdGenHash);
                int myNodeIndex = ListOfAliveNodes.indexOf(myNodeIdGenHash);
                int predecessorIndex = myNodeIndex-1;
                int successorIndex = myNodeIndex + 1;
                Log.e(TAG, "UPDATE_LIVE_NODE:MyNodeIndex : "+myNodeIndex+" predecessorIndex : "+predecessorIndex + "successorIndex : "+successorIndex);

                if(myNodeIndex == 0){
                    predecessorIndex = ListOfAliveNodes.size()-1; // for first Node , Last Node becomes the predecessor
                }
                if (successorIndex >ListOfAliveNodes.size()-1) {
                    successorIndex = 0; // for last node , first node becomes the successor
                }

                Log.e(TAG, "MyNodeIndex : "+myNodeIndex+" predecessorIndex : "+predecessorIndex + " successorIndex : "+successorIndex);


                predecessor = ListOfAliveNodes.get(predecessorIndex);
                sucessor = ListOfAliveNodes.get(successorIndex);
                Log.e(TAG, "Final Predecessor : "+predecessor + " Final Successor :" + sucessor);

                Log.e(TAG, "END OF CODE :)");

            } else if (msgTypeReceived.equalsIgnoreCase(INSERT_MSG)) {
                // means i have been forwarded a insert request with key,value from my predecessor node
                //call insert and check if the key belongs to my node or has to be again passed fwd
               ContentValues mContentValues = new ContentValues();
                String keyToInsert   = strings[2];
                String valueToInsert = strings[3];
                mContentValues.put(KEY_FIELD,keyToInsert);
                mContentValues.put(VALUE_FIELD,valueToInsert);

                insert(null,mContentValues );
                Log.e(TAG, "In Insert_MSG block progressUpdate");

            } else if (msgTypeReceived.equalsIgnoreCase(QUERY_MSG)) {
                // means i have been forwarded a query request with queryFile from my predecessor node
                //call query and check if the file belongs to my node or has to be again passed fwd
                String queryFile   = strings[2];
                String senderNodeId = strings[1];
                int keyIndex = 0, valueIndex=0;

                Cursor resultCursor = query(providerUri, new String[]{senderNodeId}, queryFile, null, null);
                if (resultCursor == null || resultCursor.getCount()==0) {
                    Log.e(TAG, "Result null");

                } else {
                    keyIndex = resultCursor.getColumnIndex(KEY_FIELD);
                    valueIndex = resultCursor.getColumnIndex(VALUE_FIELD);

                    if (keyIndex == -1 || valueIndex == -1) {
                        Log.e(TAG, "Wrong columns");
                        resultCursor.close();
                    }

                    resultCursor.moveToFirst();

                    if (!(resultCursor.isFirst() && resultCursor.isLast())) {
                        Log.e(TAG, "Wrong number of rows");
                        resultCursor.close();
                    }



                    String returnFileName = resultCursor.getString(keyIndex);
                    String returnFileContent = resultCursor.getString(valueIndex);
                    Log.e(TAG, "QUERY_MSG : returnFileName"+returnFileName + " returnFileContent: "+returnFileContent);
                    if(returnFileContent!=null && returnFileContent!="") {
                        MessageObj msgObj = new MessageObj();
                        msgObj.setMsgType(QUERY_REPLY);
                        msgObj.setmContentValues(returnFileName, returnFileContent);
                        msgObj.setSuccessorForSendingMsg(senderNodeId); // sending back to the original sender of the query
                        new ClientTask().executeOnExecutor(AsyncTask.SERIAL_EXECUTOR, msgObj);
                        Log.e(TAG, "QUERY_REPLY send to Client task with msg"+msgObj.toString());
                    }


                    Log.e(TAG, "QUERY_REPLY block progressUpdate Ends");
                }

            }else if (msgTypeReceived.equalsIgnoreCase(QUERY_REPLY)) {
                // means i have been replied with key,value for my original query
                // put the values in the hashmap to exit from the waiting while loop
                String file   = strings[1];
                String fileContent = strings[2];
                queryMap.put(file, fileContent);
                Log.e(TAG, "In QUERY_REPLY block progressUpdate 1 :");
                Log.e(TAG, "In QUERY_REPLY block progressUpdate 2 : File : "+file + "fileContent : " + fileContent);


            }  else if (msgTypeReceived.equalsIgnoreCase(QUERY_ALL_MSG)) {
                // means i have been forwarded a query request with @ from my predecessor node
                //call query and retrieve all the files stored in ur local avd & return as a HashMap of <File,FileContent> to the original sender
                // 0 - msgType, 1 - senderNodeID , 2- selection Param
                String senderNodeId = strings[1];
                String selectionParam   = strings[2];

                int keyIndex = 0, valueIndex=0;

                Cursor resultCursor = query(providerUri, new String[]{QUERY_ALL_MSG,senderNodeId}, selectionParam, null, null);
                if (resultCursor == null || resultCursor.getCount()<=0) {
                    Log.e(TAG, "Result null null null nulll nulll");
                    MessageObj msgObj = new MessageObj();
                    msgObj.setMsgType(QUERY_ALL_REPLY);
                    msgObj.setmContentValues(null);
                    msgObj.setSuccessorForSendingMsg(senderNodeId); // sending back to the original sender of the query
                    msgObj.setWhoSentReplyAll(myNodeId);
                    new ClientTask().executeOnExecutor(AsyncTask.SERIAL_EXECUTOR, msgObj);
                    Log.e(TAG, "QUERY_ALL_REPLY send to Client task with msg"+msgObj.toString());
                    Log.e(TAG, "QUERY_ALL_REPLY block progressUpdate Ends");



                } else {
                    keyIndex = resultCursor.getColumnIndex(KEY_FIELD);
                    valueIndex = resultCursor.getColumnIndex(VALUE_FIELD);

                    if (keyIndex == -1 || valueIndex == -1) {
                        Log.e(TAG, "Wrong columns");
                        resultCursor.close();
                    }

                    resultCursor.moveToFirst();

                    String returnFileName = null;
                    String returnFileContent = null;
                    int i = 0 ;
                    HashMap<String,String> fileMap = new HashMap<String,String>();

                    Log.e(TAG, "QUERY_ALL_MSG : Iterating Over the result cursor");

                    // handle the first row of cursor explicitly , then move to Next rows....
                    returnFileName = resultCursor.getString(keyIndex);
                    returnFileContent = resultCursor.getString(valueIndex);
                    Log.e(TAG, "QUERY_ALL_MSG :Iteration: "+i+" returnFileName"+returnFileName + " returnFileContent: "+returnFileContent);
                    if(returnFileContent!=null && returnFileContent!="") {
                        fileMap.put(returnFileName,returnFileContent);
                    }

                    while(resultCursor.moveToNext()) {
                        i++;
                        returnFileName = resultCursor.getString(keyIndex);
                        returnFileContent = resultCursor.getString(valueIndex);
                        Log.e(TAG, "QUERY_ALL_MSG :Iteration: "+i+" returnFileName"+returnFileName + " returnFileContent: "+returnFileContent);
                        if(returnFileContent!=null && returnFileContent!="") {
                            fileMap.put(returnFileName,returnFileContent);
                        }
                    }
                        MessageObj msgObj = new MessageObj();
                        msgObj.setMsgType(QUERY_ALL_REPLY);
                        msgObj.setmContentValues(fileMap);
                        msgObj.setWhoSentReplyAll(myNodeId);
                        msgObj.setSuccessorForSendingMsg(senderNodeId); // sending back to the original sender of the query
                        new ClientTask().executeOnExecutor(AsyncTask.SERIAL_EXECUTOR, msgObj);
                        Log.e(TAG, "QUERY_ALL_REPLY send to Client task with msg"+msgObj.toString());
                        Log.e(TAG, "QUERY_ALL_REPLY block progressUpdate Ends");
                }

            }else if (msgTypeReceived.equalsIgnoreCase(DELETE_MSG)) {
                // means i have been forwarded a query request with queryFile from my predecessor node
                //call query and check if the file belongs to my node or has to be again passed fwd
                String queryFile   = strings[2];
                String senderNodeId = strings[1];
                delete(providerUri, queryFile,new String[]{DELETE_MSG, senderNodeId});

            }else if (msgTypeReceived.equalsIgnoreCase(DELETE_ALL_MSG)) {
                // means i have been forwarded a query request with @ as part of * query from my predecessor node
                //delete ur local files and  pass @ request to succ
                String senderNodeId = strings[1];
                delete(providerUri, "@",new String[]{DELETE_ALL_MSG, senderNodeId});

            }

        }
    }



    /***
     * ClientTask is an AsyncTask that should send a string over the network.
     * It is created by ClientTask.executeOnExecutor() call whenever OnKeyListener.onKey() detects
     * an enter key press event.
     *
     * @author stevko
     */
    private class ClientTask extends AsyncTask<MessageObj, Void, Void> {

        @Override
        protected Void doInBackground(MessageObj... msgs) {

            MessageObj msgObj = msgs[0];
            if (msgObj.getMsgType().equalsIgnoreCase(JOIN_MESSAGE))  {

                try {

                    Socket socket2 = new Socket(InetAddress.getByAddress(new byte[]{10, 0, 2, 2}),
                            firstNode); //should create a new socket here or not?
                    ObjectOutputStream oos = new ObjectOutputStream((socket2.getOutputStream()));
                    oos.writeObject(msgObj);
                    oos.flush();
                    oos.close();
                    socket2.close();

                } catch (UnknownHostException e) {
                    Log.e(TAG, "Client Task UnknownHostException ( JOIN_MSG)");
                } catch (IOException e) {
                    Log.e(TAG, "ClientTask socket IOException...( JOIN_MSG) ");

                } catch (NullPointerException e) {
                    Log.e(TAG, "Client task socket Null Pointer Exception ( JOIN_MSG)");
                }
            }else if (msgObj.getMsgType().equalsIgnoreCase(UPDATE_LIVE_NODE)) {
                Iterator iterator = ListOfAliveNodes.iterator();
                while(iterator.hasNext()) {
                    String aliveNodeGenHash= (String)iterator.next();
                    int aliveNode = Integer.parseInt(SenderGenHashMap.get(aliveNodeGenHash));
                    int portNoToSend = aliveNode*2;
                    try {

                        Socket socket2 = new Socket(InetAddress.getByAddress(new byte[]{10, 0, 2, 2}),
                                portNoToSend); //should create a new socket here or not?
                        ObjectOutputStream oos = new ObjectOutputStream((socket2.getOutputStream()));
                        oos.writeObject(msgObj);
                        oos.flush();
                        oos.close();
                        socket2.close();

                    } catch (UnknownHostException e) {
                        Log.e(TAG, "Client Task UnknownHostException ( UPDATE_LIVE_NODE)");
                    } catch (IOException e) {
                        Log.e(TAG, "ClientTask socket IOException...( UPDATE_LIVE_NODE) ");

                    } catch (NullPointerException e) {
                        Log.e(TAG, "Client task socket Null Pointer Exception ( UPDATE_LIVE_NODE)");
                    }
                }
            } else if (msgObj.getMsgType().equalsIgnoreCase(INSERT_MSG))  {

                try {
                    Log.e(TAG, "Whats my successor to which msg to be sent "+msgObj.getSuccessorForSendingMsg());
                    int ReceiverPort = Integer.parseInt(msgObj.getSuccessorForSendingMsg())*2;
                    Socket socket2 = new Socket(InetAddress.getByAddress(new byte[]{10, 0, 2, 2}),
                            ReceiverPort);
                    ObjectOutputStream oos = new ObjectOutputStream((socket2.getOutputStream()));
                    oos.writeObject(msgObj);
                    oos.flush();
                    oos.close();
                    socket2.close();

                } catch (UnknownHostException e) {
                    Log.e(TAG, "Client Task UnknownHostException ( INSERT_MSG)");
                } catch (IOException e) {
                    Log.e(TAG, "ClientTask socket IOException...( INSERT_MSG) ");

                } catch (NullPointerException e) {
                    Log.e(TAG, "Client task socket Null Pointer Exception ( INSERT_MSG)");
                }
            } else if (msgObj.getMsgType().equalsIgnoreCase(QUERY_MSG))  {

                try {
                    Log.e(TAG, "Client Task : My successor to which QUERY_MSG is sent "+msgObj.getSuccessorForSendingMsg());
                    int ReceiverPort = Integer.parseInt(msgObj.getSuccessorForSendingMsg())*2;
                    Socket socket2 = new Socket(InetAddress.getByAddress(new byte[]{10, 0, 2, 2}),
                            ReceiverPort);
                    ObjectOutputStream oos = new ObjectOutputStream((socket2.getOutputStream()));
                    oos.writeObject(msgObj);
                    oos.flush();
                    oos.close();
                    socket2.close();

                } catch (UnknownHostException e) {
                    Log.e(TAG, "Client Task UnknownHostException ( QUERY_MSG)");
                } catch (IOException e) {
                    Log.e(TAG, "ClientTask socket IOException...( QUERY_MSG) ");

                } catch (NullPointerException e) {
                    Log.e(TAG, "Client task socket Null Pointer Exception ( QUERY_MSG)");
                }
            } else if (msgObj.getMsgType().equalsIgnoreCase(QUERY_REPLY))  {

                try {
                    Log.e(TAG, "Client Task : My successor to which QUERY_REPLY is sent "+msgObj.getSuccessorForSendingMsg());
                    int ReceiverPort = Integer.parseInt(msgObj.getSuccessorForSendingMsg())*2;
                    Socket socket2 = new Socket(InetAddress.getByAddress(new byte[]{10, 0, 2, 2}),
                            ReceiverPort);
                    ObjectOutputStream oos = new ObjectOutputStream((socket2.getOutputStream()));
                    oos.writeObject(msgObj);
                    oos.flush();
                    oos.close();
                    socket2.close();

                } catch (UnknownHostException e) {
                    Log.e(TAG, "Client Task UnknownHostException ( QUERY_REPLY)");
                } catch (IOException e) {
                    Log.e(TAG, "ClientTask socket IOException...( QUERY_REPLY) ");

                } catch (NullPointerException e) {
                    Log.e(TAG, "Client task socket Null Pointer Exception ( QUERY_REPLY)");
                }
            }else if (msgObj.getMsgType().equalsIgnoreCase(QUERY_ALL_MSG))  {

                try {
                    Log.e(TAG, "Client Task : My successor to which QUERY_ALL_MSG is sent "+msgObj.getSuccessorForSendingMsg());
                    int ReceiverPort = Integer.parseInt(msgObj.getSuccessorForSendingMsg())*2;
                    Socket socket2 = new Socket(InetAddress.getByAddress(new byte[]{10, 0, 2, 2}),
                            ReceiverPort);
                    ObjectOutputStream oos = new ObjectOutputStream((socket2.getOutputStream()));
                    oos.writeObject(msgObj);
                    oos.flush();
                    oos.close();
                    socket2.close();

                } catch (UnknownHostException e) {
                    Log.e(TAG, "Client Task UnknownHostException ( QUERY_ALL_MSG)");
                } catch (IOException e) {
                    Log.e(TAG, "ClientTask socket IOException...( QUERY_ALL_MSG) ");

                } catch (NullPointerException e) {
                    Log.e(TAG, "Client task socket Null Pointer Exception ( QUERY_ALL_MSG)");
                }
            } else if (msgObj.getMsgType().equalsIgnoreCase(QUERY_ALL_REPLY))  {

                try {
                    Log.e(TAG, "Client Task : My successor to which QUERY_ALL_REPLY is sent "+msgObj.getSuccessorForSendingMsg());
                    int ReceiverPort = Integer.parseInt(msgObj.getSuccessorForSendingMsg())*2;
                    Socket socket2 = new Socket(InetAddress.getByAddress(new byte[]{10, 0, 2, 2}),
                            ReceiverPort);
                    ObjectOutputStream oos = new ObjectOutputStream((socket2.getOutputStream()));
                    oos.writeObject(msgObj);
                    oos.flush();
                    oos.close();
                    socket2.close();

                } catch (UnknownHostException e) {
                    Log.e(TAG, "Client Task UnknownHostException ( QUERY_ALL_REPLY)");
                } catch (IOException e) {
                    Log.e(TAG, "ClientTask socket IOException...( QUERY_ALL_REPLY) ");

                } catch (NullPointerException e) {
                    Log.e(TAG, "Client task socket Null Pointer Exception ( QUERY_ALL_REPLY)");
                }
            } else if (msgObj.getMsgType().equalsIgnoreCase(DELETE_MSG) || msgObj.getMsgType().equalsIgnoreCase(DELETE_ALL_MSG))  {

                try {
                    Log.e(TAG, "In Client Task : Sending DELETE_MSG to : (succ) "+msgObj.getSuccessorForSendingMsg());
                    int ReceiverPort = Integer.parseInt(msgObj.getSuccessorForSendingMsg())*2;
                    Socket socket2 = new Socket(InetAddress.getByAddress(new byte[]{10, 0, 2, 2}),
                            ReceiverPort);
                    ObjectOutputStream oos = new ObjectOutputStream((socket2.getOutputStream()));
                    oos.writeObject(msgObj);
                    oos.flush();
                    oos.close();
                    socket2.close();

                } catch (UnknownHostException e) {
                    Log.e(TAG, "Client Task UnknownHostException ( DELETE_MSG)");
                } catch (IOException e) {
                    Log.e(TAG, "ClientTask socket IOException...( DELETE_MSG) ");

                } catch (NullPointerException e) {
                    Log.e(TAG, "Client task socket Null Pointer Exception ( DELETE_MSG)");
                }
            }

            return null;
        }
    }


    private String genHash(String input) throws NoSuchAlgorithmException {
        MessageDigest sha1 = MessageDigest.getInstance("SHA-1");
        byte[] sha1Hash = sha1.digest(input.getBytes());
        Formatter formatter = new Formatter();
        for (byte b : sha1Hash) {
            formatter.format("%02x", b);
        }
        return formatter.toString();
    }
}
