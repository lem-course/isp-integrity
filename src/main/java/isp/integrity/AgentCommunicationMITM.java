package isp.integrity;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;

public class AgentCommunicationMITM {
    public static void main(String[] args) throws NoSuchAlgorithmException {
        // secret shared between client1 and the service
        final String secret = "secretsecret22";

        final BlockingQueue<byte[]> alice2mitm = new LinkedBlockingQueue<>();
        final BlockingQueue<byte[]> mitm2service = new LinkedBlockingQueue<>();

        final Agent client = new Agent("CLIENT 1", alice2mitm, null, null, "SHA-1") {
            @Override
            public void execute() throws Exception {
                final String message = "count=10&lat=37.351&user_id=1&long=-119.827&waffle=eggo";
                final byte[] pt = message.getBytes("UTF-8");

                final MessageDigest d = MessageDigest.getInstance(cipher);
                d.update(secret.getBytes("UTF-8"));
                final byte[] tag = d.digest(pt);

                print("data = %s", message);
                print("pt   = %s", hex(pt));
                print("tag  = %s", hex(tag));

                outgoing.put(pt);
                outgoing.put(tag);
            }
        };

        final Agent mitm = new Agent("MITM (Client 2)", mitm2service, alice2mitm, null, null) {
            @Override
            public void execute() throws Exception {
                final byte[] pt = incoming.take();
                final byte[] tag = incoming.take();
                final String message = new String(pt, "UTF-8");
                print("data    = %s", message);
                print("pt      = %s", hex(pt));
                print("tag     = %s", hex(tag));
                outgoing.put(pt);
                outgoing.put(tag);

                // TODO: manipulate the parameters and send another valid request
                // You can assume that when the parameters repeat, the service uses the right-most value
                // For instance, to change the name of the waffle, you could send the following request
                //     count=10&lat=37.351&user_id=1&long=-119.827&waffle=eggo&waffle=liege
            }
        };

        final Agent service = new Agent("SERVICE", null, mitm2service, null, "SHA-1") {
            @Override
            public void execute() throws Exception {
                final byte[] pt = incoming.take();
                final String message = new String(pt, "UTF-8");
                final byte[] tagReceived = incoming.take();

                final Map<String, String> params = new HashMap<>();
                Arrays.stream(message.split("&")).forEach(p -> {
                    final String[] nameValue = p.split("=");
                    params.put(nameValue[0], nameValue[1]);
                });
                print("data   = %s", params);
                print("pt     = %s", hex(pt));
                print("tag_r  = %s", hex(tagReceived));

                final MessageDigest d = MessageDigest.getInstance(cipher);
                final byte[] tagComputed = d.digest((secret + message).getBytes("UTF-8"));
                print("tag_c  = %s", hex(tagComputed));

                if (Arrays.equals(tagReceived, tagComputed))
                    print("Authenticity and integrity verified.");
                else
                    print("Failed to verify authenticity and integrity.");
            }
        };

        client.start();
        mitm.start();
        service.start();
    }
}
