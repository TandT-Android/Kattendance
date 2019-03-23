package boys.indecent.kattendance;

import android.os.Bundle;
import android.os.Handler;
import android.text.SpannableString;
import android.text.format.DateFormat;
import android.text.style.ForegroundColorSpan;
import android.widget.TextView;

import com.google.android.gms.nearby.connection.ConnectionInfo;
import com.google.android.gms.nearby.connection.Payload;
import com.google.android.gms.nearby.connection.Strategy;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Locale;
import java.util.Queue;
import java.util.Set;

public class NearbyAttendanceActivity extends ConnectionsActivity {
    /** If true, debug logs are shown on the device. */
    private static final boolean DEBUG = true;

    /** The time(in millisecond) to wait before attempting connection to a endpoint */
    private static final int DISCOVERING_DELAY = 500;

    /** The limit of the connections of an endpoint */
    private static final int MAX_CONNECTION_LIMIT = 3;


    /**
     * The connection strategy we'll use for Nearby Connections. In this case, we've decided on
     * P2P_CLUSTER.
     */
    private static final Strategy STRATEGY = Strategy.P2P_CLUSTER;

    /** The timeout time to send a second request */
    private static final int ACK_TIMEOUT = 2000;

    /**
     * This service id lets us find other nearby devices that are interested in the same thing.
     * In out case we will define SERVICE_ID to separate each section
     */
    private String SERVICE_ID;

    /** A random UID used as this device's endpoint name. */
    private String mName;

    /** If true, the endpoint needs to send details for attendance */
    private boolean isRequestPending;

    /** If true, the endpoint does not need to send any more request */
    private boolean isAckReceived;

    /**
     * The state of the app. As the app changes states, the UI will update and advertising/discovery
     * will start/stop.
     */
    private State mState = State.UNKNOWN;

    /** The queue to store the forwarding responses when the endpoint is disconnected */
    private Queue<Payload> forwardingQueue;

