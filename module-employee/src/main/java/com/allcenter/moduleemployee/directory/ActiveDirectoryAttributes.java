package com.allcenter.moduleemployee.directory;

/**
 * Nombres de atributos habituales en Active Directory (LDAP) para jobs de sincronización.
 * Al leer {@code objectGuid} / {@code objectSid}, conviértelos a string como hace el cliente LDAP
 * (p. ej. GUID en formato UUID estándar para {@code externalDirectoryId}).
 */
public final class ActiveDirectoryAttributes {

    public static final String OBJECT_GUID = "objectGUID";
    public static final String OBJECT_SID = "objectSid";
    public static final String SAM_ACCOUNT_NAME = "sAMAccountName";
    public static final String USER_PRINCIPAL_NAME = "userPrincipalName";
    public static final String DISTINGUISHED_NAME = "distinguishedName";
    public static final String MAIL = "mail";
    public static final String GIVEN_NAME = "givenName";
    public static final String SN = "sn";
    public static final String DISPLAY_NAME = "displayName";
    public static final String DEPARTMENT = "department";
    public static final String TITLE = "title";
    public static final String MANAGER = "manager";
    public static final String TELEPHONE_NUMBER = "telephoneNumber";
    public static final String MOBILE = "mobile";
    public static final String EMPLOYEE_ID = "employeeID";
    public static final String EMPLOYEE_NUMBER = "employeeNumber";

    private ActiveDirectoryAttributes() {}
}
