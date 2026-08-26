package com.allcenter.modulebiesse.integration;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;

/**
 * ZPL alineado a plantilla LEdit {@code EtiquetaRM050X75} / {@code LayoutSch} (~80×50 mm).
 *
 * <pre>
 *  [order name]                    [QR unitCode]
 *  [material]                      L: xxxx
 *  ─────────────                   A: xxx
 *  ║ edge bars + rotated desc ║    n / tot
 *  ─────────────                   Pnn
 *  [edge / finish]                 dd/MM/yy
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
        String order = esc(trunc(data.orderName(), 42));
        String material = esc(trunc(firstNonBlank(data.material(), data.bookingCode()), 40));
        String desc1 =
                esc(trunc(firstNonBlank(data.desc1(), data.partCode(), data.osiPart()), 28));
        String desc2 = esc(trunc(nullToEmpty(data.desc2()), 28));
        String edge = esc(trunc(firstNonBlank(data.edgeLabel(), shortEdge(material)), 18));
        String length = esc(formatDim(data.length()));
        String width = esc(formatDim(data.width()));
        String piece = data.pieceNumber() > 0 ? String.valueOf(data.pieceNumber()) : "1";
        String qty =
                data.quantity() > 0 ? String.valueOf(data.quantity()) : piece;
        String partLabel =
                esc(
                        trunc(
                                firstNonBlank(
                                        data.partLabel(),
                                        data.partCode() != null && !data.partCode().isBlank()
                                                ? (data.partCode().toUpperCase().startsWith("P")
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
        String machine = esc(trunc(nullToEmpty(data.machineName()), 20));

        // 203 dpi ≈ 80 mm × 50 mm (plantilla LEdit 80000×46000 µm).
        return """
                ^XA
                ^PW640
                ^LL400
                ^LH0,0
                ^CI28
                ^FO20,12^A0N,34,30^FD%s^FS
                ^FO20,50^A0N,24,22^FD%s^FS
                ^FO20,82^GB360,3,3^FS
                ^FO20,98^GB360,10,10^FS
                ^FO48,120^A0R,26,22^FD%s^FS
                ^FO78,120^A0R,24,20^FD%s^FS
                ^FO20,270^GB360,10,10^FS
                ^FO20,288^A0N,22,20^FD%s^FS
                ^FO420,20^BQN,2,5^FDQA,%s^FS
                ^FO420,230^A0N,28,26^FDL: %s^FS
                ^FO420,268^A0N,28,26^FDA: %s^FS
                ^FO420,306^A0N,24,22^FD%s / %s^FS
                ^FO420,338^A0N,26,24^FD%s^FS
                ^FO420,368^A0N,20,18^FD%s^FS
                ^FO20,360^A0N,16,14^FD%s^FS
                ^XZ
                """
                .formatted(
                        order,
                        material,
                        desc1,
                        desc2,
                        edge,
                        code,
                        length,
                        width,
                        piece,
                        qty,
                        partLabel,
                        date,
                        machine.isBlank() ? "" : ("Sec: " + machine));
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

    private static String shortEdge(String material) {
        if (material == null || material.isBlank()) {
            return "";
        }
        String t = material.trim();
        if (t.length() <= 14) {
            return t.toUpperCase();
        }
        return t.substring(0, 14).toUpperCase();
    }

    private static String formatDim(double value) {
        if (value <= 0 || Double.isNaN(value) || Double.isInfinite(value)) {
            return "—";
        }
        if (Math.abs(value - Math.rint(value)) < 0.05) {
            return String.valueOf((long) Math.rint(value));
        }
        return String.format(java.util.Locale.US, "%.1f", value);
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
