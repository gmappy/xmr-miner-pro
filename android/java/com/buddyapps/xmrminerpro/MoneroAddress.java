package com.buddyapps.xmrminerpro;

import java.util.Arrays;

/**
 * Validation for public Monero addresses (standard and subaddress).
 * A Monero address is 95 base58 characters: version + spend key + view key + 4-byte checksum.
 * The checksum is the first 4 bytes of Keccak-256 over the first 65 decoded bytes.
 */
final class MoneroAddress {
    private static final String ALPHABET =
            "123456789ABCDEFGHJKLMNPQRSTUVWXYZabcdefghijkmnopqrstuvwxyz";
    private static final int DECODED_LEN = 69;
    private static final int PAYLOAD_LEN = 65;

    private MoneroAddress() {}

    static boolean isValid(String address) {
        if (address == null) return false;
        String a = address.trim();
        if (a.length() != 95) return false;

        byte[] decoded;
        try {
            decoded = decodeBase58(a);
        } catch (RuntimeException e) {
            return false;
        }
        if (decoded == null || decoded.length != DECODED_LEN) return false;

        int version = decoded[0] & 0xff;
        if (version != 0x12 && version != 0x50 && version != 0x13 && version != 0x53) return false;

        byte[] hash = Keccak256.hash(Arrays.copyOfRange(decoded, 0, PAYLOAD_LEN));
        for (int i = 0; i < 4; i++) {
            if (hash[i] != decoded[PAYLOAD_LEN + i]) return false;
        }
        return true;
    }

    static String describe(String address) {
        if (address == null || address.trim().isEmpty()) return "Enter your public wallet address";
        String a = address.trim();
        if (a.length() != 95) return "A Monero address is 95 characters";
        if (!(a.startsWith("4") || a.startsWith("8"))) return "Monero addresses start with 4 or 8";
        return isValid(a) ? "Address looks valid" : "Address checksum failed – check for typos";
    }

    /** Monero's block-based base58: 11 chars -> 8 bytes, final 7 chars -> 5 bytes. */
    private static byte[] decodeBase58(String input) {
        int fullBlocks = input.length() / 11;
        int lastChars = input.length() % 11;
        int lastBytes = LAST_BYTES[lastChars];
        if (lastBytes < 0) throw new IllegalArgumentException("bad base58 length");

        byte[] out = new byte[fullBlocks * 8 + lastBytes];
        for (int b = 0; b < fullBlocks; b++) {
            decodeBlock(input, b * 11, 11, out, b * 8, 8);
        }
        if (lastChars > 0) {
            decodeBlock(input, fullBlocks * 11, lastChars, out, fullBlocks * 8, lastBytes);
        }
        return out;
    }

    private static final int[] LAST_BYTES = {-1, -1, 1, 2, -1, 3, 4, 5, -1, 6, 7, 8};

    private static void decodeBlock(String input, int from, int chars, byte[] out, int outAt, int outLen) {
        long value = 0;
        long order = 1;
        for (int i = chars - 1; i >= 0; i--) {
            int digit = ALPHABET.indexOf(input.charAt(from + i));
            if (digit < 0) throw new IllegalArgumentException("bad base58 char");
            value += digit * order;
            order *= 58;
        }
        if (outLen < 8 && value >= (1L << (8L * outLen))) {
            throw new IllegalArgumentException("base58 block overflow");
        }
        for (int i = outLen - 1; i >= 0; i--) {
            out[outAt + i] = (byte) (value & 0xff);
            value >>>= 8;
        }
    }

    /** Minimal Keccak-256 (original padding, not SHA3-256). */
    private static final class Keccak256 {
        private static final long[] RC = {
            0x0000000000000001L, 0x0000000000008082L, 0x800000000000808AL, 0x8000000080008000L,
            0x000000000000808BL, 0x0000000080000001L, 0x8000000080008081L, 0x8000000000008009L,
            0x000000000000008AL, 0x0000000000000088L, 0x0000000080008009L, 0x000000008000000AL,
            0x000000008000808BL, 0x800000000000008BL, 0x8000000000008089L, 0x8000000000008003L,
            0x8000000000008002L, 0x8000000000000080L, 0x000000000000800AL, 0x800000008000000AL,
            0x8000000080008081L, 0x8000000000008080L, 0x0000000080000001L, 0x8000000080008008L
        };
        private static final int[][] ROT = {
            {0, 36, 3, 41, 18},
            {1, 44, 10, 45, 2},
            {62, 6, 43, 15, 61},
            {28, 55, 25, 21, 56},
            {27, 20, 39, 8, 14}
        };
        private static final int RATE = 136;

        static byte[] hash(byte[] in) {
            long[] a = new long[25];
            int off = 0;
            while (in.length - off >= RATE) {
                for (int i = 0; i < RATE; i++) {
                    a[i / 8] ^= ((long) (in[off + i] & 0xff)) << ((i % 8) * 8);
                }
                permute(a);
                off += RATE;
            }
            byte[] block = new byte[RATE];
            int rem = in.length - off;
            System.arraycopy(in, off, block, 0, rem);
            block[rem] = 0x01;              // Keccak padding (not SHA3's 0x06)
            block[RATE - 1] ^= 0x80;
            for (int i = 0; i < RATE; i++) {
                a[i / 8] ^= ((long) (block[i] & 0xff)) << ((i % 8) * 8);
            }
            permute(a);

            byte[] out = new byte[32];
            for (int i = 0; i < 32; i++) out[i] = (byte) (a[i / 8] >>> ((i % 8) * 8));
            return out;
        }

        private static void permute(long[] a) {
            long[] b = new long[25];
            long[] c = new long[5];
            long[] d = new long[5];
            for (int round = 0; round < 24; round++) {
                for (int x = 0; x < 5; x++) {
                    c[x] = a[x] ^ a[x + 5] ^ a[x + 10] ^ a[x + 15] ^ a[x + 20];
                }
                for (int x = 0; x < 5; x++) {
                    d[x] = c[(x + 4) % 5] ^ rotl(c[(x + 1) % 5], 1);
                }
                for (int x = 0; x < 5; x++) {
                    for (int y = 0; y < 5; y++) a[x + 5 * y] ^= d[x];
                }
                for (int x = 0; x < 5; x++) {
                    for (int y = 0; y < 5; y++) {
                        b[y + 5 * ((2 * x + 3 * y) % 5)] = rotl(a[x + 5 * y], ROT[x][y]);
                    }
                }
                for (int x = 0; x < 5; x++) {
                    for (int y = 0; y < 5; y++) {
                        a[x + 5 * y] = b[x + 5 * y] ^
                            ((~b[(x + 1) % 5 + 5 * y]) & b[(x + 2) % 5 + 5 * y]);
                    }
                }
                a[0] ^= RC[round];
            }
        }

        private static long rotl(long v, int n) {
            return n == 0 ? v : (v << n) | (v >>> (64 - n));
        }
    }
}
