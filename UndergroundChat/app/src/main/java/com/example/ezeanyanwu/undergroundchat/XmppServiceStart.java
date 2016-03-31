package com.example.ezeanyanwu.undergroundchat;

import android.app.IntentService;
import android.content.Intent;
import android.content.Context;
import android.os.Binder;
import android.os.IBinder;
import android.support.v4.content.LocalBroadcastManager;
import android.util.Log;

import org.jivesoftware.smack.AbstractXMPPConnection;
import org.jivesoftware.smack.ConnectionConfiguration;
import org.jivesoftware.smack.SASLAuthentication;
import org.jivesoftware.smack.SmackException;
import org.jivesoftware.smack.XMPPConnection;
import org.jivesoftware.smack.XMPPException;
import org.jivesoftware.smack.chat.Chat;
import org.jivesoftware.smack.chat.ChatManager;
import org.jivesoftware.smack.chat.ChatManagerListener;
import org.jivesoftware.smack.chat.ChatMessageListener;
import org.jivesoftware.smack.packet.Message;
import org.jivesoftware.smack.roster.Roster;
import org.jivesoftware.smack.tcp.XMPPTCPConnection;
import org.jivesoftware.smack.tcp.XMPPTCPConnectionConfiguration;
import org.jivesoftware.smackx.muc.InvitationListener;
import org.jivesoftware.smackx.muc.MultiUserChat;
import org.jivesoftware.smackx.muc.MultiUserChatManager;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

/**
 * An {@link IntentService} subclass for handling asynchronous task requests in
 * a service on a separate handler thread.
 */
public class XmppServiceStart extends IntentService {

    private final IBinder myBinder = new LocalBinder();

    //Various variables offered by this service
    private ChatManager theChatManager;
    private MultiUserChatManager theMUChatManager;
    private XMPPConnection theConnection;
    private Roster theRoster;

    /* Variables to keep track of ongoing chats */
    private List<String> chatList = new ArrayList<>();
    private List<String> muChatList = new ArrayList<>();
    private String mostRecentMuChat = "0";
    private String mostRecentChat = "0";

    /* User credentials */
    private String username;
    private String password;

    /* Constructor */
    public XmppServiceStart()
    {
        super("XmppServiceStart");
    }

    public class LocalBinder extends Binder
    {
        XmppServiceStart getService()
        {
            return XmppServiceStart.this;
        }
    }

    /* This is run when the activity calls startService() */
    @Override
    protected void onHandleIntent(Intent intent)
    {
        Log.d("Xmpp:", "Hey! The Service Ran!");
        username = intent.getStringExtra("USERNAME");
        password = intent.getStringExtra("PASSWORD");

        theConnection = connectToServer(username, password);
        theChatManager = startListeningForChats();
        theMUChatManager = startListeningForMUChats();
        theRoster = Roster.getInstanceFor(theConnection);
        Roster.setDefaultSubscriptionMode(Roster.SubscriptionMode.accept_all);

    }

    /* This is called when the activity calls bindService() */
    @Override
    public IBinder onBind(Intent intent)
    {
        Log.d("Xmpp:", "Hey! The Service is Bound!");
        return myBinder;
    }

    /* Connect to the server */
    public XMPPConnection connectToServer(String user, String pass)
    {
        /* Amazon server name */
        String serverName ="ec2-52-33-88-207.us-west-2.compute.amazonaws.com";

        /* Configure connection settings */
        XMPPTCPConnectionConfiguration config = XMPPTCPConnectionConfiguration.builder()
                .setSecurityMode(ConnectionConfiguration.SecurityMode.disabled)
                .setUsernameAndPassword(user, pass)
                .setServiceName("gordon.com")
                .setHost(serverName)
                .setPort(5222)
                .setDebuggerEnabled(true)
                .build();

        AbstractXMPPConnection conn = new XMPPTCPConnection(config);

        /* Attempt to connect, throw exception if not able to */
        try {
            conn.connect();
        } catch (SmackException e) {
            Log.d("SmackException", e.getMessage().toString());
            System.exit(0);
        } catch (IOException e) {
            Log.d("IOException onConnect", e.getMessage().toString());
            System.exit(0);
        } catch (XMPPException e) {
            Log.d("XMPPException onConnect", e.getMessage().toString());
            System.exit(0);
        }

        Log.d("Connected:", "Hey! We are connected!");

        /* Attempt to login, throw exceptions if not able to */
        try {
            conn.login();
        } catch (XMPPException e) {
            e.printStackTrace();
            System.exit(0);
        } catch (SmackException e) {
            e.printStackTrace();
            System.exit(0);
        } catch (IOException e) {
            e.printStackTrace();
            System.exit(0);
        }
        Log.d("Logged In: ", "Hey! We are logged in!");

        /* Return the connection object */
        return conn;

    }


