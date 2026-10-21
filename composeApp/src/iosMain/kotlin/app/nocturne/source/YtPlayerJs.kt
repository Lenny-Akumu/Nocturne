@file:OptIn(ExperimentalForeignApi::class, BetaInteropApi::class)

package app.nocturne.source

import app.nocturne.nowMillis
import io.ktor.client.HttpClient
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.statement.bodyAsText
import io.ktor.http.isSuccess
import kotlinx.cinterop.BetaInteropApi
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.JsonPrimitive
import platform.Foundation.NSCachesDirectory
import platform.Foundation.NSFileManager
import platform.Foundation.NSSearchPathForDirectoriesInDomains
import platform.Foundation.NSString
import platform.Foundation.NSUTF8StringEncoding
import platform.Foundation.NSUserDomainMask
import platform.Foundation.create
import platform.Foundation.stringWithContentsOfFile
import platform.Foundation.writeToFile
import platform.JavaScriptCore.JSContext
import kotlin.coroutines.cancellation.CancellationException

// Port of NewPipeExtractor's YoutubeJavaScriptPlayerManager (n-param + signature deobfuscation), run with JavaScriptCore.
internal class YtPlayerJs(private val http: HttpClient) {
    private class Player(val id: String, val code: String) {
        val ctx = JSContext() // standalone extracted functions (NewPipe's approach)
        var prepared = false
        var global: Pair<String, String>? = null // declaration, variable name
        var full: JSContext? = null // whole base.js, used when standalone extraction fails
        var fullTried = false
        var nCtx: JSContext? = null
        var nFn: String? = null
        var nError: Exception? = null
        var sigCtx: JSContext? = null
        var sigError: Exception? = null
        val nCache = HashMap<String, String>()
    }

    private val lock = Mutex()
    private val players = HashMap<String, Player>()
    private var current: Player? = null
    private var checkedAt = 0L
    private var failure: Exception? = null
    private var failedAt = 0L

    // Returns the url with its n parameter deobfuscated (throws if that isn't possible).
    suspend fun throttled(videoId: String, url: String): String {
        if ("&n=" !in url && "?n=" !in url) return url
        val m = N_PARAM.find(url) ?: return url
        val n = m.groupValues[1]
        val out = lock.withLock {
            val p = player(videoId)
            p.nCache[n] ?: withContext(Dispatchers.Default) { runN(p, n) }.also { p.nCache[n] = it }
        }
        return url.replaceRange(m.range.first + 3, m.range.last + 1, out)
    }

    suspend fun signature(videoId: String, s: String): String = lock.withLock {
        val p = player(videoId)
        withContext(Dispatchers.Default) { runSig(p, s) }
    }

    private fun prepare(p: Player) {
        if (p.prepared) return
        p.prepared = true
        p.global = globalVars(p.code).firstOrNull { p.ctx.define(it.first) }
    }

    private fun runN(p: Player, n: String): String {
        prepare(p)
        p.nError?.let { throw it }
        val known = p.nCtx
        val knownFn = p.nFn
        if (known != null && knownFn != null) return known.call(knownFn, n)?.takeIf { it.isNotEmpty() } ?: error("Extracted n-parameter is empty")
        val names = nNames(p.code).toList()
        for (name in names) {
            val fn = try { nFunction(p.code, name, p.global?.second) } catch (e: Exception) { continue }
            if (!p.ctx.define(fn)) continue
            val out = validN(p.ctx, name, n) ?: continue
            p.nCtx = p.ctx
            p.nFn = name
            return out
        }
        // Current players reference many globals from the n function, so run the whole player instead.
        fullPlayer(p, names)?.let { ctx ->
            for (name in names) {
                val fn = "__nx[${q(name)}]"
                val out = validN(ctx, fn, n) ?: continue
                p.nCtx = ctx
                p.nFn = fn
                return out
            }
        }
        throw IllegalStateException("Couldn't extract the throttling function").also { p.nError = it }
    }

    private fun validN(ctx: JSContext, fn: String, n: String): String? {
        val out = ctx.call(fn, n) ?: return null
        if (out == n || !out.matches(N_VALUE) || out.startsWith("enhanced_except") || "_w8_" in out) return null
        return out.takeIf { ctx.call(fn, n.reversed()) != out } // constant result: wrong function
    }

