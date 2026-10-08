package dev.anthonyhfm.amethyst.home.account

import com.sun.jna.Memory
import com.sun.jna.Pointer
import com.sun.jna.ptr.IntByReference
import com.sun.jna.ptr.PointerByReference
import dev.anthonyhfm.amethyst.hub.data.HubSessionStorageException
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull

class MacHubKeychainTest {
    @Test
    fun nativeWritesCreateThenUpdateTheSameItemAndRoundTripUtf8() {
        val native = FakeKeychain()
        val backend = backend(native = native)
        assertNull(backend.read())

        backend.write(value = "synthetic-first")
        backend.write(value = "synthetic-second-ä")
        assertEquals("synthetic-second-ä", backend.read())
        assertEquals(1, native.adds)
        assertEquals(1, native.updates)
        assertEquals(1, native.freedBuffers)
        assertEquals(2, native.releasedItems)

        backend.write(value = null)
        assertNull(backend.read())
        assertEquals(1, native.deletes)
        backend.write(value = null)
        assertEquals(1, native.deletes)
    }

    @Test
    fun lockedKeychainAndFailedWritesRemainVisible() {
        val native = FakeKeychain()
        val backend = backend(native = native)
        native.findFailure = -25308
        assertFailsWith<HubSessionStorageException> { backend.read() }
        assertFailsWith<HubSessionStorageException> { backend.write(value = "synthetic") }
        assertEquals(0, native.adds)

        native.findFailure = null
        native.writeFailure = -25293
        assertFailsWith<HubSessionStorageException> { backend.write(value = "synthetic") }
        assertNull(native.stored)
    }

    @Test
    fun emptyStoredDataIsCorruptRatherThanAnAbsentLogin() {
        val native = FakeKeychain()
        native.stored = byteArrayOf()
        assertFailsWith<HubSessionStorageException> { backend(native = native).read() }
        assertEquals(1, native.freedBuffers)
        assertEquals(1, native.releasedItems)
    }

    private fun backend(native: FakeKeychain): MacHubKeychain = MacHubKeychain(
        service = "synthetic-test-service",
        account = "synthetic-test-account-ä",
        securityProvider = { native },
        foundationProvider = { native }
    )

    private class FakeKeychain : MacSecurity, MacCoreFoundation {
        var stored: ByteArray? = null
        var findFailure: Int? = null
        var writeFailure: Int? = null
        var adds = 0
        var updates = 0
        var deletes = 0
        var freedBuffers = 0
        var releasedItems = 0
        private val itemPointer = Pointer(1234)
        private val buffers = mutableMapOf<Long, Memory>()

        override fun SecKeychainFindGenericPassword(
            keychain: Pointer?,
            serviceLength: Int,
            service: ByteArray,
            accountLength: Int,
            account: ByteArray,
            passwordLength: IntByReference?,
            passwordData: PointerByReference?,
            item: PointerByReference?,
        ): Int {
            assertEquals(service.size, serviceLength)
            assertEquals(account.size, accountLength)
            findFailure?.let { status ->
                return status
            }
            val bytes = stored ?: return -25300
            item?.value = itemPointer
            passwordLength?.value = bytes.size
            if (passwordData != null) {
                val data = Memory(bytes.size.coerceAtLeast(1).toLong())
                if (bytes.isNotEmpty()) {
                    data.write(0, bytes, 0, bytes.size)
                }
                buffers[Pointer.nativeValue(data)] = data
                passwordData.value = data
            }
            return 0
        }

        override fun SecKeychainAddGenericPassword(
            keychain: Pointer?,
            serviceLength: Int,
            service: ByteArray,
            accountLength: Int,
            account: ByteArray,
            passwordLength: Int,
            passwordData: ByteArray,
            item: PointerByReference?,
        ): Int {
            adds++
            writeFailure?.let { status ->
                return status
            }
            assertEquals(passwordData.size, passwordLength)
            stored = passwordData.copyOf()
            return 0
        }

        override fun SecKeychainItemModifyAttributesAndData(
            item: Pointer,
            attributes: Pointer?,
            length: Int,
            data: ByteArray,
        ): Int {
            updates++
            writeFailure?.let { status ->
                return status
            }
            assertEquals(data.size, length)
            stored = data.copyOf()
            return 0
        }

        override fun SecKeychainItemDelete(item: Pointer): Int {
            deletes++
            stored = null
            return 0
        }

        override fun SecKeychainItemFreeContent(attributes: Pointer?, data: Pointer): Int {
            freedBuffers++
            requireNotNull(buffers.remove(Pointer.nativeValue(data))).close()
            return 0
        }

        override fun CFRelease(value: Pointer) {
            releasedItems++
        }
    }
}
