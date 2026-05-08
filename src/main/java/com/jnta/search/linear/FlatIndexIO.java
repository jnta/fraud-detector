package com.jnta.search.linear;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.channels.FileChannel;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;

public class FlatIndexIO {
    public static final int MAGIC = 0x52415A52; // "RAZR"
    
    public static void save(int size, short[][] blockA, short[][] blockB, boolean[] labels, float min, float max, Path path) throws IOException {
        try (FileChannel channel = FileChannel.open(path, StandardOpenOption.CREATE, StandardOpenOption.WRITE, StandardOpenOption.TRUNCATE_EXISTING)) {
            // Header: Magic(4), Size(4), Min(4), Max(4) = 16 bytes
            ByteBuffer header = ByteBuffer.allocate(16).order(ByteOrder.LITTLE_ENDIAN);
            header.putInt(MAGIC);
            header.putInt(size);
            header.putFloat(min);
            header.putFloat(max);
            header.flip();
            channel.write(header);

            // Block A: 6 dims * size * 2 bytes
            ByteBuffer buffer = ByteBuffer.allocate(65536).order(ByteOrder.LITTLE_ENDIAN);
            for (short[] dim : blockA) {
                writeDim(channel, buffer, dim);
            }

            // Block B: 8 dims * size * 2 bytes
            for (short[] dim : blockB) {
                writeDim(channel, buffer, dim);
            }

            // Labels packed as bits
            buffer.clear();
            for (int i = 0; i < size; i += 8) {
                byte b = 0;
                for (int j = 0; j < 8 && (i + j) < size; j++) {
                    if (labels[i + j]) {
                        b |= (1 << j);
                    }
                }
                if (!buffer.hasRemaining()) {
                    buffer.flip();
                    channel.write(buffer);
                    buffer.clear();
                }
                buffer.put(b);
            }
            buffer.flip();
            channel.write(buffer);
        }
    }

    private static void writeDim(FileChannel channel, ByteBuffer buffer, short[] dim) throws IOException {
        for (short s : dim) {
            if (!buffer.hasRemaining()) {
                buffer.flip();
                channel.write(buffer);
                buffer.clear();
            }
            buffer.putShort(s);
        }
        buffer.flip();
        channel.write(buffer);
        buffer.clear();
    }
}