    private fun runSig(p: Player, s: String): String {
        prepare(p)
        p.sigError?.let { throw it }
        p.sigCtx?.let { return it.call(SIG_FN_NAME, s)?.takeIf { r -> r.isNotEmpty() } ?: error("Couldn't run the signature function") }
        val standalone = try { sigCode(p.code, p.global?.first) } catch (e: Exception) { null }
        val ctx = if (standalone != null && p.ctx.define(standalone) && !p.ctx.call(SIG_FN_NAME, s).isNullOrEmpty()) p.ctx
            else fullPlayer(p, nNames(p.code).toList())?.takeIf { !it.call(SIG_FN_NAME, s).isNullOrEmpty() }
        if (ctx == null) throw IllegalStateException("Couldn't parse the signature function").also { p.sigError = it }
        p.sigCtx = ctx
        return ctx.call(SIG_FN_NAME, s)!!
    }

    // Evaluates base.js with browser stubs, exporting the candidate n functions and the signature function from its closure.
    private fun fullPlayer(p: Player, nNames: List<String>): JSContext? {
        if (p.fullTried) return p.full
        p.fullTried = true
        val end = p.code.lastIndexOf("})(_yt_player)").takeIf { it > 0 } ?: return null
        val exports = buildString {
            append(";globalThis.__nx={};")
            nNames.filter { it.matches(IDENT) && it !in JsLexer.KEYWORDS }.forEach { append("try{__nx[${q(it)}]=$it}catch(e){}") }
            sigName(p.code)?.takeIf { it.first.matches(IDENT) }?.let { (name, params) ->
                append("globalThis.$SIG_FN_NAME=function(a){return $name(${params}a)};")
            }
        }
        val ctx = JSContext()
        ctx.js(PLAYER_PRELUDE + "\n" + p.code.substring(0, end) + exports + p.code.substring(end))
        return ctx.takeIf { it.js("typeof __nx") == "object" }.also { p.full = it }
    }

    private suspend fun player(videoId: String): Player {
        val cur = current
        if (cur != null && nowMillis() - checkedAt < TTL) return cur
        if (cur == null && nowMillis() - failedAt < 60_000) failure?.let { throw it }
        val p = try {
            load(iframeUrl())
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            try {
                load(embedUrl(videoId))
            } catch (e2: CancellationException) {
                throw e2
            } catch (e2: Exception) {
                cur ?: throw IllegalStateException("Couldn't get YouTube's player: ${e2.message}").also { failure = it; failedAt = nowMillis() }
            }
        }
        current = p
        checkedAt = nowMillis()
        return p
    }

    private suspend fun iframeUrl(): String {
        val hash = IFRAME_HASH.find(text("https://www.youtube.com/iframe_api"))?.groupValues?.get(1) ?: error("No player hash")
        return "https://www.youtube.com/s/player/$hash/player_ias.vflset/en_GB/base.js"
    }

    private suspend fun embedUrl(videoId: String): String {
        val page = text("https://www.youtube.com/embed/$videoId")
        val src = EMBED_SCRIPT.find(page)?.groupValues?.get(1) ?: EMBED_JS_URL.find(page)?.groupValues?.get(1) ?: error("No player url")
        return when {
            src.startsWith("//") -> "https:$src"
            src.startsWith("/") -> "https://www.youtube.com$src"
            else -> src
        }
    }

    private suspend fun load(url: String): Player {
        val id = PLAYER_ID.find(url)?.groupValues?.get(1) ?: error("Invalid player url")
        players[id]?.let { return it }
        val dir = cacheDir
        val file = dir?.let { "$it/yt-base-$id.js" }
        val code = file?.let { NSString.stringWithContentsOfFile(it, NSUTF8StringEncoding, null) }?.takeIf { it.length > 100_000 }
            ?: text(url).also { c -> if (dir != null && file != null) save(dir, file, c) }
        return Player(id, code).also { players[id] = it }
    }

