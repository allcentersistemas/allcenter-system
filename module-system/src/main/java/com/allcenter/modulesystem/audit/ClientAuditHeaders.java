package com.allcenter.modulesystem.audit;

/**
 * Cabeceras opcionales que pueden enviar clientes de confianza (apps móviles, escritorio, gateway
 * interno). Los navegadores web <strong>no</strong> exponen MAC ni IP LAN al servidor por HTTP; hay
 * que rellenarlas desde el agente si las necesita en auditoría.
 */
public final class ClientAuditHeaders {

    /** IP privada del dispositivo en la LAN (ej. 192.168.x.x). */
    public static final String CLIENT_LOCAL_IP = "X-Client-Local-Ip";

    /** MAC del equipo en formato habitual (ej. {@code AA:BB:CC:DD:EE:FF}). */
    public static final String CLIENT_MAC_ADDRESS = "X-Client-Mac-Address";

    /** Nombre amigable del dispositivo (ej. "MacBook-Pro-de-Juan", "Samsung SM-G991B"). */
    public static final String DEVICE_NAME = "X-Device-Name";

    /** Identificador estable del dispositivo (UUID u otro ID del fabricante). */
    public static final String DEVICE_ID = "X-Device-Id";

    private ClientAuditHeaders() {}
}
