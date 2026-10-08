package dev.anthonyhfm.amethyst.home.ui.views

import amethyst.composeapp.generated.resources.Res
import amethyst.composeapp.generated.resources.*
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.composeunstyled.Text
import com.composeunstyled.theme.Theme
import dev.anthonyhfm.amethyst.home.account.DesktopHubAccount
import dev.anthonyhfm.amethyst.hub.data.rememberHubImage
import dev.anthonyhfm.amethyst.ui.components.primitives.*
import dev.anthonyhfm.amethyst.ui.modifier.editorEventListener
import dev.anthonyhfm.amethyst.ui.theme.*
import java.awt.FileDialog
import java.awt.Frame
import java.io.File
import org.jetbrains.compose.resources.stringResource
import org.jetbrains.compose.resources.painterResource

@Composable
fun DesktopAccountView() {
    val hub = remember {
        DesktopHubAccount.get()
    }
    val signedIn = hub.account

    if (signedIn == null) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(24.dp),
            contentAlignment = Alignment.Center
        ) {
            Column(
                modifier = Modifier
                    .widthIn(max = 440.dp)
                    .fillMaxWidth(),

                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                if (hub.error != null) {
                    TypographyP(
                        text = hub.error!!
                    )
                }

                if (hub.message != null) {
                    TypographyMuted(
                        text = hub.message!!
                    )
                }

                if (hub.restoringSession) {
                    TypographyH2(
                        text = stringResource(Res.string.account_section_title)
                    )

                    TypographyP(
                        text = stringResource(
                            resource = if (hub.busy) {
                                Res.string.account_restoring_session
                            } else {
                                Res.string.account_restore_retry
                            }
                        )
                    )

                    Button(
                        onClick = { hub.refresh() },
                        enabled = !hub.busy
                    ) {
                        Text(text = stringResource(Res.string.home_hub_retry))
                    }

                    Button(
                        onClick = { hub.signOut() },
                        enabled = !hub.busy
                    ) {
                        Text(text = stringResource(Res.string.account_sign_out))
                    }
                } else {
                    SignedOutAccount(
                        hub = hub
                    )
                }
            }
        }
    } else {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(32.dp),

            verticalArrangement = Arrangement.spacedBy(20.dp),
        ) {
            TypographyH2(
                text = stringResource(Res.string.account_section_title)
            )

            if (hub.error != null) {
                TypographyP(
                    text = hub.error!!
                )
            }

            if (hub.sessionStorageNeedsRetry) {
                Button(
                    onClick = { hub.refresh() },
                    enabled = !hub.busy
                ) {
                    Text(text = stringResource(Res.string.home_hub_retry))
                }
            }

            if (hub.message != null) {
                TypographyMuted(
                    text = hub.message!!
                )
            }

            ProfileHero(
                username = signedIn.username,
                displayName = signedIn.displayName,
                bio = signedIn.bio,
                avatarUrl = signedIn.avatarUrl,
            )

            ProfileAndIdentity(
                hub = hub
            )

            SecurityAndSignIn(
                hub = hub
            )

            DataAndPrivacy(
                hub = hub
            )
        }
    }
}

