package net.blueberrymc.jbsdiffPatcher;

public class PatchData {
    public final String name;
    public final String version;
    public final String vanillaUrl;
    public final byte[] vanillaHash;
    public final byte[] patchedHash;

    public PatchData(String name, String version, String vanillaUrl, String vanillaHash, String patchedHash) {
        this.name = name;
        this.version = version;
        this.vanillaUrl = vanillaUrl;
        this.vanillaHash = fromHex(vanillaHash);
        this.patchedHash = fromHex(patchedHash);
    }

    private static byte[] fromHex(final String s) {
        if (s.length() % 2 != 0) {
            throw new IllegalArgumentException("Hex " + s + " must be divisible by two");
        }
        final byte[] bytes = new byte[s.length() / 2];
        for (int i = 0; i < bytes.length; i++) {
            final char left = s.charAt(i * 2);
            final char right = s.charAt(i * 2 + 1);
            final byte b = (byte) ((getValue(left) << 4) | (getValue(right) & 0xF));
            bytes[i] = b;
        }
        return bytes;
    }

    private static int getValue(final char c) {
        int i = Character.digit(c, 16);
        if (i < 0) {
            throw new IllegalArgumentException("Invalid hex char: " + c);
        }
        return i;
    }
}
