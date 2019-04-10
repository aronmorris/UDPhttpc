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
import java.util.Collections;
import java.util.Comparator;

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

    public String rdtSend(ArrayList<Packet> pOut) {

        int wSize = pOut.size() / 2;

        logger.info("Sender invoked. Window size: {} packets.", wSize);

        int wBase = 0;
        int wCap = wSize;

        String reply = "";

        boolean[] exitCond = new boolean[pOut.size()];

        logger.info("Window: {} - {}.", wBase, wCap);

        ArrayList<Long> timeouts = new ArrayList<>();

        for (int i = 0; i < pOut.size(); i++) {

            timeouts.add(timeout + System.currentTimeMillis());

            exitCond[i] = false;

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


            while(unACKED(exitCond)) {

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

                    if (timeouts.get(i) < System.currentTimeMillis() && !(pOut.get(i).getType() == -1)) {

                        channel.send(pOut.get(i).toBuffer(), routerAddr);

                        logger.info("Sent packet {}.", pOut.get(i).getSequenceNumber());

                        timeouts.set(i, timeout + System.currentTimeMillis());

                    }

                }

                for (int i = 0; i < exitCond.length; i++) {

                    exitCond[i] = (pOut.get(i).getType()) == -1 ? true : false;

                }


            }

            logger.info("RDT complete. Parsing payload-type replies now.");

            reply = assembleReply();

            receiver.stop();

        } catch(IOException e) {
            e.printStackTrace();
        } catch(InterruptedException e) {
            e.printStackTrace();
        }

        return reply;

    }

    private boolean unACKED(boolean[] vals) {

        for (boolean b : vals) {
            if (!b) {
                return true;
            }
        }

        return false;

    }

    private String assembleReply() throws InterruptedException {

        StringBuilder reply = new StringBuilder();

        ArrayList<Packet> repIn = new ArrayList();

        //empty the payload queue in whatever order
        while(!receiver.plPackets.isEmpty()) {

            Packet p = receiver.plPackets.take();

            if (p.getType() == RDTReceiver.PAYLOAD) {
                repIn.add(p);
            }

        }

        for (Packet p : repIn) {

            System.out.println(new String(p.getPayload(), StandardCharsets.UTF_8));
        }

        repIn.sort(Comparator.comparing(Packet::getSequenceNumber)); //order the packets ASC

        for (Packet p : repIn) {

            System.out.println(new String(p.getPayload(), StandardCharsets.UTF_8));
        }

        return reply.toString();
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

                out.set(seq, new Packet(out.get(seq), -1));
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
