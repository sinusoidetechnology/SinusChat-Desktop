package pl.sinusoide.sinuschat.desktop.ui.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.pointer.PointerIcon
import androidx.compose.ui.input.pointer.pointerHoverIcon
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import pl.sinusoide.sinuschat.desktop.LinkState
import pl.sinusoide.sinuschat.desktop.PhoneLink
import pl.sinusoide.sinuschat.desktop.chat.ChatStore
import pl.sinusoide.sinuschat.desktop.chat.ProfilePhoto
import pl.sinusoide.sinuschat.desktop.i18n.ProfileStrings
import pl.sinusoide.sinuschat.desktop.ui.IconAction
import pl.sinusoide.sinuschat.desktop.ui.SinusColors
import pl.sinusoide.sinuschat.desktop.ui.anim.dialogIn
import pl.sinusoide.sinuschat.desktop.ui.chat.Avatar
import pl.sinusoide.sinuschat.desktop.ui.chat.MediaActions
import pl.sinusoide.sinuschat.desktop.ui.chat.rememberAvatar
import pl.sinusoide.sinuschat.desktop.ui.icons.SinusIcon
import pl.sinusoide.sinuschat.link.LinkFrame

@Composable
internal fun ProfileSection(link: PhoneLink, store: ChatStore, ps: ProfileStrings, onRename: () -> Unit, onPhoto: () -> Unit, onToast: (String) -> Unit) {
    val profile by link.profile.collectAsState()
    val state by link.state.collectAsState()
    val busy by store.profileBusy.collectAsState()
    val online = state is LinkState.Connected
    val name = profile?.name.orEmpty()
    val hasPhoto = profile?.avatar?.isNotEmpty() == true
    val image = rememberAvatar(store, "", if (hasPhoto) LinkFrame.AVATAR_ME else "")
    val photoBusy = LinkFrame.PROFILE_AVATAR in busy
    val nameBusy = LinkFrame.PROFILE_NAME in busy

    fun pickPhoto() = onPhoto()

    SectionTitle(ps.profileTitle)
    Card {
        Row(
            Modifier.fillMaxWidth().padding(start = 18.dp, end = 14.dp, top = 18.dp, bottom = 14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(Modifier.size(84.dp), contentAlignment = Alignment.Center) {
                Avatar(name.ifBlank { "?" }, profile?.color?.ifBlank { null }, 84.dp, image)
                if (photoBusy) {
                    Box(Modifier.size(84.dp).clip(CircleShape).background(Color(0x66000000)), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator(color = Color.White, strokeWidth = 2.5.dp, modifier = Modifier.size(30.dp))
                    }
                } else if (online) {
                    Box(
                        Modifier.align(Alignment.BottomEnd).size(30.dp).shadow(3.dp, CircleShape).clip(CircleShape)
                            .background(SinusColors.primary).clickable(onClick = ::pickPhoto).pointerHoverIcon(PointerIcon.Hand),
                        contentAlignment = Alignment.Center
                    ) {
                        SinusIcon("ic_camera", Color.White, Modifier.size(17.dp), ps.changePhoto)
                    }
                }
            }
            Spacer(Modifier.width(18.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    name.ifBlank { "—" }, color = SinusColors.textPrimary, fontSize = 20.sp, fontWeight = FontWeight.Bold,
                    maxLines = 1, overflow = TextOverflow.Ellipsis
                )
                Text(
                    if (online) ps.nameDesc else ps.needsPhone,
                    color = if (online) SinusColors.textSecondary else SinusColors.danger.copy(alpha = 0.9f),
                    fontSize = 13.sp, lineHeight = 18.sp, modifier = Modifier.padding(top = 4.dp)
                )
            }
        }
        RowDivider(18.dp)
        ProfileAction("ic_camera", SinusColors.primary, ps.changePhoto, online && !photoBusy, photoBusy, ::pickPhoto)
        if (hasPhoto) {
            RowDivider()
            ProfileAction("ic_delete", SinusColors.danger, ps.removePhoto, online && !photoBusy, false) { store.removeProfilePhoto() }
        }
        RowDivider()
        ProfileAction("ic_edit", Color(0xFF9B6BE0), ps.changeName, online && !nameBusy, nameBusy, onRename)
    }
}

@Composable
private fun ProfileAction(icon: String, color: Color, label: String, enabled: Boolean, working: Boolean, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().alpha(if (enabled || working) 1f else 0.45f)
            .clickable(enabled = enabled, onClick = onClick)
            .pointerHoverIcon(if (enabled) PointerIcon.Hand else PointerIcon.Default)
            .padding(start = 14.dp, end = 14.dp, top = 11.dp, bottom = 11.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        IconTile(icon, color)
        Text(label, color = if (color == SinusColors.danger) SinusColors.danger else SinusColors.textPrimary, fontSize = 15.sp, modifier = Modifier.weight(1f))
        if (working) CircularProgressIndicator(color = SinusColors.primary, strokeWidth = 2.dp, modifier = Modifier.size(18.dp))
    }
}

@Composable
internal fun PhotoDialog(link: PhoneLink, store: ChatStore, ps: ProfileStrings, onDismiss: () -> Unit, onToast: (String) -> Unit) {
    val profile by link.profile.collectAsState()
    val original = profile?.color?.ifBlank { null } ?: pl.sinusoide.sinuschat.desktop.ui.chat.AvatarColors.PALETTE[0]
    val hadPhoto = profile?.avatar?.isNotEmpty() == true
    val current = rememberAvatar(store, "", if (hadPhoto) LinkFrame.AVATAR_ME else "")
    var picked by remember { mutableStateOf(original) }
    var pending by remember { mutableStateOf<java.io.File?>(null) }
    var pendingImage by remember { mutableStateOf<androidx.compose.ui.graphics.ImageBitmap?>(null) }
    var remove by remember { mutableStateOf(false) }
    val hasPhoto = pending != null || (hadPhoto && !remove)
    val changed = !picked.equals(original, ignoreCase = true) || pending != null || (remove && hadPhoto)
    val name = profile?.name.orEmpty().ifBlank { "?" }
    fun choose() {
        val file = MediaActions.chooseOpen(ps.chooseImage).firstOrNull() ?: return
        val jpeg = if (file.extension.lowercase() in ProfilePhoto.extensions) ProfilePhoto.prepare(file) else null
        val img = jpeg?.let { pl.sinusoide.sinuschat.desktop.ui.chat.previewOf(file, 512) }
        if (jpeg == null || img == null) {
            onToast(ps.photoInvalid)
            return
        }
        pending = file
        pendingImage = img
        remove = false
    }
    val shape = RoundedCornerShape(SinusColors.radius(22.dp))
    val halo = pl.sinusoide.sinuschat.desktop.ui.chat.ChatFormat.color(picked)
    Box(
        Modifier.fillMaxSize().background(Color(0x66000000))
            .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null, onClick = onDismiss),
        contentAlignment = Alignment.Center
    ) {
        Column(
            Modifier.padding(24.dp).widthIn(max = 460.dp).fillMaxWidth().dialogIn()
                .shadow(16.dp, shape).clip(shape).background(SinusColors.card).border(1.dp, SinusColors.border, shape)
                .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) {}
                .padding(start = 24.dp, end = 24.dp, top = 22.dp, bottom = 18.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(ps.photoTitle, color = SinusColors.textPrimary, fontSize = 20.sp, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center)
            Text(ps.photoDesc, color = SinusColors.textSecondary, fontSize = 13.5.sp, textAlign = TextAlign.Center, modifier = Modifier.padding(top = 6.dp, bottom = 18.dp))
            Box(Modifier.size(128.dp).clip(CircleShape).background(halo.copy(alpha = 0x33 / 255f)), contentAlignment = Alignment.Center) {
                val shown = pendingImage ?: current.takeIf { hadPhoto && !remove }
                Avatar(name, picked, 112.dp, shown)
            }
            Row(Modifier.padding(top = 12.dp, bottom = 10.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                PhotoChip("ic_image", if (hasPhoto) ps.changeChip else ps.chooseChip, SinusColors.primary, ::choose)
                if (hasPhoto) PhotoChip("ic_delete", ps.removeChip, SinusColors.danger) {
                    pending = null
                    pendingImage = null
                    remove = true
                }
            }
            PhotoChip("ic_download", ps.downloadPhoto, SinusColors.primary) {
                val shown = pendingImage ?: current.takeIf { hadPhoto && !remove }
                pl.sinusoide.sinuschat.desktop.ui.chat.AvatarExport.save(store, name.takeIf { it != "?" } ?: "SinusChat", picked, shown)
            }
            Spacer(Modifier.height(20.dp))
            Text(ps.initialColor, color = SinusColors.textSecondary, fontSize = 12.sp, modifier = Modifier.fillMaxWidth().padding(bottom = 6.dp))
            Row(
                Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(vertical = 8.dp, horizontal = 6.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                for (hex in pl.sinusoide.sinuschat.desktop.ui.chat.AvatarColors.PALETTE) {
                    val selected = hex.equals(picked, ignoreCase = true)
                    Box(
                        Modifier.size(40.dp).graphicsLayer { val sc = if (selected) 1.08f else 1f; scaleX = sc; scaleY = sc }
                            .clip(CircleShape).background(pl.sinusoide.sinuschat.desktop.ui.chat.ChatFormat.color(hex))
                            .border(2.5.dp, if (selected) (if (SinusColors.palette.dark) Color.White else SinusColors.textPrimary) else Color.Transparent, CircleShape)
                            .clickable { picked = hex }.pointerHoverIcon(PointerIcon.Hand),
                        contentAlignment = Alignment.Center
                    ) {
                        if (selected) SinusIcon("ic_check", Color.White, Modifier.size(20.dp))
                    }
                }
            }
            Spacer(Modifier.height(16.dp))
            Box(
                Modifier.fillMaxWidth().alpha(if (changed) 1f else 0.45f).clip(RoundedCornerShape(SinusColors.radius(26.dp))).background(SinusColors.primary)
                    .clickable(enabled = changed) {
                        if (!picked.equals(original, ignoreCase = true)) store.setProfileColor(picked)
                        val file = pending
                        when {
                            file != null -> if (!store.changeProfilePhoto(file)) onToast(ps.photoInvalid)
                            remove && hadPhoto -> store.removeProfilePhoto()
                        }
                        onDismiss()
                    }
                    .pointerHoverIcon(if (changed) PointerIcon.Hand else PointerIcon.Default).padding(vertical = 14.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(ps.save, color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.Bold)
            }
            Text(
                ps.cancel, color = SinusColors.textSecondary, fontSize = 15.sp, textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(SinusColors.radius(12.dp))).clickable(onClick = onDismiss)
                    .pointerHoverIcon(PointerIcon.Hand).padding(top = 14.dp, bottom = 4.dp)
            )
        }
    }
}

@Composable
private fun PhotoChip(icon: String, label: String, tint: Color, onClick: () -> Unit) {
    Row(
        Modifier.clip(RoundedCornerShape(20.dp)).background(SinusColors.surfaceVariant).clickable(onClick = onClick)
            .pointerHoverIcon(PointerIcon.Hand).padding(start = 14.dp, end = 16.dp, top = 8.dp, bottom = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        SinusIcon(icon, tint, Modifier.size(18.dp))
        Spacer(Modifier.width(8.dp))
        Text(label, color = tint, fontSize = 13.5.sp)
    }
}

@Composable
internal fun RenameDialog(current: String, ps: ProfileStrings, onDismiss: () -> Unit, onSave: (String) -> Unit) {
    var value by remember { mutableStateOf(TextFieldValue(current, TextRange(current.length))) }
    val focus = remember { FocusRequester() }
    LaunchedEffect(Unit) { runCatching { focus.requestFocus() } }
    val trimmed = value.text.trim()
    val problem = when {
        trimmed.isEmpty() -> ps.nameRequired
        trimmed.length < LinkFrame.NAME_MIN -> ps.nameTooShort(LinkFrame.NAME_MIN)
        else -> null
    }
    val ok = problem == null && trimmed != current
    fun save() {
        if (ok) onSave(trimmed)
    }
    val shape = RoundedCornerShape(SinusColors.radius(22.dp))
    Box(
        Modifier.fillMaxSize().background(Color(0x66000000))
            .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null, onClick = onDismiss),
        contentAlignment = Alignment.Center
    ) {
        Column(
            Modifier.padding(24.dp).widthIn(max = 440.dp).fillMaxWidth().dialogIn()
                .shadow(16.dp, shape).clip(shape).background(SinusColors.card).border(1.dp, SinusColors.border, shape)
                .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) {}
                .padding(start = 22.dp, end = 22.dp, top = 18.dp, bottom = 18.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(ps.changeName, color = SinusColors.textPrimary, fontSize = 19.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                IconAction("ic_close", ps.cancel, SinusColors.textSecondary, onClick = onDismiss)
            }
            Text(ps.nameDesc, color = SinusColors.textSecondary, fontSize = 13.5.sp, lineHeight = 19.sp, modifier = Modifier.padding(top = 2.dp, bottom = 14.dp))
            val fieldShape = RoundedCornerShape(SinusColors.radius(16.dp))
            Column(
                Modifier.fillMaxWidth().clip(fieldShape).background(SinusColors.surfaceVariant)
                    .border(1.5.dp, if (problem != null && value.text.isNotEmpty()) SinusColors.danger else SinusColors.primary.copy(alpha = 0.7f), fieldShape)
                    .padding(horizontal = 14.dp, vertical = 10.dp)
            ) {
                Text(ps.nameLabel, color = SinusColors.primary, fontSize = 11.5.sp, fontWeight = FontWeight.Bold)
                Box(Modifier.fillMaxWidth().heightIn(min = 26.dp).padding(top = 2.dp), contentAlignment = Alignment.CenterStart) {
                    if (value.text.isEmpty()) Text(ps.nameHint, color = SinusColors.textMuted, fontSize = 16.sp)
                    BasicTextField(
                        value = value,
                        onValueChange = { if (it.text.length <= LinkFrame.NAME_MAX) value = it.copy(text = it.text.replace("\n", " ")) },
                        singleLine = true,
                        textStyle = TextStyle(color = SinusColors.textPrimary, fontSize = 16.sp),
                        cursorBrush = SolidColor(SinusColors.primary),
                        modifier = Modifier.fillMaxWidth().focusRequester(focus).onPreviewKeyEvent { e ->
                            if (e.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false
                            when (e.key) {
                                Key.Enter, Key.NumPadEnter -> {
                                    save()
                                    true
                                }
                                Key.Escape -> {
                                    onDismiss()
                                    true
                                }
                                else -> false
                            }
                        }
                    )
                }
            }
            Row(Modifier.fillMaxWidth().padding(top = 6.dp, start = 4.dp, end = 4.dp)) {
                Text(
                    if (value.text.isNotEmpty()) problem.orEmpty() else "", color = SinusColors.danger, fontSize = 12.sp,
                    modifier = Modifier.weight(1f)
                )
                Text("${value.text.length}/${LinkFrame.NAME_MAX}", color = SinusColors.textMuted, fontSize = 12.sp, textAlign = TextAlign.End)
            }
            Spacer(Modifier.height(14.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End, verticalAlignment = Alignment.CenterVertically) {
                val hover = remember { MutableInteractionSource() }
                val hovered by hover.collectIsHoveredAsState()
                Text(
                    ps.cancel, color = SinusColors.textSecondary, fontSize = 15.sp,
                    modifier = Modifier.clip(RoundedCornerShape(SinusColors.radius(12.dp)))
                        .background(if (hovered) SinusColors.palette.overlay else Color.Transparent)
                        .clickable(interactionSource = hover, indication = null, onClick = onDismiss)
                        .pointerHoverIcon(PointerIcon.Hand).padding(horizontal = 14.dp, vertical = 10.dp)
                )
                Spacer(Modifier.width(8.dp))
                Box(
                    Modifier.clip(RoundedCornerShape(SinusColors.radius(22.dp)))
                        .background(if (ok) SinusColors.primary else SinusColors.primary.copy(alpha = 0.4f))
                        .clickable(enabled = ok, onClick = ::save)
                        .pointerHoverIcon(if (ok) PointerIcon.Hand else PointerIcon.Default)
                        .padding(horizontal = 22.dp, vertical = 10.dp)
                ) {
                    Text(ps.save, color = Color.White, fontSize = 15.sp, fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}