    private fun save(dir: String, file: String, code: String) {
        val fm = NSFileManager.defaultManager
        fm.contentsOfDirectoryAtPath(dir, null)?.forEach { n ->
            (n as? String)?.takeIf { it.startsWith("yt-base-") }?.let { fm.removeItemAtPath("$dir/$it", null) }
        }
        NSString.create(string = code).writeToFile(file, true, NSUTF8StringEncoding, null)
    }

    private suspend fun text(url: String): String {
        val r = http.get(url) { header("User-Agent", InnerTube.USER_AGENT) }
        if (!r.status.isSuccess()) error("HTTP ${r.status.value}")
        return r.bodyAsText()
    }

    private companion object {
        const val TTL = 6 * 60 * 60 * 1000L
        val cacheDir: String? by lazy { NSSearchPathForDirectoriesInDomains(NSCachesDirectory, NSUserDomainMask, true).firstOrNull() as? String }
    }
}

// YoutubeThrottlingParameterUtils

private val N_PARAM = Regex("[&?]n=([^&]+)")
private val N_VALUE = Regex("[A-Za-z0-9_-]{4,}")
private val IDENT = Regex("[A-Za-z_$][A-Za-z0-9_$]*")
private const val S = "[a-zA-Z0-9_$]"
private const val M = "$S+"
private const val A = """\[(\d+)\]"""

private val N_NAME = listOf(
    """([A-Za-z0-9_\$]{2,})=function.*return [A-Z]\[\d+\]""",
    S + """="nn"\[\+""" + M + """\.""" + M + """\],""" + M + """\(""" + M + """\),""" + M + "=" + M + """\.""" + M + """\[""" +
        M + """\]\|\|null\)&&\(""" + M + "=(" + M + ")" + A,
    S + """="nn"\[\+""" + M + """\.""" + M + """\],""" + M + """\(""" + M + """\),""" + M + "=" + M + """\.""" + M + """\[""" +
        M + """\]\|\|null\).+\|\|(""" + M + """)\(""\)""",
    "," + M + """\(""" + M + """\),""" + M + "=" + M + """\.""" + M + """\[""" + M + """\]\|\|null\)&&\(\b""" + M + "=(" + M + ")" + A +
        """\(""" + S + """\),""" + M + """\.set\((?:"n+"|""" + M + "),"  + M + """\)""",
    S + """="nn"\[\+""" + M + """\.""" + M + """\],""" + M + "=" + M + """\.get\(""" + M + """\)\).+\|\|(""" + M + """)\(""\)""",
    S + """="nn"\[\+""" + M + """\.""" + M + """\],""" + M + "=" + M + """\.get\(""" + M + """\)\)&&\(""" + M + "=(" + M + """)\[(\d+)\]""",
    """\(""" + S + """=String\.fromCharCode\(110\),""" + S + "=" + S + """\.get\(""" + S + """\)\)&&\(""" + S + "=(" + M + ")(?:" + A +
        """)?\(""" + S + """\)""",
    """\.get\("n"\)\)&&\(""" + S + "=(" + M + ")(?:" + A + """)?\(""" + S + """\)""",
    // Not in NewPipe: `if(Z=U[w[8]][w[40]]||null)Z=g5G[0](Z),U[w[11]](w[40],Z)` (2026 players)
    """\|\|null\)(?:&&\()?""" + M + "=(" + M + ")" + A + """\(""" + M + """\),""" + M + """(?:\.set|\[""" + M + """\[\d+\]\])\(""",
).map { Regex(it) }

private const val N_BODY = """=\s*function([\S\s]*?\}\s*return [\w$]+?\.join\(""\)\s*\};)"""
private val FN_ARGS = Regex("""function\s*[a-zA-Z0-9_$]*\s*\(\s*([^)]*)\s*\)""")
private const val EARLY_RETURN = """;\s*if\s*\(\s*typeof\s+[a-zA-Z0-9_$]+\s*===?\s*(["'])undefined\1\s*\)\s*return\s+"""

