package ext.mods.fakeplayer.network;

import io.netty.bootstrap.ServerBootstrap;
import io.netty.buffer.Unpooled;
import io.netty.channel.*;
import io.netty.channel.group.ChannelGroup;
import io.netty.channel.group.DefaultChannelGroup;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.SocketChannel;
import io.netty.channel.socket.nio.NioServerSocketChannel;
import io.netty.handler.codec.http.*;
import io.netty.handler.codec.http.websocketx.*;
import io.netty.handler.stream.ChunkedWriteHandler;
import io.netty.util.concurrent.GlobalEventExecutor;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.logging.Logger;

import ext.mods.fakeplayer.FakePlayer;
import ext.mods.fakeplayer.FakePlayerManager;
// Importe seu Enum de Goal se tiver, ex: import ext.mods.fakeplayer.ai.Goal;

public class FakeWebServer {
    private static final Logger _log = Logger.getLogger(FakeWebServer.class.getName());
    private static final int PORT = 8080;
    
    public static final ChannelGroup allChannels = new DefaultChannelGroup(GlobalEventExecutor.INSTANCE);

    public void start() {
        new Thread(() -> {
            EventLoopGroup bossGroup = new NioEventLoopGroup(1);
            EventLoopGroup workerGroup = new NioEventLoopGroup();
            try {
                ServerBootstrap b = new ServerBootstrap();
                b.group(bossGroup, workerGroup)
                 .channel(NioServerSocketChannel.class)
                 .childHandler(new ChannelInitializer<SocketChannel>() {
                     @Override
                     protected void initChannel(SocketChannel ch) {
                         ChannelPipeline p = ch.pipeline();
                         p.addLast(new HttpServerCodec());
                         p.addLast(new HttpObjectAggregator(65536));
                         p.addLast(new ChunkedWriteHandler());
                         p.addLast(new WebSocketServerProtocolHandler("/ws")); 
                         p.addLast(new WebHandler());
                     }
                 });

                Channel ch = b.bind(PORT).sync().channel();
                _log.info("[WEB] Dashboard Online: http://localhost:" + PORT);
                ch.closeFuture().sync();
            } catch (Exception e) {
                e.printStackTrace();
            } finally {
                bossGroup.shutdownGracefully();
                workerGroup.shutdownGracefully();
            }
        }).start();
    }

    public static void broadcastBotStatus(FakePlayer fp) {
        if (allChannels.isEmpty()) return;
        
        String objective = "IDLE";
        int courage = 0, vengeance = 0, farm = 0, rage = 0;
        // Se você não tiver variáveis booleanas no FakePlayer, simule ou use um Map auxiliar
        boolean party = true, trade = true, chat = false; 

        try {
            if (fp.getBrain() != null) {
                objective = fp.getBrain().getGoal().name();
                courage = fp.getBrain().getPersonality().courage;
                // vengeance = fp.getBrain().getPersonality().vengeance; // Descomente se existir
            }
        } catch (Exception e) {}

        String json = String.format(
            "{\"type\":\"UPDATE\", \"id\":%d, \"hp\":%d, \"maxHp\":%d, \"mp\":%d, \"maxMp\":%d, \"cp\":%d, \"maxCp\":%d, " +
            "\"x\":%d, \"y\":%d, \"z\":%d, \"objective\":\"%s\", " +
            "\"party\":%b, \"trade\":%b, \"chat\":%b, " +
            "\"courage\":%d, \"vengeance\":%d, \"farm\":%d, \"rage\":%d}",
            fp.getObjectId(),
            (int)fp.getStatus().getHp(), fp.getStatus().getMaxHp(),
            (int)fp.getStatus().getMp(), fp.getStatus().getMaxMp(),
            (int)fp.getStatus().getCp(), fp.getStatus().getMaxCp(),
            fp.getX(), fp.getY(), fp.getZ(),
            objective, party, trade, chat, courage, vengeance, farm, rage
        );
        allChannels.writeAndFlush(new TextWebSocketFrame(json));
    }

    private static class WebHandler extends SimpleChannelInboundHandler<Object> {
        @Override
        protected void channelRead0(ChannelHandlerContext ctx, Object msg) throws Exception {
            if (msg instanceof FullHttpRequest) handleHttpRequest(ctx, (FullHttpRequest) msg);
            else if (msg instanceof WebSocketFrame) handleWebSocketFrame(ctx, (WebSocketFrame) msg);
        }

