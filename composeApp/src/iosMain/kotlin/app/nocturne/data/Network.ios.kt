package app.nocturne.data

import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.alloc
import kotlinx.cinterop.memScoped
import kotlinx.cinterop.ptr
import kotlinx.cinterop.value
import platform.SystemConfiguration.SCNetworkReachabilityCreateWithName
import platform.SystemConfiguration.SCNetworkReachabilityFlagsVar
import platform.SystemConfiguration.SCNetworkReachabilityGetFlags
import platform.SystemConfiguration.kSCNetworkReachabilityFlagsIsWWAN
import platform.SystemConfiguration.kSCNetworkReachabilityFlagsReachable

@OptIn(ExperimentalForeignApi::class)
actual fun unmeteredNetwork(): Boolean = memScoped {
    val r = SCNetworkReachabilityCreateWithName(null, "apple.com") ?: return false
    val flags = alloc<SCNetworkReachabilityFlagsVar>()
    val ok = SCNetworkReachabilityGetFlags(r, flags.ptr)
    platform.CoreFoundation.CFRelease(r)
    ok && (flags.value and kSCNetworkReachabilityFlagsReachable) != 0u && (flags.value and kSCNetworkReachabilityFlagsIsWWAN) == 0u
}
