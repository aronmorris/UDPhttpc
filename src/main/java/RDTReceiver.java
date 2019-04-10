import com.sun.xml.internal.bind.api.impl.NameConverter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.net.SocketAddress;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.channels.DatagramChannel;
import java.nio.channels.SelectableChannel;
import java.nio.channels.SelectionKey;
import java.nio.channels.Selector;
import java.nio.charset.StandardCharsets;
import java.util.Set;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;

/**
 * RDTReceiver runs in its own thread from the Manager in order to keep listening at all times. It provides concurrency
 * safe queues to the Manager to examine as packets come in.
 *
 * recACKS contains the received ACKs for packets send according to the RDT protocol
 * contents provides disordered payload packet replies, which must be reassembled by the manager in the correct
 * order
 *
 * NOTE: overhead (ACKs, SYN-ACK, SYN) are Packet type 0, while payload packets are type 1
 */
public class RDTReceiver extends Thread {

    protected BlockingQueue<Packet> recACKS;
    protected BlockingQueue<Packet> plPackets;

    private DatagramChannel channel;

    private boolean sigCLOSE = false;

    private Logger logger = LoggerFactory.getLogger(RDTReceiver.class);

    @Override
    public void run() {

        try {

            ByteBuffer buf = ByteBuffer
                    .allocate(Packet.MAX_LEN)
                    .order(ByteOrder.BIG_ENDIAN);

            logger.info("Receiver listening for replies.");

            while (true) { //continually listen for incoming messages on the sender channel and queue them for the manager to handle

                buf.clear();

                channel.configureBlocking(false);

                Selector selector = Selector.open();

                channel.register(selector, SelectionKey.OP_READ);

                selector.select(1000);

                Set<SelectionKey> keys = selector.selectedKeys();

                if (keys.isEmpty()) {
                    //TODO timeout error
                    continue;
                }

                ByteBuffer replyBuf = ByteBuffer.allocate(Packet.MAX_LEN);
                SocketAddress router = channel.receive(buf);
                buf.flip();
                Packet resp = Packet.fromBuffer(buf);

               //String payload = new String(resp.getPayload(), StandardCharsets.UTF_8);

               if (resp.getType() == 0) { //OVERHEAD: ACK, SYN, FIN, w/e, this doesn't care beyond type. Payload is handled above.
                   recACKS.add(resp);
                   logger.info("Overhead reply queued for manager. Buffer at {} entries.", recACKS.size());
               }
               else {
                   plPackets.add(resp); //any other payload
                   logger.info("Payload reply queued for manager.");
               }

               keys.clear();
              // System.out.println(payload);


            }

        } catch(IOException e) {
            e.printStackTrace();
        }

    }

    public RDTReceiver(DatagramChannel channel) {

        this.channel = channel;

        recACKS = new ArrayBlockingQueue<>(Short.MAX_VALUE); //unknown how many ACKs will be sent but probably less than 32000

        plPackets = new ArrayBlockingQueue<>(Short.MAX_VALUE);

    }

    public void close() {

        sigCLOSE = true;

    }
}
