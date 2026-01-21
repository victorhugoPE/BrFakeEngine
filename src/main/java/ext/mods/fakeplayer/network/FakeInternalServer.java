package ext.mods.fakeplayer.network;

import io.netty.bootstrap.ServerBootstrap;
import io.netty.channel.*;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.SocketChannel;
import io.netty.channel.socket.nio.NioServerSocketChannel;
import io.netty.handler.codec.string.StringDecoder;
import io.netty.handler.codec.string.StringEncoder;

import java.nio.charset.StandardCharsets;
import java.util.logging.Logger;

import ext.mods.fakeplayer.FakeSpawner;
import ext.mods.fakeplayer.FakePlayerManager;

/**
 * Servidor Netty Interno (Agora roda dentro do Plugin).
 * Escuta na porta 9000 por comandos vindos do Console Externo.
 */
public class FakeInternalServer {
    private static final Logger _log = Logger.getLogger(FakeInternalServer.class.getName());
    private static FakeInternalServer _instance;
    
    private EventLoopGroup _bossGroup;
    private EventLoopGroup _workerGroup;
    private Channel _serverChannel;
    
    // O canal ativo do FakeServer (só aceitamos um cérebro por vez)
    private Channel _activeBrainSession;

    public static FakeInternalServer getInstance() {
        if (_instance == null) _instance = new FakeInternalServer();
        return _instance;
    }

    public void start() {
        _log.info("[FakePlugin] Iniciando servico de escuta na porta 9000...");
        
        _bossGroup = new NioEventLoopGroup(1);
        _workerGroup = new NioEventLoopGroup(1);

        try {
            ServerBootstrap b = new ServerBootstrap();
            b.group(_bossGroup, _workerGroup)
             .channel(NioServerSocketChannel.class)
             .childHandler(new ChannelInitializer<SocketChannel>() {
                 @Override
                 public void initChannel(SocketChannel ch) {
                     ChannelPipeline p = ch.pipeline();
                     // Decodifica tudo para String (Texto Simples) para facilitar debug
                     p.addLast(new StringDecoder(StandardCharsets.UTF_8));
                     p.addLast(new StringEncoder(StandardCharsets.UTF_8));
                     p.addLast(new FakeServerHandler());
                 }
             });

            // Bind na porta 9000
            ChannelFuture f = b.bind(9000).sync();
            _serverChannel = f.channel();
            _log.info("[FakePlugin] ESCUTANDO! Aguardando o Cerebro conectar...");

        } catch (Exception e) {
            _log.severe("[FakePlugin] Erro ao abrir porta 9000: " + e.getMessage());
        }
    }

    public void shutdown() {
        if (_bossGroup != null) _bossGroup.shutdownGracefully();
        if (_workerGroup != null) _workerGroup.shutdownGracefully();
    }
    
    // Método para mandar ordens de volta para o FakeServer (Ex: CMD:REGISTER)
    public void sendPacket(String msg) {
        if (_activeBrainSession != null && _activeBrainSession.isActive()) {
            _activeBrainSession.writeAndFlush(msg + "\n");
        }
    }

    public boolean isBrainConnected() {
        return _activeBrainSession != null && _activeBrainSession.isActive();
    }

    // Handler interno para processar eventos de rede
    private class FakeServerHandler extends SimpleChannelInboundHandler<String> {
        @Override
        public void channelActive(ChannelHandlerContext ctx) {
            _log.info("[FakePlugin] !!! CEREBRO CONECTADO !!! IP: " + ctx.channel().remoteAddress());
            _activeBrainSession = ctx.channel();
            ctx.writeAndFlush("AUTH_OK:GameServer Ready\n");
        }

        @Override
        public void channelInactive(ChannelHandlerContext ctx) {
            _log.warning("[FakePlugin] O Cerebro desconectou!");
            _activeBrainSession = null;
        }

        @Override
        protected void channelRead0(ChannelHandlerContext ctx, String msg) {
            // CORREÇÃO: Divide mensagens coladas (Sticky Packets)
            String[] commands = msg.split("\\r?\\n");
            
            for (String command : commands) {
                if (command.trim().isEmpty()) continue;
                try {
                    processCommand(ctx, command.trim());
                } catch (Exception e) {
                    _log.severe("[FakePlugin] Erro ao processar linha '" + command + "': " + e.getMessage());
                }
            }
        }

        // Método separado para processar CADA comando individualmente
        private void processCommand(ChannelHandlerContext ctx, String msg) {
            String[] args = msg.split(":");
            if (args.length < 2) return;

            String header = args[0]; 
            String action = args[1];

            if (header.equals("CMD")) {
                if (action.equals("SPAWN")) {
                    if (args.length >= 6 && args[2].equals("SOLO")) {
                        try {
                            int x = Integer.parseInt(args[3]);
                            int y = Integer.parseInt(args[4]);
                            int z = Integer.parseInt(args[5]);
                            
                            _log.info("[FakePlugin] Processando Spawn: " + x + " " + y + " " + z);
                            FakeSpawner.performSpawn(x, y, z);
                        } catch (NumberFormatException e) {
                            _log.warning("[FakePlugin] Erro de formato de numero no spawn: " + msg);
                        }
                    }
                }
                else if (action.equals("DELETE_ALL")) {
                    _log.info("[FakePlugin] Solicitacao de Delete All recebida via Rede.");
                    FakePlayerManager.getInstance().despawnAll();
                }
                else if (action.equals("INFO")) {
                     int count = FakePlayerManager.getInstance().getFakePlayersCount();
                     sendPacket("INFO:Total Fakes Online: " + count);
                }
            }
        }
    }
}