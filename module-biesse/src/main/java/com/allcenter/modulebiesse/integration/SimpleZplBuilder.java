package com.allcenter.modulebiesse.integration;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.Locale;

/**
 * ZPL del agente seccionadora (~80×50 mm @ 203 dpi). Independiente del editor web.
 *
 * <pre>
 *  [order name]
 *  [material]
 *  [desc2 / ref]
 *  ┌─ edgeUp ─────────┐     [QR]
 *  │edgeL  DESC  edgeR│     L / A
 *  └─ edgeLo ─────────┘     n/tot  Pnn  date
 * </pre>
 */
public final class SimpleZplBuilder {

    private static final DateTimeFormatter DATE_FMT = DateTimeFormatter.ofPattern("dd/MM/yy");

    private SimpleZplBuilder() {}

    public static String build(
            String orderName,
            String bookingCode,
            String partCode,
            String material,
            String unitCode,
            String machineName) {
        return build(
                LabelData.builder()
                        .orderName(orderName)
                        .bookingCode(bookingCode)
                        .partCode(partCode)
                        .material(material)
                        .unitCode(unitCode)
                        .machineName(machineName)
                        .build());
    }

    public static String build(LabelData data) {
        String order = esc(trunc(data.orderName(), 44));
        String material = esc(trunc(firstNonBlank(data.material(), data.bookingCode()), 42));
        String refLine =
                esc(
                        trunc(
                                firstNonBlank(data.desc2(), data.partCode(), data.osiPart()),
                                40));
        String center =
                esc(
                        trunc(
                                firstNonBlank(data.desc1(), data.partCode(), data.osiPart()),
                                18));
        String edgeUp = esc(formatEdge(data.edgeUp()));
        String edgeLo = esc(formatEdge(data.edgeLo()));
        String edgeL = esc(formatEdge(data.edgeL()));
        String edgeR = esc(formatEdge(data.edgeR()));
        if (edgeUp.isBlank()
                && edgeLo.isBlank()
                && edgeL.isBlank()
                && edgeR.isBlank()
                && data.edgeLabel() != null
                && !data.edgeLabel().isBlank()) {
            // Fallback compacto antiguo → canto inferior.
            edgeLo = esc(trunc(data.edgeLabel().trim(), 22));
        }
        String length = esc(formatDim(data.length()));
        String width = esc(formatDim(data.width()));
        String piece = data.pieceNumber() > 0 ? String.valueOf(data.pieceNumber()) : "1";
        String qty = data.quantity() > 0 ? String.valueOf(data.quantity()) : piece;
        String partLabel =
                esc(
                        trunc(
                                firstNonBlank(
                                        data.partLabel(),
                                        data.partCode() != null && !data.partCode().isBlank()
                                                ? (data.partCode().toUpperCase(Locale.ROOT).startsWith("P")
                                                        ? data.partCode()
                                                        : "P" + data.partNumber())
                                                : (data.partNumber() > 0
                                                        ? "P" + data.partNumber()
                                                        : "")),
                                10));
        String date =
                esc(
                        data.dateLabel() != null && !data.dateLabel().isBlank()
                                ? data.dateLabel()
                                : LocalDate.now().format(DATE_FMT));
        String code = esc(nullToEmpty(data.unitCode()));
        String machine = esc(trunc(nullToEmpty(data.machineName()), 18));
        String machineLine = machine.isBlank() ? "" : ("Sec: " + machine);

        // Marco pieza: FO28,95  GB 300×175. QR a la derecha (mag 4 para no recortar).
        return """
                ^XA
                ^PW640
                ^LL400
                ^LH0,0
                ^CI28
                ^FO16,8^A0N,30,26^FD%s^FS
                ^FO16,42^A0N,22,20^FD%s^FS
                ^FO16,68^A0N,20,18^FD%s^FS
                ^FO28,95^GB300,175,2^FS
                ^FO40,102^A0N,18,16^FD%s^FS
                ^FO40,248^A0N,18,16^FD%s^FS
                ^FO34,120^A0R,18,16^FD%s^FS
                ^FO300,120^A0R,18,16^FD%s^FS
                ^FO150,118^A0R,28,24^FD%s^FS
                ^FO360,20^BQN,2,4^FDQA,%s^FS
                ^FO360,210^A0N,26,24^FDL: %s^FS
                ^FO360,244^A0N,26,24^FDA: %s^FS
                ^FO360,278^A0N,22,20^FD%s / %s^FS
                ^FO360,308^A0N,24,22^FD%s^FS
                ^FO360,338^A0N,18,16^FD%s^FS
                ^FO16,360^A0N,14,12^FD%s^FS
                ^XZ
                """
                .formatted(
                        order,
                        material,
                        refLine,
                        edgeUp,
                        edgeLo,
                        edgeL,
                        edgeR,
                        center,
                        code,
                        length,
                        width,
                        piece,
                        qty,
                        partLabel,
                        date,
                        machineLine);
    }