@Composable
private fun SignedOutAccount(hub: DesktopHubAccount) {
    var register by remember { mutableStateOf(false) }
    var username by remember { mutableStateOf("") }
    var displayName by remember { mutableStateOf("") }
    var email by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var confirmation by remember { mutableStateOf("") }
    var mfaCode by remember { mutableStateOf("") }

    val usernameRequired = stringResource(Res.string.account_error_username_required)
    val passwordRequired = stringResource(Res.string.account_error_password_required)
    val passwordMismatch = stringResource(Res.string.account_error_password_mismatch)
    val resetUsernameRequired = stringResource(Res.string.account_reset_username_first)
    val resetSent = stringResource(Res.string.account_reset_sent)

    Card(
        modifier = Modifier
            .fillMaxWidth()
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth(),

            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Image(
                painter = painterResource(Res.drawable.amethyst_studio_logo),
                contentDescription = null,
                modifier = Modifier
                    .size(42.dp)
            )

            TypographyH3(
                text = stringResource(
                    if (register) {
                        Res.string.account_create
                    } else {
                        Res.string.account_sign_in
                    }
                )
            )

            TypographyMuted(
                text = stringResource(Res.string.account_sync_description),
                modifier = Modifier
                    .padding(horizontal = 24.dp),

                textAlign = TextAlign.Center
            )
        }

        CardContent {
            Column(
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                if (hub.mfaChallenge != null) {
                    TypographyMuted(
                        text = stringResource(Res.string.account_mfa_sign_in_description),
                        modifier = Modifier
                            .padding(horizontal = 24.dp),

                        textAlign = TextAlign.Center
                    )

                    Input(
                        value = mfaCode,
                        onValueChange = {
                            mfaCode = it
                        },
                        modifier = Modifier
                            .fillMaxWidth(),
                        placeholder = stringResource(Res.string.account_authentication_code),
                        enabled = !hub.busy,
                    )

                    Button(
                        onClick = {
                            hub.completeMfa(mfaCode)
                        },
                        enabled = !hub.busy && mfaCode.isNotBlank(),
                    ) {
                        Text(
                            text = stringResource(Res.string.account_verify_continue)
                        )
                    }
                } else {
                    Input(
                        value = username,
                        onValueChange = {
                            username = it
                        },
                        modifier = Modifier
                            .fillMaxWidth(),
                        placeholder = stringResource(Res.string.account_username),
                        enabled = !hub.busy,
                    )

                    if (register) {
                        Input(
                            value = displayName,
                            onValueChange = {
                                displayName = it
                            },
                            modifier = Modifier
                                .fillMaxWidth(),
                            placeholder = stringResource(Res.string.profile_display_name),
                            enabled = !hub.busy,
                        )

                        Input(
                            value = email,
                            onValueChange = {
                                email = it
                            },
                            modifier = Modifier
                                .fillMaxWidth(),
                            placeholder = stringResource(Res.string.account_email_optional),
                            enabled = !hub.busy,
                        )
                    }

                    HubPasswordField(
                        value = password,
                        onValueChange = {
                            password = it
                        },
                        placeholder = stringResource(Res.string.account_password),
                        enabled = !hub.busy,
                    )

                    if (register) {
                        HubPasswordField(
                            value = confirmation,
                            onValueChange = {
                                confirmation = it
                            },
                            placeholder = stringResource(Res.string.account_repeat_password),
                            enabled = !hub.busy,
                        )
                    }

                    Row(
                        modifier = Modifier
                            .fillMaxWidth(),

                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        if (register) {
                            Button(
                                onClick = {
                                    register = false
                                    hub.prepareAuth()
                                },
                                variant = ButtonVariant.Link,
                                enabled = !hub.busy,
                            ) {
                                Text(
                                    text = stringResource(Res.string.account_sign_in)
                                )
                            }
                        } else {
                            Column(
                                modifier = Modifier
                                    .offset(x = -12.dp)
                            ) {
                                Button(
                                    onClick = {
                                        register = true
                                        hub.prepareAuth()
                                    },
                                    variant = ButtonVariant.Link,
                                    enabled = !hub.busy,
                                ) {
                                    Text(
                                        text = stringResource(Res.string.account_create)
                                    )
                                }

                                Button(
                                    onClick = {
                                        if (username.isBlank()) {
                                            hub.showError(resetUsernameRequired)
                                        } else {
                                            hub.requestReset(
                                                username = username,
                                                success = resetSent,
                                            )
                                        }
                                    },
                                    variant = ButtonVariant.Link,
                                    enabled = !hub.busy,
                                ) {
                                    Text(
                                        text = stringResource(Res.string.account_forgot_password)
                                    )
                                }
                            }
                        }

                        Spacer(
                            modifier = Modifier
                                .weight(1f)
                        )

                        Button(
                            onClick = {
                                if (username.isBlank()) {
                                    hub.showError(usernameRequired)
                                } else if (password.isBlank()) {
                                    hub.showError(passwordRequired)
                                } else if (register && password != confirmation) {
                                    hub.showError(passwordMismatch)
                                } else {
                                    hub.signIn(
                                        username = username,
                                        password = password,
                                        register = register,
                                        displayName = displayName,
                                        email = email,
                                    )
                                }
                            },
                            enabled = !hub.busy,
                        ) {
                            Text(
                                text = if (register) {
                                    stringResource(Res.string.account_create)
                                } else {
                                    stringResource(Res.string.account_sign_in)
                                }
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ProfileHero(
    username: String,
    displayName: String,
    bio: String,
    avatarUrl: String?,
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .widthIn(max = 880.dp)
    ) {
        CardContent {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(20.dp)
            ) {
                DesktopHubAvatar(
                    username = username,
                    avatarUrl = avatarUrl,
                    size = 88.dp,
                )

                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    TypographyH3(
                        text = displayName.ifBlank { username }
                    )

                    TypographyMuted(
                        text = "@$username"
                    )

                    if (bio.isNotBlank()) {
                        TypographyP(
                            text = bio
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun ProfileAndIdentity(hub: DesktopHubAccount) {
    val account = hub.account ?: return
    var name by remember(account.id) { mutableStateOf(account.displayName) }
    var bio by remember(account.id) { mutableStateOf(account.bio) }

    LaunchedEffect(account.displayName, account.bio) {
        name = account.displayName
        bio = account.bio
    }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .widthIn(max = 880.dp)
    ) {
        CardHeader {
            CardTitle(
                text = "Profile & identity"
            )

            CardDescription(
                text = "How you appear to artists and listeners on Hub."
            )
        }

        CardContent {
            Column(
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                TypographyMuted(
                    text = "${stringResource(Res.string.account_username)}: @${account.username}"
                )

                Input(
                    value = name,
                    onValueChange = {
                        name = it
                    },
                    modifier = Modifier
                        .fillMaxWidth(),
                    placeholder = stringResource(Res.string.profile_display_name),
                    enabled = !hub.busy,
                )

                Textarea(
                    value = bio,
                    onValueChange = {
                        bio = it
                    },
                    modifier = Modifier
                        .fillMaxWidth(),
                    placeholder = stringResource(Res.string.profile_bio),
                    enabled = !hub.busy,
                )

                Button(
                    onClick = {
                        hub.updateProfile(name, bio)
                    },
                    enabled = !hub.busy && name.isNotBlank(),
                ) {
                    Text(
                        text = stringResource(Res.string.common_save)
                    )
                }

                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Button(
                        onClick = {
                            val dialog = FileDialog(null as Frame?, "Choose avatar", FileDialog.LOAD)
                            dialog.isVisible = true
                            val selected = dialog.file?.let {
                                File(dialog.directory, it)
                            }

                            if (selected != null) {
                                val mime = when (selected.extension.lowercase()) {
                                    "png" -> "image/png"
                                    "jpg", "jpeg" -> "image/jpeg"
                                    "webp" -> "image/webp"
                                    "gif" -> "image/gif"
                                    "svg" -> "image/svg+xml"
                                    else -> null
                                }

                                if (mime == null) {
                                    hub.showError("Choose a PNG, JPEG, WebP, GIF, or SVG image.")
                                } else if (selected.length() > 2 * 1024 * 1024) {
                                    hub.showError("Choose an image smaller than 2 MB.")
                                } else {
                                    hub.updateAvatar(selected.readBytes(), mime)
                                }
                            }
                        },
                        variant = ButtonVariant.Outline,
                        enabled = !hub.busy,
                    ) {
                        Text(
                            text = "Change avatar"
                        )
                    }

                    if (account.avatarUrl != null) {
                        Button(
                            onClick = hub::removeAvatar,
                            variant = ButtonVariant.Ghost,
                            enabled = !hub.busy,
                        ) {
                            Text(
                                text = stringResource(Res.string.common_remove)
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun SecurityAndSignIn(hub: DesktopHubAccount) {
    val account = hub.account ?: return
    var current by remember { mutableStateOf("") }
    var replacement by remember { mutableStateOf("") }
    var confirm by remember { mutableStateOf("") }
    var newEmail by remember(account.id) { mutableStateOf(account.email.orEmpty()) }

    LaunchedEffect(account.email) {
        newEmail = account.email.orEmpty()
    }

    var emailPassword by remember { mutableStateOf("") }
    var code by remember { mutableStateOf("") }
    val passwordMismatch = stringResource(Res.string.account_error_password_mismatch)
    val verificationSent = stringResource(Res.string.account_email_verification_sent)
    val emailRemoved = stringResource(Res.string.account_email_removed)

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .widthIn(max = 880.dp)
    ) {
        CardHeader {
            CardTitle(
                text = "Security & sign-in"
            )

            CardDescription(
                text = "Manage your password, email, and verification."
            )
        }

        CardContent {
            Column(
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                TypographyMuted(
                    text = "${stringResource(Res.string.account_authenticator_app)}: ${
                        if (account.totpEnabled) {
                            stringResource(Res.string.common_enabled)
                        } else {
                            stringResource(Res.string.common_not_enabled)
                        }
                    }"
                )

                TypographyH4(
                    text = stringResource(Res.string.account_change_password)
                )

                HubPasswordField(
                    value = current,
                    onValueChange = {
                        current = it
                    },
                    placeholder = stringResource(Res.string.account_current_password),
                    enabled = !hub.busy,
                )

                HubPasswordField(
                    value = replacement,
                    onValueChange = {
                        replacement = it
                    },
                    placeholder = stringResource(Res.string.account_new_password),
                    enabled = !hub.busy,
                )

                HubPasswordField(
                    value = confirm,
                    onValueChange = {
                        confirm = it
                    },
                    placeholder = stringResource(Res.string.account_repeat_new_password),
                    enabled = !hub.busy,
                )

                Button(
                    onClick = {
                        if (replacement != confirm) {
                            hub.showError(passwordMismatch)
                        } else {
                            hub.changePassword(current, replacement) {
                                current = ""
                                replacement = ""
                                confirm = ""
                            }
                        }
                    },
                    enabled = !hub.busy && current.isNotBlank() && replacement.isNotBlank(),
                ) {
                    Text(
                        text = stringResource(Res.string.account_change_password)
                    )
                }

                Spacer(
                    modifier = Modifier
                        .height(8.dp)
                )

                TypographyH4(
                    text = stringResource(Res.string.account_email_title)
                )

                TypographyMuted(
                    text = "${stringResource(Res.string.account_current_email)}: ${account.email ?: stringResource(Res.string.common_not_added)}"
                )

                Input(
                    value = newEmail,
                    onValueChange = {
                        newEmail = it
                    },
                    modifier = Modifier
                        .fillMaxWidth(),
                    placeholder = stringResource(Res.string.account_new_email_address),
                    enabled = !hub.busy,
                )

                HubPasswordField(
                    value = emailPassword,
                    onValueChange = {
                        emailPassword = it
                    },
                    placeholder = stringResource(Res.string.account_current_password),
                    enabled = !hub.busy,
                )

                if (account.totpEnabled) {
                    Input(
                        value = code,
                        onValueChange = {
                            code = it
                        },
                        modifier = Modifier
                            .fillMaxWidth(),
                        placeholder = stringResource(Res.string.account_authenticator_code),
                        enabled = !hub.busy,
                    )
                }

                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Button(
                        onClick = {
                            hub.changeEmail(emailPassword, newEmail, code, verificationSent)
                        },
                        enabled = !hub.busy && newEmail.isNotBlank() && emailPassword.isNotBlank(),
                    ) {
                        Text(
                            text = stringResource(Res.string.account_update_email)
                        )
                    }

                    if (account.email != null) {
                        Button(
                            onClick = {
                                hub.removeEmail(emailPassword, code, emailRemoved)
                            },
                            variant = ButtonVariant.Outline,
                            enabled = !hub.busy && emailPassword.isNotBlank(),
                        ) {
                            Text(
                                text = stringResource(Res.string.account_remove_email)
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun DataAndPrivacy(hub: DesktopHubAccount) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .widthIn(max = 880.dp)
    ) {
        CardHeader {
            CardTitle(
                text = "Data & privacy"
            )

            CardDescription(
                text = "Your Hub data and account session."
            )
        }

        CardContent {
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Button(
                    onClick = hub::refresh,
                    variant = ButtonVariant.Outline,
                    enabled = !hub.busy,
                ) {
                    Text(
                        text = "Refresh account"
                    )
                }

                Button(
                    onClick = hub::signOut,
                    variant = ButtonVariant.Outline,
                    enabled = !hub.busy,
                ) {
                    Text(
                        text = stringResource(Res.string.account_sign_out)
                    )
                }
            }
        }
    }
}

@Composable
private fun HubPasswordField(
    value: String,
    onValueChange: (String) -> Unit,
    placeholder: String,
    enabled: Boolean,
) {
    val foreground = Theme[colors][foreground]

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(44.dp)
            .clip(DefaultShape)
            .background(Theme[colors][background])
            .border(1.dp, Theme[colors][input], DefaultShape)
            .padding(horizontal = 12.dp),
        contentAlignment = Alignment.CenterStart
    ) {
        if (value.isEmpty()) {
            Text(
                text = placeholder,
                color = Theme[colors][mutedForeground]
            )
        }

        BasicTextField(
            value = value,
            onValueChange = onValueChange,
            modifier = Modifier
                .fillMaxWidth(),
            enabled = enabled,
            singleLine = true,
            visualTransformation = PasswordVisualTransformation(),
            textStyle = Theme[typography][small].copy(color = foreground),
            cursorBrush = SolidColor(foreground),
        )
    }
}

@Composable
fun DesktopHubAvatar(
    username: String,
    avatarUrl: String?,
    size: Dp,
    modifier: Modifier = Modifier,
) {
    val bitmap = rememberHubImage(path = avatarUrl)

    Box(
        modifier = modifier
            .size(size)
            .clip(CircleShape)
            .background(Theme[colors][muted]),
        contentAlignment = Alignment.Center
    ) {
        if (bitmap != null) {
            Image(
                bitmap = bitmap,
                contentDescription = "@$username avatar",
                modifier = Modifier
                    .fillMaxSize(),
                contentScale = ContentScale.Crop,
            )
        } else {
            Text(
                text = username.take(2).uppercase(),
                color = Theme[colors][mutedForeground]
            )
        }
    }
}
