package boys.indecent.mypresense;

import com.google.android.gms.nearby.connection.Payload;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.ObjectInput;
import java.io.ObjectInputStream;
import java.io.ObjectOutput;
import java.io.ObjectOutputStream;
import java.io.Serializable;
import java.util.ArrayList;

public class Response implements Serializable {
    private final int code;
    private final String message;
    private byte[] extra;
    private ArrayList<String> destination;

    public @interface Code {
        int SET = 0;
        int ACK = 1;
        int REQ = 2;
    }

    public Response(int code, String message, ArrayList<String> destination) {
        this.code = code;
        this.message = message;
        this.destination = destination;
        this.extra = null;
    }

    public Response(int code, String message) {
        this.code = code;
        this.message = message;
        this.destination = null;
        this.extra = null;
    }

    public static Response toResponse(Payload payload) throws IOException, ClassNotFoundException {
        byte[] bytes = payload.asBytes();
        ByteArrayInputStream bis = new ByteArrayInputStream(bytes);
        ObjectInput in = new ObjectInputStream(bis);
        Response response = (Response) in.readObject();
        in.close();
        return response;
    }

    public static Payload toPayload(Response response) throws IOException {
        ByteArrayOutputStream bos = new ByteArrayOutputStream();
        ObjectOutput out = new ObjectOutputStream(bos);
        out.writeObject(response);
        out.flush();
        byte[] bytes = bos.toByteArray();
        bos.close();

        return Payload.fromBytes(bytes);
    }

    public int getCode() {
        return code;
    }

    public String getMessage() {
        return message;
    }

    public byte[] getExtra() {
        return extra;
    }

    public ArrayList<String> getDestination() {
        return destination;
    }

    public void setDestination(ArrayList<String> destination) {
        this.destination = destination;
    }

    public void setExtra(byte[] extra) {
        this.extra = extra;
    }
}
