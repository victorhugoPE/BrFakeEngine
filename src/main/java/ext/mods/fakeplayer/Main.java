package ext.mods.fakeplayer;

import io.netty.bootstrap.Bootstrap;
import io.netty.channel.*;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.SocketChannel;
import io.netty.channel.socket.nio.NioSocketChannel;
import io.netty.handler.codec.string.StringDecoder;
import io.netty.handler.codec.string.StringEncoder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.charset.StandardCharsets;
import java.util.Scanner;

// IMPORTANTE: Importar o novo Pool
import ext.mods.fakeplayer.pool.ThreadPool;

public class Main {
    private static final Logger _log = LoggerFactory.getLogger(Main.class);
    private static Channel _channel;

    public static void main(String[] args) {
        _log.info("#############################################");
        _log.info("#        BR FAKE ENGINE - STANDALONE v3.0    #");
        _log.info("#############################################");

        // 1. INICIALIZA O MOTOR DE COROUTINES
        ThreadPool.init();

        // 2. Inicia a conexão usando o pool apropriado para I/O (Rede)
        // Substitui o antigo: new Thread(() -> connectToGameServer()).start();
        ThreadPool.execute(() -> connectToGameServer());

        // Console Loop (Mantido igual)
        Scanner scanner = new Scanner(System.in);
        while (true) {
            if (scanner.hasNextLine()) {
                String line = scanner.nextLine();
                if (line.equalsIgnoreCase("exit")) {
                    ThreadPool.shutdown(); // Desliga o pool ao sair
                    System.exit(0);
                }
                if (_channel != null && _channel.isActive()) {
                    _channel.writeAndFlush(line + "\r\n");
                } else {
                    _log.warn("Nao conectado ao GameServer.");
                }
            }
        }
    }

    private static void connectToGameServer() {
        EventLoopGroup group = new NioEventLoopGroup();
        try {
            Bootstrap b = new Bootstrap();
            b.group(group)
             .channel(NioSocketChannel.class)
             .handler(new ChannelInitializer<SocketChannel>() {
                 @Override
                 public void initChannel(SocketChannel ch) {
                     ChannelPipeline p = ch.pipeline();
                     p.addLast(new StringDecoder(StandardCharsets.UTF_8));
                     p.addLast(new StringEncoder(StandardCharsets.UTF_8));
                     p.addLast(new ClientHandler());
                 }
             });

            while (true) {
                try {
                    _log.info("Tentando conectar ao GameServer (127.0.0.1:9000)...");
                    ChannelFuture f = b.connect("127.0.0.1", 9000).sync();
                    _channel = f.channel();
                    f.channel().closeFuture().sync();
                } catch (Exception e) {
                    _log.warn("Falha na conexao: " + e.getMessage());
                    _log.info("Tentando novamente em 5 segundos...");
                }
                try { Thread.sleep(5000); } catch (InterruptedException ie) {}
            }
        } finally {
            group.shutdownGracefully();
        }
    }
    
    private static class ClientHandler extends SimpleChannelInboundHandler<String> {
        @Override
        public void channelActive(ChannelHandlerContext ctx) {
            _log.info(">>> CONECTADO AO GAMESERVER COM SUCESSO! <<<");
            ctx.writeAndFlush("HELLO:FakeServer v3.0 Connected\r\n");
        }

        @Override
        public void channelInactive(ChannelHandlerContext ctx) {
            _log.warn(">>> Conexao perdida com o GameServer! <<<");
        }

        @Override
        protected void channelRead0(ChannelHandlerContext ctx, String msg) {
            _log.info("[GS enviou]: " + msg);
            if (msg.startsWith("CMD:SPAWN")) {
                _log.info("Processando Spawn... Enviando ordem de execucao para o GS.");
                ctx.writeAndFlush(msg + "\r\n"); 
            }
            else if (msg.startsWith("CMD:DELETE")) {
                _log.info("Processando Limpeza... Enviando ordem.");
                ctx.writeAndFlush(msg + "\r\n");
            }
            else if (msg.startsWith("CMD:INFO")) {
                 ctx.writeAndFlush(msg + "\r\n");
            }
        }
    }
}