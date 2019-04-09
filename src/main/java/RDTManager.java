import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.xml.crypto.Data;
import java.io.IOException;
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

        rdt_send(outbound);

    }

    public void rdt_send(ArrayList<Packet> pOut) {

        int wSize = pOut.size() / 2;

        logger.info("Sender invoked. Window size: {} packets.", wSize);

        int wBase = 0;
        int wCap = wSize;

        long[] timeouts = new long[pOut.size()];

        for (int i = 0; i < timeouts.length; i++) {

            timeouts[i] = timeout + System.currentTimeMillis();

        }

        try(DatagramChannel channel = DatagramChannel.open()) {

            RDTReceiver receiver = new RDTReceiver(channel);

            receiver.start();

            logger.info("Channel open. Beginning RDT send.");

            //send initial up to window, then enter loop for the rest of the protocol
            for (int i = 0; i <= wCap; i++) {

                channel.send(pOut.get(i).toBuffer(), routerAddr);

            }

            while(!pOut.isEmpty()) {

                for (int i = wBase; i <= wCap; i++) {

                    if(!receiver.recACKS.isEmpty()) {

                        Packet dummy = receiver.recACKS.take();

                        String payload = new String(dummy.getPayload(), StandardCharsets.UTF_8);

                        System.out.println(payload);

                    }

                }

            }

        } catch(IOException e) {
            e.printStackTrace();
        } catch(InterruptedException e) {
            e.printStackTrace();
        }




    }

}
