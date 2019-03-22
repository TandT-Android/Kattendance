package boys.indecent.kattendance;

import android.os.Bundle;
import android.support.annotation.NonNull;
import android.text.SpannableString;
import android.text.format.DateFormat;
import android.text.style.ForegroundColorSpan;
import android.widget.TextView;

import com.google.android.gms.nearby.connection.Payload;
import com.google.android.gms.nearby.connection.Strategy;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.Locale;

public class NearbyAttendanceActivity extends ConnectionsActivity {
    /** If true, debug logs are shown on the device. */
    private static final boolean DEBUG = true;

    /** The time(in millisecond) to wait before attempting connection to a endpoint */
    private static final int DISCOVERING_DELAY = 500;

    /**
     * The connection strategy we'll use for Nearby Connections. In this case, we've decided on
     * P2P_CLUSTER.
     */
    private static final Strategy STRATEGY = Strategy.P2P_CLUSTER;

    /**
     * This service id lets us find other nearby devices that are interested in the same thing.
     * In out case we will define SERVICE_ID to separate each section
     */
    private String SERVICE_ID;

    /** A random UID used as this device's endpoint name. */
    private String mName;

    /**
     * The state of the app. As the app changes states, the UI will update and advertising/discovery
     * will start/stop.
     */
    private State mState = State.UNKNOWN;

    /** A running log of debug messages. Only visible when DEBUG=true. */
    private TextView mDebugLogView;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_nearby_attendance);

        mName = getRollNumber();
        SERVICE_ID = generateServiceId();

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
    }

    protected void triggerAdvertising(){
        switch (getState()){
            case ADVERTISING:
                stopAdvertising();
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
    protected void onEndpointConnectedAsParent(Endpoint endpoint) {
        super.onEndpointConnectedAsParent(endpoint);
        if (getConnectedChildEndpoints().size() == 3){
            setState(State.CONTENT);
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
        //TODO:
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
                break;
            case ADVERTISING:
                startAdvertising();
                break;
            case CONTENT:
                stopAdvertising();
        }
    }



    /** Generates service id for a particular section */
    private String generateServiceId(){
        String section = getSection();
        int semester = getCurrentSemester();
        return String.format(
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

    protected static class Response implements Serializable {
        @NonNull private final int code;
        private final String message;
        private ArrayList<String> destination;

        public Response(int code, String message, ArrayList<String> destination) {
            this.code = code;
            this.message = message;
            this.destination = destination;
        }
    }
}