// Candidate n-function names, in NewPipe's pattern order.
private fun nNames(code: String): Sequence<String> = N_NAME.asSequence().mapNotNull { r ->
    val m = r.find(code) ?: return@mapNotNull null
    val name = m.groupValues[1]
    val idx = m.groupValues.getOrNull(2)?.toIntOrNull() ?: return@mapNotNull name
    Regex("var " + Regex.escape(name) + """\s*=\s*\[(.+?)\][;,]""").find(code)?.groupValues?.get(1)?.split(",")?.getOrNull(idx)?.trim()
}.distinct()

private fun nFunction(code: String, name: String, globalName: String?): String {
    val fn = try {
        "$name=function" + matchToClosingBrace(code, "$name=function") + ";"
    } catch (e: Exception) {
        val body = Regex("(?s)" + Regex.escape(name) + N_BODY).find(code)?.groupValues?.get(1) ?: error("n function not found")
        "function $name$body"
    }
    // Remove `if(typeof X==="undefined")return a;` guards that break the function standalone.
    val arg = FN_ARGS.find(fn)?.groupValues?.get(1)?.split(",")?.firstOrNull()?.trim()?.takeIf { it.isNotEmpty() } ?: error("No n function argument")
    var out = Regex("(?s)" + EARLY_RETURN + Regex.escape(arg) + ";").replaceFirst(fn, ";")
    if (globalName != null) {
        out = Regex("""(?s);\s*if\s*\(\s*typeof\s+[a-zA-Z0-9_$]+\s*===?\s*""" + Regex.escape(globalName) + """\[\d+\]\s*\)\s*return\s+""" +
            Regex.escape(arg) + ";").replaceFirst(out, ";")
    }
    return out
}

// YoutubeSignatureUtils

private val SIG_NAME = listOf(
    """\b(?:[a-zA-Z0-9_$]+)&&\((?:[a-zA-Z0-9_$]+)=([a-zA-Z0-9_$]{2,})\((\d+,)decodeURIComponent\((?:[a-zA-Z0-9_$]+)\)\)""",
    """\b(?:[a-zA-Z0-9_$]+)&&\((?:[a-zA-Z0-9_$]+)=([a-zA-Z0-9_$]{2,})\(decodeURIComponent\((?:[a-zA-Z0-9_$]+)\)\)""",
    """\bm=([a-zA-Z0-9$]{2,})\(decodeURIComponent\(h\.s\)\)""",
    """\bc&&\(c=([a-zA-Z0-9$]{2,})\(decodeURIComponent\(c\)\)""",
    """(?:\b|[^a-zA-Z0-9$])([a-zA-Z0-9$]{2,})\s*=\s*function\(\s*a\s*\)\s*\{\s*a\s*=\s*a\.split\(\s*""\s*\)""",
    """([\w$]+)\s*=\s*function\((\w+)\)\{\s*\2=\s*\2\.split\(""\)\s*;""",
).map { Regex(it) }

private val GLOBAL_VAR = Regex("""(var [A-z]=['"].*['"].split\("[;{]"\))""")
private val GLOBAL_VAR_STRICT = Regex("""(var ([a-zA-Z0-9_$]+)=(?:"[^"\\]*(?:\\.[^"\\]*)*"|'[^'\\]*(?:\\.[^'\\]*)*')\.split\((?:"[^"]*"|'[^']*')\))""")
private val VAR_NAME = Regex("""var ([a-zA-Z0-9_$]+)""")
private val HELPER_NAME = Regex("""[;,]([A-Za-z0-9_$]{2,})\[..""")

private fun globalVars(code: String): List<Pair<String, String>> =
    listOfNotNull(GLOBAL_VAR.find(code), GLOBAL_VAR_STRICT.find(code)).mapNotNull { m ->
        val decl = m.groupValues[1]
        VAR_NAME.find(decl)?.groupValues?.get(1)?.let { decl to it }
    }.distinct()

// Name and extra leading call arguments of the signature function.
private fun sigName(code: String): Pair<String, String>? = SIG_NAME.firstNotNullOfOrNull { r ->
    // Only the numeric-argument pattern carries extra call parameters.
    r.find(code)?.let { m -> m.groupValues[1] to (m.groupValues.getOrNull(2)?.takeIf { it.endsWith(",") } ?: "") }
}

