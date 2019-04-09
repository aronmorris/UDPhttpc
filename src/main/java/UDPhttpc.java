import joptsimple.OptionParser;
import joptsimple.OptionSet;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.SocketAddress;

public class UDPhttpc {

    public static final Logger logger = LoggerFactory.getLogger(UDPhttpc.class);

    public static void main(String[] args) throws IOException {
        OptionParser parser = new OptionParser();

        parser.accepts("http-verb", "GET, POST, HELP")
                .withOptionalArg()
                .defaultsTo("HELP");

        parser.accepts("router-host", "Router hostname")
                .withOptionalArg()
                .defaultsTo("localhost");

        parser.accepts("router-port", "Router port number")
                .withOptionalArg()
                .defaultsTo("3000");

        parser.accepts("server-host", "EchoServer hostname")
                .withOptionalArg()
                .defaultsTo("localhost");

        parser.accepts("server-port", "EchoServer listening port")
                .withOptionalArg()
                .defaultsTo("8007");
        parser.accepts("timeout-period", "Time to wait for resend")
                .withOptionalArg()
                .defaultsTo("5000");

        OptionSet opts = parser.parse(args);

        // Router address
        String routerHost = (String) opts.valueOf("router-host");
        int routerPort = Integer.parseInt((String) opts.valueOf("router-port"));

        // Server address
        String serverHost = (String) opts.valueOf("server-host");
        int serverPort = Integer.parseInt((String) opts.valueOf("server-port"));

        SocketAddress routerAddress = new InetSocketAddress(routerHost, routerPort);
        InetSocketAddress serverAddress = new InetSocketAddress(serverHost, serverPort);

        long timeout =Long.parseLong(opts.valueOf("timeout-period").toString());

        String verb = (String) opts.valueOf("http-verb");

        runClient(routerAddress, serverAddress, timeout);

    }

    private static void runClient(SocketAddress routerAddr, InetSocketAddress serverAddr, long timeout) throws IOException {

        RDTManager manager = new RDTManager(routerAddr, serverAddr, 1000);

        manager.sendPayload("Here are a few bytes TOOOOTalling 2000 bytes in total, just gotta do this a few times to get to or near 100 or a bit past it to see what will happen and if it'll properly parse the thing if given many many many bytes. \n" +
                "\n" +
                "I should be able to read where it fucks up if the payload isnt broken into pieces correctly but we'll see I guess, hey we're in the 300s now lets try to make it to 400 and its close and there it is 420 bytes passed now let's keep it going to 500 so I can copy paste this thing woweee there we go.\n" +
                "\n" +
                "Here are a few bytes TOOOOTalling 2000 bytes in total, just gotta do this a few times to get to or near 100 or a bit past it to see what will happen and if it'll properly parse the thing if given many many many bytes. \n" +
                "\n" +
                "I should be able to read where it fucks up if the payload isnt broken into pieces correctly but we'll see I guess, hey we're in the 300s now lets try to make it to 400 and its close and there it is 420 bytes passed now let's keep it going to 500 so I can copy paste this thing woweee there we go.\n" +
                "\n" +
                "Here are a few bytes TOOOOTalling 2000 bytes in total, just gotta do this a few times to get to or near 100 or a bit past it to see what will happen and if it'll properly parse the thing if given many many many bytes. \n" +
                "\n" +
                "I should be able to read where it fucks up if the payload isnt broken into pieces correctly but we'll see I guess, hey we're in the 300s now lets try to make it to 400 and its close and there it is 420 bytes passed now let's keep it going to 500 so I can copy paste this thing woweee there we go.\n" +
                "\n" +
                "Here are a few bytes TOOOOTalling 2000 bytes in total, just gotta do this a few times to get to or near 100 or a bit past it to see what will happen and if it'll properly parse the thing if given many many many bytes. \n" +
                "\n" +
                "I should be able to read where it fucks up if the payload isnt broken into pieces correctly but we'll see I guess, hey we're in the 300s now lets try to make it to 400 and its close and there it is 420 bytes passed now let's keep it going to 500 so I can copy paste this thing woweee there we go.\n" +
                "\n" +
                "Here are a few bytes TOOOOTalling 2000 bytes in total, just gotta do this a few times to get to or near 100 or a bit past it to see what will happen and if it'll properly parse the thing if given many many many bytes. \n" +
                "\n" +
                "I should be able to read where it fucks up if the payload isnt broken into pieces correctly but we'll see I guess, hey we're in the 300s now lets try to make it to 400 and its close and there it is 420 bytes passed now let's keep it going to 500 so I can copy paste this thing woweee there we go.");

    }

}
