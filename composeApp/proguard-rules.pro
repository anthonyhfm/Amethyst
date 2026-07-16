# FlatLAF uses MethodHandle internally via reflection; these method signatures
# only exist at runtime and are not resolvable at build time.
-dontwarn com.formdev.flatlaf.**

# javacpp bundles Maven plugin classes (BuildMojo etc.) which depend on
# org.apache.maven.* and org.osgi.* — none of which are on the classpath.
-dontwarn org.bytedeco.javacpp.**
-dontwarn org.apache.maven.**
-dontwarn org.osgi.**

# composeunstyled references an internal Compose Foundation flag that is not
# present in the desktop artifact version used here.
-dontwarn core.com.composeunstyled.**

# Ktor's SocketBase uses an enclosing method that the ProGuard shrinker cannot
# resolve across the coroutine transformation boundary.
-dontwarn io.ktor.network.sockets.**

# Sentry probes optional XMLutil serialization providers via ServiceLoader. They
# are not bundled in the Android artifact, so R8 must not treat their absence as
# a release-build error.
-dontwarn nl.adaptivity.xmlutil.serialization.KotlinxSerializationProvider
-dontwarn nl.adaptivity.xmlutil.util.SerializationProvider
-dontwarn nl.adaptivity.xmlutil.util.DefaultSerializationProvider
