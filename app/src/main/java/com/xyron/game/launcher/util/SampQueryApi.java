package com.xyron.game.launcher.util;

import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.SocketException;
import java.net.UnknownHostException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.Random;
import java.util.StringTokenizer;

public class SampQueryApi {
    private static final int MAX_PACKET_SIZE = 3072;
    private static final int DEFAULT_PING_ATTEMPTS = 1;

    private final Random f = new Random();
    private boolean isValidAddr = true;
    private String serveraddress = "";
    private InetAddress serverip = null;
    private final int serverport;
    private DatagramSocket socket = null;

    public SampQueryApi(String str, int i) {
        this(str, i, 900);
    }

    public SampQueryApi(String str, int i, int timeoutMs) {
        try {
            InetAddress byName = InetAddress.getByName(str);
            this.serverip = byName;
            this.serveraddress = byName.getHostAddress();
        } catch (UnknownHostException unused) {
            this.isValidAddr = false;
        }
        try {
            DatagramSocket datagramSocket = new DatagramSocket();
            this.socket = datagramSocket;
            datagramSocket.setSoTimeout(Math.max(50, timeoutMs));
        } catch (SocketException unused2) {
            this.isValidAddr = false;
        }
        this.serverport = i;
    }

    private DatagramPacket initPacket(String str) {
        try {
            byte[] bytes = ("SAMPzalupa" + str).getBytes("windows-1251");
            StringTokenizer stringTokenizer = new StringTokenizer(this.serveraddress, ".");
            bytes[4] = (byte) Integer.parseInt(stringTokenizer.nextToken());
            bytes[5] = (byte) Integer.parseInt(stringTokenizer.nextToken());
            bytes[6] = (byte) Integer.parseInt(stringTokenizer.nextToken());
            bytes[7] = (byte) Integer.parseInt(stringTokenizer.nextToken());
            int i = this.serverport;
            bytes[8] = (byte) (i & 255);
            bytes[9] = (byte) ((i >> 8) & 255);
            return new DatagramPacket(bytes, bytes.length, this.serverip, this.serverport);
        } catch (Exception unused) {
            return null;
        }
    }

    private byte[] receiveData() {
        if (this.socket == null) {
            return null;
        }

        DatagramPacket datagramPacket = new DatagramPacket(new byte[MAX_PACKET_SIZE], MAX_PACKET_SIZE);
        try {
            this.socket.receive(datagramPacket);
            byte[] result = new byte[datagramPacket.getLength()];
            System.arraycopy(datagramPacket.getData(), 0, result, 0, datagramPacket.getLength());
            return result;
        } catch (IOException unused) {
            return null;
        }
    }

    private void sendPacket(DatagramPacket datagramPacket) {
        if (datagramPacket == null) {
            return;
        }

        try {
            DatagramSocket datagramSocket = this.socket;
            if (datagramSocket != null) {
                datagramSocket.send(datagramPacket);
            }
        } catch (IOException ignored) {
        }
    }

    public void close() {
        DatagramSocket datagramSocket = this.socket;
        if (datagramSocket != null) {
            datagramSocket.close();
        }
    }

    public boolean isOnline() {
        if (!this.isValidAddr || this.socket == null) {
            return false;
        }

        byte[] challenge = f();
        try {
            String str = new String(challenge, "windows-1251");
            sendPacket(initPacket("p" + str));
            byte[] response = receiveData();
            return isValidPingResponse(response, challenge);
        } catch (UnsupportedEncodingException e) {
            e.printStackTrace();
            return false;
        }
    }

    public String[] getInfo() {
        if (!this.isValidAddr || this.socket == null) {
            return null;
        }

        try {
            sendPacket(initPacket("i"));
            byte[] response = receiveData();
            if (!isValidHeader(response, 'i')) {
                return null;
            }

            String[] strArr = new String[6];
            ByteBuffer wrap = ByteBuffer.wrap(response);
            wrap.order(ByteOrder.LITTLE_ENDIAN);
            wrap.position(11);
            strArr[0] = wrap.get() == 0 ? "0" : "1";
            strArr[1] = String.valueOf((int) wrap.getShort());
            strArr[2] = String.valueOf((int) wrap.getShort());
            strArr[3] = convert(wrap, wrap.getInt());
            strArr[4] = convert(wrap, wrap.getInt());
            strArr[5] = convert(wrap, wrap.getInt());
            return strArr;
        } catch (Exception unused) {
            return null;
        }
    }

    public long e() {
        return measurePingMs(DEFAULT_PING_ATTEMPTS);
    }

    public long measurePingMs(int attempts) {
        if (!this.isValidAddr || this.socket == null) {
            return -1;
        }

        int tries = Math.max(1, attempts);
        long totalPing = 0L;
        int successCount = 0;

        for (int i = 0; i < tries; i++) {
            long singlePing = measureSinglePingMs();
            if (singlePing >= 0) {
                totalPing += singlePing;
                successCount++;
            }
        }

        if (successCount == 0) {
            return -1;
        }

        return Math.round((double) totalPing / (double) successCount);
    }

    byte[] f() {
        byte[] bArr = new byte[4];
        this.f.nextBytes(bArr);
        bArr[0] = (byte) (((bArr[0] % 100) + 110) & 255);
        bArr[1] = (byte) (bArr[1] % -128);
        bArr[2] = (byte) (bArr[2] % -128);
        bArr[3] = (byte) ((bArr[3] % 50) & 255);
        return bArr;
    }

    private boolean isValidPingResponse(byte[] response, byte[] challenge) {
        return isValidHeader(response, 'p')
                && response.length >= 15
                && response[11] == challenge[0]
                && response[12] == challenge[1]
                && response[13] == challenge[2]
                && response[14] == challenge[3];
    }

    private boolean isValidHeader(byte[] response, char opcode) {
        if (response == null || response.length < 11) {
            return false;
        }

        return response[0] == 'S'
                && response[1] == 'A'
                && response[2] == 'M'
                && response[3] == 'P'
                && response[10] == (byte) opcode;
    }

    private String convert(ByteBuffer byteBuffer, int i) throws UnsupportedEncodingException {
        if (i < 0 || i > byteBuffer.remaining()) {
            throw new UnsupportedEncodingException("Invalid SA:MP string size");
        }

        byte[] bArr = new byte[i];
        byteBuffer.get(bArr);
        return new String(bArr, "windows-1251");
    }

    private long measureSinglePingMs() {
        try {
            byte[] challenge = f();
            long startNs = System.nanoTime();
            sendPacket(initPacket("p" + new String(challenge, "windows-1251")));
            byte[] response = receiveData();
            if (!isValidPingResponse(response, challenge)) {
                return -1;
            }
            long elapsedNs = System.nanoTime() - startNs;
            return Math.max(1L, Math.round(elapsedNs / 1_000_000.0d));
        } catch (UnsupportedEncodingException e) {
            e.printStackTrace();
            return -1;
        }
    }
}