private fun sigCode(code: String, globalVar: String?): String {
    val (name, params) = sigName(code) ?: error("deobfuscation function not found")
    val fn = try {
        "$name=function" + matchToClosingBrace(code, "$name=function")
    } catch (e: Exception) {
        "var " + (Regex("(" + Regex.escape(name) + """=function\([a-zA-Z0-9_]+\)\{.+?\})""").find(code)?.groupValues?.get(1) ?: error("deobfuscation function body not found"))
    }
    val helperName = HELPER_NAME.find(fn)?.groupValues?.get(1) ?: error("helper object name not found")
    val helper = Regex("(var " + Regex.escape(helperName) + """=\{[\s\S]+?\}\};)""").find(code)?.groupValues?.get(1)?.replace("\n", "")
        ?: error("helper object not found")
    return (globalVar?.let { "$it;" } ?: "") + helper + fn + ";function $SIG_FN_NAME(a){return $name(${params}a);}"
}

private const val SIG_FN_NAME = "deobfuscate"

// JavaScriptCore helpers (errors are caught in JS so no JSValue exception handling is needed)

private fun q(s: String) = JsonPrimitive(s).toString()

private fun JSContext.js(script: String): String? = evaluateScript(script)?.toString()

private fun JSContext.define(code: String): Boolean = js("try{(0,eval)(${q(code)});'ok'}catch(e){'err'}") == "ok"

private fun JSContext.call(fn: String, arg: String): String? =
    js("(function(){try{var r=$fn(${q(arg)});return r==null?'err':'ok:'+r}catch(e){return 'err'}})()")
        ?.takeIf { it.startsWith("ok:") }?.substring(3)

// JavaScriptExtractor.matchToClosingBrace: returns the code from after `start` up to the brace closing the first block.
private fun matchToClosingBrace(code: String, start: String): String {
    val i = code.indexOf(start).takeIf { it >= 0 }?.plus(start.length) ?: error("Start not found")
    val lexer = JsLexer(code, i)
    var open = false
    while (true) {
        val t = lexer.next()
        when {
            t.isP("{") -> open = true
            open && lexer.balanced -> return code.substring(i, t.end)
            t.k == JsLexer.K.EOF -> error("Could not find matching braces")
        }
    }
}

// Minimal browser globals so base.js can be evaluated in a bare JSContext.
private const val PLAYER_PRELUDE = """var _yt_player={};var window=globalThis,self=globalThis;
var location={href:"https://www.youtube.com/watch",protocol:"https:",host:"www.youtube.com",hostname:"www.youtube.com",origin:"https://www.youtube.com",pathname:"/watch",search:"",hash:""};
var navigator={userAgent:"Mozilla/5.0",platform:"",language:"en",languages:["en"],vendor:""};
var __el=function(){return{style:{},setAttribute:function(){},getAttribute:function(){return null},appendChild:function(){},removeChild:function(){},addEventListener:function(){},removeEventListener:function(){},getElementsByTagName:function(){return[]},querySelector:function(){return null},querySelectorAll:function(){return[]},classList:{add:function(){},remove:function(){},contains:function(){return false}},childNodes:[],children:[]}};
var document={createElement:__el,createElementNS:__el,createTextNode:__el,getElementById:function(){return null},getElementsByTagName:function(){return[]},getElementsByClassName:function(){return[]},querySelector:function(){return null},querySelectorAll:function(){return[]},addEventListener:function(){},removeEventListener:function(){},documentElement:__el(),body:__el(),head:__el(),cookie:"",location:location,referrer:"",readyState:"complete",visibilityState:"visible"};
var setTimeout=function(){return 0},clearTimeout=function(){},setInterval=function(){return 0},clearInterval=function(){},XMLHttpRequest=function(){};"""

private val IFRAME_HASH = Regex("""player\\/([a-z0-9]{8})\\/""")
private val EMBED_SCRIPT = Regex("<script[^>]*\\ssrc=\"([^\"]*base\\.js[^\"]*)\"")
private val EMBED_JS_URL = Regex("\"jsUrl\":\"(/s/player/[A-Za-z0-9]+/player_ias\\.vflset/[A-Za-z_-]+/base\\.js)\"")
private val PLAYER_ID = Regex("/s/player/([A-Za-z0-9_-]+)/")

