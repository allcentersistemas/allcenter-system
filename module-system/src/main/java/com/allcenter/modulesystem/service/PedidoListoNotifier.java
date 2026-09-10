package com.allcenter.modulesystem.service;

/**
 * Texto unificado de aviso «pedido listo» para Telegram y WhatsApp.
 */
public final class PedidoListoNotifier {

    private PedidoListoNotifier() {}

    /** Texto plano (WhatsApp y prueba). */
    public static String plainText(String clientName, String pedidoLabel) {
        String name = blankTo(clientName, "cliente");
        String pedido = blankTo(pedidoLabel, "su pedido");
        return """
                Pedido listo para entregar

                Hola %s,
                su pedido %s se encuentra listo para recoger.

                lo esperamos con su nuevo proyecto pronto.

                AllPanel
                """
                .formatted(name, pedido)
                .trim();
    }

    /** HTML para Telegram ({@code parse_mode=HTML}). */
    public static String htmlText(String clientName, String pedidoLabel) {
        return """
                <b>Pedido listo para entregar</b>

                Hola %s,
                su pedido <b>%s</b> se encuentra listo para recoger.

                lo esperamos con su nuevo proyecto pronto.

                AllPanel
                """
                .formatted(escapeHtml(blankTo(clientName, "cliente")), escapeHtml(blankTo(pedidoLabel, "su pedido")))
                .trim();
    }

    private static String blankTo(String value, String fallback) {
        if (value == null || value.isBlank()) {
            return fallback;
        }
        return value.trim();
    }

    private static String escapeHtml(String value) {
        if (value == null) {
            return "";
        }
        return value
                .replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;")
                .replace("\"", "&quot;");
    }
}
