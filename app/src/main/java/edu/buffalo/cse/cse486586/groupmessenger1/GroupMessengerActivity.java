package edu.buffalo.cse.cse486586.groupmessenger1;

import android.app.Activity;
import android.content.ContentResolver;
import android.content.ContentValues;
import android.content.Context;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Bundle;
import android.telephony.TelephonyManager;
import android.text.method.ScrollingMovementMethod;
import android.util.Log;
import android.view.Menu;
import android.view.View;
import android.widget.EditText;
import android.widget.TextView;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.UnknownHostException;

/**
 * GroupMessengerActivity is the main Activity for the assignment.
 * 
 * @author stevko
 *
 */
public class GroupMessengerActivity extends Activity {
    static final String TAG = GroupMessengerActivity.class.getSimpleName();
    static final int SERVER_PORT = 10000;
    private static final String keyField = "key";
    private static final String valueField = "value";
    private Uri uri;
    static String portArray[] = {"11108","11112","11116","11120","11124"};

    String myPort;
    static int sequenceNumber = 0;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_group_messenger);

        /*
         * TODO: Use the TextView to display your messages. Though there is no grading component
         * on how you display the messages, if you implement it, it'll make your debugging easier.
         */
        final TextView tv = (TextView) findViewById(R.id.textView1);
        tv.setMovementMethod(new ScrollingMovementMethod());
        
        /*
         * Registers OnPTestClickListener for "button1" in the layout, which is the "PTest" button.
         * OnPTestClickListener demonstrates how to access a ContentProvider.
         */
        findViewById(R.id.button1).setOnClickListener(
                new OnPTestClickListener(tv, getContentResolver()));
        
        /*
         * TODO: You need to register and implement an OnClickListener for the "Send" button.
         * In your implementation you need to get the message from the input box (EditText)
         * and send it to other AVDs.
         */

        TelephonyManager tel = (TelephonyManager) this.getSystemService(Context.TELEPHONY_SERVICE);
        String portStr = tel.getLine1Number().substring(tel.getLine1Number().length() - 4);
        myPort = String.valueOf((Integer.parseInt(portStr) * 2));

        //Log.v(TAG, myPort);



        try{
            //Log.v(TAG,"Server Socket creation");
            ServerSocket serverSocket = new ServerSocket(SERVER_PORT);
            new ServerTask().executeOnExecutor(AsyncTask.THREAD_POOL_EXECUTOR, serverSocket);
        } catch (IOException e) {
            e.printStackTrace();
            return;
        }

        findViewById(R.id.button4).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                EditText editText = (EditText) findViewById(R.id.editText1);
                String msg = editText.getText().toString();
                if (msg != null && !msg.isEmpty()) {
                    editText.setText("");
                    //Log.v(TAG, "Message button working.");
                    new ClientTask().executeOnExecutor(AsyncTask.SERIAL_EXECUTOR, msg, myPort);
                }
            }
        });
    }

    /*
    * TODO: The below code for ServerTask and ClientTask classes is reused from PA1 with changes.
    */

    private class ServerTask extends AsyncTask<ServerSocket, String, Void> {

        @Override
        protected Void doInBackground(ServerSocket... sockets) {
            ServerSocket serverSocket = sockets[0];
            String ipServer;
            //Log.v(TAG,"Server Socket Created");
            try {
                while(true) {
                    Socket clientSocket = serverSocket.accept();
                    //Log.v(TAG,"Server Socket Accepted");
                    DataInputStream in = new DataInputStream(clientSocket.getInputStream());
                    ipServer = in.readUTF();
                    //Log.v(TAG,"Input Received. " + ipServer);
                    DataOutputStream sendAck = new DataOutputStream(clientSocket.getOutputStream());
                    sendAck.writeUTF("ACK"); //Sent to keep the client from closing the socket prematurely.
                    sendAck.flush();
                    if ((ipServer  != null)) {
                        publishProgress(ipServer);
                        in.close();
                        sendAck.close();
                        clientSocket.close();
                    }
                    //Log.v(TAG,"Server still running.");
                }
            } catch (IOException e) {
                e.printStackTrace();
                Log.e(TAG,"Server error.");
            }
            return null;
        }

        protected void onProgressUpdate(String...strings) {
            super.onProgressUpdate(strings);
            String strReceived = strings[0].trim();
            /*
            * References:
            * 1) https://developer.android.com/reference/android/content/ContentResolver
            * 2) https://developer.android.com/reference/android/net/Uri.html
            * 3) https://developer.android.com/reference/android/content/ContentValues
            *
            * The ContentResolver object is used to interact with the Content provider in order to perform insert and query operations.
            * It receives a ContentResolver object from getContentResolver() which is a method of the Context class.
            * The ContentValues object is used to store values which are to be processed by the ContentResolver.
            * The URI object is used to uniquely identify and interact with a particular ContentProvider
            */
            ContentResolver contentResolver = getContentResolver();
            Uri.Builder uriBuilder = new Uri.Builder();
            uriBuilder.authority("edu.buffalo.cse.cse486586.groupmessenger1.provider");
            uriBuilder.scheme("content");
            uri = uriBuilder.build();
            ContentValues contentValues = new ContentValues();
            contentValues.put(keyField, Integer.toString(sequenceNumber));
            contentValues.put(valueField, strReceived);
            contentResolver.insert(uri, contentValues);
            sequenceNumber += 1;
            //Log.v(TAG, "Sequence Number : "+sequenceNumber);
            TextView textView = (TextView) findViewById(R.id.textView1);
            textView.append(strReceived + "\n");

        }
    }

    private class ClientTask extends AsyncTask<String, Void, Void> {

        @Override
        protected Void doInBackground(String... msgs) {

                //Log.v(TAG, "Attempting to send");
                String message = msgs[0];
                String myPort = msgs[1];

                for(String port: portArray) {
                    try {
                        Socket socket = new Socket(InetAddress.getByAddress(new byte[]{10, 0, 2, 2}),
                                Integer.parseInt(port));

                        String msgToSend = message;

                        try {
                            DataOutputStream out = new DataOutputStream(socket.getOutputStream());
                            DataInputStream receiveAck = new DataInputStream(socket.getInputStream());
                            out.writeUTF(msgToSend);
                            out.flush();
                            while(!socket.isClosed()){ //Run a while loop until an ACK is received from the server. After which, close the client connection.
                                String ack = receiveAck.readUTF();
                                if(ack.equals("ACK")){
                                    out.close();
                                    socket.close();
                                }
                            }

                        } catch (IOException e) {
                            Log.v(TAG, "Unable to write.");
                        }
                    } catch (UnknownHostException e) {
                        Log.e(TAG, "ClientTask UnknownHostException");
                    } catch (IOException e) {
                        Log.e(TAG, "ClientTask socket IOException");
                    }
                }
            return null;
        }
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        // Inflate the menu; this adds items to the action bar if it is present.
        getMenuInflater().inflate(R.menu.activity_group_messenger, menu);
        return true;
    }
}
