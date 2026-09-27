package net.tfminecraft.cooking.husbandry;

import java.io.ByteArrayInputStream;
import java.io.DataInputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.zip.GZIPInputStream;
import java.util.zip.InflaterInputStream;

/**
 * Reads saved entity chunks ({@code entities/r.X.Z.mca}) to find where unloaded animals are.
 * Runs off the main thread; it only reads files.
 */
final class HusbandryEntityScan {

    record WorldDir(String world, File entities) {}

    record Found(String world, int x, int y, int z) {}

    /** {@code complete} is false when some chunk could not be read, so a missing animal may still exist. */
    record Result(Map<UUID, Found> found, boolean complete) {}

    private static final Pattern REGION = Pattern.compile("r\\.(-?\\d+)\\.(-?\\d+)\\.mca");
    private static final int SECTOR = 4096;

    private HusbandryEntityScan() {}

    static Result scan(List<WorldDir> worlds, Set<UUID> targets) {
        Map<UUID, Found> found = new HashMap<>();
        boolean complete = true;
        if (targets.isEmpty()) {
            return new Result(found, true);
        }
        for (WorldDir world : worlds) {
            File[] files = world.entities().listFiles();
            if (files == null) {
                continue;
            }
            for (File file : files) {
                Matcher name = REGION.matcher(file.getName());
                if (!name.matches()) {
                    continue;
                }
                int regionX = Integer.parseInt(name.group(1));
                int regionZ = Integer.parseInt(name.group(2));
                complete &= scanRegion(world, file, regionX, regionZ, targets, found);
            }
        }
        return new Result(found, complete);
    }

    private static boolean scanRegion(
            WorldDir world, File file, int regionX, int regionZ, Set<UUID> targets, Map<UUID, Found> found) {
        byte[] data;
        try {
            data = Files.readAllBytes(file.toPath());
        } catch (IOException ex) {
            return false;
        }
        if (data.length < SECTOR * 2) {
            return true;
        }
        boolean complete = true;
        for (int index = 0; index < 1024; index++) {
            int entry = readInt(data, index * 4);
            int offset = (entry >>> 8) * SECTOR;
            if (offset == 0) {
                continue;
            }
            try {
                byte[] chunk = chunkBytes(world, data, offset, regionX * 32 + index % 32, regionZ * 32 + index / 32);
                if (chunk == null) {
                    complete = false;
                    continue;
                }
                readEntityChunk(new DataInputStream(new ByteArrayInputStream(chunk)), world.world(), targets, found);
            } catch (IOException | RuntimeException ex) {
                complete = false;
            }
        }
        return complete;
    }

    private static byte[] chunkBytes(WorldDir world, byte[] data, int offset, int chunkX, int chunkZ)
            throws IOException {
        if (offset + 5 > data.length) {
            return null;
        }
        int length = readInt(data, offset);
        int compression = data[offset + 4] & 0xFF;
        byte[] raw;
        if ((compression & 0x80) != 0) {
            compression &= 0x7F;
            raw = Files.readAllBytes(new File(world.entities(), "c." + chunkX + "." + chunkZ + ".mcc").toPath());
        } else {
            if (length < 1 || offset + 4 + length > data.length) {
                return null;
            }
            raw = new byte[length - 1];
            System.arraycopy(data, offset + 5, raw, 0, raw.length);
        }
        InputStream in = switch (compression) {
            case 1 -> new GZIPInputStream(new ByteArrayInputStream(raw));
            case 2 -> new InflaterInputStream(new ByteArrayInputStream(raw));
            case 3 -> new ByteArrayInputStream(raw);
            default -> null;
        };
        if (in == null) {
            return null;
        }
        try (in) {
            return in.readAllBytes();
        }
    }

    private static int readInt(byte[] data, int at) {
        return ((data[at] & 0xFF) << 24) | ((data[at + 1] & 0xFF) << 16)
                | ((data[at + 2] & 0xFF) << 8) | (data[at + 3] & 0xFF);
    }

    // ----------------------------------------------------------------------
    //  NBT: only Entities[].UUID, Pos and Passengers are read, the rest is skipped
    // ----------------------------------------------------------------------

    private static final byte END = 0;
    private static final byte DOUBLE = 6;
    private static final byte LIST = 9;
    private static final byte COMPOUND = 10;
    private static final byte INT_ARRAY = 11;

    private static void readEntityChunk(DataInputStream in, String world, Set<UUID> targets, Map<UUID, Found> found)
            throws IOException {
        if (in.readByte() != COMPOUND) {
            return;
        }
        in.readUTF();
        byte type;
        while ((type = in.readByte()) != END) {
            String key = in.readUTF();
            if (type == LIST && key.equals("Entities")) {
                readEntityList(in, world, targets, found);
            } else {
                skip(in, type);
            }
        }
    }

    private static void readEntityList(DataInputStream in, String world, Set<UUID> targets, Map<UUID, Found> found)
            throws IOException {
        byte element = in.readByte();
        int size = in.readInt();
        if (element != COMPOUND) {
            for (int i = 0; i < size; i++) {
                skip(in, element);
            }
            return;
        }
        for (int i = 0; i < size; i++) {
            readEntity(in, world, targets, found);
        }
    }

    private static void readEntity(DataInputStream in, String world, Set<UUID> targets, Map<UUID, Found> found)
            throws IOException {
        UUID uuid = null;
        double[] pos = null;
        byte type;
        while ((type = in.readByte()) != END) {
            String key = in.readUTF();
            if (type == INT_ARRAY && key.equals("UUID")) {
                int size = in.readInt();
                int[] parts = new int[size];
                for (int i = 0; i < size; i++) {
                    parts[i] = in.readInt();
                }
                if (size == 4) {
                    uuid = new UUID(
                            ((long) parts[0] << 32) | (parts[1] & 0xFFFFFFFFL),
                            ((long) parts[2] << 32) | (parts[3] & 0xFFFFFFFFL));
                }
            } else if (type == LIST && key.equals("Pos")) {
                byte element = in.readByte();
                int size = in.readInt();
                if (element == DOUBLE && size == 3) {
                    pos = new double[] {in.readDouble(), in.readDouble(), in.readDouble()};
                } else {
                    for (int i = 0; i < size; i++) {
                        skip(in, element);
                    }
                }
            } else if (type == LIST && key.equals("Passengers")) {
                readEntityList(in, world, targets, found);
            } else {
                skip(in, type);
            }
        }
        if (uuid != null && pos != null && targets.contains(uuid)) {
            found.putIfAbsent(uuid, new Found(
                    world, (int) Math.floor(pos[0]), (int) Math.floor(pos[1]), (int) Math.floor(pos[2])));
        }
    }

    private static void skip(DataInputStream in, byte type) throws IOException {
        switch (type) {
            case 1 -> in.skipNBytes(1);
            case 2 -> in.skipNBytes(2);
            case 3, 5 -> in.skipNBytes(4);
            case 4, 6 -> in.skipNBytes(8);
            case 7 -> in.skipNBytes(in.readInt());
            case 8 -> in.skipNBytes(in.readUnsignedShort());
            case 9 -> {
                byte element = in.readByte();
                int size = in.readInt();
                for (int i = 0; i < size; i++) {
                    skip(in, element);
                }
            }
            case 10 -> {
                byte inner;
                while ((inner = in.readByte()) != END) {
                    in.skipNBytes(in.readUnsignedShort());
                    skip(in, inner);
                }
            }
            case 11 -> in.skipNBytes(4L * in.readInt());
            case 12 -> in.skipNBytes(8L * in.readInt());
            default -> throw new IOException("Unknown NBT tag " + type);
        }
    }
}
