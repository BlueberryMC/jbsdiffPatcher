package net.blueberrymc.jbsdiffPatcher;

import java.io.FileOutputStream;
import java.io.IOException;
import java.net.URL;
import java.nio.channels.Channels;
import java.nio.channels.FileChannel;
import java.nio.channels.ReadableByteChannel;
import java.nio.file.Path;

public class FileDownload {
    public static void download(Path path, String url) {
        try {
            ReadableByteChannel readableByteChannel = Channels.newChannel(new URL(url).openStream());
            FileOutputStream out = new FileOutputStream(path.toFile());
            FileChannel fileChannel = out.getChannel();
            fileChannel.transferFrom(readableByteChannel, 0, Long.MAX_VALUE);
        } catch (IOException e) {
            System.err.println("Failed to download " + url + " into " + path);
            e.printStackTrace();
            System.exit(1);
        }
    }
}
