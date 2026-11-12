package pl.sinusoide.sinuschat.desktop.chat

import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import pl.sinusoide.sinuschat.desktop.PhoneLink
import pl.sinusoide.sinuschat.link.ChatMessage
import pl.sinusoide.sinuschat.link.ChatSummary
import pl.sinusoide.sinuschat.link.LinkFrame
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

sealed interface ChatEvent {
    data class Incoming(val frame: LinkFrame.Incoming) : ChatEvent
    data class SendFailed(val error: String) : ChatEvent
    data class MediaFailed(val error: String) : ChatEvent
    data class Saved(val file: java.io.File) : ChatEvent
    data class Info(val text: String) : ChatEvent
    data class Profile(val field: String, val ok: Boolean, val removed: Boolean = false) : ChatEvent
    data class VoiceFailed(val error: String) : ChatEvent
}

class ChatStore(
    private val link: PhoneLink,
    private val cache: ChatCache = ChatCache(),
    blobs: BlobCache = BlobCache()
) {

    val media = MediaStore(link, blobs)
    val uploads = UploadQueue(link)
    val voice = VoicePlayback()
    val recorder = VoiceRecorder()
    val calls = pl.sinusoide.sinuschat.desktop.call.CallManager(link)

    companion object {
        const val PAGE = 80
        const val ERROR_OFFLINE = "offline"
        private const val TYPING_MS = 6_000L
        private const val TYPING_REPEAT_MS = 2_500L
        private const val GHOST_PREFIX = "pcup:"
        private const val GHOST_LINGER_MS = 15_000L
        private const val LEGACY_GHOST_MS = 2_500L
        private const val PC_SHARE = 0.3f
        private const val PINNED_PAGE = 100
        private const val VOICE_PREFIX = "voice-"
    }

    private class Ghost(val msg: ChatMessage, val batch: String, val staged: String) {
        @Volatile var realId: String? = null
    }

    private val initial = cache.loadChats()

    private val _chats = MutableStateFlow(initial.first)
    val chats: StateFlow<List<ChatSummary>> = _chats

    private val _hidePreviews = MutableStateFlow(initial.second)
    val hidePreviews: StateFlow<Boolean> = _hidePreviews

    private val _phoneLocked = MutableStateFlow(false)
    val phoneLocked: StateFlow<Boolean> = _phoneLocked

    private val _openConv = MutableStateFlow<String?>(null)
    val openConv: StateFlow<String?> = _openConv

    private val _messages = MutableStateFlow<List<ChatMessage>>(emptyList())
    val messages: StateFlow<List<ChatMessage>> = _messages

    private val _hasMore = MutableStateFlow(false)
    val hasMore: StateFlow<Boolean> = _hasMore

    private val _loadingOlder = MutableStateFlow(false)
    val loadingOlder: StateFlow<Boolean> = _loadingOlder

    private val _typing = MutableStateFlow<Map<String, String>>(emptyMap())
    val typing: StateFlow<Map<String, String>> = _typing

    private val _hidden = MutableStateFlow(cache.loadHidden())
    val hidden: StateFlow<Set<String>> = _hidden

    private val _events = MutableSharedFlow<ChatEvent>(extraBufferCapacity = 64)
    val events: SharedFlow<ChatEvent> = _events

    private val _sending = MutableStateFlow<Map<String, Float>>(emptyMap())
    val sending: StateFlow<Map<String, Float>> = _sending

    private val _pinned = MutableStateFlow<Pinned?>(null)
    val pinned: StateFlow<Pinned?> = _pinned

    class Pinned(val conv: String, val items: List<ChatMessage>)

    val profile: StateFlow<PhoneLink.Profile?> get() = link.profile

    private val _contact = MutableStateFlow<LinkFrame.ContactInfo?>(null)
    val contact: StateFlow<LinkFrame.ContactInfo?> = _contact

    private val _safety = MutableStateFlow<LinkFrame.SafetyNumber?>(null)
    val safety: StateFlow<LinkFrame.SafetyNumber?> = _safety

    private val _mediaList = MutableStateFlow<LinkFrame.MediaList?>(null)
    val mediaList: StateFlow<LinkFrame.MediaList?> = _mediaList

    private val _voiceSending = MutableStateFlow<Set<String>>(emptySet())
    val voiceSending: StateFlow<Set<String>> = _voiceSending
    private val voiceBatches = ConcurrentHashMap<String, String>()

    fun startRecording(): Boolean {
        voice.stop()
        return recorder.start()
    }

    fun sendVoice(conv: String, take: VoiceRecorder.Take): Boolean {
        if (!link.isConnected) {
            _events.tryEmit(ChatEvent.SendFailed(ERROR_OFFLINE))
            return false
        }
        val req = UUID.randomUUID().toString()
        val batch = VOICE_PREFIX + req
        voiceBatches[batch] = conv
        _voiceSending.update { it + conv }
        timer.execute {
            val wav = take.wav
            val ok = link.send(LinkFrame.UploadStart(req, batch, "voice.wav", "audio/wav", wav.size.toLong())) &&
                (0 until wav.size step LinkFrame.CHUNK_BYTES).withIndex().all { (i, from) ->
                    val part = wav.copyOfRange(from, minOf(wav.size, from + LinkFrame.CHUNK_BYTES))
                    link.send(LinkFrame.UploadChunk(req, i, java.util.Base64.getEncoder().encodeToString(part)))
                } &&
                link.send(LinkFrame.UploadEnd(req)) &&
                link.send(LinkFrame.VoiceCommit(batch, conv, req))
            if (!ok) finishVoice(batch, ERROR_OFFLINE)
        }
        return true
    }

    private fun finishVoice(batch: String, error: String?) {
        val conv = voiceBatches.remove(batch) ?: return
        if (voiceBatches.values.none { it == conv }) _voiceSending.update { it - conv }
        if (error != null) _events.tryEmit(ChatEvent.VoiceFailed(error))
    }

    @Volatile var diagnostics: (() -> String)? = null

    private fun sendDiagnostics(req: String) {
        timer.execute {
            val text = runCatching { diagnostics?.invoke() }.getOrNull()
                ?: pl.sinusoide.sinuschat.desktop.diag.DesktopDiagnostics.summary(link, this, null)
            val d = pl.sinusoide.sinuschat.desktop.diag.DesktopDiagnostics
            link.send(LinkFrame.DesktopDiag(req, text, d.version, d.deviceName(), d.osName()))
        }
    }

    fun openOnPhone(section: String): Boolean {
        if (!link.send(LinkFrame.OpenOnPhone(section))) return false
        if (section == LinkFrame.SECTION_REPORT_DESKTOP) sendDiagnostics("")
        return true
    }

    class StoredItem(val message: ChatMessage, val key: String, val bytes: Long)

    fun storedMedia(conv: String?): Pair<List<StoredItem>, Int> {
        val entries = media.vault.entries()
        val convs = if (conv != null) listOf(conv) else entries.map { it.conv }.filter { it.isNotEmpty() }.distinct()
        val out = ArrayList<StoredItem>()
        for (c in convs) {
            for (m in cached(c)) {
                if (m.kind == ChatMessage.KIND_TEXT || m.kind == ChatMessage.KIND_SYSTEM || m.kind == ChatMessage.KIND_LOCATION) continue
                for (key in listOf(MediaVault.fileKey(m.id), MediaVault.voiceKey(m.id))) {
                    val size = media.vault.size(key)
                    if (size > 0) {
                        out += StoredItem(m, key, size)
                        break
                    }
                }
            }
        }
        val known = entries.count { conv == null || it.conv == conv }
        return out.sortedByDescending { it.bytes } to (known - out.size).coerceAtLeast(0)
    }

    fun deleteStored(items: List<StoredItem>) {
        for (i in items) {
            media.vault.remove(i.key)
            media.dropPreview(i.message.id)
        }
    }

    fun chatAction(conv: String, action: String, until: Long = 0L): Boolean {
        if (!link.send(LinkFrame.ChatAction(conv, action, until))) {
            _events.tryEmit(ChatEvent.SendFailed(ERROR_OFFLINE))
            return false
        }
        val now = System.currentTimeMillis()
        when (action) {
            LinkFrame.CHAT_PIN, LinkFrame.CHAT_UNPIN -> _chats.update { list ->
                val next = list.map { if (it.id == conv) it.copy(pinnedAt = if (action == LinkFrame.CHAT_PIN) now else 0L) else it }
                val (top, rest) = next.sortedByDescending { it.lastAt }.partition { it.pinnedAt > 0L }
                top.sortedByDescending { it.pinnedAt } + rest
            }
            LinkFrame.CHAT_MUTE -> _chats.update { list ->
                list.map { if (it.id == conv) it.copy(muted = until == LinkFrame.MUTE_FOREVER || until > now) else it }
            }
            LinkFrame.CHAT_BLOCK, LinkFrame.CHAT_UNBLOCK -> _chats.update { list ->
                list.map { if (it.id == conv) it.copy(blocked = action == LinkFrame.CHAT_BLOCK) else it }
            }
            LinkFrame.CHAT_DELETE, LinkFrame.CHAT_BLOCK_DELETE -> {
                if (_openConv.value == conv) close()
                _chats.update { list -> list.filter { it.id != conv } }
                perConv.remove(conv)
                cache.saveMessages(conv, emptyList())
                timer.execute { media.vault.removeConversation(conv) }
            }
        }
        _contact.update { c -> if (c?.conv == conv && (action == LinkFrame.CHAT_BLOCK || action == LinkFrame.CHAT_UNBLOCK)) c.copy(blocked = action == LinkFrame.CHAT_BLOCK) else c }
        return true
    }

    private val _trOffers = MutableStateFlow<Map<String, LinkFrame.TranslateOffer>>(emptyMap())
    val trOffers: StateFlow<Map<String, LinkFrame.TranslateOffer>> = _trOffers

    private val _trSend = MutableStateFlow<LinkFrame.TranslateSendOffer?>(null)
    val trSend: StateFlow<LinkFrame.TranslateSendOffer?> = _trSend

    private val _trInfo = MutableStateFlow<LinkFrame.TranslateInfo?>(null)
    val trInfo: StateFlow<LinkFrame.TranslateInfo?> = _trInfo

    private val _restoreDraft = MutableSharedFlow<Pair<String, String>>(extraBufferCapacity = 4)
    val restoreDraft: SharedFlow<Pair<String, String>> = _restoreDraft

    fun answerTranslation(conv: String, lang: String, yes: Boolean) {
        _trOffers.update { it - conv }
        link.send(LinkFrame.TranslateDecision(conv, lang, yes))
    }

    fun chooseSend(offer: LinkFrame.TranslateSendOffer, translated: Boolean) {
        _trSend.value = null
        if (!link.send(LinkFrame.TranslateSendChoice(offer.id, translated))) {
            _events.tryEmit(ChatEvent.SendFailed(ERROR_OFFLINE))
            return
        }
        if (translated) outbox[offer.id]?.let { outbox[offer.id] = it.copy(text = offer.translated, trShown = offer.translated, trOriginal = offer.original) }
        outbox[offer.id]?.let { showOpen(it.conv) }
    }

    fun cancelSend(offer: LinkFrame.TranslateSendOffer) {
        _trSend.value = null
        link.send(LinkFrame.TranslateSendChoice(offer.id, translated = false, cancel = true))
        outbox.remove(offer.id)?.let { showOpen(it.conv) }
        _restoreDraft.tryEmit(offer.conv to offer.original)
    }

    fun requestTranslateInfo() {
        link.send(LinkFrame.TranslateInfoRequest())
    }

    fun translateSet(set: LinkFrame.TranslateSet) {
        if (!link.send(set)) _events.tryEmit(ChatEvent.SendFailed(ERROR_OFFLINE))
    }

    fun requestContact(conv: String) {
        if (_contact.value?.conv != conv) _contact.value = null
        link.send(LinkFrame.ContactRequest(conv))
    }

    fun requestSafety(conv: String) {
        _safety.value = null
        link.send(LinkFrame.SafetyRequest(conv))
    }

    fun requestMediaList(conv: String) {
        if (_mediaList.value?.conv != conv) _mediaList.value = null
        link.send(LinkFrame.MediaListRequest(conv))
    }

    fun updateContact(update: LinkFrame.ContactUpdate): Boolean {
        if (!link.send(update)) {
            _events.tryEmit(ChatEvent.SendFailed(ERROR_OFFLINE))
            return false
        }
        _contact.update { c ->
            if (c == null || c.conv != update.conv) c
            else c.copy(
                nick = update.nick?.trim() ?: c.nick,
                muteUntil = update.muteUntil ?: c.muteUntil,
                translateBlocked = update.translateBlocked ?: c.translateBlocked,
                blocked = update.blocked ?: c.blocked
            )
        }
        update.verified?.let { v -> _safety.update { s -> if (s?.conv == update.conv) s.copy(verified = v) else s } }
        update.muteUntil?.let { until ->
            val muted = until == LinkFrame.MUTE_FOREVER || until > System.currentTimeMillis()
            _chats.update { list -> list.map { if (it.id == update.conv) it.copy(muted = muted) else it } }
        }
        if (update.clearChat) {
            perConv[update.conv] = emptyList()
            cache.saveMessages(update.conv, emptyList())
            showOpen(update.conv)
            _mediaList.update { m -> if (m?.conv == update.conv) LinkFrame.MediaList(update.conv, emptyList()) else m }
            _pinned.update { p -> if (p?.conv == update.conv) Pinned(update.conv, emptyList()) else p }
            timer.execute { media.vault.removeConversation(update.conv) }
        }
        return true
    }

    private val _profileBusy = MutableStateFlow<Set<String>>(emptySet())
    val profileBusy: StateFlow<Set<String>> = _profileBusy
    @Volatile private var ownAvatar: String? = link.profile.value?.avatar
    @Volatile private var removingPhoto = false

    private val perConv = ConcurrentHashMap<String, List<ChatMessage>>()
    private val outbox = ConcurrentHashMap<String, ChatMessage>()
    private val ghosts = ConcurrentHashMap<String, Ghost>()
    private val fromPc = ConcurrentHashMap.newKeySet<String>()
    private val phoneProgress = ConcurrentHashMap<String, Map<String, Float>>()
    private val typingUntil = ConcurrentHashMap<String, Long>()
    private val timer = Executors.newSingleThreadScheduledExecutor { r -> Thread(r, "chat-timer").apply { isDaemon = true } }
    @Volatile private var lastTypingSent = 0L
    @Volatile private var typingConv: String? = null

    init {
        link.frameListener = ::onFrame
        link.onLinkUp = ::onLinkUp
        link.onForgotten = ::forget
        link.onLinkDown = {
            _profileBusy.value = emptySet()
            voiceBatches.keys.toList().forEach { finishVoice(it, ERROR_OFFLINE) }
            calls.onLinkDown()
            media.onLinkDown()
            uploads.onLinkDown()
            phoneProgress.clear()
            refreshSending()
        }
        uploads.onOutcome = { o ->
            when (o) {
                is UploadQueue.Outcome.Failed -> {
                    dropGhosts { it.batch == o.batch }
                    _events.tryEmit(ChatEvent.SendFailed(o.error))
                }
                is UploadQueue.Outcome.Done -> if (o.ids.isEmpty()) {
                    timer.schedule({ dropGhosts { it.batch == o.batch } }, LEGACY_GHOST_MS, TimeUnit.MILLISECONDS)
                }
            }
        }
        uploads.onDelivered = ::onDelivered
    }

    fun emit(event: ChatEvent) {
        _events.tryEmit(event)
    }

    private fun onLinkUp() {
        media.onLinkUp()
        link.send(LinkFrame.ChatsRequest(refresh = true))
        _openConv.value?.let { link.send(LinkFrame.OpenChat(it, PAGE)) }
        outbox.values.sortedBy { it.at }.forEach { link.send(LinkFrame.SendText(it.id, it.conv, it.text, it.replyTo)) }
    }

    private fun withOutbox(conv: String, list: List<ChatMessage>): List<ChatMessage> {
        if (outbox.isEmpty() && ghosts.isEmpty()) return list
        val ids = list.mapTo(HashSet()) { it.id }
        outbox.values.filter { it.conv == conv && it.id in ids }.forEach { outbox.remove(it.id) }
        ghosts.values.filter { g -> g.msg.conv == conv && g.realId?.let { it in ids } == true }.forEach {
            ghosts.remove(it.msg.id)
            media.dropPreview(it.msg.id)
        }
        val extra = outbox.values.filter { it.conv == conv } + ghosts.values.filter { it.msg.conv == conv }.map { it.msg }
        return if (extra.isEmpty()) list else (list + extra).sortedBy { it.at }
    }

    fun isLocalOnly(id: String) = id.startsWith(GHOST_PREFIX)

    fun sendProgress(m: ChatMessage, phone: Map<String, Float>, local: Map<String, Float>): Float? {
        if (!m.mine || m.status != ChatMessage.STATUS_UPLOADING) return null
        ghosts[m.id]?.let { g -> return if (g.realId != null) PC_SHARE else PC_SHARE * (local[g.staged] ?: 0f) }
        return phone[m.id] ?: if (m.id in fromPc) PC_SHARE else -1f
    }

    private fun refreshSending() {
        val out = HashMap<String, Float>()
        for (items in phoneProgress.values) for ((id, p) in items) {
            out[id] = if (p >= 0f && id in fromPc) PC_SHARE + (1f - PC_SHARE) * p else p
        }
        _sending.value = out
    }

    private fun addGhosts(conv: String, batch: String, items: List<UploadQueue.Staged>, caption: String, replyTo: String?) {
        val known = cached(conv)
        val me = known.lastOrNull { it.mine }?.sender.orEmpty()
        val base = maxOf(System.currentTimeMillis(), (known.lastOrNull()?.at ?: 0L) + 1)
        val album = if (items.count { it.isImage && !it.viewOnce } >= 2) GHOST_PREFIX + batch else null
        val captionAt = if (caption.isEmpty() && replyTo == null) -1 else items.indexOfFirst { !it.viewOnce }
        val reply = replyTo?.let { r -> known.firstOrNull { it.id == r } }
        items.forEachIndexed { i, s ->
            val id = GHOST_PREFIX + s.id
            val withCaption = i == captionAt
            ghosts[id] = Ghost(ChatMessage(
                id = id,
                conv = conv,
                sender = me,
                mine = true,
                text = if (withCaption) caption else "",
                kind = when {
                    s.isImage -> ChatMessage.KIND_PHOTO
                    s.isVideo -> ChatMessage.KIND_VIDEO
                    else -> ChatMessage.KIND_FILE
                },
                fileName = s.name,
                at = base + i,
                status = ChatMessage.STATUS_UPLOADING,
                replyTo = if (withCaption) replyTo else null,
                replyText = if (withCaption) reply?.text?.take(200) else null,
                viewOnce = s.viewOnce,
                size = s.size,
                albumId = if (s.isImage && !s.viewOnce) album else null,
                hasThumb = s.isMedia && !s.viewOnce
            ), batch, s.id)
            if (s.isMedia && !s.viewOnce) media.previewLocal(id, s.file, s.isVideo)
        }
        showOpen(conv)
    }

    private fun onDelivered(conv: String, sent: List<Pair<String, UploadQueue.Staged>>) {
        val batch = sent.firstNotNullOfOrNull { ghosts[GHOST_PREFIX + it.second.id]?.batch }
        for ((real, s) in sent) {
            fromPc.add(real)
            ghosts[GHOST_PREFIX + s.id]?.realId = real
            media.copyPreview(GHOST_PREFIX + s.id, real)
        }
        refreshSending()
        showOpen(conv)
        for ((real, s) in sent) media.adoptSent(real, s.file, s.isMedia && !s.viewOnce, s.isVideo, keep = !s.viewOnce, conv = conv)
        if (batch != null) timer.schedule({ dropGhosts { it.batch == batch } }, GHOST_LINGER_MS, TimeUnit.MILLISECONDS)
    }

    private fun dropGhosts(which: (Ghost) -> Boolean) {
        val gone = ghosts.values.filter(which)
        if (gone.isEmpty()) return
        gone.forEach {
            ghosts.remove(it.msg.id)
            media.dropPreview(it.msg.id)
        }
        gone.map { it.msg.conv }.toSet().forEach { showOpen(it) }
    }

    private fun showOpen(conv: String) {
        if (_openConv.value == conv) _messages.value = withOutbox(conv, cached(conv))
    }

    private fun forget() {
        _openConv.value = null
        _messages.value = emptyList()
        _chats.value = emptyList()
        _typing.value = emptyMap()
        perConv.clear()
        ghosts.clear()
        fromPc.clear()
        phoneProgress.clear()
        _sending.value = emptyMap()
        _pinned.value = null
        _contact.value = null
        _safety.value = null
        _mediaList.value = null
        recorder.cancel()
        _hidden.value = emptySet()
        cache.wipe()
        voice.stop()
        media.wipe()
    }

    fun shutdown() {
        _openConv.value?.let { link.send(LinkFrame.CloseChat(it)) }
        cache.flush()
    }

    private fun cached(conv: String): List<ChatMessage> =
        perConv[conv] ?: cache.loadMessages(conv).also { perConv[conv] = it }

    fun open(conv: String) {
        val prev = _openConv.value
        if (prev == conv) return
        prev?.let { link.send(LinkFrame.CloseChat(it)) }
        stopTyping()
        _openConv.value = conv
        _messages.value = withOutbox(conv, cached(conv))
        _hasMore.value = false
        _loadingOlder.value = false
        link.send(LinkFrame.OpenChat(conv, PAGE))
    }

    fun close() {
        val prev = _openConv.value ?: return
        stopTyping()
        link.send(LinkFrame.CloseChat(prev))
        _openConv.value = null
        _messages.value = emptyList()
    }

    fun loadOlder() {
        val conv = _openConv.value ?: return
        if (!_hasMore.value || _loadingOlder.value) return
        val first = _messages.value.firstOrNull() ?: return
        _loadingOlder.value = true
        if (!link.send(LinkFrame.OlderMessages(conv, first.at, PAGE))) _loadingOlder.value = false
    }

    fun send(text: String, replyTo: String?): Boolean {
        val conv = _openConv.value ?: return false
        val clean = text.trim()
        if (uploads.hasStaged(conv)) {
            if (!link.isConnected) {
                _events.tryEmit(ChatEvent.SendFailed(ERROR_OFFLINE))
                return false
            }
            stopTyping()
            val apart = clean.isNotEmpty() && uploads.allViewOnce(conv)
            val caption = if (apart) "" else clean
            val reply = if (apart) null else replyTo
            val (batch, items) = uploads.send(conv, caption, reply) ?: return false
            addGhosts(conv, batch, items, caption, reply)
            if (apart) sendText(conv, clean, replyTo)
            return true
        }
        if (clean.isEmpty()) return false
        stopTyping()
        return sendText(conv, clean, replyTo)
    }

    private fun sendText(conv: String, clean: String, replyTo: String?): Boolean {
        val id = UUID.randomUUID().toString()
        val known = cached(conv)
        val reply = replyTo?.let { r -> known.firstOrNull { it.id == r } }
        outbox[id] = ChatMessage(
            id = id,
            conv = conv,
            sender = known.lastOrNull { it.mine }?.sender.orEmpty(),
            mine = true,
            text = clean,
            at = maxOf(System.currentTimeMillis(), (known.lastOrNull()?.at ?: 0L) + 1),
            status = ChatMessage.STATUS_PENDING,
            replyTo = replyTo,
            replyText = reply?.text?.take(200)
        )
        showOpen(conv)
        link.send(LinkFrame.SendText(id, conv, clean, replyTo))
        return true
    }

    private val pendingDeletes = ConcurrentHashMap<String, java.util.concurrent.ScheduledFuture<*>>()

    private fun deleteKey(ids: Collection<String>) = ids.sorted().joinToString(",")

    fun deleteMessages(conv: String, ids: List<String>, delayMs: Long) {
        if (ids.isEmpty()) return
        hide(ids)
        val key = deleteKey(ids)
        pendingDeletes.remove(key)?.cancel(false)
        pendingDeletes[key] = timer.schedule({
            if (pendingDeletes.remove(key) == null) return@schedule
            if (!link.send(LinkFrame.DeleteMessages(conv, ids))) {
                unhide(ids)
                _events.tryEmit(ChatEvent.SendFailed(ERROR_OFFLINE))
            }
        }, delayMs, TimeUnit.MILLISECONDS)
    }

    fun undoDelete(ids: List<String>) {
        pendingDeletes.remove(deleteKey(ids))?.cancel(false)
        unhide(ids)
    }

    private fun onDeleted(conv: String, ids: List<String>) {
        val drop = ids.toSet()
        val next = cached(conv).filter { it.id !in drop }
        perConv[conv] = next
        cache.saveMessages(conv, next)
        if (_openConv.value == conv) _messages.value = withOutbox(conv, next)
        if (_hidden.value.any { it in drop }) {
            _hidden.update { it - drop }
            cache.saveHidden(_hidden.value)
        }
    }

    fun hide(ids: Collection<String>) {
        _hidden.update { it + ids }
        cache.saveHidden(_hidden.value)
    }

    fun unhide(ids: Collection<String>) {
        val drop = ids.toSet()
        _hidden.update { it - drop }
        cache.saveHidden(_hidden.value)
    }

    fun forward(conv: String, texts: List<String>): Boolean {
        val clean = texts.map { it.trim() }.filter { it.isNotEmpty() }
        if (clean.isEmpty()) return false
        val ok = clean.all { link.send(LinkFrame.SendText(UUID.randomUUID().toString(), conv, it)) }
        if (!ok) _events.tryEmit(ChatEvent.SendFailed(ERROR_OFFLINE))
        return ok
    }

    fun canChange(m: ChatMessage): Boolean =
        m.kind != ChatMessage.KIND_SYSTEM && !isLocalOnly(m.id) && !outbox.containsKey(m.id) &&
            m.status != ChatMessage.STATUS_PENDING && m.status != ChatMessage.STATUS_FAILED &&
            m.status != ChatMessage.STATUS_UPLOADING

    fun canEdit(m: ChatMessage, isGroup: Boolean): Boolean =
        !isGroup && m.mine && m.kind == ChatMessage.KIND_TEXT && !m.viewOnce && m.text.isNotBlank() && canChange(m)

    fun myReaction(m: ChatMessage): String? = myHash(m.conv)?.let { m.reactions[it] }

    fun setPinned(m: ChatMessage, pinned: Boolean): Boolean {
        if (!link.send(LinkFrame.PinMessage(m.conv, m.id, pinned))) {
            _events.tryEmit(ChatEvent.SendFailed(ERROR_OFFLINE))
            return false
        }
        patch(m.conv, m.id) { it.copy(pinned = pinned) }
        _pinned.update { p ->
            if (p == null || p.conv != m.conv) p
            else {
                val rest = p.items.filter { it.id != m.id }
                Pinned(p.conv, if (pinned) (rest + m.copy(pinned = true)).sortedByDescending { it.at } else rest)
            }
        }
        return true
    }

    fun react(m: ChatMessage, emoji: String) {
        if (!link.send(LinkFrame.ReactMessage(m.conv, m.id, emoji))) {
            _events.tryEmit(ChatEvent.SendFailed(ERROR_OFFLINE))
            return
        }
        val me = myHash(m.conv) ?: return
        patch(m.conv, m.id) { cur ->
            val next = cur.reactions.toMutableMap()
            if (emoji.isEmpty() || next[me] == emoji) next.remove(me) else next[me] = emoji
            cur.copy(reactions = next)
        }
    }

    fun edit(m: ChatMessage, text: String): Boolean {
        val clean = text.trim()
        if (clean.isEmpty() || clean == m.text) return false
        if (!link.send(LinkFrame.EditMessage(m.conv, m.id, clean))) {
            _events.tryEmit(ChatEvent.SendFailed(ERROR_OFFLINE))
            return false
        }
        patch(m.conv, m.id) { it.copy(text = clean, edited = true) }
        return true
    }

    fun requestPinned(conv: String) {
        link.send(LinkFrame.PinnedRequest(conv))
    }

    private fun myHash(conv: String): String? =
        cached(conv).lastOrNull { it.mine && it.sender.isNotEmpty() && !isLocalOnly(it.id) }?.sender

    private fun patch(conv: String, id: String, change: (ChatMessage) -> ChatMessage) {
        var hit = false
        val next = cached(conv).map { if (it.id == id) change(it).also { hit = true } else it }
        if (!hit) return
        perConv[conv] = next
        cache.saveMessages(conv, next)
        showOpen(conv)
    }

    private fun onPinned(f: LinkFrame.PinnedList) {
        _pinned.value = Pinned(f.conv, f.items.sortedByDescending { it.at })
        val ids = f.items.mapTo(HashSet()) { it.id }
        val complete = f.items.size < PINNED_PAGE
        val list = cached(f.conv)
        fun wanted(m: ChatMessage) = if (m.id in ids) true else if (complete) false else m.pinned
        if (list.none { wanted(it) != it.pinned }) return
        val next = list.map { if (wanted(it) != it.pinned) it.copy(pinned = wanted(it)) else it }
        perConv[f.conv] = next
        cache.saveMessages(f.conv, next)
        showOpen(f.conv)
    }

    fun renameProfile(name: String): Boolean {
        val clean = name.filter { !it.isISOControl() }.replace(Regex("\\s+"), " ").trim()
        if (clean.length !in LinkFrame.NAME_MIN..LinkFrame.NAME_MAX) return false
        if (!link.send(LinkFrame.SetProfileName(clean))) {
            _events.tryEmit(ChatEvent.SendFailed(ERROR_OFFLINE))
            return false
        }
        _profileBusy.update { it + LinkFrame.PROFILE_NAME }
        return true
    }

    fun setProfileColor(color: String): Boolean {
        if (!link.send(LinkFrame.SetProfileColor(color))) {
            _events.tryEmit(ChatEvent.SendFailed(ERROR_OFFLINE))
            return false
        }
        _profileBusy.update { it + LinkFrame.PROFILE_COLOR }
        return true
    }

    fun removeProfilePhoto(): Boolean {
        removingPhoto = true
        if (!link.send(LinkFrame.SetProfileAvatar(remove = true))) {
            _events.tryEmit(ChatEvent.SendFailed(ERROR_OFFLINE))
            return false
        }
        _profileBusy.update { it + LinkFrame.PROFILE_AVATAR }
        return true
    }

    fun changeProfilePhoto(file: java.io.File): Boolean {
        if (!link.isConnected) {
            _events.tryEmit(ChatEvent.SendFailed(ERROR_OFFLINE))
            return false
        }
        val jpeg = ProfilePhoto.prepare(file) ?: return false
        removingPhoto = false
        _profileBusy.update { it + LinkFrame.PROFILE_AVATAR }
        timer.execute {
            val req = UUID.randomUUID().toString()
            val ok = link.send(LinkFrame.UploadStart(req, "avatar-$req", "avatar.jpg", "image/jpeg", jpeg.size.toLong())) &&
                jpeg.asList().chunked(LinkFrame.CHUNK_BYTES).withIndex().all { (i, part) ->
                    link.send(LinkFrame.UploadChunk(req, i, java.util.Base64.getEncoder().encodeToString(part.toByteArray())))
                } &&
                link.send(LinkFrame.UploadEnd(req)) &&
                link.send(LinkFrame.SetProfileAvatar(req))
            if (!ok) {
                _profileBusy.update { it - LinkFrame.PROFILE_AVATAR }
                _events.tryEmit(ChatEvent.SendFailed(ERROR_OFFLINE))
            }
        }
        return true
    }

    fun markRead(conv: String) {
        if (!link.send(LinkFrame.MarkRead(conv))) return
        _chats.update { list -> list.map { if (it.id == conv && it.unread > 0) it.copy(unread = 0) else it } }
    }

    fun onComposerChanged(text: String) {
        val conv = _openConv.value ?: return
        if (text.isBlank()) {
            stopTyping()
            return
        }
        val now = System.currentTimeMillis()
        if (typingConv == conv && now - lastTypingSent < TYPING_REPEAT_MS) return
        typingConv = conv
        lastTypingSent = now
        link.send(LinkFrame.TypingOut(conv, true))
    }

    private fun stopTyping() {
        val conv = typingConv ?: return
        typingConv = null
        lastTypingSent = 0L
        link.send(LinkFrame.TypingOut(conv, false))
    }

    private fun onFrame(frame: LinkFrame) {
        when (frame) {
            is LinkFrame.Chats -> {
                val before = _chats.value.associateBy { avatarKeyOf(it) }
                for (c in frame.items) {
                    val old = before[avatarKeyOf(c)] ?: continue
                    if (old.avatar != c.avatar) media.invalidateAvatar(avatarKeyOf(c))
                }
                _chats.value = frame.items
                _hidePreviews.value = frame.hidePreviews
                cache.saveChats(frame.items, frame.hidePreviews)
            }
            is LinkFrame.Messages -> onMessages(frame)
            is LinkFrame.SendProgress -> {
                if (frame.items.isEmpty()) phoneProgress.remove(frame.conv) else phoneProgress[frame.conv] = frame.items
                refreshSending()
            }
            is LinkFrame.SendResult -> if (!frame.ok) {
                outbox.remove(frame.id)?.let { showOpen(it.conv) }
                if (_trSend.value?.id == frame.id) _trSend.value = null
                if (frame.error != LinkFrame.ERROR_CANCELLED) _events.tryEmit(ChatEvent.SendFailed(frame.error ?: LinkFrame.ERROR_FAILED))
            }
            is LinkFrame.PhoneState -> _phoneLocked.value = frame.locked
            is LinkFrame.Incoming -> {
                clearTyping(frame.conv)
                _events.tryEmit(ChatEvent.Incoming(frame))
            }
            is LinkFrame.TypingIn -> if (frame.typing) setTyping(frame.conv, frame.name) else clearTyping(frame.conv)
            is LinkFrame.MessagesDeleted -> onDeleted(frame.conv, frame.ids)
            is LinkFrame.PinnedList -> onPinned(frame)
            is LinkFrame.ContactInfo -> _contact.value = frame
            is LinkFrame.TranslateOffer -> _trOffers.update { it + (frame.conv to frame) }
            is LinkFrame.TranslateSendOffer -> _trSend.value = frame
            is LinkFrame.TranslateInfo -> _trInfo.value = frame
            is LinkFrame.DesktopDiagRequest -> sendDiagnostics(frame.req)
            is LinkFrame.UploadResult -> if (frame.batch.startsWith(VOICE_PREFIX)) {
                finishVoice(frame.batch, if (frame.ok) null else frame.error ?: LinkFrame.ERROR_FAILED)
            } else if (!calls.onFrame(frame) && !media.onFrame(frame)) uploads.onFrame(frame)
            is LinkFrame.SafetyNumber -> _safety.value = frame
            is LinkFrame.MediaList -> _mediaList.value = frame
            is LinkFrame.Profile -> {
                if (ownAvatar != frame.avatar) media.invalidateAvatar(LinkFrame.AVATAR_ME)
                ownAvatar = frame.avatar
            }
            is LinkFrame.ProfileResult -> {
                _profileBusy.update { it - frame.field }
                val removed = frame.field == LinkFrame.PROFILE_AVATAR && removingPhoto
                if (frame.field == LinkFrame.PROFILE_AVATAR) removingPhoto = false
                _events.tryEmit(ChatEvent.Profile(frame.field, frame.ok, removed))
            }
            is LinkFrame.Presence -> _chats.update { list ->
                list.map { if (!it.isGroup && it.peer == frame.peer) it.copy(online = frame.online, lastSeen = frame.lastSeen) else it }
            }
            is LinkFrame.AvatarChanged -> {
                _chats.update { list -> list.map { if (avatarKeyOf(it) == frame.key) it.copy(avatar = frame.token) else it } }
                media.invalidateAvatar(frame.key)
            }
            else -> if (!calls.onFrame(frame) && !media.onFrame(frame)) uploads.onFrame(frame)
        }
    }

    private fun onMessages(frame: LinkFrame.Messages) {
        val conv = frame.conv
        val before = cached(conv)
        val merged = if (frame.mode == LinkFrame.MODE_OLDER) {
            mergeOlder(before, frame.items)
        } else {
            mergeLatest(before, frame.items, frame.more)
        }
        perConv[conv] = merged
        cache.saveMessages(conv, merged)
        if (_openConv.value == conv) {
            _messages.value = withOutbox(conv, merged)
            if (frame.mode == LinkFrame.MODE_OLDER) {
                _hasMore.value = frame.more
                _loadingOlder.value = false
            } else if (!_loadingOlder.value) {
                _hasMore.value = frame.more
            }
        }
    }

    private fun setTyping(conv: String, name: String) {
        typingUntil[conv] = System.currentTimeMillis() + TYPING_MS
        _typing.update { it + (conv to name) }
        timer.schedule({
            val until = typingUntil[conv] ?: return@schedule
            if (System.currentTimeMillis() >= until) clearTyping(conv)
        }, TYPING_MS + 50, TimeUnit.MILLISECONDS)
    }

    private fun clearTyping(conv: String) {
        typingUntil.remove(conv)
        _typing.update { it - conv }
    }
}

private fun avatarKeyOf(c: ChatSummary): String = if (c.isGroup || c.peer.isEmpty()) c.id else c.peer

fun mergeLatest(cached: List<ChatMessage>, window: List<ChatMessage>, more: Boolean): List<ChatMessage> {
    if (!more) return window.sortedBy { it.at }
    if (window.isEmpty()) return cached
    val ids = window.mapTo(HashSet()) { it.id }
    val cutoff = window.minOf { it.at }
    return (cached.filter { it.at < cutoff && it.id !in ids } + window).sortedBy { it.at }
}

fun mergeOlder(cached: List<ChatMessage>, older: List<ChatMessage>): List<ChatMessage> {
    val byId = LinkedHashMap<String, ChatMessage>()
    for (m in older) byId[m.id] = m
    for (m in cached) byId[m.id] = m
    return byId.values.sortedBy { it.at }
}
