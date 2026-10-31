package com.stash.core.common.platform

import java.io.File
import java.nio.file.Files
import java.nio.file.attribute.AclEntry
import java.nio.file.attribute.AclEntryPermission
import java.nio.file.attribute.AclEntryType
import java.nio.file.attribute.AclFileAttributeView
import java.nio.file.attribute.PosixFilePermission

/**
 * Utility for restricting filesystem permissions of sensitive files (keys, credentials, session cookies)
 * to the current operating system user.
 */
object FileSecurity {

    /**
     * Restricts [file] access so only the current owner has read and write permissions.
     *
     * - On Windows NTFS: Sets an explicit ACL granting the owner full permissions while stripping
     *   inherited access from other non-admin users.
     * - On POSIX (macOS, Linux, Android): Sets `0600` permissions (OWNER_READ + OWNER_WRITE).
     *
     * Fails silently if the underlying filesystem does not support ACLs or POSIX permissions (e.g. FAT32).
     */
    fun restrictToOwner(file: File) {
        if (!file.exists()) return
        runCatching {
            val path = file.toPath()
            val isWindows = System.getProperty("os.name")?.contains("Windows", ignoreCase = true) == true
            if (isWindows) {
                val aclView = Files.getFileAttributeView(path, AclFileAttributeView::class.java)
                if (aclView != null) {
                    val owner = aclView.owner
                    if (owner != null) {
                        val acl = listOf(
                            AclEntry.newBuilder()
                                .setType(AclEntryType.ALLOW)
                                .setPrincipal(owner)
                                .setPermissions(
                                    AclEntryPermission.READ_DATA,
                                    AclEntryPermission.WRITE_DATA,
                                    AclEntryPermission.APPEND_DATA,
                                    AclEntryPermission.READ_NAMED_ATTRS,
                                    AclEntryPermission.WRITE_NAMED_ATTRS,
                                    AclEntryPermission.EXECUTE,
                                    AclEntryPermission.READ_ATTRIBUTES,
                                    AclEntryPermission.WRITE_ATTRIBUTES,
                                    AclEntryPermission.DELETE,
                                    AclEntryPermission.READ_ACL,
                                    AclEntryPermission.SYNCHRONIZE,
                                )
                                .build()
                        )
                        aclView.acl = acl
                        return
                    }
                }
            }
            // Fall back to POSIX permissions on macOS, Linux, and Android
            Files.setPosixFilePermissions(
                path,
                setOf(PosixFilePermission.OWNER_READ, PosixFilePermission.OWNER_WRITE),
            )
        }
    }
}

