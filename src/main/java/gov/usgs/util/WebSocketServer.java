package gov.usgs.util;


import io.netty.bootstrap.ServerBootstrap;
import io.netty.channel.Channel;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.ChannelPipeline;
import io.netty.channel.EventLoopGroup;
import io.netty.channel.SimpleChannelInboundHandler;
import io.netty.channel.group.ChannelGroup;
import io.netty.channel.group.DefaultChannelGroup;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.SocketChannel;
import io.netty.channel.socket.nio.NioServerSocketChannel;
import io.netty.handler.codec.http.HttpObjectAggregator;
import io.netty.handler.codec.http.HttpServerCodec;
import io.netty.handler.codec.http.websocketx.TextWebSocketFrame;
import io.netty.handler.codec.http.websocketx.WebSocketFrame;
import io.netty.handler.codec.http.websocketx.WebSocketServerProtocolHandler;
import io.netty.handler.codec.http.websocketx.extensions.compression.WebSocketServerCompressionHandler;
import io.netty.handler.logging.LogLevel;
import io.netty.handler.logging.LoggingHandler;
import io.netty.util.concurrent.GlobalEventExecutor;

/**
 * Broadcast websocket server.
 */
public class WebSocketServer extends SimpleChannelInboundHandler<WebSocketFrame> implements Runnable {

    /** List of connections. */
    protected ChannelGroup allChannels = new DefaultChannelGroup(GlobalEventExecutor.INSTANCE);

    /** Group for accepting connections. */
    protected EventLoopGroup bossGroup = null;
    /** Group for processing connections. */
    protected EventLoopGroup workerGroup = null;

    /** Port to run server. */
    private int port;


    /**
     * Construct a new server.
     *
     * @param port port to bind.
     */
    public WebSocketServer(final int port) {
        this.port = port;
    }

    /**
     * Broadcast a message to all connected clients.
     *
     * @param message message to send.
     */
    public void broadcast(final String message) {
        final TextWebSocketFrame frame = new TextWebSocketFrame(message);
        allChannels.stream().forEach(c -> c.writeAndFlush(frame));
    }

    /**
     * New connection with server is ready.
     */
    @Override
    public void channelActive(ChannelHandlerContext ctx) throws Exception {
        allChannels.add(ctx.channel());
    }

    /**
     * Receive and process messages from clients.
     */
    @Override
    protected void channelRead0(ChannelHandlerContext ctx, WebSocketFrame frame) throws Exception {
        // Don't allow messages from clients.
        throw new UnsupportedOperationException("This websocket is broadcast only");
    }

    /**
     * Set up channel pipeline for a new client.
     * 
     * Implements initChannel for ChannelInitializer<SocketChannel>.
     *
     * @param channel channel to be initialized.
     */
    protected void initChannel(SocketChannel channel) throws Exception {
        final ChannelPipeline pipeline = channel.pipeline();

        pipeline.addLast(new HttpServerCodec())
                .addLast(new HttpObjectAggregator(65536))
                .addLast(new WebSocketServerCompressionHandler())
                .addLast(new WebSocketServerProtocolHandler("/", null, true))
                .addLast(this);
    }

    /**
     * Run server in foreground.
     *
     * Blocks until server is stopped.
     *
     * @see #start()
     */
    public void run() {
        final WebSocketServer self = this;

        bossGroup = new NioEventLoopGroup(1);
        workerGroup = new NioEventLoopGroup();
        try {
            ServerBootstrap b = new ServerBootstrap();
            b.group(bossGroup, workerGroup)
                    .channel(NioServerSocketChannel.class)
                    .handler(new LoggingHandler(LogLevel.INFO))
                    .childHandler(new ChannelInitializer<SocketChannel>() {
                        @Override
                        protected void initChannel(SocketChannel ch) throws Exception {
                            self.initChannel(ch);
                        }
                    });

            Channel ch = b.bind(port).sync().channel();
            ch.closeFuture().sync();
        } catch (Exception e) {
            e.printStackTrace();
        } finally {
            stop();
        }
    }

    /**
     * Run WebSocketServer in background thread.
     */
    public void start() {
        if (bossGroup != null || workerGroup != null) {
            // already started
            return;
        }
        new Thread(this).start();
    }

    /**
     * Stop WebSocketServer in background thread.
     */
    public void stop() {
        if (bossGroup != null) {
            bossGroup.shutdownGracefully();
            bossGroup = null;
        }
        if (workerGroup != null) {
            workerGroup.shutdownGracefully();
            workerGroup = null;
        }
    }

}
