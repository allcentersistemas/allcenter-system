package com.allcenter.modulebiesse.agent;

/** ZPL simple 80×50 mm para impresión local del agente CNC. */
public final class SimpleZplBuilder {

    private SimpleZplBuilder() {}

    public static String build(
            String orderName,
            String bookingCode,
            String partCode,
            String material,
            String unitCode,
            String machineName) {
        String order = esc(trunc(orderName, 40));
        String booking = esc(trunc(bookingCode != null ? bookingCode : "", 36));
        String part = esc(trunc(partCode != null ? partCode : "", 24));
        String mat = esc(trunc(material != null ? material : "", 36));
        String code = esc(unitCode != null ? unitCode : "");
        String machine = esc(trunc(machineName != null ? machineName : "", 24));

        // 80mm x 50mm @ 203 dpi ≈ 640 x 400 dots
        return """
                ^XA
                ^PW640
                ^LL400
                ^LH0,0
                ^CI28
                ^FO24,20^A0N,36,30^FD%s^FS
                ^FO24,60^A0N,28,24^FD%s^FS
                ^FO24,100^A0N,28,24^FDParte: %s^FS
                ^FO24,140^A0N,24,20^FD%s^FS
                ^FO24,180^A0N,22,18^FD%s^FS
                ^FO24,220^A0N,22,18^FDMaq: %s^FS
                ^FO400,40^BQN,2,5^FDQA,%s^FS
                ^FO24,340^A0N,20,16^FD%s^FS
                ^XZ
                """
                .formatted(order, booking, part, mat, machine, machine, code, code);
    }

    private static String esc(String text) {
        return String.valueOf(text)
                .replace("\\", "\\\\")
                .replace("^", "\\^")
                .replace("~", "\\~");
    }

    private static String trunc(String text, int max) {
        String s = text == null ? "" : text.trim();
        if (s.length() <= max) {
            return s;
        }
        return s.substring(0, Math.max(0, max - 1)) + ".";
    }
}