    /** A running log of debug messages. Only visible when DEBUG=true. */
    private TextView mDebugLogView;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_nearby_attendance);

        generateName();
        generateServiceId();

        setState(State.SEARCHING);
    }

    @Override
    protected void onDiscoveryFailed() {
        super.onDiscoveryFailed();
        logW("Search failed");
        //startDiscovering();
    }

    @Override
    protected void onEndpointDiscovered(Endpoint endpoint) {
        super.onEndpointDiscovered(endpoint);
        connectToEndpoint(endpoint);
        setState(State.CONNECTING);
        //stopDiscovering();
    }

    @Override
    protected void onEndpointConnectedAsChild(Endpoint endpoint) {
        super.onEndpointConnectedAsChild(endpoint);
        setState(State.CONNECTED);
    }

    @Override
    protected void onConnectionFailed(Endpoint endpoint) {
        super.onConnectionFailed(endpoint);
        setState(State.SEARCHING);
    }

    @Override
    protected void onEndpointDisconnectedAsChild(Endpoint endpoint) {
        super.onEndpointDisconnectedAsChild(endpoint);
        setState(State.SEARCHING);
        if (isAdvertising()){
            stopAdvertising();
        }
    }

    protected void triggerAdvertising(){
        if (isAdvertising())
            stopAdvertising();
        switch (getState()){
            case ADVERTISING:
                setState(State.ADVERTISING);
                break;
            case CONTENT:
                break;
            default:
                setState(State.ADVERTISING);
        }
    }

    @Override
    protected void onAdvertisingFailed() {
        super.onAdvertisingFailed();
        logW("Advertising failed");
        //startAdvertising();
    }

    @Override
    protected void onConnectionInitiatedAsChild(Endpoint endpoint, ConnectionInfo connectionInfo) {
        super.onConnectionInitiatedAsChild(endpoint, connectionInfo);
        acceptConnectionAsChild(endpoint);
    }

    @Override
    protected void onConnectionInitiatedAsParent(Endpoint endpoint, ConnectionInfo connectionInfo) {
        super.onConnectionInitiatedAsParent(endpoint, connectionInfo);
        if (!getState().equals(State.CONTENT)){
            acceptConnectionAsParent(endpoint);
        }
    }

    @Override
    protected void onEndpointConnectedAsParent(Endpoint endpoint) {
        super.onEndpointConnectedAsParent(endpoint);
        int connectedChildEndpoints = getConnectedChildEndpoints().size();
        if (connectedChildEndpoints == MAX_CONNECTION_LIMIT){
            setState(State.CONTENT);
        } else if (connectedChildEndpoints > MAX_CONNECTION_LIMIT){
            disconnect(endpoint);
        }
    }

    @Override
    protected void onEndpointDisconnectedAsParent(Endpoint endpoint) {
        super.onEndpointDisconnectedAsParent(endpoint);
        setState(State.ADVERTISING);
    }

    @Override
    protected void onReceiveAsChild(Endpoint endpoint, Payload payload) {
        super.onReceiveAsChild(endpoint, payload);
        if (payload.getType() == Payload.Type.BYTES) {
            try {
                Response response = Response.toResponse(payload);
                switch (response.getCode()){
                    case Response.Code.SET:
                        triggerAdvertising();
                        break;
                    case Response.Code.ACK:
                        if (response.getDestination().isEmpty()){
                            //This response is for me
                            onAckReceived(response);
                        } else {
                            //Deliver the message to proper child
                            deliverResponse(response);
                        }
                        break;
                    default:
                        logW("Unexpected response from parent");
                }
            } catch (Exception e) {
                e.printStackTrace();
                logE("Response format unsupported",e);
            }
        }
    }

    @Override
    protected void onReceiveAsParent(Endpoint endpoint, Payload payload) {
        super.onReceiveAsParent(endpoint, payload);
        if (payload.getType() == Payload.Type.BYTES){
            try{
                Response response = Response.toResponse(payload);
                switch (response.getCode()){
                    case Response.Code.REQ:
                        forwardResponse(response,endpoint);
                        break;
                    default:
                        logW("Unexpected response from child");
                }
            } catch (Exception e){
                e.printStackTrace();
                logE("Response format unsupported",e);
            }
        }
    }

    private void forwardResponse(Response response,Endpoint endpoint) throws IOException {
        ArrayList<String> destination = response.getDestination();
        destination.add(endpoint.getName());
        response.setDestination(destination);
        Payload payload = Response.toPayload(response);
        if ((getState().equals(State.ADVERTISING) || getState().equals(State.CONTENT))){
            //Forwarded the Request to parent
            sendToParent(payload);
            logD("Forwarded to the parent");
        } else {
            //Added to the queue to forward
            forwardingQueue.add(payload);
        }
    }

    private void sendRequest(){
        Response response = new Response(Response.Code.REQ,getRollNumber());
        response.setDestination(new ArrayList<String>());
        if (isRequestPending){
            try {
                sendToParent(Response.toPayload(response));
                isRequestPending = false;
                if (!isAckReceived){
                    startAckTimer();
                }
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
        while(forwardingQueue.size() > 0){
            sendToParent(forwardingQueue.remove());
        }
    }

    private void startAckTimer() {
        final Runnable r = new Runnable() {
            public void run() {
                sendRequest();
            }
        };
        new Handler().postDelayed(r, ACK_TIMEOUT);
    }


    private void onAckReceived(Response response) {
        if (response.getMessage().equals("POS")){
            isAckReceived = true;
            isRequestPending = false;
        }
    }

    private void deliverResponse(Response response) throws IOException {
        ArrayList<String> destination = response.getDestination();
        String nextHopName = destination.get(destination.size()-1);
        destination.remove(destination.size()-1);
        response.setDestination(destination);
        Set<Endpoint> endpoints = getConnectedChildEndpoints();
        for (Endpoint e : endpoints){
            if (e.getName().equals(nextHopName)){
                sendToChild(Response.toPayload(response),e.getId());
                return;
            }
        }
    }

    /**
     * The state has changed. I wonder what we'll be doing now.
     *
     * @param state The new state.
     */
    private void setState(State state){
        if (mState == state){
            logW("State set to " + state + " but already in that state");
            return;
        }

        logD("State set to " + state);
        State oldState = mState;
        mState = state;
        onStateChanged(oldState,state);
    }

    /** @return The current state. */
    private State getState() {
        return mState;
    }

    /**
     * State has changed.
     *
     * @param oldState The previous state we were in. Clean up anything related to this state.
     * @param newState The new state we're now in. Prepare the UI for this state.
     */
    private void onStateChanged(State oldState, State newState){
        //TODO : add transition methods
        switch (newState){
            case SEARCHING:
                startDiscovering();
                break;
            case CONNECTING:
                stopDiscovering();
                break;
            case CONNECTED:
                sendRequest();
                break;
            case ADVERTISING:
                startAdvertising();
                break;
            case CONTENT:
                stopAdvertising();
        }
    }

    /** Generates name for a particular device*/
    private void generateName() {
        mName = getRollNumber();
    }

    /** Generates service id for a particular section */
    private void generateServiceId(){
        String section = getSection();
        int semester = getCurrentSemester();
        SERVICE_ID =  String.format(
                Locale.getDefault(),
                "%s.KIIT.%d.%s",
                getApplicationContext().getPackageName(),
                semester,
                section
        );
    }

    /** Get the roll number of a student */
    private String getRollNumber(){
        //TODO: add proper getRollNumber method
        return "1605271";
    }

    /** Get the section name of a student */
    private String getSection() {
        //TODO: add proper getSection method
        return "CS4";
    }

    /** Get the current semester of a student */
    private int getCurrentSemester(){
        //TODO: add proper getCurrentSemester method
        return 6;
    }

    /**
     * Queries the phone's contacts for their own profile, and returns their name. Used when
     * connecting to another device.
     */
    @Override
    protected String getName() {
        return mName;
    }

    /** {@see ConnectionsActivity#getServiceId()} */
    @Override
    public String getServiceId() {
        return SERVICE_ID;
    }

    /** {@see ConnectionsActivity#getStrategy()} */
    @Override
    public Strategy getStrategy() {
        return STRATEGY;
    }

    @Override
    protected void logV(String msg) {
        super.logV(msg);
        appendToLogs(toColor(msg, getResources().getColor(R.color.log_verbose)));
    }

    @Override
    protected void logD(String msg) {
        super.logD(msg);
        appendToLogs(toColor(msg, getResources().getColor(R.color.log_debug)));
    }

    @Override
    protected void logW(String msg) {
        super.logW(msg);
        appendToLogs(toColor(msg, getResources().getColor(R.color.log_warning)));
    }

    @Override
    protected void logW(String msg, Throwable e) {
        super.logW(msg, e);
        appendToLogs(toColor(msg, getResources().getColor(R.color.log_warning)));
    }

    @Override
    protected void logE(String msg, Throwable e) {
        super.logE(msg, e);
        appendToLogs(toColor(msg, getResources().getColor(R.color.log_error)));
    }

    private void appendToLogs(CharSequence msg) {
        mDebugLogView.append("\n");
        mDebugLogView.append(DateFormat.format("hh:mm", System.currentTimeMillis()) + ": ");
        mDebugLogView.append(msg);
    }

    private static CharSequence toColor(String msg, int color) {
        SpannableString spannable = new SpannableString(msg);
        spannable.setSpan(new ForegroundColorSpan(color), 0, msg.length(), 0);
        return spannable;
    }

    public enum State {
        UNKNOWN,
        SEARCHING,
        CONNECTING,
        CONNECTED,
        ADVERTISING,
        CONTENT
    }

//    protected static class Response implements Serializable {
//        @NonNull private final int code;
//        private final String message;
//        private byte[] extra;
//        private ArrayList<String> destination;
//
//        public @interface Code{
//            int SET = 0;
//            int ACK = 1;
//        }
//
//        public Response(int code, String message, ArrayList<String> destination) {
//            this.code = code;
//            this.message = message;
//            this.destination = destination;
//            this.extra = null;
//        }
//
//        public Response(int code, String message){
//            this.code = code;
//            this.message = message;
//            this.destination = null;
//            this.extra = null;
//        }
//
//        static Response toResponse(Payload payload) throws IOException, ClassNotFoundException {
//            byte[] bytes = payload.asBytes();
//            ByteArrayInputStream bis = new ByteArrayInputStream(bytes);
//            ObjectInput in = new ObjectInputStream(bis);
//            Response response =(Response) in.readObject();
//            in.close();
//            return response;
//        }
//
//        static Payload toPayload(Response response) throws IOException {
//            ByteArrayOutputStream bos = new ByteArrayOutputStream();
//            ObjectOutput out = new ObjectOutputStream(bos);
//            out.writeObject(response);
//            out.flush();
//            byte[] bytes = bos.toByteArray();
//            bos.close();
//
//            return Payload.fromBytes(bytes);
//        }
//
//        public int getCode() {
//            return code;
//        }
//
//        public String getMessage() {
//            return message;
//        }
//
//
//
//        public ArrayList<String> getDestination() {
//            return destination;
//        }
//
//        public void setDestination(ArrayList<String> destination) {
//            this.destination = destination;
//        }
//    }
}
