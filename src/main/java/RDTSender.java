import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.xml.crypto.Data;
import java.io.IOException;
import java.lang.reflect.Array;
import java.net.DatagramSocket;
import java.net.InetSocketAddress;
import java.net.SocketAddress;
import java.nio.channels.DatagramChannel;
import java.util.ArrayList;
import java.util.Arrays;

public class RDTSender implements Runnable {

    private SocketAddress routerAddr;

    private ArrayList<Packet> outbound;

    private int wSize;

    private long timeout;

    private final Logger logger = LoggerFactory.getLogger(RDTSender.class);

    public RDTSender(SocketAddress routerAddr, long timeout) {

        this.routerAddr = routerAddr;

        this.timeout = timeout;

    }

    public void send(ArrayList<Packet> outbound) {

        int wSize = outbound.size() / 2; //wSize optimal at half queue size

        this.wSize = wSize;

        this.outbound = outbound;

        this.run();

    }

    private void rdt_send() {

        logger.info("Sender invoked. Window size: {} packets.");

        int wBase = 0;
        int wEnd = wSize;

        ArrayList<Long> timeouts = new ArrayList();

        for (Packet p : outbound) {
            timeouts.add(timeout + System.currentTimeMillis());
        }

        try (DatagramChannel channel = DatagramChannel.open()) {

            logger.info("Sender channel opened.");

            while(!outbound.isEmpty()) {

                for (int i = wBase; i < wEnd; i++) {

                    if (timeouts.get(i) <= System.currentTimeMillis()) {

                        channel.send(outbound.get(i).toBuffer(), routerAddr);

                    }

                }

                channel.send(outbound.get(0).toBuffer(), routerAddr);

                outbound.remove(0);

                logger.info("Packet sent to {}", routerAddr.toString());

            }

        } catch(IOException e) {
            e.printStackTrace();
        }

    }

    @Override
    public void run() {

        rdt_send();

    }
}
