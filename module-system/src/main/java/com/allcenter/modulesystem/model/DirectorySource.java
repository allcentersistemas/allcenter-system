package com.allcenter.modulesystem.model;

/**
 * Origen del registro del empleado. Las cuentas {@link #ACTIVE_DIRECTORY} se rellenan/actualizan
 * por sincronización con AD; su contraseña local suele estar vacía hasta que exista login LDAP/SSO.
 */
public enum DirectorySource {
    /** Alta manual o registro por API con contraseña local. */
    LOCAL,
    /** Sincronizado desde Microsoft Active Directory (LDAP). */
    ACTIVE_DIRECTORY
}
