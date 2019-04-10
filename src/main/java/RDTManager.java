import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.xml.crypto.Data;
import java.io.IOException;
import java.lang.reflect.Array;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.net.SocketAddress;
import java.nio.channels.DatagramChannel;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;

/**
 * RDTManager converts the input payload into Packets conforming to the simulation standard.
 * It manages the sender and receiver objects which deliver and receive packets in accordance
 * with the RDT protocol.
 *
 *
 * Receiver receives according to RDT.
 *
 * Manager then confirms delivery or queues the received payload.
 */
public class RDTManager {

    private RDTReceiver receiver; //deals with reassembling the reply
    private RDTSender sender; //deals with sending out the data

    private SocketAddress routerAddr;
    private InetSocketAddress serverAddr;

    private DatagramChannel channel;

    private ArrayList<Packet> outbound;
    private ArrayList<Packet> inbound;

    private final int PAYLOAD_CAP = 1013;

    private long timeout;

    private final Logger logger = LoggerFactory.getLogger(RDTManager.class);

    public RDTManager(SocketAddress routerAddr, InetSocketAddress serverAddr, long timeout) {

        this.routerAddr = routerAddr;
        this.serverAddr = serverAddr;

        this.outbound = new ArrayList<>();
        this.inbound = new ArrayList<>();

        this.timeout = timeout;

        this.sender = new RDTSender(routerAddr, timeout);

        logger.info("RDTManager created.");

    }

    public void sendPayload(String payload){

        //case: payload is too big for one packet
        if (payload.getBytes().length > 1013) {

            int pCnt = (int) Math.ceil(payload.getBytes().length / 1013.0); //# of packets = to how many divisions it takes to fit the payload

            int prevIndex = 0;

            String dataSlice = payload;

            logger.info("Payload > 1013 bytes. Split into {} parts", pCnt);


            for (int i = 0; i < pCnt; i++) {

                if ((i * 1013) >= payload.length() || ((i + 1) * 1013) >= payload.length()) {

                    //hit the tail of the string

                    dataSlice = payload.substring(prevIndex);

                }
                else {
                    dataSlice = payload.substring(i * 1013, ((i + 1) * 1013));

                    prevIndex = (i + 1) * 1013; //keep track of where the last slice was
                }


                Packet p = new Packet.Builder()
                        .setType(0)
                        .setSequenceNumber(i)
                        .setPortNumber(serverAddr.getPort())
                        .setPeerAddress(serverAddr.getAddress())
                        .setPayload(dataSlice.getBytes())
                        .create();

                outbound.add(p);

                logger.info("Packet, dest. {}, created with data fragment. Contents: {}", p.getPeerAddress().getAddress(), p.getPayload());

            }

        }
        else {

            Packet p = new Packet.Builder()
                    .setType(0)
                    .setSequenceNumber(0)
                    .setPortNumber(serverAddr.getPort())
                    .setPeerAddress(serverAddr.getAddress())
                    .setPayload(payload.getBytes())
                    .create();

            outbound.add(p);

        }

        logger.info("Manager sending packets now. Total delivery: {} packets.", outbound.size());

        rdtSend(outbound);

    }

    public void rdtSend(ArrayList<Packet> pOut) {

        int wSize = pOut.size() / 2;

        logger.info("Sender invoked. Window size: {} packets.", wSize);

        int wBase = 0;
        int wCap = wSize;

        logger.info("Window: {} - {}.", wBase, wCap);

        ArrayList<Long> timeouts = new ArrayList<>();

        for (int i = 0; i < outbound.size(); i++) {

            timeouts.add(timeout + System.currentTimeMillis());

        }

        try(DatagramChannel channel = DatagramChannel.open()) {

            receiver = new RDTReceiver(channel);

            receiver.start();

            logger.info("Channel open. Beginning RDT send.");

            //send initial up to window, then enter loop for the rest of the protocol
            for (int i = 0; i < wCap; i++) {

                channel.send(pOut.get(i).toBuffer(), routerAddr);

                logger.info("Sent packet {}.", pOut.get(i).getSequenceNumber());

            }

            boolean exit = false;


            while(!exit) {

                for (int i = wBase; i < wCap; i++) {

                    if(rdtOp(wBase, pOut, timeouts)) {

                        wBase++;
                        wCap++;// ((wCap + 1) == pOut.size() ? wCap : wCap++); //cap shouldnt move outside index

                        if (wCap >= pOut.size()) {
                            wCap = pOut.size();
                            logger.info("Hit end of buffer");
                        }
                        logger.info("Window: {} - {}.", wBase, wCap);

                    }

                    if (timeouts.get(i) < System.currentTimeMillis() && !pOut.get(i).equals(null)) {

                        channel.send(pOut.get(i).toBuffer(), routerAddr);

                        timeouts.set(i, timeout + System.currentTimeMillis());

                    }

                }

                int isExitable = 0;

                for (int i = 0; i < pOut.size(); i++) {

                    if (!pOut.get(i).equals(null)) {
                        isExitable++;
                        //System.out.println(isExitable);
                    }

                }

                if (isExitable == pOut.size()) {
                    exit = true;

                    logger.info("Buffer sent and ACKed, terminating.");

                    receiver.stop();

                }

            }


        } catch(IOException e) {
            e.printStackTrace();
        } catch(InterruptedException e) {
            e.printStackTrace();
        }

    }

    private boolean rdtOp(int base, ArrayList<Packet> out, ArrayList<Long> timeouts) throws InterruptedException {

        if (receiver.recACKS.equals(null)) {
            return false;
        }

        if(!receiver.recACKS.isEmpty()) {

            Packet dummy = receiver.recACKS.take();

            String dStr = new String(dummy.getPayload(), StandardCharsets.UTF_8);

            if (dStr.contains("ACK")) {

                int seq = Integer.parseInt(dStr.substring("ACK ".length())); //ACK removed

                out.set(seq, null);
                timeouts.set(seq, Long.MAX_VALUE);

                logger.info("Packet seq: {} ACKed.", seq);

                if (seq == base) {

                    logger.info("Lowest seq: {} ACKed, moving window.", seq);

                    return true;
                }

            } else if (dStr.contains("SYN")) {
                //TODO TCP handshake logic - refactor: TCP handshake should be Type 2

            } else if (dStr.contains("FIN")) {
                //TODO TCP handshake logic

            }

        }

        return false;

    }

}
