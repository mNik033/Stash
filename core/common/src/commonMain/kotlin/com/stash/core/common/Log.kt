package com.stash.core.common

/**
 * Portable logging facade matching the android.util.Log method signatures.
 * Delegates to android.util.Log on Android (configured at startup), or
 * to standard console output on JVM / Desktop.
 */
object Log {
    const val VERBOSE: Int = 2
    const val DEBUG: Int = 3
    const val INFO: Int = 4
    const val WARN: Int = 5
    const val ERROR: Int = 6
    const val ASSERT: Int = 7

    var delegate: Logger = ConsoleLogger

    interface Logger {
        fun v(tag: String, msg: String): Int
        fun v(tag: String, msg: String, tr: Throwable?): Int
        fun d(tag: String, msg: String): Int
        fun d(tag: String, msg: String, tr: Throwable?): Int
        fun i(tag: String, msg: String): Int
        fun i(tag: String, msg: String, tr: Throwable?): Int
        fun w(tag: String, msg: String): Int
        fun w(tag: String, tr: Throwable?): Int
        fun w(tag: String, msg: String, tr: Throwable?): Int
        fun e(tag: String, msg: String): Int
        fun e(tag: String, msg: String, tr: Throwable?): Int
        fun isLoggable(tag: String, level: Int): Boolean
    }

    object ConsoleLogger : Logger {
        private val debugEnabled: Boolean = System.getProperty("stash.debug")?.toBoolean() == true

        override fun isLoggable(tag: String, level: Int): Boolean =
            level >= INFO || debugEnabled

        override fun v(tag: String, msg: String) = log("V", tag, msg, null)
        override fun v(tag: String, msg: String, tr: Throwable?) = log("V", tag, msg, tr)
        override fun d(tag: String, msg: String) = log("D", tag, msg, null)
        override fun d(tag: String, msg: String, tr: Throwable?) = log("D", tag, msg, tr)
        override fun i(tag: String, msg: String) = log("I", tag, msg, null)
        override fun i(tag: String, msg: String, tr: Throwable?) = log("I", tag, msg, tr)
        override fun w(tag: String, msg: String) = log("W", tag, msg, null)
        override fun w(tag: String, tr: Throwable?) = log("W", tag, "", tr)
        override fun w(tag: String, msg: String, tr: Throwable?) = log("W", tag, msg, tr)
        override fun e(tag: String, msg: String) = log("E", tag, msg, null)
        override fun e(tag: String, msg: String, tr: Throwable?) = log("E", tag, msg, tr)

        private fun log(level: String, tag: String, msg: String, tr: Throwable?): Int {
            val levelInt = when (level) {
                "V" -> VERBOSE
                "D" -> DEBUG
                "I" -> INFO
                "W" -> WARN
                "E" -> ERROR
                else -> INFO
            }
            if (!isLoggable(tag, levelInt)) return 0
            val out = if (level == "E" || level == "W") System.err else System.out
            out.println("[$level/$tag] $msg")
            tr?.printStackTrace(out)
            return 0
        }
    }

    @JvmStatic fun v(tag: String, msg: String) = delegate.v(tag, msg)
    @JvmStatic fun v(tag: String, msg: String, tr: Throwable?) = delegate.v(tag, msg, tr)
    @JvmStatic fun d(tag: String, msg: String) = delegate.d(tag, msg)
    @JvmStatic fun d(tag: String, msg: String, tr: Throwable?) = delegate.d(tag, msg, tr)
    @JvmStatic fun i(tag: String, msg: String) = delegate.i(tag, msg)
    @JvmStatic fun i(tag: String, msg: String, tr: Throwable?) = delegate.i(tag, msg, tr)
    @JvmStatic fun w(tag: String, msg: String) = delegate.w(tag, msg)
    @JvmStatic fun w(tag: String, tr: Throwable?) = delegate.w(tag, tr)
    @JvmStatic fun w(tag: String, msg: String, tr: Throwable?) = delegate.w(tag, msg, tr)
    @JvmStatic fun e(tag: String, msg: String) = delegate.e(tag, msg)
    @JvmStatic fun e(tag: String, msg: String, tr: Throwable?) = delegate.e(tag, msg, tr)
    @JvmStatic fun isLoggable(tag: String, level: Int) = delegate.isLoggable(tag, level)
}
