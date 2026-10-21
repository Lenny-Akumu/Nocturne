package app.nocturne.data

import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.alloc
import kotlinx.cinterop.convert
import kotlinx.cinterop.memScoped
import kotlinx.cinterop.ptr
import kotlinx.cinterop.value
import platform.CoreFoundation.CFDictionaryAddValue
import platform.CoreFoundation.CFDictionaryCreateMutable
import platform.CoreFoundation.CFMutableDictionaryRef
import platform.CoreFoundation.CFRelease
import platform.CoreFoundation.CFTypeRef
import platform.CoreFoundation.CFTypeRefVar
import platform.CoreFoundation.kCFBooleanTrue
import platform.CoreFoundation.kCFTypeDictionaryKeyCallBacks
import platform.CoreFoundation.kCFTypeDictionaryValueCallBacks
import platform.Foundation.CFBridgingRelease
import platform.Foundation.CFBridgingRetain
import platform.Foundation.NSData
import platform.Foundation.NSString
import platform.Foundation.NSUTF8StringEncoding
import platform.Foundation.create
import platform.Foundation.dataUsingEncoding
import platform.Security.SecItemAdd
import platform.Security.SecItemCopyMatching
import platform.Security.SecItemDelete
import platform.Security.errSecSuccess
import platform.Security.kSecAttrAccessible
import platform.Security.kSecAttrAccessibleAfterFirstUnlockThisDeviceOnly
import platform.Security.kSecAttrAccount
import platform.Security.kSecAttrService
import platform.Security.kSecClass
import platform.Security.kSecClassGenericPassword
import platform.Security.kSecMatchLimit
import platform.Security.kSecMatchLimitOne
import platform.Security.kSecReturnData
import platform.Security.kSecValueData

@OptIn(ExperimentalForeignApi::class)
actual object SecureStore {
    private const val SERVICE = "app.nocturne.secrets"

    private fun query(name: String, vararg extra: Pair<CFTypeRef?, CFTypeRef?>): CFMutableDictionaryRef? {
        val d = CFDictionaryCreateMutable(null, (3 + extra.size).convert(), kCFTypeDictionaryKeyCallBacks.ptr, kCFTypeDictionaryValueCallBacks.ptr)
        val service = CFBridgingRetain(SERVICE as NSString)
        val account = CFBridgingRetain(name as NSString)
        CFDictionaryAddValue(d, kSecClass, kSecClassGenericPassword)
        CFDictionaryAddValue(d, kSecAttrService, service)
        CFDictionaryAddValue(d, kSecAttrAccount, account)
        extra.forEach { (k, v) -> CFDictionaryAddValue(d, k, v) }
        CFRelease(service)
        CFRelease(account)
        return d
    }

    actual fun read(name: String): String? = runCatching {
        memScoped {
            val q = query(name, kSecReturnData to kCFBooleanTrue, kSecMatchLimit to kSecMatchLimitOne)
            val out = alloc<CFTypeRefVar>()
            val status = SecItemCopyMatching(q, out.ptr)
            CFRelease(q)
            if (status != errSecSuccess) return null
            val data = CFBridgingRelease(out.value) as? NSData ?: return null
            NSString.create(data = data, encoding = NSUTF8StringEncoding)?.toString()
        }
    }.getOrNull()

    actual fun write(name: String, content: String): Boolean = runCatching {
        val del = query(name)
        SecItemDelete(del)
        CFRelease(del)
        val data = (content as NSString).dataUsingEncoding(NSUTF8StringEncoding) ?: return false
        val value = CFBridgingRetain(data)
        val q = query(name, kSecValueData to value, kSecAttrAccessible to kSecAttrAccessibleAfterFirstUnlockThisDeviceOnly)
        val status = SecItemAdd(q, null)
        CFRelease(q)
        CFRelease(value)
        status == errSecSuccess
    }.getOrDefault(false)
}