// Port of NewPipe's jsextractor Lexer (regex-vs-division detection and brace/paren bookkeeping).
private class JsLexer(private val s: String, private var pos: Int) {
    enum class K { PUNCT, KEYW, NAME, NUM, STR, TPL, REGEX, COMMENT, EOF }
    class Tok(val k: K, val text: String, val end: Int) {
        fun isP(p: String) = k == K.PUNCT && text == p
        fun isKw(w: String) = k == K.KEYW && text == w
    }
    class Paren(val funcExpr: Boolean, val conditional: Boolean)
    class Brace(val isBlock: Boolean, val paren: Paren?)
    class Meta(val tok: Tok, val line: Int, val paren: Paren? = null, val brace: Brace? = null)

    private val last = arrayOfNulls<Meta>(3)
    val braces = ArrayList<Brace>()
    private val parens = ArrayList<Paren>()
    private var line = 0
    val balanced get() = braces.isEmpty() && parens.isEmpty()

    fun next(): Tok = read().also { keep(it) }

    fun read(): Tok {
        while (pos < s.length && (s[pos].isWhitespace() || s[pos] == '\uFEFF')) { if (s[pos] == '\n') line++; pos++ }
        if (pos >= s.length) return Tok(K.EOF, "", pos)
        val start = pos
        val c = s[pos]
        val n = s.getOrNull(pos + 1)
        when {
            c == '/' && n == '/' -> { while (pos < s.length && s[pos] != '\n' && s[pos] != '\r') pos++; return Tok(K.COMMENT, "", pos) }
            c == '/' && n == '*' -> {
                val e = s.indexOf("*/", pos + 2).takeIf { it >= 0 } ?: error("Unterminated comment")
                for (j in pos until e) if (s[j] == '\n') line++
                pos = e + 2
                return Tok(K.COMMENT, "", pos)
            }
            c == '/' && regexStart() -> { readRegex(); return Tok(K.REGEX, "", pos) }
            c == '"' || c == '\'' -> { readString(c); return Tok(K.STR, "", pos) }
            c == '`' -> { readTemplate(); return Tok(K.TPL, "", pos) }
            c in '0'..'9' || (c == '.' && n != null && n in '0'..'9') -> { readNumber(); return Tok(K.NUM, "", pos) }
            idStart(c) -> {
                while (pos < s.length && idPart(s[pos])) pos++
                val w = s.substring(start, pos)
                return Tok(if (w in KEYWORDS) K.KEYW else K.NAME, w, pos)
            }
        }
        val p = PUNCTS.firstOrNull { s.startsWith(it, pos) } ?: c.toString()
        pos += p.length
        return Tok(K.PUNCT, p, pos)
    }

    private fun push(m: Meta) { last[2] = last[1]; last[1] = last[0]; last[0] = m }

    fun keep(t: Tok) {
        if (t.k == K.COMMENT) return
        if (t.k == K.PUNCT) when (t.text) {
            "(" -> {
                val one = last[0]
                val funcExpr = when {
                    one?.tok?.isKw("function") == true -> last[1]?.let { expr(it.tok) } ?: false
                    last[1]?.tok?.isKw("function") == true -> last[2]?.let { expr(it.tok) } ?: false
                    else -> false
                }
                val p = Paren(funcExpr, one != null && one.tok.k == K.KEYW && one.tok.text in CONDITIONAL)
                parens += p
                return push(Meta(t, line, paren = p))
            }
            "{" -> {
                val one = last[0]
                val block = when {
                    one == null -> true
                    one.tok.isP("(") || one.tok.isP("{") || one.tok.isKw("case") -> false
                    one.tok.isP(":") -> braces.lastOrNull()?.isBlock == true
                    one.tok.isKw("return") || one.tok.isKw("yield") -> last[1]?.let { it.line != line } ?: false
                    else -> !op(one.tok)
                }
                val b = Brace(block, one?.takeIf { it.tok.isP(")") }?.paren)
                braces += b
                return push(Meta(t, line, brace = b))
            }
            ")" -> return push(Meta(t, line, paren = parens.removeLastOrNull() ?: error("unmatched closing paren")))
            "}" -> return push(Meta(t, line, brace = braces.removeLastOrNull() ?: error("unmatched closing brace")))
        }
        push(Meta(t, line))
    }