    public record LabelData(
            String orderName,
            String bookingCode,
            String partCode,
            String material,
            String unitCode,
            String machineName,
            String desc1,
            String desc2,
            String edgeLabel,
            String edgeUp,
            String edgeLo,
            String edgeL,
            String edgeR,
            String osiPart,
            String partLabel,
            String dateLabel,
            double length,
            double width,
            int partNumber,
            int pieceNumber,
            int quantity) {

        public static Builder builder() {
            return new Builder();
        }

        public static final class Builder {
            private String orderName;
            private String bookingCode;
            private String partCode;
            private String material;
            private String unitCode;
            private String machineName;
            private String desc1;
            private String desc2;
            private String edgeLabel;
            private String edgeUp;
            private String edgeLo;
            private String edgeL;
            private String edgeR;
            private String osiPart;
            private String partLabel;
            private String dateLabel;
            private double length;
            private double width;
            private int partNumber;
            private int pieceNumber;
            private int quantity;

            public Builder orderName(String v) {
                this.orderName = v;
                return this;
            }

            public Builder bookingCode(String v) {
                this.bookingCode = v;
                return this;
            }

            public Builder partCode(String v) {
                this.partCode = v;
                return this;
            }

            public Builder material(String v) {
                this.material = v;
                return this;
            }

            public Builder unitCode(String v) {
                this.unitCode = v;
                return this;
            }

            public Builder machineName(String v) {
                this.machineName = v;
                return this;
            }

            public Builder desc1(String v) {
                this.desc1 = v;
                return this;
            }

            public Builder desc2(String v) {
                this.desc2 = v;
                return this;
            }

            public Builder edgeLabel(String v) {
                this.edgeLabel = v;
                return this;
            }

            public Builder edgeUp(String v) {
                this.edgeUp = v;
                return this;
            }

            public Builder edgeLo(String v) {
                this.edgeLo = v;
                return this;
            }

            public Builder edgeL(String v) {
                this.edgeL = v;
                return this;
            }

            public Builder edgeR(String v) {
                this.edgeR = v;
                return this;
            }

            public Builder osiPart(String v) {
                this.osiPart = v;
                return this;
            }

            public Builder partLabel(String v) {
                this.partLabel = v;
                return this;
            }

            public Builder dateLabel(String v) {
                this.dateLabel = v;
                return this;
            }

            public Builder length(double v) {
                this.length = v;
                return this;
            }

            public Builder width(double v) {
                this.width = v;
                return this;
            }

            public Builder partNumber(int v) {
                this.partNumber = v;
                return this;
            }

            public Builder pieceNumber(int v) {
                this.pieceNumber = v;
                return this;
            }

            public Builder quantity(int v) {
                this.quantity = v;
                return this;
            }

            public LabelData build() {
                return new LabelData(
                        orderName,
                        bookingCode,
                        partCode,
                        material,
                        unitCode,
                        machineName,
                        desc1,
                        desc2,
                        edgeLabel,
                        edgeUp,
                        edgeLo,
                        edgeL,
                        edgeR,
                        osiPart,
                        partLabel,
                        dateLabel,
                        length,
                        width,
                        partNumber,
                        pieceNumber,
                        quantity);
            }
        }
    }

    /** Texto de canto para el diagrama (sin prefijo U/B/I/D). */
    static String formatEdge(String raw) {
        if (raw == null || raw.isBlank()) {
            return "";
        }
        String t = raw.trim();
        if ("0".equals(t)
                || "NONE".equalsIgnoreCase(t)
                || "N/A".equalsIgnoreCase(t)) {
            return "";
        }
        return trunc(t.toUpperCase(Locale.ROOT), 20);
    }

    private static String formatDim(double value) {
        if (value <= 0 || Double.isNaN(value) || Double.isInfinite(value)) {
            return "—";
        }
        if (Math.abs(value - Math.rint(value)) < 0.05) {
            return String.valueOf((long) Math.rint(value));
        }
        return String.format(Locale.US, "%.1f", value);
    }

    private static String firstNonBlank(String... values) {
        if (values == null) {
            return "";
        }
        for (String v : values) {
            if (v != null && !v.isBlank()) {
                return v.trim();
            }
        }
        return "";
    }

    private static String nullToEmpty(String text) {
        return text == null ? "" : text;
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
