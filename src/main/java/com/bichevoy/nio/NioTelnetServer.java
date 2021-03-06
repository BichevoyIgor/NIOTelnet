package com.bichevoy.nio;

import java.io.File;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.SocketAddress;
import java.net.URI;
import java.nio.ByteBuffer;
import java.nio.channels.SelectionKey;
import java.nio.channels.Selector;
import java.nio.channels.ServerSocketChannel;
import java.nio.channels.SocketChannel;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;

public class NioTelnetServer {
    public static final String LS_COMMAND = "\tls   view all files and directories\n";
    public static final String MKDIR_COMMAND = "\tmkdir   create directory\n";
    public static final String CHANGE_NICKNAME = "\tnick   change nickname\n";
    private static final String ROOT_NOTIFICATION = "You are alredy in the root directory\n\n";
    private static final String ROOT_PATH = "server";
    private static final String DIRECTORY_DOESNT_EXIST = "Directory %s doesn't exist\n\n";

    private Path currentPath = Path.of("server");
    private Map<SocketAddress, String> clients = new HashMap<>();
    private final ByteBuffer buffer = ByteBuffer.allocate(512);

    public NioTelnetServer() throws IOException {
        ServerSocketChannel server = ServerSocketChannel.open();
        server.bind(new InetSocketAddress(5678));
        server.configureBlocking(false);
        Selector selector = Selector.open();

        server.register(selector, SelectionKey.OP_ACCEPT);
        System.out.println("Server started");

        while (server.isOpen()) {
            selector.select();
            var seletionKeys = selector.selectedKeys();
            var iterator = seletionKeys.iterator();

            while (iterator.hasNext()) {
                var key = iterator.next();
                if (key.isAcceptable()) {
                    handleAccept(key, selector);
                } else if (key.isReadable()) {
                    handleRead(key, selector);
                }
                iterator.remove();
            }
        }

    }

    private void handleRead(SelectionKey key, Selector selector) throws IOException {
        String nickName = "";

        SocketChannel channel = ((SocketChannel) key.channel());
        SocketAddress client = channel.getRemoteAddress();
        int readBytes = channel.read(buffer);
        if (readBytes < 0) {
            channel.close();
            return;
        } else if (readBytes == 0) {
            return;
        }
        buffer.flip();
        StringBuilder sb = new StringBuilder();
        while (buffer.hasRemaining()) {
            sb.append((char) buffer.get());
        }
        buffer.clear();

        // TODO
        // touch [filename] - ???????????????? ??????????
        // mkdir [dirname] - ???????????????? ????????????????????
        // cd [path] - ?????????????????????? ???? ???????????????? (.. | ~ )
        // rm [filename | dirname] - ???????????????? ?????????? ?????? ??????????
        // copy [src] [target] - ?????????????????????? ?????????? ?????? ??????????
        // cat [filename] - ???????????????? ??????????????????????

        if (key.isValid()) {
        String command = sb.toString().replace("\r", "").replace("\n","");

            if ("--help".equals(command)){
                sendMessage(LS_COMMAND, selector, client);
                sendMessage(MKDIR_COMMAND, selector, client);
                sendMessage(CHANGE_NICKNAME, selector, client);
            }else if ("ls".equals(command)){
                sendMessage(getFileList().concat("\n"), selector, client);
            }else if (command.startsWith("nick ")){
                nickName = command.split(" ")[1];
                clients.put(channel.getRemoteAddress(), nickName);
                System.out.println("Client - " + channel.getRemoteAddress().toString() + "changed nickname on " + nickName);
                System.out.println(clients);
            }else if (command.startsWith("cd ")){
                String neededPathString = command.split(" ")[1];
                Path tempPath = Path.of(currentPath.toString(), neededPathString);
                if ("..".equals(neededPathString)){
                    tempPath = currentPath.getParent();
                    if (tempPath == null || !tempPath.toString().startsWith("server")){
                        sendMessage(ROOT_NOTIFICATION, selector,client);
                    }else {
                        currentPath = tempPath;
                    }
                }else if ("~".equals(neededPathString)) {
                    currentPath = Path.of(ROOT_PATH);
                }else {
                    if(tempPath.toFile().exists()){
                        currentPath = tempPath;
                    }else {
                        sendMessage(String.format(DIRECTORY_DOESNT_EXIST, neededPathString), selector, client);
                    }
                }
            } else if ("exit".equals(command)){
                System.out.println("Client logged out. IP: " + channel.getRemoteAddress());
                channel.close();
                return;
            }
        }
        sendName(channel, nickName);
    }

    private void sendName(SocketChannel channel, String nickName) throws IOException {
        if (nickName.isEmpty()){
            nickName = clients.getOrDefault(channel.getRemoteAddress(), channel.getRemoteAddress().toString());
        }
        String curentPathString = currentPath.toString().replace("server", "~");
        channel.write(ByteBuffer.wrap(nickName.concat(">:").concat(curentPathString).concat("$ ").getBytes(StandardCharsets.UTF_8)
        ));
    }

    private String getFileList() {
        return String.join(" ", new File(currentPath.toString()).list());
    }

    private void sendMessage(String message, Selector selector, SocketAddress client) throws IOException {
        for (SelectionKey key : selector.keys()){
            if (key.isValid() && key.channel() instanceof  SocketChannel){
                if (((SocketChannel)key.channel()).getRemoteAddress().equals(client)){
                    ((SocketChannel)key.channel()).write(ByteBuffer.wrap(message.getBytes(StandardCharsets.UTF_8)));
                }
            }
        }
    }


    private void handleAccept(SelectionKey key, Selector selector) throws IOException {
        SocketChannel channel = ((ServerSocketChannel) key.channel()).accept();
        channel.configureBlocking(false);
        System.out.println("Client accepted. IP: " + channel.getRemoteAddress());

        channel.register(selector, SelectionKey.OP_READ, "some attach");
        channel.write(ByteBuffer.wrap("Hello user\n".getBytes(StandardCharsets.UTF_8)));
        channel.write(ByteBuffer.wrap("Enter -- help for support info\n".getBytes(StandardCharsets.UTF_8)));
        sendName(channel, "");

    }

    public static void main(String[] args) throws IOException {
        new NioTelnetServer();
    }

}