        private void handleHttpRequest(ChannelHandlerContext ctx, FullHttpRequest req) {
            String uri = req.uri();
            if (uri.equals("/")) uri = "/index.html";
            if (uri.contains("?")) uri = uri.substring(0, uri.indexOf("?"));
            
            String resourcePath = "web" + uri;
            InputStream is = getClass().getClassLoader().getResourceAsStream(resourcePath);
            if (is == null) { sendResponse(ctx, req, new DefaultFullHttpResponse(HttpVersion.HTTP_1_1, HttpResponseStatus.NOT_FOUND)); return; }

            try {
                byte[] content = is.readAllBytes();
                is.close();
                String ct = uri.endsWith(".css") ? "text/css" : "text/html";
                
                if (ct.equals("text/html") && uri.equals("/index.html")) {
                    String html = new String(content, StandardCharsets.UTF_8);
                    html = renderHostList(html); // Método de renderização da lista (recuperar do histórico se necessário)
                    content = html.getBytes(StandardCharsets.UTF_8);
                }

                FullHttpResponse res = new DefaultFullHttpResponse(HttpVersion.HTTP_1_1, HttpResponseStatus.OK, Unpooled.copiedBuffer(content));
                res.headers().set(HttpHeaderNames.CONTENT_TYPE, ct);
                HttpUtil.setContentLength(res, content.length);
                sendResponse(ctx, req, res);
            } catch (Exception e) {}
        }
        
        // RECUPERANDO O MÉTODO DA LISTA PARA NÃO FICAR VAZIO
        private String renderHostList(String html) {
            StringBuilder rows = new StringBuilder();
            for (FakePlayer fp : FakePlayerManager.getInstance().getFakePlayers()) {
                 rows.append(String.format("<tr><td><span class='status-dot online'></span>ONLINE</td><td class='mono'>#%d (%s)</td><td>%s</td><td>%d</td><td>%s</td><td class='mono'>%d, %d, %d</td><td>%s</td><td><button class='mini-btn' onclick=\"window.location='profile.html?id=%d'\">VIEW</button></td></tr>",
                 fp.getObjectId(), fp.getName(), fp.getClassId().name(), fp.getLevel(), (fp.getClan()!=null?fp.getClan().getName():"-"), fp.getX(), fp.getY(), fp.getZ(), (fp.getBrain()!=null?fp.getBrain().getGoal().name():"IDLE"), fp.getObjectId()));
            }
            return html.replace("{{FAKE_LIST}}", rows.toString());
        }

        private void sendResponse(ChannelHandlerContext ctx, FullHttpRequest req, FullHttpResponse res) {
            ChannelFuture f = ctx.writeAndFlush(res);
            if (!HttpUtil.isKeepAlive(req) || res.status().code() != 200) f.addListener(ChannelFutureListener.CLOSE);
        }

        private void handleWebSocketFrame(ChannelHandlerContext ctx, WebSocketFrame frame) {
            if (frame instanceof TextWebSocketFrame) {
                String text = ((TextWebSocketFrame) frame).text();
                if (text.startsWith("REQUEST_DATA")) allChannels.add(ctx.channel());
                else if (text.startsWith("CMD:")) processCommand(text);
            }
        }
        
        // === AQUI A MÁGICA DOS BOTÕES ACONTECE ===
        private void processCommand(String cmd) {
            try {
                String[] parts = cmd.split(":");
                int objId = Integer.parseInt(parts[1]);
                String type = parts[2];
                String key = parts[3];
                int val = Integer.parseInt(parts[4]);
                
                FakePlayer fp = FakePlayerManager.getInstance().getFakePlayer(objId);
                if (fp == null) return;

                if (type.equals("SET_OBJ")) {
                    // SEU CÓDIGO JAVA PARA MUDAR OBJETIVO
                    // Exemplo: fp.getBrain().setGoal(Goal.valueOf(key));
                    _log.info("CMD: Mudando objetivo de " + fp.getName() + " para " + key);
                }
                else if (type.equals("PARAM")) {
                    if (key.equals("COURAGE")) fp.getBrain().getPersonality().courage += val;
                    // Proteção para não passar de 100 ou 0
                    if (fp.getBrain().getPersonality().courage > 100) fp.getBrain().getPersonality().courage = 100;
                    if (fp.getBrain().getPersonality().courage < 0) fp.getBrain().getPersonality().courage = 0;
                    
                    _log.info("CMD: Parametro " + key + " alterado para " + fp.getBrain().getPersonality().courage);
                }
                
                // IMPORTANTE: Manda o status novo IMEDIATAMENTE para o site atualizar
                broadcastBotStatus(fp);

            } catch (Exception e) { e.printStackTrace(); }
        }
    }
}