    private fun op(t: Tok) = (t.k == K.PUNCT && t.text !in NON_OP) || (t.k == K.KEYW && t.text in OP_KEYWORDS)

    private fun expr(t: Tok) = op(t) || t.isKw("return") || t.isKw("case")

    private fun regexStart(): Boolean {
        val one = last[0] ?: return true
        val t = one.tok
        return when {
            t.k == K.KEYW -> t.text != "this"
            t.isP(")") -> one.paren?.conditional == true
            t.isP("}") -> one.brace?.let { b -> if (b.isBlock) b.paren?.let { !it.funcExpr } ?: true else false } ?: true
            t.k == K.PUNCT -> t.text != "]"
            else -> false
        }
    }

    private fun readString(q: Char) {
        pos++
        while (pos < s.length) {
            when (s[pos]) {
                '\\' -> pos += 2
                q -> { pos++; return }
                '\n' -> error("Unterminated string")
                else -> pos++
            }
        }
        error("Unterminated string")
    }

    private fun readTemplate() {
        pos++
        while (pos < s.length) {
            val c = s[pos]
            when {
                c == '\\' -> pos += 2
                c == '`' -> { pos++; return }
                c == '$' && s.getOrNull(pos + 1) == '{' -> pos = skipExpr(pos + 2)
                else -> { if (c == '\n') line++; pos++ }
            }
        }
        error("Unterminated template literal")
    }

    // Skips a template `${...}` expression, returning the index after its closing brace.
    private fun skipExpr(from: Int): Int {
        val sub = JsLexer(s, from)
        while (true) {
            val t = sub.read()
            if (t.k == K.EOF) error("Unterminated template expression")
            if (t.isP("}") && sub.braces.isEmpty()) return t.end
            sub.keep(t)
        }
    }

    private fun readRegex() {
        pos++
        var cls = false
        while (pos < s.length) {
            when (s[pos]) {
                '\\' -> pos++
                '[' -> cls = true
                ']' -> cls = false
                '/' -> if (!cls) { pos++; while (pos < s.length && idPart(s[pos])) pos++; return }
                '\n' -> error("Unterminated regex")
            }
            pos++
        }
        error("Unterminated regex")
    }

    private fun readNumber() {
        val r = s.getOrNull(pos + 1)?.lowercaseChar()
        val radix = s[pos] == '0' && (r == 'x' || r == 'b' || r == 'o')
        pos++
        while (pos < s.length) {
            val c = s[pos]
            if (idPart(c) || c == '.') pos++
            else if ((c == '+' || c == '-') && !radix && (s[pos - 1] == 'e' || s[pos - 1] == 'E')) pos++
            else break
        }
    }

    private fun idStart(c: Char) = c.isLetter() || c == '_' || c == '$' || c == '\\'
    private fun idPart(c: Char) = idStart(c) || c in '0'..'9'

    companion object {
        val KEYWORDS = setOf(
            "return", "new", "delete", "typeof", "null", "this", "false", "true", "throw", "in", "instanceof", "yield", "try",
            "function", "export", "import", "if", "else", "switch", "case", "default", "while", "do", "for", "break", "continue",
            "var", "with", "catch", "finally", "void", "let", "const", "debugger", "class", "enum", "extends", "super",
        )
        val OP_KEYWORDS = setOf("new", "delete", "typeof", "throw", "in", "instanceof", "void")
        val CONDITIONAL = setOf("if", "for", "while", "with")
        val NON_OP = setOf(";", "[", "]", "{", "}", "(", ")", ",", ".", "?.", "=>", "...")
        val PUNCTS = listOf(
            ">>>=", "...", "===", "!==", "**=", "<<=", ">>=", ">>>", "&&=", "||=", "??=",
            "=>", "==", "!=", "<=", ">=", "&&", "||", "??", "?.", "++", "--", "+=", "-=", "*=", "/=", "%=", "&=", "|=", "^=", "<<", ">>", "**",
        )
    }
}