    /* Function to setup a listener for incoming multi-user chats (chat rooms) */
    /* NOT YET FINISHED */
    public MultiUserChatManager startListeningForMUChats()
    {
        final MultiUserChatManager muChatManager = MultiUserChatManager.getInstanceFor(theConnection);
        muChatManager.addInvitationListener(new InvitationListener() {
            @Override
            public void invitationReceived(XMPPConnection conn, MultiUserChat room, String inviter, String reason, String password, Message message) {
                if (muChatList == null || !muChatList.contains(room.getRoom())) {
                    try {
                        room.join(username);
                    } catch (SmackException.NoResponseException e) {
                        Log.d("NEW_GRP_CHT", e.getMessage().toString());
                        e.printStackTrace();
                    } catch (XMPPException.XMPPErrorException e) {
                        Log.d("NEW_GRP_CHT", e.getMessage().toString());
                    } catch (SmackException.NotConnectedException e) {
                        Log.d("NEW_GRP_CHT", e.getMessage().toString());
                    }

                    muChatList.add(room.getRoom());
                    Log.d("NEW_GRP_CHT", room.getRoom());
                }

                mostRecentMuChat = room.getRoom();
            }
        });

        return muChatManager;
    }

    /* Function to setup a listener for incoming chats */
    public ChatManager startListeningForChats()
    {
        ChatManager chatmanager = ChatManager.getInstanceFor(theConnection);
        chatmanager.addChatListener(
                new ChatManagerListener() {
                    @Override
                    public void chatCreated(Chat chat, boolean createdLocally) {
                        if (!createdLocally) {
                            chat.addMessageListener(new ChatMessageListener());
                        }
                    }
                }
        );
        return chatmanager;
    }


    /* Convenience method to send message */
    /* NOT FINISHED YET */
    public void sendChat(String text)
    {
        Chat chat = theChatManager.getThreadChat(mostRecentChat);
        try {
            chat.sendMessage(text);
        } catch (SmackException.NotConnectedException e) {
            Log.d("Xmpp: ", "NOT CONNECTED");
        }
    }

    /* Setters and Getters */
    public XMPPConnection getConnection()
    {
        return theConnection;
    }
    public Roster getRoster()
    {
        return theRoster;
    }
    public ChatManager getChatManager()
    {
        return theChatManager;
    }

    /* Listener object that is used by startListeningForChats */
    public class ChatMessageListener implements org.jivesoftware.smack.chat.ChatMessageListener
    {
        @Override
        public void processMessage(Chat chat, Message message)
        {
            if( chatList == null || !chatList.contains(chat.getThreadID()) )
            {
                chatList.add(chat.getThreadID());
                Log.d("NEWCHAT: ", chat.getThreadID());
            }

            if((message.getType() == Message.Type.chat || message.getType() == Message.Type.groupchat) && hasBody(message))
            {
                mostRecentChat = chat.getThreadID();
                String from = chat.getParticipant().toString();
                String text = message.getBody().toString();
//                Log.d("NEW CHAT:", from + ":" + text);
                Intent intent = new Intent("Message-Received");
                intent.putExtra("FROM", from);
                intent.putExtra("TEXT", text);
                LocalBroadcastManager.getInstance(XmppServiceStart.this).sendBroadcast(intent);
            }
        }

        private boolean hasBody(Message message)
        {

            if (message.getBody() == null )
            {
                return false;
            }
            else
            {
                return true;
            }
        }
    }

}
