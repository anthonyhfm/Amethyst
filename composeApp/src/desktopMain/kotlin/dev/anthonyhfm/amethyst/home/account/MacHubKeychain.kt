package dev.anthonyhfm.amethyst.home.account

import com.sun.jna.Library
import com.sun.jna.Native
import com.sun.jna.Pointer
import com.sun.jna.ptr.IntByReference
import com.sun.jna.ptr.PointerByReference
import dev.anthonyhfm.amethyst.hub.data.HubSessionStorageException

internal interface MacSecurity : Library {
    fun SecKeychainFindGenericPassword(
        keychain: Pointer?,
        serviceLength: Int,
        service: ByteArray,
        accountLength: Int,
        account: ByteArray,
        passwordLength: IntByReference?,
        passwordData: PointerByReference?,
        item: PointerByReference?,
    ): Int

    fun SecKeychainAddGenericPassword(
        keychain: Pointer?,
        serviceLength: Int,
        service: ByteArray,
        accountLength: Int,
        account: ByteArray,
        passwordLength: Int,
        passwordData: ByteArray,
        item: PointerByReference?,
    ): Int

    fun SecKeychainItemModifyAttributesAndData(
        item: Pointer,
        attributes: Pointer?,
        length: Int,
        data: ByteArray,
    ): Int

    fun SecKeychainItemDelete(item: Pointer): Int
    fun SecKeychainItemFreeContent(attributes: Pointer?, data: Pointer): Int
}

internal interface MacCoreFoundation : Library {
    fun CFRelease(value: Pointer)
}

internal class MacHubKeychain(
    service: String,
    account: String,
    securityProvider: () -> MacSecurity = {
        loadMacFramework(
            path = "/System/Library/Frameworks/Security.framework/Security",
            type = MacSecurity::class.java
        )
    },
    foundationProvider: () -> MacCoreFoundation = {
        loadMacFramework(
            path = "/System/Library/Frameworks/CoreFoundation.framework/CoreFoundation",
            type = MacCoreFoundation::class.java
        )
    },
) : DesktopSessionBackend {
    private val serviceBytes = service.toByteArray(Charsets.UTF_8)
    private val accountBytes = account.toByteArray(Charsets.UTF_8)
    private val security by lazy(initializer = securityProvider)
    private val foundation by lazy(initializer = foundationProvider)

    override fun read(): String? {
        val length = IntByReference()
        val data = PointerByReference()
        val item = PointerByReference()
        val status = find(length = length, data = data, item = item)

        try {
            if (status == ITEM_NOT_FOUND) {
                return null
            }
            requireSuccess(status = status)

            val size = length.value
            if (size !in 1..MAX_PAYLOAD_BYTES || data.value == null) {
                throw HubSessionStorageException(message = "Your saved session could not be read. Sign out and sign in again.")
            }

            val bytes = data.value.getByteArray(0, size)
            return try {
                String(bytes = bytes, charset = Charsets.UTF_8)
            } finally {
                bytes.fill(0)
            }
        } finally {
            data.value?.let { pointer ->
                security.SecKeychainItemFreeContent(attributes = null, data = pointer)
            }
            item.value?.let { pointer ->
                foundation.CFRelease(value = pointer)
            }
        }
    }

    override fun write(value: String?) {
        val item = PointerByReference()
        val status = find(length = null, data = null, item = item)

        try {
            if (status != ITEM_NOT_FOUND) {
                requireSuccess(status = status)
            }

            if (value == null) {
                if (status != ITEM_NOT_FOUND) {
                    requireSuccess(status = security.SecKeychainItemDelete(item = requireNotNull(item.value)))
                }
                return
            }

            val bytes = value.toByteArray(Charsets.UTF_8)
            try {
                val writeStatus = if (status == ITEM_NOT_FOUND) {
                    security.SecKeychainAddGenericPassword(
                        keychain = null,
                        serviceLength = serviceBytes.size,
                        service = serviceBytes,
                        accountLength = accountBytes.size,
                        account = accountBytes,
                        passwordLength = bytes.size,
                        passwordData = bytes,
                        item = null
                    )
                } else {
                    security.SecKeychainItemModifyAttributesAndData(
                        item = requireNotNull(item.value),
                        attributes = null,
                        length = bytes.size,
                        data = bytes
                    )
                }

                requireSuccess(status = writeStatus)
            } finally {
                bytes.fill(0)
            }
        } finally {
            item.value?.let { pointer ->
                foundation.CFRelease(value = pointer)
            }
        }
    }

    private fun find(
        length: IntByReference?,
        data: PointerByReference?,
        item: PointerByReference,
    ): Int = security.SecKeychainFindGenericPassword(
        keychain = null,
        serviceLength = serviceBytes.size,
        service = serviceBytes,
        accountLength = accountBytes.size,
        account = accountBytes,
        passwordLength = length,
        passwordData = data,
        item = item
    )

    private fun requireSuccess(status: Int) {
        if (status != 0) {
            val message = if (status == INTERACTION_NOT_ALLOWED) {
                "Your secure session storage is locked. Unlock it and try again."
            } else {
                "Your saved session could not be accessed. Allow Amethyst to use secure storage and try again."
            }
            throw HubSessionStorageException(message = message, statusCode = status)
        }
    }

    private companion object {
        const val ITEM_NOT_FOUND = -25300
        const val INTERACTION_NOT_ALLOWED = -25308
        const val MAX_PAYLOAD_BYTES = 16_384
    }
}

private fun <T : Library> loadMacFramework(path: String, type: Class<T>): T = try {
    Native.load(path, type)
} catch (_: LinkageError) {
    throw HubSessionStorageException(message = "Secure session storage is unavailable. Try again.")
}
