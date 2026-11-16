/*
 * Explorer: the matching core of the ad blocker.
 * Own code, GPL-3.0.
 *
 * Modelled on Chromite's blocker (Chromium's own UI on Android, with a filter engine in front of
 * the network stack): the lists are EasyList and EasyPrivacy in the Adblock Plus syntax, a rule is
 * a pattern with `||`, `*`, `^` and `|`, an exception is a rule that starts with `@@`, an element
 * rule is `domain##selector`, and a blocked request is answered before it leaves the app. FOSS
 * Browser's own blocker — one file of bare host names, matched whole — is gone: it could only stop
 * a request whose domain sat in its list, character for character.
 *
 * This class has no Android in it on purpose: it is where every rule is parsed and matched, and a
 * plain unit test can feed it a list and ask it for a verdict.
 */
package com.rpooi3.explorer.browser

/** One list parsed into the rules the engine matches, plus the element rules it hides with. */
class AdFilter private constructor(
    private val buckets: Map<String, List<Rule>>,
    private val keywordless: List<Rule>,
    private val genericCosmetic: List<String>,
    private val domainCosmetic: Map<String, List<String>>,
    private val cosmeticExceptions: Map<String, List<String>>,
    val networkRuleCount: Int,
    val cosmeticRuleCount: Int,
) {

    val isEmpty: Boolean get() = buckets.isEmpty() && keywordless.isEmpty()

    /** Whether [url] is blocked on a page of [pageHost]; a rule with `@@` overrides any block. */
    fun isBlocked(
        url: String,
        pageHost: String? = null,
        resourceType: String? = null,
        thirdParty: Boolean? = null,
    ): Boolean {
        if (url.isEmpty()) return false
        val lowered = url.lowercase()
        var allowed = false
        for (rule in candidates(lowered)) {
            if (rule.options.unsupported) continue
            if (!rule.options.accepts(resourceType, thirdParty, pageHost)) continue
            if (!rule.pattern.matches(lowered)) continue
            if (rule.exception) allowed = true else if (!allowed) return true
        }
        return false
    }

    /** The element rules that apply to a page of [pageHost], as one CSS text; empty if none. */
    fun cosmeticCss(pageHost: String?): String {
        if (genericCosmetic.isEmpty() && domainCosmetic.isEmpty()) return ""
        val selectors = LinkedHashSet<String>()
        selectors.addAll(genericCosmetic)
        val host = (pageHost ?: "").lowercase().trim().removePrefix("www.")
        if (host.isNotEmpty()) {
            var part = host
            while (part.isNotEmpty()) {
                domainCosmetic[part]?.let { selectors.addAll(it) }
                val dot = part.indexOf('.')
                if (dot < 0) break
                part = part.substring(dot + 1)
            }
            var excluded = host
            while (excluded.isNotEmpty()) {
                cosmeticExceptions[excluded]?.let { selectors.removeAll(it.toSet()) }
                val dot = excluded.indexOf('.')
                if (dot < 0) break
                excluded = excluded.substring(dot + 1)
            }
        }
        if (selectors.isEmpty()) return ""
        return selectors.joinToString(",\n") + "{display:none !important}"
    }

    /** The rules that may match [lowered]: those whose keyword the url carries, and the rest. */
    private fun candidates(lowered: String): List<Rule> {
        if (buckets.isEmpty()) return keywordless
        if (buckets.size == 1 && keywordless.isEmpty()) {
            // A hosts list, whose rules have one shape: every rule is a candidate.
            return buckets.values.first()
        }
        val out = LinkedHashSet<Rule>()
        out.addAll(keywordless)
        for (keyword in keywordsOf(lowered)) {
            buckets[keyword]?.let { out.addAll(it) }
        }
        return out.toList()
    }

    companion object {

        /** The generic element rules Explorer injects into every page. */
        const val MAX_GENERIC_COSMETIC = 3000

        /** The shortest keyword a rule is filed under; shorter ones match too much to be useful. */
        private const val MIN_KEYWORD = 4

        /** The longest keyword a url is searched for; longer substrings would not pay off. */
        private const val MAX_KEYWORD = 16

        @JvmStatic val EMPTY = AdFilter(emptyMap(), emptyList(), emptyList(), emptyMap(), emptyMap(), 0, 0)

        /** Parses a filter list in the Adblock Plus syntax, or a hosts file, into a filter. */
        @JvmStatic
        fun parse(lines: Sequence<String>): AdFilter {
            val buckets = HashMap<String, MutableList<Rule>>(1 shl 12)
            val keywordless = ArrayList<Rule>()
            val generic = ArrayList<String>()
            val specific = HashMap<String, MutableList<String>>()
            val exceptions = HashMap<String, MutableList<String>>()
            var network = 0
            var cosmetic = 0

            for (raw in lines) {
                val line = raw.trim()
                if (line.isEmpty() || line.startsWith("!") || line.startsWith("[Adblock")) continue
                val hosts = hostsRule(line)
                if (hosts != null) {
                    val pattern = UrlPattern.ofHost(hosts.lowercase())
                    val rule = Rule(pattern, false, Options.ANY, pattern.keyword)
                    val keyword = pattern.keyword
                    if (keyword == null) keywordless.add(rule)
                    else buckets.getOrPut(keyword) { ArrayList() }.add(rule)
                    network++
                    continue
                }
                if (line.contains("##") || line.contains("#@#")) {
                    val parsed = CosmeticRule.parse(line) ?: continue
                    cosmetic++
                    if (parsed.exception) {
                        // `domain#@#selector`: the rule is taken back on that domain. Without a
                        // domain it takes back nothing in particular, so it is left out.
                        for (domain in parsed.domains) {
                            exceptions.getOrPut(domain) { ArrayList() }.addAll(parsed.selectors)
                        }
                    } else if (parsed.domains.isEmpty()) {
                        generic.addAll(parsed.selectors)
                        // `~domain##selector` hides everywhere but on that domain.
                        for (domain in parsed.negated) {
                            exceptions.getOrPut(domain) { ArrayList() }.addAll(parsed.selectors)
                        }
                    } else {
                        for (domain in parsed.domains) {
                            specific.getOrPut(domain) { ArrayList() }.addAll(parsed.selectors)
                        }
                    }
                    continue
                }
                if (line.contains("#?#") || line.contains("#$#") || line.contains("#@?#")) continue
                val rule = Rule.parse(line) ?: continue
                network++
                val keyword = rule.keyword
                if (keyword == null) keywordless.add(rule)
                else buckets.getOrPut(keyword) { ArrayList() }.add(rule)
            }

            val cappedGeneric = if (generic.size > MAX_GENERIC_COSMETIC) {
                generic.subList(0, MAX_GENERIC_COSMETIC)
            } else {
                generic
            }
            return AdFilter(
                buckets.mapValues { it.value.toList() },
                keywordless,
                cappedGeneric.toList(),
                specific.mapValues { it.value.distinct() },
                exceptions.mapValues { it.value.distinct() },
                network,
                cosmetic,
            )
        }

        /** The domain of a hosts line (`0.0.0.0 ads.example.com`), or null if it is not one. */
        private fun hostsRule(line: String): String? {
            if (line.startsWith("#")) return null
            val space = line.indexOfFirst { it == ' ' || it == '\t' }
            if (space <= 0) return null
            val address = line.substring(0, space)
            if (address != "0.0.0.0" && address != "127.0.0.1" && address != "::1") return null
            val host = line.substring(space).trim().split(' ', '\t')[0]
            if (host.isEmpty() || host == "localhost" || host == "localhost.localdomain") return null
            val valid = host.all { it.isLetterOrDigit() || it == '.' || it == '-' || it == '_' }
            return if (valid && host.contains('.')) host else null
        }

        /** Every substring of [url] a rule could be filed under, without duplicates. */
        private fun keywordsOf(url: String): Set<String> {
            val out = HashSet<String>(64)
            var start = -1
            var index = 0
            while (index <= url.length) {
                val alnum = index < url.length && url[index].isLetterOrDigit()
                if (alnum && start < 0) start = index
                if (!alnum && start >= 0) {
                    val token = url.substring(start, index)
                    if (token.length >= MIN_KEYWORD) {
                        val longest = minOf(MAX_KEYWORD, token.length)
                        for (from in token.indices) {
                            val limit = minOf(longest, token.length - from)
                            for (length in MIN_KEYWORD..limit) {
                                out.add(token.substring(from, from + length))
                            }
                        }
                    }
                    start = -1
                }
                index++
            }
            return out
        }

        /** The host of a url, without the scheme, the port and the credentials. */
        @JvmStatic
        fun hostOf(url: String?): String? {
            if (url.isNullOrEmpty()) return null
            var rest = url
            val scheme = rest.indexOf("://")
            if (scheme >= 0) rest = rest.substring(scheme + 3)
            val at = rest.indexOf('@')
            if (at >= 0) rest = rest.substring(at + 1)
            var end = rest.length
            for (mark in charArrayOf('/', '?', '#')) {
                val found = rest.indexOf(mark)
                if (found >= 0 && found < end) end = found
            }
            rest = rest.substring(0, end)
            val colon = rest.lastIndexOf(':')
            if (colon >= 0 && rest.indexOf(']') < colon) rest = rest.substring(0, colon)
            return rest.lowercase().ifEmpty { null }
        }
    }

    /** One rule of a list: the pattern, the exception marker and the `$` options behind it. */
    internal class Rule(
        val pattern: UrlPattern,
        val exception: Boolean,
        val options: Options,
        val keyword: String?,
    ) {
        companion object {
            fun parse(line: String): Rule? {
                var text = line
                val exception = text.startsWith("@@")
                if (exception) text = text.substring(2)
                var options = Options.ANY
                val dollar = text.lastIndexOf('$')
                if (dollar >= 0 && dollar < text.length - 1) {
                    options = Options.parse(text.substring(dollar + 1)) ?: return null
                    text = text.substring(0, dollar)
                }
                val pattern = UrlPattern.parse(text) ?: return null
                return Rule(pattern, exception, options, pattern.keyword)
            }
        }
    }

    /** A url pattern: `||` anchors it to a host, `*` is anything, `^` a separator. */
    internal class UrlPattern private constructor(
        private val segments: List<String>,
        private val separators: List<Boolean>,
        private val startAnchor: Boolean,
        private val hostAnchor: Boolean,
        private val endAnchor: Boolean,
        val keyword: String?,
        private val text: String,
    ) {

        /** Whether this pattern is a substring of [url], with its anchors and separators met. */
        fun matches(url: String): Boolean {
            if (segments.isEmpty()) return true
            val first = segments[0]
            var from = 0
            var position = -1
            while (from <= url.length) {
                val found = if (first.isEmpty()) from else url.indexOf(first, from)
                if (found < 0) break
                if (anchoredStart(url, found)) {
                    position = found
                    break
                }
                from = found + 1
            }
            if (position < 0) return false
            var current = position + first.length
            for (index in 1 until segments.size) {
                if (separators[index]) {
                    if (current >= url.length) return false
                    if (!isSeparator(url[current])) return false
                    current++
                }
                val segment = segments[index]
                if (segment.isEmpty()) {
                    if (index == segments.size - 1) {
                        return !endAnchor || current == url.length
                    }
                    continue
                }
                val found = url.indexOf(segment, current)
                if (found < 0) return false
                current = found + segment.length
            }
            return !endAnchor || current == url.length || url[current] == '/' ||
                url[current] == '?' || url[current] == '#'
        }

        private fun anchoredStart(url: String, index: Int): Boolean {
            if (startAnchor) return index == 0
            if (hostAnchor) {
                if (index == 0) return true
                val before = url[index - 1]
                return before == '.' || before == '/'
            }
            return true
        }

        override fun toString(): String = text

        companion object {

            /** A `||host^` rule, as a hosts file writes it; filed under its longest label. */
            fun ofHost(host: String): UrlPattern {
                val lowered = host.lowercase()
                val keyword = lowered.split('.').filter { it.isNotEmpty() }
                    .maxByOrNull { it.length }
                    ?.takeIf { it.length >= MIN_KEYWORD }
                return UrlPattern(
                    listOf(lowered),
                    listOf(false),
                    false,
                    true,
                    true,
                    keyword,
                    "||" + lowered + "^",
                )
            }

            fun parse(text: String): UrlPattern? {
                var rest = text
                var startAnchor = false
                var hostAnchor = false
                var endAnchor = false
                if (rest.startsWith("||")) {
                    hostAnchor = true
                    rest = rest.substring(2)
                } else if (rest.startsWith("|")) {
                    startAnchor = true
                    rest = rest.substring(1)
                }
                if (rest.endsWith("|")) {
                    endAnchor = true
                    rest = rest.dropLast(1)
                }
                if (rest.isEmpty()) return null
                val segments = ArrayList<String>()
                val separators = ArrayList<Boolean>()
                var pending = StringBuilder()
                var needsSeparator = false
                var index = 0
                while (index < rest.length) {
                    when (val c = rest[index]) {
                        '*' -> {
                            if (pending.isNotEmpty() || segments.isEmpty()) {
                                segments.add(pending.toString())
                                separators.add(needsSeparator)
                                pending = StringBuilder()
                            }
                            needsSeparator = false
                        }
                        '^' -> {
                            segments.add(pending.toString())
                            separators.add(needsSeparator)
                            pending = StringBuilder()
                            needsSeparator = true
                        }
                        else -> pending.append(c)
                    }
                    index++
                }
                segments.add(pending.toString())
                separators.add(needsSeparator)
                val cleaned = ArrayList<String>(segments.size)
                val cleanedSeparators = ArrayList<Boolean>(segments.size)
                for (i in segments.indices) {
                    if (segments[i].isEmpty() && i > 0 && i < segments.size - 1 &&
                        separators[i] && separators[i + 1]
                    ) {
                        // A `^^` is one separator too many; the next segment keeps the requirement.
                        continue
                    }
                    cleaned.add(segments[i])
                    cleanedSeparators.add(separators[i])
                }
                if (cleaned.isEmpty()) return null
                val keyword = keywordOf(cleaned)
                return UrlPattern(cleaned, cleanedSeparators, startAnchor, hostAnchor, endAnchor, keyword, text)
            }

            /**
             * The literal a rule is filed under: its longest run of letters and digits. A url is
             * asked for these first, so a list of a hundred thousand rules costs a few lookups.
             */
            private fun keywordOf(segments: List<String>): String? {
                var best: String? = null
                for (segment in segments) {
                    var runStart = -1
                    var i = 0
                    while (i <= segment.length) {
                        val c = if (i < segment.length) segment[i] else null
                        val alnum = c != null && c.isLetterOrDigit()
                        if (alnum && runStart < 0) runStart = i
                        if (!alnum && runStart >= 0) {
                            val run = segment.substring(runStart, i)
                            if (run.length >= MIN_KEYWORD && (best == null || run.length > best.length)) {
                                best = run
                            }
                            runStart = -1
                        }
                        i++
                    }
                }
                return best
            }

            fun isSeparator(c: Char): Boolean =
                !(c.isLetterOrDigit() || c == '_' || c == '-' || c == '.' || c == '%')
        }
    }

    /** The `$` options of a rule, as far as Explorer can honour them with a WebView. */
    internal class Options private constructor(
        private val thirdParty: Int,
        private val types: Set<String>?,
        private val notTypes: Set<String>,
        private val domains: List<String>,
        private val notDomains: List<String>,
        internal val unsupported: Boolean,
    ) {

        fun accepts(resourceType: String?, thirdParty: Boolean?, pageHost: String?): Boolean {
            if (this.thirdParty == FIRST_PARTY && thirdParty == true) return false
            if (this.thirdParty == THIRD_PARTY && thirdParty == false) return false
            if (types != null) {
                if (resourceType == null) return false
                if (resourceType !in types) return false
            }
            if (resourceType != null && resourceType in notTypes) return false
            if (domains.isNotEmpty()) {
                if (pageHost == null) return false
                if (domains.none { matchesDomain(pageHost, it) }) return false
            }
            if (notDomains.isNotEmpty() && pageHost != null) {
                if (notDomains.any { matchesDomain(pageHost, it) }) return false
            }
            return true
        }

        private fun matchesDomain(host: String, domain: String): Boolean =
            host == domain || host.endsWith(".$domain")

        companion object {

            private const val ANY_PARTY = 0
            private const val FIRST_PARTY = 1
            private const val THIRD_PARTY = 2

            val ANY = Options(ANY_PARTY, null, emptySet(), emptyList(), emptyList(), false)

            /** What Chromium can do with a request, which is what the options talk about. */
            private val TYPES = setOf(
                "script", "image", "stylesheet", "object", "xmlhttprequest", "subdocument",
                "document", "font", "media", "websocket", "ping", "other",
            )

            /** An option Explorer cannot honour; the rule is left out rather than misapplied. */
            private val UNSUPPORTED = setOf(
                "redirect", "redirect-rule", "csp", "removeparam", "replace", "header",
                "permissions", "rewrite", "cookie", "uritransform", "elemhide", "generichide",
                "specifichide", "inline-script", "inline-font", "elemhide-emulation",
            )

            private val ALIASES = mapOf(
                "xhr" to "xmlhttprequest", "css" to "stylesheet", "frame" to "subdocument",
                "object-subrequest" to "object", "beacon" to "ping",
            )

            fun parse(text: String): Options? {
                var party = ANY_PARTY
                var types: MutableSet<String>? = null
                val notTypes = HashSet<String>()
                val domains = ArrayList<String>()
                val notDomains = ArrayList<String>()
                var unsupported = false
                for (raw in text.split(',')) {
                    var token = raw.trim().lowercase()
                    if (token.isEmpty()) continue
                    var negated = false
                    if (token.startsWith("~")) {
                        negated = true
                        token = token.substring(1)
                    }
                    when {
                        token == "third-party" || token == "3p" -> {
                            if (negated) party = FIRST_PARTY else party = THIRD_PARTY
                        }
                        token.startsWith("domain=") -> {
                            for (entry in token.removePrefix("domain=").split('|')) {
                                val domain = entry.trim()
                                if (domain.isEmpty()) continue
                                if (domain.startsWith("~")) notDomains.add(domain.substring(1))
                                else domains.add(domain)
                            }
                        }
                        token == "match-case" || token == "important" -> Unit
                        token in UNSUPPORTED -> unsupported = true
                        token in TYPES -> {
                            if (negated) notTypes.add(token) else {
                                if (types == null) types = HashSet()
                                types!!.add(token)
                            }
                        }
                        ALIASES.containsKey(token) -> {
                            val type = ALIASES.getValue(token)
                            if (negated) notTypes.add(type) else {
                                if (types == null) types = HashSet()
                                types!!.add(type)
                            }
                        }
                        else -> unsupported = true
                    }
                }
                return Options(party, types, notTypes, domains, notDomains, unsupported)
            }
        }
    }

    /** A `domain##selector` rule, and its `domain#@#selector` counterpart that takes it back. */
    internal class CosmeticRule(
        val domains: List<String>,
        val negated: List<String>,
        val exception: Boolean,
        val selectors: List<String>,
    ) {
        companion object {
            fun parse(line: String): CosmeticRule? {
                val marker = if (line.contains("#@#")) "#@#" else "##"
                val index = line.indexOf(marker)
                if (index < 0) return null
                var selectorText = line.substring(index + marker.length).trim()
                if (selectorText.isEmpty()) return null
                // Rules that style an element, or that run a script, are not element hiding.
                if (selectorText.startsWith("+js(") || selectorText.contains(":style(") ||
                    selectorText.contains(":remove(") || selectorText.contains(":has-text(") ||
                    selectorText.contains(":xpath(") || selectorText.contains("{")
                ) {
                    return null
                }
                if (selectorText.contains(":")) {
                    // Drop the pseudo-classes Chromium does not know, which would void the list.
                    selectorText = selectorText.replace(":has(", ":is(")
                }
                val domainText = line.substring(0, index).trim()
                val domains = ArrayList<String>()
                val negated = ArrayList<String>()
                if (domainText.isNotEmpty()) {
                    for (entry in domainText.split(',')) {
                        val domain = entry.trim().lowercase()
                        if (domain.isEmpty() || domain.contains('*')) continue
                        if (domain.startsWith("~")) negated.add(domain.substring(1))
                        else domains.add(domain)
                    }
                }
                return CosmeticRule(domains, negated, marker == "#@#", listOf(selectorText))
            }
        }
    }
}
