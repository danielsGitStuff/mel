package de.mel

open class AndroidPermission(val permission: String, val titleInt: Int, val textInt: Int) {
    fun isOfKind(permission: String) = this.permission == permission
}

class AndroidPermissionRequest(
    val requestId: Int,
    permission: String,
    titleInt: Int,
    textInt: Int
) : AndroidPermission(permission, titleInt, textInt)